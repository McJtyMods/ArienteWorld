package mcjty.arienteworld.dimension.features;

import mcjty.ariente.api.MarbleColor;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.dimension.terrain.NoiseGeneratorPerlin;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import mcjty.arienteworld.dimension.terrain.TerrainBuffer;

import java.util.Random;

public class SpikesFeature implements IFeature {

    public static final String FEATURE_SPIKES = "spikes";

    private NoiseGeneratorPerlin featureNoise;

    public SpikesFeature() {
    }

    private NoiseGeneratorPerlin getNoise(long seed) {
        if (featureNoise == null) {
            Random random = new Random(seed * 817504943L + 101754694003L);
            featureNoise = new NoiseGeneratorPerlin(random, 4);
        }
        return featureNoise;
    }

    @Override
    public String getId() {
        return FEATURE_SPIKES;
    }

    @Override
    public void generate(long seed, TerrainBuffer primer, int chunkX, int chunkZ, int dx, int dz) {

        if (dx != 0 || dz != 0) {
            return;
        }

        Random random = new Random(seed + chunkX * 899809363L + chunkZ * 953485367L);
        random.nextFloat();

        BlockState block1 = LegacyBlockStates.read("ariente:marble@color=black");
        BlockState block2 = LegacyBlockStates.read("ariente:marble@color=darkblue");
        BlockState blockG = LegacyBlockStates.read("ariente:marble@color=gray");
        BlockState air = Blocks.AIR.defaultBlockState();

        int radius = random.nextInt(4) + 2;
        int centerx = random.nextInt(10) + 3;
        int centerz = random.nextInt(10) + 3;

        int index = (centerx << 12) | (centerz << 8);
        int centery = primer.findTop(centerx, centerz, 200)-5;
        if (centery < 1) {
            centery = 1;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = centerx - radius / 2; x <= centerx + radius / 2; x++) {
            int distx = Math.abs(x-centerx);
            for (int z = centerz - radius / 2; z <= centerz + radius / 2; z++) {
                int distz = Math.abs(z-centerz);
                int avgdist = (distx+distz)/2;
                index = (x * 16 + z) * 256 + centery;
                int y = 0;
                while (y+centery < 255) {
                    if (primer.getBlockState(pos.set(x, centery+y-1, z)) != air) {
                        pos.set(x, centery+y, z);
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
        return new Random(seed + chunkZ * 836962723L + chunkX * 953480239L);
    }

    @Override
    public boolean isBase() {
        return false;
    }
}
