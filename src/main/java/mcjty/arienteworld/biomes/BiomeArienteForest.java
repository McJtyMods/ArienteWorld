package mcjty.arienteworld.biomes;

import mcjty.arienteworld.dimension.features.*;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationSettings;
import net.minecraft.world.gen.GenerationStage;

import java.util.HashMap;
import java.util.Map;

public class BiomeArienteForest extends AbstractArienteBiome {

    private static final Map<String, Double> FEATURE_STRENGTHS = new HashMap<>();

    static {
        FEATURE_STRENGTHS.put(SpheresFeature.FEATURE_SPHERES, 0.0);
        FEATURE_STRENGTHS.put(SpikesFeature.FEATURE_SPIKES, 0.0);
        FEATURE_STRENGTHS.put(BubbleFeature.FEATURE_BUBBLES, 0.0);
        FEATURE_STRENGTHS.put(GlowBubbleFeature.FEATURE_GLOWBUBBLES, 0.0);
    }

    public BiomeArienteForest(Builder properties) {
        super(properties);
        // @todo 1.15
//        this.decorator.treesPerChunk = 15;
//        this.decorator.extraTreeChance = 0.1F;
//        this.decorator.grassPerChunk = 2;
//        this.decorator.flowersPerChunk = 0;
    }

    @Override
    public void decorate(GenerationStage.Decoration stage, ChunkGenerator<? extends GenerationSettings> chunkGenerator, IWorld worldIn, long seed, SharedSeedRandom random, BlockPos pos) {
        super.decorate(stage, chunkGenerator, worldIn, seed, random, pos);
        // @todo 1.15
//        generateFlowers(worldIn.getWorld(), random, 20);
    }

    @Override
    public double getFeatureStrength(IFeature feature) {
        return FEATURE_STRENGTHS.getOrDefault(feature.getId(), 0.0);
    }
}