package mcjty.arienteworld.entities.scout;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.entities.soldier.SoldierBehaviourType;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

import static mcjty.ariente.setup.Registration.AICORE;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class ScoutCoreTests {

    @GameTest(template = "empty")
    public static void uninitializedBasesNeedRealCoresAndRetainMultipleCoreStateAcrossReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        City city = findLargeBase(level, 22008);
        CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(city.getCenter());
        helper.assertTrue(!ai.isDead(level) && !ai.hasOperationalCore(),
                "An uninitialized city's assumed-alive status must not authorize patrols");
        helper.assertTrue(level.getChunkSource().getChunkNow(city.getCenter().x, city.getCenter().z) == null,
                "Checking core presence must not generate a previously unvisited base");
        BlockPos first = installCore(level, city);
        BlockPos second = first.east();
        level.setBlockAndUpdate(second, AICORE.get().defaultBlockState());
        try {
            helper.assertTrue(ai.hasOperationalCore(), "Real cores must authorize patrols before full city initialization");
            CompoundTag saved = new CompoundTag();
            ai.writeToNBT(saved);
            CityAI restored = new CityAI(level, city.getCenter());
            restored.readFromNBT(saved);
            helper.assertTrue(restored.hasOperationalCore(), "Verified core locations must survive city AI reloads");
            destroyCore(level, ai, first);
            helper.assertTrue(ai.hasOperationalCore(), "One surviving core must keep a base operational");
            destroyCore(level, ai, second);
            helper.assertTrue(!ai.hasOperationalCore(), "Destroying the last core must disable patrol eligibility");
            saved = new CompoundTag();
            ai.writeToNBT(saved);
            helper.assertTrue(saved.getList("cores", 10).isEmpty(), "Destroyed core locations must not revive after their chunks unload");
            restored.readFromNBT(saved);
            helper.assertTrue(!restored.hasOperationalCore(), "A coreless base must stay ineligible after reload");
            level.setBlockAndUpdate(second, AICORE.get().defaultBlockState());
            helper.assertTrue(ai.hasOperationalCore(), "An actual replacement core must restore patrol eligibility");
        } finally {
            level.removeBlock(first, false);
            level.removeBlock(second, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void routePlanningExcludesDestroyedAndUnconfirmedBases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        City source = findLargeBase(level, 28008);
        List<City> candidates = ScoutRoutes.nearbyBases(level, source.getCenter(), 2).stream()
                .filter(city -> !city.getCenter().equals(source.getCenter())).toList();
        helper.assertTrue(candidates.size() >= 2, "Route fixture needs two neighboring bases");
        City active = candidates.get(0);
        City captured = candidates.get(1);
        BlockPos sourceCore = installCore(level, source);
        BlockPos activeCore = installCore(level, active);
        BlockPos capturedCore = installCore(level, captured);
        CityAI capturedAI = CityAISystem.getCityAISystem(level).getCityAI(captured.getCenter());
        capturedAI.hasOperationalCore();
        destroyCore(level, capturedAI, capturedCore);
        Vec3 start = ScoutRoutes.aboveBase(level, source);
        boolean travel = false;
        try {
            for (int seed = 0; seed < 32; seed++) {
                ScoutRoutes.FlightPlan plan = ScoutRoutes.create(level, source.getCenter(), start, RandomSource.create(seed * 198491317L));
                helper.assertTrue(plan.destination().equals(source.getCenter()) || plan.destination().equals(active.getCenter()),
                        "New flights may use the active neighbor or survey home, never a destroyed or unconfirmed base");
                travel |= plan.destination().equals(active.getCenter());
            }
            helper.assertTrue(travel, "An operational neighboring base must remain available for inter-base patrols");
            CityAI activeAI = CityAISystem.getCityAISystem(level).getCityAI(active.getCenter());
            destroyCore(level, activeAI, activeCore);
            for (int seed = 0; seed < 16; seed++) {
                ScoutRoutes.FlightPlan plan = ScoutRoutes.create(level, source.getCenter(), start, RandomSource.create(seed));
                helper.assertTrue(plan.destination().equals(source.getCenter()) && plan.waypoints().size() == 9,
                        "Without an operational neighbor, scouts must keep making local loops from their live base");
            }
        } finally {
            level.removeBlock(sourceCore, false);
            level.removeBlock(activeCore, false);
            level.removeBlock(capturedCore, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flightsToDestroyedCoresFinishTheirSavedRouteThenRetireWithoutNewDepartures(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        City source = findLargeBase(level, 34008);
        City destination = ScoutRoutes.nearbyBases(level, source.getCenter(), 2).stream()
                .filter(city -> !city.getCenter().equals(source.getCenter())).findFirst().orElseThrow();
        BlockPos sourceCore = installCore(level, source);
        BlockPos destinationCore = installCore(level, destination);
        Vec3 start = ScoutRoutes.aboveBase(level, source);
        Vec3 finish = ScoutRoutes.aboveBase(level, destination);
        FluxShipEntity ship = FluxShipEntity.create(level, start.x, start.y, start.z);
        SoldierEntity soldier = SoldierEntity.create(level, source.getCenter(), SoldierBehaviourType.SOLDIER_FIGHTER);
        soldier.startScout(new ScoutPilot(source.getCenter(), new ScoutRoutes.FlightPlan(destination.getCenter(), List.of(start.lerp(finish, .5), finish))));
        ship.getPersistentData().putBoolean(ScoutPatrols.SCOUT_SHIP, true);
        level.addFreshEntity(ship);
        level.addFreshEntity(soldier);
        soldier.startRiding(ship, true);
        ship.tickCount = 1;
        ServerPlayer observer = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CoreRouteFixture"));
        observer.setGameMode(GameType.CREATIVE);
        observer.setPos(start.add(20, 0, 0));
        level.players().add(observer);
        ScoutPatrols scheduler = ScoutPatrols.get(level);
        scheduler.heartbeat(source.getCenter(), ship.getUUID(), level.getGameTime());
        try {
            CityAI destinationAI = CityAISystem.getCityAISystem(level).getCityAI(destination.getCenter());
            destinationAI.hasOperationalCore();
            destroyCore(level, destinationAI, destinationCore);
            soldier.controlShip(ship);
            CompoundTag route = soldier.getScoutPilot().save();
            helper.assertTrue(!ship.isRemoved() && route.getLong("destination") == destination.getCenter().toLong()
                            && route.getList("route", 10).size() == 2,
                    "Losing the destination core must not cancel or replace an in-flight route");
            soldier.startScout(ScoutPilot.load(route));
            soldier.getScoutPilot().divert(ship, List.of(observer));
            route = soldier.getScoutPilot().save();
            route.putLong("divertUntil", level.getGameTime() - 1);
            soldier.startScout(ScoutPilot.load(route));
            observer.setPos(start.add(1000, 0, 0));
            soldier.controlShip(ship);
            helper.assertTrue(soldier.getScoutPilot().getPhase() == ScoutPilot.Phase.PATROL
                            && soldier.getScoutPilot().save().getLong("destination") == destination.getCenter().toLong(),
                    "Reload and traffic diversion must preserve the grandfathered destination");
            route = soldier.getScoutPilot().save();
            route.putInt("waypoint", 2);
            ship.setPos(finish);
            observer.setPos(finish.add(20, 0, 0));
            soldier.startScout(ScoutPilot.load(route));
            soldier.controlShip(ship);
            helper.assertTrue(!ship.isRemoved() && soldier.getScoutPilot().getPhase() == ScoutPilot.Phase.RETIRING,
                    "A visible arrival at a captured base must retire rather than launch another patrol or disappear in view");
            soldier.startScout(ScoutPilot.load(soldier.getScoutPilot().save()));
            observer.setPos(finish.add(1000, 0, 0));
            soldier.controlShip(ship);
            helper.assertTrue(ship.isRemoved() && soldier.isRemoved()
                            && scheduler.heartbeat(source.getCenter(), UUID.randomUUID(), level.getGameTime()),
                    "A finished patrol must leave service and release its original base lease once out of view");
        } finally {
            level.players().remove(observer);
            scheduler.release(source.getCenter(), ship.getUUID());
            ScoutNavigation.releaseFlight(level, ship.getUUID(), ship.chunkPosition());
            soldier.discard();
            ship.discard();
            level.removeBlock(sourceCore, false);
            level.removeBlock(destinationCore, false);
        }
        helper.succeed();
    }

    private static City findLargeBase(ServerLevel level, int start) {
        for (int i = 0; i < 1000; i++) {
            ChunkPos center = new ChunkPos(start + i * 16, start);
            if (CityTools.isCityCenter(level.getSeed(), center.x, center.z)) {
                City city = CityTools.getCity(level.getSeed(), center);
                if (city.getPlan().getName().equals("city2_1")) {
                    return city;
                }
            }
        }
        throw new AssertionError("No large base for the core fixture");
    }

    private static BlockPos installCore(ServerLevel level, City city) {
        ChunkPos center = city.getCenter();
        BlockPos pos = new BlockPos(center.getMinBlockX() + 8,
                CityTools.getLowestHeight(level, city, center.x, center.z) + 1, center.getMinBlockZ() + 8);
        level.setBlockAndUpdate(pos, AICORE.get().defaultBlockState());
        return pos;
    }

    private static void destroyCore(ServerLevel level, CityAI ai, BlockPos pos) {
        ai.breakAICore(level, pos);
        level.removeBlock(pos, false);
    }
}
