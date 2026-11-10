package mcjty.arienteworld.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import mcjty.arienteworld.ArienteWorld;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class ArienteDimensionEffects extends DimensionSpecialEffects {

    private static final ResourceLocation STARS = texture("stars");
    private static final ResourceLocation STAR_CAPS = texture("starsa");
    private static final ResourceLocation SUN = texture("ariente_sun");
    private static final ResourceLocation MOON = texture("ariente_moon_phases");
    private static final float[][] STAR_UVS = {
            {0, 1, 0, 0, 1, 0, 1, 1},
            {0, 0, 0, 1, 1, 1, 1, 0},
            {1, 1, 1, 0, 0, 0, 0, 1},
            {0, 1, 0, 0, 1, 0, 1, 1},
            {0, 1, 1, 1, 1, 0, 0, 0},
            {1, 0, 0, 0, 0, 1, 1, 1}
    };

    public ArienteDimensionEffects() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    public static void register() {
        DimensionRenderingRegistry.registerDimensionEffects(ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "ariente"), new ArienteDimensionEffects());
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "textures/sky/" + name + ".png");
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return color.scale(brightness * 0.85 + 0.15);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poses,
                                double camX, double camY, double camZ, Matrix4f modelView, Matrix4f projection) {
        // The original dimension suppresses vanilla clouds in favor of its skybox.
        return true;
    }

    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera,
                             Matrix4f projection, boolean foggy, Runnable setupFog) {
        PoseStack poses = new PoseStack();
        poses.mulPose(modelView);
        if (foggy || camera.getFluidInCamera() != FogType.NONE) {
            return true;
        }
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        FogRenderer.setupNoFog();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            // Preserve the original provider's purple tint, including at night.
            Vec3 sky = level.getSkyColor(camera.getPosition(), partialTick);
            float red = 1;
            float green = (float) sky.y;
            float blue = 1;
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            Matrix4f matrix = poses.last().pose();
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
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
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
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
            BufferUploader.drawWithShader(buffer.buildOrThrow());

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees(-90));
            poses.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partialTick) * 360));
            renderStars(poses, level.getStarBrightness(partialTick));
            // The original RGB celestial textures use black for empty space.
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            RenderSystem.setShaderTexture(0, SUN);
            quad(poses.last().pose(), 30, 100, new float[]{0, 0, 1, 0, 1, 1, 0, 1}, 1);
            int phase = level.getMoonPhase();
            float u = (phase % 4) / 4f;
            float v = (phase / 4) / 2f;
            RenderSystem.setShaderTexture(0, MOON);
            quad(poses.last().pose(), 20, -100, new float[]{u, v + .5f, u + .25f, v + .5f, u + .25f, v, u, v}, 1);
            poses.popPose();
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            setupFog.run();
        }
        return true;
    }

    private static void renderStars(PoseStack poses, float alpha) {
        if (alpha <= 0) {
            return;
        }
        for (int face = 0; face < 6; face++) {
            poses.pushPose();
            switch (face) {
                case 1 -> poses.mulPose(Axis.XP.rotationDegrees(90));
                case 2 -> poses.mulPose(Axis.XP.rotationDegrees(-90));
                case 3 -> poses.mulPose(Axis.XP.rotationDegrees(180));
                case 4 -> poses.mulPose(Axis.ZP.rotationDegrees(90));
                case 5 -> poses.mulPose(Axis.ZP.rotationDegrees(-90));
            }
            RenderSystem.setShaderTexture(0, face == 0 || face == 3 ? STAR_CAPS : STARS);
            quad(poses.last().pose(), 100, -100, STAR_UVS[face], alpha);
            poses.popPose();
        }
    }

    private static void quad(Matrix4f matrix, float size, float y, float[] uv, float alpha) {
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, -size, y, -size).setUv(uv[0], uv[1]);
        buffer.addVertex(matrix, -size, y, size).setUv(uv[2], uv[3]);
        buffer.addVertex(matrix, size, y, size).setUv(uv[4], uv[5]);
        buffer.addVertex(matrix, size, y, -size).setUv(uv[6], uv[7]);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
