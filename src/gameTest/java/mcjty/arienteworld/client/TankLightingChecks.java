package mcjty.arienteworld.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mcjty.ariente.blocks.generators.TankRendererTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import mcjty.lib.client.RenderBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Path;

import static mcjty.ariente.blocks.generators.NegariteTankRenderer.NEGARITE_BEAM;
import static mcjty.ariente.blocks.generators.NegariteTankRenderer.NEGARITE_WINDOW;
import static mcjty.ariente.blocks.generators.PosiriteTankRenderer.POSIRITE_BEAM;
import static mcjty.ariente.blocks.generators.PosiriteTankRenderer.POSIRITE_WINDOW;

/** Reproduce a tank batch submitted before another renderer changes item lighting. */
public class TankLightingChecks {

    public static void check(Minecraft mc) throws IOException {
        TankRenderingChecks.check(mc);
        for (int angle : new int[]{-35, 0, 35}) {
            try (NativeImage reference = picture(mc, angle, 0)) {
                reference.writeToFile(Path.of("screenshots/tank-lighting-" + angle + "-world.png"));
                int visible = 0;
                for (int y = 0; y < reference.getHeight(); y++) {
                    for (int x = 0; x < reference.getWidth(); x++) {
                        if ((reference.getPixelRGBA(x, y) & 0xffffff) != 0) {
                            visible++;
                        }
                    }
                }
                require(visible > 1000, "Both tank columns must appear in the lighting audit");
                for (int lighting : new int[]{1, 2}) {
                    try (NativeImage changed = picture(mc, angle, lighting)) {
                        changed.writeToFile(Path.of("screenshots/tank-lighting-" + angle + "-items-" + lighting + ".png"));
                        for (int y = 0; y < reference.getHeight(); y++) {
                            for (int x = 0; x < reference.getWidth(); x++) {
                                require(reference.getPixelRGBA(x, y) == changed.getPixelRGBA(x, y),
                                        "Tank brightness changed after item lighting at angle " + angle + ", mode " + lighting);
                            }
                        }
                    }
                }
            }
        }
    }

    private static NativeImage picture(Minecraft mc, int angle, int lighting) {
        TextureTarget target = new TextureTarget(256, 256, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-1.8f, 1.8f, -1.8f, 1.8f, .1f, 20), sorting);
            target.setClearColor(0, 0, 0, 0);
            RenderSystem.depthMask(true);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            PoseStack poses = new PoseStack();
            poses.translate(0, 0, -5);
            poses.mulPose(Axis.XP.rotationDegrees(15));
            poses.mulPose(Axis.YP.rotationDegrees(angle));
            poses.translate(-1, -1, -.5);
            var buffers = RenderBufferSource.immediate(new BufferBuilder(4096));
            double yaw = Math.toRadians(-angle);
            Vec3 camera = new Vec3(Math.sin(yaw) * 5 + 1, 2.5, Math.cos(yaw) * 5 + .5);
            for (int column = 0; column < 2; column++) {
                for (int section = 0; section < 2; section++) {
                    poses.pushPose();
                    poses.translate(column, section, 0);
                    TankRendererTools.renderTank(poses, buffers, column == 0 ? NEGARITE_BEAM : POSIRITE_BEAM,
                            column == 0 ? NEGARITE_WINDOW : POSIRITE_WINDOW, true, 6,
                            camera.subtract(column + .5, section + .5, .5), LightTexture.FULL_BRIGHT);
                    poses.popPose();
                }
            }
            // Change lighting after submission, exactly as another entity renderer can do before a shared batch flush.
            if (lighting == 1) {
                Lighting.setupForFlatItems();
            } else if (lighting == 2) {
                Lighting.setupFor3DItems();
            } else {
                Lighting.setupLevel(new Matrix4f());
            }
            buffers.endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            Lighting.setupLevel(new Matrix4f());
            target.destroyBuffers();
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            mc.gameRenderer.mainRenderTarget().bindWrite(true);
        }
    }

    private static void require(boolean success, String message) {
        if (!success) {
            throw new IllegalStateException("Tank lighting regression: " + message);
        }
    }
}
