package mcjty.arienteworld.entities.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Procedural translucent energy shells, with a jagged discharge from the sphere to its victim. */
public class CoreGuardianRenderer extends EntityRenderer<CoreGuardianEntity> {

    public CoreGuardianRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.8f;
    }

    @Override
    public ResourceLocation getTextureLocation(CoreGuardianEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(CoreGuardianEntity guardian, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        poses.pushPose();
        poses.translate(0, guardian.getBbHeight() / 2, 0);
        Matrix4f matrix = poses.last().pose();
        VertexConsumer shell = buffers.getBuffer(RenderType.debugQuads());
        float time = guardian.tickCount + partialTick;
        float charge = Math.min(1, guardian.getCharge() / 30.0f);
        sphere(shell, matrix, 1.95f, time * 0.012f, 0.25f + charge * 0.5f, 0.65f, 1, 0.28f + charge * 0.12f);
        sphere(shell, matrix, 0.7f + (float) Math.sin(time * 0.1f) * 0.08f, -time * 0.025f, 0.65f, 0.9f, 1, 0.7f);
        LivingEntity target = guardian.getLightningTarget();
        if (target != null && guardian.getLightningTicks() > 0) {
            Vec3 start = guardian.getPosition(partialTick).add(0, guardian.getBbHeight() / 2, 0);
            Vec3 end = target.getPosition(partialTick).add(0, target.getBbHeight() / 2, 0).subtract(start);
            bolt(buffers.getBuffer(RenderType.lightning()), matrix, end, guardian.tickCount / 2);
        }
        poses.popPose();
        super.render(guardian, yaw, partialTick, poses, buffers, light);
    }

    private static void sphere(VertexConsumer vertices, Matrix4f matrix, float radius, float rotation,
                               float red, float green, float blue, float alpha) {
        int rings = 16;
        int segments = 32;
        for (int ring = 0; ring < rings; ring++) {
            double a = Math.PI * ring / rings;
            double b = Math.PI * (ring + 1) / rings;
            for (int segment = 0; segment < segments; segment++) {
                double c = Math.PI * 2 * segment / segments + rotation;
                double d = Math.PI * 2 * (segment + 1) / segments + rotation;
                float band = ring == 7 || ring == 8 || segment % 8 == 0 ? Math.min(0.8f, alpha + 0.2f) : alpha;
                point(vertices, matrix, radius, a, c, red, green, blue, band);
                point(vertices, matrix, radius, b, c, red, green, blue, band);
                point(vertices, matrix, radius, b, d, red, green, blue, band);
                point(vertices, matrix, radius, a, d, red, green, blue, band);
            }
        }
    }

    private static void point(VertexConsumer vertices, Matrix4f matrix, float radius, double latitude, double longitude,
                              float red, float green, float blue, float alpha) {
        vertices.addVertex(matrix, (float) (radius * Math.sin(latitude) * Math.cos(longitude)),
                        (float) (radius * Math.cos(latitude)), (float) (radius * Math.sin(latitude) * Math.sin(longitude)))
                .setColor(red, green, blue, alpha);
    }

    private static void bolt(VertexConsumer vertices, Matrix4f matrix, Vec3 end, long seed) {
        Random random = new Random(seed);
        Vec3 previous = Vec3.ZERO;
        for (int i = 1; i <= 16; i++) {
            Vec3 next = end.scale(i / 16.0);
            if (i < 16) {
                next = next.add((random.nextDouble() - 0.5) * 1.4, (random.nextDouble() - 0.5) * 1.4,
                        (random.nextDouble() - 0.5) * 1.4);
            }
            for (Vec3 width : new Vec3[]{new Vec3(0.09, 0, 0), new Vec3(0, 0.09, 0), new Vec3(0, 0, 0.09)}) {
                vertex(vertices, matrix, previous.subtract(width));
                vertex(vertices, matrix, next.subtract(width));
                vertex(vertices, matrix, next.add(width));
                vertex(vertices, matrix, previous.add(width));
            }
            previous = next;
        }
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3 point) {
        vertices.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z).setColor(0.65f, 0.85f, 1, 0.95f);
    }
}
