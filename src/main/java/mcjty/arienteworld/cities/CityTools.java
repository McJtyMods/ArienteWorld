package mcjty.arienteworld.cities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Seed-based queries shared by generation and the city AI. No state is shared between worlds. */
public class CityTools {

    public static ChunkPos getNearestDungeonCenter(int chunkX, int chunkZ) {
        return new ChunkPos((chunkX & ~15) + 8, (chunkZ & ~15) + 8);
    }

    public static boolean isDungeonChunk(long seed, int chunkX, int chunkZ) {
        City city = getCity(seed, getNearestDungeonCenter(chunkX, chunkZ));
        return !getBuildingParts(seed, city, chunkX, chunkZ).isEmpty();
    }

    public static boolean isStationChunk(int chunkX, int chunkZ) {
        int x = chunkX & 15;
        int z = chunkZ & 15;
        return x >= 7 && x <= 9 && z >= 7 && z <= 9;
    }

    public static int getStationHeight() {
        return 30;
    }

    @Nullable
    public static BuildingPart getStationPart(long seed, int chunkX, int chunkZ) {
        CityPlan plan = AssetRegistries.CITYPLANS.get("station");
        CityIndex index = getDungeonIndex(chunkX, chunkZ, getNearestDungeonCenter(chunkX, chunkZ), plan);
        if (index == null) {
            return null;
        }
        char symbol = plan.getPlan().get(index.getZOffset()).charAt(index.getXOffset());
        if (symbol == ' ') {
            return null;
        }
        List<String> parts = plan.getPartPalette().get(symbol).getPalette();
        Random random = new Random(seed + chunkX * 23567813L + chunkZ * 923568029L + 3939321);
        random.nextFloat();
        random.nextFloat();
        return AssetRegistries.PARTS.get(parts.get(random.nextInt(parts.size())));
    }

    public static City getCity(long seed, ChunkPos center) {
        Random random = new Random(seed + center.x * 198491317L + center.z * 776531419L);
        random.nextFloat();
        random.nextFloat();
        CityPlan plan;
        do {
            plan = AssetRegistries.CITYPLANS.get(random.nextInt(AssetRegistries.CITYPLANS.getCount()));
        } while (!plan.isCity());
        Random names = new Random(seed + center.x * 903932899L + center.z * 776531419L);
        return new City(center, plan, NameGenerator.randomName(names));
    }

    @Nullable
    public static CityIndex getDungeonIndex(int chunkX, int chunkZ, ChunkPos center, CityPlan plan) {
        int width = plan.getPlan().get(0).length();
        int depth = plan.getPlan().size();
        int x = chunkX + width / 2 - center.x;
        int z = chunkZ + depth / 2 - center.z;
        return x >= 0 && x < width && z >= 0 && z < depth ? new CityIndex(width, depth, x, z) : null;
    }

    public static Map<String, Integer> getVariantSelections(long seed, City city) {
        ChunkPos center = city.getCenter();
        Random random = new Random(seed + center.z * 198491317L + center.x * 776531419L);
        random.nextFloat();
        Map<String, Integer> variants = new LinkedHashMap<>();
        city.getPlan().getVariants().forEach((name, count) -> variants.put(name, random.nextInt(count)));
        return variants;
    }

    @Nullable
    private static BuildingPart getPart(CityPlan plan, List<String> pattern, CityIndex index, Map<String, Integer> variants) {
        if (pattern.isEmpty()) {
            return null;
        }
        char symbol = pattern.get(index.getZOffset()).charAt(index.getXOffset());
        if (symbol == ' ') {
            return null;
        }
        PartPalette palette = plan.getPartPalette().get(symbol);
        int variant = variants.getOrDefault(palette.getVariant(), 0);
        if (variant >= palette.getPalette().size()) {
            variant = 0;
        }
        return AssetRegistries.PARTS.get(palette.getPalette().get(variant));
    }

    public static List<BuildingPart> getBuildingParts(long seed, City city, int chunkX, int chunkZ) {
        CityPlan plan = city.getPlan();
        CityIndex index = getDungeonIndex(chunkX, chunkZ, city.getCenter(), plan);
        List<BuildingPart> parts = new ArrayList<>();
        if (index != null) {
            Map<String, Integer> variants = getVariantSelections(seed, city);
            for (List<String> layer : List.of(plan.getCellar(), plan.getPlan(), plan.getLayer2(), plan.getTop())) {
                BuildingPart part = getPart(plan, layer, index, variants);
                if (part != null) {
                    parts.add(part);
                }
            }
        }
        return parts;
    }

    public static int getCellarHeight(long seed, City city, int chunkX, int chunkZ) {
        CityIndex index = getDungeonIndex(chunkX, chunkZ, city.getCenter(), city.getPlan());
        if (index == null) {
            return 0;
        }
        BuildingPart cellar = getPart(city.getPlan(), city.getPlan().getCellar(), index, getVariantSelections(seed, city));
        return cellar == null ? 0 : cellar.getSliceCount();
    }

    public static int getCityHeight(City city, ChunkGenerator generator, LevelHeightAccessor height, RandomState randomState) {
        if (city.getPlan().isUnderground()) {
            return 40;
        }
        int sum = 0;
        int maximum = 0;
        for (int x : new int[]{2, 8, 14}) {
            for (int z : new int[]{2, 8, 14}) {
                int y = generator.getBaseHeight(city.getCenter().getMinBlockX() + x, city.getCenter().getMinBlockZ() + z,
                        Heightmap.Types.WORLD_SURFACE_WG, height, randomState);
                sum += y;
                maximum = Math.max(maximum, y);
            }
        }
        int y = city.getPlan().isFloating() ? Math.max(100, maximum + 40) : Math.max(generator.getSeaLevel() + 1, sum / 9);
        // Reserve room for the tallest authored stack above the main floor.
        int tallest = 0;
        for (PartPalette palette : city.getPlan().getPartPalette().values()) {
            for (String part : palette.getPalette()) {
                tallest = Math.max(tallest, AssetRegistries.PARTS.get(part).getSliceCount());
            }
        }
        return Math.min(y, height.getMaxBuildHeight() - tallest * 3 - 1);
    }

    public static int getCityHeight(ServerLevel level, City city) {
        return getCityHeight(city, level.getChunkSource().getGenerator(), level, level.getChunkSource().randomState());
    }

    public static int getLowestHeight(ServerLevel level, City city, int chunkX, int chunkZ) {
        return getCityHeight(level, city) - getCellarHeight(level.getSeed(), city, chunkX, chunkZ);
    }
}
