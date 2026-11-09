package mcjty.arienteworld.client;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import java.util.Optional;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import mcjty.arienteworld.ArienteWorld;
import mcjty.lib.client.RenderBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

@EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class ArienteDimensionEffects implements CustomSkyboxRenderer {

    private static final List<RenderPipeline> PIPELINES = new ArrayList<>();
    private static final RenderType GRADIENT = color("sky_gradient", PrimitiveTopology.QUADS);
    private static final RenderType TRIANGLES = color("sky_caps", PrimitiveTopology.TRIANGLES);
    private static final RenderType STARS_TYPE = textureType("stars", false);
    private static final RenderType STAR_CAPS_TYPE = textureType("starsa", false);
    private static final RenderType SUN_TYPE = textureType("ariente_sun", true);
    private static final RenderType MOON_TYPE = textureType("ariente_moon_phases", true);

    private static final float[][] STAR_UVS = {
            {0, 1, 0, 0, 1, 0, 1, 1},
            {0, 0, 0, 1, 1, 1, 1, 0},
            {1, 1, 1, 0, 0, 0, 0, 1},
            {0, 1, 0, 0, 1, 0, 1, 1},
            {0, 1, 1, 1, 1, 0, 0, 0},
            {1, 0, 0, 0, 0, 1, 1, 1}
    };

    @SubscribeEvent
    public static void register(RegisterCustomEnvironmentEffectRendererEvent event) {
        event.registerSkyboxRenderer(Identifier.parse("arienteworld:ariente"), new ArienteDimensionEffects());
    }

    @SubscribeEvent
    public static void pipelines(RegisterRenderPipelinesEvent event) {
        PIPELINES.forEach(event::registerPipeline);
    }

    private static RenderType color(String name, PrimitiveTopology mode) {
        var pipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(Identifier.parse("arienteworld:pipeline/" + name))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR).withPrimitiveTopology(mode).withCull(false).withDepthStencilState(Optional.empty()).build();
        PIPELINES.add(pipeline);
        return RenderType.create(name, RenderSetup.builder(pipeline).createRenderSetup());
    }

    private static RenderType textureType(String name, boolean additive) {
        var pipeline = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Identifier.parse("arienteworld:pipeline/" + name))
                .withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color")
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(additive ? BlendFunction.ADDITIVE : BlendFunction.TRANSLUCENT))
                .withCull(false).withDepthStencilState(Optional.empty()).build();
        PIPELINES.add(pipeline);
        return RenderType.create(name, RenderSetup.builder(pipeline)
                .withTexture("Sampler0", Identifier.parse("arienteworld:textures/sky/" + name + ".png")).createRenderSetup());
    }

    @Override
    public boolean renderSky(LevelRenderState level, SkyRenderState sky, Matrix4fc modelView, GpuBufferSlice skyFog) {
        if (level.cameraRenderState.fogType != FogType.NONE) {
            return true;
        }
        RenderSystem.setShaderFog(skyFog);
        PoseStack poses = new PoseStack();
        poses.mulPose(modelView);
        float red = 1;
        float green = sky.skyColor.y();
        float blue = 1;
        try (var buffers = RenderBufferSource.immediate()) {
            Matrix4f matrix = poses.last().pose();
            VertexConsumer buffer = buffers.getBuffer(GRADIENT);
            // A horizon gradient keeps the original vivid upper sky without a blue horizon.
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                double b = (i + 1) * Math.PI / 8;
                float x1 = (float) Math.cos(a) * 200;
                float z1 = (float) Math.sin(a) * 200;
                float x2 = (float) Math.cos(b) * 200;
                float z2 = (float) Math.sin(b) * 200;
                buffer.addVertex(matrix, x1, -100, z1).setColor(red * .16f, green * .16f, blue * .2f, 1);
                buffer.addVertex(matrix, x2, -100, z2).setColor(red * .16f, green * .16f, blue * .2f, 1);
                buffer.addVertex(matrix, x2, 100, z2).setColor(red, green, blue, 1);
                buffer.addVertex(matrix, x1, 100, z1).setColor(red, green, blue, 1);
            }
            buffers.endBatch();
            buffer = buffers.getBuffer(TRIANGLES);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                double b = (i + 1) * Math.PI / 8;
                float x1 = (float) Math.cos(a) * 200;
                float z1 = (float) Math.sin(a) * 200;
                float x2 = (float) Math.cos(b) * 200;
                float z2 = (float) Math.sin(b) * 200;
                buffer.addVertex(matrix, 0, 100, 0).setColor(red, green, blue, 1);
                buffer.addVertex(matrix, x1, 100, z1).setColor(red, green, blue, 1);
                buffer.addVertex(matrix, x2, 100, z2).setColor(red, green, blue, 1);
                buffer.addVertex(matrix, 0, -100, 0).setColor(red * .16f, green * .16f, blue * .2f, 1);
                buffer.addVertex(matrix, x1, -100, z1).setColor(red * .16f, green * .16f, blue * .2f, 1);
                buffer.addVertex(matrix, x2, -100, z2).setColor(red * .16f, green * .16f, blue * .2f, 1);
            }
            buffers.endBatch();

            poses.pushPose();
            poses.rotate(Axis.YP.rotationDegrees(-90));
            poses.rotate(Axis.XP.rotationDegrees(sky.sunAngle));
            renderStars(poses, buffers, sky.starBrightness);
            // The original RGB celestial textures use black for empty space.
            quad(buffers, SUN_TYPE, poses.last().pose(), 30, 100, new float[]{0, 0, 1, 0, 1, 1, 0, 1}, 1);
            int phase = sky.moonPhase.index();
            float u = (phase % 4) / 4f;
            float v = (phase / 4) / 2f;
            quad(buffers, MOON_TYPE, poses.last().pose(), 20, -100, new float[]{u, v + .5f, u + .25f, v + .5f, u + .25f, v, u, v}, 1);
            poses.popPose();
            buffers.endBatch();
        }
        return true;
    }

    private static void renderStars(PoseStack poses, RenderBufferSource.Immediate buffers, float alpha) {
        if (alpha <= 0) {
            return;
        }
        for (int face = 0; face < 6; face++) {
            poses.pushPose();
            switch (face) {
                case 1 -> poses.rotate(Axis.XP.rotationDegrees(90));
                case 2 -> poses.rotate(Axis.XP.rotationDegrees(-90));
                case 3 -> poses.rotate(Axis.XP.rotationDegrees(180));
                case 4 -> poses.rotate(Axis.ZP.rotationDegrees(90));
                case 5 -> poses.rotate(Axis.ZP.rotationDegrees(-90));
            }
            quad(buffers, face == 0 || face == 3 ? STAR_CAPS_TYPE : STARS_TYPE, poses.last().pose(), 100, -100, STAR_UVS[face], alpha);
            poses.popPose();
        }
    }

    private static void quad(RenderBufferSource.Immediate buffers, RenderType type, Matrix4f matrix, float size, float y, float[] uv, float alpha) {
        VertexConsumer vertices = buffers.getBuffer(type);
        vertices.addVertex(matrix, -size, y, -size).setUv(uv[0], uv[1]).setColor(1, 1, 1, alpha);
        vertices.addVertex(matrix, -size, y, size).setUv(uv[2], uv[3]).setColor(1, 1, 1, alpha);
        vertices.addVertex(matrix, size, y, size).setUv(uv[4], uv[5]).setColor(1, 1, 1, alpha);
        vertices.addVertex(matrix, size, y, -size).setUv(uv[6], uv[7]).setColor(1, 1, 1, alpha);
        buffers.endBatch();
    }
}
