package mcjty.arienteworld.client;

import static mcjty.arienteworld.setup.Registration.ENTITY_DRONE;

import mcjty.ariente.entities.LaserEntity;
import mcjty.arienteworld.entities.drone.DroneEntity;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/** Uses tracked server projectiles; local renderer-only entities cannot verify movement synchronization. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class LaserClientChecks {

    public static boolean checked;
    private static boolean started;
    private static boolean pixelsChecked;
    private static volatile Map<Integer, Vec3> directions;
    private static int ticks;
    private static int samples;
    private static volatile DroneEntity shooter;
    private static boolean networkChecked;
    private static CameraType originalCamera;
    private static int cameraIndex;
    private static boolean cameraRequested;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("ariente.laserCheck") || checked || event.phase != TickEvent.Phase.END
                || mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.gui.screen() != null || mc.isPaused()) {
            return;
        }
        require(++ticks < 200, "Timed out waiting for tracked lance and drone lasers");
        if (networkChecked) {
            checked = pixelsChecked;
            if (checked && Boolean.getBoolean("ariente.laserCheckExit")) {
                mc.stop();
            }
            return;
        }
        if (!started) {
            started = true;
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                var level = player.serverLevel();
                server.setDifficulty(Difficulty.NORMAL, true);
                level.getEntitiesOfClass(LaserEntity.class, player.getBoundingBox().inflate(100)).forEach(LaserEntity::discard);
                level.getEntitiesOfClass(DroneEntity.class, player.getBoundingBox().inflate(100)).forEach(DroneEntity::discard);
                Vec3 origin = player.position().add(0, 8, 0);
                BlockPos center = BlockPos.containing(origin);
                for (BlockPos pos : BlockPos.betweenClosed(center.offset(-35, -1, -3), center.offset(35, 20, 3))) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                var drone = ENTITY_DRONE.get().create(level);
                shooter = drone;
                drone.setNoAi(true);
                drone.setNoGravity(true);
                drone.setPos(origin);
                level.addFreshEntity(drone);
                LaserEntity droneShot = LaserEntity.create(level, drone, -20, 5, 0);
                droneShot.setPos(origin.add(-2, 0, 0));
                LaserEntity lanceShot = LaserEntity.createLanceShot(level, player, new Vec3(1, .2, 0), 16.9F);
                lanceShot.setPos(origin.add(2, 0, 0));
                level.addFreshEntity(droneShot);
                level.addFreshEntity(lanceShot);
                directions = Map.of(droneShot.getId(), droneShot.getAcceleration().normalize(),
                        lanceShot.getId(), lanceShot.getAcceleration().normalize());
            });
            return;
        }
        if (directions == null) {
            return;
        }
        for (var entry : directions.entrySet()) {
            if (!(mc.level.getEntity(entry.getKey()) instanceof LaserEntity laser)) {
                return;
            }
            require(laser.getAcceleration().lengthSqr() > .009D, "Client lasers must receive nonzero acceleration");
            require(laser.getBeamDirection().dot(entry.getValue()) > .999D, "Client beam direction must match server aim");
            require(laser.getDeltaMovement().dot(entry.getValue()) > 0, "Both projectile types must visibly travel forward on the client");
        }
        if (++samples >= 8) {
            networkChecked = true;
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var level = server.getPlayerList().getPlayer(uuid).serverLevel();
                for (int id : directions.keySet()) {
                    if (level.getEntity(id) != null) {
                        level.getEntity(id).discard();
                    }
                }
                shooter.discard();
            });
            ArienteWorld.setup.getLogger().info("LASER NETWORK CHECK PASSED: tracked lance and drone projectiles retain server acceleration, movement and beam direction");
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (Boolean.getBoolean("ariente.laserCheck") && !pixelsChecked && networkChecked
                && event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            Minecraft mc = Minecraft.getInstance();
            CameraType[] cameras = CameraType.values();
            if (originalCamera == null) {
                originalCamera = mc.options.getCameraType();
            }
            if (!cameraRequested) {
                mc.options.setCameraType(cameras[cameraIndex]);
                cameraRequested = true;
                return;
            }
            LaserPerspectiveChecks.check(mc, event.getPoseStack(), cameras[cameraIndex]);
            if (++cameraIndex == cameras.length) {
                mc.options.setCameraType(originalCamera);
                pixelsChecked = true;
            } else {
                cameraRequested = false;
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Laser client check: " + message);
        }
    }
}
