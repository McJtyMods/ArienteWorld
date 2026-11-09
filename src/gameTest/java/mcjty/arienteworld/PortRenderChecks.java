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
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

/** Client pixel regressions for multipart devices, display occlusion and power armor. */
public final class PortRenderChecks implements ClientModInitializer {

    private static final BlockPos PANEL = new BlockPos(4, 184, 0);
    private static int ticks;
    private static int stage;
    private static int waiting;
    private static CompletableFuture<NativeImage> capture;
    private static int[] baseline;
    private static int[] barePlayer;
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("ariente.portRenderChecks")) ClientTickEvents.END_CLIENT_TICK.register(PortRenderChecks::tick);
    }

    private static void tick(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
            if (++waiting % 40 == 0 && mc.gui.screen() != null) {
                for (var child : mc.gui.screen().children()) {
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
        if (stage == 0) {
            stage = 1;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.cloudStatus().set(CloudStatus.OFF);
            mc.getSingleplayerServer().execute(() -> prepareNodes(mc));
        }
        if (++ticks < (stage >= 4 ? 60 : 140)) return;
        if (capture == null) {
            if (stage == 1) inspectNodes(mc);
            capture = new CompletableFuture<>();
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), capture::complete);
            return;
        }
        if (!capture.isDone()) return;
        try (NativeImage image = capture.join()) {
            var directory = mc.gameDirectory.toPath().resolve("screenshots/render-checks");
            Files.createDirectories(directory);
            image.writeToFile(directory.resolve("stage-" + stage + ".png"));
            if (stage == 1) checkNodePixels(mc, image);
            if (stage >= 4) checkArmorPixels(image);
            if (stage == 2) baseline = image.getPixels();
            if (stage == 3) {
                int inside = 0;
                int outside = 0;
                int width = image.getWidth(), height = image.getHeight();
                int panelColor = baseline[(height / 2) * width + width / 2] & 0xffffff;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int before = baseline[y * width + x], after = image.getPixel(x, y);
                    if (Math.abs((before >> 16 & 255) - (after >> 16 & 255))
                            + Math.abs((before >> 8 & 255) - (after >> 8 & 255)) + Math.abs((before & 255) - (after & 255)) > 12) {
                        if ((before & 0xffffff) == panelColor) inside++;
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
        if (stage++ == 10) {
            System.out.println("FABRIC RENDER CHECKS PASSED: multipart devices, opaque display clouds and all power armor slots");
            mc.stop();
        } else if (stage == 2) {
            mc.getSingleplayerServer().execute(() -> preparePanel(mc));
        } else if (stage == 3) {
            mc.options.cloudStatus().set(CloudStatus.FANCY);
        } else if (stage >= 4) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.options.bobView().set(false);
            mc.getSingleplayerServer().execute(() -> prepareArmor(mc));
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
        var camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
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

    private static void prepareArmor(Minecraft mc) {
        var level = mc.getSingleplayerServer().overworld();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
        if (stage == 4) {
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) level.setBlockAndUpdate(new BlockPos(x, 69, z), Blocks.STONE.defaultBlockState());
            for (int x = -6; x <= 6; x++) for (int y = 70; y <= 77; y++) level.setBlockAndUpdate(new BlockPos(x, y, -3), Blocks.STONE.defaultBlockState());
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(level, .5, 70, .5, Set.of(), 0, 0, true);
        }
        Item[] suit = {Registration.POWERSUIT_HEAD.get(), Registration.POWERSUIT_CHEST.get(), Registration.POWERSUIT_LEGS.get(), Registration.POWERSUIT_FEET.get()};
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            player.setItemSlot(ARMOR_SLOTS[i], stage == 9 || stage == 5 + i ? new ItemStack(suit[i]) : ItemStack.EMPTY);
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    private static void checkArmorPixels(NativeImage image) {
        int missing = 0;
        int changed = 0;
        int width = image.getWidth(), height = image.getHeight();
        for (int y = height / 8; y < height * 19 / 20; y++) for (int x = width * 7 / 20; x < width * 13 / 20; x++) {
            int rgb = image.getPixel(x, y);
            int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
            if (r > 100 && b > 100 && g < r * .2 && g < b * .2) missing++;
            if (barePlayer != null) {
                int before = barePlayer[y * width + x];
                int delta = Math.abs((before >> 16 & 255) - r) + Math.abs((before >> 8 & 255) - g) + Math.abs((before & 255) - b);
                if (delta > 30) changed++;
            }
        }
        System.out.println("POWER ARMOR PIXEL CHECK: stage=" + stage + " missing=" + missing + " changed=" + changed);
        require(missing == 0, "Player/armor rendered with a missing texture at stage " + stage);
        if (stage == 4) barePlayer = image.getPixels();
        if (stage >= 5 && stage <= 9) require(changed > 30, "Armor piece did not render at stage " + stage);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
