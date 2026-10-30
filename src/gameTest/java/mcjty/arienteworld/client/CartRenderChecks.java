package mcjty.arienteworld.client;

import mcjty.ariente.entities.levitator.FluxLevitatorEntity;
import mcjty.ariente.gui.ModGuis;
import mcjty.arienteworld.ArienteWorld;
import mcjty.hologui.gui.HoloGuiEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static mcjty.hologui.setup.Registration.HOLOGUI_ENTITY_TYPE;
import static mcjty.ariente.setup.Registration.FLUX_BEAM;

/** Explicit client check that mounted cart controls emit geometry through their registered renderer. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class CartRenderChecks {

    private static boolean checked;

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (checked || !Boolean.getBoolean("ariente.cartRenderCheck") || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // Also leave a real server-spawned cart in the copied visual fixture,
        // so its synchronized passenger panels can be inspected in the world.
        if (mc.getSingleplayerServer() != null) {
            var dimension = mc.level.dimension();
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().getLevel(dimension);
                for (int x = 90018; x <= 90025; x++) {
                    level.setBlock(new BlockPos(x, 241, 90024), FLUX_BEAM.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 2);
                }
                level.addFreshEntity(FluxLevitatorEntity.create(level, 90021.5, 241.0625, 90024.5));
            });
        }
        FluxLevitatorEntity cart = FluxLevitatorEntity.create(mc.level, 0, 0, 0);
        HoloGuiEntity panel = new HoloGuiEntity(HOLOGUI_ENTITY_TYPE.get(), mc.level);
        panel.setGuiId(ModGuis.GUI_LEVITATOR);
        panel.setScale(.75f);
        panel.startRiding(cart, true);
        cart.setHoloFrontUUID(panel.getUUID());
        HologramRenderingChecks.checkGui(mc, panel, false);
        panel.stopRiding();
        checked = true;
        ArienteWorld.setup.getLogger().info("CART RENDER CHECK PASSED: mounted controls render their background and buttons");
    }
}
