package mcjty.arienteworld.entities.scout;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.entities.LaserEntity;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.config.AIConfiguration;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static mcjty.ariente.setup.Registration.ENTITY_LASER;
import static mcjty.ariente.setup.Registration.AICORE;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class ScoutPatrolTests {

    @GameTest(template = "empty")
    public static void laserHitsDamageThePilotByHalfAndDefeatedShipsDescendInsteadOfDisappearing(GameTestHelper helper) {
        FluxShipEntity ship = ship(helper);
        SoldierEntity soldier = (SoldierEntity) ship.getControllingPassenger();
        ChunkPos home = soldier.getScoutPilot().getHome();
        ScoutPatrols scheduler = ScoutPatrols.get(helper.getLevel());
        scheduler.heartbeat(home, ship.getUUID(), helper.getLevel().getGameTime());
        LaserEntity laser = ENTITY_LASER.get().create(helper.getLevel());
        try {
            float health = soldier.getHealth();
            DamageSource source = helper.getLevel().damageSources().indirectMagic(laser, helper.makeMockPlayer());
            helper.assertTrue(ship.hurt(source, 8) && Math.abs(soldier.getHealth() - (health - 4)) < .001,
                    "A laser hitting the indestructible hull must pass exactly half of its damage to the pilot");
            helper.assertTrue(ship.isAlive() && !ship.isRemoved(), "The hull must survive the hit");
            soldier.setHealth(0);
            ship.tickCount = 50;
            for (int tick = 0; tick < 70; tick++) {
                ship.tick();
            }
            ScoutPatrols.loadedScouts(helper.getLevel());
            helper.assertTrue(!ship.isRemoved() && ship.getDeltaMovement().y < -.199
                            && !ship.getPersistentData().getBoolean(ScoutPatrols.SCOUT_SHIP),
                    "A dead pilot must leave a recoverable ship descending at four blocks per second, including while its corpse is seated");
            helper.assertTrue(scheduler.heartbeat(home, UUID.randomUUID(), helper.getLevel().getGameTime()),
                    "A defeated patrol must release its base for replacement launches");
        } finally {
            laser.discard();
            soldier.discard();
            ship.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void patrolsDescendBelowLaunchTowersAndBankVisiblyThroughCorners(GameTestHelper helper) {
        FluxShipEntity ship = ship(helper);
        SoldierEntity soldier = (SoldierEntity) ship.getControllingPassenger();
        Vec3 start = ship.position();
        helper.getLevel().getChunkAt(BlockPos.containing(start.add(40, 0, 0)));
        Vec3 cruise = ScoutNavigation.cruiseTarget(helper.getLevel(), start, start.add(200, 80, 0));
        helper.assertTrue(cruise.y < start.y - 30 && cruise.y >= ScoutNavigation.floor(helper.getLevel(), cruise) + 14,
                "A patrol leaving a tall base must target low terrain clearance instead of staying above the roof");
        float maxBank = 0;
        try {
            soldier.startScout(new ScoutPilot(soldier.getScoutPilot().getHome(),
                    new ScoutRoutes.FlightPlan(new ChunkPos(24, 8), List.of(start.add(120, 0, 0), start.add(120, 0, 120)))));
            for (int tick = 0; tick < 120; tick++) {
                ship.tick();
                maxBank = Math.max(maxBank, Math.abs(ship.getBank(1)));
            }
            helper.assertTrue(maxBank >= 20 && maxBank <= 40, "Patrol turns must visibly bank 20 to 40 degrees: " + maxBank);
        } finally {
            cleanup(ship);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void frequentLaunchesUpgradeOldWorldDefaultsAndQueuedCooldowns(GameTestHelper helper) {
        var level = helper.getLevel();
        int configured = AIConfiguration.SCOUT_LAUNCH_INTERVAL.get();
        ChunkPos base = new ChunkPos(8, 8);
        UUID owner = UUID.randomUUID();
        CompoundTag entry = new CompoundTag();
        entry.putLong("base", base.toLong());
        entry.putLong("next", level.getGameTime() + 7200);
        entry.putUUID("ship", owner);
        entry.putLong("active", level.getGameTime());
        ListTag entries = new ListTag();
        entries.add(entry);
        CompoundTag legacy = new CompoundTag();
        legacy.put("bases", entries);
        try {
            AIConfiguration.SCOUT_LAUNCH_INTERVAL.set(180);
            ScoutPatrols data = ScoutPatrols.load(legacy);
            data.upgradeCadence(level);
            helper.assertTrue(AIConfiguration.SCOUT_LAUNCH_INTERVAL.get() == 10
                            && data.save(new CompoundTag()).getList("bases", Tag.TAG_COMPOUND).getCompound(0).getLong("next") <= level.getGameTime() + 20,
                    "Existing worlds must adopt the faster default and discard the old multi-minute wait");
            helper.assertTrue(!data.heartbeat(base, UUID.randomUUID(), level.getGameTime()),
                    "Increasing frequency must preserve the active ship lease and avoid duplicate patrols");
            data.launched(level, base, owner);
            long next = data.save(new CompoundTag()).getList("bases", Tag.TAG_COMPOUND).getCompound(0).getLong("next");
            helper.assertTrue(next >= level.getGameTime() + 200 && next <= level.getGameTime() + 400,
                    "Repeat opportunities must be 10 to 20 seconds apart");
            ScoutPatrols restored = ScoutPatrols.load(data.save(new CompoundTag()));
            restored.upgradeCadence(level);
            helper.assertTrue(restored.save(new CompoundTag()).getList("bases", Tag.TAG_COMPOUND).getCompound(0).getLong("next") == next,
                    "Migration must happen once, preserving new cooldowns across reloads");
            legacy.putInt("cadenceVersion", 1);
            AIConfiguration.SCOUT_LAUNCH_INTERVAL.set(20);
            ScoutPatrols.load(legacy).upgradeCadence(level);
            helper.assertTrue(AIConfiguration.SCOUT_LAUNCH_INTERVAL.get() == 10,
                    "Worlds already upgraded to the previous 20-second default must also adopt the new cadence");
            AIConfiguration.SCOUT_LAUNCH_INTERVAL.set(60);
            ScoutPatrols.load(legacy).upgradeCadence(level);
            helper.assertTrue(AIConfiguration.SCOUT_LAUNCH_INTERVAL.get() == 60, "Explicit custom intervals must be preserved");
            ScoutPatrols fresh = new ScoutPatrols();
            helper.assertTrue(!fresh.ready(level, base)
                            && fresh.save(new CompoundTag()).getList("bases", Tag.TAG_COMPOUND).getCompound(0).getLong("next") <= level.getGameTime() + 20,
                    "A newly approached base must be ready within one second, even with a longer repeat interval");
        } finally {
            AIConfiguration.SCOUT_LAUNCH_INTERVAL.set(configured);
            AIConfiguration.SCOUT_LAUNCH_INTERVAL.save();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aPlayerFlyingPastRealBasesMeetsPatrolsWithinSeconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City source = null;
        int startChunk = 6008 + level.random.nextInt(1000) * 16;
        for (int i = 0; i < 300; i++) {
            ChunkPos center = new ChunkPos(startChunk + i * 16, 6008);
            if (CityTools.isCityCenter(level.getSeed(), center.x, center.z)) {
                City city = CityTools.getCity(level.getSeed(), center);
                if (city.getPlan().getName().equals("city2_1")) {
                    source = city;
                    break;
                }
            }
        }
        if (source == null) {
            throw new AssertionError("No large base for the flyby fixture");
        }
        Vec3 start = ScoutRoutes.aboveBase(level, source).add(-600, 0, 0);
        ServerPlayer player = player(level, start);
        player.setDeltaMovement(5.2, 0, 0);
        int[] firstSeen = {-1};
        boolean enabled = AIConfiguration.SCOUT_PATROLS.get();
        AIConfiguration.SCOUT_PATROLS.set(true);
        for (int tick = 1; tick <= 100; tick++) {
            int elapsed = tick;
            helper.runAfterDelay(tick, () -> {
                player.setPos(start.add(elapsed * 5.2, 0, 0));
                List<FluxShipEntity> nearby = level.getEntitiesOfClass(FluxShipEntity.class, new AABB(player.blockPosition()).inflate(700),
                        ship -> ship.getPersistentData().getBoolean(ScoutPatrols.SCOUT_SHIP));
                if (!nearby.isEmpty() && firstSeen[0] < 0) {
                    firstSeen[0] = elapsed;
                }
                if (elapsed == 100) {
                    try {
                        helper.assertTrue(firstSeen[0] > 0 && firstSeen[0] <= 100,
                                "A 104-block/s flyby must trigger real scheduled patrols within five seconds: first=" + firstSeen[0]);
                        helper.assertTrue(nearby.stream().anyMatch(ship -> ship.distanceToSqr(player) <= 256 * 256),
                                "A fast flyby must actually pass within viewing distance of a patrol");
                        ArienteWorld.setup.getLogger().info("SCOUT FAST FLYBY CHECK PASSED: first scheduled patrol after {} ticks", firstSeen[0]);
                        helper.succeed();
                    } finally {
                        nearby.forEach(ScoutPatrolTests::cleanup);
                        level.players().remove(player);
                        AIConfiguration.SCOUT_PATROLS.set(enabled);
                    }
                }
            });
        }
    }

    @GameTest(template = "empty")
    public static void largerOutdoorBasesSupplyTravelAndSurveyRoutes(GameTestHelper helper) {
        for (var plan : AssetRegistries.CITYPLANS.getIterable()) {
            if (!plan.isCity()) {
                continue;
            }
            boolean expected = Set.of("city2_1", "city4_1", "city5_1").contains(plan.getName());
            helper.assertTrue(ScoutRoutes.canLaunchFrom(new City(new ChunkPos(8, 8), plan, "fixture")) == expected,
                    "Only larger outdoor bases should launch scouts: " + plan.getName());
        }
        ServerLevel level = helper.getLevel();
        City origin = ScoutRoutes.nearbyBases(level, new ChunkPos(8, 8), 2).stream()
                .filter(ScoutRoutes::canLaunchFrom).findFirst().orElseThrow();
        City destination = ScoutRoutes.nearbyBases(level, origin.getCenter(), 2).stream()
                .filter(city -> !city.getCenter().equals(origin.getCenter())).findFirst().orElseThrow();
        BlockPos originCore = installCore(level, origin);
        BlockPos destinationCore = installCore(level, destination);
        Vec3 start = ScoutRoutes.aboveBase(level, origin);
        boolean travel = false;
        boolean survey = false;
        for (int seed = 0; seed < 30; seed++) {
            var route = ScoutRoutes.create(level, origin.getCenter(), start, RandomSource.create(seed * 198491317L));
            if (route.destination().equals(origin.getCenter())) {
                survey = true;
                helper.assertTrue(route.waypoints().size() == 9 && route.waypoints().get(8).equals(start),
                        "Local patrols must make a broad loop back to their origin");
            } else {
                travel = true;
                helper.assertTrue(CityTools.isCityCenter(helper.getLevel().getSeed(), route.destination().x, route.destination().z),
                        "Travel routes must end at a real seed-selected base");
                helper.assertTrue(route.destination().equals(destination.getCenter()),
                        "Travel routes must choose the base with an actual operating core");
            }
        }
        helper.assertTrue(travel && survey, "Scouts need both inter-base flights and local survey loops");
        level.removeBlock(originCore, false);
        level.removeBlock(destinationCore, false);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void coverPreservesTheLastSightingAndTheFullMinuteOfFocusAcrossSaving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FluxShipEntity ship = ship(helper);
        SoldierEntity soldier = (SoldierEntity) ship.getControllingPassenger();
        Vec3 point = ship.position().add(0, -36, 80);
        ServerPlayer target = player(level, point);
        ServerPlayer distraction = null;
        Map<BlockPos, BlockState> roof = new HashMap<>();
        try {
            ship.tickCount = 10;
            ship.tick();
            ScoutPilot pilot = soldier.getScoutPilot();
            helper.assertTrue(target.getUUID().equals(pilot.getTargetId()) && pilot.getPhase() == ScoutPilot.Phase.SETUP,
                    "A visible player must start a stand-off setup run: phase=" + pilot.getPhase() + ", target=" + pilot.getTargetId()
                            + ", expected=" + target.getUUID() + ", visible=" + ScoutPilot.visible(soldier, ship, target)
                            + ", players=" + level.players().size() + ", position=" + ship.position());
            Vec3 sighting = pilot.getLastSeen();
            BlockPos above = BlockPos.containing(point).above(4);
            for (BlockPos pos : BlockPos.betweenClosed(above.offset(-12, 0, -12), above.offset(12, 0, 12))) {
                roof.put(pos.immutable(), level.getBlockState(pos));
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            }
            target.setPos(point.add(4, 0, 0));
            distraction = player(level, ship.position().add(0, 0, 20));
            ship.tick();
            helper.assertTrue(pilot.getPhase() == ScoutPilot.Phase.SEARCH && !pilot.shouldFire(soldier, ship),
                    "An overhead roof must break sight and prevent laser fire");
            helper.assertTrue(pilot.getLastSeen().equals(sighting) && target.getUUID().equals(pilot.getTargetId()),
                    "Hidden movement and a closer player must not update or replace the remembered target");
            CompoundTag saved = pilot.save();
            saved.putLong("seenTime", level.getGameTime() - ScoutPilot.MEMORY_TICKS + 1);
            soldier.startScout(ScoutPilot.load(saved));
            soldier.controlShip(ship);
            helper.assertTrue(target.getUUID().equals(soldier.getScoutPilot().getTargetId()),
                    "A restored pursuit must retain its target at 59.95 seconds out of sight");
            saved.putLong("seenTime", level.getGameTime() - ScoutPilot.MEMORY_TICKS);
            soldier.startScout(ScoutPilot.load(saved));
            ship.tickCount = 11;
            soldier.controlShip(ship);
            helper.assertTrue(soldier.getScoutPilot().getTargetId() == null && soldier.getScoutPilot().getPhase() == ScoutPilot.Phase.PATROL,
                    "A full minute continuously hidden must resume the saved route");
            SoldierEntity restored = Registration.ENTITY_SOLDIER.get().create(level);
            restored.load(soldier.saveWithoutId(new CompoundTag()));
            helper.assertTrue(restored.getScoutPilot() != null && restored.isNoAi() && restored.requiresCustomPersistence(),
                    "The soldier must save its pilot role without reverting to foot-soldier AI or ordinary despawning");
        } finally {
            roof.forEach((pos, state) -> level.setBlock(pos, state, 2));
            level.players().remove(target);
            level.players().remove(distraction);
            cleanup(ship);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aScoutActuallyTurnsDivesFiresAndPullsAwayForAnotherRun(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FluxShipEntity ship = ship(helper);
        SoldierEntity soldier = (SoldierEntity) ship.getControllingPassenger();
        ServerPlayer target = player(level, ship.position().add(0, -36, 80));
        EnumSet<ScoutPilot.Phase> phases = EnumSet.noneOf(ScoutPilot.Phase.class);
        Set<UUID> shots = new HashSet<>();
        try {
            for (int i = 0; i < 900 && !ship.isRemoved(); i++) {
                float yaw = ship.getYRot();
                float pitch = ship.getXRot();
                ship.tickCount++;
                ship.tick();
                ship.positionRider(soldier);
                phases.add(soldier.getScoutPilot().getPhase());
                helper.assertTrue(Math.abs(Mth.wrapDegrees(ship.getYRot() - yaw)) <= 2.401
                                && Math.abs(ship.getXRot() - pitch) <= 2.001,
                        "Steering must have bounded turn and pitch rates through every maneuver");
                for (LaserEntity laser : level.getEntitiesOfClass(LaserEntity.class, new AABB(ship.blockPosition()).inflate(260))) {
                    if (laser.isShipShot() && !laser.isRemoved()) {
                        shots.add(laser.getUUID());
                        helper.assertTrue(laser.getOwner() == soldier, "Cannon kills must credit the soldier pilot");
                        laser.tick();
                    }
                }
            }
            helper.assertTrue(phases.containsAll(EnumSet.of(ScoutPilot.Phase.SETUP, ScoutPilot.Phase.ATTACK, ScoutPilot.Phase.EGRESS))
                            && !shots.isEmpty(),
                    "A real simulated patrol must complete firing passes, not merely select AI states: phases=" + phases + ", shots=" + shots.size()
                            + ", position=" + ship.position() + ", heading=" + ship.getYRot());
            ArienteWorld.setup.getLogger().info("SCOUT ATTACK RUN CHECK: phases={}, shots={}", phases, shots.size());
        } finally {
            level.players().remove(target);
            cleanup(ship);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void trafficDivertsTheFurthestScoutsAndRejectsFurtherLaunches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<FluxShipEntity> fleet = new ArrayList<>();
        ServerPlayer player = player(level, new Vec3(helper.absolutePos(BlockPos.ZERO).getX(), 220, helper.absolutePos(BlockPos.ZERO).getZ()));
        try {
            for (int distance : new int[]{40, 60, 160, 280}) {
                FluxShipEntity ship = ship(helper);
                ship.setPos(player.position().add(distance, 0, 0));
                fleet.add(ship);
            }
            ScoutPatrols.enforceTraffic(fleet, List.of(player));
            for (int i = 0; i < fleet.size(); i++) {
                ScoutPilot pilot = ((SoldierEntity) fleet.get(i).getControllingPassenger()).getScoutPilot();
                helper.assertTrue((pilot.getPhase() == ScoutPilot.Phase.DIVERT) == (i >= 2),
                        "The two nearest scouts keep their runs; further ships divert before entering the nearby zone");
            }
            helper.assertTrue(!ScoutPatrols.hasCapacity(player.position().add(140, 0, 0), fleet, List.of(player)),
                    "Departing ships still count toward the launch cap until they actually leave");
            helper.assertTrue(ScoutPatrols.hasCapacity(player.position().add(140, 0, 0), fleet.subList(0, 1), List.of(player)),
                    "One nearby scout must leave room for one further launch");
            helper.assertTrue(!ScoutPatrols.hasCapacity(player.position().add(140, 0, 0), fleet, List.of(player, player)),
                    "Overlapping player neighborhoods must not multiply the fleet limit");
        } finally {
            level.players().remove(player);
            fleet.forEach(ScoutPatrolTests::cleanup);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arrivalRetiresRemoteShipsButObservedShipsMakeAnotherRunAndLeasesPersist(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FluxShipEntity remote = ship(helper);
        SoldierEntity soldier = (SoldierEntity) remote.getControllingPassenger();
        // Other combat fixtures may still have players nearby; this arrival is deliberately remote.
        remote.setPos(remote.position().add(5000, 0, 5000));
        soldier.setPos(remote.position());
        remote.tickCount = 1;
        soldier.startScout(new ScoutPilot(soldier.getScoutPilot().getHome(), new ScoutRoutes.FlightPlan(new ChunkPos(10016, 10000), List.of())));
        soldier.controlShip(remote);
        helper.assertTrue(remote.isRemoved() && soldier.isRemoved(), "An unobserved arrival must remove ship and pilot together");
        FluxShipEntity observed = ship(helper);
        observed.tickCount = 1;
        SoldierEntity pilot = (SoldierEntity) observed.getControllingPassenger();
        City arrival = ScoutRoutes.nearbyBases(level, new ChunkPos(8, 8), 2).get(0);
        BlockPos core = installCore(level, arrival);
        observed.setPos(ScoutRoutes.aboveBase(level, arrival));
        ServerPlayer spectator = player(level, observed.position().add(20, 0, 0));
        spectator.setGameMode(GameType.CREATIVE);
        try {
            pilot.startScout(new ScoutPilot(pilot.getScoutPilot().getHome(), new ScoutRoutes.FlightPlan(arrival.getCenter(), List.of())));
            pilot.controlShip(observed);
            helper.assertTrue(!observed.isRemoved() && !pilot.isRemoved()
                            && !pilot.getScoutPilot().save().getList("route", Tag.TAG_COMPOUND).isEmpty(),
                    "An observed arrival must start another real route instead of popping out of view");
            ScoutPatrols data = new ScoutPatrols();
            ChunkPos base = new ChunkPos(8, 8);
            helper.assertTrue(!data.ready(level, base), "Discovering a base must schedule a delay instead of instantly spawning a scout");
            ScoutPatrols cooldown = ScoutPatrols.load(data.save(new CompoundTag()));
            helper.assertTrue(!cooldown.ready(level, base), "Base launch cooldowns must survive server reloads");
            helper.assertTrue(data.heartbeat(base, observed.getUUID(), level.getGameTime()), "An unused base accepts a patrol lease");
            ScoutPatrols restored = ScoutPatrols.load(data.save(new CompoundTag()));
            UUID replacement = UUID.randomUUID();
            helper.assertTrue(!restored.heartbeat(base, replacement, level.getGameTime()), "Saved patrol leases must prevent duplicate launch owners");
            restored.release(base, observed.getUUID());
            helper.assertTrue(restored.heartbeat(base, replacement, level.getGameTime()), "A retired patrol releases its base lease");
        } finally {
            level.players().remove(spectator);
            cleanup(observed);
            level.removeBlock(core, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void airbornePlayersAreLedByTheFixedCannonAndCannotBeShotWithTheHullMisaligned(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FluxShipEntity ship = ship(helper);
        SoldierEntity soldier = (SoldierEntity) ship.getControllingPassenger();
        ServerPlayer target = player(level, ship.position().add(0, 24, 96));
        Vec3 velocity = new Vec3(1.6, .25, 0);
        target.setDeltaMovement(velocity);
        try {
            Vec3 center = target.getBoundingBox().getCenter();
            CompoundTag state = soldier.getScoutPilot().save();
            state.putString("phase", "ATTACK");
            state.putUUID("target", target.getUUID());
            state.putLong("seenTime", level.getGameTime());
            CompoundTag seen = new CompoundTag();
            seen.putDouble("x", center.x);
            seen.putDouble("y", center.y);
            seen.putDouble("z", center.z);
            state.put("lastSeen", seen);
            CompoundTag movement = new CompoundTag();
            movement.putDouble("x", velocity.x);
            movement.putDouble("y", velocity.y);
            movement.putDouble("z", velocity.z);
            state.put("velocity", movement);
            soldier.startScout(ScoutPilot.load(state));
            // Independently solve the intercept by narrowing the projectile travel-time interval.
            Vec3 muzzle = ship.position().add(0, .62, 3.12);
            double low = 0;
            double high = 16;
            for (int i = 0; i < 40; i++) {
                double time = (low + high) / 2;
                if (center.add(velocity.scale(time)).distanceTo(muzzle) > time * 9.6) {
                    low = time;
                } else {
                    high = time;
                }
            }
            Vec3 aim = center.add(velocity.scale((low + high) / 2)).subtract(muzzle);
            ship.setYRot((float) (Mth.atan2(-aim.x, aim.z) * Mth.RAD_TO_DEG));
            ship.setXRot((float) (-Mth.atan2(aim.y, aim.horizontalDistance()) * Mth.RAD_TO_DEG));
            ship.positionRider(soldier);
            helper.assertTrue(soldier.shouldFireShipLaser(ship), "A scout must aim ahead of a flying player's horizontal and vertical motion");
            ship.setYRot(ship.getYRot() + 15);
            helper.assertTrue(!soldier.shouldFireShipLaser(ship), "The cannon must not act as a turret while the hull turns into alignment");
        } finally {
            level.players().remove(target);
            cleanup(ship);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aLoadedBaseLaunchesPilotedShipsOutsideTheNaturalSpawningSystem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City source = null;
        for (int i = 0; i < 300; i++) {
            ChunkPos center = new ChunkPos(4008 + i * 16, 4008);
            if (CityTools.isCityCenter(level.getSeed(), center.x, center.z)) {
                City city = CityTools.getCity(level.getSeed(), center);
                if (city.getPlan().getName().equals("city2_1")) {
                    source = city;
                    break;
                }
            }
        }
        if (source == null) {
            throw new AssertionError("No large surface base in fixture seed");
        }
        City base = source;
        ChunkPos chunk = source.getCenter();
        ScoutNavigation.prepareLaunch(level, chunk);
        helper.runAfterDelay(40, () -> {
            Vec3 center = ScoutRoutes.aboveBase(level, base);
            ServerPlayer player = player(level, center.add(64, 0, 0));
            List<FluxShipEntity> fleet = new ArrayList<>();
            Map<BlockPos, BlockState> coreStates = new HashMap<>();
            boolean natural = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
            try {
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
                helper.assertTrue(ScoutPatrols.launch(level, base, fleet, List.of(player)) == null,
                        "A scout must not pop into existence within 128 horizontal blocks of a player");
                player.setPos(center.add(150, 0, 0));
                FluxShipEntity first = ScoutPatrols.launch(level, base, fleet, List.of(player));
                helper.assertTrue(first != null && first.getControllingPassenger() instanceof SoldierEntity soldier
                                && soldier.getScoutPilot() != null && soldier.isNoAi(),
                        "A loaded large base must launch a real soldier-piloted ship even with natural spawning disabled");
                fleet.add(first);
                first.tick();
                helper.assertTrue(first.getHoloGui() == null && first.getDeltaMovement().lengthSqr() > 0,
                        "Scout pilots use the ship's real flight physics and do not display a player cockpit hologram");
                FluxShipEntity second = ScoutPatrols.launch(level, base, fleet, List.of(player));
                helper.assertTrue(second != null, "A second nearby scout is allowed");
                fleet.add(second);
                helper.assertTrue(ScoutPatrols.launch(level, base, fleet, List.of(player)) == null, "A third nearby launch must be rejected");
                fleet.forEach(ScoutPatrolTests::cleanup);
                fleet.clear();
                CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(chunk);
                CompoundTag state = new CompoundTag();
                ai.writeToNBT(state);
                ListTag cores = state.getList("cores", Tag.TAG_COMPOUND);
                helper.assertTrue(!cores.isEmpty(), "The successful launch must be tied to actual generated cores");
                for (int i = 0; i < cores.size(); i++) {
                    BlockPos pos = NbtUtils.readBlockPos(cores.getCompound(i));
                    coreStates.put(pos, level.getBlockState(pos));
                    level.destroyBlock(pos, false);
                }
                // Reproduce a base whose core disappeared before its city AI initialized.
                ai.readFromNBT(new CompoundTag());
                helper.assertTrue(!ai.isDead(level) && ScoutPatrols.hasCapacity(center, fleet, List.of(player))
                                && ScoutPatrols.launch(level, base, fleet, List.of(player)) == null,
                        "A real coreless base must not launch, even with spare traffic capacity and assumed-alive AI state");
            } finally {
                fleet.forEach(ScoutPatrolTests::cleanup);
                coreStates.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
                level.players().remove(player);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(natural, level.getServer());
                ScoutNavigation.releaseLaunch(level, chunk);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void flyingPlayerLeadUsesSightingsWhenNetworkOrRidingMotionIsZero(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FluxShipEntity ship = ship(helper);
        SoldierEntity soldier = (SoldierEntity) ship.getControllingPassenger();
        ServerPlayer target = player(level, ship.position().add(0, 24, 96));
        target.setDeltaMovement(Vec3.ZERO);
        try {
            CompoundTag state = soldier.getScoutPilot().save();
            state.putString("phase", "ATTACK");
            state.putUUID("target", target.getUUID());
            state.putLong("seenTime", level.getGameTime() - 1);
            Vec3 previous = target.getBoundingBox().getCenter().subtract(2, .25, 0);
            CompoundTag sighting = new CompoundTag();
            sighting.putDouble("x", previous.x);
            sighting.putDouble("y", previous.y);
            sighting.putDouble("z", previous.z);
            state.put("lastSeen", sighting);
            soldier.startScout(ScoutPilot.load(state));
            soldier.controlShip(ship);
            CompoundTag motion = soldier.getScoutPilot().save().getCompound("velocity");
            helper.assertTrue(motion.getDouble("x") > .9 && motion.getDouble("y") > .1,
                    "Leading must use actual visible travel even when a flying or mounted player reports zero velocity");
        } finally {
            level.players().remove(target);
            cleanup(ship);
        }
        helper.succeed();
    }

    private static FluxShipEntity ship(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        FluxShipEntity ship = FluxShipEntity.create(level, origin.getX() + .5, 220, origin.getZ() + .5);
        SoldierEntity soldier = Registration.ENTITY_SOLDIER.get().create(level);
        ChunkPos home = new ChunkPos(level.random.nextInt(100000) + 50000, level.random.nextInt(100000) + 50000);
        soldier.startScout(new ScoutPilot(home, new ScoutRoutes.FlightPlan(home, List.of(ship.position().add(0, 0, 400)))));
        ship.getPersistentData().putBoolean(ScoutPatrols.SCOUT_SHIP, true);
        soldier.setPos(ship.position());
        level.addFreshEntity(ship);
        level.addFreshEntity(soldier);
        soldier.startRiding(ship, true);
        ship.positionRider(soldier);
        return ship;
    }

    private static BlockPos installCore(ServerLevel level, City city) {
        ChunkPos center = city.getCenter();
        BlockPos pos = new BlockPos(center.getMinBlockX() + 8,
                CityTools.getLowestHeight(level, city, center.x, center.z) + 1, center.getMinBlockZ() + 8);
        level.setBlockAndUpdate(pos, AICORE.get().defaultBlockState());
        return pos;
    }

    private static ServerPlayer player(ServerLevel level, Vec3 position) {
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ScoutFixture"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(position);
        level.players().add(player);
        return player;
    }

    private static void cleanup(FluxShipEntity ship) {
        ship.level().getEntitiesOfClass(LaserEntity.class, new AABB(ship.blockPosition()).inflate(300))
                .stream().filter(LaserEntity::isShipShot).forEach(LaserEntity::discard);
        if (ship.getControllingPassenger() != null) {
            if (ship.getControllingPassenger() instanceof SoldierEntity soldier && soldier.getScoutPilot() != null) {
                ScoutPatrols.get((ServerLevel) ship.level()).release(soldier.getScoutPilot().getHome(), ship.getUUID());
            }
            ship.getControllingPassenger().discard();
        }
        ship.discard();
    }
}
