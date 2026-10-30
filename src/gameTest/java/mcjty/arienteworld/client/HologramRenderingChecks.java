package mcjty.arienteworld.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.blocks.defense.ForceFieldRenderer;
import mcjty.ariente.gui.ModGuis;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.hologui.gui.HoloGuiEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntPredicate;

import static mcjty.hologui.setup.Registration.HOLOGUI_ENTITY_TYPE;

/** Pixel regressions: emitting vertices alone does not prove that controls survive depth testing. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class HologramRenderingChecks {

    private static final int SIZE = 384;
    private static final Matrix4f PROJECTION = new Matrix4f().ortho(-.7f, .7f, -.7f, .7f, .1f, 10);
    private static boolean checked;
    private static int runs;

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (checked || !Boolean.getBoolean("ariente.hologramRenderCheck") || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || mc.player == null || !mc.player.getMainHandItem().is(Registration.ENHANCED_ENERGY_SABRE.get())
                || !mc.player.getMainHandItem().getOrCreateTag().getBoolean(ArmorUpgradeType.ENERGY.getModuleKey())
                || mc.player.getMainHandItem().getOrCreateTag().getInt(ArmorUpgradeType.ENERGY.getHotkeyKey()) != 1
                || !mc.player.getItemBySlot(EquipmentSlot.CHEST).getOrCreateTag().getBoolean(ArmorUpgradeType.ENERGY.getModuleKey())) {
            return;
        }
        checkGui(mc, ModGuis.GUI_ARMOR_CHEST, 1, true);
        checkGui(mc, ModGuis.GUI_ARMOR_SABRE, 1, true);
        checkGui(mc, ModGuis.GUI_LEVITATOR, .75f, false);
        checked = true;
        runs++;
        ArienteWorld.setup.getLogger().info("HOLOGRAM PIXEL CHECKS PASSED (run {}, {}): armor/sabre modules, checkboxes, hotkeys and mounted cart controls at five angles; glass/shield depth occlusion",
                runs, mc.options.graphicsMode().get());
        if (runs == 1) {
            mc.reloadResourcePacks().thenRun(() -> checked = false);
        }
    }

    public static void checkGui(Minecraft mc, String guiId, float scale, boolean checkbox) {
        HoloGuiEntity holo = new HoloGuiEntity(HOLOGUI_ENTITY_TYPE.get(), mc.level);
        holo.setGuiId(guiId);
        holo.setScale(scale);
        checkGui(mc, holo, checkbox);
    }

    public static void checkGui(Minecraft mc, HoloGuiEntity holo, boolean checkbox) {
        String guiId = holo.getGuiId();
        float scale = holo.getScale();
        holo.setYRot(180);
        holo.yRotO = 180;
        var target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        var oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var oldSorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(PROJECTION, oldSorting);
            int angleIndex = 0;
            for (float[] angle : new float[][]{{0, 0}, {35, 0}, {-35, 0}, {0, 25}, {0, -25}}) {
                target.setClearColor(0, 0, 0, 0);
                RenderSystem.depthMask(true);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                PoseStack poses = new PoseStack();
                poses.translate(0, 0, -2);
                poses.mulPose(Axis.YP.rotationDegrees(angle[0]));
                poses.mulPose(Axis.XP.rotationDegrees(angle[1]));
                Matrix4f centered = new Matrix4f(poses.last().pose());
                poses.translate(0, -.5, 0);
                var buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
                mc.getEntityRenderDispatcher().getRenderer(holo).render(holo, 0, 0, poses, buffers, LightTexture.FULL_BRIGHT);
                buffers.endBatch();
                try (NativeImage before = Screenshot.takeScreenshot(target)) {
                    save(before, "hologram-" + guiId + "-" + angleIndex + ".png");
                    if (checkbox) {
                        float factor = 1 - (1 - scale) * 1.6f;
                        int[] pixel = project(centered, -.18f * factor, .18f * factor);
                        int green = 0;
                        for (int y = pixel[1] - 14; y <= pixel[1] + 14; y++) {
                            for (int x = pixel[0] - 14; x <= pixel[0] + 14; x++) {
                                int color = before.getPixelRGBA(x, y);
                                int red = color & 255;
                                int g = color >> 8 & 255;
                                int blue = color >> 16 & 255;
                                if (g > 150 && g > red * 1.4 && g > blue * 1.4) {
                                    green++;
                                }
                            }
                        }
                        require(green > 8, "Enabled checkbox must be visible at " + guiId + "/" + angleIndex + "; green pixels=" + green);
                        checkDarkIcon(before, centered, -.27f, .18f, "module item");
                        checkDarkIcon(before, centered, -.08f, .18f, "hotkey number");
                        checkDarkIcon(before, centered, .13f, .08f, "unavailable module item");
                    } else {
                        float factor = 1 - (1 - scale) * 1.6f;
                        checkIcon(before, centered, -.18f * factor, .16f * factor, "mounted cart arrow", color -> {
                            int r = color & 255;
                            int g = color >> 8 & 255;
                            int b = color >> 16 & 255;
                            return r > 100 && Math.abs(r - g) < 15 && Math.abs(g - b) < 15;
                        });
                    }
                    // These are the same native texture/material inputs as glass items and shields.
                    drawBehind(centered, new ResourceLocation("minecraft", "textures/block/glass.png"));
                    drawBehind(centered, ForceFieldRenderer.FORCEFIELD);
                    try (NativeImage after = Screenshot.takeScreenshot(target)) {
                        int changed = 0;
                        for (float y = -.3f; y <= .3f; y += .03f) {
                            for (float x = -.3f; x <= .3f; x += .03f) {
                                float factor = 1 - (1 - scale) * 1.6f;
                                int[] pixel = project(centered, x * factor, y * factor);
                                if (before.getPixelRGBA(pixel[0], pixel[1]) != after.getPixelRGBA(pixel[0], pixel[1])) {
                                    changed++;
                                }
                            }
                        }
                        require(changed == 0, "Glass/shields behind a panel must not overdraw its face: " + guiId + "/" + angleIndex + "; changed pixels=" + changed);
                        save(after, "hologram-" + guiId + "-" + angleIndex + ".png");
                    }
                }
                angleIndex++;
            }
        } finally {
            target.destroyBuffers();
            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static int[] project(Matrix4f view, float x, float y) {
        Vector4f point = new Vector4f(x, y, .01f, 1).mul(view).mul(PROJECTION);
        return new int[]{Math.round((point.x / point.w + 1) * SIZE / 2), Math.round((1 - point.y / point.w) * SIZE / 2)};
    }

    private static void checkDarkIcon(NativeImage image, Matrix4f view, float x, float y, String description) {
        checkIcon(image, view, x, y, description, color -> (color & 255) < 65 && (color >> 8 & 255) < 120 && (color >> 16 & 255) < 180);
    }

    private static void checkIcon(NativeImage image, Matrix4f view, float x, float y, String description, IntPredicate matches) {
        int[] pixel = project(view, x, y);
        int count = 0;
        for (int py = pixel[1] - 8; py <= pixel[1] + 8; py++) {
            for (int px = pixel[0] - 8; px <= pixel[0] + 8; px++) {
                int color = image.getPixelRGBA(px, py);
                if (matches.test(color)) {
                    count++;
                }
            }
        }
        require(count > 8, description + " must remain visible when viewed at an angle; pixels=" + count);
    }

    private static void drawBehind(Matrix4f view, ResourceLocation texture) {
        var buffers = MultiBufferSource.immediate(new BufferBuilder(1024));
        var vertices = buffers.getBuffer(RenderType.entityTranslucent(texture));
        for (float[] corner : new float[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
            vertices.vertex(view, corner[0], corner[1], -.2f).color(255, 255, 255, 200)
                    .uv((corner[0] + 1) / 2, (corner[1] + 1) / 2).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();
        }
        buffers.endBatch();
    }

    private static void save(NativeImage image, String name) {
        try {
            Path directory = Path.of("..", "verification");
            Files.createDirectories(directory);
            image.writeToFile(directory.resolve(name));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save hologram rendering fixture", e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Hologram pixel regression: " + message);
        }
    }
}
