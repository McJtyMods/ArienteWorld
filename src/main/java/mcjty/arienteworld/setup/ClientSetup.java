package mcjty.arienteworld.setup;

import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.entities.boss.CoreGuardianRenderer;
import mcjty.arienteworld.entities.darkbat.DarkbatRender;
import mcjty.arienteworld.entities.drone.DroneRender;
import mcjty.arienteworld.entities.drone.SentinelDroneRender;
import mcjty.arienteworld.entities.soldier.SoldierRender;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void entityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Registration.ENTITY_CORE_GUARDIAN.get(), CoreGuardianRenderer::new);
        event.registerEntityRenderer(Registration.ENTITY_DARKBAT.get(), DarkbatRender::new);
        event.registerEntityRenderer(Registration.ENTITY_DRONE.get(), DroneRender.FACTORY);
        event.registerEntityRenderer(Registration.ENTITY_SENTINEL_DRONE.get(), SentinelDroneRender.FACTORY);
        event.registerEntityRenderer(Registration.ENTITY_SOLDIER.get(), SoldierRender.FACTORY);
        event.registerEntityRenderer(Registration.ENTITY_MASTER_SOLDIER.get(), SoldierRender.MASTER_FACTORY);
    }

    public static void init(FMLClientSetupEvent event) {
    }
}
