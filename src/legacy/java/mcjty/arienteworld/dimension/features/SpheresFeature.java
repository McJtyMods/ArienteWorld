package mcjty.arienteworld.dimension.features;

import mcjty.ariente.api.MarbleColor;
import mcjty.arienteworld.ArienteStuff;
import mcjty.arienteworld.dimension.NoiseGeneratorPerlin;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.chunk.IChunk;

import java.util.Random;

public class SpheresFeature implements IFeature {

    public static final String FEATURE_SPHERES = "spheres";

    private NoiseGeneratorPerlin featureNoise;

    public SpheresFeature() {
    }

    private NoiseGeneratorPerlin getNoise(IWorld world) {
        if (featureNoise == null) {
            Random random = new Random(world.getSeed() * 257017164707L + 101754694003L);
            featureNoise = new NoiseGeneratorPerlin(random, 4);
        }
        return featureNoise;
    }

    @Override
    public String getId() {
        return FEATURE_SPHERES;
    }

    @Override
    public void generate(IWorld world, IChunk primer, int chunkX, int chunkZ, int dx, int dz) {
        Random random = new Random(world.getSeed() + (chunkZ+dz) * 256203221L + (chunkX+dx) * 899809363L);
        random.nextFloat();
        int radius = random.nextInt(6) + 6;
        int centery = random.nextInt(60) + 40;

        BlockState block = ArienteStuff.marble.getDefaultState().with(MarbleColor.COLOR, MarbleColor.BLUE);
        int centerx = 8 + (dx) * 16;
        int centerz = 8 + (dz) * 16;
        double sqradius = radius * radius;

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = 0 ; x < 16 ; x++) {
            double dxdx = (x-centerx) * (x-centerx);
            for (int z = 0 ; z < 16 ; z++) {
                double dzdz = (z-centerz) * (z-centerz);
                for (int y = centery-radius ; y <= centery+radius ; y++) {
                    double dydy = (y-centery) * (y-centery);
                    double sqdist = dxdx + dydy + dzdz;
                    if (sqdist <= sqradius) {
                        primer.setBlockState(pos.setPos(x, y, z), block, false);
                    }
                }
            }
        }
    }

    @Override
    public double getFactor(IWorld world, int chunkX, int chunkZ) {
        double v = (getNoise(world).getValue(chunkX / 20.0f, chunkZ / 20.0f) - 1) / 70;
        if (v < 0) {
            return 0;
        } else if (v > 1) {
            return 1;
        } else {
            return v;
        }
    }

    @Override
    public Random getRandom(IWorld world, int chunkX, int chunkZ) {
        return new Random(world.getSeed() + chunkZ * 899809363L + chunkX * 256203221L);
    }

    @Override
    public boolean isBase() {
        return false;
    }
}
