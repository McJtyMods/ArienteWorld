package mcjty.arienteworld.entities.scout;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import java.util.UUID;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Local avoidance considers the hull's width, terrain, momentum and the loaded chunk boundary. */
public class ScoutNavigation {

    private static final Map<ServerLevel, Map<UUID, FlightLease>> FLIGHTS = new WeakHashMap<>();

    private record FlightLease(ChunkPos chunk, long expires) {
    }

    public static void prepareLaunch(ServerLevel level, ChunkPos base) {
        level.getChunkSource().addTicketWithRadius(mcjty.arienteworld.setup.Registration.SCOUT_LAUNCH.get(), base, 3);
    }

    public static void releaseLaunch(ServerLevel level, ChunkPos base) {
        level.getChunkSource().removeTicketWithRadius(mcjty.arienteworld.setup.Registration.SCOUT_LAUNCH.get(), base, 3);
    }

    public static void keepFlightLoaded(ServerLevel level, UUID ship, ChunkPos current, ChunkPos previous) {
        var flights = FLIGHTS.computeIfAbsent(level, key -> new HashMap<>());
        flights.values().removeIf(lease -> lease.expires() < level.getGameTime());
        flights.put(ship, new FlightLease(current, level.getGameTime() + 60));
        level.getChunkSource().addTicketWithRadius(mcjty.arienteworld.setup.Registration.SCOUT_FLIGHT.get(), current, 3);
        if (previous != null && !previous.equals(current)) {
            removeUnusedTicket(level, previous, flights);
        }
    }

    public static void releaseFlight(ServerLevel level, UUID ship, ChunkPos chunk) {
        var flights = FLIGHTS.get(level);
        if (flights != null) {
            flights.remove(ship);
            if (chunk != null) {
                removeUnusedTicket(level, chunk, flights);
            }
        }
    }

    private static void removeUnusedTicket(ServerLevel level, ChunkPos chunk, Map<UUID, FlightLease> flights) {
        if (flights.values().stream().noneMatch(lease -> lease.chunk().equals(chunk) && lease.expires() >= level.getGameTime())) {
            level.getChunkSource().removeTicketWithRadius(mcjty.arienteworld.setup.Registration.SCOUT_FLIGHT.get(), chunk, 3);
        }
    }

    public static boolean loaded(ServerLevel level, Vec3 point) {
        return level.getChunkSource().getChunkNow(Mth.floor(point.x) >> 4, Mth.floor(point.z) >> 4) != null;
    }

    public static double floor(ServerLevel level, Vec3 point) {
        return loaded(level, point) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(point.x), Mth.floor(point.z)) : level.getMinY();
    }

    /** Patrol close to the terrain instead of retaining the launch tower's altitude for the whole route. */

    public static Vec3 cruiseTarget(ServerLevel level, Vec3 position, Vec3 destination) {
        Vec3 offset = new Vec3(destination.x - position.x, 0, destination.z - position.z);
        Vec3 ahead = position.add(offset.normalize().scale(Math.min(40, offset.length())));
        double height = loaded(level, ahead) ? Math.max(floor(level, position), floor(level, ahead)) + 14 : position.y;
        return new Vec3(ahead.x, height, ahead.z);
    }

    public static boolean loadedPath(ServerLevel level, Vec3 start, Vec3 end) {
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) / 8));
        for (int i = 0; i <= steps; i++) {
            if (!loaded(level, start.lerp(end, (double) i / steps))) {
                return false;
            }
        }
        return true;
    }

    public static Vec3 steer(FluxShipEntity ship, Vec3 destination, double speed) {
        ServerLevel level = (ServerLevel) ship.level();
        Vec3 direction = destination.subtract(ship.position()).normalize();
        double lookAhead = Math.max(18, speed * 18);
        Vec3 ahead = ship.position().add(direction.scale(lookAhead));
        if (!loaded(level, ahead)) {
            // Stay in simulated space until the player brings the next stretch into view.
            return Vec3.directionFromRotation(-20, ship.getYRot() + 90);
        }
        double floor = Math.max(floor(level, ship.position()), floor(level, ahead));
        if (ship.getY() < floor + 10) {
            direction = new Vec3(direction.x, Math.max(.8, direction.y), direction.z).normalize();
        }
        for (FluxShipEntity other : level.getEntitiesOfClass(FluxShipEntity.class, ship.getBoundingBox().inflate(24), other -> other != ship)) {
            Vec3 separation = other.position().subtract(ship.position());
            if (separation.lengthSqr() < 22 * 22 && separation.normalize().dot(direction) > .3) {
                int side = ship.getUUID().compareTo(other.getUUID()) < 0 ? -1 : 1;
                direction = Vec3.directionFromRotation(-20, ship.getYRot() + side * 60);
                break;
            }
        }
        Vec3 momentum = ship.getDeltaMovement().normalize();
        if (clearance(ship, direction, lookAhead) >= lookAhead - .1
                && (momentum.lengthSqr() < .1 || clearance(ship, momentum, lookAhead) >= lookAhead - .1)) {
            return direction;
        }
        Vec3 best = new Vec3(direction.x, 1.2, direction.z).normalize();
        double bestScore = -Double.MAX_VALUE;
        for (float turn : new float[]{0, 45, -45, 90, -90}) {
            Vec3 candidate = Vec3.directionFromRotation(-45, ship.getYRot() + turn);
            double score = clearance(ship, candidate, lookAhead) + candidate.dot(direction) * 4;
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static double clearance(FluxShipEntity ship, Vec3 direction, double distance) {
        Vec3 side = new Vec3(-direction.z, 0, direction.x).normalize();
        double result = distance;
        for (double offset : new double[]{-2.5, 0, 2.5}) {
            Vec3 start = ship.position().add(0, .8, 0).add(side.scale(offset));
            Vec3 end = start.add(direction.scale(distance));
            if (!loadedPath((ServerLevel) ship.level(), start, end)) {
                return 0;
            }
            HitResult hit = ship.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ship));
            result = Math.min(result, start.distanceTo(hit.getLocation()));
        }
        return result;
    }
}
