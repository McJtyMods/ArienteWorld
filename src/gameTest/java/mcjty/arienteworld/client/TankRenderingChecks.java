package mcjty.arienteworld.client;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import mcjty.ariente.blocks.generators.TankRendererTools;
import mcjty.ariente.client.ArienteRenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import mcjty.lib.client.RenderBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static mcjty.ariente.blocks.generators.NegariteTankRenderer.NEGARITE_BEAM;
import static mcjty.ariente.blocks.generators.NegariteTankRenderer.NEGARITE_WINDOW;
import static mcjty.ariente.blocks.generators.PosiriteTankRenderer.POSIRITE_BEAM;
import static mcjty.ariente.blocks.generators.PosiriteTankRenderer.POSIRITE_WINDOW;

/** Client regressions for tank billboards, stack seams and shared transparency sorting. */
public class TankRenderingChecks {

    private static final int STRIDE = ArienteRenderType.TANK_CONTENTS.format().getVertexSize();

    private static void require(boolean success, String message) {
        if (!success) {
            throw new IllegalStateException("Tank rendering regression: " + message);
        }
    }

    public static void check(Minecraft mc) {
        for (var beam : new ResourceLocation[]{NEGARITE_BEAM, POSIRITE_BEAM}) {
            var window = beam.equals(NEGARITE_BEAM) ? NEGARITE_WINDOW : POSIRITE_WINDOW;
            var sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(beam);
            require(sprite.contents().name().equals(beam), "beam sprite must exist after resource reload");
            for (int angle = 0; angle < 360; angle += 30) {
                double radians = Math.toRadians(angle);
                Vec3 camera = new Vec3(Math.cos(radians) * 4, 12, Math.sin(radians) * 4);
                for (long time : new long[]{0, 2, 18}) {
                    BufferBuilder builder = new BufferBuilder(2048);
                    builder.begin(PrimitiveTopology.QUADS, ArienteRenderType.TANK_CONTENTS.format());
                    TankRendererTools.renderTank(new PoseStack(), observe(builder), beam, window, true, time, camera, 0);
                    var rendered = builder.end();
                    try {
                        require(rendered.drawState().vertexCount() == 20, "four glass faces and exactly one beam quad per tank");
                        var vertices = rendered.vertexBuffer().order(ByteOrder.nativeOrder());
                        Vec3 topLeft = position(vertices, 16);
                        Vec3 topRight = position(vertices, 17);
                        Vec3 edge = topRight.subtract(topLeft);
                        Vec3 horizontalCamera = new Vec3(camera.x, 0, camera.z).normalize();
                        require(Math.abs(edge.dot(horizontalCamera)) < .00001, "billboard must face the camera horizontally at " + angle);
                        require(edge.cross(new Vec3(0, -1, 0)).dot(horizontalCamera) > 0, "billboard winding must face the viewer");
                        require(edge.length() >= .839 && topLeft.y == 1 && position(vertices, 19).y == 0,
                                "beam must be broad and vertical regardless of camera pitch");
                        float expectedV = sprite.getV(((time / 2) % 10 + .5f) / 10 * 16);
                        for (int vertex = 16; vertex < 20; vertex++) {
                            Vec3 point = position(vertices, vertex);
                            require(point.x > .0625 && point.x < .9375 && point.z > .0625 && point.z < .9375,
                                    "beam must stay inside the glass at diagonal angles");
                            int offset = vertex * STRIDE;
                            float u = vertices.getFloat(offset + 16);
                            require(u >= sprite.getU0() - .000001 && u <= sprite.getU1() + .000001
                                    && Math.abs(vertices.getFloat(offset + 20) - expectedV) < .000001, "sprite-local animated strip UVs");
                            require(vertices.getInt(offset + 28) == LightTexture.FULL_BRIGHT, "beam must retain full brightness");
                        }
                    } finally {
                        rendered.release();
                    }
                }
            }
        }
        checkSortingAndSeams();
    }

    private static RenderBufferSource observe(BufferBuilder builder) {
        return type -> {
            require(type == ArienteRenderType.TANK_CONTENTS, "glass and beam must use the same shared translucent buffer");
            return builder;
        };
    }

    private static Vec3 position(ByteBuffer vertices, int index) {
        int offset = index * STRIDE;
        return new Vec3(vertices.getFloat(offset), vertices.getFloat(offset + 4), vertices.getFloat(offset + 8));
    }

    private static void checkSortingAndSeams() {
        for (Vec3 camera : new Vec3[]{new Vec3(4, 2, 4), new Vec3(-4, 3, -4), new Vec3(4, -2, -4)}) {
            BufferBuilder builder = new BufferBuilder(4096);
            builder.begin(PrimitiveTopology.QUADS, ArienteRenderType.TANK_CONTENTS.format());
            var buffer = observe(builder);
            PoseStack poses = new PoseStack();
            TankRendererTools.renderTank(poses, buffer, NEGARITE_BEAM, NEGARITE_WINDOW, true, 6, camera.subtract(.5, .5, .5), 0);
            poses.translate(0, 1, 0);
            TankRendererTools.renderTank(poses, buffer, NEGARITE_BEAM, NEGARITE_WINDOW, true, 6, camera.subtract(.5, 1.5, .5), 0);
            poses.translate(1, -1, 0);
            TankRendererTools.renderTank(poses, buffer, POSIRITE_BEAM, POSIRITE_WINDOW, true, 6, camera.subtract(1.5, .5, .5), 0);
            builder.setQuadSorting(VertexSorting.byDistance((float) camera.x, (float) camera.y, (float) camera.z));
            var rendered = builder.end();
            try {
                var vertices = rendered.vertexBuffer().order(ByteOrder.nativeOrder());
                require(position(vertices, 16).distanceToSqr(position(vertices, 39)) < 1.0E-10
                                && position(vertices, 17).distanceToSqr(position(vertices, 38)) < 1.0E-10,
                        "neighboring vertical segments must share their exact seam positions");
                require(vertices.getFloat(16 * STRIDE + 20) == vertices.getFloat(39 * STRIDE + 20), "UVs must remain continuous at stack boundaries");
                var indices = rendered.indexBuffer().order(ByteOrder.nativeOrder());
                int bytes = rendered.drawState().indexType().bytes;
                double previous = Double.POSITIVE_INFINITY;
                for (int index = 0; index < rendered.drawState().indexCount(); index += 6) {
                    int first = bytes == 2 ? Short.toUnsignedInt(indices.getShort(index * bytes)) : indices.getInt(index * bytes);
                    Vec3 center = position(vertices, first).add(position(vertices, first + 2)).scale(.5);
                    double distance = center.distanceToSqr(camera);
                    require(distance <= previous + .00001, "glass and beams across adjacent tanks must sort together back to front");
                    previous = distance;
                }
            } finally {
                rendered.release();
            }
        }
        BufferBuilder builder = new BufferBuilder(2048);
        builder.begin(PrimitiveTopology.QUADS, ArienteRenderType.TANK_CONTENTS.format());
        TankRendererTools.renderTank(new PoseStack(), observe(builder), NEGARITE_BEAM, NEGARITE_WINDOW, false, 0, new Vec3(0, 8, 0), 0);
        var rendered = builder.end();
        require(rendered.drawState().vertexCount() == 16, "inactive tanks must retain glass without a beam");
        rendered.release();
    }
}
