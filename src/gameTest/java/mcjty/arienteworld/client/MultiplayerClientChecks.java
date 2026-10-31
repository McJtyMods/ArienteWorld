package mcjty.arienteworld.client;

import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.items.armor.PacketArmorHotkey;
import mcjty.ariente.items.modules.ModuleSupport;
import mcjty.ariente.network.ArienteMessages;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Real network check; deliberately has no access to the server or its fixture. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class MultiplayerClientChecks {

    private static int ticks;
    private static int stage;
    private static int idle;
    private static boolean connecting;
    private static boolean sawPeer;
    private static boolean sawEquippedPeer;
    private static int unequippedPeerTicks;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("ariente.multiplayerCheck") || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            if (!connecting && ++idle > 60) {
                String address = "127.0.0.1:25596";
                connecting = true;
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                        new ServerData("Ariente verification", address, false), false);
            }
            return;
        }
        if (mc.getSingleplayerServer() != null) {
            throw new IllegalStateException("Multiplayer verification requires a dedicated server");
        }
        ticks++;
        sawPeer |= mc.level.players().stream().anyMatch(p -> p != mc.player);
        if (Boolean.getBoolean("ariente.multiplayerObserver")) {
            if (ticks == 100) {
                auditModels(mc);
                mc.reloadResourcePacks().thenRun(() -> auditModels(mc));
            }
            if (ticks > 80) {
                check(mc.player.hasEffect(MobEffects.NIGHT_VISION), "Another player's hotkey/death must not disable our gear");
            }
            mc.level.players().stream().filter(p -> p != mc.player).forEach(peer -> {
                if (!peer.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                    sawEquippedPeer = true;
                    unequippedPeerTicks = 0;
                } else if (sawEquippedPeer && !peer.isDeadOrDying()) {
                    unequippedPeerTicks++;
                }
            });
            if (unequippedPeerTicks >= 40) {
                ArienteWorld.setup.getLogger().info("MULTIPLAYER OBSERVER PASSED: independent equipment while another client toggles, reconnects and dies");
                mc.stop();
            }
            check(ticks < 1800, "The observer must see its peer return without gear after death");
            return;
        }
        if (stage == 0 && ticks == 100) {
            check(sawPeer, "The pilot must see the observer client");
            check(mc.player.hasEffect(MobEffects.NIGHT_VISION), "Initial equipment/effect must reach the client");
            ArienteMessages.INSTANCE.sendToServer(new PacketArmorHotkey(1));
        } else if (stage == 0 && ticks == 140) {
            check(!mc.player.hasEffect(MobEffects.NIGHT_VISION)
                    && !ModuleSupport.hasWorkingUpgrade(mc.player.getItemBySlot(EquipmentSlot.HEAD), ArmorUpgradeType.NIGHTVISION),
                    "A real hotkey packet must stop equipment and synchronize its effect");
            ArienteMessages.INSTANCE.sendToServer(new PacketArmorHotkey(1));
        } else if (stage == 0 && ticks == 180) {
            check(mc.player.hasEffect(MobEffects.NIGHT_VISION), "A real hotkey packet must resume equipment");
            mc.level.disconnect();
            mc.clearLevel();
            mc.setScreen(new TitleScreen());
            connecting = false;
            idle = ticks = 0;
            stage = 1;
        } else if (stage == 1 && ticks == 100) {
            check(mc.player.hasEffect(MobEffects.NIGHT_VISION)
                    && mc.player.getItemBySlot(EquipmentSlot.HEAD).getOrCreateTag().getInt(ArmorUpgradeType.NIGHTVISION.getHotkeyKey()) == 1,
                    "Equipment, hotkeys and effects must survive a real disconnect/login");
            mc.player.connection.sendCommand("kill");
            stage = 2;
            ticks = 0;
        } else if (stage == 2 && mc.player.isDeadOrDying()) {
            mc.player.respawn();
            mc.setScreen(null);
            stage = 3;
            ticks = 0;
        } else if (stage == 3 && ticks == 100) {
            check(mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !mc.player.hasEffect(MobEffects.NIGHT_VISION),
                    "Death/respawn without retained gear must remove the old suit effect");
            ArienteWorld.setup.getLogger().info("MULTIPLAYER PILOT PASSED: packet toggles, equipment synchronization, reconnect and death/respawn");
            mc.stop();
        }
        if (ticks > 1200) {
            throw new IllegalStateException("Multiplayer check timed out at stage " + stage);
        }
    }

    private static void auditModels(Minecraft mc) {
        var missing = mc.getModelManager().getMissingModel();
        int[] counts = {0, 0};
        ForgeRegistries.ITEMS.getEntries().forEach(entry -> {
            if (entry.getKey().location().getNamespace().equals("ariente")) {
                check(mc.getItemRenderer().getModel(new ItemStack(entry.getValue()), mc.level, mc.player, 0) != missing,
                        "Missing registered item model: " + entry.getKey().location());
                counts[0]++;
            }
        });
        ForgeRegistries.BLOCKS.getEntries().forEach(entry -> {
            if (entry.getKey().location().getNamespace().equals("ariente")) {
                entry.getValue().getStateDefinition().getPossibleStates().forEach(state -> {
                    check(mc.getBlockRenderer().getBlockModel(state) != missing, "Missing blockstate model: " + state);
                    counts[1]++;
                });
            }
        });
        ArienteWorld.setup.getLogger().info("FULL MODEL AUDIT PASSED: {} registered items, {} block states", counts[0], counts[1]);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
