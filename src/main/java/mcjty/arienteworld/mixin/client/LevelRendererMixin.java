package mcjty.arienteworld.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import mcjty.arienteworld.client.ArienteDimensionEffects;
import mcjty.arienteworld.client.FabricSky;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {

    @Inject(method = "lambda$addSkyPass$0", at = @At("HEAD"), cancellable = true)
    private static void arienteworld$sky(GpuBufferSlice fog, SkyRenderState sky, SkyRenderer renderer, CallbackInfo ci) {
        var level = ((FabricRenderState) sky).getData(FabricSky.LEVEL);
        if (level != null) {
            RenderSystem.setShaderFog(fog);
            new ArienteDimensionEffects().renderSky(level, sky, RenderSystem.getModelViewMatrix(), () -> {});
            ci.cancel();
        }
    }
}
