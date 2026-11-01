package mcjty.arienteworld.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import mcjty.ariente.client.ArienteRenderType;
import mcjty.ariente.client.ArienteSpriteUploader;
import mcjty.ariente.entities.LaserEntity;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Isolates beam geometry and blending while retaining the world's camera and model-view transforms. */
public class LaserPerspectiveChecks {

    public static void check(Minecraft mc, PoseStack cameraPose, CameraType cameraType) {
        PoseStack framePose = viewPose(cameraPose);
        checkBlendState();
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 forward = new Vec3(camera.getLookVector());
        Vec3 left = new Vec3(camera.getLeftVector());
        Vec3 up = new Vec3(camera.getUpVector());
        Vec3 origin = camera.getPosition().add(forward.scale(6)).add(left.scale(.3)).add(up.scale(.2));
        int index = 0;
        for (Vec3 direction : new Vec3[]{new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 1, 0),
                new Vec3(0, -1, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(1, .5, 1).normalize(), forward}) {
            LaserEntity laser = LaserEntity.create(mc.level, origin.x, origin.y, origin.z, direction.x, direction.y, direction.z);
            laser.xOld = origin.x - direction.x * .5;
            laser.yOld = origin.y - direction.y * .5;
            laser.zOld = origin.z - direction.z * .5;
            // Entity#getPosition interpolates these different fields. They must not determine the billboard frame.
            laser.xo = origin.x + 200;
            laser.yo = origin.y - 70;
            laser.zo = origin.z - 300;
            checkVertices(mc, framePose, laser);
            try (NativeImage single = picture(mc, framePose, laser, 1);
                 NativeImage doubled = picture(mc, framePose, laser, 2)) {
                int background = single.getPixelRGBA(0, 0);
                int bright = 0;
                int accumulated = 0;
                for (int y = 0; y < single.getHeight(); y++) {
                    for (int x = 0; x < single.getWidth(); x++) {
                        int pixel = single.getPixelRGBA(x, y);
                        int twice = doubled.getPixelRGBA(x, y);
                        for (int shift = 0; shift <= 16; shift += 8) {
                            int base = background >> shift & 255;
                            int channel = pixel >> shift & 255;
                            require(channel >= base - 1, "The additive beam must never darken its background");
                            if (channel > base + 10) {
                                bright++;
                            }
                            if ((twice >> shift & 255) > channel + 2) {
                                accumulated++;
                            }
                        }
                    }
                }
                require(bright > 10, "The beam must remain visible in " + cameraType + ": " + direction);
                require(accumulated > 0, "Overlapping laser glows must add brightness in " + cameraType);
                save(mc, single, cameraType.name().toLowerCase() + "-" + index++);
            }
        }
        ArienteWorld.setup.getLogger().info("LASER PERSPECTIVE CHECK PASSED: {} camera, interpolated dispatcher origin, lengthwise UVs, additive blending and overlapping glow", cameraType);
    }

    private static void checkBlendState() {
        ArienteRenderType.ARIENTE_ADDITIVE.setupRenderState();
        try {
            require(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) == GL11.GL_ONE
                            && GL11.glGetInteger(GL14.GL_BLEND_DST_RGB) == GL11.GL_ONE,
                    "Lasers must use ONE + ONE additive blending");
            require(!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "Laser glow must not write depth");
            require(!GL11.glIsEnabled(GL11.GL_CULL_FACE), "The quad must be visible from both camera sides");
        } finally {
            ArienteRenderType.ARIENTE_ADDITIVE.clearRenderState();
        }
    }

    private static void checkVertices(Minecraft mc, PoseStack cameraPose, LaserEntity laser) {
        BufferBuilder vertices = new BufferBuilder(256);
        vertices.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        PoseStack poses = viewPose(cameraPose);
        render(mc, poses, laser, type -> {
            require(type == ArienteRenderType.ARIENTE_ADDITIVE, "Laser vertices must use the additive atlas material");
            return vertices;
        });
        var result = vertices.end();
        try {
            require(result.drawState().vertexCount() == 4, "A laser must emit one camera-facing quad");
            var data = result.vertexBuffer().order(ByteOrder.nativeOrder());
            int stride = DefaultVertexFormat.NEW_ENTITY.getVertexSize();
            Vector3f[] corners = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                int offset = stride * i;
                corners[i] = new Vector3f(data.getFloat(offset), data.getFloat(offset + 4), data.getFloat(offset + 8));
                require(corners[i].isFinite(), "The quad must not collapse to invalid coordinates");
            }
            Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
            poses.translate(Mth.lerp(.5D, laser.xOld, laser.getX()) - camera.x,
                    Mth.lerp(.5D, laser.yOld, laser.getY()) - camera.y,
                    Mth.lerp(.5D, laser.zOld, laser.getZ()) - camera.z);
            Vector3f head = poses.last().pose().transformPosition(0, 0, 0, new Vector3f());
            Vec3 direction = laser.getBeamDirection();
            Vector3f tail = poses.last().pose().transformPosition((float) (-direction.x * 2), (float) (-direction.y * 2),
                    (float) (-direction.z * 2), new Vector3f());
            Vector3f cross = new Vector3f(tail).cross(head);
            if (cross.lengthSquared() > 1.0E-10F * tail.lengthSquared() * head.lengthSquared()) {
                require(new Vector3f(corners[0]).add(corners[3]).mul(.5F).distance(tail) < .0001F
                                && new Vector3f(corners[1]).add(corners[2]).mul(.5F).distance(head) < .0001F,
                        "Billboarding must preserve the actual head and tail, including reversed F5 views");
                var atlas = (TextureAtlas) mc.getTextureManager().getTexture(ArienteSpriteUploader.ARIENTE_ATLAS);
                var sprite = atlas.getSprite(new ResourceLocation("ariente", "negarite_laserbeam1"));
                require(Math.abs(data.getFloat(16) - sprite.getU0()) < .00001F
                                && Math.abs(data.getFloat(stride + 16) - sprite.getU1()) < .00001F
                                && Math.abs(data.getFloat(20) - data.getFloat(stride + 20)) < .00001F,
                        "The texture's lengthwise U coordinate must run from tail to head");
            }
        } finally {
            result.release();
        }
    }

    private static NativeImage picture(Minecraft mc, PoseStack cameraPose, LaserEntity laser, int copies) {
        TextureTarget target = new TextureTarget(512, 512, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float) Math.toRadians(70), 1, .05F, 100), sorting);
            target.setClearColor(.1F, .2F, .3F, 1);
            RenderSystem.depthMask(true);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            var buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
            for (int i = 0; i < copies; i++) {
                render(mc, viewPose(cameraPose), laser, buffers);
            }
            buffers.endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            target.destroyBuffers();
            RenderSystem.setProjectionMatrix(projection, sorting);
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static PoseStack viewPose(PoseStack cameraPose) {
        PoseStack poses = new PoseStack();
        poses.mulPoseMatrix(cameraPose.last().pose());
        return poses;
    }

    private static void render(Minecraft mc, PoseStack poses, LaserEntity laser, MultiBufferSource buffers) {
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        mc.getEntityRenderDispatcher().render(laser,
                Mth.lerp(.5D, laser.xOld, laser.getX()) - camera.x,
                Mth.lerp(.5D, laser.yOld, laser.getY()) - camera.y,
                Mth.lerp(.5D, laser.zOld, laser.getZ()) - camera.z,
                0, .5F, poses, buffers, LightTexture.FULL_BRIGHT);
    }

    private static void save(Minecraft mc, NativeImage image, String name) {
        try {
            Path directory = mc.gameDirectory.toPath().resolve("screenshots/laser-perspectives");
            Files.createDirectories(directory);
            image.writeToFile(directory.resolve(name + ".png"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Laser perspective check: " + message);
        }
    }
}
