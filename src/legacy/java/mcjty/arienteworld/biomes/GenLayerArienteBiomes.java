package mcjty.arienteworld.biomes;

import net.minecraft.world.WorldType;
import net.minecraft.world.gen.layer.BiomeLayer;

public class GenLayerArienteBiomes extends BiomeLayer {

    public GenLayerArienteBiomes(WorldType type, int l) {
        super(type, l);
    }

    // @todo 1.15
//    @Override
//    public int[] getInts(int x, int z, int width, int depth) {
//        int dest[] = IntCache.getIntCache(width * depth);
//        for (int dz = 0; dz < depth; dz++) {
//            for (int dx = 0; dx < width; dx++) {
//                initChunkSeed(dx + x, dz + z);
//                dest[dx + dz * width] = Biome.getIdForBiome(commonBiomes[nextInt(commonBiomes.length)]);
////                if (nextInt(RARE_BIOME_CHANCE) == 0) {
////                    // make rare biome
////                    dest[dx + dz * width] = Biome.getIdForBiome(rareBiomes[nextInt(rareBiomes.length)]);
////                } else {
////                    // make common biome
////                    dest[dx + dz * width] = Biome.getIdForBiome(commonBiomes[nextInt(commonBiomes.length)]);
////                }
//            }
//
//        }
//        return dest;
//    }
}
