package mcjty.arienteworld.dimension;

import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.PaletteIndex;
import net.minecraft.core.Direction;

import java.util.Random;

/** Different equipment bays inside the cube's existing outer-ring connections. */
final class CubeCorridors {

    private CubeCorridors() {
    }

    static void decorate(BuildingPart part, Random random, int style) {
        for (int deck = 0; deck <= 24; deck += 8) {
            decorateDeck(part, random, style, deck);
        }
    }

    private static void decorateDeck(BuildingPart part, Random random, int style, int deck) {
        boolean[][] routes = routes(part, deck);
        for (int x = 1; x < 15; x++) {
            for (int z = 1; z < 15; z++) {
                if (plain(part, x, deck, z) && air(part, x, deck + 1, z)) {
                    boolean inlay = style % 2 == 0 ? x == 8 : z == 8;
                    set(part, x, deck, z, inlay ? 3 : style == 2 && (x + z) % 5 == 0 ? 10 : 1);
                }
            }
        }
        // Find intact wall panels facing into the room, including the thick outer
        // facade. Full-panel validation excludes door frames, stairs and equipment.
        for (Direction face : Direction.Plane.HORIZONTAL) {
            int count = 0;
            int start = 2 + random.nextInt(3);
            for (int t = start; t <= 13 && count < 2; t++) {
                for (int depth = 0; depth < 16; depth++) {
                    int x = face.getAxis() == Direction.Axis.X ? depth : t;
                    int z = face.getAxis() == Direction.Axis.X ? t : depth;
                    if (panelFits(part, routes, x, z, face, deck)) {
                        panel(part, x, z, face, style, random, deck);
                        count++;
                        t += 4;
                        break;
                    }
                }
            }
        }
    }

    private static boolean[][] routes(BuildingPart part, int deck) {
        boolean[][] routes = new boolean[16][16];
        // Protect a three-wide approach from every authored boundary opening to
        // the centre. This includes blue-dot links, exterior exits and inner doors.
        for (int t = 0; t < 16; t++) {
            for (int[] point : new int[][]{{0, t}, {15, t}, {t, 0}, {t, 15}}) {
                int x = point[0];
                int z = point[1];
                if (air(part, x, deck, z) || !air(part, x, deck + 2, z) || !air(part, x, deck + 3, z)) {
                    continue;
                }
                boolean alongX = x == 0 || x == 15;
                while (x != 8 || z != 8) {
                    protect(routes, x, z);
                    if (alongX && x != 8 || z == 8) {
                        x += Integer.compare(8, x);
                    } else {
                        z += Integer.compare(8, z);
                    }
                }
                protect(routes, 8, 8);
            }
        }
        return routes;
    }

    private static void protect(boolean[][] routes, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (inside(x + dx, z + dz)) {
                    routes[x + dx][z + dz] = true;
                }
            }
        }
    }

    private static boolean panelFits(BuildingPart part, boolean[][] routes, int x, int z, Direction face, int deck) {
        int tx = face.getStepZ();
        int tz = face.getStepX();
        for (int t = -1; t <= 1; t++) {
            int wx = x + tx * t;
            int wz = z + tz * t;
            int nx = wx + face.getStepX();
            int nz = wz + face.getStepZ();
            if (!inside(wx, wz) || nx < 1 || nx > 14 || nz < 1 || nz > 14 || routes[nx][nz]
                    || !plain(part, wx, deck, wz) || air(part, nx, deck, nz)) {
                return false;
            }
            for (int y = deck + 1; y <= deck + 6; y++) {
                if (!plain(part, wx, y, wz) || !air(part, nx, y, nz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void panel(BuildingPart part, int x, int z, Direction face, int style, Random random, int deck) {
        int height = style == 0 ? 4 + random.nextInt(2) : style == 1 ? 3 + random.nextInt(2) : style == 2 ? 5 : 4;
        int tx = face.getStepZ();
        int tz = face.getStepX();
        int fx = face.getStepX();
        int fz = face.getStepZ();
        int light = switch (face) {
            case NORTH -> 20;
            case SOUTH -> 21;
            case WEST -> 22;
            default -> 23;
        };
        for (int t = -1; t <= 1; t++) {
            int wx = x + tx * t;
            int wz = z + tz * t;
            for (int y = 1; y <= height; y++) {
                int backing = style == 2 ? 10 : 1;
                if (style == 0 && t == 0 && y > 1 && y < height) {
                    backing = 28;
                } else if (style == 1 && (y == 3 || y == height)) {
                    backing = 4;
                } else if (style == 2 && t == 0 && y > 1 && y < height) {
                    backing = 13;
                }
                set(part, wx, deck + y, wz, backing);
                if (t != 0 && (style != 3 || y > 2)) {
                    set(part, wx + fx, deck + y, wz + fz, style == 2 ? 25 : 15);
                } else if (style == 3 && y <= 2) {
                    set(part, wx + fx, deck + y, wz + fz, y == 1 ? 1 : t == 0 ? 28 : 7);
                }
            }
        }
        if (style == 0 || style == 3) {
            set(part, x + fx, deck + height - 1, z + fz, light);
        }
        // Different silhouettes: radiator canopy, twin relay arms, a glass cage,
        // or a low console. Cantilevers grow out of the wall, above the route.
        int reach = style == 0 ? 2 : style == 1 ? 3 + random.nextInt(2) : style == 2 ? 1 : 0;
        for (int t = -1; t <= 1; t++) {
            if (style == 1 && t == 0) {
                continue;
            }
            for (int distance = 1; distance <= reach; distance++) {
                int nx = x + tx * t + fx * distance;
                int nz = z + tz * t + fz * distance;
                if (nx < 1 || nx > 14 || nz < 1 || nz > 14 || !air(part, nx, deck + height + 1, nz)) {
                    break;
                }
                boolean clear = true;
                if (distance > 1) {
                    for (int y = 1; y <= height; y++) {
                        clear &= air(part, nx, deck + y, nz);
                    }
                }
                if (!clear) {
                    break;
                }
                set(part, nx, deck + height + 1, nz, distance == reach ? (style == 1 ? 29 : 28) : distance == 1 ? 7 : 15);
            }
        }
    }

    private static boolean inside(int x, int z) {
        return x >= 0 && x < 16 && z >= 0 && z < 16;
    }

    private static boolean plain(BuildingPart part, int x, int y, int z) {
        PaletteIndex index = part.getC(x, y, z);
        return index.equals(new PaletteIndex('e', ' ')) || index.equals(new PaletteIndex('d', ' '));
    }

    private static boolean air(BuildingPart part, int x, int y, int z) {
        PaletteIndex index = part.getC(x, y, z);
        return index.equals(new PaletteIndex(' ', ' ')) || index.equals(new PaletteIndex('!', '0'));
    }

    private static void set(BuildingPart part, int x, int y, int z, int material) {
        part.getSlice(y).getSlice().set(z * 16 + x,
                new PaletteIndex('!', (char) (material < 10 ? '0' + material : 'a' + material - 10)));
    }
}
