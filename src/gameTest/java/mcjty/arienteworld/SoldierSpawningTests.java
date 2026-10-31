package mcjty.arienteworld;

import mcjty.ariente.entities.soldier.SoldierEntity;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityPlan;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class SoldierSpawningTests {

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void everyDungeonUsesTheCitySpawnTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        var defaults = biomes.get(new ResourceLocation(ArienteWorld.MODID, "ariente_plains"))
                .getMobSettings().getMobs(MobCategory.MONSTER);
        var citySpawns = biomes.get(new ResourceLocation(ArienteWorld.MODID, "ariente_city"))
                .getMobSettings().getMobs(MobCategory.MONSTER);
        helper.assertTrue(citySpawns.unwrap().stream().anyMatch(entry -> entry.type == Registration.ENTITY_SOLDIER.get())
                        && citySpawns.unwrap().stream().anyMatch(entry -> entry.type == Registration.ENTITY_MASTER_SOLDIER.get()),
                "City biomes must include soldiers and occasional masters");
        int checked = 0;
        for (CityPlan plan : AssetRegistries.CITYPLANS.getIterable()) {
            if (!plan.isCity()) {
                continue;
            }
            City city = findCity(level.getSeed(), plan);
            ChunkPos chunk = city.getCenter();
            List<BuildingPart> parts = CityTools.getBuildingParts(level.getSeed(), city, chunk.x, chunk.z);
            helper.assertTrue(!parts.isEmpty(), "Dungeon center must have authored parts: " + plan.getName());
            int bottom = CityTools.getLowestHeight(level, city, chunk.x, chunk.z);
            int top = bottom + parts.stream().mapToInt(BuildingPart::getSliceCount).sum();
            BlockPos pos = new BlockPos(chunk.getMinBlockX() + 8, bottom + 1, chunk.getMinBlockZ() + 8);
            for (int y = bottom + 1; y < top; y += 5) {
                var spawns = spawnsAt(level, pos.atY(y), MobCategory.MONSTER, defaults);
                helper.assertTrue(spawns.unwrap().equals(citySpawns.unwrap()),
                        "Missing city spawn table in " + plan.getName() + " at height " + y);
                // NaturalSpawner validates each pack member by identity against a second table lookup.
                helper.assertTrue(spawnsAt(level, pos.atY(y), MobCategory.MONSTER, defaults).unwrap().contains(spawns.unwrap().get(0)),
                        "Pack validation must recognize the selected spawn entry");
            }
            helper.assertTrue(spawnsAt(level, pos.atY(bottom - 1), MobCategory.MONSTER, defaults).unwrap().equals(defaults.unwrap())
                            && spawnsAt(level, pos.atY(top), MobCategory.MONSTER, defaults).unwrap().equals(defaults.unwrap()),
                    "Dungeon spawning must stop above and below " + plan.getName());
            helper.assertTrue(spawnsAt(level, pos, MobCategory.CREATURE, WeightedRandomList.create()).isEmpty(),
                    "Dungeon spawning must only replace the hostile table");
            helper.assertTrue(spawnsAt(helper.getLevel(), pos, MobCategory.MONSTER, defaults).unwrap().equals(defaults.unwrap()),
                    "Dungeon spawning must be restricted to the Ariente dimension");
            checked++;
        }
        helper.assertTrue(checked >= 5, "Must cover the surface, floating, underground and huge dungeon plans");
        ArienteWorld.setup.getLogger().info("Soldier spawning checked {} dungeon plans", checked);
        helper.succeed();
    }

    private static City findCity(long seed, CityPlan plan) {
        for (int i = 0; i < 1000; i++) {
            ChunkPos center = new ChunkPos(24008 + i * 16, 24008);
            if (CityTools.isCityCenter(seed, center.x, center.z)) {
                City city = CityTools.getCity(seed, center);
                if (city.getPlan() == plan) {
                    return city;
                }
            }
        }
        throw new AssertionError("No dungeon found for " + plan.getName());
    }

    private static WeightedRandomList<MobSpawnSettings.SpawnerData> spawnsAt(ServerLevel level, BlockPos pos,
                MobCategory category, WeightedRandomList<MobSpawnSettings.SpawnerData> defaults) {
        return ForgeEventFactory.getPotentialSpawns(level, category, pos, defaults);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void soldiersSpawnAtElevenButNotTwelve(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 150, 8));
        for (BlockPos block : BlockPos.betweenClosed(pos.offset(-6, -1, -2), pos.offset(6, 3, 2))) {
            boolean wall = block.getX() == pos.getX() - 6 || block.getX() == pos.getX() + 6
                    || block.getY() == pos.getY() - 1 || block.getY() == pos.getY() + 3
                    || block.getZ() == pos.getZ() - 2 || block.getZ() == pos.getZ() + 2;
            level.setBlock(block, (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
        }
        level.setBlock(pos.west(4), Blocks.GLOWSTONE.defaultBlockState(), 3);
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBrightness(LightLayer.SKY, pos) == 0
                            && level.getBrightness(LightLayer.BLOCK, pos) == 11
                            && level.getBrightness(LightLayer.BLOCK, pos.west()) == 12,
                    "Wait for the enclosed room's light levels to settle");
            for (var type : List.of(Registration.ENTITY_SOLDIER.get(), Registration.ENTITY_MASTER_SOLDIER.get())) {
                SoldierEntity soldier = type.create(level);
                soldier.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                helper.assertTrue(SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, RandomSource.create(1))
                                && ForgeEventFactory.checkSpawnPosition(soldier, level, MobSpawnType.NATURAL),
                        "Both placement and entity checks must accept a soldier at light 11");
                helper.assertTrue(!SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos.west(), RandomSource.create(1)),
                        "Soldiers must reject light 12");
                level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
                helper.assertTrue(!SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, RandomSource.create(1)),
                        "Natural soldiers must still require a supporting floor");
                level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
                Difficulty difficulty = level.getDifficulty();
                try {
                    level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
                    helper.assertTrue(!SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, RandomSource.create(1)),
                            "Soldiers must not spawn in peaceful mode");
                } finally {
                    level.getServer().setDifficulty(difficulty, true);
                }
            }
        });
    }
}
