package mcjty.arienteworld.oregen;

import mcjty.ariente.api.IWarper;
import mcjty.ariente.api.MarbleColor;
import mcjty.ariente.api.TechType;
import mcjty.arienteworld.ArienteStuff;
import mcjty.arienteworld.config.WorldgenConfiguration;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.state.properties.SlabType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationSettings;

import java.util.Random;

import static mcjty.ariente.api.MarbleColor.COLOR;
import static mcjty.ariente.api.TechType.TYPE;

public class OverworldDungeonGen {

    private static String[][] dungeon = {
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

    public static BlockPos getNearestDungeon(World world, BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos);
        if (isValidDungeonChunk(world, cp.x, cp.z)) {
            return getDungeonPos(world, cp.x, cp.z);
        }
        for (int d = 1 ; d < 20 ; d++) {
            for (int m = 0 ; m < d*2 ; m++) {
                int cx = cp.x - d + m;
                int cz = cp.z - d;
                if (isValidDungeonChunk(world, cx, cz)) {
                    return getDungeonPos(world, cx, cz);
                }
                cx = cp.x + d;
                cz = cp.z - d + m;
                if (isValidDungeonChunk(world, cx, cz)) {
                    return getDungeonPos(world, cx, cz);
                }
                cx = cp.x + d - m;
                cz = cp.z + d;
                if (isValidDungeonChunk(world, cx, cz)) {
                    return getDungeonPos(world, cx, cz);
                }
                cx = cp.x - d;
                cz = cp.z + d - m;
                if (isValidDungeonChunk(world, cx, cz)) {
                    return getDungeonPos(world, cx, cz);
                }
            }
        }
        return null;
    }

    public static BlockPos getDungeonPos(IWorld world, int cx, int cz) {
        return new BlockPos(cx * 16 + 8, getDungeonHeight(world, cx, cz), cz * 16 + 8);
    }


    /// Return true if this chunk can contain a dungeon and the warper is still present
    public static boolean isValidDungeonChunk(IWorld world, int chunkX, int chunkZ) {
        if (isDungeonChunk(world, chunkX, chunkZ)) {
            BlockPos pos = getDungeonPos(world, chunkX, chunkZ);
            for (int dx = -2 ; dx <= 2 ; dx++) {
                for (int dy = -2 ; dy <= 2 ; dy++) {
                    for (int dz = -2 ; dz <= 2 ; dz++) {
                        TileEntity te = world.getTileEntity(pos.add(dx, dy, dz));
                        if (te instanceof IWarper) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /// Return true if this chunk can contain a dungeon
    public static boolean isDungeonChunk(IWorld world, int chunkX, int chunkZ) {
        // Only for the overworld!
        if (world.getDimension().getType() == DimensionType.OVERWORLD) {
            Random rnd = new Random(world.getSeed() + chunkX * 198491317L + chunkZ * 776531419L);
            rnd.nextFloat();
            return rnd.nextFloat() < WorldgenConfiguration.OVERWORLD_DUNGEON_CHANCE.get();
        } else {
            return false;
        }
    }

    private static int getDungeonHeight(IWorld world, int chunkX, int chunkZ) {
        Random rnd = new Random(world.getSeed() + chunkX * 23567813L + chunkZ * 923568029L);
        rnd.nextFloat();
        return rnd.nextInt(25) + 8;
    }

    public static void generate(Random random, int chunkX, int chunkZ, IWorld world, ChunkGenerator<? extends GenerationSettings> generator) {
        if (isDungeonChunk(world, chunkX, chunkZ)) {
            BlockPos dungeonPos = getDungeonPos(world, chunkX, chunkZ);
            int midx = dungeonPos.getX();
            int midy = dungeonPos.getY();
            int midz = dungeonPos.getZ();
            for (int dy = 0; dy < dungeon.length; dy++) {
                String[] level = dungeon[dy];
                for (int dx = 0; dx < level.length; dx++) {
                    String slice = level[dx];
                    for (int dz = 0; dz < slice.length(); dz++) {
                        char c = slice.charAt(dz);
                        BlockPos pos = new BlockPos(midx + dx - level.length / 2, midy + dy, midz + dz - slice.length() / 2);
                        switch (c) {
                            case ' ':
                                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                                break;
                            case 'a':
                                setBlock(random, world, pos, 15,
                                        ArienteStuff.marble.getDefaultState().with(COLOR, MarbleColor.BLACK),
                                        ArienteStuff.marble_bricks.getDefaultState().with(COLOR, MarbleColor.BLACK),
                                        ArienteStuff.marble_smooth.getDefaultState().with(COLOR, MarbleColor.BLACK));
                                break;
                            case 'f':
                                if (!world.isAirBlock(pos)) {
                                    setBlock(random, world, pos, 15,
                                            ArienteStuff.marble.getDefaultState().with(COLOR, MarbleColor.GRAY),
                                            ArienteStuff.marble_bricks.getDefaultState().with(COLOR, MarbleColor.GRAY),
                                            ArienteStuff.marble_smooth.getDefaultState().with(COLOR, MarbleColor.GRAY));
                                }
                                break;
                            case 's':
                                world.setBlockState(pos, ArienteStuff.marbleSlabBlock.getDefaultState()
                                        .with(SlabBlock.TYPE, SlabType.BOTTOM)
                                        .with(COLOR, MarbleColor.BLACK), 2);
                                break;
                            case 'x':
                                world.setBlockState(pos, ArienteStuff.blackmarble_techpat.getDefaultState().with(TYPE, TechType.RED_LINES_GLOW), 2);
                                break;
                            case '#':
                                world.setBlockState(pos, ArienteStuff.warperBlock.getDefaultState(), 2);
                                break;
                        }
                    }
                }
            }
        }
    }

    private static void setBlock(Random random, IWorld world, BlockPos pos, int chances, BlockState common, BlockState s1, BlockState s2) {
        switch (random.nextInt(chances)) {
            case 0:
                world.setBlockState(pos, s1, 2);
                break;
            case 1:
                world.setBlockState(pos, s2, 2);
                break;
            default:
                world.setBlockState(pos, common, 2);
                break;
        }

    }
}
