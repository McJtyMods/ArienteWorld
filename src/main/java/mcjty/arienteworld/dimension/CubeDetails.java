package mcjty.arienteworld.dimension;

import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.PaletteIndex;
import mcjty.arienteworld.cities.Transform;
import net.minecraft.core.BlockPos;

import java.util.Random;

/** Additions to the cube's authored halls; equipment and the central machinery stay intact. */
public final class CubeDetails {

    private CubeDetails() {
    }

    public static void decorate(BuildingPart part, Random random, int corridorStyle) {
        char section = part.getName().charAt(3);
        if (section == 'C' || section == 'I') {
            return;
        }
        int ceiling = part.getSliceCount() - 1;
        // White vertical ribs are intentional. Only the horizontal white/gray slabs change.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (pale(part.getC(x, 0, z))) {
                    set(part, x, 0, z, material(x == 7 || z == 7 ? 8 : 1));
                }
                if (pale(part.getC(x, ceiling, z))) {
                    set(part, x, ceiling, z, material(1));
                    if (section != 'e' && (x == 5 || x == 11) && (z == 5 || z == 11)) {
                        pendant(part, x, ceiling, z, random);
                    }
                }
            }
        }
        if ("EFGHKLMN".indexOf(section) >= 0) {
            Transform transform = switch (section) {
                case 'F', 'K' -> Transform.ROTATE_270;
                case 'G', 'M' -> Transform.ROTATE_90;
                case 'H', 'N' -> Transform.ROTATE_180;
                default -> Transform.ROTATE_NONE;
            };
            crossEnd(part, transform, "KLMN".indexOf(section) >= 0, random);
        }
        if ("abcdfghi".indexOf(section) >= 0) {
            corridor(part);
        }
        // These faces lead from the outer ring into the inner rooms. Other ring
        // openings (especially their blue dot jambs) are deliberately left alone.
        // The ring owns each door; the adjoining cross end must not add a second plane.
        switch (section) {
            case 't' -> doors(part, true, 15);
            case 'v' -> doors(part, false, 15);
            case 'x' -> doors(part, false, 0);
            case 'z' -> doors(part, true, 0);
            default -> { }
        }
        if ("stuvxyz0".indexOf(section) >= 0) {
            CubeCorridors.decorate(part, random, corridorStyle);
        }
    }

    private static boolean pale(PaletteIndex index) {
        return index.equals(authored('a')) || index.equals(authored('b'));
    }

    private static PaletteIndex authored(char symbol) {
        return new PaletteIndex(symbol, ' ');
    }

    private static PaletteIndex material(int index) {
        return new PaletteIndex('!', (char) (index < 10 ? '0' + index : 'a' + index - 10));
    }

    private static boolean air(BuildingPart part, int x, int y, int z) {
        return part.getC(x, y, z).equals(authored(' ')) || part.getC(x, y, z).equals(material(0));
    }

    private static void set(BuildingPart part, int x, int y, int z, PaletteIndex block) {
        part.getSlice(y).getSlice().set(z * 16 + x, block);
    }

    private static void pendant(BuildingPart part, int x, int ceiling, int z, Random random) {
        int length = 3 + random.nextInt(5);
        for (int h = 1; h <= length; h++) {
            if (!air(part, x, ceiling - h, z)) {
                return;
            }
        }
        for (int h = 1; h <= length; h++) {
            set(part, x, ceiling - h, z, material(h == length ? (random.nextBoolean() ? 28 : 29) : h < 3 ? 7 : 15));
        }
    }

    private static void corridor(BuildingPart part) {
        // Embedded strips, with attached fins above player head height. Never fill
        // the authored openings or replace the white ribs with these details.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if ((x + z) % 8 != 3) {
                    continue;
                }
                for (int y = 6; y <= 22; y++) {
                    if (!part.getC(x, y, z).equals(authored('e'))) {
                        continue;
                    }
                    for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = x + direction[0];
                        int nz = z + direction[1];
                        if (nx > 0 && nx < 15 && nz > 0 && nz < 15 && air(part, nx, y, nz)) {
                            set(part, x, y, z, material(y % 8 == 6 ? 28 : 1));
                            if (y % 8 != 6) {
                                set(part, nx, y, nz, material(15));
                            }
                            break;
                        }
                    }
                }
            }
        }
        // Low ramped equipment plinths sit along the wall, away from the main lane.
        for (int x : new int[]{5, 11}) {
            for (int z : new int[]{5, 11}) {
                if (!part.getC(x, 0, z).equals(material(1)) || !air(part, x, 1, z)) {
                    continue;
                }
                set(part, x, 1, z, authored((x + z) % 4 == 0 ? 'o' : 'n'));
                for (int dx : new int[]{-1, 1}) {
                    if (part.getC(x + dx, 0, z).equals(material(1)) && air(part, x + dx, 1, z)) {
                        set(part, x + dx, 1, z, material(dx < 0 ? 18 : 19));
                    }
                }
            }
        }
    }

    private static void doors(BuildingPart part, boolean alongX, int wall) {
        for (int y = 0; y + 4 < part.getSliceCount(); y++) {
            for (int t = 1; t <= 13; t++) {
                boolean opening = true;
                for (int h = 1; h <= 3; h++) {
                    for (int offset = 0; offset < 2; offset++) {
                        opening &= air(part, alongX ? t + offset : wall, y + h, alongX ? wall : t + offset);
                    }
                    // Restrict doors to plain black jambs, not the blue decorative dot blocks.
                    for (int side : new int[]{t - 1, t + 2}) {
                        PaletteIndex jamb = part.getC(alongX ? side : wall, y + h, alongX ? wall : side);
                        opening &= jamb.equals(authored('e')) || jamb.equals(authored('d'));
                    }
                }
                for (int offset = 0; offset < 2; offset++) {
                    int x = alongX ? t + offset : wall;
                    int z = alongX ? wall : t + offset;
                    opening &= !air(part, x, y, z) && !air(part, x, y + 4, z);
                }
                if (opening) {
                    for (int offset = 0; offset < 2; offset++) {
                        set(part, alongX ? t + offset : wall, y + 1, alongX ? wall : t + offset, material(alongX ? 30 : 31));
                    }
                }
            }
        }
    }

    private static void local(BuildingPart part, Transform transform, int x, int y, int z, PaletteIndex block) {
        BlockPos pos = transform.transform(new BlockPos(x, y, z));
        set(part, pos.getX(), y, pos.getZ(), block);
    }

    private static void crossEnd(BuildingPart part, Transform transform, boolean upper, Random random) {
        if (!upper) {
            // Open the bottom of each cross end into the surrounding ring corridor.
            for (int x = 6; x <= 8; x++) {
                for (int y = 1; y <= 2; y++) {
                    local(part, transform, x, y, 0, material(0));
                }
            }
        }
        // A continuous central walk joins the outer door and the existing cross.
        // Upper ends also get perimeter balconies over the open volume below.
        for (int x = 1; x < 15; x++) {
            for (int z = 1; z < 16; z++) {
                if (!upper || x <= 3 || x >= 12 || z <= 3 || x >= 6 && x <= 9) {
                    local(part, transform, x, 0, z, material(x == 6 || x == 9 ? 8 : 1));
                }
            }
        }
        for (int side : new int[]{0, 1}) {
            int start = side == 0 ? 2 : 11;
            for (int x = start; x < start + 3; x++) {
                for (int z = 2; z <= 5; z++) {
                    local(part, transform, x, 1, z, material(1));
                }
            }
            for (int z = 2; z <= 5; z++) {
                // The ramp symbol is chosen in world orientation to match the rotated bay.
                char[] ramps = side == 0 ? new char[]{'k', 'o', 's', 'n'} : new char[]{'s', 'n', 'k', 'o'};
                int rotation = switch (transform) {
                    case ROTATE_90 -> 1;
                    case ROTATE_180 -> 2;
                    case ROTATE_270 -> 3;
                    default -> 0;
                };
                local(part, transform, side == 0 ? 5 : 10, 1, z, authored(ramps[rotation]));
            }
            int mast = side == 0 ? 3 : 12;
            int height = 12 + random.nextInt(7);
            for (int y = 2; y <= height; y++) {
                local(part, transform, mast, y, 3, material(y > height - 3 ? 15 : y % 5 == 0 ? 28 : 7));
            }
            local(part, transform, mast, height + 1, 3, material(29));
            for (int y = 5; y <= 25; y++) {
                local(part, transform, mast, y, 0, material(y % 7 == 0 ? 12 : 1));
                local(part, transform, side == 0 ? 4 : 11, y, 0, material(28));
            }
            for (int y : new int[]{10, 21}) {
                for (int z : new int[]{5, 11}) {
                    int reach = 2 + random.nextInt(3);
                    for (int distance = 1; distance <= reach; distance++) {
                        local(part, transform, side == 0 ? distance : 15 - distance, y, z,
                                material(distance == reach ? 29 : distance == reach - 1 ? 15 : 7));
                    }
                }
            }
        }
    }
}
