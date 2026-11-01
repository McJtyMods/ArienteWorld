package mcjty.arienteworld.tutorial;

import com.mojang.blaze3d.platform.NativeImage;
import mcjty.arienteworld.ArienteWorld;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Screenshots a separate copy of the map; the installable save stays at its welcome point. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class TutorialPreview {

    private static final String[] SCENES = {"Tutorial-city", "Tutorial-plaza", "Tutorial-bend",
            "Tutorial-beams", "Tutorial-elevator", "Tutorial-cockpit"};
    private static int scene;
    private static int ticks;
    private static boolean started;
    private static volatile boolean ready;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) throws IOException {
        if (!Boolean.getBoolean("ariente.tutorial.preview") || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        if (mc.player == null && mc.screen != null) {
            for (var child : mc.screen.children()) {
                if (child instanceof Button button && (button.getMessage().getString().equals("Proceed")
                        || button.getMessage().getString().equals("Continue"))) {
                    button.onPress();
                    break;
                }
            }
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) {
            return;
        }
        if (!started) {
            started = true;
            mc.options.hideGui = true;
            if (Boolean.getBoolean("ariente.tutorial.fabulous")) {
                mc.options.graphicsMode().set(GraphicsStatus.FABULOUS);
                mc.levelRenderer.allChanged();
            }
            mc.options.renderDistance().set(12);
            mc.options.simulationDistance().set(5);
            prepare(mc);
        }
        if (!ready || ++ticks < 160) {
            return;
        }
        Path directory = Path.of("..", "screenshots");
        Files.createDirectories(directory);
        try (NativeImage screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            screenshot.writeToFile(directory.resolve(SCENES[scene] + ".png"));
        }
        if (++scene == SCENES.length) {
            ArienteWorld.setup.getLogger().info("TUTORIAL PREVIEW: city, plaza, bend, beam transparency, elevator and cockpit screenshots saved");
            mc.stop();
        } else {
            ready = false;
            ticks = 0;
            prepare(mc);
        }
    }

    private static void prepare(Minecraft mc) {
        int current = scene;
        var playerId = mc.player.getUUID();
        mc.options.setCameraType(current == 5 ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            player.stopRiding();
            player.setGameMode(current == 5 ? GameType.CREATIVE : GameType.SPECTATOR);
            switch (current) {
                case 0 -> player.teleportTo(player.serverLevel(), 236, 183, 236, 135, 40);
                case 1 -> player.teleportTo(player.serverLevel(), 178, 116, 185, 139, 42);
                case 2 -> player.teleportTo(player.serverLevel(), 142.5, 68, 144.5, 27, 60);
                case 3 -> player.teleportTo(player.serverLevel(), 134.5, 66, 140.5, -144, 7);
                case 4 -> player.teleportTo(player.serverLevel(), 151.5, 67, 157.5, 180, 10);
                case 5 -> {
                    var ships = player.serverLevel().getEntitiesOfClass(FluxShipEntity.class, new AABB(128, 60, 144, 144, 80, 160));
                    if (ships.isEmpty()) {
                        throw new IllegalStateException("Tutorial has no parked Flux Ship");
                    }
                    FluxShipEntity ship = ships.get(0);
                    player.teleportTo(player.serverLevel(), ship.getX(), ship.getY(), ship.getZ(), 135, 15);
                    player.startRiding(ship, true);
                }
                default -> throw new IllegalStateException("Unknown Tutorial preview scene");
            }
            ready = true;
        });
    }
}
