package mcjty.arienteworld.client;

import net.minecraft.world.item.DyeColor;
import mcjty.ariente.entities.LaserEntity;
import mcjty.arienteworld.entities.drone.DroneEntity;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;

/** Opt-in captures of tracked shots in the normal world pipeline, including optional graphics mods. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class LaserLiveClientChecks {

    private static int ticks;
    private static int scenario;
    private static int lastCaptureTick = -1;
    private static int shotFrames;
    private static volatile boolean ready;
    private static boolean started;
    private static BlockPos aimBlock;
    private static int usesBefore;
    private static volatile boolean shotChecked;
    private static volatile boolean shotFired;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("ariente.laserLiveCheck") || event.phase != TickEvent.Phase.END
                || Boolean.getBoolean("ariente.laserCheck") && !LaserClientChecks.checked
                || mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.gui.screen() != null
                || mc.gui.overlay() != null || mc.isPaused()) {
            return;
        }
        if (!started) {
            started = true;
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                if (Boolean.getBoolean("ariente.lanceAlignmentDimension")) {
                    player.teleportTo(server.getLevel(DimensionRegister.DIMENSION), .5, 180, .5, -45, 0);
                }
                player.setGameMode(GameType.CREATIVE);
                player.setHealth(player.getMaxHealth());
                player.setNoGravity(true);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.ENERGY_LANCE.get()));
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                var level = player.serverLevel();
                level.setDayTime(6000);
                level.getEntitiesOfClass(LaserEntity.class, player.getBoundingBox().inflate(200)).forEach(LaserEntity::discard);
                level.getEntitiesOfClass(DroneEntity.class, new AABB(-30, 175, -30, 30, 195, 30)).forEach(DroneEntity::discard);
                for (BlockPos pos : BlockPos.betweenClosed(-20, 179, -20, 20, 195, 20)) {
                    boolean wall = pos.getX() == -20 || pos.getX() == 20 || pos.getZ() == -20 || pos.getZ() == 20;
                    level.setBlock(pos, pos.getY() == 179 || wall
                            ? Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
                player.teleportTo(.5, 180, .5);
                player.connection.teleport(.5, 180, .5, -45, 0);
                ready = true;
            });
            return;
        }
        if (!ready) {
            return;
        }
        if (Boolean.getBoolean("ariente.lanceAlignmentCheck")) {
            mc.mouseHandler.releaseMouse();
        }
        ticks++;
        if (ticks == 20) {
            mc.options.setCameraType(CameraType.values()[scenario % 3]);
            boolean alignment = Boolean.getBoolean("ariente.lanceAlignmentCheck");
            int setting = scenario / 3;
            if (alignment) {
                shotChecked = false;
                shotFired = false;
                mc.options.fov().set(setting % 2 == 0 ? 70 : 100);
                mc.options.mainHand().set(setting < 4 ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
            }
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                player.serverLevel().getEntitiesOfClass(LaserEntity.class, player.getBoundingBox().inflate(100)).forEach(LaserEntity::discard);
                float yaw = alignment ? new float[]{0, -45, 115, -137}[setting % 4] : new float[]{0, -45, 115}[setting];
                float pitch = alignment ? new float[]{0, -25, 15, -40}[setting % 4] : (setting == 2 ? -15 : 0);
                if (alignment) {
                    usesBefore = player.getStats().getValue(Stats.ITEM_USED.get(Registration.ENERGY_LANCE.get()));
                    player.setMainArm(setting < 4 ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
                    if (aimBlock != null) {
                        player.serverLevel().setBlock(aimBlock, Blocks.AIR.defaultBlockState(), 2);
                    }
                    Vec3 aim = Vec3.directionFromRotation(pitch, yaw);
                    double distance = new double[]{18, 8, 3, 12}[setting % 4];
                    aimBlock = BlockPos.containing(new Vec3(.5, 180 + player.getEyeHeight(), .5).add(aim.scale(distance)));
                    player.serverLevel().setBlock(aimBlock, Blocks.RED_CONCRETE.defaultBlockState(), 2);
                }
                player.connection.teleport(.5, 180, .5, yaw, pitch);
            });
        }
        if (ticks == 40) {
            KeyMapping.click(mc.options.keyUse.getKey());
            if (Boolean.getBoolean("ariente.laserLiveStrafe")) {
                mc.options.keyRight.setDown(true);
            }
        }
        if (ticks == 50) {
            mc.options.keyRight.setDown(false);
        }
        if (ticks == 70 && Boolean.getBoolean("ariente.lanceAlignmentCheck")) {
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                shotFired = player.getStats().getValue(Stats.ITEM_USED.get(Registration.ENERGY_LANCE.get())) > usesBefore;
                shotChecked = true;
            });
        }
        if (ticks >= 80) {
            boolean alignment = Boolean.getBoolean("ariente.lanceAlignmentCheck");
            if (alignment) {
                if (!shotChecked && ticks < 400) {
                    return;
                }
                if (!shotFired) {
                    throw new IllegalStateException("No native lance use was confirmed for scenario " + scenario);
                }
                LanceAlignmentClientChecks.verifyCurrentScenario();
            }
            // Nearby shots can hit and disappear before the first tracked client frame.
            if (shotFrames == 0 && !(alignment && scenario / 3 % 4 == 2)) {
                throw new IllegalStateException("No tracked lance shot was captured for scenario " + scenario);
            }
            ArienteWorld.setup.getLogger().info("LASER LIVE CAPTURE COMPLETED: aim {}, {}", scenario / 3, CameraType.values()[scenario % 3]);
            shotFrames = 0;
            if (++scenario == CameraType.values().length * (alignment ? 8 : 3)) {
                mc.stop();
            } else if (alignment && scenario == 12) {
                mc.reloadResourcePacks();
            }
            ticks = 19;
        }
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("ariente.laserLiveCheck") || !ready || ticks < 40 || ticks > 58
                || lastCaptureTick == ticks) {
            return;
        }
        lastCaptureTick = ticks;
        try (var picture = Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget())) {
            var directory = mc.gameDirectory.toPath().resolve("screenshots/laser-live-" + System.getProperty("ariente.laserLiveLabel", "capture"));
            Files.createDirectories(directory);
            picture.writeToFile(directory.resolve(scenario / 3 + "-" + CameraType.values()[scenario % 3].name().toLowerCase() + "-" + ticks + ".png"));
            for (LaserEntity shot : mc.level.getEntitiesOfClass(LaserEntity.class, mc.player.getBoundingBox().inflate(50))) {
                shotFrames++;
                ArienteWorld.setup.getLogger().info("LASER LIVE FRAME {} {}: projectile={}, direction={}, camera={}",
                        scenario, ticks, shot.position(), shot.getBeamDirection(), mc.gameRenderer.mainCamera().getPosition());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
