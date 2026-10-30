package mcjty.arienteworld.biomes;

import mcjty.arienteworld.dimension.features.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class BiomeArienteRough extends AbstractArienteBiome {

    private static final Map<String, Double> FEATURE_STRENGTHS = new HashMap<>();

    static {
        FEATURE_STRENGTHS.put(SpheresFeature.FEATURE_SPHERES, 0.3);
        FEATURE_STRENGTHS.put(SpikesFeature.FEATURE_SPIKES, 4.0);
        FEATURE_STRENGTHS.put(BubbleFeature.FEATURE_BUBBLES, 0.0);
        FEATURE_STRENGTHS.put(GlowBubbleFeature.FEATURE_GLOWBUBBLES, 0.0);
    }


    public BiomeArienteRough(Builder properties) {
        super(properties);
        // @todo 1.15
//        this.decorator.treesPerChunk = 0;
//        this.decorator.extraTreeChance = 0;
//        this.decorator.flowersPerChunk = 0;
    }

    public void genTerrainBlocks(World worldIn, Random rand, ChunkPrimer chunkPrimerIn, int x, int z, double noiseVal) {
    }

    @Override
    public double getFeatureStrength(IFeature feature) {
        return FEATURE_STRENGTHS.getOrDefault(feature.getId(), 0.0);
    }
}
