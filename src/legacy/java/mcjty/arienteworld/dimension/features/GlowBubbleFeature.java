package mcjty.arienteworld.dimension.features;

import mcjty.arienteworld.ArienteStuff;
import mcjty.arienteworld.dimension.NoiseGeneratorPerlin;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.chunk.IChunk;

import java.util.Random;

public class GlowBubbleFeature implements IFeature {

    public static final String FEATURE_GLOWBUBBLES = "glowbubbles";

    private NoiseGeneratorPerlin featureNoise;

    public GlowBubbleFeature() {
    }

    private NoiseGeneratorPerlin getNoise(IWorld world) {
        if (featureNoise == null) {
            Random random = new Random(world.getSeed() * 961719287L + 911942051L);
            featureNoise = new NoiseGeneratorPerlin(random, 4);
        }
        return featureNoise;
    }

    @Override
    public String getId() {
        return FEATURE_GLOWBUBBLES;
    }

    @Override
    public void generate(IWorld world, IChunk primer, int chunkX, int chunkZ, int dx, int dz) {
        Random random = new Random(world.getSeed() + (chunkZ+dz) * 838037023L + (chunkX+dx) * 825719033L);
        random.nextFloat();
        int radius = random.nextInt(6) + 6;
        int centery = random.nextInt(7) + 40;

//        char block = (char) Block.BLOCK_STATE_IDS.get(ArienteStuff.marble.getDefaultState().with(MarbleColor.COLOR, MarbleColor.BLUE));
//        char block = (char) Block.BLOCK_STATE_IDS.get(Blocks.STAINED_GLASS.getDefaultState().with(BlockStainedGlass.COLOR, EnumDyeColor.BLUE));
        BlockState block = Blocks.AIR.getDefaultState(); // @todo 1.15  should be stained glass!
        BlockState inner = ArienteStuff.fluxGlow.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
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
                        primer.setBlockState(pos, inner, false);
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
        return new Random(world.getSeed() + chunkZ * 817515073L + chunkX * 961712959L);
    }

    @Override
    public boolean isBase() {
        return false;
    }
}
