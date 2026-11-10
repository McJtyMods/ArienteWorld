package mcjty.arienteworld.tutorial;

import mcjty.ariente.blocks.defense.PanelInfo;
import mcjty.ariente.blocks.defense.PentakisDodecahedron;
import mcjty.ariente.blocks.utility.wireless.WirelessButtonTile;
import mcjty.ariente.sounds.ForcefieldSound;
import mcjty.ariente.sounds.SoundController;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Checks actual sound starts, range crossings, fade-out and quick re-entry in a disposable Tutorial. */
public class TutorialForcefieldSoundCheck {

    private static final BlockPos SHIELD = new BlockPos(151, 65, 119);
    private static final BlockPos BUTTON = new BlockPos(147, 66, 126);
    private static final List<ForcefieldSound> PLAYED = new ArrayList<>();
    private static final boolean BASELINE = Boolean.getBoolean("ariente.tutorial.soundBaseline");
    private static int ticks;
    private static int waiting;
    private static boolean started;
    private static volatile boolean ready;
    private static BufferedWriter trace;
    private static float previousVolume;
    private static float maxStep;
    private static float maxInitialVolume;
    private static ForcefieldSound reentrySound;
    private static int startsBeforeReentry;

    public static void tick() throws IOException {
        if (!Boolean.getBoolean("ariente.tutorial.forcefieldSoundCheck")) {
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
                        || button.getMessage().getString().equals("Continue")
                        || button.getMessage().getString().equals("I know what I'm doing!"))) {
                    button.onPress();
                    break;
                }
            }
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null || started && !ready) {
            if (++waiting > 2400) {
                throw new IllegalStateException("Forcefield sound check did not enter Tutorial");
            }
            return;
        }
        if (!started) {
            started = true;
            if (!BASELINE) {
                checkPanelDistance();
            }
            trace = Files.newBufferedWriter(Path.of("forcefield-sound.csv"));
            trace.write("tick,z,volume,starts\n");
            for (SoundSource source : SoundSource.values()) {
                mc.options.getSoundSourceOptionInstance(source).set(source == SoundSource.MASTER ? .5 : source == SoundSource.BLOCKS ? 1.0 : 0.0);
            }
            mc.getSoundManager().addListener((sound, events, distance) -> {
                if (sound instanceof ForcefieldSound field) {
                    maxInitialVolume = Math.max(maxInitialVolume, field.getVolume());
                    require(field.canStartSilent() && field.getVolume() == 0, "A forcefield loop must start silent");
                    PLAYED.add(field);
                }
            });
            var playerId = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(player.server.overworld(), 151.5, 65, 137, 180, 0);
                ready = true;
            });
            return;
        }
        ForcefieldSound sound = SoundController.getForcefieldSoundAt(mc.level, SHIELD);
        float volume = sound == null ? 0 : sound.getVolume();
        maxStep = Math.max(maxStep, Math.abs(volume - previousVolume));
        require(Math.abs(volume - previousVolume) <= .1001f, "Forcefield volume jumped instead of fading");
        previousVolume = volume;
        trace.write(ticks + "," + mc.player.getZ() + "," + volume + "," + PLAYED.size() + "\n");
        trace.flush();
        if (ticks == 225 || ticks == 450) {
            require(sound != null && mc.getSoundManager().isActive(sound) && volume > .7f, "Nearby active panels must hum");
        }
        if (ticks == 420) {
            require(sound == null && PLAYED.stream().noneMatch(mc.getSoundManager()::isActive), "Leaving range must release the sound channel");
        }
        if (ticks == 449) {
            reentrySound = sound;
            startsBeforeReentry = PLAYED.size();
        }
        if (ticks == 470) {
            require(sound == reentrySound && sound != null && PLAYED.size() == startsBeforeReentry,
                    "Quick re-entry must reuse the fading loop");
        }
        if (ticks == 590) {
            require(sound == null && PLAYED.stream().noneMatch(mc.getSoundManager()::isActive), "Disabling the shield must stop the hum");
            trace.close();
            ArienteWorld.setup.getLogger().info("FORCEFIELD SOUND CHECK: {}; {} starts, max initial volume {}, max volume step {}",
                    BASELINE ? "baseline captured" : "passed", PLAYED.size(), maxInitialVolume, maxStep);
            mc.stop();
            return;
        }
        double z = ticks < 60 ? 137 : ticks < 185 ? 137 - (ticks - 60) * .1
                : ticks < 245 ? 124.5 : ticks < 370 ? 124.5 + (ticks - 245) * .1
                : ticks < 430 ? 137 : ticks < 460 ? 124.5 : ticks < 465 ? 137 : 124.5;
        var playerId = mc.player.getUUID();
        boolean disable = ticks == 520;
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            player.teleportTo(player.server.overworld(), 151.5, 65, z, 180, 0);
            if (disable) {
                ((WirelessButtonTile) player.serverLevel().getBlockEntity(BUTTON)).setLocked(false);
            }
        });
        ticks++;
    }

    private static void checkPanelDistance() {
        Vec3 origin = new Vec3(-400, 120, 817);
        for (double scale : new double[]{3, 10, 50}) {
            for (int index = 0; index < PentakisDodecahedron.MAX_TRIANGLES; index++) {
                var triangle = PentakisDodecahedron.getTriangle(index);
                Vec3 midpoint = origin.add(triangle.getMid().scale(scale));
                PanelInfo panel = new PanelInfo(index, midpoint.x, midpoint.y, midpoint.z);
                Vec3 normal = triangle.getB().subtract(triangle.getA()).cross(triangle.getC().subtract(triangle.getA())).normalize();
                for (double distance : new double[]{-6, 0, 6}) {
                    Vec3 centroid = origin.add(triangle.getA().add(triangle.getB()).add(triangle.getC()).scale(scale / 3));
                    Vec3 point = centroid.add(normal.scale(distance));
                    require(Math.abs(panel.getSquaredDistance(point, scale) - distance * distance) < .000001,
                            "Panel distance must measure separation from the surface");
                }
                Vec3 beyondEdge = origin.add(triangle.getA().add(triangle.getB().subtract(triangle.getA()).scale(3)).scale(scale));
                require(panel.getSquaredDistance(beyondEdge, scale) > 1,
                        "Panel distance must clamp to the triangle's edges");
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!BASELINE && !condition) {
            throw new IllegalStateException(message);
        }
    }
}
