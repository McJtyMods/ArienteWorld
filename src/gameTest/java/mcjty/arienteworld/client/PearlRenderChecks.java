package mcjty.arienteworld.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import mcjty.ariente.entities.EntityArientePearl;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Explicit development-client regression for the vanilla thrown-item renderer bridge. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class PearlRenderChecks {

    private static boolean checked;

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (checked || !Boolean.getBoolean("ariente.pearlRenderCheck") || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        EntityArientePearl pearl = EntityArientePearl.create(mc.level, mc.player.getX(), mc.player.getY() + 2, mc.player.getZ());
        pearl.tickCount = 5;
        var poses = event.getPoseStack();
        poses.pushPose();
        try {
            poses.translate(0, 2, 0);
            var buffers = MultiBufferSource.immediate(new BufferBuilder(256));
            mc.getEntityRenderDispatcher().getRenderer(pearl).render(pearl, 0, event.getPartialTick(), poses, buffers, LightTexture.FULL_BRIGHT);
            buffers.endBatch();
        } finally {
            poses.popPose();
        }
        checked = true;
        ArienteWorld.setup.getLogger().info("PEARL RENDER CHECK PASSED: vanilla thrown-item renderer accepts Ariente pearl");
    }
}
