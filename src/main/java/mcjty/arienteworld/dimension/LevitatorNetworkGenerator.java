package mcjty.arienteworld.dimension;

import mcjty.ariente.api.EnumFacingUpDown;
import mcjty.ariente.api.MarbleColor;
import mcjty.ariente.api.TechType;
import mcjty.ariente.blocks.utility.ElevatorTile;
import net.minecraft.world.level.chunk.ChunkGenerator;
import static mcjty.ariente.setup.Registration.*;
import mcjty.arienteworld.cities.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

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

    private static synchronized void initialize() {
        if (!initialized) {
            airChar = Blocks.AIR.defaultBlockState();
            blueMarble = LegacyBlockStates.read("ariente:marble_smooth@color=blue");
            blackMarble = LegacyBlockStates.read("ariente:marble_smooth@color=black");
            horizontalBeam = FLUX_BEAM.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
            verticalBeam = FLUX_BEAM.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
            slopeNorth = SLOPE.get().defaultBlockState().setValue(FACING, EnumFacingUpDown.NORTH);
            slopeSouth = SLOPE.get().defaultBlockState().setValue(FACING, EnumFacingUpDown.SOUTH);
            slopeWest = SLOPE.get().defaultBlockState().setValue(FACING, EnumFacingUpDown.WEST);
            slopeEast = SLOPE.get().defaultBlockState().setValue(FACING, EnumFacingUpDown.EAST);
            slopeNorthUp = SLOPE.get().defaultBlockState().setValue(FACING, EnumFacingUpDown.NORTH_UP);
            slopeSouthUp = SLOPE.get().defaultBlockState().setValue(FACING, EnumFacingUpDown.SOUTH_UP);
            slopeWestUp = SLOPE.get().defaultBlockState().setValue(FACING, EnumFacingUpDown.WEST_UP);
            slopeEastUp = SLOPE.get().defaultBlockState().setValue(FACING, EnumFacingUpDown.EAST_UP);
            glowLines = LegacyBlockStates.read("ariente:blacktech@type=lines_glow");
            lampTop = FLAT_LIGHT.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN);
            elevator = ELEVATOR.get().defaultBlockState();
            levelMarker = LEVEL_MARKER.get().defaultBlockState();
            initialized = true;
        }
    }


    public static void generate(int chunkX, int chunkZ, CityChunk primer, ChunkGenerator generator) {
        if (CityTools.isDungeonChunk(primer.getLevel().getSeed(), chunkX, chunkZ)) {
            City city = CityTools.getCity(primer.getLevel().getSeed(), CityTools.getNearestDungeonCenter(chunkX, chunkZ));
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

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        if (CityTools.isStationChunk(chunkX, chunkZ)) {
            BuildingPart part = CityTools.getStationPart(primer.getLevel().getSeed(), chunkX, chunkZ);
            if (part != null) {
                CityPlan station = AssetRegistries.CITYPLANS.get("station");
                int lowest = primer.placePart(station.getPalette(), part, Transform.ROTATE_NONE, 0, CityTools.getStationHeight(), 0);

                BlockPos elevatorPos = null;
                if (cx == 7 && cz == 9 && CityTools.isDungeonChunk(primer.getLevel().getSeed(), chunkX, chunkZ)) {
                    int startz = 12;
                    elevatorPos = createElevatorShaft(chunkX, chunkZ, primer, generator, lowest, startz);
                } else if (cx == 7 && cz == 7 && CityTools.isDungeonChunk(primer.getLevel().getSeed(), chunkX, chunkZ) && !CityTools.isDungeonChunk(primer.getLevel().getSeed(), chunkX, chunkZ+2)) {
                    int startz = 3;
                    elevatorPos = createElevatorShaft(chunkX, chunkZ, primer, generator, lowest, startz);
                }
                if (elevatorPos != null) {
                    if (primer.getLevel().getBlockEntity(elevatorPos) instanceof ElevatorTile tile) {
                        City city = CityTools.getCity(primer.getLevel().getSeed(), CityTools.getNearestDungeonCenter(chunkX, chunkZ));
                        int floor = CityTools.getCityHeight(city, generator, primer.getLevel(), primer.getLevel().getLevel().getChunkSource().randomState())
                                - CityTools.getCellarHeight(primer.getLevel().getSeed(), city, chunkX, chunkZ);
                        tile.setHeight(floor - CityTools.getStationHeight() + 5);
                    }
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
            primer.setBlockState(pos.set(8, 36, 8), lampTop, false);
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
            primer.setBlockState(pos.set(8, 36, 8), lampTop, false);
        }
    }

    private static BlockPos createElevatorShaft(int chunkX, int chunkZ, CityChunk primer, ChunkGenerator generator, int lowest, int startz) {
        ChunkPos center = CityTools.getNearestDungeonCenter(chunkX, chunkZ);
        City city = CityTools.getCity(primer.getLevel().getSeed(), center);
        int cityBottom = CityTools.getCityHeight(city, generator, primer.getLevel(), primer.getLevel().getLevel().getChunkSource().randomState())
                - CityTools.getCellarHeight(primer.getLevel().getSeed(), city, chunkX, chunkZ);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
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
                    primer.setBlockState(pos.set(sx, y, sz), f, false);
                    y++;
                }

            }
        }

        primer.setBlockState(pos.set(2, CityTools.getStationHeight(), startz+1), elevator, false);
        primer.setBlockState(pos.set(1, CityTools.getStationHeight()+1, startz+2), levelMarker, false);
        primer.setBlockState(pos.set(1, cityBottom+1, startz+2), levelMarker, false);
        return new BlockPos(chunkX * 16 + 2, CityTools.getStationHeight(), chunkZ * 16 + startz+1);
    }

    private static void fillInner(CityChunk primer, int dx, int dz) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        primer.setBlockState(pos.set(dx, 30, dz), blueMarble, false);
        for (int y = 1 ; y < 6 ; y++) {
            primer.setBlockState(pos.set(dx, 30+y, dz), airChar, false);
        }
        primer.setBlockState(pos.set(dx, 30+6, dz), blueMarble, false);
    }

    private static void fillInnerRamp(CityChunk primer, int dx, int dz, BlockState rampBlock, BlockState rampBlockUp) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        primer.setBlockState(pos.set(dx, 30, dz), blueMarble, false);
        primer.setBlockState(pos.set(dx, 31, dz), rampBlock, false);
        primer.setBlockState(pos.set(dx, 32, dz), airChar, false);
        primer.setBlockState(pos.set(dx, 33, dz), airChar, false);
        primer.setBlockState(pos.set(dx, 34, dz), airChar, false);
        primer.setBlockState(pos.set(dx, 35, dz), rampBlockUp, false);
        primer.setBlockState(pos.set(dx, 36, dz), blueMarble, false);
    }

    private static void fillSide(CityChunk primer, int dx, int dz) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        primer.setBlockState(pos.set(dx, 30, dz), blueMarble, false);
        for (int y = 1 ; y < 6 ; y++) {
            primer.setBlockState(pos.set(dx, 30+y, dz), blackMarble, false);
        }
        primer.setBlockState(pos.set(dx, 30+6, dz), blueMarble, false);
    }

    private static void fillGlowingSide(CityChunk primer, int dx, int dz) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        primer.setBlockState(pos.set(dx, 30, dz), blueMarble, false);
        for (int y = 1 ; y < 6 ; y++) {
            primer.setBlockState(pos.set(dx, 30+y, dz), glowLines, false);
        }
        primer.setBlockState(pos.set(dx, 30+6, dz), blueMarble, false);
    }

    private static void fillHorizontalBeam(CityChunk primer, int dx, int dz) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        primer.setBlockState(pos.set(dx, 30, dz), blueMarble, false);
        primer.setBlockState(pos.set(dx, 31, dz), airChar, false);
        primer.setBlockState(pos.set(dx, 32, dz), horizontalBeam, false);
        primer.setBlockState(pos.set(dx, 33, dz), airChar, false);
        primer.setBlockState(pos.set(dx, 34, dz), airChar, false);
        primer.setBlockState(pos.set(dx, 35, dz), airChar, false);
        primer.setBlockState(pos.set(dx, 36, dz), blueMarble, false);
    }

    private static void fillVerticalBeam(CityChunk primer, int dx, int dz) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        primer.setBlockState(pos.set(dx, 30, dz), blueMarble, false);
        primer.setBlockState(pos.set(dx, 31, dz), airChar, false);
        primer.setBlockState(pos.set(dx, 32, dz), verticalBeam, false);
        primer.setBlockState(pos.set(dx, 33, dz), airChar, false);
        primer.setBlockState(pos.set(dx, 34, dz), airChar, false);
        primer.setBlockState(pos.set(dx, 35, dz), airChar, false);
        primer.setBlockState(pos.set(dx, 36, dz), blueMarble, false);
    }
}
