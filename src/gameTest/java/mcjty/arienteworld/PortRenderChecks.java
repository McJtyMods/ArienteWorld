package mcjty.arienteworld;

import com.mojang.blaze3d.platform.NativeImage;
import mcjty.ariente.blocks.utility.autofield.AbstractNodeTile;
import mcjty.ariente.blocks.utility.autofield.NodeOrientation;
import mcjty.ariente.display.DisplayAppearance;
import mcjty.ariente.display.DisplayBackground;
import mcjty.ariente.display.DisplayBaseTile;
import mcjty.ariente.multipart.MultipartTE;
import mcjty.ariente.setup.Registration;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Real client regressions for multipart devices and opaque display cloud occlusion. */
public final class PortRenderChecks implements ClientModInitializer {

    private static final BlockPos PANEL = new BlockPos(4, 184, 0);
    private static int ticks;
    private static int stage;
    private static int waiting;
    private static CompletableFuture<NativeImage> capture;
    private static int[] baseline;

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("ariente.portRenderChecks")) ClientTickEvents.END_CLIENT_TICK.register(PortRenderChecks::tick);
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
            require(waiting < 1200, "Render fixture world did not open");
            return;
        }
        mc.options.hideGui = true;
        if (stage == 0) {
            stage = 1;
            mc.options.cloudStatus().set(CloudStatus.OFF);
            mc.getSingleplayerServer().execute(() -> prepareNodes(mc));
        }
        if (++ticks < 140) return;
        if (capture == null) {
            if (stage == 1) inspectNodes(mc);
            capture = new CompletableFuture<>();
            Screenshot.takeScreenshot(mc.getMainRenderTarget(), capture::complete);
            return;
        }
        if (!capture.isDone()) return;
        try (NativeImage image = capture.join()) {
            var directory = mc.gameDirectory.toPath().resolve("screenshots/render-checks");
            Files.createDirectories(directory);
            image.writeToFile(directory.resolve("stage-" + stage + ".png"));
            if (stage == 1) checkNodePixels(mc, image);
            if (stage == 2) baseline = image.getPixels();
            if (stage == 3) {
                int inside = 0;
                int outside = 0;
                int width = image.getWidth(), height = image.getHeight();
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int before = baseline[y * width + x], after = image.getPixel(x, y);
                    if (Math.abs((before >> 16 & 255) - (after >> 16 & 255))
                            + Math.abs((before >> 8 & 255) - (after >> 8 & 255)) + Math.abs((before & 255) - (after & 255)) > 12) {
                        if (x > width * .38 && x < width * .62 && y > height * .25 && y < height * .60) inside++;
                        else outside++;
                    }
                }
                System.out.println("DISPLAY CLOUD CHECK: inside=" + inside + " outside=" + outside);
                require(outside > 100, "Clouds must be visible around the test panel");
                require(inside < 10, "Clouds appear over the opaque display");
            }
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        capture = null;
        ticks = 0;
        if (stage++ == 3) {
            System.out.println("FABRIC RENDER CHECKS PASSED: multipart devices and opaque display clouds");
            mc.stop();
        } else if (stage == 2) {
            mc.getSingleplayerServer().execute(() -> preparePanel(mc));
        } else if (stage == 3) {
            mc.options.cloudStatus().set(CloudStatus.FANCY);
        }
    }

    private static void prepareNodes(Minecraft mc) {
        ServerLevel level = mc.getSingleplayerServer().overworld();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        for (int x = -5; x <= 5; x++) for (int y = 184; y <= 193; y++) level.setBlockAndUpdate(new BlockPos(x, y, 0), Blocks.AIR.defaultBlockState());
        Block[] blocks = {Registration.INPUT_ITEM_NODE.get(), Registration.OUTPUT_ITEM_NODE.get(),
                Registration.SENSOR_ITEM_NODE.get(), Registration.ROUND_ROBIN_NODE.get()};
        for (int i = 0; i < blocks.length; i++) {
            BlockState state = blocks[i].defaultBlockState().setValue(AbstractNodeTile.ORIENTATION, NodeOrientation.SOUTH_UE);
            var ordinary = new BlockPos(i * 2 - 3, 185, 0);
            var multipartPos = ordinary.above();
            level.setBlockAndUpdate(ordinary, state);
            level.setBlockAndUpdate(multipartPos, Registration.MULTIPART_BLOCK.get().defaultBlockState());
            var tile = (MultipartTE) level.getBlockEntity(multipartPos);
            tile.addPart(NodeOrientation.SOUTH_UE.getSlot(), state, ((EntityBlock) blocks[i]).newBlockEntity(multipartPos, state));
        }
        player.teleportTo(level, .5, 184.5, -7, Set.of(), 0, 0, true);
    }

    private static void inspectNodes(Minecraft mc) {
        for (int i = 0; i < 4; i++) {
            var pos = new BlockPos(i * 2 - 3, 186, 0);
            require(mc.level.getBlockEntity(pos) instanceof MultipartTE, "Multipart fixture missing at " + pos);
            var tile = (MultipartTE) mc.level.getBlockEntity(pos);
            System.out.println("MULTIPART CHECK: " + pos + " parts=" + tile.getParts().size());
            require(tile.getParts().size() == 1, "Multipart parts were not synchronized");
            var state = tile.getParts().values().iterator().next().getState();
            var parts = new ArrayList<BlockStateModelPart>();
            mc.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(0), parts);
            long quads = parts.stream().mapToLong(part -> part.getQuads(null).size()
                    + Arrays.stream(Direction.values()).mapToInt(side -> part.getQuads(side).size()).sum()).sum();
            System.out.println("MULTIPART MODEL CHECK: " + state + " quads=" + quads);
            require(quads >= 6, "Multipart node model has no geometry");
        }
    }

    private static void checkNodePixels(Minecraft mc, NativeImage image) {
        var camera = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        var transform = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        for (int i = 0; i < 4; i++) {
            var pos = new BlockPos(i * 2 - 3, 186, 0);
            var tile = (MultipartTE) mc.level.getBlockEntity(pos);
            var state = tile.getParts().values().iterator().next().getState();
            Vec3 point = state.getShape(mc.level, pos).bounds().getCenter().add(Vec3.atLowerCornerOf(pos)).subtract(camera.pos);
            var projected = transform.transform(new Vector4f((float) point.x, (float) point.y, (float) point.z, 1));
            int x = Math.round((projected.x / projected.w + 1) * image.getWidth() / 2);
            int y = Math.round((1 - projected.y / projected.w) * image.getHeight() / 2);
            require(x >= 5 && y >= 5 && x < image.getWidth() - 5 && y < image.getHeight() - 5, "Node probe is outside image");
            int visible = 0;
            for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) {
                int rgb = image.getPixel(x + dx, y + dy);
                if ((rgb >> 16 & 255) + (rgb >> 8 & 255) + (rgb & 255) < 400) visible++;
            }
            System.out.println("MULTIPART PIXEL CHECK: " + state + " visible=" + visible);
            require(visible > 8, "Multipart device is invisible: " + state);
        }
    }

    private static void preparePanel(Minecraft mc) {
        ServerLevel level = mc.getSingleplayerServer().overworld();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
        for (int x = -5; x <= 5; x++) for (int y = 184; y <= 191; y++) level.setBlockAndUpdate(new BlockPos(x, y, 0), Blocks.AIR.defaultBlockState());
        for (int column = 0; column < 8; column++) {
            BlockPos pos = PANEL.relative(Direction.WEST, column);
            level.setBlockAndUpdate(pos, Registration.DISPLAY_BASE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            var tile = (DisplayBaseTile) level.getBlockEntity(pos);
            var tag = tile.saveWithoutMetadata(level.registryAccess());
            tag.putInt("Height", 8);
            new DisplayAppearance(DisplayBackground.SOLID, 0x163a52, 0, false).write(tag);
            tile.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            tile.changed();
        }
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "time set 6000");
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "gamerule minecraft:advance_time false");
        player.teleportTo(level, .5, 185, -10, Set.of(), 0, -6, true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
