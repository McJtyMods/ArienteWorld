package mcjty.arienteworld.dimension;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.IChunk;

public class PrimerTools {
    public static void setBlockStateRange(IChunk primer, int sx, int sy, int sz, int ey, BlockState c) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = sy ; y < ey ; y++) {
            primer.setBlockState(pos.setPos(sx, y, sz), c, false);
        }
//        Arrays.fill(primer.data, s, e, c);
    }

    // Don't use in the void! 'index' is the coordinate of the bottom
    // Return the height at which there is a non-air block
    public static int findTopBlock(IChunk primer, int sx, int sy, int sz, int height, BlockState air) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        while (height > 0 && primer.getBlockState(pos.setPos(sx, sy+height, sz)) == air) {
            height--;
        }
        return height;
    }

    public static void setBlockStateRangeSafe(IChunk primer, int sx, int sy, int sz, int ey, BlockState c) {
        if (ey <= sy) {
            return;
        }
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = sy ; y < ey ; y++) {
            primer.setBlockState(pos.setPos(sx, y, sz), c, false);
        }
    }

    public static void fillChunk(IChunk primer, BlockState baseChar, int start, int stop) {
        for (int dx = 0 ; dx < 16 ; dx++) {
            for (int dz = 0 ; dz < 16 ; dz++) {
                setBlockStateRange(primer, dx, start, dz, stop, baseChar);
            }
        }
    }
}
