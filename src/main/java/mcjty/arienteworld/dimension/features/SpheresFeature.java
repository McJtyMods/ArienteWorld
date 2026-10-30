package mcjty.arienteworld.dimension.features;

import mcjty.ariente.api.MarbleColor;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.dimension.terrain.NoiseGeneratorPerlin;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import mcjty.arienteworld.dimension.terrain.TerrainBuffer;

import java.util.Random;

public class SpheresFeature implements IFeature {

    public static final String FEATURE_SPHERES = "spheres";

    private NoiseGeneratorPerlin featureNoise;

    public SpheresFeature() {
    }

    private NoiseGeneratorPerlin getNoise(long seed) {
        if (featureNoise == null) {
            Random random = new Random(seed * 257017164707L + 101754694003L);
            featureNoise = new NoiseGeneratorPerlin(random, 4);
        }
        return featureNoise;
    }

    @Override
    public String getId() {
        return FEATURE_SPHERES;
    }

    @Override
    public void generate(long seed, TerrainBuffer primer, int chunkX, int chunkZ, int dx, int dz) {
        Random random = new Random(seed + (chunkZ+dz) * 256203221L + (chunkX+dx) * 899809363L);
        random.nextFloat();
        int radius = random.nextInt(6) + 6;
        int centery = random.nextInt(60) + 40;

        BlockState block = LegacyBlockStates.read("ariente:marble@color=blue");
        int centerx = 8 + (dx) * 16;
        int centerz = 8 + (dz) * 16;
        double sqradius = radius * radius;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0 ; x < 16 ; x++) {
            double dxdx = (x-centerx) * (x-centerx);
            for (int z = 0 ; z < 16 ; z++) {
                double dzdz = (z-centerz) * (z-centerz);
                for (int y = centery-radius ; y <= centery+radius ; y++) {
                    double dydy = (y-centery) * (y-centery);
                    double sqdist = dxdx + dydy + dzdz;
                    if (sqdist <= sqradius) {
                        primer.setBlockState(pos.set(x, y, z), block, false);
                    }
                }
            }
        }
    }

    @Override
    public double getFactor(long seed, int chunkX, int chunkZ) {
        double v = (getNoise(seed).getValue(chunkX / 20.0f, chunkZ / 20.0f) - 1) / 70;
        if (v < 0) {
            return 0;
        } else if (v > 1) {
            return 1;
        } else {
            return v;
        }
    }

    @Override
    public Random getRandom(long seed, int chunkX, int chunkZ) {
        return new Random(seed + chunkZ * 899809363L + chunkX * 256203221L);
    }

    @Override
    public boolean isBase() {
        return false;
    }
}
