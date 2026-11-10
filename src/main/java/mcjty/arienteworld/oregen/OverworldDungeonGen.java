package mcjty.arienteworld.oregen;

import static mcjty.ariente.setup.Registration.WARPER;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import mcjty.ariente.api.IWarper;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.config.WorldgenConfiguration;
import mcjty.arienteworld.dimension.CityFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class OverworldDungeonGen {

    private static final int ROOM_RADIUS = 6;
    private static final int ROOM_HEIGHT = 5;
    private static final int MAX_ENTRANCE_DISTANCE = ROOM_RADIUS + 8;

    private static final String[][] DUNGEON = {
            {
                    "fffffffffffff",
                    "faaaaaaaaaaaf",
                    "faaaaaaaaaaaf",
                    "faaaaaaaaaaaf",
                    "faaaxaxaxaaaf",
                    "faaaaaaaaaaaf",
                    "faaaxaaaxaaaf",
                    "faaaaaaaaaaaf",
                    "faaaxaxaxaaaf",
                    "faaaaaaaaaaaf",
                    "faaaaaaaaaaaf",
                    "faaaaaaaaaaaf",
                    "fffffffffffff"
            },
            {
                    "fffff   fffff",
                    "f           f",
                    "f           f",
                    "f           f",
                    "f           f",
                    "     sss     ",
                    "     s#s     ",
                    "     sss     ",
                    "f           f",
                    "f           f",
                    "f           f",
                    "f           f",
                    "fffff   fffff"
            },
            {
                    "fffff   fffff",
                    "f           f",
                    "f           f",
                    "f           f",
                    "f           f",
                    "             ",
                    "             ",
                    "             ",
                    "f           f",
                    "f           f",
                    "f           f",
                    "f           f",
                    "fffff   fffff"
            },
            {
                    "fffffffffffff",
                    "f           f",
                    "f           f",
                    "f           f",
                    "f           f",
                    "f           f",
                    "f           f",
                    "f           f",
                    "f           f",
                    "f           f",
                    "f           f",
                    "f           f",
                    "fffffffffffff"
            },
            {
                    "fffffffffffff",
                    "fffffffffffff",
                    "fffffffffffff",
                    "fffffffffffff",
                    "fffffffffffff",
                    "fffffffffffff",
                    "fffffffffffff",
                    "fffffffffffff",
                    "fffffffffffff",
                    "fffffffffffff",
                    "fffffffffffff",
                    "fffffffffffff",
                    "fffffffffffff"
            }
    };

    @Nullable
    public static BlockPos getNearestDungeon(ServerLevel world, BlockPos origin) {
        return getNearestDungeon(world, origin, 19);
    }

    @Nullable
    public static BlockPos getNearestDungeon(ServerLevel world, BlockPos origin, int radius) {
        if (!world.dimension().equals(Level.OVERWORLD)) {
            return null;
        }
        ChunkPos center = new ChunkPos(origin);
        List<BlockPos> candidates = new ArrayList<>();
        // Only seeded entrance candidates are loaded; ordinary chunks in the
        // search area are not generated.
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                if (isDungeonChunk(world, x, z)) {
                    candidates.add(new BlockPos(x * 16 + 8, 0, z * 16 + 8));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> {
            double x = pos.getX() - origin.getX();
            double z = pos.getZ() - origin.getZ();
            return x * x + z * z;
        }));
        for (BlockPos candidate : candidates) {
            BlockPos warper = findDungeon(world, candidate.getX() >> 4, candidate.getZ() >> 4);
            if (warper != null) {
                return warper;
            }
        }
        return null;
    }

    @Nullable
    public static BlockPos findDungeon(ServerLevel world, int chunkX, int chunkZ) {
        var chunk = world.getChunk(chunkX, chunkZ);
        BlockPos center = new BlockPos(chunkX * 16 + 8, getDungeonHeight(world, chunkX, chunkZ) + 1, chunkZ * 16 + 8);
        // Include pending block entities from saved chunks. Asking the world for
        // the entity below also loads its NBT if it has not been promoted yet.
        List<BlockPos> positions = new ArrayList<>(chunk.getBlockEntitiesPos());
        positions.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        for (BlockPos pos : positions) {
            if (Math.abs(pos.getX() - center.getX()) <= 2 && Math.abs(pos.getZ() - center.getZ()) <= 2
                    && chunk.getBlockState(pos).is(WARPER.get()) && world.getBlockEntity(pos) instanceof IWarper) {
                return pos;
            }
        }
        return null;
    }

    /// Return true if this chunk can contain a dungeon
    public static boolean isDungeonChunk(WorldGenLevel world, int chunkX, int chunkZ) {
        // Only for the overworld!
        if (world.getLevel().dimension().equals(Level.OVERWORLD)) {
            Random rnd = new Random(world.getSeed() + chunkX * 198491317L + chunkZ * 776531419L);
            rnd.nextFloat();
            return rnd.nextFloat() < WorldgenConfiguration.OVERWORLD_DUNGEON_CHANCE.get();
        } else {
            return false;
        }
    }

    private static int getDungeonHeight(WorldGenLevel world, int chunkX, int chunkZ) {
        Random rnd = new Random(world.getSeed() + chunkX * 23567813L + chunkZ * 923568029L);
        rnd.nextFloat();
        return rnd.nextInt(25) + 8;
    }

    public static void generate(Random random, int chunkX, int chunkZ, WorldGenLevel world) {
        if (isDungeonChunk(world, chunkX, chunkZ)) {
            Placement placement = findPlacement(world, chunkX, chunkZ);
            BlockPos dungeonPos = placement.floor();
            int midx = dungeonPos.getX();
            int midy = dungeonPos.getY();
            int midz = dungeonPos.getZ();
            for (int dy = 0; dy < DUNGEON.length; dy++) {
                String[] level = DUNGEON[dy];
                for (int dx = 0; dx < level.length; dx++) {
                    String slice = level[dx];
                    for (int dz = 0; dz < slice.length(); dz++) {
                        char c = slice.charAt(dz);
                        BlockPos pos = new BlockPos(midx + dx - level.length / 2, midy + dy, midz + dz - slice.length() / 2);
                        switch (c) {
                            case ' ':
                                set(world, pos, Blocks.AIR.defaultBlockState(), 2);
                                break;
                            case 'a':
                                setBlock(random, world, pos, 15,
                                        LegacyBlockStates.read("ariente:marble@color=black"),
                                        LegacyBlockStates.read("ariente:marble_bricks@color=black"),
                                        LegacyBlockStates.read("ariente:marble_smooth@color=black"));
                                break;
                            case 'f':
                                if (!world.isEmptyBlock(pos)) {
                                    setBlock(random, world, pos, 15,
                                            LegacyBlockStates.read("ariente:marble@color=gray"),
                                            LegacyBlockStates.read("ariente:marble_bricks@color=gray"),
                                            LegacyBlockStates.read("ariente:marble_smooth@color=gray"));
                                }
                                break;
                            case 's':
                                set(world, pos, LegacyBlockStates.read("ariente:marble_slab@color=black,type=bottom"), 2);
                                break;
                            case 'x':
                                set(world, pos, LegacyBlockStates.read("ariente:blacktech@type=red_lines_glow"), 2);
                                break;
                            case '#':
                                set(world, pos, LegacyBlockStates.read("ariente:warper"), 2);
                                break;
                        }
                    }
                }
            }
            Entrance entrance = placement.entrance();
            if (entrance != null) {
                for (int distance = ROOM_RADIUS + 1; distance < entrance.distance(); distance++) {
                    BlockPos pos = dungeonPos.above().relative(entrance.direction(), distance);
                    set(world, pos, Blocks.CAVE_AIR.defaultBlockState(), 2);
                    set(world, pos.above(), Blocks.CAVE_AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static Placement findPlacement(WorldGenLevel world, int chunkX, int chunkZ) {
        BlockPos original = new BlockPos(chunkX * 16 + 8, getDungeonHeight(world, chunkX, chunkZ), chunkZ * 16 + 8);
        Placement best = new Placement(original, null);
        int bestScore = Integer.MAX_VALUE;
        int minY = Math.max(-48, world.getMinBuildHeight() + 8);
        int maxY = Math.min(48, world.getMaxBuildHeight() - ROOM_HEIGHT - 4);
        for (int y = minY; y <= maxY; y++) {
            BlockPos floor = new BlockPos(original.getX(), y, original.getZ());
            if (!hasCover(world, floor)) {
                continue;
            }
            Entrance entrance = findEntrance(world, floor, chunkX, chunkZ);
            if (entrance == null) {
                continue;
            }
            // Prefer natural doorway connections, then short tunnels. Retain the
            // seeded height as the tie-breaker without consuming decoration RNG.
            int score = entrance.distance() * 128 + Math.abs(y - original.getY());
            if (score < bestScore && fitsRoom(world, floor)) {
                best = new Placement(floor, entrance);
                bestScore = score;
            }
        }
        return best;
    }

    @Nullable
    private static Entrance findEntrance(WorldGenLevel world, BlockPos floor, int chunkX, int chunkZ) {
        Entrance best = null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos doorstep = floor.relative(direction, ROOM_RADIUS);
            if (!world.getBlockState(doorstep).isFaceSturdy(world, doorstep, Direction.UP)) {
                continue;
            }
            for (int distance = ROOM_RADIUS + 1; distance <= MAX_ENTRANCE_DISTANCE; distance++) {
                BlockPos feet = floor.above().relative(direction, distance);
                int x = feet.getX() >> 4;
                int z = feet.getZ() >> 4;
                // Do not tunnel into another seeded portal room, even if its
                // chunk has not generated yet. All reads/writes stay in 3x3 chunks.
                if (((x != chunkX || z != chunkZ) && isDungeonChunk(world, x, z))
                        || !canTunnel(world, feet)) {
                    break;
                }
                BlockPos outside = feet.relative(direction);
                if (world.isEmptyBlock(feet) && world.isEmptyBlock(feet.above())
                        && world.isEmptyBlock(outside) && world.isEmptyBlock(outside.above())
                        && world.getBlockState(outside.below()).isFaceSturdy(world, outside.below(), Direction.UP)
                        && isDry(world, outside)) {
                    if (best == null || distance < best.distance()) {
                        best = new Entrance(direction, distance);
                    }
                    break;
                }
            }
        }
        return best;
    }

    private static boolean canTunnel(WorldGenLevel world, BlockPos feet) {
        return hasCover(world, feet) && isNatural(world.getBlockState(feet))
                && isNatural(world.getBlockState(feet.above()))
                && world.getBlockState(feet.below()).isFaceSturdy(world, feet.below(), Direction.UP)
                && isDry(world, feet);
    }

    private static boolean hasCover(WorldGenLevel world, BlockPos pos) {
        return world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, pos.getX(), pos.getZ()) >= pos.getY() + ROOM_HEIGHT + 4;
    }

    private static boolean fitsRoom(WorldGenLevel world, BlockPos floor) {
        int solidFloor = 0;
        int solidRoof = 0;
        for (int x = -ROOM_RADIUS - 1; x <= ROOM_RADIUS + 1; x++) {
            for (int z = -ROOM_RADIUS - 1; z <= ROOM_RADIUS + 1; z++) {
                BlockPos column = floor.offset(x, 0, z);
                boolean inside = Math.abs(x) <= ROOM_RADIUS && Math.abs(z) <= ROOM_RADIUS;
                if (inside && !hasCover(world, column)) {
                    return false;
                }
                for (int y = -1; y <= ROOM_HEIGHT; y++) {
                    BlockPos pos = column.above(y);
                    BlockState state = world.getBlockState(pos);
                    if (!state.getFluidState().isEmpty() || (inside && !isNatural(state))) {
                        return false;
                    }
                    if (inside && state.isFaceSturdy(world, pos, Direction.UP)) {
                        if (y == 0) {
                            solidFloor++;
                        } else if (y == ROOM_HEIGHT - 1) {
                            solidRoof++;
                        }
                    }
                }
            }
        }
        int area = (ROOM_RADIUS * 2 + 1) * (ROOM_RADIUS * 2 + 1);
        return solidFloor * 4 >= area * 3 && solidRoof * 4 >= area * 3;
    }

    private static boolean isNatural(BlockState state) {
        return !state.hasBlockEntity() && (state.isAir() || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.DIRT) || state.is(Blocks.GRAVEL) || state.is(TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:ores")))
                || state.is(Blocks.CLAY) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.CALCITE)
                || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.POINTED_DRIPSTONE));
    }

    private static boolean isDry(WorldGenLevel world, BlockPos feet) {
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 2, 1))) {
            if (!world.getBlockState(pos).getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private record Entrance(Direction direction, int distance) {
    }

    private record Placement(BlockPos floor, @Nullable Entrance entrance) {
    }

    private static void set(WorldGenLevel world, BlockPos pos, BlockState state, int flags) {
        world.setBlock(pos, state, flags);
        if (state.hasBlockEntity() && !CityFeature.hasBlockEntity(state)) {
            world.getChunk(pos).removeBlockEntity(pos);
        }
    }

    private static void setBlock(Random random, WorldGenLevel world, BlockPos pos, int chances, BlockState common, BlockState s1, BlockState s2) {
        switch (random.nextInt(chances)) {
            case 0:
                set(world, pos, s1, 2);
                break;
            case 1:
                set(world, pos, s2, 2);
                break;
            default:
                set(world, pos, common, 2);
                break;
        }

    }
}
