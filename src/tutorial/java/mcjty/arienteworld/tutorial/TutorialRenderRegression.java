package mcjty.arienteworld.tutorial;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import mcjty.ariente.blocks.ArienteBlockEntity;
import mcjty.ariente.blocks.generators.NegariteTankTile;
import mcjty.ariente.blocks.generators.PosiriteTankTile;
import mcjty.ariente.blocks.utility.camera.CameraTile;
import mcjty.ariente.display.DisplayBaseTile;
import mcjty.ariente.display.client.LiveCameraRenderer;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.lib.tileentity.GenericTileEntity;
import mcjty.lib.varia.RedstoneMode;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Pixel checks for live-camera transforms, skies, player visibility and full-height tank beams. */
@EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public final class TutorialRenderRegression {

    private static final BlockPos CAMERA = new BlockPos(127, 70, 118);
    private static final BlockPos DISPLAY = new BlockPos(139, 65, 123);
    private static final BlockPos NEGATIVE = new BlockPos(151, 65, 140);
    private static final BlockPos POSITIVE = new BlockPos(155, 65, 140);
    private static final String[] NAMES = {"live-baseline", "live-strafe", "live-distance", "live-height-yaw",
            "tanks-front", "tanks-left", "tanks-right", "tanks-inactive", "sky-day", "sky-night",
            "player-first-person", "player-third-person", "player-out-of-view"};
    private static int stage;
    private static int ticks;
    private static int waiting;
    private static volatile boolean ready;
    private static boolean started;
    private static Button acceptedButton;
    private static volatile RuntimeException failure;
    private static CompletableFuture<NativeImage> capture;
    private static int[] reference;
    private static Matrix4f transform;
    private static Vec3 eye;
    private static double dayBrightness;
    private static boolean playerExtracted;

    private TutorialRenderRegression() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ariente.tutorial.renderRegression")) {
            return;
        }
        if (failure != null) {
            throw failure;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        if (mc.player == null && mc.screen != null) {
            for (var child : mc.screen.children()) {
                if (child instanceof Button button && button != acceptedButton && (button.getMessage().getString().equals("Proceed")
                        || button.getMessage().getString().equals("Continue")
                        || button.getMessage().getString().equals("I know what I'm doing!"))) {
                    acceptedButton = button;
                    button.onPress(new KeyEvent(257, 0, 0));
                    break;
                }
            }
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) {
            require(++waiting < 2400, "Render regression did not enter Tutorial");
            return;
        }
        if (!started) {
            started = true;
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.renderDistance().set(12);
            mc.options.simulationDistance().set(5);
            mc.options.graphicsPreset().set(Boolean.getBoolean("ariente.tutorial.fabulous") ? GraphicsPreset.FABULOUS : GraphicsPreset.FANCY);
            mc.levelRenderer.allChanged();
            prepare(mc);
            return;
        }
        if (!ready) {
            require(++waiting < 2400, "Server did not prepare the render fixture");
            return;
        }
        if (++ticks < (stage == 0 ? 200 : 80)) {
            return;
        }
        if (capture == null) {
            RenderTarget target = stage < 4 || stage >= 8 ? liveTarget() : mc.getMainRenderTarget();
            transform = mc.gameRenderer.getMainCamera().getViewRotationProjectionMatrix(new Matrix4f());
            eye = mc.gameRenderer.getMainCamera().position();
            capture = new CompletableFuture<>();
            Screenshot.takeScreenshot(target, capture::complete);
            return;
        }
        if (!capture.isDone()) {
            return;
        }
        try (NativeImage image = capture.join()) {
            Path directory = Path.of("render-regression");
            Files.createDirectories(directory);
            image.writeToFile(directory.resolve(NAMES[stage] + ".png"));
            if (stage < 4) {
                checkLive(image);
            } else if (stage < 8) {
                checkTanks(mc, image);
            } else {
                checkScene(image);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        ArienteWorld.setup.getLogger().info("RENDER REGRESSION: {} passed", NAMES[stage]);
        capture = null;
        ready = false;
        ticks = 0;
        if (++stage == NAMES.length) {
            ArienteWorld.setup.getLogger().info("RENDER REGRESSION: all camera and tank checks passed");
            mc.stop();
        } else {
            prepare(mc);
        }
    }

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        if (Boolean.getBoolean("ariente.tutorial.renderRegression") && ready && stage >= 10 && LiveCameraRenderer.isRendering()) {
            playerExtracted = event.getRenderState().entityRenderStates.stream().anyMatch(state -> state.entityType == EntityType.PLAYER);
        }
    }

    private static void checkScene(NativeImage image) {
        long brightness = 0;
        int blue = 0;
        int bright = 0;
        int magenta = 0;
        int count = image.getWidth() * image.getHeight();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getPixel(x, y);
                int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
                brightness += r + g + b;
                if (b > 90 && b > r * 1.2 && b > g * 1.05) blue++;
                if (r > 170 && g > 170 && b > 170) bright++;
                if (x > image.getWidth() / 3 && x < image.getWidth() * 2 / 3
                        && r > 60 && b > 60 && g < r * .65 && g < b * .65) magenta++;
            }
        }
        double average = (double) brightness / (count * 3);
        ArienteWorld.setup.getLogger().info("RENDER REGRESSION: scene {} brightness {}, blue {}, bright {}, player pixels {}, extracted {}",
                NAMES[stage], average, blue, bright, magenta, playerExtracted);
        if (stage == 8) {
            dayBrightness = average;
            require(blue > count / 10 && average > 60, "The live camera must render the daytime sky");
            require(bright > 20, "The daytime sky must include the sun or clouds");
        } else if (stage == 9) {
            require(average < dayBrightness * .6 && bright > 20, "The live camera must render the night sky and celestial geometry");
        } else {
            require(playerExtracted == (stage != 12), "The local player must follow the mounted camera's visibility");
            require(stage == 12 ? magenta < 20 : magenta > 20, "The live image must show the player's dyed chestplate only when in view");
        }
    }

    private static void checkLive(NativeImage image) {
        int[] pixels = new int[(image.getWidth() - 16) * (image.getHeight() - 16)];
        int index = 0;
        for (int y = 8; y < image.getHeight() - 8; y++) {
            for (int x = 8; x < image.getWidth() - 8; x++) {
                pixels[index++] = image.getPixel(x, y);
            }
        }
        if (reference == null) {
            reference = pixels;
            long distinct = Arrays.stream(pixels).distinct().count();
            require(distinct > 8, "The live camera must render the patterned wall");
        } else {
            require(reference.length == pixels.length, "Moving the player resized the fixed camera feed");
            long difference = 0;
            for (int i = 0; i < pixels.length; i++) {
                for (int shift : new int[]{0, 8, 16}) {
                    difference += Math.abs((pixels[i] >> shift & 255) - (reference[i] >> shift & 255));
                }
            }
            double error = (double) difference / (pixels.length * 3);
            ArienteWorld.setup.getLogger().info("RENDER REGRESSION: live camera mean pixel difference {}", error);
            require(error < 2, "The mounted camera moved with the player; mean pixel difference " + error);
        }
    }

    private static void checkTanks(Minecraft mc, NativeImage image) {
        for (BlockPos generator : new BlockPos[]{NEGATIVE, POSITIVE}) {
            boolean negative = generator.equals(NEGATIVE);
            int visible = 0;
            int probes = 0;
            for (int section = 1; section <= 8; section++) {
                BlockPos tank = generator.above(section);
                boolean working = negative ? ((NegariteTankTile) mc.level.getBlockEntity(tank)).isWorking()
                        : ((PosiriteTankTile) mc.level.getBlockEntity(tank)).isWorking();
                require(working == (stage != 7), "Tank fixture has the wrong working state");
                for (double height : new double[]{.2, .5, .8}) {
                    Vec3 point = Vec3.atLowerCornerOf(tank).add(.5, height, .5).subtract(eye);
                    Vector4f projected = transform.transform(new Vector4f((float) point.x, (float) point.y, (float) point.z, 1));
                    int x = Math.round((projected.x / projected.w + 1) * .5f * image.getWidth());
                    int y = Math.round((1 - projected.y / projected.w) * .5f * image.getHeight());
                    require(x >= 3 && y >= 3 && x < image.getWidth() - 3 && y < image.getHeight() - 3, "Tank probe is outside the image");
                    boolean colored = false;
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            int rgb = image.getPixel(x + dx, y + dy);
                            int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
                            colored |= negative ? r > 150 && g > 120 && b < 170 && r > b * 1.2
                                    : r > 90 && g > 160 && b > 160 && b > r * 1.15;
                        }
                    }
                    if (colored) visible++;
                    probes++;
                }
            }
            ArienteWorld.setup.getLogger().info("RENDER REGRESSION: {} tank beam visible at {}/{} probes", negative ? "negarite" : "posirite", visible, probes);
            require(stage == 7 ? visible == 0 : visible == probes, "Tank beam does not match the full-height working state");
        }
    }

    private static RenderTarget liveTarget() {
        try {
            Field feedsField = LiveCameraRenderer.class.getDeclaredField("FEEDS");
            feedsField.setAccessible(true);
            Map<?, ?> feeds = (Map<?, ?>) feedsField.get(null);
            require(!feeds.isEmpty(), "Expected a visible live feed");
            Object feed = null;
            long newest = -1;
            for (Object candidate : feeds.values()) {
                Field update = candidate.getClass().getDeclaredField("lastUpdate");
                update.setAccessible(true);
                long time = update.getLong(candidate);
                if (time > newest) {
                    newest = time;
                    feed = candidate;
                }
            }
            Field target = feed.getClass().getDeclaredField("target");
            target.setAccessible(true);
            return (RenderTarget) target.get(feed);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void prepare(Minecraft mc) {
        mc.options.setCameraType(stage == 11 ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                ServerLevel level = player.level();
                player.setGameMode(GameType.CREATIVE);
                if (stage == 0) {
                    for (BlockPos pos : BlockPos.betweenClosed(121, 64, 108, 126, 79, 129)) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                    for (int y = 64; y <= 79; y++) {
                        for (int z = 108; z <= 129; z++) {
                            Block block = (y / 2 + z / 2) % 2 == 0 ? Blocks.MAGENTA_CONCRETE : Blocks.LIME_CONCRETE;
                            level.setBlockAndUpdate(new BlockPos(121, y, z), block.defaultBlockState());
                        }
                    }
                    DisplayBaseTile display = (DisplayBaseTile) level.getBlockEntity(DISPLAY);
                    var module = display.modules().stream().filter(m -> m.stack().is(Registration.DISPLAY_CAMERA.get())).findFirst().orElseThrow();
                    require(display.configureModule(player, module.id(), data -> {
                        data.putInt("CameraIndex", 0);
                        data.putInt("RotationSpeed", 0);
                        data.putInt("LivePan", 0);
                        data.putInt("LiveTilt", 0);
                        data.putInt("ViewZoom", 100);
                    }), "Cannot configure the live display");
                    ((CameraTile) level.getBlockEntity(CAMERA)).setLive(true);
                    for (BlockPos pos : BlockPos.betweenClosed(148, 65, 135, 158, 76, 144)) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                    for (int y = 65; y <= 76; y++) {
                        for (int x = 148; x <= 158; x++) {
                            level.setBlockAndUpdate(new BlockPos(x, y, 142), Blocks.BLACK_CONCRETE.defaultBlockState());
                        }
                    }
                    for (BlockPos generator : new BlockPos[]{NEGATIVE, POSITIVE}) {
                        boolean negative = generator.equals(NEGATIVE);
                        level.setBlockAndUpdate(generator, (negative ? Registration.NEGARITE_GENERATOR : Registration.POSIRITE_GENERATOR).get().defaultBlockState());
                        for (int y = 1; y <= 8; y++) {
                            level.setBlockAndUpdate(generator.above(y), (negative ? Registration.NEGARITE_TANK : Registration.POSIRITE_TANK).get().defaultBlockState());
                        }
                        working(level, generator, true);
                    }
                }
                if (stage == 7) {
                    working(level, NEGATIVE, false);
                    working(level, POSITIVE, false);
                }
                if (stage == 8 || stage == 9) {
                    level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "time set " + (stage == 8 ? 6000 : 18000));
                    DisplayBaseTile display = (DisplayBaseTile) level.getBlockEntity(DISPLAY);
                    var module = display.modules().stream().filter(m -> m.stack().is(Registration.DISPLAY_CAMERA.get())).findFirst().orElseThrow();
                    require(display.configureModule(player, module.id(), data -> data.putInt("LiveTilt", 90)), "Cannot aim the live camera at the sky");
                }
                if (stage == 10) {
                    level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "time set 6000");
                    level.setBlockAndUpdate(CAMERA, level.getBlockState(CAMERA).setValue(BlockStateProperties.FACING, Direction.EAST));
                    level.setBlockAndUpdate(CAMERA.east(), Blocks.AIR.defaultBlockState());
                    ((CameraTile) level.getBlockEntity(CAMERA)).setLive(true);
                    for (int x = 124; x <= 148; x++) {
                        for (int y = 64; y <= 79; y++) {
                            level.setBlockAndUpdate(new BlockPos(x, y, 274 - x), Blocks.WHITE_CONCRETE.defaultBlockState());
                        }
                    }
                    DisplayBaseTile display = (DisplayBaseTile) level.getBlockEntity(DISPLAY);
                    var module = display.modules().stream().filter(m -> m.stack().is(Registration.DISPLAY_CAMERA.get())).findFirst().orElseThrow();
                    require(display.configureModule(player, module.id(), data -> {
                        data.putInt("LiveTilt", 0);
                        data.putInt("LivePan", 45);
                    }), "Cannot aim the live camera at the player");
                    ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
                    chest.set(DataComponents.DYED_COLOR, new DyedItemColor(0xff00ff));
                    player.setItemSlot(EquipmentSlot.CHEST, chest);
                }
                player.setGameMode(stage < 4 || stage >= 8 ? GameType.CREATIVE : GameType.SPECTATOR);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                switch (stage) {
                    case 0 -> TutorialSupport.teleport(player, level, 141, 66, 130, 180, 0);
                    case 1 -> TutorialSupport.teleport(player, level, 142.5, 66, 130, 180, 0);
                    case 2 -> TutorialSupport.teleport(player, level, 139.5, 66, 132, 180, 0);
                    case 3 -> TutorialSupport.teleport(player, level, 141, 66.8, 129, 160, 0);
                    case 4, 7 -> TutorialSupport.teleport(player, level, 153.5, 67.8, 131, 0, -4);
                    case 5 -> TutorialSupport.teleport(player, level, 148.5, 67.8, 131, -29, -4);
                    case 6 -> TutorialSupport.teleport(player, level, 158.5, 67.8, 131, 29, -4);
                    case 8, 9 -> TutorialSupport.teleport(player, level, 141, 66, 130, 180, 0);
                    case 10, 11 -> TutorialSupport.teleport(player, level, 133, 69.2, 124, -97, 22);
                    case 12 -> TutorialSupport.teleport(player, level, 125.5, 69.2, 124.5, -95, 15);
                    default -> throw new IllegalStateException();
                }
                ready = true;
            } catch (RuntimeException exception) {
                failure = exception;
            }
        });
    }

    private static void working(ServerLevel level, BlockPos pos, boolean working) {
        ArienteBlockEntity tile = (ArienteBlockEntity) level.getBlockEntity(pos);
        ((GenericTileEntity) tile).setRSMode(RedstoneMode.REDSTONE_IGNORED);
        CompoundTag tag = tile.saveWithoutMetadata(level.registryAccess());
        CompoundTag info = tag.getCompoundOrEmpty("Info");
        info.putInt("dust", working ? 100000 : 0);
        tag.put("Info", info);
        tile.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        tile.setChanged();
        level.sendBlockUpdated(pos, tile.getBlockState(), tile.getBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
