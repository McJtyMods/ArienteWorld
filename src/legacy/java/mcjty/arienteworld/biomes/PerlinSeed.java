package mcjty.arienteworld.biomes;

import net.minecraft.util.SharedSeedRandom;
import net.minecraft.world.gen.PerlinNoiseGenerator;

public class PerlinSeed {

    private static long seed;

    private PerlinNoiseGenerator perlinGen;

    public PerlinSeed() {
        SharedSeedRandom sharedseedrandom = new SharedSeedRandom(seed);
        perlinGen = new PerlinNoiseGenerator(sharedseedrandom, 0, 0);
    }

    public PerlinNoiseGenerator getPerlinGen() {
        return perlinGen;
    }

    public static void setSeed(long seed) {
        PerlinSeed.seed = seed;
    }

}
