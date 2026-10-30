package mcjty.arienteworld.dimension;

import net.minecraft.world.biome.provider.BiomeProvider;

public interface IArienteChunkGenerator {

    BiomeProvider getBiomes();

    // Get a heightmap for a chunk. If needed calculate (and cache) a primer
    ChunkHeightmap getHeightmap(int chunkX, int chunkZ);

    ArienteDungeonGenerator getDungeonGenerator();
}
