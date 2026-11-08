package mcjty.arienteworld.entities.scout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.config.AIConfiguration;
import mcjty.arienteworld.dimension.ArienteChunkGenerator;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.entities.soldier.SoldierBehaviourType;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** An independent base launch scheduler; neither biome spawn tables nor hostile mob caps are involved. */
@EventBusSubscriber(modid = ArienteWorld.MODID)
public class ScoutPatrols extends SavedData {

    public static final double NEAR_RADIUS = 256;
    public static final double TRAFFIC_RADIUS = NEAR_RADIUS + 64;
    public static final double MIN_LAUNCH_DISTANCE = 128;
    public static final double MAX_LAUNCH_DISTANCE = 896;
    public static final String SCOUT_SHIP = "arienteworldScout";
    private static final long LEASE_TIMEOUT = 20 * 60 * 5;
    private final Map<Long, Launch> bases = new HashMap<>();
    private int cadenceVersion;

    private static class Launch {

        private long nextLaunch;
        private UUID ship;
        private long lastActive;
    }

    public static ScoutPatrols get(ServerLevel level) {
        ScoutPatrols data = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ScoutPatrols::new, (tag, provider) -> load(tag)), "ArienteScoutPatrols");
        data.upgradeCadence(level);
        return data;
    }

    void upgradeCadence(ServerLevel level) {
        if (cadenceVersion < 2) {
            if (AIConfiguration.SCOUT_LAUNCH_INTERVAL.get() == 180 || AIConfiguration.SCOUT_LAUNCH_INTERVAL.get() == 20) {
                AIConfiguration.SCOUT_LAUNCH_INTERVAL.set(10);
                AIConfiguration.SCOUT_LAUNCH_INTERVAL.save();
            }
            // Existing worlds otherwise retain several minutes of queued cooldowns after upgrading.
            bases.values().forEach(launch -> launch.nextLaunch = Math.min(launch.nextLaunch, level.getGameTime() + 20));
            cadenceVersion = 2;
            setDirty();
        }
    }

    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(DimensionRegister.DIMENSION)
                || !(level.getChunkSource().getGenerator() instanceof ArienteChunkGenerator)
                || level.getGameTime() % 10 != 0) {
            return;
        }
        List<FluxShipEntity> fleet = loadedScouts(level);
        enforceTraffic(fleet, level.players());
        if (!AIConfiguration.SCOUT_PATROLS.get() || level.getDifficulty() == Difficulty.PEACEFUL
                || level.getGameTime() % 20 != 0) {
            return;
        }
        ScoutPatrols scheduler = get(level);
        Set<ChunkPos> checked = new HashSet<>();
        for (Player player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            Vec3 travel = player.getRootVehicle().getDeltaMovement();
            Vec3 direction = new Vec3(travel.x, 0, travel.z);
            if (direction.lengthSqr() < .01) {
                Vec3 look = player.getLookAngle();
                direction = new Vec3(look.x, 0, look.z);
            }
            Vec3 heading = direction.normalize();
            List<City> sources = ScoutRoutes.nearbyBases(level, player.chunkPosition(), 4).stream()
                    .sorted(Comparator.comparingDouble(city -> sourcePriority(city, player, heading, travel.horizontalDistance()))).toList();
            for (City city : sources) {
                Vec3 center = new Vec3(city.getCenter().getMinBlockX() + 8, player.getY(), city.getCenter().getMinBlockZ() + 8);
                double distance = ScoutPilot.horizontalDistance(center, player.position());
                if (distance >= MIN_LAUNCH_DISTANCE && distance <= MAX_LAUNCH_DISTANCE
                        && checked.add(city.getCenter()) && ScoutRoutes.canLaunchFrom(city)
                        && hasCapacity(center, fleet, level.players())
                        && level.players().stream().filter(p -> p.isAlive() && !p.isSpectator())
                        .noneMatch(p -> ScoutPilot.horizontalDistance(center, p.position()) < MIN_LAUNCH_DISTANCE)
                        && !CityAISystem.getCityAISystem(level).getCityAI(city.getCenter()).isDead(level)) {
                    if (!scheduler.ready(level, city.getCenter())) {
                        if (scheduler.preparingSoon(level, city.getCenter())) {
                            ScoutNavigation.prepareLaunch(level, city.getCenter());
                            break;
                        }
                        continue;
                    }
                    FluxShipEntity ship = launch(level, city, fleet, level.players());
                    if (ship != null) {
                        scheduler.launched(level, city.getCenter(), ship.getUUID());
                        fleet.add(ship);
                        break;
                    } else if (!level.isPositionEntityTicking(BlockPos.containing(center))) {
                        // Prepare one distant base at a time instead of loading a whole neighborhood at once.
                        break;
                    }
                }
            }
        }
    }

    private static double sourcePriority(City city, Player player, Vec3 heading, double speed) {
        Vec3 offset = new Vec3(city.getCenter().getMinBlockX() + 8 - player.getX(), 0,
                city.getCenter().getMinBlockZ() + 8 - player.getZ());
        double sideways = Math.abs(offset.x * heading.z - offset.z * heading.x);
        double preparation = Math.max(0, speed * 80 - offset.dot(heading));
        return offset.length() + (1 - offset.normalize().dot(heading)) * 512 + sideways * 1.5 + preparation * 6;
    }

    static List<FluxShipEntity> loadedScouts(ServerLevel level) {
        List<FluxShipEntity> fleet = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FluxShipEntity ship && ship.getPersistentData().getBoolean(SCOUT_SHIP)) {
                if (ship.getControllingPassenger() instanceof Player) {
                    // A captured ship is now an ordinary player vehicle.
                    releaseShip(level, ship);
                    ship.getPersistentData().remove(SCOUT_SHIP);
                } else if (ship.getControllingPassenger() instanceof SoldierEntity soldier && soldier.getScoutPilot() != null
                        && soldier.isAlive()) {
                    fleet.add(ship);
                } else if (ship.tickCount > 40) {
                    // Leave defeated scouts to descend and be recovered. Their flight ticket expires naturally.
                    releaseShip(level, ship);
                    ship.getPersistentData().remove(SCOUT_SHIP);
                }
            }
        }
        return fleet;
    }

    private static void releaseShip(ServerLevel level, FluxShipEntity ship) {
        ScoutPatrols scheduler = get(level);
        for (Launch launch : scheduler.bases.values()) {
            if (ship.getUUID().equals(launch.ship)) {
                launch.ship = null;
                scheduler.setDirty();
            }
        }
        ScoutNavigation.releaseFlight(level, ship.getUUID(), ship.chunkPosition());
    }

    public static void enforceTraffic(List<FluxShipEntity> fleet, List<? extends Player> players) {
        Set<FluxShipEntity> divert = new HashSet<>();
        for (Player player : players) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            List<FluxShipEntity> nearby = fleet.stream()
                    .filter(ship -> ScoutPilot.horizontalDistance(ship.position(), player.position()) <= TRAFFIC_RADIUS)
                    .sorted(Comparator.comparingDouble((FluxShipEntity ship) -> ship.distanceToSqr(player))
                            .thenComparing(Entity::getUUID)).toList();
            for (int i = 2; i < nearby.size(); i++) {
                divert.add(nearby.get(i));
            }
        }
        for (FluxShipEntity ship : divert) {
            if (ship.getControllingPassenger() instanceof SoldierEntity soldier && soldier.getScoutPilot() != null) {
                soldier.getScoutPilot().divert(ship, players);
            }
        }
    }

    public static boolean hasCapacity(Vec3 position, List<FluxShipEntity> fleet, List<? extends Player> players) {
        for (Player player : players) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            if (ScoutPilot.horizontalDistance(position, player.position()) <= TRAFFIC_RADIUS
                    && fleet.stream().filter(ship -> ship.isAlive()
                    && ScoutPilot.horizontalDistance(ship.position(), player.position()) <= TRAFFIC_RADIUS).count() >= 2) {
                return false;
            }
        }
        return true;
    }

    public static boolean playerNear(ServerLevel level, Vec3 position, double radius) {
        return level.players().stream().anyMatch(player -> player.isAlive() && !player.isSpectator()
                && player.position().distanceToSqr(position) <= radius * radius);
    }

    static FluxShipEntity launch(ServerLevel level, City city, List<FluxShipEntity> fleet, List<? extends Player> players) {
        if (!ScoutRoutes.canLaunchFrom(city) || level.getDifficulty() == Difficulty.PEACEFUL) {
            return null;
        }
        Vec3 basePosition = ScoutRoutes.aboveBase(level, city);
        if (CityAISystem.getCityAISystem(level).getCityAI(city.getCenter()).isDead(level)
                || players.stream().filter(player -> player.isAlive() && !player.isSpectator())
                .anyMatch(player -> ScoutPilot.horizontalDistance(basePosition, player.position()) < MIN_LAUNCH_DISTANCE)
                || players.stream().noneMatch(player -> player.isAlive() && !player.isSpectator()
                && ScoutPilot.horizontalDistance(basePosition, player.position()) <= MAX_LAUNCH_DISTANCE)
                || !hasCapacity(basePosition, fleet, players)) {
            return null;
        }
        if (!ScoutNavigation.loaded(level, basePosition) || !level.isPositionEntityTicking(BlockPos.containing(basePosition))) {
            ScoutNavigation.prepareLaunch(level, city.getCenter());
            return null;
        }
        if (!ScoutRoutes.isOperationalBase(level, city.getCenter())) {
            ScoutNavigation.releaseLaunch(level, city.getCenter());
            return null;
        }
        int roof = level.getMinBuildHeight();
        // Include the whole hull and launch clearance; neighboring roof spires can be taller than the center column.
        for (int x = -6; x <= 6; x += 3) {
            for (int z = -6; z <= 6; z += 3) {
                roof = Math.max(roof, (int) ScoutNavigation.floor(level, basePosition.add(x, 0, z)));
            }
        }
        Vec3 position = new Vec3(basePosition.x, Math.max(basePosition.y, roof + 6), basePosition.z);
        if (position.y >= level.getMaxBuildHeight() - 5) {
            return null;
        }
        FluxShipEntity ship = FluxShipEntity.create(level, position.x, position.y, position.z);
        if (!level.noCollision(ship, ship.getBoundingBox().inflate(3, 2, 3))) {
            return null;
        }
        ScoutRoutes.FlightPlan route = ScoutRoutes.create(level, city.getCenter(), position, level.random);
        Vec3 direction = route.waypoints().get(0).subtract(position);
        ship.setYRot((float) (Math.atan2(-direction.x, direction.z) * 180 / Math.PI));
        ship.getPersistentData().putBoolean(SCOUT_SHIP, true);
        SoldierEntity soldier = SoldierEntity.create(level, city.getCenter(), SoldierBehaviourType.SOLDIER_FIGHTER);
        soldier.startScout(new ScoutPilot(city.getCenter(), route));
        soldier.setPos(position);
        if (!level.addFreshEntity(ship)) {
            return null;
        }
        if (!level.addFreshEntity(soldier) || !soldier.startRiding(ship, true)) {
            soldier.discard();
            ship.discard();
            return null;
        }
        ship.positionRider(soldier);
        ScoutNavigation.keepFlightLoaded(level, ship.getUUID(), ship.chunkPosition(), null);
        ScoutNavigation.releaseLaunch(level, city.getCenter());
        return ship;
    }

    boolean ready(ServerLevel level, ChunkPos base) {
        Launch launch = bases.get(base.toLong());
        if (launch == null) {
            launch = new Launch();
            // Stagger first encounters too, so a traveler can meet a patrol while passing a new base.
            launch.nextLaunch = level.getGameTime() + 10 + level.random.nextInt(11);
            bases.put(base.toLong(), launch);
            setDirty();
        }
        return level.getGameTime() >= launch.nextLaunch
                && (launch.ship == null || level.getGameTime() - launch.lastActive > LEASE_TIMEOUT);
    }

    private boolean preparingSoon(ServerLevel level, ChunkPos base) {
        Launch launch = bases.get(base.toLong());
        return launch != null && launch.nextLaunch <= level.getGameTime() + 20
                && (launch.ship == null || level.getGameTime() - launch.lastActive > LEASE_TIMEOUT);
    }

    private static int delay(ServerLevel level) {
        int minimum = AIConfiguration.SCOUT_LAUNCH_INTERVAL.get() * 20;
        return minimum + level.random.nextInt(minimum + 1);
    }

    void launched(ServerLevel level, ChunkPos base, UUID ship) {
        Launch launch = bases.computeIfAbsent(base.toLong(), key -> new Launch());
        launch.nextLaunch = level.getGameTime() + delay(level);
        launch.ship = ship;
        launch.lastActive = level.getGameTime();
        setDirty();
    }

    public boolean heartbeat(ChunkPos base, UUID ship, long now) {
        Launch launch = bases.computeIfAbsent(base.toLong(), key -> new Launch());
        if (launch.ship != null && !launch.ship.equals(ship)) {
            return false;
        }
        launch.ship = ship;
        launch.lastActive = now;
        setDirty();
        return true;
    }

    public void release(ChunkPos base, UUID ship) {
        Launch launch = bases.get(base.toLong());
        if (launch != null && ship.equals(launch.ship)) {
            launch.ship = null;
            setDirty();
        }
    }

    public static ScoutPatrols load(CompoundTag tag) {
        ScoutPatrols data = new ScoutPatrols();
        data.cadenceVersion = tag.getInt("cadenceVersion");
        ListTag entries = tag.getList("bases", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            Launch launch = new Launch();
            launch.nextLaunch = entry.getLong("next");
            launch.lastActive = entry.getLong("active");
            launch.ship = entry.hasUUID("ship") ? entry.getUUID("ship") : null;
            data.bases.put(entry.getLong("base"), launch);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("cadenceVersion", cadenceVersion);
        ListTag entries = new ListTag();
        bases.forEach((base, launch) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("base", base);
            entry.putLong("next", launch.nextLaunch);
            entry.putLong("active", launch.lastActive);
            if (launch.ship != null) {
                entry.putUUID("ship", launch.ship);
            }
            entries.add(entry);
        });
        tag.put("bases", entries);
        return tag;
    }
}
