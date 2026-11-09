package mcjty.arienteworld;

import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityPlan;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = ArienteWorld.MODID)
public class SoldierBalanceTests {

    @SubscribeEvent
    public static void functions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> {
            registry.register(Identifier.parse("arienteworld:small_dungeon_natural_packs"), SoldierBalanceTests::naturalPacksAreReducedOnlyInsideSmallDungeons);
            registry.register(Identifier.parse("arienteworld:small_dungeon_reinforcements"), helper -> {
                try {
                    actualAlertWavesAreHalvedForEveryPlayerCount(helper);
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot check soldier reinforcement counts", e);
                }
            });
        });
    }

    @SubscribeEvent
    public static void tests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(Identifier.parse("arienteworld:soldier_balance"));
        event.registerTest(Identifier.parse("arienteworld:small_dungeon_natural_packs"), data ->
                new FunctionGameTestInstance(ResourceKey.create(
                        Registries.TEST_FUNCTION, Identifier.parse("arienteworld:small_dungeon_natural_packs")), data),
                new TestData<>(environment, Identifier.parse("arienteworld:empty"), 600, 0, true));
        event.registerTest(Identifier.parse("arienteworld:small_dungeon_reinforcements"), data ->
                new FunctionGameTestInstance(ResourceKey.create(
                        Registries.TEST_FUNCTION, Identifier.parse("arienteworld:small_dungeon_reinforcements")), data),
                new TestData<>(environment, Identifier.parse("arienteworld:empty"), 600, 0, true));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }

    private static final Set<String> SMALL_PLANS = Set.of("city0_0", "city0_1", "city1_1", "city1_2", "city2_1", "city4_1");

    public static void naturalPacksAreReducedOnlyInsideSmallDungeons(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        var defaults = biomes.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(ArienteWorld.MODID, "ariente_plains"))).value()
                .getMobSettings().getMobs(MobCategory.MONSTER);
        var citySpawns = biomes.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(ArienteWorld.MODID, "ariente_city"))).value()
                .getMobSettings().getMobs(MobCategory.MONSTER);
        int checked = 0;
        for (CityPlan plan : AssetRegistries.CITYPLANS.getIterable()) {
            if (!plan.isCity()) {
                continue;
            }
            City city = findCity(level.getSeed(), plan);
            ChunkPos chunk = city.getCenter();
            List<BuildingPart> parts = CityTools.getBuildingParts(level.getSeed(), city, chunk.x(), chunk.z());
            int bottom = CityTools.getLowestHeight(level, city, chunk.x(), chunk.z());
            int top = bottom + parts.stream().mapToInt(BuildingPart::getSliceCount).sum();
            BlockPos pos = new BlockPos(chunk.getMinBlockX() + 8, bottom + 1, chunk.getMinBlockZ() + 8);
            boolean small = SMALL_PLANS.contains(plan.getName());
            check(helper, plan.isSmallDungeon() == small, "Only small surface and floating bases must be reduced: " + plan.getName());
            for (int y = bottom + 1; y < top; y += 5) {
                var entries = EventHooks.getPotentialSpawns(level, MobCategory.MONSTER, pos.atY(y), defaults).unwrap();
                if (small) {
                    check(helper, entries.size() == citySpawns.unwrap().size(), "Keep the datapack's spawn entries");
                    for (int i = 0; i < entries.size(); i++) {
                        var entry = entries.get(i);
                        var original = citySpawns.unwrap().get(i);
                        check(helper, entry.value().type() == original.value().type() && entry.weight() == original.weight()
                                        && entry.value().minCount() == 1 && entry.value().maxCount() == 1,
                                "Small dungeons must use single-soldier packs without changing species or weights");
                    }
                } else {
                    check(helper, entries.equals(citySpawns.unwrap()), "Large dungeons must retain their complete original spawn table");
                }
                var repeated = EventHooks.getPotentialSpawns(level, MobCategory.MONSTER, pos.atY(y), defaults).unwrap();
                check(helper, entries.get(0) == repeated.get(0), "Pack validation must recognize the selected spawn entry");
            }
            for (int y : new int[]{bottom - 1, top}) {
                check(helper, EventHooks.getPotentialSpawns(level, MobCategory.MONSTER, pos.atY(y), defaults).unwrap().equals(defaults.unwrap()),
                        "The dungeon balance must not change spawning outside its vertical bounds");
            }
            checked++;
        }
        check(helper, checked == 8, "Check all eight shipped dungeon plans");
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    public static void actualAlertWavesAreHalvedForEveryPlayerCount(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        BlockPos post = helper.absolutePos(new BlockPos(8, 180, 8));
        level.getChunkAt(post);
        AABB bounds = new AABB(post).inflate(3);
        Class<?> counterType = Class.forName(CityAI.class.getName() + "$EntityInfo");
        var constructor = counterType.getDeclaredConstructor(CityAI.class);
        constructor.setAccessible(true);
        var handle = CityAI.class.getDeclaredMethod("handleSoldiers", Level.class, counterType);
        handle.setAccessible(true);
        Field randomField = field(CityAI.class, "random");
        Random originalRandom = (Random) randomField.get(null);
        try {
            for (CityPlan plan : AssetRegistries.CITYPLANS.getIterable()) {
                if (!plan.isCity()) {
                    continue;
                }
                CityAI ai = new CityAI(level, findCity(level.getSeed(), plan).getCenter());
                ((Map<BlockPos, Direction>) field(CityAI.class, "soldierPositions").get(ai))
                        .put(post, Direction.NORTH);
                for (int players = 1; players <= 3; players++) {
                    Map<UUID, BlockPos> watching = (Map<UUID, BlockPos>) field(CityAI.class, "watchingPlayers").get(ai);
                    watching.clear();
                    for (int i = 0; i < players; i++) {
                        watching.put(UUID.randomUUID(), post);
                    }
                    for (boolean high : new boolean[]{false, true}) {
                        for (boolean wave : new boolean[]{false, true}) {
                            randomField.set(null, new Random(1) {

                                @Override
                                public float nextFloat() {
                                    return wave ? 0 : 1;
                                }
                            });
                            field(CityAI.class, "onAlert").setInt(ai, 100);
                            field(CityAI.class, "soldierTicker").setInt(ai, 0);
                            field(CityAI.class, "highAlert").setBoolean(ai, high);
                            Object counter = constructor.newInstance(ai);
                            field(counterType, "countSoldier").setInt(counter, 0);
                            int configured = wave
                                    ? players == 1 ? plan.getSoldiersWaveMax1() : players == 2 ? plan.getSoldiersWaveMax2() : plan.getSoldiersWaveMaxN()
                                    : players == 1 ? plan.getSoldiersMinimum1() : players == 2 ? plan.getSoldiersMinimum2() : plan.getSoldiersMinimumN();
                            int expected = configured * (high ? 2 : 1);
                            if (SMALL_PLANS.contains(plan.getName())) {
                                expected /= 2;
                            }
                            handle.invoke(ai, level, counter);
                            var spawned = level.getEntitiesOfClass(SoldierEntity.class, bounds);
                            check(helper, spawned.size() == expected,
                                    "Incorrect actual reinforcement count in " + plan.getName() + ", players=" + players + ", high=" + high
                                            + ", wave=" + wave + ": expected " + expected + ", got " + spawned.size());
                            spawned.forEach(SoldierEntity::discard);
                        }
                    }
                }
            }
        } finally {
            randomField.set(null, originalRandom);
            level.getEntitiesOfClass(SoldierEntity.class, bounds).forEach(SoldierEntity::discard);
        }
        helper.succeed();
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static City findCity(long seed, CityPlan plan) {
        for (int i = 0; i < 1000; i++) {
            ChunkPos center = new ChunkPos(24008 + i * 16, 24008);
            if (CityTools.isCityCenter(seed, center.x(), center.z())) {
                City city = CityTools.getCity(seed, center);
                if (city.getPlan() == plan) {
                    return city;
                }
            }
        }
        throw new AssertionError("No dungeon found for " + plan.getName());
    }
}
