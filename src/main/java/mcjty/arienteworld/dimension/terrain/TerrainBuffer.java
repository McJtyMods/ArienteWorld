package mcjty.arienteworld.dimension.terrain;

import java.util.Arrays;
import java.util.Random;
import mcjty.arienteworld.cities.LegacyBlockStates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Local 16 x 16 x 256 buffer for the original noise algorithms. */
public class TerrainBuffer {

    private final BlockState[] blocks = new BlockState[65536];

    public TerrainBuffer() {
        Arrays.fill(blocks, Blocks.AIR.defaultBlockState());
    }

    public void set(int index, BlockState state) {
        blocks[index] = state;
    }

    public void setBlockState(int x, int y, int z, BlockState state) {
        if (y >= 0 && y < 256) {
            blocks[(x << 12) | (z << 8) | y] = state;
        }
    }

    public BlockState getBlockState(int x, int y, int z) {
        return y < 0 || y >= 256 ? Blocks.AIR.defaultBlockState() : blocks[(x << 12) | (z << 8) | y];
    }

    public BlockState getBlockState(BlockPos pos) {
        return getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }

    public void setBlockState(BlockPos pos, BlockState state, boolean moving) {
        setBlockState(pos.getX(), pos.getY(), pos.getZ(), state);
    }

    public int findTop(int x, int z, int top) {
        while (top > 0 && getBlockState(x, top, z).isAir()) {
            top--;
        }
        return top;
    }

    public void surface(int x, int z, Random random, double noise) {
        BlockState marble = LegacyBlockStates.read("ariente:marble");
        BlockState top = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState filler = Blocks.DIRT.defaultBlockState();
        int remaining = -1;
        int thickness = (int) (noise / 3 + 3 + random.nextDouble() * .25);
        for (int y = 255; y >= 0; y--) {
            BlockState state = getBlockState(x, y, z);
            if (y <= random.nextInt(5)) {
                setBlockState(x, y, z, Blocks.BEDROCK.defaultBlockState());
            } else if (state.isAir()) {
                remaining = -1;
            } else if (state.is(marble.getBlock())) {
                if (remaining == -1) {
                    if (thickness <= 0) {
                        top = Blocks.AIR.defaultBlockState();
                        filler = marble;
                    } else if (y >= 59 && y <= 64) {
                        top = Blocks.GRASS_BLOCK.defaultBlockState();
                        filler = Blocks.DIRT.defaultBlockState();
                    }
                    if (y < 63 && top.isAir()) {
                        top = Blocks.WATER.defaultBlockState();
                    }
                    remaining = thickness;
                    if (y >= 62) {
                        setBlockState(x, y, z, top);
                    } else if (y < 56 - thickness) {
                        top = Blocks.AIR.defaultBlockState();
                        filler = marble;
                        setBlockState(x, y, z, Blocks.GRAVEL.defaultBlockState());
                    } else {
                        setBlockState(x, y, z, filler);
                    }
                } else if (remaining > 0) {
                    remaining--;
                    setBlockState(x, y, z, filler);
                }
            }
        }
    }
}
