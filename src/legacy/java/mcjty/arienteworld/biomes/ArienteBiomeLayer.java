package mcjty.arienteworld.biomes;

import mcjty.arienteworld.setup.Registration;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.INoiseRandom;
import net.minecraft.world.gen.layer.traits.IAreaTransformer0;
import net.minecraftforge.common.util.Lazy;

public enum ArienteBiomeLayer implements IAreaTransformer0 {

    INSTANCE;

    private static final Lazy<PerlinSeed> PERLIN_SEED = Lazy.of(PerlinSeed::new);

    private final int cityId = Registry.BIOME.getId(Registration.ARIENTE_CITY.get());
    private final int forestId = Registry.BIOME.getId(Registration.ARIENTE_FOREST.get());
    private final int hillsId = Registry.BIOME.getId(Registration.ARIENTE_HILLS.get());
    private final int oceanId = Registry.BIOME.getId(Registration.ARIENTE_OCEAN.get());
    private final int plainsId = Registry.BIOME.getId(Registration.ARIENTE_PLAINS.get());
    private final int roughId = Registry.BIOME.getId(Registration.ARIENTE_ROUGH.get());

    @Override
    public int apply(INoiseRandom noise, int x, int z) {
        double perlinNoise = PERLIN_SEED.get().getPerlinGen().noiseAt(x * 0.1D, z * 0.00001D, false) * 0.5D + 0.5D;

        if (noise.random(9) == 0) {
            return cityId;
        } else if (Math.abs(perlinNoise) < 0.2) {
            return forestId;
        } else if (Math.abs(perlinNoise) < 0.3) {
            return hillsId;
        } else if (Math.abs(perlinNoise) < 0.5) {
            return oceanId;
        } else if (Math.abs(perlinNoise) < 0.7) {
            return roughId;
        } else {
            return plainsId;
        }
    }

    public static void setSeed(long seed) {
        PerlinSeed.setSeed(seed);
    }

}
