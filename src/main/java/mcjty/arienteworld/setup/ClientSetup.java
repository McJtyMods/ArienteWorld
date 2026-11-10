package mcjty.arienteworld.setup;

import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.client.FabricSky;
import mcjty.arienteworld.entities.boss.CoreGuardianRenderer;
import mcjty.arienteworld.entities.darkbat.DarkbatRender;
import mcjty.arienteworld.entities.drone.DroneRender;
import mcjty.arienteworld.entities.drone.SentinelDroneRender;
import mcjty.arienteworld.entities.soldier.SoldierRender;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.RenderType;

public class ClientSetup implements ClientModInitializer {

    public static void entityRenderers() {
        EntityRendererRegistry.register(Registration.ENTITY_CORE_GUARDIAN.get(), CoreGuardianRenderer::new);
        EntityRendererRegistry.register(Registration.ENTITY_DARKBAT.get(), DarkbatRender::new);
        EntityRendererRegistry.register(Registration.ENTITY_DRONE.get(), DroneRender.FACTORY);
        EntityRendererRegistry.register(Registration.ENTITY_SENTINEL_DRONE.get(), SentinelDroneRender.FACTORY);
        EntityRendererRegistry.register(Registration.ENTITY_SOLDIER.get(), SoldierRender.FACTORY);
        EntityRendererRegistry.register(Registration.ENTITY_MASTER_SOLDIER.get(), SoldierRender.MASTER_FACTORY);
    }

    public static void init() {
    }

    @Override
    public void onInitializeClient() {
        AssetRegistries.initialize();
        entityRenderers();
        ColorProviderRegistry.BLOCK.register((state, level, pos, index) -> 0xd9ffffff,
                Registration.NEGARITE_CRYSTAL.get(), Registration.POSIRITE_CRYSTAL.get());
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(),
                Registration.NEGARITE_CRYSTAL.get(), Registration.POSIRITE_CRYSTAL.get());
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(),
                Registration.GLOWLEAVES.get(), Registration.BLUELEAVES.get(), Registration.DARKLEAVES.get(),
                Registration.BLACKBUSH.get(), Registration.DARKGRASS.get(), Registration.TALLDARKGRASS.get(), Registration.SMALLFLOWER.get());
        FabricSky.register();

    }

}
