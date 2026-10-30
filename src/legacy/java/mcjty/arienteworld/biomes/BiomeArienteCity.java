package mcjty.arienteworld.biomes;

import mcjty.arienteworld.dimension.features.*;

import java.util.HashMap;
import java.util.Map;

public class BiomeArienteCity extends AbstractArienteBiome {

    private static final Map<String, Double> FEATURE_STRENGTHS = new HashMap<>();


    static {
        FEATURE_STRENGTHS.put(SpheresFeature.FEATURE_SPHERES, 0.0);
        FEATURE_STRENGTHS.put(SpikesFeature.FEATURE_SPIKES, 0.0);
        FEATURE_STRENGTHS.put(BubbleFeature.FEATURE_BUBBLES, 0.0);
        FEATURE_STRENGTHS.put(GlowBubbleFeature.FEATURE_GLOWBUBBLES, 0.0);
    }

    public BiomeArienteCity(Builder properties) {
        super(properties);
        // @todo 1.15
//        this.decorator.treesPerChunk = 0;
//        this.decorator.extraTreeChance = 0;
//        this.decorator.flowersPerChunk = 0;
//        this.decorator.grassPerChunk = 0;
//        this.spawnableMonsterList.clear();
//        this.spawnableMonsterList.add(new Biome.SpawnListEntry(ModSetup.arienteSystem.getSoldierClass(), 95, 4, 4));
//        this.spawnableMonsterList.add(new Biome.SpawnListEntry(ModSetup.arienteSystem.getMasterSoldierClass(), 5, 1, 1));
    }

    @Override
    public boolean isCityBiome() {
        return true;
    }

    @Override
    public double getFeatureStrength(IFeature feature) {
        return FEATURE_STRENGTHS.getOrDefault(feature.getId(), 0.0);
    }
}
