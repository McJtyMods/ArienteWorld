package mcjty.arienteworld.dimension;

import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.PaletteIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.EmptyBlockGetter;

import java.util.Random;
import java.util.Map;

/** Chunk-local details: no world reads, shared random stream or writes into neighboring chunks. */
public final class DungeonInteriors {

    private DungeonInteriors() {
    }

    public static BuildingPart vary(BuildingPart original, Palette palette, long seed, int chunkX, int chunkZ) {
        // Custom palettes can opt out simply by omitting the expansion materials.
        if (!palette.getPalette().containsKey(material(8))) {
            return original;
        }
        Random random = new Random(seed * 6364136223846793005L ^ chunkX * 341873128712L ^ chunkZ * 132897987541L ^ original.getName().hashCode());
        BuildingPart part = original.createCopy(original.getName());
        String name = original.getName();
        if (name.equals("c5_e1") || name.equals("c5_w1")) {
            int dx = (chunkX & 15) - 4;
            int dz = (chunkZ & 15) - 4;
            if (name.equals("c5_e1") && dx >= 2 && dx <= 3 && dz >= 2 && dz <= 3) {
                arena(part, dx - 2, dz - 2);
            } else {
                rooms(part, random, (dx == 2 || dx == 6) && (dz == 2 || dz == 6));
            }
        } else if (!name.startsWith("c5_")) {
            // Two coherent surface treatments for every dungeon and station, including ships.
            // Only full decorative blocks change; doors, equipment, glass and routes stay intact.
            if (random.nextBoolean()) {
                for (int y = 0; y < part.getSliceCount(); y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState state = palette.getPalette().get(part.getC(x, y, z));
                            if (state != null && !CityFeature.hasBlockEntity(state)
                                    && (state.getBlock().getDescriptionId().contains("marble")
                                    || state.getBlock().getDescriptionId().equals("block.ariente.pattern"))
                                    && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                                    && (x % 7 == 2 || z % 7 == 2) && y % 10 == 0) {
                                set(part, x, y, z, x % 7 == 2 && z % 7 == 2 ? 3 : 10);
                            }
                        }
                    }
                }
            }
            if (name.startsWith("c3_1_") && part.getSliceCount() > 10) {
                cavern(part, palette, random);
            }
        }
        if (name.startsWith("c5_")) {
            // Neighboring ring sections use different bay types, with world-seeded
            // dimensions and fittings inside each type.
            int corridorStyle = Math.floorMod(chunkX + chunkZ + (int) (seed ^ (seed >>> 32)), 4);
            CubeDetails.decorate(part, random, corridorStyle);
        }
        return part;
    }

    private static PaletteIndex material(int index) {
        return new PaletteIndex('!', (char) (index < 10 ? '0' + index : 'a' + index - 10));
    }

    private static void set(BuildingPart part, int x, int y, int z, int material) {
        part.getSlice(y).getSlice().set(z * 16 + x, material(material));
    }

    private static void arena(BuildingPart part, int dx, int dz) {
        // Four sections form a 32x32 hall; the central 24x24x30 volume is always clear.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int ax = dx * 16 + x;
                int az = dz * 16 + z;
                set(part, x, 0, z, ax == 3 || ax == 28 || az == 3 || az == 28 ? 3 : 1);
                for (int y = 1; y < 31; y++) {
                    boolean rib = (ax == 0 || ax == 31) && az % 8 == 3
                            || (az == 0 || az == 31) && ax % 8 == 3;
                    set(part, x, y, z, rib ? (y == 8 || y == 22 ? 3 : 7) : 0);
                }
            }
        }
        set(part, dx == 0 ? 2 : 13, 1, dz == 0 ? 2 : 13, 5);
    }

    private static void rooms(BuildingPart part, Random random, boolean elevator) {
        for (int deck = 0; deck <= 20; deck += 10) {
            int west = 2 + random.nextInt(2);
            int east = 12 + random.nextInt(2);
            int cladding = random.nextBoolean() ? 1 : 10;
            // Four bays around intersecting, four-wide circulation corridors.
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    set(part, x, deck, z, x == 6 || z == 6 ? 3 : 1);
                    for (int h = 1; h < 10; h++) {
                        boolean wall = ((x == west || x == east) && (z < 6 || z > 9))
                                || ((z == west || z == east) && (x < 6 || x > 9));
                        boolean doorway = (x == 4 || x == 5 || x == 10 || x == 11
                                || z == 4 || z == 5 || z == 10 || z == 11) && h <= 3;
                        set(part, x, deck + h, z, wall && !doorway ? (h == 1 && (x + z) % 7 == 0 ? 2 : cladding) : 0);
                    }
                }
            }
            for (int x : new int[]{4, 11}) {
                for (int z : new int[]{4, 11}) {
                    int theme = random.nextInt(3);
                    int height = theme == 0 ? 5 : theme == 1 ? 2 : 7;
                    for (int h = 1; h <= height; h++) {
                        set(part, x, deck + h, z, h == height ? 10 : theme == 0 && h == 2 ? 11 : 1);
                    }
                    // Server bays have parallel racks; control bays have a low illuminated desk.
                    if (theme != 2) {
                        int innerX = x == 4 ? 5 : 10;
                        for (int h = 1; h <= (theme == 0 ? 4 : 1); h++) {
                            set(part, innerX, deck + h, z, h == 2 ? 11 : 2);
                        }
                    }
                }
            }
            for (boolean alongX : new boolean[]{false, true}) {
                for (int wall : new int[]{west, east}) {
                    for (int center : new int[]{2, 13}) {
                        wallDetail(part, deck, alongX, wall, center, wall == west ? 1 : -1, random);
                    }
                }
            }
            // Occasional caches sit in bays off the corridor, never in the shaft.
            if (random.nextInt(6) == 0) {
                set(part, 5, deck + 1, 5, 5);
            }
        }
        if (!elevator) {
            return;
        }
        // Open beam shaft with three floor selectors; elevators need no power supply.
        for (int y = 1; y < 30; y++) {
            set(part, 0, y, 7, y == 10 || y == 20 ? 1 : 0);
            set(part, 1, y, 7, 0);
            set(part, 2, y, 7, y == 10 || y == 20 ? 1 : 0);
        }
        set(part, 1, 0, 7, 6);
        part.getTeInfo().put(new BlockPos(1, 0, 7), Map.of("height", 23));
        for (int deck : new int[]{0, 10, 20}) {
            set(part, 0, deck + 1, 5, 9);
        }

    }

    /** Framed wall inserts stay outside the central corridors and below the ceiling. */
    private static void wallDetail(BuildingPart part, int deck, boolean alongX, int wall,
                                   int center, int normal, Random random) {
        int theme = random.nextInt(6);
        if (theme == 5) {
            return; // Some bays remain quiet, flat surfaces.
        }
        int fence = theme == 4 ? 25 : 15;
        int light = alongX ? (normal > 0 ? 21 : 20) : (normal > 0 ? 23 : 22);
        for (int side : new int[]{center - 1, center + 1}) {
            for (int h = 1; h <= 8; h++) {
                wallAttachment(part, alongX, wall + normal, side, deck + h, fence);
            }
        }
        if (theme == 0) {
            wallAttachment(part, alongX, wall + normal, center, deck + 4, light);
        } else if (theme == 1 || theme == 2) {
            for (int h = 3; h <= 7; h++) {
                wallBlock(part, alongX, wall, center, deck + h, theme == 1 ? 26 : 27);
            }
            wallAttachment(part, alongX, wall + normal, center - 1, deck + 5, light, true);
            wallAttachment(part, alongX, wall + normal, center + 1, deck + 5, light, true);
        } else if (theme == 3) {
            for (int side = center - 1; side <= center + 1; side++) {
                wallBlock(part, alongX, wall, side, deck + 4, 12);
            }
            wallAttachment(part, alongX, wall + normal, center, deck + 6, light);
        } else {
            wallBlock(part, alongX, wall, center, deck + 3, 27);
            wallBlock(part, alongX, wall, center, deck + 7, 29);
            wallAttachment(part, alongX, wall + normal, center, deck + 5, light);
        }
    }

    private static void wallBlock(BuildingPart part, boolean alongX, int wall, int side, int y, int block) {
        set(part, alongX ? side : wall, y, alongX ? wall : side, block);
    }

    private static void wallAttachment(BuildingPart part, boolean alongX, int wall, int side, int y, int block) {
        wallAttachment(part, alongX, wall, side, y, block, false);
    }

    private static void wallAttachment(BuildingPart part, boolean alongX, int wall, int side, int y,
                                       int block, boolean replaceRail) {
        int x = alongX ? side : wall;
        int z = alongX ? wall : side;
        PaletteIndex old = part.getC(x, y, z);
        if (old.equals(material(0)) || replaceRail && old.equals(material(15))) {
            set(part, x, y, z, block);
        }
    }

    private static void cavern(BuildingPart part, Palette palette, Random random) {
        // Equipment islands only fit in clear volumes on an existing floor. Keep a broad
        // central cross free for flux-cart intersections and the original circulation routes.
        for (int x : new int[]{3, 10}) {
            for (int z : new int[]{3, 10}) {
                int theme = random.nextInt(3);
                int height = 3 + random.nextInt(6);
                for (int floor = 0; floor < part.getSliceCount() - height - 1; floor++) {
                    boolean fits = true;
                    for (int dx = 0; dx < 3; dx++) {
                        for (int dz = 0; dz < 3; dz++) {
                            BlockState base = palette.getPalette().get(part.getC(x + dx, floor, z + dz));
                            fits &= base != null && base.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                            for (int y = 1; y <= height; y++) {
                                BlockState state = palette.getPalette().get(part.getC(x + dx, floor + y, z + dz));
                                fits &= state != null && state.isAir();
                            }
                        }
                    }
                    if (fits) {
                        for (int y = 1; y <= (theme == 0 ? 2 : height); y++) {
                            set(part, x + 1, floor + y, z + 1, y == height ? 8 : y % 3 == 0 ? 3 : 2);
                            if (y <= 2) {
                                for (int dx = 0; dx < 3; dx++) {
                                    set(part, x + dx, floor + y, z, 2);
                                }
                            }
                        }
                        if (theme == 0) {
                            // A console bank with a recessed light strip.
                            for (int dx = 0; dx < 3; dx++) {
                                set(part, x + dx, floor + 2, z, dx == 1 ? 4 : 2);
                            }
                        } else if (theme == 2) {
                            // Paired cooling stacks joined overhead, with open space beneath.
                            for (int y = 1; y <= height; y++) {
                                set(part, x, floor + y, z + 2, y % 3 == 0 ? 3 : 7);
                            }
                            for (int dx = 0; dx < 3; dx++) {
                                set(part, x + dx, floor + height, z + 2, 2);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }
}
