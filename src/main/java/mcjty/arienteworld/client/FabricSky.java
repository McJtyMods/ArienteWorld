package mcjty.arienteworld.client;

import mcjty.arienteworld.dimension.DimensionRegister;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;

public final class FabricSky {

    public static void register() {
        ArienteDimensionEffects.register();
        ArienteDimensionEffects effects = new ArienteDimensionEffects();
        DimensionRenderingRegistry.registerCloudRenderer(
                DimensionRegister.DIMENSION, context -> {});
        DimensionRenderingRegistry.registerSkyRenderer(
                DimensionRegister.DIMENSION,
                context ->
                        effects.renderSky(
                                context.world(),
                                context.world().getGameTime() > Integer.MAX_VALUE
                                        ? 0
                                        : (int) context.world().getGameTime(),
                                context.tickCounter().getGameTimeDeltaPartialTick(false),
                                context.positionMatrix(),
                                context.camera(),
                                context.projectionMatrix(),
                                false,
                                () -> {}));
    }
}
