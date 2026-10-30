package mcjty.arienteworld.dimension.features;

import mcjty.arienteworld.dimension.NoiseGeneratorPerlin;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.chunk.IChunk;

import java.util.Random;

public class BubbleFeature implements IFeature {

    public static final String FEATURE_BUBBLES = "bubbles";

    private NoiseGeneratorPerlin featureNoise;

    public BubbleFeature() {
    }

    private NoiseGeneratorPerlin getNoise(IWorld world) {
        if (featureNoise == null) {
            Random random = new Random(world.getSeed() * 911930311L + 911942051L);
            featureNoise = new NoiseGeneratorPerlin(random, 4);
        }
        return featureNoise;
    }

    @Override
    public String getId() {
        return FEATURE_BUBBLES;
    }

    @Override
    public void generate(IWorld world, IChunk primer, int chunkX, int chunkZ, int dx, int dz) {
        Random random = new Random(world.getSeed() + (chunkZ+dz) * 838037023L + (chunkX+dx) * 899809363L);
        random.nextFloat();
        int radius = random.nextInt(6) + 6;
        int centery = random.nextInt(7) + 60;

//        char block = (char) Block.BLOCK_STATE_IDS.get(Blocks.STAINED_GLASS.getDefaultState().with(BlockStainedGlass.COLOR, EnumDyeColor.BLUE));
        BlockState block = Blocks.AIR.getDefaultState();  // @todo 1.15 stained glass
        BlockState water = Blocks.WATER.getDefaultState();
        int centerx = 8 + (dx) * 16;
        int centerz = 8 + (dz) * 16;
        double sqradius = radius * radius;
        double innerRadius = (((double) radius) - 1.5f) * (((double) radius) - 1.5f);

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = 0 ; x < 16 ; x++) {
            double dxdx = (x-centerx) * (x-centerx);
            for (int z = 0 ; z < 16 ; z++) {
                double dzdz = (z-centerz) * (z-centerz);
                for (int y = centery-radius ; y <= centery+radius ; y++) {
                    double dydy = (y-centery) * (y-centery);
                    double sqdist = dxdx + dydy + dzdz;
                    pos.setPos(x, y, z);
                    if (sqdist < innerRadius) {
                        primer.setBlockState(pos, water, false);
                    } else if (sqdist <= sqradius) {
                        primer.setBlockState(pos, block, false);
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
        return new Random(world.getSeed() + chunkZ * 899809363L + chunkX * 961712959L);
    }

    @Override
    public boolean isBase() {
        return false;
    }
}
