package mcjty.arienteworld.dimension;

import mcjty.ariente.api.MarbleColor;
import mcjty.arienteworld.ArienteStuff;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.cities.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.IChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ArienteDungeonGenerator {

    private static Set<BlockState> rotatableChars = null;
    private boolean initialized = false;
    private IArienteChunkGenerator generator;

    private BlockState airChar;
    private BlockState fillerChar;
    private BlockState cityWallChar;
    private BlockState cityWallTop;

    public void initialize(IArienteChunkGenerator generator) {
        this.generator = generator;
        if (!initialized) {
            airChar = Blocks.AIR.getDefaultState();
//            baseChar = ArienteStuff.marble.getDefaultState();
            fillerChar = ArienteStuff.marble_bricks.getDefaultState().with(MarbleColor.COLOR, MarbleColor.BLACK);
            cityWallChar = ArienteStuff.marble.getDefaultState().with(MarbleColor.COLOR, MarbleColor.BLACK);
            cityWallTop = ArienteStuff.marble_smooth.getDefaultState().with(MarbleColor.COLOR, MarbleColor.BLACK);

            initialized = true;
        }
    }

    public BlockState getCityWallChar() {
        return cityWallChar;
    }

    public BlockState getCityWallTop() {
        return cityWallTop;
    }

    public static Set<BlockState> getRotatableChars() {
        if (rotatableChars == null) {
            rotatableChars = new HashSet<>();
            addStates(Blocks.ACACIA_STAIRS, rotatableChars);
            addStates(Blocks.BIRCH_STAIRS, rotatableChars);
            addStates(Blocks.BRICK_STAIRS, rotatableChars);
            addStates(Blocks.QUARTZ_STAIRS, rotatableChars);
            addStates(Blocks.STONE_BRICK_STAIRS, rotatableChars);
            addStates(Blocks.DARK_OAK_STAIRS, rotatableChars);
            addStates(Blocks.JUNGLE_STAIRS, rotatableChars);
            addStates(Blocks.NETHER_BRICK_STAIRS, rotatableChars);
            addStates(Blocks.OAK_STAIRS, rotatableChars);
            addStates(Blocks.PURPUR_STAIRS, rotatableChars);
            addStates(Blocks.RED_SANDSTONE_STAIRS, rotatableChars);
            addStates(Blocks.SANDSTONE_STAIRS, rotatableChars);
            addStates(Blocks.SPRUCE_STAIRS, rotatableChars);
            addStates(Blocks.STONE_STAIRS, rotatableChars);
            addStates(Blocks.LADDER, rotatableChars);
            addStates(ArienteStuff.fluxBeamBlock, rotatableChars);
            addStates(ArienteStuff.slopeBlock, rotatableChars);
            addStates(ArienteStuff.rampBlock, rotatableChars);
            addStates(ArienteStuff.storageBlock, rotatableChars);
            addStates(ArienteStuff.flatLightBlock, rotatableChars);
            addStates(ArienteStuff.doorMarkerBlock, rotatableChars);
        }
        return rotatableChars;
    }

    private static void addStates(Block block, Set<BlockState> set) {
        // @todo, other properties as well...
        set.add(block.getDefaultState());
//
//        for (int m = 0; m < 16; m++) {
//            try {
//                BlockState state = block.getDefaultState(); // @todo 1.15 meta getStateFromMeta(m);
//                set.add((char) Block.BLOCK_STATE_IDS.get(state));
//            } catch (Exception e) {
//                // Ignore
//            }
//        }
    }

    public void generate(int x, int z, IChunk primer) {
        if (CityTools.isPortalChunk(x, z)) {
            int minHeight = getPortalHeight(generator, x, z);
            generatePart(primer, "portal", AssetRegistries.PARTS.get("portal"), Transform.ROTATE_NONE, 4, minHeight, 4);

            return;
        }

        City city = CityTools.getNearestDungeon(x, z);
        if (city != null) {
            List<BuildingPart> parts = CityTools.getBuildingParts(city, x, z);
            if (!parts.isEmpty()) {
                int lowestY = CityTools.getLowestHeight(city, generator, x, z);
                int y = lowestY;
                CityPlan plan = city.getPlan();
                for (BuildingPart part : parts) {
                    generatePart(primer, plan.getPalette(), part, Transform.ROTATE_NONE, 0, y, 0);
                    y += part.getSliceCount();
                }

                // Make pilars down if needed
                if (!city.getPlan().isFloating()) {
                    CityIndex cityIndex = CityTools.getDungeonIndex(x, z);
                    assert cityIndex != null;
                    if (cityIndex.isTopLeft()) {
                        fillDown(primer, lowestY, 2, 2);
                    }
                    if (cityIndex.isTopRight()) {
                        fillDown(primer, lowestY, 13, 2);
                    }
                    if (cityIndex.isBottomLeft()) {
                        fillDown(primer, lowestY, 2, 13);
                    }
                    if (cityIndex.isBottomRight()) {
                        fillDown(primer, lowestY, 13, 13);
                    }
                }
            }
        }
    }

    public static int getPortalHeight(IArienteChunkGenerator generator, int x, int z) {
        ChunkHeightmap heightmap = generator.getHeightmap(x, z);
        int minHeight = 160;
        for (int dx = 4 ; dx <= 10 ; dx++) {
            for (int dz = 4 ; dz <= 10 ; dz++) {
                int h = heightmap.getHeight(dx, dz);
                if (h < minHeight) {
                    minHeight = h;
                }
            }
        }
        return minHeight;
    }

    private void fillDown(IChunk primer, int lowestY, int dx, int dz) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int y = lowestY-1;
        int index = (dx << 12) | (dz << 8);
        while (y > 1) {
            pos.setPos(dx, y, dz);
            if (primer.getBlockState(pos) == airChar) {
                primer.setBlockState(pos, fillerChar, false);
            } else {
                break;
            }
            y--;
        }
    }

    public int generatePart(IChunk primer, String palette,
                                    BuildingPart part,
                                    Transform transform,
                                    int ox, int oy, int oz) {
        CompiledPalette compiledPalette = CompiledPalette.getCompiledPalette(palette);

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = 0; x < part.getXSize(); x++) {
            for (int z = 0; z < part.getZSize(); z++) {
                BuildingPart.PalettedSlice vs = part.getVSlice(x, z);
                if (vs != null) {
                    int rx = ox + transform.rotateX(x, z);
                    int rz = oz + transform.rotateZ(x, z);
                    int index = 0;
                    int len = vs.getSlice().size();
                    boolean allSpaces = true;
                    for (int y = 0; y < len; y++) {
                        PaletteIndex c = vs.getSlice().get(y);
                        BlockState b = compiledPalette.get(c);
                        if (b == null) {
                            ArienteWorld.setup.getLogger().error("Could not find entry '" + c + "' in the palette for part '" + part.getName() + "'!");
                            b = airChar;
                        }

                        if (transform != Transform.ROTATE_NONE) {
                            if (getRotatableChars().contains(b)) {
                                b = b.rotate(transform.getMcRotation());
                            }
                        }
                        if (allSpaces) {
                            // Skip all initial spaces. This is to avoid ugly generation issues with floating cities
                            if (b != airChar) {
                                primer.setBlockState(pos.setPos(rx, oy+index, rz), b, false);
                                allSpaces = false;
                            }
                        } else {
                            primer.setBlockState(pos.setPos(rx, oy+index, rz), b, false);
                        }
                        index++;
                    }
                }
            }
        }
        return oy + part.getSliceCount();
    }


}
