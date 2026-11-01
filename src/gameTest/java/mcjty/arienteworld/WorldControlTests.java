package mcjty.arienteworld;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.config.Config;
import mcjty.arienteworld.config.WorldgenConfiguration;
import mcjty.arienteworld.commands.ModCommands;
import mcjty.arienteworld.dimension.ArienteLandscapeCity;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.PortalGenerator;
import mcjty.arienteworld.dimension.terrain.ArienteTerrainGenerator;
import mcjty.arienteworld.dimension.terrain.BiomeProfile;
import mcjty.arienteworld.dimension.terrain.TerrainBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static mcjty.arienteworld.setup.Registration.BLUELEAVES;
import static mcjty.arienteworld.setup.Registration.BLUELOG;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class WorldControlTests {

    @GameTest(template = "empty")
    public static void cityChanceSearchAndSeedIsolation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        long seed = level.getSeed();
        ChunkPos center = CityTools.getNearestDungeonCenter(-1, -17);
        City original = CityTools.getCity(seed, center);
        try (TestConfig ignored = new TestConfig()) {
            WorldgenConfiguration.CITY_DUNGEON_CHANCE.set(0.0);
            helper.assertTrue(!CityTools.isDungeonChunk(seed, center.x, center.z)
                            && CityTools.getBuildingParts(seed, original, center.x, center.z).isEmpty()
                            && ArienteWorld.arienteWorldImplementation.getNearestCityCenter(center) == null,
                    "Chance zero must disable placement, parts and the public city lookup");
            int loaded = level.getChunkSource().getLoadedChunksCount();
            var commands = level.getServer().getCommands();
            var source = level.getServer().createCommandSourceStack();
            helper.assertTrue(commands.performPrefixedCommand(source, "arworld findcity") == 0
                            && commands.performPrefixedCommand(source, "arworld findcity city4_1") == 0,
                    "City search must not advertise disabled complexes");
            helper.assertTrue(commands.performPrefixedCommand(source, "arworld findcity station") == 1,
                    "Station search must remain available when complexes are disabled");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded, "Search must not load chunks");
            WorldgenConfiguration.CITY_DUNGEON_CHANCE.set(1.0);
            boolean presentAtCenter = CityTools.isCityCenter(seed, center.x, center.z);
            helper.assertTrue((presentAtCenter || original.getPlan().getName().equals("city5_1"))
                            && (presentAtCenter ? center.equals(ArienteWorld.arienteWorldImplementation.getNearestCityCenter(center))
                            : ArienteWorld.arienteWorldImplementation.getNearestCityCenter(center) == null),
                    "Chance one must enable all ordinary centers and retain the huge cube's additional rarity");
            City underground = null;
            for (int x = -8; x <= 8 && underground == null; x++) {
                for (int z = -8; z <= 8; z++) {
                    City candidate = CityTools.getCity(seed, new ChunkPos(x * 16 + 8, z * 16 + 8));
                    if (candidate.getPlan().isUnderground()) {
                        underground = candidate;
                        break;
                    }
                }
            }
            helper.assertTrue(underground != null, "The station test needs an original underground complex");
            ChunkPos occupied = underground.getCenter();
            ChunkPos station = ModCommands.findStation(level, occupied.getWorldPosition());
            helper.assertTrue(CityTools.getStationPart(seed, occupied.x, occupied.z) == null
                            && station != null && !station.equals(occupied),
                    "Station search must skip slots occupied by active underground complexes");
            WorldgenConfiguration.CITY_DUNGEON_CHANCE.set(0.0);
            helper.assertTrue(CityTools.getStationPart(seed, occupied.x, occupied.z) != null,
                    "Disabling the underground complex must restore the independent station slot");
            boolean[] sparse = new boolean[1024];
            WorldgenConfiguration.CITY_DUNGEON_CHANCE.set(.35);
            int sparseCount = 0;
            for (int i = 0; i < sparse.length; i++) {
                int x = (i % 32 - 16) * 16 + 8;
                int z = (i / 32 - 16) * 16 + 8;
                sparse[i] = CityTools.isCityCenter(seed, x, z);
                sparseCount += sparse[i] ? 1 : 0;
            }
            WorldgenConfiguration.CITY_DUNGEON_CHANCE.set(.7);
            int denseCount = 0;
            int differentSeed = 0;
            for (int i = sparse.length - 1; i >= 0; i--) {
                int x = (i % 32 - 16) * 16 + 8;
                int z = (i / 32 - 16) * 16 + 8;
                boolean present = CityTools.isCityCenter(seed, x, z);
                differentSeed += present != CityTools.isCityCenter(seed + 1, x, z) ? 1 : 0;
                helper.assertTrue(!sparse[i] || present, "Increasing chance must keep already selected centers");
                helper.assertTrue(present == CityTools.isCityCenter(seed, x, z), "Another seed must not alter this seed's presence");
                helper.assertTrue(!CityTools.isCityCenter(seed, x - 1, z), "Only the sixteen-chunk grid may hold complex centers");
                denseCount += present ? 1 : 0;
            }
            helper.assertTrue(sparseCount > 250 && sparseCount < 450 && denseCount > 600 && denseCount < 820 && differentSeed > 100,
                    "Sampled presence must follow configured probability and the world seed: " + sparseCount + "/" + denseCount);
            City repeated = CityTools.getCity(seed, center);
            helper.assertTrue(original.getPlan() == repeated.getPlan() && original.getName().equals(repeated.getName()),
                    "Presence changes must not reshuffle selected plans or names");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void hugeCubeFrequencyIsHalved(GameTestHelper helper) {
        try (TestConfig ignored = new TestConfig()) {
            for (double chance : new double[]{.35, .7, 1}) {
                WorldgenConfiguration.CITY_DUNGEON_CHANCE.set(chance);
                int candidates = 0;
                int cubes = 0;
                for (long seed : new long[]{123, -17, 91823}) {
                    for (int dx = -32; dx < 32; dx++) {
                        for (int dz = -32; dz < 32; dz++) {
                            int x = dx * 16 + 8;
                            int z = dz * 16 + 8;
                            Random original = new Random(seed + x * 776531419L + z * 198491317L);
                            original.nextFloat();
                            original.nextFloat();
                            boolean previouslyPresent = original.nextFloat() < chance;
                            boolean present = CityTools.isCityCenter(seed, x, z);
                            if (CityTools.getCity(seed, new ChunkPos(x, z)).getPlan().getName().equals("city5_1")) {
                                candidates += previouslyPresent ? 1 : 0;
                                cubes += present ? 1 : 0;
                                helper.assertTrue(!present || previouslyPresent, "Cube rarity must only remove existing sites");
                            } else {
                                helper.assertTrue(present == previouslyPresent, "Other dungeon types must keep their original frequency and sites");
                            }
                        }
                    }
                }
                helper.assertTrue(candidates > 300 && cubes > candidates * .45 && cubes < candidates * .55,
                        "Huge cube count must be half of the previous count: " + cubes + "/" + candidates);
                ArienteWorld.setup.getLogger().info("Huge cube frequency at chance {}: {} / {} original sites", chance, cubes, candidates);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void freshGenerationRespectsChanceWithoutRemovingStationsAndPortals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        try (TestConfig ignored = new TestConfig()) {
            for (double chance : new double[]{0, 1}) {
                WorldgenConfiguration.CITY_DUNGEON_CHANCE.set(chance);
                City city = smallCity(level, chance == 0 ? 16008 : 24008);
                ChunkPos cp = city.getCenter();
                helper.assertTrue(!level.hasChunk(cp.x, cp.z), "The chance fixture must use fresh chunks");
                var chunk = level.getChunk(cp.x, cp.z);
                boolean core = chunk.getBlockEntitiesPos().stream().anyMatch(pos -> level.getBlockState(pos).is(Registration.AICORE.get()));
                helper.assertTrue(core == (chance == 1), "Actual fresh complex blocks must follow the configured chance");
                BlockPos beam = new BlockPos(cp.getMiddleBlockX(), 32, (cp.z - 6) * 16 + 8);
                level.getChunkAt(beam);
                helper.assertTrue(level.getBlockState(beam).is(Registration.FLUX_BEAM.get()),
                        "The intercity network must survive disabled complexes");
                if (chance == 0) {
                    var station = CityTools.getStationPart(level.getSeed(), cp.x, cp.z);
                    helper.assertTrue(station != null && !level.getBlockState(new BlockPos(cp.getMinBlockX(), 30, cp.getMinBlockZ())).isAir(),
                            "An empty complex region must retain its independent station");
                    BlockPos arrival = PortalGenerator.getArrival(level, cp.getWorldPosition());
                    BlockPos warper = new BlockPos((arrival.getX() & ~15) + 7, arrival.getY() - 1, (arrival.getZ() & ~15) + 7);
                    helper.assertTrue(level.getBlockState(warper).is(Registration.WARPER.get()),
                            "Arrival portals must remain usable when no complexes generate");
                }
            }
        }
        helper.succeed();
    }

    private static City smallCity(ServerLevel level, int origin) {
        for (int i = 0; i < 1024; i++) {
            ChunkPos cp = new ChunkPos(origin + i * 16, 28008);
            City city = CityTools.getCity(level.getSeed(), cp);
            if (city.getPlan().getName().equals("city0_1") && !level.hasChunk(cp.x, cp.z)
                    && !ArienteLandscapeCity.isLandscapeCityChunk(level.getSeed(), cp.x, cp.z)) {
                return city;
            }
        }
        throw new AssertionError("No fresh small-city fixture");
    }

    @GameTest(template = "empty")
    public static void originalSoilAndRoughSurfaceProfiles(GameTestHelper helper) {
        var marble = LegacyBlockStates.read("ariente:marble");
        TerrainBuffer buffer = new TerrainBuffer();
        BiomeProfile[] profiles = new BiomeProfile[256];
        Arrays.fill(profiles, BiomeProfile.FOREST);
        profiles[3 + 12 * 16] = BiomeProfile.ROUGH;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= 90; y++) {
                    buffer.setBlockState(x, y, z, y < 2 ? Blocks.BEDROCK.defaultBlockState() : marble);
                }
            }
        }
        ArienteTerrainGenerator terrain = new ArienteTerrainGenerator();
        terrain.setup(new Random(123), marble);
        terrain.replaceBiomeBlocks(3, -4, buffer, profiles);
        helper.assertTrue(buffer.getBlockState(12, 90, 3).is(Blocks.GRASS_BLOCK)
                        && buffer.getBlockState(12, 89, 3).is(Blocks.DIRT), "Ordinary terrain must retain grass over dirt");
        helper.assertTrue(buffer.getBlockState(3, 90, 12).equals(marble)
                        && buffer.getBlockState(3, 89, 12).equals(marble), "Rough biome must skip surfacing without transposing biome coordinates");
        TerrainBuffer ocean = new TerrainBuffer();
        for (int y = 0; y <= 62; y++) {
            ocean.setBlockState(8, y, 8, y <= 45 ? marble : Blocks.WATER.defaultBlockState());
        }
        ocean.surface(8, 8, new Random(123), 0);
        helper.assertTrue(ocean.getBlockState(8, 45, 8).is(Blocks.GRAVEL), "Deep original ocean floor must retain gravel");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void nativeTreesKeepOriginalTrunksFoliageAndSoil(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = new BlockPos(90528, 150, 90528);
        var features = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        var marble = LegacyBlockStates.read("ariente:marble_smooth@color=white");
        for (String name : List.of("variant_tree", "huge_blue_tree")) {
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -1, -8), origin.offset(8, 45, 8))) {
                level.setBlock(pos, pos.getY() == origin.getY() - 1 ? marble : Blocks.AIR.defaultBlockState(), 2);
            }
            var feature = features.get(new ResourceLocation(ArienteWorld.MODID, name + "_shape"));
            level.setBlock(origin.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            helper.assertTrue(!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(123), origin),
                    "Every two-by-two tree root must validate adjacent soil: " + name);
            helper.assertTrue(level.getBlockState(origin.below().east()).equals(marble), "Failed tree growth must preserve the marble floor");
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    level.setBlock(origin.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                }
            }
            helper.assertTrue(feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(123), origin),
                    "A valid native tree must grow: " + name);
            int branches = 0;
            int vines = 0;
            int blueLeaves = 0;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, 0, -8), origin.offset(8, 45, 8))) {
                var state = level.getBlockState(pos);
                if (state.is(Blocks.VINE)) {
                    vines++;
                }
                if (state.is(BLUELEAVES.get())) {
                    blueLeaves++;
                }
                if (state.is(BLUELOG.get())
                        && (pos.getX() < origin.getX() || pos.getX() > origin.getX() + 1
                        || pos.getZ() < origin.getZ() || pos.getZ() > origin.getZ() + 1)) {
                    branches++;
                }
            }
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    helper.assertTrue(level.getBlockState(origin.offset(x, 0, z)).is(BLUELOG.get()),
                            "Original trees must retain their two-by-two blue trunks");
                }
            }
            helper.assertTrue(blueLeaves > 40 && branches > 0 && (name.equals("variant_tree") || vines > 0),
                    "Original blue canopies, branches and giant-tree vines must be restored: " + name + " " + blueLeaves + "/" + branches + "/" + vines);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flowerPatchesSampleTheOriginalVerticalRange(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        PlacementModifier placement = PlacementModifier.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"arienteworld:flower_height\"}")).getOrThrow(false, message -> {
                    throw new IllegalStateException(message);
                });
        PlacementContext context = new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.empty());
        RandomSource random = RandomSource.create(123);
        boolean below = false;
        boolean above = false;
        for (int i = 0; i < 128; i++) {
            BlockPos sampled = placement.getPositions(context, random, origin).findFirst().orElseThrow();
            helper.assertTrue(sampled.getY() >= 0 && sampled.getY() < surface + 32,
                    "Flower patch height must stay within the original surface-plus-32 range");
            below |= sampled.getY() < surface / 2;
            above |= sampled.getY() >= surface;
        }
        helper.assertTrue(below && above, "Flower patches must sample above and below ground instead of forming heightmap carpets");
        helper.succeed();
    }

    /** Config toggles are confined to an in-memory copy, away from NightConfig's file watcher. */
    private static class TestConfig implements AutoCloseable {

        private final CommentedConfig original;

        private TestConfig() {
            original = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER).stream()
                    .filter(config -> config.getSpec() == Config.SERVER_CONFIG).findFirst().orElseThrow().getConfigData();
            Config.SERVER_CONFIG.setConfig(copy(original));
        }

        private static CommentedConfig copy(UnmodifiableConfig source) {
            CommentedConfig result = CommentedConfig.inMemory();
            for (var entry : source.entrySet()) {
                result.set(List.of(entry.getKey()), entry.getValue() instanceof UnmodifiableConfig nested ? copy(nested) : entry.getValue());
            }
            return result;
        }

        @Override
        public void close() {
            Config.SERVER_CONFIG.setConfig(original);
        }
    }
}
