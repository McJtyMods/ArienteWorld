package mcjty.arienteworld.setup;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup {

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
