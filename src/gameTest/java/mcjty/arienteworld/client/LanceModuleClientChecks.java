package mcjty.arienteworld.client;

import com.mojang.blaze3d.platform.InputConstants;
import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.items.EnergyHolderItem;
import mcjty.ariente.bindings.Binding;
import mcjty.ariente.bindings.KeyBindings;
import mcjty.ariente.gui.ModGuis;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.hologui.gui.HoloGuiEntity;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in end-to-end check of the lance's configuration binding, synchronization and module tooltips. */
@JeiPlugin
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class LanceModuleClientChecks implements IModPlugin {

    private static IJeiRuntime runtime;
    private static boolean started;
    private static volatile boolean prepared;
    private static boolean reloadReady;
    private static int renders;
    private static int ticks;
    private static int stage;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(ArienteWorld.MODID, "lance_module_check");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("ariente.lanceModuleCheck") || event.phase != TickEvent.Phase.END
                || mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.isPaused() || mc.getOverlay() != null) {
            return;
        }
        require(++ticks < 600, "The lance configuration check timed out at stage " + stage);
        if (!started) {
            started = true;
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                player.setGameMode(GameType.CREATIVE);
                player.setHealth(player.getMaxHealth());
                player.deathTime = 0;
                player.setNoGravity(true);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.serverLevel().getEntitiesOfClass(HoloGuiEntity.class, player.getBoundingBox().inflate(20)).forEach(HoloGuiEntity::discard);
                player.getInventory().clearContent();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.ENERGY_LANCE.get()));
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    player.setItemSlot(slot, ItemStack.EMPTY);
                }
                player.getInventory().setItem(9, new ItemStack(Registration.MODULE_ENERGY.get(), 4));
                ItemStack holder = new ItemStack(Registration.ENERGY_HOLDER.get());
                holder.getOrCreateTag().putInt("negarite", 1);
                holder.getOrCreateTag().putInt("posirite", 1);
                holder.getOrCreateTag().putLong("chargeCapacity", 100);
                holder.getOrCreateTag().putLong("charge", 74);
                player.getInventory().setItem(12, holder);
                player.getInventory().setItem(10, new ItemStack(Registration.DUST_NEGARITE.get()));
                player.getInventory().setItem(11, new ItemStack(Registration.DUST_POSIRITE.get()));
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                prepared = true;
            });
            return;
        }
        if (!prepared || !mc.player.getMainHandItem().is(Registration.ENERGY_LANCE.get())) {
            return;
        }
        var panels = mc.level.getEntitiesOfClass(HoloGuiEntity.class, mc.player.getBoundingBox().inflate(20));
        var tag = mc.player.getMainHandItem().getOrCreateTag();
        if (stage == 0) {
            if (!mc.player.isAlive() || !mc.level.hasChunkAt(mc.player.blockPosition())) {
                return;
            }
            if (mc.screen != null) {
                mc.setScreen(null);
                return;
            }
            press(KeyBindings.configureArmor);
            stage = 1;
        } else if (stage == 1 && !panels.isEmpty() && panels.get(0).getGuiId().equals(ModGuis.GUI_ARMOR)) {
            int id = panels.get(0).getId();
            var uuid = mc.player.getUUID();
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                var holo = (HoloGuiEntity) player.serverLevel().getEntity(id);
                holo.setCloseStrategy(0);
                holo.setTimeout(1200);
                Vec3 position = player.position();
                click(player, holo, 3, 3);
                require(holo.getGuiId().equals(ModGuis.GUI_ARMOR_SABRE), "The 0-key menu must configure the held lance");
                click(player, holo, 1.5, 2.7);
                click(player, holo, 2.5, 2.7);
                click(player, holo, 3.5, 2.7);
                player.teleportTo(position.x, position.y, position.z);
            });
            stage = 2;
        } else if (stage == 2 && renders == 2) {
            require(EnergyHolderItem.count(mc.player, "negarite") == 1 && EnergyHolderItem.count(mc.player, "posirite") == 1
                            && mc.player.getInventory().countItem(Registration.MODULE_ENERGY.get()) == 3,
                    "Lance fuel, installed modules and inventory must synchronize to the client");
            press(KeyBindings.armorHotkey1);
            stage = 3;
        } else if (stage == 3 && !tag.getBoolean(ArmorUpgradeType.ENERGY.getModuleKey())) {
            press(KeyBindings.configureArmor);
            stage = 4;
        } else if (stage == 4 && !panels.isEmpty() && panels.get(0).getGuiId().equals(ModGuis.GUI_ARMOR)) {
            press(KeyBindings.configureArmor);
            stage = 5;
        } else if (stage == 5 && panels.isEmpty() && (!Boolean.getBoolean("ariente.laserCheck") || LaserClientChecks.checked)) {
            ArienteWorld.setup.getLogger().info("LANCE MODULE CLIENT CHECK PASSED: 0-key menu, installation, fuel, hotkeys, return/close, JEI and hologram tooltips, resource reload");
            mc.stop();
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("ariente.lanceModuleCheck") || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || stage != 2 || runtime == null || mc.player == null || renders >= 2 || renders == 1 && !reloadReady) {
            return;
        }
        var tag = mc.player.getMainHandItem().getOrCreateTag();
        if (!tag.getBoolean(ArmorUpgradeType.ENERGY.getModuleKey()) || tag.getInt(ArmorUpgradeType.ENERGY.getHotkeyKey()) != 1) {
            return;
        }
        HologramRenderingChecks.checkGui(mc, ModGuis.GUI_ARMOR_SABRE, 1, true);
        SystemRenderingChecks.checkModuleTooltips(mc, runtime);
        if (++renders == 1) {
            mc.reloadResourcePacks().thenRun(() -> reloadReady = true);
        }
    }

    private static void press(Binding binding) {
        KeyMapping.click(InputConstants.getKey(binding.getDisplayName()));
        Binding.checkBindings();
    }

    private static void click(Player player, HoloGuiEntity holo, double x, double y) {
        Vec3 normal = holo.getLookAngle();
        Vec3 right = normal.cross(new Vec3(0, 1, 0));
        float factor = 1 - (1 - holo.getScale()) * 1.6f;
        float offset = .8f + (1 - holo.getScale()) * 13.2f;
        Vec3 target = holo.position().add(right.scale((x + offset) * factor / 10 - .5)).add(0, 1 - (y + offset) * factor / 10, 0);
        Vec3 eyes = target.subtract(normal);
        player.moveTo(eyes.x, eyes.y - player.getEyeHeight(), eyes.z, holo.getYRot(), 0);
        player.attack(holo);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Lance module client check: " + message);
        }
    }
}
