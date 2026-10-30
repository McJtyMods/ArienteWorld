package mcjty.arienteworld.dimension.features;

import mcjty.ariente.api.MarbleColor;
import mcjty.arienteworld.ArienteStuff;
import mcjty.arienteworld.dimension.NoiseGeneratorPerlin;
import mcjty.arienteworld.dimension.PrimerTools;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.chunk.IChunk;

import java.util.Random;

public class SpikesFeature implements IFeature {

    public static final String FEATURE_SPIKES = "spikes";

    private NoiseGeneratorPerlin featureNoise;

    public SpikesFeature() {
    }

    private NoiseGeneratorPerlin getNoise(IWorld world) {
        if (featureNoise == null) {
            Random random = new Random(world.getSeed() * 817504943L + 101754694003L);
            featureNoise = new NoiseGeneratorPerlin(random, 4);
        }
        return featureNoise;
    }

    @Override
    public String getId() {
        return FEATURE_SPIKES;
    }

    @Override
    public void generate(IWorld world, IChunk primer, int chunkX, int chunkZ, int dx, int dz) {

        if (dx != 0 || dz != 0) {
            return;
        }

        Random random = new Random(world.getSeed() + chunkX * 899809363L + chunkZ * 953485367L);
        random.nextFloat();

        BlockState block1 = ArienteStuff.marble.getDefaultState().with(MarbleColor.COLOR, MarbleColor.BLACK);
        BlockState block2 = ArienteStuff.marble.getDefaultState().with(MarbleColor.COLOR, MarbleColor.DARKBLUE);
        BlockState blockG = ArienteStuff.marble.getDefaultState().with(MarbleColor.COLOR, MarbleColor.GRAY);
        BlockState air = Blocks.AIR.getDefaultState();

        int radius = random.nextInt(4) + 2;
        int centerx = random.nextInt(10) + 3;
        int centerz = random.nextInt(10) + 3;

        int index = (centerx << 12) | (centerz << 8);
        int centery = PrimerTools.findTopBlock(primer, centerx, 0, centerz, 200, air)-5;
        if (centery < 1) {
            centery = 1;
        }

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = centerx - radius / 2; x <= centerx + radius / 2; x++) {
            int distx = Math.abs(x-centerx);
            for (int z = centerz - radius / 2; z <= centerz + radius / 2; z++) {
                int distz = Math.abs(z-centerz);
                int avgdist = (distx+distz)/2;
                index = (x * 16 + z) * 256 + centery;
                int y = 0;
                while (y+centery < 255) {
                    if (primer.getBlockState(pos.setPos(x, centery+y-1, z)) != air) {
                        pos.setPos(x, centery+y, z);
                        float factor = 1.0f - y / (120.0f - avgdist * 3);
                        if (random.nextFloat() < factor) {
                            if (random.nextFloat() > (y-10) / 5.0f) {
                                primer.setBlockState(pos, blockG, false);
                            } else if (random.nextFloat() > (y-20) / 6.0f) {
                                primer.setBlockState(pos, block2, false);
                            } else {
                                primer.setBlockState(pos, block1, false);
                            }
                        }
                        y++;
                    } else {
                        break;
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
        return new Random(world.getSeed() + chunkZ * 836962723L + chunkX * 953480239L);
    }

    @Override
    public boolean isBase() {
        return false;
    }
}
