package mcjty.arienteworld.client;

import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in native sun/moon screenshots and black-background regression checks in a disposable world. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class SkyRenderingChecks {

    private static boolean started;
    private static volatile boolean ready;
    private static int ticks;
    private static int scene;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("arienteworld.skyCheck") || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) {
            mc.gui.setScreen(null);
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.gui.overlay() != null) {
            return;
        }
        if (!started) {
            started = true;
            mc.options.hideGui = true;
            mc.options.renderDistance().set(3);
            prepare(mc);
        }
        if (!ready || ++ticks < 100 || !mc.level.dimension().equals(DimensionRegister.DIMENSION)) {
            return;
        }
        try (var shot = Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget())) {
            int black = 0;
            int light = 0;
            for (int y = 0; y < shot.getHeight(); y++) {
                for (int x = 0; x < shot.getWidth(); x++) {
                    int rgba = shot.getPixelRGBA(x, y);
                    int red = rgba & 255;
                    int green = (rgba >> 8) & 255;
                    int blue = (rgba >> 16) & 255;
                    if (red < 20 && green < 20 && blue < 20) {
                        black++;
                    }
                    if (red > 230 && green > 230 && blue > 230) {
                        light++;
                    }
                }
            }
            Path directory = Path.of("..", "sky-images");
            Files.createDirectories(directory);
            String stack = ModList.get().isLoaded("oculus") ? "oculus" : "vanilla";
            shot.writeToFile(directory.resolve(stack + "-" + (scene % 2 == 0 ? "sun" : "moon") + (scene >= 2 ? "-reload" : "") + ".png"));
            ArienteWorld.setup.getLogger().info("SKY PIXELS: {} scene={}, black={}, light={}, time={}, pitch={}", stack, scene, black, light, mc.level.getDayTime(), mc.player.getXRot());
            if (black > 20 || light < 20) {
                throw new IllegalStateException("Celestial additive rendering failed: scene=" + scene + ", black=" + black + ", light=" + light);
            }

        } catch (Exception e) {
            throw new IllegalStateException("Sky rendering regression", e);
        }
        ticks = 0;
        ready = false;
        scene++;
        if (scene == 4) {
            ArienteWorld.setup.getLogger().info("SKY RENDERING CHECK PASSED: original sun/moon additive blending before and after resource reload");
            mc.stop();
        } else if (scene == 2) {
            mc.reloadResourcePacks().thenRun(() -> prepare(mc));
        } else {
            prepare(mc);
        }
    }

    private static void prepare(Minecraft mc) {
        var id = mc.player.getUUID();
        int next = scene;
        mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().getLevel(DimensionRegister.DIMENSION);
            // Other dimensions share the Overworld clock; their DerivedLevelData setter is a no-op.
            mc.getSingleplayerServer().overworld().setDayTime(next % 2 == 0 ? 6000 : 18000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, mc.getSingleplayerServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, mc.getSingleplayerServer());
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            player.setGameMode(GameType.SPECTATOR);
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.teleportTo(level, 64008.5, 280, 64008.5, 0, -90);
            ready = true;
        });
    }
}
