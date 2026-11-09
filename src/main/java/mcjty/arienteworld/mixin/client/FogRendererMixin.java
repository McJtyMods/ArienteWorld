package mcjty.arienteworld.mixin.client;

import mcjty.arienteworld.client.SwampFogRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
abstract class FogRendererMixin {

    @Shadow
    protected abstract FogType getFogType(Camera camera);

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void arienteworld$swampFog(Camera camera, int distance, DeltaTracker delta, float partialTick, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        SwampFogRenderer.renderFog(camera, getFogType(camera), cir.getReturnValue());
    }
}
