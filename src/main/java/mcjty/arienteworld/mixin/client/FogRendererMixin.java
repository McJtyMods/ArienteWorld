package mcjty.arienteworld.mixin.client;

import mcjty.arienteworld.client.SwampFogRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void arienteworld$swampFog(
            Camera camera,
            FogRenderer.FogMode mode,
            float distance,
            boolean thickFog,
            float partialTick,
            CallbackInfo ci) {
        SwampFogRenderer.renderFog(camera, mode);
    }
}
