package mcjty.arienteworld.dimension;

import mcjty.ariente.api.MarbleColor;
import mcjty.ariente.api.TechType;
import mcjty.arienteworld.dimension.terrain.ArienteBiomeLayout;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import mcjty.arienteworld.cities.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class ArienteLandscapeCity {

    public static final int CITY_LEVEL = 65;    // @todo make variable?
    public static final int NUM_PARKS = 9;
    public static final int NUM_BUILDINGS = 13;
    public static final int MAX_STRIP_LENGTH = 6;
    public static final int CITYLEV_HEIGHT = 6;        // The height of city levitator section

    public static final String CITY_PALETTE = "landscapecities";

    private static final Cache<Long, ArienteBiomeLayout> LAYOUTS = CacheBuilder.newBuilder().maximumSize(8).build();
    private static final Cache<ChunkPos, LStripInfo> STRIPS = CacheBuilder.newBuilder().maximumSize(16384).build();

    // For the citylev network (city levitator network)
    private static class LStripInfo {
        private final boolean isLevel0Chunk;
        private final int length;
        private final int direction; // Like Direction.ordinal() (2, 3, 4, 5)
        private Boolean isLevel1Chunk;
        private Boolean containsStrip;

        public LStripInfo(int chunkX, int chunkZ) {
            Random random = new Random(chunkX * 132897987541L + chunkZ * 341873128712L);
            random.nextFloat();

            boolean gridpoint = (chunkX % 2) == 0 && (chunkZ % 2) == 0;

            isLevel0Chunk = gridpoint && random.nextFloat() < .1;
            length = random.nextInt(MAX_STRIP_LENGTH+1-2) + 2;
            direction = random.nextInt(4) + 2;

            if (random.nextFloat() < .1 || !gridpoint) {
                isLevel1Chunk = false;      // Already set to false
            }
        }
    }

    private static LStripInfo getStripInfo(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        LStripInfo info = STRIPS.getIfPresent(pos);
        if (info == null) {
            info = new LStripInfo(chunkX, chunkZ);
            STRIPS.put(pos, info);
        }
        return info;
    }

    private static boolean isStripStartStation(int chunkX, int chunkZ) {
        LStripInfo info0 = getStripInfo(chunkX, chunkZ);
        if (info0.isLevel0Chunk) {
            return true;
        }
        if (info0.isLevel1Chunk != null) {
            return info0.isLevel1Chunk;
        }
        boolean level1 = false;
        for (int i = 1 ; i <= MAX_STRIP_LENGTH ; i++) {
            LStripInfo info;
            info = getStripInfo(chunkX + i, chunkZ);
            if (info.isLevel0Chunk && info.direction == Direction.WEST.ordinal() && info.length > i) {
                level1 = true;
                break;
            }
            info = getStripInfo(chunkX - i, chunkZ);
            if (info.isLevel0Chunk && info.direction == Direction.EAST.ordinal() && info.length > i) {
                level1 = true;
                break;
            }
            info = getStripInfo(chunkX, chunkZ + i);
            if (info.isLevel0Chunk && info.direction == Direction.NORTH.ordinal() && info.length > i) {
                level1 = true;
                break;
            }
            info = getStripInfo(chunkX, chunkZ - i);
            if (info.isLevel0Chunk && info.direction == Direction.SOUTH.ordinal() && info.length > i) {
                level1 = true;
                break;
            }
        }
        info0.isLevel1Chunk = level1;
        return level1;
    }

    public static boolean isCityLevitatorChunk(int chunkX, int chunkZ) {
        LStripInfo info0 = getStripInfo(chunkX, chunkZ);

        if (info0.containsStrip != null) {
            return info0.containsStrip;
        }

        if (info0.isLevel0Chunk) {
            info0.containsStrip = true;
            return true;
        }
        if (info0.isLevel1Chunk != null && info0.isLevel1Chunk) {
            info0.containsStrip = true;
            return true;
        }

        for (int i = 1 ; i <= MAX_STRIP_LENGTH ; i++) {
            LStripInfo info;
            info = getStripInfo(chunkX + i, chunkZ);
            if (isStripStartStation(chunkX+i, chunkZ) && info.direction == Direction.WEST.ordinal() && info.length > i) {
                info0.containsStrip = true;
                return true;
            }
            info = getStripInfo(chunkX - i, chunkZ);
            if (isStripStartStation(chunkX-i, chunkZ) && info.direction == Direction.EAST.ordinal() && info.length > i) {
                info0.containsStrip = true;
                return true;
            }
            info = getStripInfo(chunkX, chunkZ + i);
            if (isStripStartStation(chunkX, chunkZ+i) && info.direction == Direction.NORTH.ordinal() && info.length > i) {
                info0.containsStrip = true;
                return true;
            }
            info = getStripInfo(chunkX, chunkZ - i);
            if (isStripStartStation(chunkX, chunkZ-i) && info.direction == Direction.SOUTH.ordinal() && info.length > i) {
                info0.containsStrip = true;
                return true;
            }
        }

        info0.containsStrip = false;
        return false;
    }


    public static boolean isLandscapeCityChunk(long seed, int chunkX, int chunkZ) {
        ArienteBiomeLayout layout = LAYOUTS.getIfPresent(seed);
        if (layout == null) {
            layout = new ArienteBiomeLayout(seed);
            LAYOUTS.put(seed, layout);
        }
        return layout.isCityChunk(chunkX, chunkZ);
    }

    // Only relevant on city chunks. Returns the part name
    public static Pair<String, Transform> getBuildingPart(int chunkX, int chunkZ) {
        Random random = new Random(chunkX * 234516783139L + chunkZ * 567000003533L);
        random.nextFloat();
        if (random.nextFloat() < .6) {
            return Pair.of("park" + (random.nextInt(NUM_PARKS)+1), Transform.random(random));
        } else {
            return Pair.of("building" + (random.nextInt(NUM_BUILDINGS)+1), Transform.random(random));
        }
    }

    // Used when there is no room for a building
    public static Pair<String, Transform> getParkPart(int chunkX, int chunkZ) {
        Random random = new Random(chunkX * 234516783139L + chunkZ * 567000003533L);
        random.nextFloat();
        return Pair.of("park" + (random.nextInt(NUM_PARKS)+1), Transform.random(random));
    }

    // Only relevant on city chunks. Returns the height of this city part (lower y for the building)
    public static int getBuildingYOffset(int chunkX, int chunkZ) {
        Random random = new Random(chunkZ * 593441843L + chunkX * 217645177L);
        random.nextFloat();
        if (isCityLevitatorChunk(chunkX, chunkZ)) {
            // If this is a building above a citylev network then we start the building right
            // above this
            return CITY_LEVEL + CITYLEV_HEIGHT;
        } else {
            return CITY_LEVEL + random.nextInt(6);
        }
    }

    // Indexed by bitmask for all four directions:
    // <-X> <-Z> <+X> <+Z>
    private static final List<Pair<String, Transform>> CITYLEV_PARTS = Arrays.asList(
            Pair.of("citylev_straight", Transform.ROTATE_NONE),     // 0000: Impossible configuration
            Pair.of("citylev_end", Transform.ROTATE_270),           // 0001: OK
            Pair.of("citylev_end", Transform.ROTATE_180),           // 0010: OK
            Pair.of("citylev_bend", Transform.ROTATE_270),          // 0011: OK
            Pair.of("citylev_end", Transform.ROTATE_90),            // 0100: OK
            Pair.of("citylev_straight", Transform.ROTATE_90),       // 0101: OK
            Pair.of("citylev_bend", Transform.ROTATE_180),          // 0110: OK
            Pair.of("citylev_three", Transform.ROTATE_270),         // 0111: OK
            Pair.of("citylev_end", Transform.ROTATE_NONE),          // 1000: OK
            Pair.of("citylev_bend", Transform.ROTATE_NONE),         // 1001: OK
            Pair.of("citylev_straight", Transform.ROTATE_NONE),     // 1010: OK
            Pair.of("citylev_three", Transform.ROTATE_NONE),        // 1011: OK
            Pair.of("citylev_bend", Transform.ROTATE_90),           // 1100: OK
            Pair.of("citylev_three", Transform.ROTATE_90),          // 1101: OK
            Pair.of("citylev_three", Transform.ROTATE_180),         // 1110: OK
            Pair.of("citylev_cross", Transform.ROTATE_NONE)         // 1111: OK
    );

    public static void generate(int chunkX, int chunkZ, CityChunk primer) {
        BlockState baseChar = LegacyBlockStates.read("ariente:marble");
        BlockState fillChar = LegacyBlockStates.read("ariente:marble@color=black");

        boolean undergroundDungeon = false;
        if (CityTools.isDungeonChunk(primer.getLevel().getSeed(), chunkX, chunkZ)) {
            ChunkPos center = CityTools.getNearestDungeonCenter(chunkX, chunkZ);
            City city = CityTools.getCity(primer.getLevel().getSeed(), center);
            undergroundDungeon = city.getPlan().isUnderground();
            if (!city.getPlan().isUnderground() && !city.getPlan().isFloating()) {
                return;
            }
        }

        // If there is a dungeon chunk adjacent to this we restrict to flat (unless it is underground)
        boolean adjacentDungeon = (!undergroundDungeon) && (CityTools.isDungeonChunk(primer.getLevel().getSeed(), chunkX-1, chunkZ) ||
                CityTools.isDungeonChunk(primer.getLevel().getSeed(), chunkX+1, chunkZ) ||
                CityTools.isDungeonChunk(primer.getLevel().getSeed(), chunkX, chunkZ-1) ||
                CityTools.isDungeonChunk(primer.getLevel().getSeed(), chunkX, chunkZ+1) ||
                CityTools.isDungeonChunk(primer.getLevel().getSeed(), chunkX, chunkZ));

        int yOffset = getBuildingYOffset(chunkX, chunkZ);
        boolean levitatorChunk = isCityLevitatorChunk(chunkX, chunkZ);
        if (levitatorChunk) {
            yOffset -= CITYLEV_HEIGHT;   // Building height is where the building starts. We need to go lower here
        }

        primer.fill(fillChar, CITY_LEVEL-2, yOffset);

        boolean undergroundPark = false;        // True if this park section is underground
        Pair<String, Transform> part = getBuildingPart(chunkX, chunkZ);
        int partHeight = AssetRegistries.PARTS.get(part.getKey()).getSliceCount();
        // Sample the world above this to see if we have room for a building
        if (isChunkOccupied(primer, yOffset+6, yOffset + partHeight + (levitatorChunk ? CITYLEV_HEIGHT : 0))) {
            undergroundPark = true;
        }

        if ((undergroundPark || adjacentDungeon) && part.getKey().startsWith("building")) {
            // Replace the building with a park section here
            part = getParkPart(chunkX, chunkZ);
        }

        int start = yOffset;

        if (levitatorChunk) {
            Pair<String, Transform> pair = getCityLevitatorPart(chunkX, chunkZ);
            BuildingPart buildingPart = AssetRegistries.PARTS.get(pair.getKey());
            primer.placePart(CITY_PALETTE, buildingPart,
                    pair.getValue(), 0, yOffset, 0);
            start += buildingPart.getSliceCount();

            Random random = new Random(chunkX * 341873128712L + chunkZ * 132897987541L);
            random.nextFloat();
            if (random.nextFloat() < .8 || undergroundPark) {
                // Disable the building above this in most cases
                part = null;
            } else {
                // We generate a roofpart on top of which the building will come
                primer.placePart(CITY_PALETTE, AssetRegistries.PARTS.get("citylev_roofpart"),
                        Transform.ROTATE_NONE, 0, yOffset, 0);
                yOffset += CITYLEV_HEIGHT;
            }
        }

        if (part != null) {
            BuildingPart buildingPart = AssetRegistries.PARTS.get(part.getKey());
            primer.placePart(CITY_PALETTE, buildingPart, part.getValue(), 0, yOffset, 0);
            start += buildingPart.getSliceCount();
        }

        if (undergroundPark) {
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockState marble = LegacyBlockStates.read("ariente:marble_smooth@color=black");
            BlockState tech = LegacyBlockStates.read("ariente:blacktech@type=lines");
            int layerHeight = CITY_LEVEL + CITYLEV_HEIGHT + 6;
            if (start < layerHeight) {
                primer.fill(air, start, layerHeight);
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dx = 0 ; dx < 16 ; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    pos.set(dx, layerHeight, dz);
                    if (primer.getBlockState(pos) != air) {
                        if (dx == 6 || dx == 9 || dz == 6 || dz == 9) {
                            primer.setBlockState(pos, tech, false);
                        } else {
                            primer.setBlockState(pos, marble, false);
                        }
                    }
                }
            }
        }
    }

    private static boolean isChunkOccupied(CityChunk primer, int y1, int y2) {
        BlockState air = Blocks.AIR.defaultBlockState();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0 ; dx < 16 ; dx += 2) {
            for (int dz = 0 ; dz < 16 ; dz += 2) {
                int y = y1;
                while (y <= y2) {
                    if (primer.getBlockState(pos.set(dx, y, dz)) != air) {
                        return true;
                    }
                    y += 2;
                }
            }
        }
        return false;
    }

    public static Pair<String, Transform> getCityLevitatorPart(int chunkX, int chunkZ) {
        // Generate a bitmask of 4 bits for all four directions around this chunk to see
        // which citylev part we need (and transform)
        int citylev0 = isCityLevitatorChunk(chunkX-1, chunkZ) ? 8 : 0;
        int citylev1 = isCityLevitatorChunk(chunkX, chunkZ-1) ? 4 : 0;
        int citylev2 = isCityLevitatorChunk(chunkX+1, chunkZ) ? 2 : 0;
        int citylev3 = isCityLevitatorChunk(chunkX, chunkZ+1) ? 1 : 0;
        int mask = citylev0 | citylev1 | citylev2 | citylev3;
        return CITYLEV_PARTS.get(mask);
    }

}
