package mcjty.arienteworld.cities;

import mcjty.arienteworld.dimension.ArienteDungeonGenerator;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.IArienteChunkGenerator;
import mcjty.lib.varia.BlockPosTools;
import mcjty.lib.varia.WorldTools;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Stream;

public class CityTools {

    private static final Map<ChunkPos, City> cities = new HashMap<>();

    // Mostly for editmode purposes
    private static final Map<ChunkPos, Map<String, Integer>> cachedVariantSelections = new HashMap<>();

    public static City getCity(ChunkPos center) {
        if (!cities.containsKey(center)) {
            City city = new City(center, getRandomDungeonPlan(center), getRandomDungeonName(center), -1);
            cacheCity(center, city);
        }
        return cities.get(center);
    }

    private static void cacheCity(ChunkPos center, City city) {
        cities.put(center, city);
    }

    public static Map<String, Integer> getVariantSelections(ChunkPos center) {
        if (!cachedVariantSelections.containsKey(center)) {
            long seed = WorldTools.getOverworld().getSeed();
            Random random = new Random(seed + center.z * 198491317L + center.x * 776531419L);
            random.nextFloat();
            City city = getCity(center);
            Map<String, Integer> variants = new HashMap<>();
            for (Map.Entry<String, Integer> entry : city.getPlan().getVariants().entrySet()) {
                variants.put(entry.getKey(), random.nextInt(entry.getValue()));
            }
            cachedVariantSelections.put(center, variants);
        }
        return cachedVariantSelections.get(center);
    }

    public static boolean isPortalChunk(int chunkX, int chunkZ) {
//        if (Math.abs(chunkX) < 32 && Math.abs(chunkZ) < 32) {
//            return false;
//        }

        return ((chunkX & 0xf) == 0) && ((chunkZ & 0xf) == 0);
    }

    private static boolean isCityCenter(ChunkPos c) {
        int chunkX = c.x;
        int chunkZ = c.z;
//        if (Math.abs(chunkX) < 32 && Math.abs(chunkZ) < 32) {
//            return false;
//        }

        boolean candidate = ((chunkX & 0xf) == 8) && ((chunkZ & 0xf) == 8);
//        if (candidate) {
//            long seed = DimensionManager.getWorld(0).getSeed();
//            Random random = new Random(seed + chunkX * 776531419L + chunkZ * 198491317L);
//            random.nextFloat();
//            random.nextFloat();
//            return random.nextFloat() < WorldgenConfiguration.CITY_DUNGEON_CHANCE;
//        }
//        return false;
        // @todo always return a city. But it can be the 0_0 city which is basically empty and takes care of the station below it
        return true;
    }

    public static boolean isDungeonChunk(int chunkX, int chunkZ) {
        return getDungeonIndex(chunkX, chunkZ) != null;
    }

    public static boolean isStationChunk(int chunkX, int chunkZ) {
        int cx = chunkX & 0xf;
        int cz = chunkZ & 0xf;
        return cx >= 7 && cx <= 9 && cz >= 7 && cz <= 9;
    }

    @Nullable
    public static CityIndex getDungeonIndex(int chunkX, int chunkZ) {
        ChunkPos center = getNearestDungeonCenter(chunkX, chunkZ);
        if (center == null) {
            return null;
        }
        City city = getCity(center);
        CityPlan plan = city.getPlan();
        return getDungeonIndex(chunkX, chunkZ, center, plan);
    }

    public static CityIndex getDungeonIndex(int chunkX, int chunkZ, ChunkPos center, CityPlan plan) {
        List<String> pattern = plan.getPlan();
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("Bad plan!");
        }
        int dimX = pattern.get(0).length();
        int dimZ = pattern.size();
        int ox = (chunkX + dimX / 2) - center.x;
        int oz = (chunkZ + dimZ / 2) - center.z;

        if (ox >= 0 && ox < dimX && oz >= 0 && oz < dimZ) {
            return new CityIndex(dimX, dimZ, ox, oz);
        } else {
            return null;
        }
    }

    private static String getRandomDungeonName(ChunkPos c) {
        long seed = WorldTools.getOverworld().getSeed();
        Random random = new Random(seed + c.x * 903932899L + c.z * 776531419L);
        return NameGenerator.randomName(random);
    }

    // Return a random city plan. Use a valid city center as chunk coordinate parameter
    private static CityPlan getRandomDungeonPlan(ChunkPos c) {
        int chunkX = c.x;
        int chunkZ = c.z;
        long seed = WorldTools.getOverworld().getSeed();
        Random random = new Random(seed + chunkX * 198491317L + chunkZ * 776531419L);
        random.nextFloat();
        random.nextFloat();
        CityPlan plan = AssetRegistries.CITYPLANS.get(random.nextInt(AssetRegistries.CITYPLANS.getCount()));
        while (!plan.isCity()) {
            plan = AssetRegistries.CITYPLANS.get(random.nextInt(AssetRegistries.CITYPLANS.getCount()));
        }
        return plan;
    }

    public static City getNearestDungeon(int chunkX, int chunkZ) {
        ChunkPos center = getNearestDungeonCenter(chunkX, chunkZ);
        if (center == null) {
            return null;
        }
        City city = cities.get(center);
        if (city == null) {
            city = new City(center, getRandomDungeonPlan(center), getRandomDungeonName(center), -1);
            cacheCity(center, city);
        }
        return city;
    }

    @Nullable
    public static City getNearestDungeon(World world, BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        return getNearestDungeon(cx, cz);
    }

    @Nullable
    public static ChunkPos getNearestDungeonCenter(int chunkX, int chunkZ) {
        int cx = (chunkX & ~0xf) + 8;
        int cz = (chunkZ & ~0xf) + 8;
        ChunkPos cc = new ChunkPos(cx, cz);
        if (isCityCenter(cc)) {
            return cc;
        } else {
            return null;
        }
    }

    @Nonnull
    public static ChunkPos getNearestStationCenter(int chunkX, int chunkZ) {
        int cx = (chunkX & ~0xf) + 8;
        int cz = (chunkZ & ~0xf) + 8;
        return new ChunkPos(cx, cz);
    }

    public static BlockPos getNearestTeleportationSpot(BlockPos overworldPos) {
        ChunkPos cc = BlockPosTools.getChunkCoordFromPos(overworldPos);
        int chunkX = cc.x;
        int chunkZ = cc.z;
        int cx = (chunkX & ~0xf);
        int cz = (chunkZ & ~0xf);
        MinecraftServer server = WorldTools.getOverworld().getServer();
        ServerWorld world = server.getWorld(DimensionRegister.dimensionType);
        IArienteChunkGenerator generator = (IArienteChunkGenerator) (world.getChunkProvider().getChunkGenerator());
        int minHeight = ArienteDungeonGenerator.getPortalHeight(generator, cx, cz);
        return new BlockPos(cx * 16 + 8, minHeight + 2, cz * 16 + 8);
    }

    @Nonnull
    public static Optional<ChunkPos> getNearestCityCenterO(int chunkX, int chunkZ) {
        return Optional.ofNullable(getNearestDungeonCenter(chunkX, chunkZ));
    }

    public static int getLowestHeight(City city, IArienteChunkGenerator generator, int x, int z) {
        BuildingPart cellar = getPart(x, z, getDungeonIndex(x, z), city.getPlan(), city.getPlan().getCellar(), 13);
        if (cellar != null) {
            return city.getHeight(generator) - cellar.getSliceCount();
        } else {
            return city.getHeight(generator);
        }
    }

    @Nonnull
    public static List<BuildingPart> getStationParts(int chunkX, int chunkZ) {
        BuildingPart part = getStationPart(chunkX, chunkZ);
        if (part != null) {
            return Collections.singletonList(part);
        } else {
            return Collections.emptyList();
        }
    }

    @Nullable
    public static BuildingPart getStationPart(int chunkX, int chunkZ) {
        CityPlan station = AssetRegistries.CITYPLANS.get("station");
        CityIndex index = CityTools.getDungeonIndex(chunkX, chunkZ, CityTools.getNearestStationCenter(chunkX, chunkZ), station);
        if (index == null) {
            return null;
        }
        return CityTools.getPart(chunkX, chunkZ, index, station, station.getPlan(), 3939321);
    }

    public static int getStationHeight() {
        return 30;
    }

    @Nonnull
    public static List<PartPalette> getPartPalettes(City city, int x, int z) {
        List<PartPalette> parts = new ArrayList<>();
        CityIndex cityIndex = getDungeonIndex(x, z);
        CityPlan plan = city.getPlan();

        Stream.of(getPartPalette(x, z, cityIndex, plan, plan.getCellar(), 13),
                getPartPalette(x, z, cityIndex, plan, plan.getPlan(), 123),
                getPartPalette(x, z, cityIndex, plan, plan.getLayer2(), 366670937L * 57),
                getPartPalette(x, z, cityIndex, plan, plan.getTop(), 137777))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(parts::add);
        return parts;
    }

    @Nonnull
    public static List<BuildingPart> getBuildingParts(City city, int x, int z) {
        Map<String, Integer> variantSelections = getVariantSelections(city.getCenter());

        List<PartPalette> partPalettes = getPartPalettes(city, x, z);
        List<BuildingPart> parts = new ArrayList<>();
        for (PartPalette palette : partPalettes) {
            String variantName = palette.getVariant();
            int variant = (variantName == null || variantName.isEmpty()) ? 0 : variantSelections.getOrDefault(variantName, 0);
            List<String> p = palette.getPalette();
            if (variant >= p.size()) {
                variant = 0;
            }
            parts.add(AssetRegistries.PARTS.get(p.get(variant)));
        }
        return parts;
    }

    public static BuildingPart getPart(int x, int z, CityIndex index, CityPlan plan, List<String> pattern, long randomSeed) {
        if (pattern.isEmpty()) {
            return null;
        }
        if (index == null) {
            return null;
        }
        Map<Character, PartPalette> partPalette = plan.getPartPalette();
        char partChar = pattern.get(index.getZOffset()).charAt(index.getXOffset());
        if (partChar != ' ') {
            List<String> parts = partPalette.get(partChar).getPalette();

            long seed = WorldTools.getOverworld().getSeed();
            Random random = new Random(x * 23567813L + z * 923568029L + randomSeed + seed);
            random.nextFloat();
            random.nextFloat();

            return AssetRegistries.PARTS.get(parts.get(random.nextInt(parts.size())));
        }
        return null;
    }

    public static Optional<PartPalette> getPartPalette(int x, int z, CityIndex index, CityPlan plan, List<String> pattern, long randomSeed) {
        if (pattern.isEmpty()) {
            return Optional.empty();
        }
        if (index == null) {
            return Optional.empty();
        }
        Map<Character, PartPalette> partPalette = plan.getPartPalette();
        char partChar = pattern.get(index.getZOffset()).charAt(index.getXOffset());
        if (partChar != ' ') {
            return Optional.ofNullable(partPalette.get(partChar));
        }
        return Optional.empty();
    }

}
