package mcjty.arienteworld.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import mcjty.ariente.blocks.utility.autofield.AutoFieldTile;
import mcjty.ariente.blocks.utility.autofield.AutoFieldRenderer;
import mcjty.ariente.client.ArienteRenderType;
import mcjty.ariente.client.WorldHolograms;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Replays the Tutorial field crash in a disposable client, including a resource reload. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class AutoFieldRenderingChecks {

    private static final BlockPos FIELD = new BlockPos(113, 65, 117);
    private static int ticks;
    private static int checks;
    private static boolean started;
    private static boolean reloading;
    private static volatile boolean ready;
    private static volatile boolean reloaded;
    private static volatile int reloadTick;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("ariente.autoFieldRenderCheck") || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) {
            mc.gui.setScreen(null);
        }
        if (mc.player == null && mc.gui.screen() != null) {
            for (var child : mc.gui.screen().children()) {
                if (child instanceof Button button && (button.getMessage().getString().equals("Proceed")
                        || button.getMessage().getString().equals("Continue"))) {
                    button.onPress();
                    break;
                }
            }
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.gui.overlay() != null) {
            return;
        }
        if (!started) {
            started = true;
            mc.options.renderDistance().set(8);
            if (Boolean.getBoolean("ariente.hologramFabulous")) {
                mc.options.graphicsMode().set(GraphicsStatus.FABULOUS);
                mc.levelRenderer.allChanged();
            }
            mc.options.hideGui = true;
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(), 119.5, 74, 129.5, 180, 35);
                ready = true;
            });
        }
        ticks++;
        if (checks == 1 && ticks > 160 && !reloading) {
            reloading = true;
            mc.reloadResourcePacks().thenRun(() -> {
                reloadTick = ticks;
                reloaded = true;
            });
        }
        if (checks == 2) {
            ArienteWorld.setup.getLogger().info("AUTOFIELD RENDER CHECK PASSED: outline depth, uniform module backgrounds, stable marker lighting and resource reload; Oculus={}, Embeddium={}",
                    ModList.get().isLoaded("oculus"), ModList.get().isLoaded("embeddium"));
            mc.stop();
        }
        require(ticks < 1200, "Client never finished the field rendering check");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void render(RenderLevelStageEvent event) throws IOException {
        if (!Boolean.getBoolean("ariente.autoFieldRenderCheck") || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || !ready || ticks < 100 || checks == 2 || (checks == 1 && (!reloaded || ticks < reloadTick + 60))) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.overlay() != null) {
            return;
        }
        try (NativeImage before = Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget())) {
            WorldHolograms.renderWorld();
            try (NativeImage after = Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget())) {
                int changed = 0;
                for (int y = 0; y < after.getHeight(); y += 2) {
                    for (int x = 0; x < after.getWidth(); x += 2) {
                        if (before.getPixelRGBA(x, y) != after.getPixelRGBA(x, y)) {
                            changed++;
                        }
                    }
                }
                require(changed > 100, "Deferred holograms must appear in the final world framebuffer, including Fabulous mode");
            }
        }
        require(mc.level.getBlockEntity(FIELD) instanceof AutoFieldTile, "The Tutorial automation field is missing");
        AutoFieldTile field = (AutoFieldTile) mc.level.getBlockEntity(FIELD);
        // Isolate the outline in the vertex audit; normal world frames also render item transfers.
        AutoFieldTile outline = new AutoFieldTile(FIELD, field.getBlockState());
        outline.setLevel(mc.level);
        CompoundTag tag = field.saveWithoutMetadata();
        tag.getCompound("Info").putBoolean("renderOutline", true);
        tag.getCompound("Info").putBoolean("renderItems", false);
        outline.load(tag);
        require(outline.getFieldBox() != null, "Field markers must be loaded before auditing its outline");
        RenderType type = ArienteRenderType.FIELD_OUTLINE;
        BufferBuilder vertices = new BufferBuilder(4096);
        vertices.begin(PrimitiveTopology.QUADS, type.format());
        var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(field);
        require(renderer != null, "The registered field renderer is missing");
        AutoFieldRenderer.renderOutline(outline, new PoseStack(), requested -> {
            require(requested == type, "Outline must retain its entity render type and dedicated atlas");
            return vertices;
        });
        var rendered = vertices.end();
        try {
            require(rendered.drawState().vertexCount() == 48, "A field outline must have 12 four-vertex beams");
            var data = rendered.vertexBuffer().order(ByteOrder.nativeOrder());
            // Oculus can replace NEW_ENTITY with an extended shader vertex format.
            VertexFormat format = rendered.drawState().format();
            int stride = format.getVertexSize();
            int overlayOffset = -1;
            int lightOffset = -1;
            for (int element = 0; element < format.getElements().size(); element++) {
                VertexFormatElement attribute = format.getElements().get(element);
                if (attribute.getUsage() == VertexFormatElement.Usage.UV) {
                    if (attribute.getIndex() == 1) {
                        overlayOffset = format.getOffset(element);
                    } else if (attribute.getIndex() == 2) {
                        lightOffset = format.getOffset(element);
                    }
                }
            }
            require(format.hasPosition() && format.hasColor() && format.hasNormal() && format.hasUV(0) && lightOffset >= 0,
                    "The active vertex format must retain entity geometry, texture and lighting");
            for (int i = 0; i < 48; i++) {
                int offset = i * stride;
                require(Float.isFinite(data.getFloat(offset)) && Float.isFinite(data.getFloat(offset + 4))
                        && Float.isFinite(data.getFloat(offset + 8)), "Outline coordinates must stay finite");
                if (overlayOffset >= 0) {
                    require(data.getInt(offset + overlayOffset) == OverlayTexture.NO_OVERLAY, "Every overlay attribute must contain overlay UVs");
                }
                require(data.getInt(offset + lightOffset) == 100, "Overlay coordinates must not displace the configured lightmap");
            }
        } finally {
            rendered.release();
        }
        BeamRenderingChecks.check(mc);
        DisplayTransparencyChecks.check(mc);
        TankLightingChecks.check(mc);
        Path output = Path.of("screenshots");
        Files.createDirectories(output);
        try (NativeImage screenshot = Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget())) {
            screenshot.writeToFile(output.resolve("autofield-" + checks + ".png"));
        }
        checks++;
    }

    private static void require(boolean success, String message) {
        if (!success) {
            throw new IllegalStateException("Automation field rendering regression: " + message);
        }
    }
}
