package mcjty.arienteworld.dimension;

import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.Random;

public class ArienteSkyRenderer extends IRenderHandler {

    private static final ResourceLocation MOON_PHASES_TEXTURES = new ResourceLocation(ArienteWorld.MODID, "textures/sky/ariente_moon_phases.png");
    private static final ResourceLocation SUN_TEXTURES = new ResourceLocation(ArienteWorld.MODID, "textures/sky/ariente_sun.png");
    private static final ResourceLocation STARS = new ResourceLocation(ArienteWorld.MODID, "textures/sky/stars.png");
    private static final ResourceLocation STARSA = new ResourceLocation(ArienteWorld.MODID, "textures/sky/starsa.png");

    private boolean vboEnabled;
    private VertexBuffer starVBO;
    private VertexBuffer skyVBO;
    private VertexBuffer sky2VBO;
    private int starGLCallList = -1;
    private int glSkyList = -1;
    private int glSkyList2 = -1;
    private final VertexFormat vertexBufferFormat;
    private final TextureManager renderEngine;
    private final RenderManager renderManager;

    public ArienteSkyRenderer() {
        Minecraft mc = Minecraft.getMinecraft();
        this.renderManager = mc.getRenderManager();
        this.renderEngine = mc.getTextureManager();
        this.vboEnabled = OpenGlHelper.useVbo();

        this.vertexBufferFormat = new VertexFormat();
        this.vertexBufferFormat.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.POSITION, 3));
        generateStars();
        generateSky();
        generateSky2();
    }

    @Override
    public void render(float partialTicks, WorldClient world, Minecraft mc) {
        renderSky(partialTicks, world, mc);
    }

    private void generateStars() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();

        if (this.starVBO != null) {
            this.starVBO.deleteGlBuffers();
        }

        if (this.starGLCallList >= 0) {
            GLAllocation.deleteDisplayLists(this.starGLCallList);
            this.starGLCallList = -1;
        }

        if (this.vboEnabled) {
            this.starVBO = new VertexBuffer(this.vertexBufferFormat);
            this.renderStars(bufferbuilder);
            bufferbuilder.finishDrawing();
            bufferbuilder.reset();
            this.starVBO.bufferData(bufferbuilder.getByteBuffer());
        } else {
            this.starGLCallList = GLAllocation.generateDisplayLists(1);
            GlStateManager.pushMatrix();
            GlStateManager.glNewList(this.starGLCallList, 4864);
            this.renderStars(bufferbuilder);
            tessellator.draw();
            GlStateManager.glEndList();
            GlStateManager.popMatrix();
        }
    }

    private void renderStars(BufferBuilder bufferBuilderIn) {
        Random random = new Random(10842L);
        bufferBuilderIn.begin(7, DefaultVertexFormats.POSITION);

        for (int i = 0; i < 1500; ++i) {
            double d0 = (random.nextFloat() * 2.0F - 1.0F);
            double d1 = (random.nextFloat() * 2.0F - 1.0F);
            double d2 = (random.nextFloat() * 2.0F - 1.0F);
            double d3 = (0.15F + random.nextFloat() * 0.1F);
            double d4 = d0 * d0 + d1 * d1 + d2 * d2;

            if (d4 < 1.0D && d4 > 0.01D) {
                d4 = 1.0D / Math.sqrt(d4);
                d0 = d0 * d4;
                d1 = d1 * d4;
                d2 = d2 * d4;
                double d5 = d0 * 100.0D;
                double d6 = d1 * 100.0D;
                double d7 = d2 * 100.0D;
                double d8 = Math.atan2(d0, d2);
                double d9 = Math.sin(d8);
                double d10 = Math.cos(d8);
                double d11 = Math.atan2(Math.sqrt(d0 * d0 + d2 * d2), d1);
                double d12 = Math.sin(d11);
                double d13 = Math.cos(d11);
                double d14 = random.nextDouble() * Math.PI * 2.0D;
                double d15 = Math.sin(d14);
                double d16 = Math.cos(d14);

                for (int j = 0; j < 4; ++j) {
                    double d17 = 0.0D;
                    double d18 = ((j & 2) - 1) * d3;
                    double d19 = ((j + 1 & 2) - 1) * d3;
                    double d20 = 0.0D;
                    double d21 = d18 * d16 - d19 * d15;
                    double d22 = d19 * d16 + d18 * d15;
                    double d23 = d21 * d12 + 0.0D * d13;
                    double d24 = 0.0D * d12 - d21 * d13;
                    double d25 = d24 * d9 - d22 * d10;
                    double d26 = d22 * d9 + d24 * d10;
                    bufferBuilderIn.pos(d5 + d25, d6 + d23, d7 + d26).endVertex();
                }
            }
        }
    }


    private void generateSky() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();

        if (this.skyVBO != null) {
            this.skyVBO.deleteGlBuffers();
        }

        if (this.glSkyList >= 0) {
            GLAllocation.deleteDisplayLists(this.glSkyList);
            this.glSkyList = -1;
        }

        if (this.vboEnabled) {
            this.skyVBO = new VertexBuffer(this.vertexBufferFormat);
            this.renderSky(bufferbuilder, 16.0F, false);
            bufferbuilder.finishDrawing();
            bufferbuilder.reset();
            this.skyVBO.bufferData(bufferbuilder.getByteBuffer());
        } else {
            this.glSkyList = GLAllocation.generateDisplayLists(1);
            GlStateManager.glNewList(this.glSkyList, 4864);
            this.renderSky(bufferbuilder, 16.0F, false);
            tessellator.draw();
            GlStateManager.glEndList();
        }
    }

    private void generateSky2() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();

        if (this.sky2VBO != null) {
            this.sky2VBO.deleteGlBuffers();
        }

        if (this.glSkyList2 >= 0) {
            GLAllocation.deleteDisplayLists(this.glSkyList2);
            this.glSkyList2 = -1;
        }

        if (this.vboEnabled) {
            this.sky2VBO = new VertexBuffer(this.vertexBufferFormat);
            this.renderSky(bufferbuilder, -16.0F, true);
            bufferbuilder.finishDrawing();
            bufferbuilder.reset();
            this.sky2VBO.bufferData(bufferbuilder.getByteBuffer());
        } else {
            this.glSkyList2 = GLAllocation.generateDisplayLists(1);
            GlStateManager.glNewList(this.glSkyList2, 4864);
            this.renderSky(bufferbuilder, -16.0F, true);
            tessellator.draw();
            GlStateManager.glEndList();
        }
    }

    private void renderSky(BufferBuilder bufferBuilderIn, float posY, boolean reverseX) {
        int i = 64;
        int j = 6;
        bufferBuilderIn.begin(7, DefaultVertexFormats.POSITION);

        for (int k = -384; k <= 384; k += 64) {
            for (int l = -384; l <= 384; l += 64) {
                float f = k;
                float f1 = (k + 64);

                if (reverseX) {
                    f1 = k;
                    f = (k + 64);
                }

                bufferBuilderIn.pos(f, posY, l).endVertex();
                bufferBuilderIn.pos(f1, posY, l).endVertex();
                bufferBuilderIn.pos(f1, posY, (l + 64)).endVertex();
                bufferBuilderIn.pos(f, posY, (l + 64)).endVertex();
            }
        }
    }

    private static class UV {
        private final double u;
        private final double v;

        private UV(double u, double v) {
            this.u = u;
            this.v = v;
        }

        public static UV uv(double u, double v) {
            return new UV(u, v);
        }
    }


    private static UV[] faceDown  = new UV[] { UV.uv(0.0D, 1.0D), UV.uv(0.0D, 0.0D), UV.uv(1.0D, 0.0D), UV.uv(1.0D, 1.0D) };
    private static UV[] faceUp    = new UV[] { UV.uv(0.0D, 1.0D), UV.uv(0.0D, 0.0D), UV.uv(1.0D, 0.0D), UV.uv(1.0D, 1.0D) };
    private static UV[] faceNorth = new UV[] { UV.uv(0.0D, 0.0D), UV.uv(0.0D, 1.0D), UV.uv(1.0D, 1.0D), UV.uv(1.0D, 0.0D) };
    private static UV[] faceSouth = new UV[] { UV.uv(1.0D, 1.0D), UV.uv(1.0D, 0.0D), UV.uv(0.0D, 0.0D), UV.uv(0.0D, 1.0D) };
    private static UV[] faceWest  = new UV[] { UV.uv(1.0D, 0.0D), UV.uv(0.0D, 0.0D), UV.uv(0.0D, 1.0D), UV.uv(1.0D, 1.0D) };
    private static UV[] faceEast  = new UV[] { UV.uv(0.0D, 1.0D), UV.uv(1.0D, 1.0D), UV.uv(1.0D, 0.0D), UV.uv(0.0D, 0.0D) };


    @SideOnly(Side.CLIENT)
    private static void renderSkyTexture(float alpha) {
        ResourceLocation sky = STARS;
        ResourceLocation sky2 = STARSA;
        TextureManager renderEngine = Minecraft.getMinecraft().getTextureManager();

        GlStateManager.enableTexture2D();
        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.depthMask(false);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder renderer = tessellator.getBuffer();

        for (int i = 0; i < 6; ++i) {
            GlStateManager.pushMatrix();

            UV[] uv = faceDown;
            boolean white = true;

            if (i == 0) {       // Down face
                uv = faceDown;
                renderEngine.bindTexture(sky2);
            } else if (i == 1) {       // North face
                renderEngine.bindTexture(sky);
                GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
                uv = faceNorth;
            } else if (i == 2) {       // South face
                renderEngine.bindTexture(sky);
                GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
                uv = faceSouth;
            } else if (i == 3) {       // Up face
                GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
                uv = faceUp;
                renderEngine.bindTexture(sky2);
            } else if (i == 4) {       // East face
                renderEngine.bindTexture(sky);
                GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
                uv = faceEast;
            } else if (i == 5) {       // West face
                renderEngine.bindTexture(sky);
                GlStateManager.rotate(-90.0F, 0.0F, 0.0F, 1.0F);
                uv = faceWest;
            }

            renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            int cc = white ? 255 : 0;
            int a = (int) (alpha * 255);
            renderer.pos(-100.0D, -100.0D, -100.0D).tex(uv[0].u, uv[0].v).color(cc, cc, cc, a).endVertex();
            renderer.pos(-100.0D, -100.0D, 100.0D).tex(uv[1].u, uv[1].v).color(cc, cc, cc, a).endVertex();
            renderer.pos(100.0D, -100.0D, 100.0D).tex(uv[2].u, uv[2].v).color(cc, cc, cc, a).endVertex();
            renderer.pos(100.0D, -100.0D, -100.0D).tex(uv[3].u, uv[3].v).color(cc, cc, cc, a).endVertex();
            tessellator.draw();
            GlStateManager.popMatrix();
        }

        GlStateManager.depthMask(false);
        GlStateManager.disableTexture2D();
        GlStateManager.enableAlpha();
    }


    @SideOnly(Side.CLIENT)
    private void renderSky(float partialTicks, WorldClient world, Minecraft mc) {
        GlStateManager.disableTexture2D();
        Vec3d vec3d = world.getSkyColor(mc.getRenderViewEntity(), partialTicks);
        float f = (float) vec3d.x;
        float f1 = (float) vec3d.y;
        float f2 = (float) vec3d.z;

        GlStateManager.color(f, f1, f2);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        GlStateManager.depthMask(false);
        GlStateManager.enableFog();
        GlStateManager.color(f, f1, f2);

        if (vboEnabled) {
            skyVBO.bindBuffer();
            GlStateManager.glEnableClientState(32884);
            GlStateManager.glVertexPointer(3, 5126, 12, 0);
            skyVBO.drawArrays(7);
            skyVBO.unbindBuffer();
            GlStateManager.glDisableClientState(32884);
        } else {
            GlStateManager.callList(glSkyList);
        }

        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderHelper.disableStandardItemLighting();
        float[] afloat = world.provider.calcSunriseSunsetColors(world.getCelestialAngle(partialTicks), partialTicks);

        if (afloat != null) {
            GlStateManager.disableTexture2D();
            GlStateManager.shadeModel(7425);
            GlStateManager.pushMatrix();
            GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(MathHelper.sin(world.getCelestialAngleRadians(partialTicks)) < 0.0F ? 180.0F : 0.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
            float f6 = afloat[0];
            float f7 = afloat[1];
            float f8 = afloat[2];

            bufferbuilder.begin(6, DefaultVertexFormats.POSITION_COLOR);
            bufferbuilder.pos(0.0D, 100.0D, 0.0D).color(f6, f7, f8, afloat[3]).endVertex();
            int l1 = 16;

            for (int j2 = 0; j2 <= 16; ++j2) {
                float f21 = j2 * ((float) Math.PI * 2F) / 16.0F;
                float f12 = MathHelper.sin(f21);
                float f13 = MathHelper.cos(f21);
                bufferbuilder.pos((f12 * 120.0F), (f13 * 120.0F), (-f13 * 40.0F * afloat[3])).color(afloat[0], afloat[1], afloat[2], 0.0F).endVertex();
            }

            tessellator.draw();
            GlStateManager.popMatrix();
            GlStateManager.shadeModel(7424);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.pushMatrix();
        float f16 = 1.0F - world.getRainStrength(partialTicks);
        GlStateManager.color(1.0F, 1.0F, 1.0F, f16);
        GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(world.getCelestialAngle(partialTicks) * 360.0F, 1.0F, 0.0F, 0.0F);
        float f17 = 30.0F;
        renderEngine.bindTexture(SUN_TEXTURES);
        bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX);
        bufferbuilder.pos((-f17), 100.0D, (-f17)).tex(0.0D, 0.0D).endVertex();
        bufferbuilder.pos(f17, 100.0D, (-f17)).tex(1.0D, 0.0D).endVertex();
        bufferbuilder.pos(f17, 100.0D, f17).tex(1.0D, 1.0D).endVertex();
        bufferbuilder.pos((-f17), 100.0D, f17).tex(0.0D, 1.0D).endVertex();
        tessellator.draw();
        f17 = 20.0F;
        renderEngine.bindTexture(MOON_PHASES_TEXTURES);
        int k1 = world.getMoonPhase();
        int i2 = k1 % 4;
        int k2 = k1 / 4 % 2;
        float f22 = (i2 + 0) / 4.0F;
        float f23 = (k2 + 0) / 2.0F;
        float f24 = (i2 + 1) / 4.0F;
        float f14 = (k2 + 1) / 2.0F;
        bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX);
        bufferbuilder.pos((-f17), -100.0D, f17).tex(f24, f14).endVertex();
        bufferbuilder.pos(f17, -100.0D, f17).tex(f22, f14).endVertex();
        bufferbuilder.pos(f17, -100.0D, (-f17)).tex(f22, f23).endVertex();
        bufferbuilder.pos((-f17), -100.0D, (-f17)).tex(f24, f23).endVertex();
        tessellator.draw();
        GlStateManager.disableTexture2D();
        float f15 = world.getStarBrightness(partialTicks) * f16;

        if (f15 > 0.0F) {
//            GlStateManager.color(f15, f15, f15, f15);
//
//            if (vboEnabled) {
//                starVBO.bindBuffer();
//                GlStateManager.glEnableClientState(32884);
//                GlStateManager.glVertexPointer(3, 5126, 12, 0);
//                starVBO.drawArrays(7);
//                starVBO.unbindBuffer();
//                GlStateManager.glDisableClientState(32884);
//            } else {
//                GlStateManager.callList(starGLCallList);
//            }
            renderSkyTexture(f15);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableFog();
        GlStateManager.popMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.color(0.0F, 0.0F, 0.0F);
        double d3 = mc.player.getPositionEyes(partialTicks).y - world.getHorizon();

        if (d3 < 0.0D) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0F, 12.0F, 0.0F);

            if (vboEnabled) {
                sky2VBO.bindBuffer();
                GlStateManager.glEnableClientState(32884);
                GlStateManager.glVertexPointer(3, 5126, 12, 0);
                sky2VBO.drawArrays(7);
                sky2VBO.unbindBuffer();
                GlStateManager.glDisableClientState(32884);
            } else {
                GlStateManager.callList(glSkyList2);
            }

            GlStateManager.popMatrix();
            float f18 = 1.0F;
            float f19 = -((float) (d3 + 65.0D));
            float f20 = -1.0F;
            bufferbuilder.begin(7, DefaultVertexFormats.POSITION_COLOR);
            bufferbuilder.pos(-1.0D, f19, 1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(1.0D, f19, 1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(1.0D, -1.0D, 1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(-1.0D, -1.0D, 1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(-1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(1.0D, f19, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(-1.0D, f19, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(1.0D, -1.0D, 1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(1.0D, f19, 1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(1.0D, f19, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(-1.0D, f19, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(-1.0D, f19, 1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(-1.0D, -1.0D, 1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(-1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(-1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(-1.0D, -1.0D, 1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(1.0D, -1.0D, 1.0D).color(0, 0, 0, 255).endVertex();
            bufferbuilder.pos(1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            tessellator.draw();
        }
        if (world.provider.isSkyColored()) {
            GlStateManager.color(f * 0.2F + 0.04F, f1 * 0.2F + 0.04F, f2 * 0.6F + 0.1F);
        } else {
            GlStateManager.color(f, f1, f2);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, -((float) (d3 - 16.0D)), 0.0F);
        GlStateManager.callList(glSkyList2);
        GlStateManager.popMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(true);
    }

}
