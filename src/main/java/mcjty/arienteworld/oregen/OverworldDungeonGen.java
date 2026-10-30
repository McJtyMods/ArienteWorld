package mcjty.arienteworld.oregen;

import mcjty.ariente.api.IWarper;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.config.WorldgenConfiguration;
import mcjty.arienteworld.dimension.CityFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static mcjty.ariente.setup.Registration.WARPER;

public class OverworldDungeonGen {

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
        if (!world.dimension().equals(Level.OVERWORLD)) {
            return null;
        }
        ChunkPos center = new ChunkPos(origin);
        List<BlockPos> candidates = new ArrayList<>();
        // Retain the original nineteen-chunk search radius. Only seeded entrance
        // candidates are loaded; ordinary chunks in the search area are not generated.
        for (int x = center.x - 19; x <= center.x + 19; x++) {
            for (int z = center.z - 19; z <= center.z + 19; z++) {
                if (isDungeonChunk(world, x, z)) {
                    candidates.add(getDungeonPos(world, x, z));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> {
            double x = pos.getX() - origin.getX();
            double z = pos.getZ() - origin.getZ();
            return x * x + z * z;
        }));
        for (BlockPos candidate : candidates) {
            world.getChunkAt(candidate);
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        BlockPos pos = candidate.offset(x, y, z);
                        if (world.getBlockState(pos).is(WARPER.get())
                                && world.getBlockEntity(pos) instanceof IWarper) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static BlockPos getDungeonPos(WorldGenLevel world, int x, int z) {
        return new BlockPos(x * 16 + 8, getDungeonHeight(world, x, z), z * 16 + 8);
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
            BlockPos dungeonPos = getDungeonPos(world, chunkX, chunkZ);
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
        }
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
