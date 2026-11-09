package mcjty.arienteworld;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import mcjty.ariente.blocks.utility.camera.CameraTile;
import mcjty.ariente.config.DisplayConfiguration;
import mcjty.ariente.display.DisplayBaseTile;
import mcjty.ariente.display.client.LiveCameraRenderer;
import mcjty.ariente.setup.Registration;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Exercises real native live renders, mode switches, camera motion and texture resizing. */
public final class PortLiveCameraChecks implements ClientModInitializer {

    private static final BlockPos CAMERA = new BlockPos(127, 70, 118);
    private static final BlockPos DISPLAY = new BlockPos(139, 65, 123);
    private static int stage;
    private static int ticks;
    private static int waiting;
    private static int updates;
    private static boolean started;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static long previousUpdate;
    private static CompletableFuture<NativeImage> capture;
    private static int[] reference;

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("ariente.portLiveCameraChecks")) ClientTickEvents.END_CLIENT_TICK.register(PortLiveCameraChecks::tick);
    }

    private static void tick(Minecraft mc) {
        if (failure != null) throw new AssertionError("Preparing live-camera check", failure);
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
            require(waiting < 2400, "Live-camera fixture world did not open");
            return;
        }
        if (!started) {
            started = true;
            mc.options.bobView().set(false);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.renderDistance().set(10);
            mc.options.graphicsPreset().set(Boolean.getBoolean("ariente.liveCameraFabulous") ? GraphicsPreset.FABULOUS : GraphicsPreset.FANCY);
            DisplayConfiguration.LIVE_CAMERA_FPS.set(60);
            DisplayConfiguration.LIVE_CAMERA_TEXTURE_WIDTH.set(256);
            mc.levelRenderer.allChanged();
            prepare(mc);
            return;
        }
        if (!ready) return;
        Object feed = latestFeed();
        if (feed != null) {
            long update = (long) field(feed, "lastUpdate");
            if (update != previousUpdate && update != 0) {
                previousUpdate = update;
                updates++;
            }
        }
        if (++ticks == 40 && stage == 2) {
            mc.getSingleplayerServer().execute(() -> ((CameraTile) mc.getSingleplayerServer().overworld().getBlockEntity(CAMERA)).setLive(true));
        }
        if (ticks < 160) return;
        require(feed != null && field(feed, "target") != null, "Live camera did not create a feed");
        if (capture == null) {
            require(updates >= 10, "Live feed stopped refreshing: " + updates);
            RenderTarget target = (RenderTarget) field(feed, "target");
            require(target.width == (stage == 3 ? 512 : 256), "Live camera texture did not resize");
            capture = new CompletableFuture<>();
            Screenshot.takeScreenshot(target, capture::complete);
            return;
        }
        if (!capture.isDone()) return;
        try (NativeImage image = capture.join()) {
            var directory = mc.gameDirectory.toPath().resolve("screenshots/live-camera-checks");
            Files.createDirectories(directory);
            image.writeToFile(directory.resolve("feed-" + stage + ".png"));
            int colored = 0;
            long difference = 0;
            int[] pixels = image.getPixels();
            for (int i = 0; i < pixels.length; i++) {
                int rgb = pixels[i];
                int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
                if (r > g * 1.5 && b > g * 1.3 && r > 60 || g > r * 1.2 && g > b * 1.2 && g > 60) colored++;
                if (stage == 1) {
                    int before = reference[i];
                    difference += Math.abs((before >> 16 & 255) - r) + Math.abs((before >> 8 & 255) - g) + Math.abs((before & 255) - b);
                }
            }
            require(colored > pixels.length / 3, "Live target is missing its terrain: " + colored);
            if (stage == 0) reference = pixels;
            if (stage == 1) require((double) difference / (pixels.length * 3) < 3, "Moving the player changed the mounted camera image");
            System.out.println("LIVE CAMERA CHECK: stage=" + stage + " refreshes=" + updates + " terrainPixels=" + colored);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        capture = null;
        ready = false;
        ticks = updates = 0;
        if (++stage == 5) {
            System.out.println("FABRIC LIVE CAMERA CHECKS PASSED: continuous updates, player movement, mode switching, resize and camera pan");
            mc.stop();
            return;
        }
        if (stage == 2) {
            LiveCameraRenderer.clear();
        }
        if (stage == 3) DisplayConfiguration.LIVE_CAMERA_TEXTURE_WIDTH.set(512);
        if (stage == 4) DisplayConfiguration.LIVE_CAMERA_TEXTURE_WIDTH.set(256);
        prepare(mc);
    }

    private static void prepare(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = server.overworld();
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                if (stage == 0) {
                    for (BlockPos pos : BlockPos.betweenClosed(121, 64, 108, 126, 79, 129)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    for (int y = 64; y <= 79; y++) for (int z = 108; z <= 129; z++) {
                        var color = (y / 2 + z / 2) % 2 == 0 ? DyeColor.MAGENTA : DyeColor.LIME;
                        level.setBlockAndUpdate(new BlockPos(121, y, z), (color == DyeColor.MAGENTA ? Blocks.MAGENTA_CONCRETE : Blocks.LIME_CONCRETE).defaultBlockState());
                    }
                    level.setBlockAndUpdate(CAMERA, Registration.CAMERA.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST));
                }
                player.teleportTo(level, stage == 1 ? 142.5 : 141, 66, 130, Set.of(), 180, 0, true);
                DisplayBaseTile display = (DisplayBaseTile) level.getBlockEntity(DISPLAY);
                var module = display.modules().stream().filter(m -> m.stack().is(Registration.DISPLAY_CAMERA.get())).findFirst().orElseThrow();
                require(display.configureModule(player, module.id(), data -> {
                    data.putInt("CameraIndex", 0);
                    data.putInt("RotationSpeed", 0);
                    data.putInt("LivePan", stage == 4 ? 15 : 0);
                    data.putInt("LiveTilt", 0);
                    data.putInt("Radius", 32);
                    data.putInt("ViewZoom", 100);
                }), "Cannot configure live display");
                ((CameraTile) level.getBlockEntity(CAMERA)).setLive(stage != 2);
                player.teleportTo(level, stage == 1 ? 142.5 : 141, 66, 130, Set.of(), 180, 0, true);
                ready = true;
            } catch (Throwable error) {
                failure = error;
            }
        });
    }

    private static Object latestFeed() {
        try {
            var field = LiveCameraRenderer.class.getDeclaredField("FEEDS");
            field.setAccessible(true);
            Map<?, ?> feeds = (Map<?, ?>) field.get(null);
            Object newest = null;
            long time = 0;
            for (var feed : feeds.values()) {
                long update = (long) field(feed, "lastUpdate");
                if (update > time) {
                    newest = feed;
                    time = update;
                }
            }
            return newest;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
