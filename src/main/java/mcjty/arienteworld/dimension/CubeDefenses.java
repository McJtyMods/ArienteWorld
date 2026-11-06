package mcjty.arienteworld.dimension;

import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.PaletteIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Fixed posts and reinforcement positions, separate from the interior's loot random stream. */
public final class CubeDefenses {

    private static final PaletteIndex GUARD = new PaletteIndex('^', 'g');
    private static final PaletteIndex SOLDIER = new PaletteIndex('^', 's');
    private static final PaletteIndex MASTER = new PaletteIndex('^', 'm');

    private CubeDefenses() {
    }

    public static void decorate(BuildingPart part, Palette palette, int chunkX, int chunkZ) {
        String name = part.getName();
        boolean lower = name.equals("c5_e1");
        if ((!lower && !name.equals("c5_w1")) || !palette.getPalette().containsKey(GUARD)
                || !palette.getPalette().containsKey(SOLDIER) || !palette.getPalette().containsKey(MASTER)) {
            return;
        }
        int dx = (chunkX & 15) - 4;
        int dz = (chunkZ & 15) - 4;
        // Keep the reserved arena volume clear.
        if (lower && dx >= 2 && dx <= 3 && dz >= 2 && dz <= 3) {
            return;
        }
        if ((dx & 1) == 1 && (dz == 1 || dz == 7)) {
            for (int deck : new int[]{0, 10, 20}) {
                post(part, palette, 5, deck + 1, 5, SOLDIER);
            }
        }
        if (lower && (dx & 1) == 1 && (dz == 1 || dz == 7)
                || !lower && (dx == 2 && dz == 6 || dx == 6 && dz == 2)) {
            post(part, palette, 10, 1, 10, GUARD);
        }
        if (lower && (dx == 1 || dx == 7) && (dz == 3 || dz == 5)
                || !lower && dx == 5 && dz == 5) {
            post(part, palette, 5, 1, 10, MASTER);
        }
    }

    private static void post(BuildingPart part, Palette palette, int x, int y, int z, PaletteIndex marker) {
        BlockState floor = palette.getPalette().get(part.getC(x, y - 1, z));
        if (floor == null || !floor.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            return;
        }
        for (int h = 0; h < 3; h++) {
            BlockState state = palette.getPalette().get(part.getC(x, y + h, z));
            if (state == null || !state.isAir()) {
                return;
            }
        }
        part.getSlice(y).getSlice().set(z * part.getXSize() + x, marker);
    }
}
