package mcjty.arienteworld.client;

import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.items.armor.PacketArmorHotkey;
import mcjty.ariente.network.ArienteMessages;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Opt-in survival-client checks. Use an isolated save; this creates a small movement arena. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class EquipmentMovementClientChecks {

    private static final UUID OTHER_STEP = UUID.fromString("3342def1-c2dd-4f22-803e-2a020b52b744");
    private static boolean started;
    private static volatile boolean ready;
    private static int ticks;
    private static double height;
    private static double startZ;
    private static double poweredDistance;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("ariente.equipmentMovementCheck") || event.phase != TickEvent.Phase.END
                || mc.player == null || mc.level == null || mc.getSingleplayerServer() == null
                || !mc.level.dimension().equals(DimensionRegister.DIMENSION) || mc.isPaused() || mc.getOverlay() != null) {
            return;
        }
        var server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        if (!started) {
            started = true;
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                var level = player.serverLevel();
                for (BlockPos pos : BlockPos.betweenClosed(90060, 240, 90060, 90100, 258, 90100)) {
                    level.setBlockAndUpdate(pos, (pos.getY() == 240 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                }
                for (int x = 90078; x <= 90082; x++) {
                    level.setBlockAndUpdate(new BlockPos(x, 241, 90084), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(x, 242, 90084), Blocks.STONE_SLAB.defaultBlockState());
                }
                player.setGameMode(GameType.SURVIVAL);
                server.setFlightAllowed(false);
                player.getInventory().clearContent();
                ItemStack chest = gear(Registration.POWERSUIT_CHEST.get(), ArmorUpgradeType.FLIGHT, ArmorUpgradeType.HOVER);
                chest.getTag().putInt(ArmorUpgradeType.FLIGHT.getHotkeyKey(), 1);
                player.setItemSlot(EquipmentSlot.CHEST, chest);
                player.setItemSlot(EquipmentSlot.LEGS, gear(Registration.POWERSUIT_LEGS.get(), ArmorUpgradeType.SPEED));
                player.setItemSlot(EquipmentSlot.FEET, gear(Registration.POWERSUIT_FEET.get(), ArmorUpgradeType.STEPASSIST));
                player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get()).addTransientModifier(new AttributeModifier(
                        OTHER_STEP, "Other source step height", .2, AttributeModifier.Operation.ADDITION));
                player.teleportTo(level, 90080.5, 245, 90075.5, 0, 0);
                ready = true;
            });
            return;
        }
        if (!ready) {
            return;
        }
        ticks++;
        if (ticks == 20) {
            require(!mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying, "Survival abilities must remain unchanged");
            height = mc.player.getY();
            mc.options.keyJump.setDown(true);
        } else if (ticks == 40) {
            mc.options.keyJump.setDown(false);
            require(mc.player.getY() > height + 4, "Space must provide real upward thrust");
            height = mc.player.getY();
        } else if (ticks == 160) {
            require(Math.abs(mc.player.getY() - height) < .5, "Hover must hold altitude for more than the vanilla flying timeout");
            mc.options.keyShift.setDown(true);
        } else if (ticks == 170) {
            mc.options.keyShift.setDown(false);
            require(mc.player.getY() < height - 2, "Sneak must descend while hovering");
            height = mc.player.getY();
            ArienteMessages.INSTANCE.sendToServer(new PacketArmorHotkey(1));
        } else if (ticks == 190) {
            require(mc.player.getY() < height - 1, "Disabling Flight must restore falling even with Hover installed");
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                player.teleportTo(player.serverLevel(), 90080.5, 241, 90070.5, 0, 0);
            });
        } else if (ticks == 210) {
            startZ = mc.player.getZ();
            mc.options.keyUp.setDown(true);
        } else if (ticks == 230) {
            mc.options.keyUp.setDown(false);
            poweredDistance = mc.player.getZ() - startZ;
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                player.teleportTo(player.serverLevel(), 90080.5, 241, 90070.5, 0, 0);
            });
        } else if (ticks == 250) {
            startZ = mc.player.getZ();
            mc.options.keyUp.setDown(true);
        } else if (ticks == 270) {
            mc.options.keyUp.setDown(false);
            double ordinary = mc.player.getZ() - startZ;
            require(poweredDistance > ordinary * 1.3, "Powered Speed must move faster and stop boosting after unequip");
            ArienteWorld.setup.getLogger().info("SPEED CLIENT DISTANCES: powered={}, ordinary={}", poweredDistance, ordinary);
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                player.teleportTo(player.serverLevel(), 90080.5, 241, 90082, 0, 0);
            });
        } else if (ticks == 290) {
            require(Math.abs(mc.player.getStepHeight() - 2.102) < .01, "Step Assist must add to another source's modifier");
            mc.options.keyUp.setDown(true);
        } else if (ticks == 305) {
            mc.options.keyUp.setDown(false);
            require(mc.player.getY() >= 242.4, "Step Assist must climb a real 1.5-block obstacle");
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                ItemStack chest = gear(Registration.POWERSUIT_CHEST.get(), ArmorUpgradeType.FLIGHT, ArmorUpgradeType.HOVER);
                chest.getTag().putInt("power", 2);
                player.setItemSlot(EquipmentSlot.CHEST, chest);
                player.teleportTo(player.serverLevel(), 90080.5, 250, 90075, 0, 0);
                player.setDeltaMovement(Vec3.ZERO);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            });
        } else if (ticks == 325) {
            require(Math.abs(mc.player.getStepHeight() - .8) < .01, "Unequipping boots must preserve the other step modifier");
            require(mc.player.getY() < 249, "Fuel exhaustion must restore gravity");
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get()).removeModifier(OTHER_STEP);
            });
            ArienteWorld.setup.getLogger().info("EQUIPMENT SURVIVAL CLIENT CHECKS PASSED: thrust, 120-tick hover, descent, toggle, speed, step obstacle, unequip, fuel exhaustion; allow-flight=false");
            mc.stop();
        }
    }

    private static ItemStack gear(Item item, ArmorUpgradeType... modules) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putInt("power", 3000);
        for (ArmorUpgradeType module : modules) {
            stack.getTag().putBoolean(module.getModuleKey(), true);
        }
        return stack;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Equipment survival client regression: " + message);
        }
    }
}
