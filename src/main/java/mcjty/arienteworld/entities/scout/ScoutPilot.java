package mcjty.arienteworld.entities.scout;

import net.minecraft.core.UUIDUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.ariente.entities.fluxship.FluxShipLaser;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A remembered sighting drives search and attack runs; hidden players' positions are never used. */
public class ScoutPilot {

    public enum Phase {
        PATROL, SETUP, ATTACK, EGRESS, SEARCH, DIVERT, RETIRING
    }

    public static final int MEMORY_TICKS = 20 * 60;
    public static final double NOTICE_RANGE = 192;
    private final ChunkPos home;
    private ChunkPos destination;
    private List<Vec3> route;
    private int waypoint;
    private Phase phase = Phase.PATROL;
    private int phaseTicks;
    private UUID targetId;
    private Vec3 lastSeen = Vec3.ZERO;
    private Vec3 lastVelocity = Vec3.ZERO;
    private long lastSeenTime;
    private Vec3 maneuver = Vec3.ZERO;
    private float searchAngle;
    private long divertUntil;
    private boolean turningIn;
    private ChunkPos flightChunk;

    public ScoutPilot(ChunkPos home, ScoutRoutes.FlightPlan plan) {
        this.home = home;
        destination = plan.destination();
        route = List.copyOf(plan.waypoints());
    }

    public Phase getPhase() {
        return phase;
    }

    @Nullable
    public UUID getTargetId() {
        return targetId;
    }

    public Vec3 getLastSeen() {
        return lastSeen;
    }

    public ChunkPos getHome() {
        return home;
    }

    private void changePhase(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    public void tick(SoldierEntity soldier, FluxShipEntity ship) {
        ServerLevel level = (ServerLevel) ship.level();
        long now = level.getGameTime();
        ChunkPos current = ship.chunkPosition();
        if (!current.equals(flightChunk) || ship.tickCount % 20 == 0) {
            ScoutNavigation.keepFlightLoaded(level, ship.getUUID(), current, flightChunk);
            flightChunk = current;
        }
        if (ship.tickCount % 200 == 0 && !ScoutPatrols.get(level).heartbeat(home, ship.getUUID(), now)) {
            if (!ScoutPatrols.playerNear(level, ship.position(), 288)) {
                retire(soldier, ship);
                return;
            }
            divert(ship, level.players());
        }
        phaseTicks++;
        if (phase == Phase.DIVERT || phase == Phase.RETIRING) {
            if (phase == Phase.RETIRING && !ScoutPatrols.playerNear(level, ship.position(), 288)) {
                retire(soldier, ship);
                return;
            }
            if (phase == Phase.DIVERT && now >= divertUntil
                    && !ScoutPatrols.playerNear(level, ship.position(), ScoutPatrols.TRAFFIC_RADIUS)) {
                // Resume the assigned flight, including a destination whose core was destroyed while en route.
                changePhase(Phase.PATROL);
            } else {
                if (ship.position().distanceToSqr(maneuver) < 30 * 30) {
                    maneuver = ship.position().add(Vec3.directionFromRotation(-5, ship.getYRot()).scale(256));
                }
                fly(ship, soldier, maneuver, 1.7);
                return;
            }
        }

        Player target = targetId == null ? null : level.getPlayerByUUID(targetId);
        if (targetId != null && (target != null && !canAttack(target) || now - lastSeenTime >= MEMORY_TICKS)) {
            targetId = null;
            changePhase(Phase.PATROL);
            target = null;
        }
        // Keep one target for the entire pursuit; another player cannot distract a focused scout.
        if (targetId == null && ship.tickCount % 10 == 0) {
            target = level.players().stream().filter(ScoutPilot::canAttack)
                    .filter(player -> ship.distanceToSqr(player) <= NOTICE_RANGE * NOTICE_RANGE)
                    .filter(player -> player.getEyePosition().subtract(ship.position()).normalize()
                            .dot(FluxShipLaser.direction(ship, 1)) > -.3)
                    .filter(player -> visible(soldier, ship, player))
                    .min(Comparator.comparingDouble(ship::distanceToSqr)).orElse(null);
            if (target != null) {
                targetId = target.getUUID();
            }
        }
        boolean seen = target != null && canAttack(target) && visible(soldier, ship, target);
        if (seen) {
            Vec3 observation = target.getBoundingBox().getCenter();
            long elapsed = now - lastSeenTime;
            if (elapsed > 0 && elapsed <= 5 && targetId.equals(target.getUUID())) {
                // Network-controlled players and vehicle passengers can report zero delta movement.
                Vec3 measured = observation.subtract(lastSeen).scale(1.0 / elapsed);
                lastVelocity = measured.lengthSqr() <= 8 * 8 ? lastVelocity.lerp(measured, .5) : Vec3.ZERO;
            } else {
                lastVelocity = target.isPassenger() ? target.getVehicle().getDeltaMovement() : target.getDeltaMovement();
            }
            lastSeen = observation;
            lastSeenTime = now;
            if (phase == Phase.PATROL || phase == Phase.SEARCH) {
                setup(ship);
            }
        } else if (targetId != null && phase != Phase.SEARCH) {
            searchAngle = (float) Mth.atan2(ship.getZ() - lastSeen.z, ship.getX() - lastSeen.x);
            changePhase(Phase.SEARCH);
        }
        if (targetId == null) {
            patrol(soldier, ship);
            return;
        }
        switch (phase) {
            case SETUP -> {
                boolean ready = turningIn || ship.position().distanceToSqr(maneuver) < 26 * 26
                        || phaseTicks > 20 && horizontalDistance(ship.position(), lastSeen) > 100
                        && ship.getY() > lastSeen.y + 16;
                turningIn = ready;
                Vec3 aim = intercept(FluxShipLaser.muzzle(ship, 1));
                fly(ship, soldier, ready ? aim : maneuver, ready ? 1.1 : 1.6);
                if (ready && aim.subtract(ship.position()).normalize().dot(FluxShipLaser.direction(ship, 1)) > .85) {
                    changePhase(Phase.ATTACK);
                } else if (phaseTicks > 260) {
                    setup(ship);
                }
            }
            case ATTACK -> {
                Vec3 aim = intercept(FluxShipLaser.muzzle(ship, 1));
                fly(ship, soldier, aim, 1.9);
                double ground = ScoutNavigation.floor(level, ship.position());
                double pullUpHeight = 16 + Math.max(0, -ship.getDeltaMovement().y) * 18;
                if (ship.position().distanceToSqr(lastSeen) < 45 * 45 || ship.getY() < ground + pullUpHeight || phaseTicks > 160
                        || lastSeen.subtract(ship.position()).normalize().dot(FluxShipLaser.direction(ship, 1)) < -.2) {
                    Vec3 forward = Vec3.directionFromRotation(0, ship.getYRot());
                    maneuver = ship.position().add(forward.scale(110)).add(0, 24, 0);
                    changePhase(Phase.EGRESS);
                }
            }
            case EGRESS -> {
                fly(ship, soldier, maneuver, 2);
                if (phaseTicks > 100 || ship.position().distanceToSqr(maneuver) < 24 * 24) {
                    setup(ship);
                }
            }
            case SEARCH -> {
                float angle = searchAngle + phaseTicks * .014f + .6f;
                Vec3 point = lastSeen.add(Mth.cos(angle) * 85, 26, Mth.sin(angle) * 85);
                fly(ship, soldier, point, 1.2);
            }
            default -> patrol(soldier, ship);
        }
    }

    private static boolean canAttack(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    private static boolean clear(FluxShipEntity ship, Vec3 start, Vec3 end) {
        return ScoutNavigation.loadedPath((ServerLevel) ship.level(), start, end)
                && ship.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ship))
                .getType() == HitResult.Type.MISS;
    }

    public static boolean visible(SoldierEntity soldier, FluxShipEntity ship, Player player) {
        return ship.distanceToSqr(player) <= NOTICE_RANGE * NOTICE_RANGE
                && (clear(ship, soldier.getEyePosition(), player.getEyePosition())
                || clear(ship, soldier.getEyePosition(), player.getBoundingBox().getCenter()));
    }

    private void setup(FluxShipEntity ship) {
        Vec3 away = new Vec3(ship.getX() - lastSeen.x, 0, ship.getZ() - lastSeen.z).normalize();
        if (away.lengthSqr() < .1) {
            away = Vec3.directionFromRotation(0, ship.getYRot());
        }
        maneuver = lastSeen.add(away.scale(120)).add(0, 32, 0);
        turningIn = false;
        changePhase(Phase.SETUP);
    }

    private Vec3 intercept(Vec3 muzzle) {
        Vec3 relative = lastSeen.subtract(muzzle);
        double speed = FluxShipLaser.SHOT_SPEED;
        double a = lastVelocity.lengthSqr() - speed * speed;
        double b = 2 * relative.dot(lastVelocity);
        double discriminant = b * b - 4 * a * relative.lengthSqr();
        double time = relative.length() / speed;
        if (Math.abs(a) > .00001 && discriminant >= 0) {
            double first = (-b - Math.sqrt(discriminant)) / (2 * a);
            double second = (-b + Math.sqrt(discriminant)) / (2 * a);
            time = first > 0 ? first : second > 0 ? second : time;
        }
        return lastSeen.add(lastVelocity.scale(Mth.clamp(time, 0, FluxShipLaser.SHOT_LIFETIME)));
    }

    public boolean shouldFire(SoldierEntity soldier, FluxShipEntity ship) {
        if (phase != Phase.ATTACK || targetId == null) {
            return false;
        }
        Player target = ship.level().getPlayerByUUID(targetId);
        if (target == null || !canAttack(target) || !visible(soldier, ship, target)) {
            return false;
        }
        Vec3 muzzle = FluxShipLaser.muzzle(ship, 1);
        Vec3 aim = intercept(muzzle).subtract(muzzle);
        return aim.lengthSqr() <= FluxShipLaser.RANGE * FluxShipLaser.RANGE
                && aim.normalize().dot(FluxShipLaser.direction(ship, 1)) > .997
                && clear(ship, muzzle, target.getBoundingBox().getCenter());
    }

    private void patrol(SoldierEntity soldier, FluxShipEntity ship) {
        if (waypoint >= route.size()) {
            if (!ScoutPatrols.playerNear((ServerLevel) ship.level(), ship.position(), 288)) {
                retire(soldier, ship);
                return;
            }
            if (!ScoutRoutes.isOperationalBase((ServerLevel) ship.level(), destination)) {
                // Finish the flight without launching another patrol from a defeated base or vanishing in view.
                divert(ship, ship.level().players());
                changePhase(Phase.RETIRING);
                return;
            }
            newRoute((ServerLevel) ship.level(), ship);
        }
        Vec3 next = route.get(waypoint);
        if (horizontalDistance(ship.position(), next) < 22) {
            waypoint++;
        }
        fly(ship, soldier, ScoutNavigation.cruiseTarget((ServerLevel) ship.level(), ship.position(), next), 1.25);
    }

    private void newRoute(ServerLevel level, FluxShipEntity ship) {
        ScoutRoutes.FlightPlan plan = ScoutRoutes.create(level, destination, ship.position(), level.getRandom());
        destination = plan.destination();
        route = plan.waypoints();
        waypoint = 0;
        changePhase(Phase.PATROL);
    }

    private void fly(FluxShipEntity ship, SoldierEntity soldier, Vec3 destination, double speed) {
        ServerLevel level = (ServerLevel) ship.level();
        Vec3 bounded = new Vec3(destination.x, Mth.clamp(destination.y, level.getMinY() + 12,
                level.getMaxY() + 96), destination.z);
        ship.flyTowards(soldier, ScoutNavigation.steer(ship, bounded, speed), speed);
    }

    public void divert(FluxShipEntity ship, List<? extends Player> players) {
        if (phase == Phase.DIVERT || phase == Phase.RETIRING) {
            return;
        }
        double score = -Double.MAX_VALUE;
        for (int angle = 0; angle < 360; angle += 45) {
            Vec3 candidate = ship.position().add(Vec3.directionFromRotation(0, angle).scale(480)).add(0, 24, 0);
            double clearance = players.stream().filter(p -> p.isAlive() && !p.isSpectator())
                    .mapToDouble(p -> horizontalDistance(candidate, p.position())).min().orElse(480);
            if (clearance > score) {
                score = clearance;
                maneuver = candidate;
            }
        }
        targetId = null;
        divertUntil = ship.level().getGameTime() + 600;
        changePhase(Phase.DIVERT);
    }

    private void retire(SoldierEntity soldier, FluxShipEntity ship) {
        ScoutNavigation.releaseFlight((ServerLevel) ship.level(), ship.getUUID(), flightChunk);
        ScoutPatrols.get((ServerLevel) ship.level()).release(home, ship.getUUID());
        soldier.discard();
        ship.discard();
    }

    public static double horizontalDistance(Vec3 first, Vec3 second) {
        return Math.sqrt((first.x - second.x) * (first.x - second.x) + (first.z - second.z) * (first.z - second.z));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("home", home.pack());
        tag.putLong("destination", destination.pack());
        ListTag points = new ListTag();
        route.forEach(point -> points.add(vector(point)));
        tag.put("route", points);
        tag.putInt("waypoint", waypoint);
        tag.putString("phase", phase.name());
        tag.putInt("phaseTicks", phaseTicks);
        if (targetId != null) {
            tag.store("target", UUIDUtil.CODEC, targetId);
        }
        tag.put("lastSeen", vector(lastSeen));
        tag.put("velocity", vector(lastVelocity));
        tag.putLong("seenTime", lastSeenTime);
        tag.put("maneuver", vector(maneuver));
        tag.putFloat("searchAngle", searchAngle);
        tag.putLong("divertUntil", divertUntil);
        tag.putBoolean("turningIn", turningIn);
        return tag;
    }

    public static ScoutPilot load(CompoundTag tag) {
        List<Vec3> points = new ArrayList<>();
        ListTag list = tag.getListOrEmpty("route");
        for (int i = 0; i < Math.min(32, list.size()); i++) {
            points.add(vector(list.getCompoundOrEmpty(i)));
        }
        ScoutPilot pilot = new ScoutPilot(ChunkPos.unpack(tag.getLongOr("home", 0)),
                new ScoutRoutes.FlightPlan(ChunkPos.unpack(tag.getLongOr("destination", 0)), points));
        pilot.waypoint = Mth.clamp(tag.getIntOr("waypoint", 0), 0, points.size());
        try {
            pilot.phase = Phase.valueOf(tag.getStringOr("phase", ""));
        } catch (IllegalArgumentException ignored) {
            pilot.phase = Phase.PATROL;
        }
        pilot.phaseTicks = tag.getIntOr("phaseTicks", 0);
        pilot.targetId = tag.read("target", UUIDUtil.CODEC).isPresent() ? tag.read("target", UUIDUtil.CODEC).orElse(null) : null;
        pilot.lastSeen = vector(tag.getCompoundOrEmpty("lastSeen"));
        pilot.lastVelocity = vector(tag.getCompoundOrEmpty("velocity"));
        pilot.lastSeenTime = tag.getLongOr("seenTime", 0);
        pilot.maneuver = vector(tag.getCompoundOrEmpty("maneuver"));
        pilot.searchAngle = tag.getFloatOr("searchAngle", 0);
        pilot.divertUntil = tag.getLongOr("divertUntil", 0);
        pilot.turningIn = tag.getBooleanOr("turningIn", false);
        return pilot;
    }

    private static CompoundTag vector(Vec3 vector) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", vector.x);
        tag.putDouble("y", vector.y);
        tag.putDouble("z", vector.z);
        return tag;
    }

    private static Vec3 vector(CompoundTag tag) {
        return new Vec3(tag.getDoubleOr("x", 0), tag.getDoubleOr("y", 0), tag.getDoubleOr("z", 0));
    }
}
