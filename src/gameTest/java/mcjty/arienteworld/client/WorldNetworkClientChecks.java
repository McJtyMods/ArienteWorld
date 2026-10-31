package mcjty.arienteworld.client;

import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.WorldLifecycleFixture;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** Real clients with no access to server state; fixture commands invoke production travel. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class WorldNetworkClientChecks {

    private static int stage;
    private static int ticks;
    private static int idle;
    private static int total;
    private static boolean connecting;
    private static boolean audited;
    private static volatile boolean reloaded;
    private static boolean sawPeer;
    private static boolean resetRequested;
    private static volatile boolean peersReady;

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent event) {
        if (Boolean.getBoolean("arienteworld.worldClientCheck") && event.getMessage().getString().equals("WORLD CHECK: peers ready")) {
            peersReady = true;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("arienteworld.worldClientCheck") || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(3);
        require(++total < 6000, "Network fixture timed out at stage " + stage);
        if (mc.getOverlay() != null) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            if (stage == 7) {
                // The server can complete its checkpoint as soon as both DONE commands arrive.
                mc.stop();
                return;
            }
            require(!(mc.screen instanceof DisconnectedScreen) || stage == 0 || stage == 3,
                    "Server disconnected during world verification at stage " + stage);
            if (!connecting && ++idle >= 60) {
                connecting = true;
                String address = "127.0.0.1:25597";
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                        new ServerData("Ariente world lifecycle", address, false), false);
            }
            return;
        }
        require(mc.getSingleplayerServer() == null, "This check requires a real dedicated server");
        ticks++;
        if (stage == 0) {
            if (mc.level.dimension().equals(DimensionRegister.DIMENSION)) {
                // A replay may start after an interrupted journey; use the real return path first.
                if (!resetRequested) {
                    resetRequested = true;
                    mc.player.connection.sendCommand("worldverify return");
                }
                return;
            }
            if (!audited && ticks >= 60) {
                checkInventory(mc);
                audited = true;
                auditModels(mc);
                mc.reloadResourcePacks().thenRun(() -> {
                    auditModels(mc);
                    reloaded = true;
                });
            }
            if (reloaded && ticks >= 100) {
                mc.player.connection.sendCommand("worldverify enter");
                stage = 1;
            }
        } else if (stage == 1 && mc.level.dimension().equals(DimensionRegister.DIMENSION)) {
            ticks = 0;
            stage = 2;
        } else if (stage == 2) {
            if (!sawPeer && mc.level.players().stream().anyMatch(peer -> peer != mc.player && peer.distanceToSqr(mc.player) < 25)) {
                sawPeer = true;
                mc.player.connection.sendCommand("worldverify arrived");
            }
            if (ticks >= 80 && peersReady) {
                require(sawPeer, "Both clients must observe their peer before either starts reconnecting");
                checkInventory(mc);
                mc.level.disconnect();
                mc.clearLevel();
                mc.setScreen(new TitleScreen());
                connecting = false;
                idle = ticks = 0;
                stage = 3;
            }
        } else if (stage == 3 && ticks >= 80) {
            require(mc.level.dimension().equals(DimensionRegister.DIMENSION), "Reconnect must retain the player's Ariente dimension");
            checkInventory(mc);
            mc.player.connection.sendCommand("worldverify die");
            stage = 4;
        } else if (stage == 4 && mc.player.isDeadOrDying()) {
            mc.player.respawn();
            mc.setScreen(null);
            ticks = 0;
            stage = 5;
        } else if (stage == 5 && ticks >= 80 && mc.player.isAlive()) {
            require(mc.level.dimension().equals(DimensionRegister.DIMENSION), "The forced test respawn must remain in Ariente");
            checkInventory(mc);
            mc.player.connection.sendCommand("worldverify return");
            ticks = 0;
            stage = 6;
        } else if (stage == 6 && mc.level.dimension().equals(Level.OVERWORLD) && ticks >= 80) {
            checkInventory(mc);
            String role = System.getProperty("arienteworld.worldClientRole");
            var source = WorldLifecycleFixture.source(role);
            require(Math.abs(mc.player.getX() - source.getX() - .5) < 1 && Math.abs(mc.player.getZ() - source.getZ() - .5) < 1,
                    "The shared return must reach this client's own entrance after reconnect and death");
            require(mc.level.noCollision(mc.player), "Return landing must remain collision free on the client");
            mc.player.connection.sendCommand("worldverify done");
            ArienteWorld.setup.getLogger().info("WORLD NETWORK CLIENT PASSED: {}, shared arrival, reconnect, death/respawn, independent return, inventory/effects and model reload", role);
            stage = 7;
            ticks = 0;
        } else if (stage == 7 && ticks >= 20) {
            mc.stop();
        }
    }

    private static void checkInventory(Minecraft mc) {
        ItemStack token = mc.player.getInventory().getItem(9);
        require(token.is(Registration.KEY_CARD.get()) && token.getOrCreateTag().getString("worldcheckRole")
                .equals(System.getProperty("arienteworld.worldClientRole")), "Per-player inventory identity must remain isolated");
        require(mc.player.hasEffect(MobEffects.NIGHT_VISION), "Equipment effects must resume across travel, login and retained-inventory respawn");
    }

    private static void auditModels(Minecraft mc) {
        var missing = mc.getModelManager().getMissingModel();
        for (String namespace : List.of("ariente", "arienteworld")) {
            int[] counts = {0, 0};
            ForgeRegistries.ITEMS.getEntries().stream().filter(entry -> entry.getKey().location().getNamespace().equals(namespace)).forEach(entry -> {
                require(mc.getItemRenderer().getModel(new ItemStack(entry.getValue()), mc.level, mc.player, 0) != missing,
                        "Missing item model: " + entry.getKey().location());
                counts[0]++;
            });
            ForgeRegistries.BLOCKS.getEntries().stream().filter(entry -> entry.getKey().location().getNamespace().equals(namespace)).forEach(entry -> {
                for (var state : entry.getValue().getStateDefinition().getPossibleStates()) {
                    require(mc.getBlockRenderer().getBlockModel(state) != missing, "Missing block state model: " + state);
                    counts[1]++;
                }
            });
            ArienteWorld.setup.getLogger().info("WORLD MODEL AUDIT PASSED: {}, {} items, {} block states", namespace, counts[0], counts[1]);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
