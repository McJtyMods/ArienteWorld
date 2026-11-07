package mcjty.arienteworld.dimension;

import mcjty.ariente.blocks.decorative.RampBlock;
import mcjty.ariente.blocks.decorative.SlopeBlock;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.PaletteIndex;
import mcjty.arienteworld.cities.Transform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Purpose-built, supported machinery halls inside the cube's basement and roof. */
final class CubeDecorativeDecks {

    private static final int DECK_FLOOR = 1;
    private static final int LIFT_HEIGHT = 84;
    private static final PaletteIndex TECH_NS = new PaletteIndex('%', 'n');
    private static final PaletteIndex TECH_EW = new PaletteIndex('%', 'e');
    private static final PaletteIndex MARBLE_NS = new PaletteIndex('%', 'm');
    private static final PaletteIndex MARBLE_EW = new PaletteIndex('%', 'w');
    private static final PaletteIndex GLASS_NS = new PaletteIndex('%', 'b');
    private static final PaletteIndex GLASS_EW = new PaletteIndex('%', 'c');

    private CubeDecorativeDecks() {
    }

    static boolean isDecorativeDeck(BuildingPart part) {
        return part.getName().startsWith("c5_") && "jklmnopqr123456789".indexOf(part.getName().charAt(3)) >= 0;
    }

    static void decorate(BuildingPart part, Palette palette, long seed, int chunkX, int chunkZ) {
        char section = part.getName().charAt(3);
        boolean basement = "jklmnopqr".indexOf(section) >= 0;
        boolean rooftop = "123456789".indexOf(section) >= 0;
        if (basement || rooftop) {
            decorateDeck(part, palette, seed, chunkX, chunkZ, basement);
        }
        int dx = (chunkX & 15) - 4;
        int dz = (chunkZ & 15) - 4;
        // The fourth main-floor column is the reserved arena; leave its upper room lift local.
        boolean throughLift = (dx == 2 || dx == 6) && (dz == 2 || dz == 6) && (dx != 2 || dz != 2);
        if (throughLift && (basement || rooftop || section == 'e' || section == 'w')) {
            lift(part, basement, rooftop);
        }
    }

    private static void decorateDeck(BuildingPart part, Palette palette, long seed, int chunkX, int chunkZ, boolean basement) {
        int ceiling = basement ? 15 : 28;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 1; y < ceiling; y++) {
                    if (part.getC(x, y, z).equals(new PaletteIndex('a', ' '))) {
                        set(part, x, y, z, material(0));
                    }
                }
                if (part.getC(x, ceiling, z).equals(new PaletteIndex('a', ' '))) {
                    set(part, x, ceiling, z, material(1));
                }
                // Leave the original foundation and outer shell in place below the new paving.
                if (air(part, palette, x, DECK_FLOOR, z) && solid(part, palette, x, 0, z)) {
                    boolean trim = (x == 3 || x == 12) && z >= 6 && z <= 9
                            || (z == 3 || z == 12) && x >= 6 && x <= 9;
                    boolean lamp = (x == 3 || x == 12) && z == 7 || (z == 3 || z == 12) && x == 7;
                    set(part, x, DECK_FLOOR, z, material(lamp ? 8 : trim ? 3 : 1));
                }
                // Every beam and inset lamp touches the solid ceiling directly above it.
                if ((x == 0 || z == 0) && air(part, palette, x, ceiling - 1, z) && solid(part, palette, x, ceiling, z)) {
                    set(part, x, ceiling - 1, z, material(x == 7 || z == 7 ? 8 : 1));
                }
            }
        }
        Random random = new Random(seed ^ chunkX * 341873128712L ^ chunkZ * 132897987541L
                ^ (basement ? 0x426173656d656e74L : 0x526f6f66746f70L));
        int theme = random.nextInt(6);
        int height = basement ? 7 + random.nextInt(4) : 12 + random.nextInt(9);
        // Custom palettes can retain the paving and lifts without opting into shaped railings.
        if (!palette.getPalette().keySet().containsAll(List.of(TECH_NS, TECH_EW, MARBLE_NS, MARBLE_EW, GLASS_NS, GLASS_EW))) {
            return;
        }
        // Check the whole assembly before placing it. Never crop a support to fit a wall or lift.
        for (int x = 4; x <= 11; x++) {
            for (int z = 4; z <= 11; z++) {
                if (!solid(part, palette, x, DECK_FLOOR, z)) {
                    return;
                }
                for (int y = 2; y <= height + 1; y++) {
                    if (!air(part, palette, x, y, z)) {
                        return;
                    }
                }
            }
        }
        // Four-wide perimeter walks also leave the existing through-lift columns clear.
        plinth(part);
        switch (theme) {
            case 0 -> chamber(part, height, random.nextBoolean());
            case 1 -> coolingBanks(part, height);
            case 2 -> consoles(part);
            case 3 -> gantry(part, height);
            case 4 -> steppedMachine(part, height);
            default -> instrumentBeds(part);
        }
        rotateAssembly(part, palette, Transform.values()[random.nextInt(4)], height + 1);
    }

    private static void plinth(BuildingPart part) {
        // Cut the corners and rake the four edges into the surrounding floor.
        for (int x = 4; x <= 11; x++) {
            for (int z = 4; z <= 11; z++) {
                if ((x == 4 || x == 11) && (z == 4 || z == 11)) {
                    continue;
                }
                PaletteIndex block = material(1);
                if (x >= 6 && x <= 9 && (z == 4 || z == 11)) {
                    block = authored(z == 4 ? 'o' : 'n');
                } else if (z >= 6 && z <= 9 && (x == 4 || x == 11)) {
                    block = authored(x == 4 ? 'k' : 's');
                }
                set(part, x, 2, z, block);
            }
        }
        // Lamps sit in the full corner shoulders, beside the ramped edges.
        for (int[] lamp : new int[][]{{5, 4}, {11, 5}, {10, 11}, {4, 10}}) {
            set(part, lamp[0], 2, lamp[1], material(8));
        }
    }

    private static void chamber(BuildingPart part, int height, boolean red) {
        for (int y = 3; y < height; y++) {
            for (int x = 5; x <= 10; x++) {
                for (int z = 5; z <= 10; z++) {
                    boolean corner = (x == 5 || x == 10) && (z == 5 || z == 10);
                    boolean pier = (x == 5 || x == 10) && (z == 6 || z == 9)
                            || (z == 5 || z == 10) && (x == 6 || x == 9);
                    boolean edge = x == 5 || x == 10 || z == 5 || z == 10;
                    if (!corner && edge) {
                        set(part, x, y, z, material(pier ? 1 : y == 3 || y == height - 1 ? 10 : 13));
                    }
                }
            }
            fill(part, 7, 8, y, y, 7, 8, 10);
        }
        // A single short indicator band replaces the old column of control panels.
        fill(part, 7, 8, Math.max(4, height / 2), Math.max(4, height / 2), 7, 7, red ? 4 : 3);
        for (int x = 5; x <= 10; x++) {
            for (int z = 5; z <= 10; z++) {
                if (!((x == 5 || x == 10) && (z == 5 || z == 10))) {
                    set(part, x, height - 1, z, material(1));
                }
            }
        }
        cap(part, 5, 10, height, 5, 10);
        for (int x = 6; x <= 9; x++) {
            set(part, x, height + 1, 7, TECH_EW);
        }
    }

    private static void coolingBanks(BuildingPart part, int height) {
        // Thin, continuous fins sit between full end posts and a solid cap.
        fill(part, 6, 6, 3, height - 1, 5, 10, 10);
        for (int y = 3; y < height; y++) {
            for (int x = 5; x <= 7; x++) {
                set(part, x, y, 5, material(1));
                set(part, x, y, 10, material(1));
            }
            for (int x : new int[]{5, 7}) {
                for (int z = 6; z <= 9; z++) {
                    set(part, x, y, z, MARBLE_NS);
                }
            }
        }
        cap(part, 5, 7, height, 5, 10);
        int lower = height - 3;
        fill(part, 9, 10, 3, lower, 6, 9, 1);
        cap(part, 9, 10, lower + 1, 6, 9);
        set(part, 6, 4, 5, material(26));
        set(part, 9, 3, 6, material(27));
    }

    private static void consoles(BuildingPart part) {
        // An L-shaped bench with raked working faces and a low glass back rail.
        fill(part, 5, 10, 3, 3, 8, 10, 1);
        fill(part, 5, 10, 4, 4, 9, 10, 10);
        for (int x = 5; x <= 10; x++) {
            set(part, x, 4, 8, authored('o'));
        }
        fill(part, 9, 10, 3, 3, 5, 7, 1);
        for (int z = 5; z <= 7; z++) {
            set(part, 9, 4, z, authored('k'));
            set(part, 10, 4, z, material(10));
        }
        set(part, 6, 4, 9, material(11));
        set(part, 10, 4, 6, material(26));
        for (int x = 6; x <= 9; x++) {
            set(part, x, 5, 10, GLASS_EW);
        }
        fill(part, 5, 6, 3, 4, 5, 6, 1);
        cap(part, 5, 6, 5, 5, 7);
    }

    private static void gantry(BuildingPart part, int height) {
        // Offset piers carry a stepped lintel, joined by a thick central knee.
        fill(part, 5, 6, 3, height - 2, 6, 8, 1);
        fill(part, 9, 10, 3, height - 4, 8, 10, 1);
        fill(part, 5, 7, height - 1, height, 6, 8, 1);
        fill(part, 8, 10, height - 3, height - 2, 8, 10, 1);
        fill(part, 7, 8, height - 2, height - 1, 8, 8, 1);
        cap(part, 5, 7, height + 1, 6, 8);
        cap(part, 8, 10, height - 1, 8, 10);
        for (int y = 4; y < height - 2; y++) {
            set(part, 5, y, 6, MARBLE_EW);
            set(part, 6, y, 6, MARBLE_EW);
        }
        set(part, 6, 3, 6, material(3));
        set(part, 9, 3, 8, material(4));
    }

    private static void steppedMachine(BuildingPart part, int height) {
        fill(part, 5, 10, 3, 4, 5, 10, 1);
        fill(part, 7, 8, 5, height - 2, 6, 9, 1);
        int left = Math.max(4, height - 6);
        int right = Math.max(4, height - 4);
        fill(part, 6, 6, 5, left, 7, 8, 10);
        fill(part, 9, 9, 5, right, 7, 8, 10);
        for (int z = 7; z <= 8; z++) {
            set(part, 6, left + 1, z, material(18));
            set(part, 9, right + 1, z, material(19));
        }
        cap(part, 7, 8, height - 1, 6, 9);
        fill(part, 7, 8, Math.max(5, height / 2), Math.max(5, height / 2), 6, 6, 3);
        // Two short needles grow out of full shoulders, at different heights.
        set(part, 7, height - 1, 7, material(1));
        set(part, 8, height - 1, 8, material(1));
        for (int y = height; y <= height + 1; y++) {
            set(part, 7, y, 7, TECH_NS);
        }
        set(part, 8, height, 8, TECH_NS);
    }

    private static void instrumentBeds(BuildingPart part) {
        // Three offset instruments share the plinth, with low railings on solid bases.
        fill(part, 5, 6, 3, 3, 5, 7, 10);
        for (int x = 5; x <= 6; x++) {
            set(part, x, 4, 5, material(16));
            set(part, x, 4, 6, material(1));
        }
        set(part, 5, 4, 6, material(11));
        fill(part, 9, 10, 3, 5, 6, 8, 1);
        cap(part, 9, 10, 6, 6, 8);
        set(part, 9, 4, 6, material(26));
        fill(part, 7, 9, 3, 3, 9, 10, 10);
        for (int x = 7; x <= 9; x++) {
            set(part, x, 4, 10, GLASS_EW);
        }
        for (int z = 6; z <= 8; z++) {
            set(part, 10, 6, z, GLASS_NS);
        }
    }

    private static void cap(BuildingPart part, int minX, int maxX, int y, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if ((x == minX || x == maxX) && (z == minZ || z == maxZ)) {
                    continue;
                }
                int block = z == minZ ? 16 : z == maxZ ? 17 : x == minX ? 18 : x == maxX ? 19 : 1;
                set(part, x, y, z, material(block));
            }
        }
    }

    private static void rotateAssembly(BuildingPart part, Palette palette, Transform transform, int top) {
        if (transform == Transform.ROTATE_NONE) {
            return;
        }
        BuildingPart before = part.createCopy(part.getName());
        Map<BlockState, PaletteIndex> materials = new HashMap<>();
        palette.getPalette().forEach((index, state) -> materials.put(state, index));
        for (int y = 2; y <= top; y++) {
            for (int x = 4; x <= 11; x++) {
                for (int z = 4; z <= 11; z++) {
                    BlockState state = palette.getPalette().get(before.getC(x, y, z));
                    // Plain cladding also inherits a facing property, but has no directional shape.
                    boolean directional = state.getBlock() instanceof RampBlock || state.getBlock() instanceof SlopeBlock
                            || state.getBlock() instanceof IronBarsBlock;
                    PaletteIndex block = materials.get(directional ? transform.transform(state) : state);
                    if (block == null) {
                        throw new IllegalStateException("Missing rotated deck material: " + state);
                    }
                    set(part, transform.rotateX(x, z), y, transform.rotateZ(x, z), block);
                }
            }
        }
    }

    private static void fill(BuildingPart part, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, int block) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    set(part, x, y, z, material(block));
                }
            }
        }
    }

    private static boolean solid(BuildingPart part, Palette palette, int x, int y, int z) {
        BlockState state = palette.getPalette().get(part.getC(x, y, z));
        return state != null && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static void lift(BuildingPart part, boolean basement, boolean rooftop) {
        int floor = basement || rooftop ? DECK_FLOOR : 0;
        int top = rooftop ? floor + 3 : part.getSliceCount() - 1;
        part.getTeInfo().remove(new BlockPos(1, 0, 7));
        for (int y = basement ? floor : 0; y <= top; y++) {
            boolean landing = y == floor || !basement && !rooftop && (y == 10 || y == 20);
            for (int x = 0; x <= 2; x++) {
                for (int z = 6; z <= 8; z++) {
                    set(part, x, y, z, material(landing && (x != 1 || z != 7) ? 1 : 0));
                }
            }
        }
        // Open the landing back onto the district's street, clear of building walls and plinths.
        for (int y = floor + 1; y <= floor + 3; y++) {
            for (int z = 6; z <= 8; z++) {
                set(part, 3, y, z, material(0));
                set(part, 3, floor, z, material(1));
            }
        }
        if (basement) {
            set(part, 1, floor, 7, material(6));
            part.getTeInfo().put(new BlockPos(1, floor, 7), Map.of("height", LIFT_HEIGHT));
        }
        if (basement || rooftop) {
            set(part, 0, floor, 5, material(1));
            set(part, 0, floor + 1, 5, material(9));
        }
    }

    private static boolean air(BuildingPart part, Palette palette, int x, int y, int z) {
        BlockState state = palette.getPalette().get(part.getC(x, y, z));
        return state != null && state.isAir();
    }

    static boolean isLiftOpening(BuildingPart part, int x, int z) {
        return part.getName().startsWith("c5_") && x == 1 && z == 7 && part.getC(x, 0, z).equals(material(0));
    }

    private static PaletteIndex material(int index) {
        return new PaletteIndex('!', (char) (index < 10 ? '0' + index : 'a' + index - 10));
    }

    private static PaletteIndex authored(char symbol) {
        return new PaletteIndex(symbol, ' ');
    }

    private static void set(BuildingPart part, int x, int y, int z, PaletteIndex block) {
        part.getSlice(y).getSlice().set(z * 16 + x, block);
    }
}
