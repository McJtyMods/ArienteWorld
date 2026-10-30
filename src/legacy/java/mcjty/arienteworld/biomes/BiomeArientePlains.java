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

public class BiomeArientePlains extends AbstractArienteBiome {

    private static final Map<String, Double> FEATURE_STRENGTHS = new HashMap<>();

    static {
        FEATURE_STRENGTHS.put(SpheresFeature.FEATURE_SPHERES, 0.7);
        FEATURE_STRENGTHS.put(SpikesFeature.FEATURE_SPIKES, 0.0);
        FEATURE_STRENGTHS.put(BubbleFeature.FEATURE_BUBBLES, 0.0);
        FEATURE_STRENGTHS.put(GlowBubbleFeature.FEATURE_GLOWBUBBLES, 0.0);
    }

    public BiomeArientePlains(Builder properties) {
        super(properties);
        // @todo 1.15
//        this.decorator.treesPerChunk = 0;
//        this.decorator.extraTreeChance = 0.1F;
//        this.decorator.flowersPerChunk = 0;
//        this.decorator.grassPerChunk = 10;
    }

    @Override
    public void decorate(GenerationStage.Decoration stage, ChunkGenerator<? extends GenerationSettings> chunkGenerator, IWorld worldIn, long seed, SharedSeedRandom random, BlockPos pos) {
        super.decorate(stage, chunkGenerator, worldIn, seed, random, pos);
        // @todo 1.15
//        generateFlowers(worldIn.getWorld(), random, 5);
    }

    @Override
    public double getFeatureStrength(IFeature feature) {
        return FEATURE_STRENGTHS.getOrDefault(feature.getId(), 0.0);
    }
}
