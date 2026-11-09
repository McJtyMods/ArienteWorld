package mcjty.arienteworld.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mcjty.ariente.blocks.utility.autofield.AutoFieldRenderer;
import mcjty.ariente.client.ArienteRenderType;
import mcjty.ariente.client.ArienteSpriteUploader;
import mcjty.ariente.client.BeamRenderTools;
import mcjty.ariente.client.WorldHolograms;
import mcjty.ariente.display.DisplayBaseTile;
import mcjty.ariente.display.DisplayGeometry;
import mcjty.ariente.display.client.DisplayCanvas;
import mcjty.ariente.multipart.MultipartTE;
import mcjty.ariente.setup.Registration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import mcjty.lib.client.RenderBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

/** Pixel regressions for the tutorial's camera-dependent display and marker artifacts. */
public class DisplayTransparencyChecks {

    private static final int WIDTH = 512;
    private static final int HEIGHT = 384;
    private static final Matrix4f PROJECTION = new Matrix4f().setOrtho(-2.5f, 2.5f, -2, 2, .1f, 100);

    public static void check(Minecraft mc) throws IOException {
        DisplayBaseTile base = (DisplayBaseTile) mc.level.getBlockEntity(new BlockPos(119, 65, 126));
        List<DisplayBaseTile> row = DisplayGeometry.row(base);
        require(row.stream().flatMap(tile -> tile.modules().stream())
                        .filter(module -> module.stack().is(Registration.DISPLAY_STORAGE.get()))
                        .anyMatch(module -> !module.snapshot().getList("Items", Tag.TAG_COMPOUND).isEmpty()),
                "The angle checks must exercise a populated live inventory module");
        for (int angle : new int[]{-35, 0, 35}) {
            PoseStack transform = new PoseStack();
            transform.translate(0, 0, -5);
            transform.mulPose(Axis.YP.rotationDegrees(angle));
            transform.translate(-2, -1.5, -.5);
            try (NativeImage picture = picture(mc, (poses, buffers) -> {
                poses.last().pose().set(transform.last().pose());
                poses.last().normal().set(transform.last().normal());
                // Reverse block-entity visitation to exercise ownership across base boundaries.
                for (int column = row.size() - 1; column >= 0; column--) {
                    poses.pushPose();
                    poses.translate(column, 0, 0);
                    var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(row.get(column));
                    renderer.render(row.get(column), 0, poses, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    poses.popPose();
                }
                WorldHolograms.render();
            })) {
                picture.writeToFile(Path.of("screenshots/display-module-angle-" + angle + ".png"));
                int reference = sample(picture, transform, 24 / 64f, 3 - 128 / 64f, .50125f);
                require((reference >> 16 & 255) > (reference & 255) + 8, "The sample must hit the storage module's blue background");
                for (int x : new int[]{74, 104}) {
                    require(close(reference, sample(picture, transform, x / 64f, 3 - 128 / 64f, .50125f)),
                            "A module spanning display bases must have an even background at angle " + angle);
                }
            }
        }

        MultipartTE marker = (MultipartTE) mc.level.getBlockEntity(new BlockPos(114, 65, 116));
        int reference = 0;
        for (int angle : new int[]{30, 50, 70}) {
            PoseStack transform = new PoseStack();
            transform.translate(0, 0, -5);
            transform.mulPose(Axis.XP.rotationDegrees(angle));
            transform.translate(-.5, -1 / 16f, -.5);
            try (NativeImage picture = picture(mc, (poses, buffers) -> {
                poses.last().pose().set(transform.last().pose());
                poses.last().normal().set(transform.last().normal());
                mc.getBlockEntityRenderDispatcher().getRenderer(marker).render(marker, 0, poses, buffers,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                buffers.endBatch();
            })) {
                picture.writeToFile(Path.of("screenshots/field-marker-angle-" + angle + ".png"));
                int pixel = sample(picture, transform, 7.5f / 16, 1 / 16f, 9.5f / 16);
                require((pixel & 0xffffff) != 0, "Marker samples must hit its top texture");
                require(angle == 30 || close(reference, pixel), "Field-marker brightness must not change with camera pitch " + angle);
                reference = pixel;
            }
        }

        try (NativeImage picture = picture(mc, (poses, buffers) -> {
            poses.translate(0, 0, -4);
            var sprite = ArienteSpriteUploader.INSTANCE.getSprite(AutoFieldRenderer.BEAMS[0]);
            BeamRenderTools.drawBeam(poses, buffers.getBuffer(ArienteRenderType.FIELD_OUTLINE), sprite,
                    new Vec3(0, -1, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 4), .5f);
            buffers.endBatch();
            // Reproduce the old ordering: a display behind an outline is submitted later.
            poses.translate(0, 0, -1);
            new DisplayCanvas(poses, buffers).fill(-2, -2, 4, 4, 0xff00ff00);
            buffers.endBatch();
        })) {
            picture.writeToFile(Path.of("screenshots/field-outline-no-depth-mask.png"));
            require((picture.getPixelRGBA(WIDTH / 2, HEIGHT / 2) & 0xffffff) == 0x00ff00,
                    "The field outline must not punch a depth hole in a later display");
        }
    }

    private static int sample(NativeImage image, PoseStack poses, float x, float y, float z) {
        Vector4f point = new Vector4f(x, y, z, 1).mul(poses.last().pose()).mul(PROJECTION);
        int px = Math.round((point.x / point.w + 1) * WIDTH / 2f);
        int py = Math.round((1 - point.y / point.w) * HEIGHT / 2f);
        return image.getPixelRGBA(px, py);
    }

    private static boolean close(int first, int second) {
        for (int shift : new int[]{0, 8, 16}) {
            if (Math.abs((first >> shift & 255) - (second >> shift & 255)) > 3) {
                return false;
            }
        }
        return true;
    }

    private static NativeImage picture(Minecraft mc, BiConsumer<PoseStack, RenderBufferSource.Immediate> draw) {
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(PROJECTION, sorting);
            target.setClearColor(0, 0, 0, 0);
            RenderSystem.depthMask(true);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            draw.accept(new PoseStack(), RenderBufferSource.immediate(new BufferBuilder(4096)));
            return Screenshot.takeScreenshot(target);
        } finally {
            target.destroyBuffers();
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            mc.gameRenderer.mainRenderTarget().bindWrite(true);
        }
    }

    private static void require(boolean success, String message) {
        if (!success) {
            throw new IllegalStateException("Hologram rendering regression: " + message);
        }
    }
}
