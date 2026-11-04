package mcjty.arienteworld.entities.scout;

import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Base queries use the generation seed, without generating or loading destination chunks. */
public class ScoutRoutes {

    public record FlightPlan(ChunkPos destination, List<Vec3> waypoints) {
    }

    public static boolean canLaunchFrom(City city) {
        return !city.getPlan().isUnderground()
                && city.getPlan().getPlan().stream().mapToInt(row -> (int) row.chars().filter(c -> c != ' ').count()).sum() >= 9;
    }

    public static List<City> nearbyBases(ServerLevel level, ChunkPos around, int cells) {
        List<City> result = new ArrayList<>();
        ChunkPos center = CityTools.getNearestDungeonCenter(around.x, around.z);
        for (int x = -cells; x <= cells; x++) {
            for (int z = -cells; z <= cells; z++) {
                ChunkPos pos = new ChunkPos(center.x + x * 16, center.z + z * 16);
                if (CityTools.isCityCenter(level.getSeed(), pos.x, pos.z)) {
                    City city = CityTools.getCity(level.getSeed(), pos);
                    if (!city.getPlan().isUnderground()) {
                        result.add(city);
                    }
                }
            }
        }
        return result;
    }

    public static Vec3 aboveBase(ServerLevel level, City city) {
        ChunkPos center = city.getCenter();
        int roof = CityTools.getLowestHeight(level, city, center.x, center.z)
                + CityTools.getBuildingParts(level.getSeed(), city, center.x, center.z).stream()
                .mapToInt(BuildingPart::getSliceCount).sum();
        return new Vec3(center.getMinBlockX() + 8, Math.min(level.getMaxBuildHeight() - 8, roof + 8), center.getMinBlockZ() + 8);
    }

    public static FlightPlan create(ServerLevel level, ChunkPos from, Vec3 start, RandomSource random) {
        List<City> destinations = nearbyBases(level, from, 2).stream()
                .filter(city -> !city.getCenter().equals(from)).toList();
        if (!destinations.isEmpty() && random.nextInt(4) != 0) {
            City destination = destinations.get(random.nextInt(destinations.size()));
            Vec3 finish = aboveBase(level, destination);
            Vec3 midpoint = start.lerp(finish, .5);
            return new FlightPlan(destination.getCenter(), List.of(midpoint, finish));
        }
        // A broad loop rather than a hover-and-spin when there is no destination, or for a local survey.
        List<Vec3> loop = new ArrayList<>();
        float angle = random.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < 8; i++) {
            float heading = angle + i * Mth.TWO_PI / 8;
            loop.add(start.add(Mth.cos(heading) * 128, 0, Mth.sin(heading) * 128));
        }
        loop.add(start);
        return new FlightPlan(from, loop);
    }
}
