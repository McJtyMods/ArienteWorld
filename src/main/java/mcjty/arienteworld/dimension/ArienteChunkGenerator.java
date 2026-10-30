package mcjty.arienteworld.dimension;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.dimension.features.IFeature;
import mcjty.arienteworld.dimension.features.SpheresFeature;
import mcjty.arienteworld.dimension.features.SpikesFeature;
import mcjty.arienteworld.dimension.features.BubbleFeature;
import mcjty.arienteworld.dimension.features.GlowBubbleFeature;
import mcjty.arienteworld.dimension.terrain.ArienteTerrainGenerator;
import mcjty.arienteworld.dimension.terrain.BiomeProfile;
import mcjty.arienteworld.dimension.terrain.IslandsTerrainGenerator;
import mcjty.arienteworld.dimension.terrain.TerrainBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ArienteChunkGenerator extends ChunkGenerator {

    public static final Codec<ArienteChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ArienteBiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.source)
    ).apply(instance, ArienteChunkGenerator::new));

    private final ArienteBiomeSource source;
    private long seed;
    private final Cache<Long, TerrainBuffer> terrain = CacheBuilder.newBuilder().maximumSize(128).build();
    private final ThreadLocal<TerrainGenerators> generators = ThreadLocal.withInitial(() -> new TerrainGenerators(seed));

    public ArienteChunkGenerator(ArienteBiomeSource source) {
        super(source);
        this.source = source;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState random, long seed) {
        this.seed = seed;
        source.initialize(seed);
        return super.createState(structures, random, seed);
    }

    private TerrainBuffer getTerrain(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        TerrainBuffer cached = terrain.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        TerrainGenerators gen = generators.get();
        TerrainBuffer buffer = new TerrainBuffer();
        BiomeProfile[] biomes = new BiomeProfile[100];
        for (int z = 0; z < 10; z++) {
            for (int x = 0; x < 10; x++) {
                biomes[x + z * 10] = source.getLayout().get(chunkX * 4 - 2 + x, chunkZ * 4 - 2 + z);
            }
        }
        gen.terrain.generate(chunkX, chunkZ, buffer, biomes);
        gen.islands.setBlocksInChunk(chunkX, chunkZ, buffer);
        generateFeatures(gen, buffer, chunkX, chunkZ, true);
        BiomeProfile[] surface = new BiomeProfile[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                surface[x + z * 16] = source.getLayout().get(chunkX * 4 + (x >> 2), chunkZ * 4 + (z >> 2));
            }
        }
        gen.terrain.replaceBiomeBlocks(chunkX, chunkZ, buffer, surface);
        generateFeatures(gen, buffer, chunkX, chunkZ, false);
        terrain.put(key, buffer);
        return buffer;
    }

    private void generateFeatures(TerrainGenerators gen, TerrainBuffer buffer, int chunkX, int chunkZ, boolean base) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = chunkX + dx;
                int z = chunkZ + dz;
                BiomeProfile biome = source.getLayout().get(x * 4 + 2, z * 4 + 2);
                double[] strengths = switch (biome) {
                    case PLAINS -> new double[]{.7, 0, 0, 0};
                    case HILLS -> new double[]{.5, 0, 0, 0};
                    case OCEAN -> new double[]{.7, 0, 1, 1};
                    case ROUGH -> new double[]{.3, 4, 0, 0};
                    default -> new double[4];
                };
                for (int i = 0; i < gen.features.length; i++) {
                    IFeature feature = gen.features[i];
                    Random placement = feature.getRandom(seed, x, z);
                    placement.nextFloat();
                    if (strengths[i] > 0 && feature.isBase() == base
                            && placement.nextFloat() < strengths[i] * feature.getFactor(seed, x, z)) {
                        feature.generate(seed, buffer, chunkX, chunkZ, dx, dz);
                    }
                }
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
                                                       StructureManager structures, ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        TerrainBuffer buffer = getTerrain(cp.x, cp.z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 256; y++) {
                    BlockState state = buffer.getBlockState(x, y, z);
                    if (!state.isAir()) {
                        chunk.setBlockState(pos.set(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z), state, false);
                        if (state.hasBlockEntity() && !CityFeature.hasBlockEntity(state)) {
                            chunk.removeBlockEntity(pos);
                        }
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        // The original cave carver did not replace Ariente marble. Authored underground rooms are carved by the city feature.
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {
        // The original surface pass is included in the cached terrain used by height queries and generation.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        // City equipment initializes guards and patrols after all chunks in its footprint are loaded.
    }

    @Override
    public int getGenDepth() {
        return 256;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        TerrainBuffer buffer = getTerrain(x >> 4, z >> 4);
        for (int y = Math.min(255, height.getMaxBuildHeight() - 1); y >= 0; y--) {
            if (type.isOpaque().test(buffer.getBlockState(x & 15, y, z & 15))) {
                return y + 1;
            }
        }
        return 0;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        TerrainBuffer buffer = getTerrain(x >> 4, z >> 4);
        BlockState[] column = new BlockState[256];
        for (int y = 0; y < 256; y++) {
            column[y] = buffer.getBlockState(x & 15, y, z & 15);
        }
        return new NoiseColumn(0, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Ariente terrain: " + source.getLayout().get(pos.getX() >> 2, pos.getZ() >> 2).getId());
    }

    private static class TerrainGenerators {

        private final ArienteTerrainGenerator terrain = new ArienteTerrainGenerator();
        private final IFeature[] features = {new SpheresFeature(), new SpikesFeature(), new BubbleFeature(), new GlowBubbleFeature()};
        private final IslandsTerrainGenerator islands = new IslandsTerrainGenerator();

        private TerrainGenerators(long seed) {
            terrain.setup(new Random((seed + 516) * 314), LegacyBlockStates.read("ariente:marble"));
            islands.setup(seed);
        }
    }
}
