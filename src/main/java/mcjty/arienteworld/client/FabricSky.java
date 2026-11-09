package mcjty.arienteworld.client;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import mcjty.arienteworld.dimension.DimensionRegister;

public final class FabricSky {

    public static final RenderStateDataKey<LevelRenderState> LEVEL = RenderStateDataKey.create(() -> "Ariente sky");

    private FabricSky() {
    }

    public static void register() {
        LevelRenderEvents.END_EXTRACTION.register(context -> {
            if (context.level().dimension().equals(DimensionRegister.DIMENSION)) {
                ((FabricRenderState) context.levelState().skyRenderState).setData(LEVEL, context.levelState());
            }
        });
    }
}
