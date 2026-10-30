package mcjty.arienteworld.dimension.features;

import mcjty.arienteworld.dimension.terrain.TerrainBuffer;

import java.util.Random;

public interface IFeature {

    String getId();

    double getFactor(long seed, int chunkX, int chunkZ);

    Random getRandom(long seed, int chunkX, int chunkZ);

    /// Return true if this feature has to be done on the base terrain. Otherwise it will be done after biome decoration
    boolean isBase();

    /**
     * Geneate this feature in the chunk at chunkX, chunkZ. The relative coordinates
     * of the feature are given with dx, dz. So the center chunk of the feature will be at 0,0
     */
    void generate(long seed, TerrainBuffer primer, int chunkX, int chunkZ, int dx, int dz);
}
