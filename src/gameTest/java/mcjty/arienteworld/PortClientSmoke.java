package mcjty.arienteworld;

import mcjty.ariente.Ariente;
import mcjty.ariente.blocks.storagecube.StorageCubeTile;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.ariente.entities.fluxship.FluxShipRender;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in visual smoke check. Only run in a disposable PortSmoke save. */
@EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public final class PortClientSmoke {

    private static int ticks;
    private static int stage;
    private static int waitingTicks;

    private PortClientSmoke() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ariente.portClientSmoke")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            if (++waitingTicks % 60 == 0 && mc.screen != null) {
                System.out.println("Port client screen: " + mc.screen.getClass().getSimpleName());
                for (var listener : mc.screen.children()) {
                    if (listener instanceof net.minecraft.client.gui.components.AbstractButton button) {
                        String text = button.getMessage().getString();
                        System.out.println("Port client button: " + text);
                        if (text.equals("Proceed") || text.equals("I know what I'm doing!") || text.equals("Yes")) {
                            button.onPress();
                            break;
                        }
                    }
                }
            }
            if (waitingTicks > 1200) {
                throw new AssertionError("PortSmoke world did not open");
            }
            return;
        }
        if (stage == 0) {
            stage = 1;
            if (mc.getModelManager().getModel(ModelResourceLocation.standalone(FluxShipRender.MODEL)) == mc.getModelManager().getMissingModel()) {
                throw new AssertionError("Flux Ship model did not bake");
            }
            mc.getSingleplayerServer().execute(() -> {
                ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var level = player.server.overworld();
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                for (int x = -8; x <= 8; x++) {
                    for (int z = -8; z <= 8; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, 69, z), Blocks.STONE.defaultBlockState());
                    }
                }
                BlockPos pos = new BlockPos(0, 70, 0);
                level.setBlockAndUpdate(pos, Registration.HOLO_STORAGE_1.get().defaultBlockState());
                StorageCubeTile storage = (StorageCubeTile) level.getBlockEntity(pos);
                storage.getAutomationHandler(null).insertItem(0, new ItemStack(Items.DIAMOND, 23), false);
                player.teleportTo(level, 2.5, 71, -4.5, 25, 15);
                player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Registration.POWERSUIT_HEAD.get()));
                Ariente.guiHandler.openHoloGui(level, pos, player);
                FluxShipEntity ship = Registration.ENTITY_FLUX_SHIP.get().create(level);
                ship.setPos(-2, 70, 1);
                level.addFreshEntity(ship);
            });
        }
        if (++ticks == 160) {
            Screenshot.grab(mc.gameDirectory, "port-overworld.png", mc.getMainRenderTarget(), message -> {});
            mc.getSingleplayerServer().execute(() -> {
                ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var dimension = player.server.getLevel(DimensionRegister.DIMENSION);
                if (dimension == null) {
                    throw new AssertionError("Ariente dimension is missing");
                }
                player.teleportTo(dimension, 1048, 150, 1048, 0, 25);
            });
        } else if (ticks == 320) {
            if (!mc.level.dimension().equals(DimensionRegister.DIMENSION)) {
                throw new AssertionError("Client did not enter the Ariente dimension");
            }
            Screenshot.grab(mc.gameDirectory, "port-ariente.png", mc.getMainRenderTarget(), message -> {});
            mc.stop();
        }
    }
}
