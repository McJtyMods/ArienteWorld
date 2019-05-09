package mcjty.arienteworld.dimension.features;

import mcjty.ariente.api.MarbleColor;
import mcjty.arienteworld.ArienteStuff;
import mcjty.arienteworld.dimension.GeneratorTools;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.NoiseGeneratorPerlin;

import java.util.Random;

public class SpheresFeature implements IFeature {

    public static final String FEATURE_SPHERES = "spheres";

    private NoiseGeneratorPerlin featureNoise;

    public SpheresFeature() {
    }

    private NoiseGeneratorPerlin getNoise(World world) {
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
    public void generate(World world, ChunkPrimer primer, int chunkX, int chunkZ, int dx, int dz) {
        int radius = getRandomRadius(world, chunkX+dx, chunkZ+dz);
        int centery = getRandomHeight(world, chunkX+dx, chunkZ+dz);

        IBlockState block = ArienteStuff.marble.getDefaultState().withProperty(MarbleColor.COLOR, MarbleColor.BLUE);
        int centerx = 8 + (dx) * 16;
        int centerz = 8 + (dz) * 16;
        double sqradius = radius * radius;

        for (int x = 0 ; x < 16 ; x++) {
            double dxdx = (x-centerx) * (x-centerx);
            for (int z = 0 ; z < 16 ; z++) {
                double dzdz = (z-centerz) * (z-centerz);
                int index = (x * 16 + z) * 256;
                for (int y = centery-radius ; y <= centery+radius ; y++) {
                    double dydy = (y-centery) * (y-centery);
                    double sqdist = dxdx + dydy + dzdz;
                    if (sqdist <= sqradius) {
                        GeneratorTools.setBlockState(primer, index + y, block);
                    }
                }
            }
        }
    }

    @Override
    public double getFactor(World world, int chunkX, int chunkZ) {
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
    public Random getRandom(World world, int chunkX, int chunkZ) {
        return new Random(world.getSeed() + chunkZ * 899809363L + chunkX * 256203221L);
    }

    private int getRandomRadius(World world, int chunkX, int chunkZ) {
        Random random = new Random(world.getSeed() + chunkZ * 256203221L + chunkX * 899809363L);
        random.nextFloat();
        random.nextFloat();
        return random.nextInt(6)+6;
    }

    private int getRandomHeight(World world, int chunkX, int chunkZ) {
        Random random = new Random(world.getSeed() + chunkZ * 256203221L + chunkX * 899809363L);
        random.nextFloat();
        return random.nextInt(60)+40;
    }

    @Override
    public boolean isBase() {
        return false;
    }
}
