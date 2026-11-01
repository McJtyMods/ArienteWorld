package mcjty.arienteworld.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import mcjty.ariente.client.ArienteRenderType;
import mcjty.ariente.client.BeamRenderTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static mcjty.ariente.blocks.utility.WarperRenderer.HALO;

/** Pixel checks for additive glow, translucent draw order and the texture's black borders. */
public class BeamRenderingChecks {

    public static void check(Minecraft mc) throws IOException {
        try (NativeImage beam = picture(mc, 0, 1, 0);
             NativeImage behind = picture(mc, -5, 1, 0);
             NativeImage front = picture(mc, -2, 1, 0);
             NativeImage litBackground = picture(mc, 0, 1, .2f);
             NativeImage overlapping = picture(mc, 0, 2, 0)) {
            Files.createDirectories(Path.of("screenshots"));
            beam.writeToFile(Path.of("screenshots/beam-order-base.png"));
            behind.writeToFile(Path.of("screenshots/beam-order-behind.png"));
            front.writeToFile(Path.of("screenshots/beam-order-front.png"));
            litBackground.writeToFile(Path.of("screenshots/beam-glow-background.png"));
            overlapping.writeToFile(Path.of("screenshots/beam-glow-overlap.png"));
            int center = beam.getPixelRGBA(128, 128);
            require((center >> 16 & 255) > 150, "The beam's core must remain visible");
            require(center == behind.getPixelRGBA(128, 128), "A translucent surface behind the beam must fail its depth test");
            require((front.getPixelRGBA(128, 128) >> 8 & 255) > (center >> 8 & 255),
                    "A translucent surface in front must blend over the beam");
            boolean transparentBorder = false;
            for (int x = 77; x < 100; x++) {
                if ((beam.getPixelRGBA(x, 128) & 0xffffff) == 0 && (behind.getPixelRGBA(x, 128) >> 8 & 255) > 100) {
                    transparentBorder = true;
                }
            }
            require(transparentBorder, "Black glow texels must not write invisible depth over surfaces behind them");
            int lit = litBackground.getPixelRGBA(128, 128);
            require((lit & 255) >= (center & 255) + 40
                            && (lit >> 8 & 255) >= (center >> 8 & 255) + 40,
                    "A glowing strand must add light while retaining the background beneath it");
            int white = overlapping.getPixelRGBA(128, 128);
            require((white & 255) > 245 && (white >> 8 & 255) > 245 && (white >> 16 & 255) > 245,
                    "Overlapping strands must accumulate into the original bright white core");
        }
    }

    private static NativeImage picture(Minecraft mc, int planeZ, int strands, float background) {
        TextureTarget target = new TextureTarget(256, 256, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-1, 1, -1, 1, .1f, 10), sorting);
            target.setClearColor(background, background, background, 0);
            RenderSystem.depthMask(true);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            PoseStack pose = new PoseStack();
            pose.translate(0, 0, -3);
            var buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
            var sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(HALO);
            var vertices = buffers.getBuffer(ArienteRenderType.BEAM);
            for (int strand = 0; strand < strands; strand++) {
                BeamRenderTools.drawBeam(pose, vertices, sprite,
                        new Vec3(0, -1, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 3), .4f);
            }
            buffers.endBatch();
            if (planeZ != 0) {
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                var tesselator = Tesselator.getInstance();
                var plane = tesselator.getBuilder();
                plane.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                plane.vertex(-1, -1, planeZ).color(0f, 1f, 0f, .5f).endVertex();
                plane.vertex(1, -1, planeZ).color(0f, 1f, 0f, .5f).endVertex();
                plane.vertex(1, 1, planeZ).color(0f, 1f, 0f, .5f).endVertex();
                plane.vertex(-1, 1, planeZ).color(0f, 1f, 0f, .5f).endVertex();
                tesselator.end();
            }
            return Screenshot.takeScreenshot(target);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            target.destroyBuffers();
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void require(boolean success, String message) {
        if (!success) {
            throw new IllegalStateException("Beam transparency regression: " + message);
        }
    }
}
