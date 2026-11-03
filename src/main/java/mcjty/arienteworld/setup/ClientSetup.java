package mcjty.arienteworld.setup;

import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.entities.darkbat.DarkbatRender;
import mcjty.arienteworld.entities.drone.DroneRender;
import mcjty.arienteworld.entities.drone.SentinelDroneRender;
import mcjty.arienteworld.entities.soldier.SoldierRender;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void entityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Registration.ENTITY_DARKBAT.get(), DarkbatRender::new);
        event.registerEntityRenderer(Registration.ENTITY_DRONE.get(), DroneRender.FACTORY);
        event.registerEntityRenderer(Registration.ENTITY_SENTINEL_DRONE.get(), SentinelDroneRender.FACTORY);
        event.registerEntityRenderer(Registration.ENTITY_SOLDIER.get(), SoldierRender.FACTORY);
        event.registerEntityRenderer(Registration.ENTITY_MASTER_SOLDIER.get(), SoldierRender.MASTER_FACTORY);
    }

    public static void init(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(Registration.GLOWLEAVES.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(Registration.BLUELEAVES.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(Registration.DARKLEAVES.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(Registration.BLACKBUSH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(Registration.DARKGRASS.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(Registration.SMALLFLOWER.get(), RenderType.cutout());
        });
    }
}
