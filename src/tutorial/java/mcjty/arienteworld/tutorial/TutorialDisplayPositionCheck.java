package mcjty.arienteworld.tutorial;

import com.mojang.blaze3d.vertex.PoseStack;
import mcjty.hologui.gui.HoloRenderBatch;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;

import com.mojang.blaze3d.systems.RenderSystem;
import mcjty.ariente.client.WorldHolograms;
import mcjty.ariente.display.DisplayBaseTile;
import mcjty.ariente.display.client.LiveCameraRenderer;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Compares the deferred display pass with Minecraft's actual block entity camera transform. */
@EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class TutorialDisplayPositionCheck {

    private record View(String name, double x, double y, double z, float yaw, float pitch, CameraType camera) {
    }

    private static final View[] VIEWS = {
            new View("diagonal", 136.5, 65, 140.5, 135, 15, CameraType.FIRST_PERSON),
            new View("north", 136.5, 65, 140.5, 180, 0, CameraType.FIRST_PERSON),
            new View("west", 136.5, 65, 140.5, 90, 0, CameraType.FIRST_PERSON),
            new View("away", 136.5, 65, 140.5, 0, 0, CameraType.FIRST_PERSON),
            new View("east", 136.5, 65, 140.5, -90, 0, CameraType.FIRST_PERSON),
            new View("up", 136.5, 65, 140.5, 180, -65, CameraType.FIRST_PERSON),
            new View("down", 136.5, 65, 140.5, 180, 65, CameraType.FIRST_PERSON),
            new View("translated", 143.5, 65, 140.5, 150, 5, CameraType.FIRST_PERSON),
            new View("third-person", 136.5, 65, 140.5, 135, 15, CameraType.THIRD_PERSON_BACK)
    };
    private static int view;
    private static int ticks;
    private static int frames;
    private static boolean started;
    private static volatile boolean ready;
    private static boolean queued;
    private static boolean drawn;
    private static Matrix4f previousModelView;

    private static boolean active() {
        return Boolean.getBoolean("ariente.tutorial.positionCheck") && ready && !LiveCameraRenderer.isRendering();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ariente.tutorial.positionCheck")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) {
            mc.gui.setScreen(null);
        }
        if (mc.player == null && mc.gui.screen() != null) {
            for (var child : mc.gui.screen().children()) {
                if (child instanceof Button button && (button.getMessage().getString().startsWith("Proceed")
                        || button.getMessage().getString().equals("Continue")
                        || button.getMessage().getString().equalsIgnoreCase("I know what I'm doing!"))) {
                    button.onPress(new KeyEvent(257, 0, 0));
                    break;
                }
            }
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.gui.overlay() != null) {
            if (++ticks > 2400) {
                throw new IllegalStateException("Display position check did not enter Tutorial");
            }
            return;
        }
        if (!started) {
            started = true;
            ticks = 0;
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            mc.options.graphicsPreset().set(Boolean.getBoolean("ariente.tutorial.fabulous") ? GraphicsPreset.FABULOUS : GraphicsPreset.FANCY);
            mc.levelExtractor.allChanged();
            prepare(mc);
        }
        if (!ready || ++ticks < 80) {
            return;
        }
        if (frames < 10 || DisplayBaseTile.LOADED.stream().noneMatch(tile -> tile.getLevel() == mc.level && !tile.isRemoved())) {
            throw new IllegalStateException("Display position check did not render the tutorial's displays");
        }
        Screenshot.grab(mc.gameDirectory, "display-position-" + VIEWS[view].name() + ".png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
        ArienteWorld.setup.getLogger().info("DISPLAY POSITION CHECK: {} passed over {} frames", VIEWS[view].name(), frames);
        ready = false;
        if (++view == VIEWS.length) {
            ArienteWorld.setup.getLogger().info("DISPLAY POSITION CHECK: all views passed");
            mc.stop();
        } else {
            ticks = 0;
            frames = 0;
            prepare(mc);
        }
    }

    private static void prepare(Minecraft mc) {
        View next = VIEWS[view];
        mc.options.setCameraType(next.camera());
        var playerId = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            player.stopRiding();
            player.setGameMode(GameType.SPECTATOR);
            TutorialSupport.teleport(player, player.level().getServer().overworld(), next.x(), next.y(), next.z(), next.yaw(), next.pitch());
            ready = true;
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void extract(ExtractLevelRenderStateEvent event) {
        if (!active()) return;
        Matrix4f expectedView = new Matrix4f(event.getRenderState().cameraRenderState.viewRotationMatrix);
        queued = true;
        drawn = false;
        var batch = new HoloRenderBatch();
        batch.command(new PoseStack(), (poses, collector) -> {
            if (!expectedView.equals(RenderSystem.getModelViewMatrixCopy(), .00001f)) {
                throw new IllegalStateException("Deferred displays lost the world camera transform at view " + VIEWS[view].name());
            }
            drawn = true;
            frames++;
        });
        WorldHolograms.addRelative(event.getCamera().position(), batch);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeRender(RenderLevelStageEvent.AfterLevel event) {
        if (active()) previousModelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterRender(RenderLevelStageEvent.AfterLevel event) {
        if (active() && queued) {
            if (!drawn || !previousModelView.equals(RenderSystem.getModelViewMatrixCopy(), .00001f)) {
                throw new IllegalStateException("Deferred display pass failed to draw or restore the camera transform");
            }
            queued = false;
        }
    }
}
