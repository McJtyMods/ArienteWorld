package mcjty.arienteworld.client;

import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.commands.ModCommands;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.ArienteLandscapeCity;
import mcjty.arienteworld.dimension.PortalGenerator;
import mcjty.arienteworld.dimension.terrain.ArienteBiomeLayout;
import mcjty.arienteworld.dimension.terrain.BiomeProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in screenshots of fresh terrain, vegetation and the two kinds of landscape districts. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class WorldGenerationClientChecks {

    private static final String[] SCENES = {"forest", "rough", "surface", "underground"};
    private static int scene;
    private static int ticks;
    private static boolean started;
    private static volatile boolean ready;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("arienteworld.phase1VisualCheck") || event.phase != TickEvent.Phase.END) {
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
            mc.options.renderDistance().set(8);
            prepare(mc);
        }
        if (!ready || ++ticks < 120 || !mc.level.dimension().equals(DimensionRegister.DIMENSION)) {
            return;
        }
        try (var screenshot = Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget())) {
            Path directory = Path.of("..", "verification");
            Files.createDirectories(directory);
            screenshot.writeToFile(directory.resolve("world-phase1-" + SCENES[scene] + ".png"));
        } catch (Exception e) {
            throw new IllegalStateException("Could not capture world generation scene", e);
        }
        if (++scene == SCENES.length) {
            ArienteWorld.setup.getLogger().info("PHASE 1 WORLD CLIENT CHECK PASSED: fresh forest, rough terrain, surface and underground districts");
            mc.stop();
        } else {
            ticks = 0;
            ready = false;
            prepare(mc);
        }
    }

    private static void prepare(Minecraft mc) {
        int index = scene;
        var playerId = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            ServerLevel level = mc.getSingleplayerServer().getLevel(DimensionRegister.DIMENSION);
            BlockPos target = switch (index) {
                case 0 -> findBiome(level, BiomeProfile.FOREST);
                case 1 -> findBiome(level, BiomeProfile.ROUGH);
                case 2 -> findBiome(level, BiomeProfile.CITY);
                default -> ModCommands.findDistrict(level, BlockPos.ZERO, index == 3);
            };
            if (target == null) {
                throw new IllegalStateException("No scene for " + SCENES[index]);
            }
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    level.getChunk((target.getX() >> 4) + x, (target.getZ() >> 4) + z);
                }
            }
            level.setDayTime(6000);
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, target.getX() + (index == 3 ? 0 : 24), target.getY() + (index == 3 ? 0 : 12),
                    target.getZ() + (index == 3 ? 0 : 24), index == 3 ? 0 : 135, index == 3 ? 5 : 30);
            ArienteWorld.setup.getLogger().info("PHASE 1 WORLD SCENE {} at {}", SCENES[index], target);
            ready = true;
        });
    }

    private static BlockPos findBiome(ServerLevel level, BiomeProfile profile) {
        ArienteBiomeLayout layout = new ArienteBiomeLayout(level.getSeed());
        var generator = level.getChunkSource().getGenerator();
        for (int x = 512; x < 1024; x++) {
            for (int z = 512; z < 1024; z++) {
                if (layout.get(x * 4 + 2, z * 4 + 2) != profile || CityTools.isDungeonChunk(level.getSeed(), x, z)
                        || PortalGenerator.isPortalChunk(x, z)) {
                    continue;
                }
                if (profile == BiomeProfile.CITY) {
                    boolean interior = true;
                    for (int dx = -2; dx <= 2 && interior; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (!ArienteLandscapeCity.isLandscapeCityChunk(level.getSeed(), x + dx, z + dz)
                                    || CityTools.isDungeonChunk(level.getSeed(), x + dx, z + dz)
                                    || PortalGenerator.isPortalChunk(x + dx, z + dz)) {
                                interior = false;
                                break;
                            }
                        }
                    }
                    if (!interior) {
                        continue;
                    }
                }
                int y = generator.getBaseHeight(x * 16 + 8, z * 16 + 8, Heightmap.Types.WORLD_SURFACE_WG,
                        level, level.getChunkSource().randomState());
                if (y > 64 && y < (profile == BiomeProfile.CITY ? 80 : 100)) {
                    return new BlockPos(x * 16 + 8, profile == BiomeProfile.CITY ? 110 : y + 12, z * 16 + 8);
                }
            }
        }
        return null;
    }
}
