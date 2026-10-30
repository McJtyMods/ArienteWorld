package mcjty.arienteworld.dimension;

import mcjty.ariente.api.EnumFacingUpDown;
import mcjty.ariente.api.MarbleColor;
import mcjty.ariente.api.TechType;
import mcjty.arienteworld.ArienteStuff;
import mcjty.arienteworld.cities.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.IChunk;

import static mcjty.ariente.api.EnumFacingUpDown.FACING;
import static mcjty.ariente.api.MarbleColor.COLOR;
import static mcjty.ariente.api.TechType.TYPE;

public class LevitatorNetworkGenerator {

    private static boolean initialized = false;
    private static BlockState airChar;
    private static BlockState blueMarble;
    private static BlockState blackMarble;
    private static BlockState horizontalBeam;
    private static BlockState verticalBeam;
    private static BlockState slopeNorth;
    private static BlockState slopeSouth;
    private static BlockState slopeEast;
    private static BlockState slopeWest;
    private static BlockState slopeNorthUp;
    private static BlockState slopeSouthUp;
    private static BlockState slopeEastUp;
    private static BlockState slopeWestUp;
    private static BlockState glowLines;
    private static BlockState lampTop;
    private static BlockState elevator;
    private static BlockState levelMarker;

    private static void initialize() {
        if (!initialized) {
            airChar = Blocks.AIR.getDefaultState();
            blueMarble = ArienteStuff.marble_smooth.getDefaultState().with(COLOR, MarbleColor.BLUE);
            blackMarble = ArienteStuff.marble_smooth.getDefaultState().with(COLOR, MarbleColor.BLACK);
            horizontalBeam = ArienteStuff.fluxBeamBlock.getDefaultState().with(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
            verticalBeam = ArienteStuff.fluxBeamBlock.getDefaultState().with(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
            slopeNorth = ArienteStuff.slopeBlock.getDefaultState().with(FACING, EnumFacingUpDown.NORTH);
            slopeSouth = ArienteStuff.slopeBlock.getDefaultState().with(FACING, EnumFacingUpDown.SOUTH);
            slopeWest = ArienteStuff.slopeBlock.getDefaultState().with(FACING, EnumFacingUpDown.WEST);
            slopeEast = ArienteStuff.slopeBlock.getDefaultState().with(FACING, EnumFacingUpDown.EAST);
            slopeNorthUp = ArienteStuff.slopeBlock.getDefaultState().with(FACING, EnumFacingUpDown.NORTH_UP);
            slopeSouthUp = ArienteStuff.slopeBlock.getDefaultState().with(FACING, EnumFacingUpDown.SOUTH_UP);
            slopeWestUp = ArienteStuff.slopeBlock.getDefaultState().with(FACING, EnumFacingUpDown.WEST_UP);
            slopeEastUp = ArienteStuff.slopeBlock.getDefaultState().with(FACING, EnumFacingUpDown.EAST_UP);
            glowLines = ArienteStuff.blackmarble_techpat.getDefaultState().with(TYPE, TechType.LINES_GLOW);
            lampTop = ArienteStuff.flatLightBlock.getDefaultState().with(BlockStateProperties.FACING, Direction.DOWN);
            elevator = ArienteStuff.elevatorBlock.getDefaultState();
            levelMarker = ArienteStuff.levelMarkerBlock.getDefaultState();
            initialized = true;
        }
    }


    public static void generate(int chunkX, int chunkZ, IChunk primer, IArienteChunkGenerator generator) {
        if (CityTools.isDungeonChunk(chunkX, chunkZ)) {
            City city = CityTools.getNearestDungeon(chunkX, chunkZ);
            if (city != null && city.getPlan().isUnderground()) {
                return;
            }
        }

        initialize();

        int cx = chunkX & 0xf;
        int cz = chunkZ & 0xf;

        boolean candidateX = cx == 8;
        boolean candidateZ = cz == 8;

        // At -X,+Z we have a possible elevator
        // At -X,-Z we have a possible elevator

        BlockPos.Mutable pos = new BlockPos.Mutable();

        if (CityTools.isStationChunk(chunkX, chunkZ)) {
            BuildingPart part = CityTools.getStationPart(chunkX, chunkZ);
            if (part != null) {
                CityPlan station = AssetRegistries.CITYPLANS.get("station");
                int lowest = generator.getDungeonGenerator().generatePart(primer, station.getPalette(), part, Transform.ROTATE_NONE, 0, CityTools.getStationHeight(), 0);

                BlockPos elevatorPos = null;
                if (cx == 7 && cz == 9 && CityTools.isDungeonChunk(chunkX, chunkZ)) {
                    int startz = 12;
                    elevatorPos = createElevatorShaft(chunkX, chunkZ, primer, generator, lowest, startz);
                } else if (cx == 7 && cz == 7 && CityTools.isDungeonChunk(chunkX, chunkZ) && !CityTools.isDungeonChunk(chunkX, chunkZ+2)) {
                    int startz = 3;
                    elevatorPos = createElevatorShaft(chunkX, chunkZ, primer, generator, lowest, startz);
                }
                if (elevatorPos != null) {
                    ArienteChunkGeneratorNew.registerStationLevitatorTodo(new ChunkPos(chunkX, chunkZ), elevatorPos);
                }
            }
        } else if (candidateX) {
            for (int dx = 5 ; dx <= 11 ; dx++) {
                for (int dz = 0 ; dz <= 15 ; dz++) {
                    if (dx == 8) {
                        fillHorizontalBeam(primer, dx, dz);
                    } else if (dx == 5) {
                        fillInnerRamp(primer, dx, dz, slopeEast, slopeEastUp);
                    } else if (dx == 11) {
                        fillInnerRamp(primer, dx, dz, slopeWest, slopeWestUp);
                    } else {
                        fillInner(primer, dx, dz);
                    }
                }
            }
            for (int dz = 0 ; dz <= 15 ; dz++) {
                if (dz == 4 || dz == 11) {
                    fillGlowingSide(primer, 4, dz);
                    fillGlowingSide(primer, 12, dz);
                } else {
                    fillSide(primer, 4, dz);
                    fillSide(primer, 12, dz);
                }
            }
            primer.setBlockState(pos.setPos(8, 36, 8), lampTop, false);
        } else if (candidateZ) {
            for (int dx = 0 ; dx <= 15 ; dx++) {
                for (int dz = 5 ; dz <= 11 ; dz++) {
                    if (dz == 8) {
                        fillVerticalBeam(primer, dx, dz);
                    } else if (dz == 5) {
                        fillInnerRamp(primer, dx, dz, slopeSouth, slopeSouthUp);
                    } else if (dz == 11) {
                        fillInnerRamp(primer, dx, dz, slopeNorth, slopeNorthUp);
                    } else {
                        fillInner(primer, dx, dz);
                    }
                }
            }
            for (int dx = 0 ; dx <= 15 ; dx++) {
                if (dx == 4 || dx == 11) {
                    fillGlowingSide(primer, dx, 4);
                    fillGlowingSide(primer, dx, 12);
                } else {
                    fillSide(primer, dx, 4);
                    fillSide(primer, dx, 12);
                }
            }
            // @todo 1.15
//            primer.data[(8 << 12) | (8 << 8) + 36] = lampTop;
        }
    }

    private static BlockPos createElevatorShaft(int chunkX, int chunkZ, IChunk primer, IArienteChunkGenerator generator, int lowest, int startz) {
        ChunkPos center = CityTools.getNearestDungeonCenter(chunkX, chunkZ);
        City city = CityTools.getCity(center);
        int cityBottom = CityTools.getLowestHeight(city, generator, chunkX, chunkZ);

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int sx = 3 ; sx >= 1 ; sx--) {
            for (int sz = startz ; sz <= startz+2 ; sz++) {
                int y = lowest-1;
                BlockState f = blackMarble;
                if (sx == 2 && sz == startz+1) {
                    f = airChar;
                } else if (sx == 2 || sz == startz+1) {
                    f = glowLines;
                }
                while (y <= cityBottom) {
                    primer.setBlockState(pos.setPos(sx, y, sz), f, false);
                    y++;
                }

            }
        }

        primer.setBlockState(pos.setPos(2, CityTools.getStationHeight(), startz+1), elevator, false);
        primer.setBlockState(pos.setPos(1, CityTools.getStationHeight()+1, startz+2), levelMarker, false);
        primer.setBlockState(pos.setPos(1, cityBottom+1, startz+2), levelMarker, false);
        return new BlockPos(chunkX * 16 + 2, CityTools.getStationHeight(), chunkZ * 16 + startz+1);
    }

    private static void fillInner(IChunk primer, int dx, int dz) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        primer.setBlockState(pos.setPos(dx, 30, dz), blueMarble, false);
        for (int y = 1 ; y < 6 ; y++) {
            primer.setBlockState(pos.setPos(dx, 30+y, dz), airChar, false);
        }
        primer.setBlockState(pos.setPos(dx, 30+6, dz), blueMarble, false);
    }

    private static void fillInnerRamp(IChunk primer, int dx, int dz, BlockState rampBlock, BlockState rampBlockUp) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        primer.setBlockState(pos.setPos(dx, 30, dz), blueMarble, false);
        primer.setBlockState(pos.setPos(dx, 31, dz), rampBlock, false);
        primer.setBlockState(pos.setPos(dx, 32, dz), airChar, false);
        primer.setBlockState(pos.setPos(dx, 33, dz), airChar, false);
        primer.setBlockState(pos.setPos(dx, 34, dz), airChar, false);
        primer.setBlockState(pos.setPos(dx, 35, dz), rampBlock, false);
        primer.setBlockState(pos.setPos(dx, 36, dz), blueMarble, false);
    }

    private static void fillSide(IChunk primer, int dx, int dz) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        primer.setBlockState(pos.setPos(dx, 30, dz), blueMarble, false);
        for (int y = 1 ; y < 6 ; y++) {
            primer.setBlockState(pos.setPos(dx, 30+y, dz), blackMarble, false);
        }
        primer.setBlockState(pos.setPos(dx, 30+6, dz), blueMarble, false);
    }

    private static void fillGlowingSide(IChunk primer, int dx, int dz) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        primer.setBlockState(pos.setPos(dx, 30, dz), blueMarble, false);
        for (int y = 1 ; y < 6 ; y++) {
            primer.setBlockState(pos.setPos(dx, 30+y, dz), glowLines, false);
        }
        primer.setBlockState(pos.setPos(dx, 30+6, dz), blueMarble, false);
    }

    private static void fillHorizontalBeam(IChunk primer, int dx, int dz) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        primer.setBlockState(pos.setPos(dx, 30, dz), blueMarble, false);
        primer.setBlockState(pos.setPos(dx, 31, dz), airChar, false);
        primer.setBlockState(pos.setPos(dx, 32, dz), horizontalBeam, false);
        primer.setBlockState(pos.setPos(dx, 33, dz), airChar, false);
        primer.setBlockState(pos.setPos(dx, 34, dz), airChar, false);
        primer.setBlockState(pos.setPos(dx, 35, dz), airChar, false);
        primer.setBlockState(pos.setPos(dx, 36, dz), blueMarble, false);
    }

    private static void fillVerticalBeam(IChunk primer, int dx, int dz) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        primer.setBlockState(pos.setPos(dx, 30, dz), blueMarble, false);
        primer.setBlockState(pos.setPos(dx, 31, dz), airChar, false);
        primer.setBlockState(pos.setPos(dx, 32, dz), verticalBeam, false);
        primer.setBlockState(pos.setPos(dx, 33, dz), airChar, false);
        primer.setBlockState(pos.setPos(dx, 34, dz), airChar, false);
        primer.setBlockState(pos.setPos(dx, 35, dz), airChar, false);
        primer.setBlockState(pos.setPos(dx, 36, dz), blueMarble, false);
    }
}
