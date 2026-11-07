package mcjty.arienteworld;

import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.entities.boss.CoreGuardianEntity;
import mcjty.arienteworld.entities.drone.DroneEntity;
import mcjty.arienteworld.entities.drone.SentinelDroneEntity;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class CityReliabilityTests {

    @GameTest(template = "empty")
    public static void populationCountsOnlyLivingUnitsOwnedByTheCity(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        City city = findCity(level, "city5_1", 68008);
        ChunkPos center = city.getCenter();
        ChunkPos neighbor = new ChunkPos(center.x + 16, center.z);
        BlockPos pos = new BlockPos(center.getMiddleBlockX(), 200, center.getMiddleBlockZ());
        List<Mob> units = new ArrayList<>();
        try {
            units.add(DroneEntity.create(level, center));
            units.add(SentinelDroneEntity.create(level, 0, center));
            units.add(DroneEntity.create(level, neighbor));
            units.add(SentinelDroneEntity.create(level, 0, neighbor));
            units.add(Registration.ENTITY_DRONE.get().create(level));
            units.add(Registration.ENTITY_SENTINEL_DRONE.get().create(level));
            units.add(DroneEntity.create(level, center));
            units.add(SentinelDroneEntity.create(level, 1, center));
            for (int i = 0; i < units.size(); i++) {
                Mob mob = units.get(i);
                mob.setNoAi(true);
                mob.setPos(Vec3.atBottomCenterOf(i == 2 || i == 3 ? pos.east(256) : pos.east(i)));
                level.getChunkAt(mob.blockPosition());
                level.addFreshEntity(mob);
            }
            units.get(6).setHealth(0);
            units.get(7).setHealth(0);
            CityAI ai = new CityAI(level, center);
            Class<?> counterType = Class.forName(CityAI.class.getName() + "$EntityInfo");
            Constructor<?> constructor = counterType.getDeclaredConstructor(CityAI.class);
            constructor.setAccessible(true);
            Object counts = constructor.newInstance(ai);
            for (String name : List.of("getCountDrone", "getCountSentinel")) {
                Method method = counterType.getDeclaredMethod(name, Level.class);
                method.setAccessible(true);
                helper.assertTrue((int) method.invoke(counts, level) == 1,
                        "Foreign, unbound and dying units must not suppress reinforcements: " + name);
            }
            CompoundTag droneTag = new CompoundTag();
            units.get(0).addAdditionalSaveData(droneTag);
            DroneEntity restoredDrone = Registration.ENTITY_DRONE.get().create(level);
            restoredDrone.readAdditionalSaveData(droneTag);
            CompoundTag sentinelTag = new CompoundTag();
            units.get(1).addAdditionalSaveData(sentinelTag);
            SentinelDroneEntity restoredSentinel = Registration.ENTITY_SENTINEL_DRONE.get().create(level);
            restoredSentinel.readAdditionalSaveData(sentinelTag);
            helper.assertTrue(center.equals(restoredDrone.getCityCenter()) && center.equals(restoredSentinel.getCityCenter()),
                    "Population ownership survives entity serialization");
        } finally {
            units.forEach(Mob::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void cubeRecoversMissingGuardianWithoutDuplicatingUnloadedEntities(GameTestHelper helper) {
        guardianRecovery(helper, "city5_1", 70008);
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void undergroundRecoversMissingGuardianWithoutDuplicatingUnloadedEntities(GameTestHelper helper) {
        guardianRecovery(helper, "city3_1", 72008);
    }

    private static void guardianRecovery(GameTestHelper helper, String plan, int start) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City city = findCity(level, plan, start);
        ChunkPos center = city.getCenter();
        int floor = CityTools.getCityHeight(level, city);
        BlockPos core = new BlockPos(center.getMiddleBlockX(), floor + 27, center.getMiddleBlockZ());
        BlockPos home = plan.equals("city3_1")
                ? new BlockPos(center.getMinBlockX() - 24, floor - 22, center.getMinBlockZ() + 8)
                : core.above(8);
        ChunkPos homeChunk = new ChunkPos(home);
        level.setChunkForced(homeChunk.x, homeChunk.z, true);
        level.getChunkAt(home);
        CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(center);
        ai.ensureGuardian(core);
        CompoundTag saved = new CompoundTag();
        ai.writeToNBT(saved);
        helper.assertTrue(saved.hasUUID("guardianId"), "The encounter must initially spawn a guardian");
        UUID id = saved.getUUID("guardianId");
        helper.runAfterDelay(20, () -> {
            CoreGuardianEntity original = (CoreGuardianEntity) level.getEntity(id);
            original.discard();
            ChunkPos unloaded = new ChunkPos(center.x + 30, center.z + 30);
            ai.guardianMoved(id, unloaded);
            CompoundTag position = new CompoundTag();
            ai.writeToNBT(position);
            CityAI restored = new CityAI(level, center);
            restored.readFromNBT(position);
            CompoundTag roundTrip = new CompoundTag();
            restored.writeToNBT(roundTrip);
            helper.assertTrue(roundTrip.getLong("guardianChunk") == unloaded.toLong() && id.equals(roundTrip.getUUID("guardianId")),
                    "The last guardian chunk and encounter identity survive saving");
            helper.runAfterDelay(120, () -> {
                ai.ensureGuardian(core);
                helper.assertTrue(level.getEntity(id) == null && !level.areEntitiesLoaded(unloaded.toLong()),
                        "An unloaded entity chunk must prevent replacement without being force-loaded");
                ai.guardianMoved(id, homeChunk);
                ai.ensureGuardian(core);
                helper.runAfterDelay(40, () -> {
                    ai.ensureGuardian(core);
                    helper.assertTrue(level.getEntity(id) == null, "Recovery must allow an entity-loading grace period");
                });
                helper.runAfterDelay(130, () -> {
                    ai.ensureGuardian(core);
                    CoreGuardianEntity replacement = (CoreGuardianEntity) level.getEntity(id);
                    helper.assertTrue(replacement != null && replacement != original,
                            "A genuinely missing guardian must be recreated with its original encounter UUID");
                    replacement.setNoAi(true);
                    for (int i = 0; i < 20; i++) {
                        ai.ensureGuardian(core);
                    }
                    helper.assertTrue(level.getEntitiesOfClass(CoreGuardianEntity.class, new AABB(home).inflate(60),
                            guardian -> guardian.getUUID().equals(id)).size() == 1,
                            "Repeated core ticks must retain exactly one guardian");
                    replacement.hurt(level.damageSources().genericKill(), 10000);
                    helper.assertTrue(!ai.isCoreProtected(), "Defeating a recovered guardian releases the core normally");
                    replacement.discard();
                    ai.ensureGuardian(core);
                    helper.assertTrue(level.getEntity(id) == null, "A defeated encounter must never recover another guardian");
                    CoreGuardianEntity obsolete = Registration.ENTITY_CORE_GUARDIAN.get().create(level);
                    obsolete.setUUID(id);
                    obsolete.bindToCity(center, home);
                    level.addFreshEntity(obsolete);
                    obsolete.tick();
                    helper.assertTrue(obsolete.isRemoved(), "An obsolete restoration after conquest is discarded without another reward");
                    level.setChunkForced(homeChunk.x, homeChunk.z, false);
                    helper.succeed();
                });
            });
        });
    }

    private static City findCity(ServerLevel level, String plan, int start) {
        for (int x = start; x < start + 1024; x += 16) {
            for (int z = start; z < start + 1024; z += 16) {
                City city = CityTools.getCity(level.getSeed(), new ChunkPos(x, z));
                if (city.getPlan().getName().equals(plan)) {
                    return city;
                }
            }
        }
        throw new IllegalStateException("No fixture for " + plan);
    }
}
