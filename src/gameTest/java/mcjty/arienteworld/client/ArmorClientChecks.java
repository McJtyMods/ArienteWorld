package mcjty.arienteworld.client;

import mcjty.ariente.Ariente;
import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.gui.ModGuis;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.hologui.api.CloseStrategy;
import mcjty.hologui.gui.HoloGuiEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Explicitly enabled integrated-client fixture for inspecting the worn suit and module controls. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class ArmorClientChecks {

    public static boolean removalChecked;
    private static boolean opened;
    private static int ticks;
    private static volatile HoloGuiEntity hologram;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("ariente.armorGuiCheck") || event.phase != TickEvent.Phase.END
                || mc.level == null || mc.player == null || mc.getSingleplayerServer() == null
                || mc.isPaused() || mc.getOverlay() != null
                || !mc.level.dimension().equals(DimensionRegister.DIMENSION)
                || !mc.level.hasChunkAt(new BlockPos(90020, 241, 90023))) {
            return;
        }
        var server = mc.getSingleplayerServer();
        var playerId = mc.player.getUUID();
        if (hologram != null && ++ticks == 100) {
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                click(player, hologram, 1.5, 2.3);
                click(player, hologram, 2.5, 2.3);
                click(player, hologram, 3.5, 2.3);
                player.teleportTo(player.serverLevel(), 90020.5, 241, 90023.5, 180, 0);
            });
        } else if (ticks == 140) {
            var tag = mc.player.getItemBySlot(EquipmentSlot.CHEST).getOrCreateTag();
            if (!tag.getBoolean(ArmorUpgradeType.ENERGY.getModuleKey()) || tag.getInt(ArmorUpgradeType.ENERGY.getHotkeyKey()) != 1
                    || mc.player.getInventory().countItem(Registration.MODULE_ENERGY.get()) != 3) {
                throw new IllegalStateException("Armor GUI changes must synchronize worn equipment and consumed inventory modules to the client");
            }
            ArienteWorld.setup.getLogger().info("ARMOR GUI CLIENT CHECK PASSED: installed/enabled module, hotkey and inventory reach client");
        }
        if (ticks == 200 && Boolean.getBoolean("ariente.armorGuiStateCheck")) {
            server.execute(() -> click(server.getPlayerList().getPlayer(playerId), hologram, 1.5, 2.3));
        } else if (ticks == 240 && Boolean.getBoolean("ariente.armorGuiStateCheck")) {
            var tag = mc.player.getItemBySlot(EquipmentSlot.CHEST).getOrCreateTag();
            if (tag.contains(ArmorUpgradeType.ENERGY.getModuleKey()) || tag.contains(ArmorUpgradeType.ENERGY.getWorkingKey())
                    || tag.contains(ArmorUpgradeType.ENERGY.getHotkeyKey())
                    || mc.player.getInventory().countItem(Registration.MODULE_ENERGY.get()) != 4) {
                throw new IllegalStateException("Removal must synchronize cleared module/working/key state and returned item to the client");
            }
            removalChecked = true;
            ArienteWorld.setup.getLogger().info("ARMOR GUI REMOVAL CLIENT CHECK PASSED: returned item and cleared equipment state");
        } else if (ticks == 280 && Boolean.getBoolean("ariente.armorGuiStateCheck")) {
            mc.stop();
        }
        if (opened) {
            return;
        }
        opened = true;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui = false;
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            var level = player.serverLevel();
            level.getEntitiesOfClass(HoloGuiEntity.class, new AABB(player.blockPosition()).inflate(30)).forEach(HoloGuiEntity::discard);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Registration.POWERSUIT_HEAD.get()));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Registration.POWERSUIT_CHEST.get()));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Registration.POWERSUIT_LEGS.get()));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Registration.POWERSUIT_FEET.get()));
            ItemStack sabre = new ItemStack(Registration.ENHANCED_ENERGY_SABRE.get());
            sabre.getOrCreateTag().putBoolean(ArmorUpgradeType.ENERGY.getModuleKey(), true);
            sabre.getOrCreateTag().putInt(ArmorUpgradeType.ENERGY.getHotkeyKey(), 1);
            player.setItemSlot(EquipmentSlot.MAINHAND, sabre);
            player.getInventory().setItem(9, new ItemStack(Registration.MODULE_ENERGY.get(), 4));
            player.getInventory().setItem(10, new ItemStack(Registration.MODULE_ARMOR.get(), 4));
            player.teleportTo(level, 90020.5, 241, 90023.5, 180, 0);
            var holo = Ariente.guiHandler.openHoloGui(player, ModGuis.GUI_ARMOR_CHEST, 1);
            holo.setCloseStrategy(CloseStrategy.NEVER);
            hologram = (HoloGuiEntity) holo;
            ArienteWorld.setup.getLogger().info("ARMOR GUI VISUAL FIXTURE READY: module controls and equipped suit");
        });
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
}
