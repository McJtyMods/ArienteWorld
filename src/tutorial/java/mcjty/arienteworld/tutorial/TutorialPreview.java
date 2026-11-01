package mcjty.arienteworld.tutorial;

import com.mojang.blaze3d.platform.NativeImage;
import mcjty.arienteworld.ArienteWorld;
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
            screenshot.writeToFile(directory.resolve(scene == 0 ? "Tutorial-city.png" : "Tutorial-plaza.png"));
        }
        if (++scene == 2) {
            ArienteWorld.setup.getLogger().info("TUTORIAL PREVIEW: city and plaza screenshots saved");
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
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            player.setGameMode(GameType.SPECTATOR);
            if (current == 0) {
                player.teleportTo(player.serverLevel(), 236, 183, 236, 135, 40);
            } else {
                player.teleportTo(player.serverLevel(), 171, 97, 176, 139, 34);
            }
            ready = true;
        });
    }
}
