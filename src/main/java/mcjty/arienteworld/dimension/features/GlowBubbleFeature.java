package mcjty.arienteworld.dimension.features;

import java.util.Random;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.dimension.terrain.NoiseGeneratorPerlin;
import mcjty.arienteworld.dimension.terrain.TerrainBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class GlowBubbleFeature implements IFeature {

    public static final String FEATURE_GLOWBUBBLES = "glowbubbles";

    private NoiseGeneratorPerlin featureNoise;

    public GlowBubbleFeature() {
    }

    private NoiseGeneratorPerlin getNoise(long seed) {
        if (featureNoise == null) {
            Random random = new Random(seed * 961719287L + 911942051L);
            featureNoise = new NoiseGeneratorPerlin(random, 4);
        }
        return featureNoise;
    }

    @Override
    public String getId() {
        return FEATURE_GLOWBUBBLES;
    }

    @Override
    public void generate(long seed, TerrainBuffer primer, int chunkX, int chunkZ, int dx, int dz) {
        Random random = new Random(seed + (chunkZ+dz) * 838037023L + (chunkX+dx) * 825719033L);
        random.nextFloat();
        int radius = random.nextInt(6) + 6;
        int centery = random.nextInt(7) + 40;

        BlockState block = Blocks.BLUE_STAINED_GLASS.defaultBlockState();
        BlockState inner = LegacyBlockStates.read("ariente:fluxglow");
        BlockState air = Blocks.AIR.defaultBlockState();
        int centerx = 8 + (dx) * 16;
        int centerz = 8 + (dz) * 16;

        double sqradius = radius * radius;
        double innerRadius = (((double) radius) - 1.5f) * (((double) radius) - 1.5f);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0 ; x < 16 ; x++) {
            double dxdx = (x-centerx) * (x-centerx);
            for (int z = 0 ; z < 16 ; z++) {
                double dzdz = (z-centerz) * (z-centerz);
                for (int y = centery-radius ; y <= centery+radius ; y++) {
                    double dydy = (y-centery) * (y-centery);
                    double sqdist = dxdx + dydy + dzdz;
                    pos.set(x, y, z);
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
        return new Random(seed + chunkZ * 817515073L + chunkX * 961712959L);
    }

    @Override
    public boolean isBase() {
        return false;
    }
}
