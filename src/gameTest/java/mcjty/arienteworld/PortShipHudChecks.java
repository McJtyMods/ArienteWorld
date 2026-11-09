package mcjty.arienteworld;

import com.mojang.blaze3d.platform.NativeImage;
import mcjty.ariente.config.UtilityConfiguration;
import mcjty.ariente.entities.fluxship.FluxShipEnergy;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.ariente.entities.fluxship.FluxShipLaser;
import mcjty.ariente.setup.Registration;
import mcjty.hologui.gui.HoloGuiEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Samples the actual cockpit charge bar across consecutive frames and changing view angles. */
public final class PortShipHudChecks implements ClientModInitializer {

    private static FluxShipEntity serverShip;
    private static boolean started;
    private static int waiting;
    private static int ticks;
    private static int samples;
    private static int missing;
    private static int minVisible = 4;
    private static int maxVisible;
    private static int[][] points;
    private static CompletableFuture<NativeImage> capture;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("ariente.portShipHudChecks")) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (serverShip != null && !serverShip.isRemoved()) {
                serverShip.setCharge(FluxShipEnergy.CAPACITY / 2);
                serverShip.setDeltaMovement(Vec3.ZERO);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(PortShipHudChecks::tick);
    }

    private static void tick(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
            if (++waiting % 40 == 0 && mc.screen != null) {
                for (var child : mc.screen.children()) {
                    if (child instanceof AbstractButton button && (button.getMessage().getString().startsWith("Proceed")
                            || button.getMessage().getString().equals("Continue"))) {
                        button.onPress(new KeyEvent(257, 0, 0));
                        break;
                    }
                }
            }
            require(waiting < 2400, "HUD fixture world did not open");
            return;
        }
        if (!started) {
            started = true;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.bobView().set(false);
            mc.options.graphicsPreset().set(Boolean.getBoolean("ariente.shipHudFabulous") ? GraphicsPreset.FABULOUS : GraphicsPreset.FANCY);
            UtilityConfiguration.FLUX_SHIP_MOUSE_CONTROL.set(false);
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                player.stopRiding();
                player.setGameMode(GameType.CREATIVE);
                player.teleportTo(level, 0, 105, 0, Set.of(), 0, 0, true);
                serverShip = Registration.ENTITY_FLUX_SHIP.get().create(level, EntitySpawnReason.TRIGGERED);
                serverShip.setPos(0, 105, 0);
                serverShip.setCharge(FluxShipEnergy.CAPACITY / 2);
                level.addFreshEntity(serverShip);
                player.startRiding(serverShip);
            });
        }
        if (++ticks < 120) return;
        require(mc.player.getVehicle() instanceof FluxShipEntity, "Player did not mount the flux ship");
        var ship = (FluxShipEntity) mc.player.getVehicle();
        require(Math.abs(ship.getChargeRatio() - .5f) < .001, "Fixture charge changed unexpectedly");
        if (capture == null) {
            points = samplePoints(mc, ship);
            capture = new CompletableFuture<>();
            Screenshot.takeScreenshot(mc.getMainRenderTarget(), capture::complete);
            return;
        }
        if (!capture.isDone()) return;
        try (NativeImage image = capture.join()) {
            int visible = 0;
            for (int[] p : points) {
                require(p[0] >= 2 && p[1] >= 2 && p[0] < image.getWidth() - 2 && p[1] < image.getHeight() - 2, "Charge bar probe is outside the screen");
                boolean cyan = false;
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                    int rgb = image.getPixel(p[0] + dx, p[1] + dy);
                    cyan |= (rgb >> 16 & 255) > 40 && (rgb >> 8 & 255) > 130 && (rgb & 255) > 140;
                }
                if (cyan) visible++;
            }
            minVisible = Math.min(minVisible, visible);
            maxVisible = Math.max(maxVisible, visible);
            if (visible != 4) missing++;
            if (samples == 0 || visible != 4 && missing <= 3) {
                var directory = mc.gameDirectory.toPath().resolve("screenshots/ship-hud-checks");
                Files.createDirectories(directory);
                image.writeToFile(directory.resolve("hud-" + samples + ".png"));
            }
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        capture = null;
        samples++;
        mc.player.setYRot((float) (Math.sin(samples * .13) * 1.5));
        mc.player.setXRot((float) (Math.sin(samples * .17) * .5));
        if (samples == 120) {
            System.out.println("SHIP HUD PIXEL CHECK: frames=" + samples + " bad=" + missing + " visible=" + minVisible + ".." + maxVisible);
            require(missing == 0, "Charge bar flickers or is obscured in " + missing + " frames");
            System.out.println("FABRIC SHIP HUD CHECKS PASSED: fixed charge bar remains visible across 120 frames");
            mc.stop();
        }
    }

    private static int[][] samplePoints(Minecraft mc, FluxShipEntity ship) {
        require(ship.getHoloGui() instanceof HoloGuiEntity, "Cockpit panel is missing");
        var panel = (HoloGuiEntity) ship.getHoloGui();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var camera = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        var transform = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        float scale = 1 - (1 - panel.getScale()) * 1.6f;
        var center = FluxShipLaser.panelCenter(ship, partialTick);
        var window = mc.getWindow();
        int[][] result = new int[4][2];
        for (int i = 0; i < 4; i++) {
            float x = -.46f + .92f * (.1f + i * .1f);
            Vec3 point = center.add(ship.rotateCabinOffset(new Vec3(-x * scale, -.4575 * scale, -.006 * scale), partialTick)).subtract(camera.pos);
            var projected = transform.transform(new Vector4f((float) point.x, (float) point.y, (float) point.z, 1));
            result[i][0] = Math.round((projected.x / projected.w + 1) * window.getWidth() / 2);
            result[i][1] = Math.round((1 - projected.y / projected.w) * window.getHeight() / 2);
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
