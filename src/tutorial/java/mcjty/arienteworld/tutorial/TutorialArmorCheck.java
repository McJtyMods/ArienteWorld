package mcjty.arienteworld.tutorial;

import com.mojang.blaze3d.platform.NativeImage;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/** Client pixel check for each power armor piece, the complete suit and removal. */
@EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public final class TutorialArmorCheck {

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static boolean started;
    private static int stage;
    private static int ticks;
    private static int waiting;
    private static int[] barePlayer;
    private static CompletableFuture<NativeImage> capture;

    private TutorialArmorCheck() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ariente.tutorial.armorCheck")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
            if (++waiting % 40 == 0 && mc.gui.screen() != null) {
                for (var child : mc.gui.screen().children()) {
                    if (child instanceof AbstractButton button && (button.getMessage().getString().startsWith("Proceed")
                            || button.getMessage().getString().equals("Continue")
                            || button.getMessage().getString().equalsIgnoreCase("I know what I'm doing!"))) {
                        button.onPress(new KeyEvent(257, 0, 0));
                        break;
                    }
                }
            }
            require(waiting < 2400, "Armor fixture world did not open");
            return;
        }
        if (!started) {
            started = true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.options.bobView().set(false);
            mc.getSingleplayerServer().execute(() -> prepare(mc));
        }
        if (++ticks < (stage == 0 ? 120 : 60)) return;
        if (capture == null) {
            capture = new CompletableFuture<>();
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), capture::complete);
            return;
        }
        if (!capture.isDone()) return;
        try (NativeImage image = capture.join()) {
            var directory = mc.gameDirectory.toPath().resolve("screenshots/power-armor-checks");
            Files.createDirectories(directory);
            image.writeToFile(directory.resolve("armor-" + stage + ".png"));
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
            require(missing == 0, "Missing player/armor texture at stage " + stage);
            if (stage == 0) barePlayer = image.getPixels();
            if (stage >= 1 && stage <= 5) require(changed > 30, "Armor piece did not render at stage " + stage);
            System.out.println("NEOFORGE POWER ARMOR CHECK: stage=" + stage + " missing=" + missing + " changed=" + changed);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        capture = null;
        ticks = 0;
        if (++stage == 7) {
            System.out.println("NEOFORGE POWER ARMOR CHECKS PASSED: individual slots, complete suit and removal");
            mc.stop();
        } else {
            mc.getSingleplayerServer().execute(() -> prepare(mc));
        }
    }

    private static void prepare(Minecraft mc) {
        var level = mc.getSingleplayerServer().overworld();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
        if (stage == 0) {
            player.setGameMode(GameType.CREATIVE);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0, false, false));
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) level.setBlockAndUpdate(new BlockPos(x, 69, z), Blocks.STONE.defaultBlockState());
            for (int x = -6; x <= 6; x++) for (int y = 70; y <= 77; y++) level.setBlockAndUpdate(new BlockPos(x, y, -3), Blocks.STONE.defaultBlockState());
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            TutorialSupport.teleport(player, level, .5, 70, .5, 0, 0);
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "time set 6000");
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "gamerule minecraft:advance_time false");
        }
        Item[] suit = {Registration.POWERSUIT_HEAD.get(), Registration.POWERSUIT_CHEST.get(), Registration.POWERSUIT_LEGS.get(), Registration.POWERSUIT_FEET.get()};
        for (int i = 0; i < SLOTS.length; i++) player.setItemSlot(SLOTS[i], stage == 5 || stage == 1 + i ? new ItemStack(suit[i]) : ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
