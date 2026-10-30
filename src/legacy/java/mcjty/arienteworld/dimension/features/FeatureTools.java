package mcjty.arienteworld.dimension.features;

import mcjty.arienteworld.biomes.IArienteBiome;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class FeatureTools {

    public static boolean isFeatureCenter(IFeature feature, double strength, IWorld world, int chunkX, int chunkZ) {
        double factor = feature.getFactor(world, chunkX, chunkZ);
        Random random = feature.getRandom(world, chunkX, chunkZ);
        random.nextFloat();
        double value = random.nextFloat();
//        System.out.println((value < factor ? "YES" : "no ") + "  chunkX = " + chunkX + "," + chunkZ + "    factor=" + factor +", value=" + value);
        return value < factor * strength;
    }


    // @todo 1.15 evaluate if doing this on a single biome is enough!
//    public static Map<String, Double> getActiveFeatures(BiomeManager biomes) {
//        if (biomes.length == 0) {
//            return Collections.emptyMap();
//        }
//        Map<String, Double> activeFeatures = new HashMap<>();
//        for (IFeature feature : FeatureRegistry.getFeatures()) {
//            double f = 0.0;
//            for (Biome biome : biomes) {
//                if (biome instanceof IArienteBiome) {
//                    IArienteBiome arienteBiome = (IArienteBiome) biome;
//                    f += arienteBiome.getFeatureStrength(feature);
//                }
//            }
//            f /= biomes.length;
//            if (f > 0) {
//                activeFeatures.put(feature.getId(), f);
//            }
//        }
//        return activeFeatures;
//    }

    public static Map<String, Double> getActiveFeatures(Biome biome) {
        Map<String, Double> activeFeatures = new HashMap<>();
        for (IFeature feature : FeatureRegistry.getFeatures()) {
            double f = 0.0;
            if (biome instanceof IArienteBiome) {
                IArienteBiome arienteBiome = (IArienteBiome) biome;
                f += arienteBiome.getFeatureStrength(feature);
            }
            f /= 1; // @todo was designed for more biomes
            if (f > 0) {
                activeFeatures.put(feature.getId(), f);
            }
        }
        return activeFeatures;
    }
}
