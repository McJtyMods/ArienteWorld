package mcjty.arienteworld.biomes;

import mcjty.arienteworld.dimension.features.IFeature;

public interface IArienteBiome {

    double getFeatureStrength(IFeature feature);

    boolean isCityBiome();
}
