package mcjty.arienteworld.client;

import static mcjty.arienteworld.setup.Registration.ENTITY_SOLDIER;
import static mcjty.arienteworld.setup.Registration.ENTITIES;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mcjty.ariente.blocks.utility.autofield.AutoFieldRenderer;
import mcjty.ariente.blocks.utility.autofield.AutoFieldRenderInfo;
import mcjty.ariente.blocks.utility.autofield.TransferRender;
import mcjty.ariente.facade.FacadeItemBlock;
import mcjty.lib.multipart.PartPos;
import mcjty.lib.multipart.PartSlot;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import mcjty.ariente.client.ArienteSpriteUploader;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.ariente.entities.fluxship.FluxShipRender;
import mcjty.arienteworld.entities.soldier.SoldierRender;
import mcjty.ariente.items.BlueprintItem;
import mcjty.ariente.items.BlueprintRenderer;
import mcjty.ariente.items.modules.ArmorModuleItem;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.hologui.config.HoloGuiModConfigGui;
import mcjty.hologui.gui.HoloGuiRenderTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import mcjty.lib.client.RenderBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.ArrayList;
import net.minecraft.core.Direction;

/** Opt-in pixel and resource-reload checks for the systems restored in phase 3. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class SystemRenderingChecks {

    private static int ticks;
    private static int runs;
    private static boolean reloadReady;
    private static int flightTicks;
    private static volatile boolean flightChecked;
    private static TransferRender transfer;
    private static int reloadTick;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        boolean soldierOnly = Boolean.getBoolean("ariente.soldierRenderCheck");
        if ((!Boolean.getBoolean("ariente.systemRenderCheck") && !soldierOnly) || event.phase != TickEvent.Phase.END || mc.level == null) {
            return;
        }
        ticks++;
        if (soldierOnly) {
            if (runs == 1 && ticks == 120) {
                mc.reloadResourcePacks().thenRun(() -> {
                    reloadTick = ticks;
                    reloadReady = true;
                });
            }
            if (runs == 2 && ticks > 200) {
                mc.stop();
            }
            return;
        }
        if (ticks == 5 && mc.getSingleplayerServer() != null) {
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                var level = player.serverLevel();
                BlockPos origin = new BlockPos(90044, 260, 90044);
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -1, -3), origin.offset(3, 30, 40))) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                var ship = FluxShipEntity.create(level, 90044.5, 260, 90044.5);
                level.addFreshEntity(ship);
                player.teleportTo(90044.5, 262, 90044.5);
                player.startRiding(ship);
            });
        }
        if (mc.player.getVehicle() instanceof FluxShipEntity && flightTicks < 45) {
            KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_W), true);
            KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_UP), true);
            if (++flightTicks == 45) {
                KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_W), false);
                KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_UP), false);
                var server = mc.getSingleplayerServer();
                var uuid = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    var ship = (FluxShipEntity) player.getVehicle();
                    require(ship.getZ() > 90052 && ship.getY() > 265, "Held client keys must move the server ship: " + ship.position());
                    player.stopRiding();
                    ship.discard();
                    flightChecked = true;
                    ArienteWorld.setup.getLogger().info("SHIP PILOT CHECK PASSED: native held keys, pilot packets, synchronized server movement");
                });
            }
        }
        if (ticks > 400 && !flightChecked) {
            throw new IllegalStateException("Phase 3 client check: pilot control test did not complete");
        }
        if (ticks == 40) {
            transfer = createTransfer();
        }
        if (runs == 1 && ticks == 120) {
            mc.reloadResourcePacks().thenRun(() -> {
                transfer = createTransfer();
                reloadTick = ticks;
                reloadReady = true;
            });
        }
        if (runs == 2 && ticks > 200) {
            mc.stop();
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        boolean soldierOnly = Boolean.getBoolean("ariente.soldierRenderCheck");
        if ((!Boolean.getBoolean("ariente.systemRenderCheck") && !soldierOnly) || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || ticks < 80 || !soldierOnly && !flightChecked || runs >= 2 || runs == 1 && (!reloadReady || ticks < reloadTick + 30)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (soldierOnly) {
            renderSoldiers(mc);
            renderDimensionMobs(mc);
            runs++;
            ArienteWorld.setup.getLogger().info("SOLDIER RENDER CHECK PASSED: sword transforms, both hands, idle/attack poses and armor; pass=" + runs);
            return;
        }
        var model = mc.getModelManager().getModel(FluxShipRender.MODEL);
        var quads = new ArrayList<>(model.getQuads(null, null, RandomSource.create(1)));
        for (Direction side : Direction.values()) {
            quads.addAll(model.getQuads(null, side, RandomSource.create(1)));
        }
        require(quads.size() > 20 && quads.stream().noneMatch(quad -> quad.getSprite().contents().name().getPath().equals("missingno")),
                "The original ship OBJ must bake textured faces: model=" + model.getClass().getName() + ", quads=" + quads.size());
        require(!mc.getModelManager().getModel(BlueprintRenderer.BASE_MODEL).isCustomRenderer(), "Blueprint base must avoid renderer recursion");
        for (var beam : AutoFieldRenderer.BEAMS) {
            require(mc.getTextureAtlas(ArienteSpriteUploader.ARIENTE_ATLAS).apply(beam).contents().name().equals(beam), "Automation beam must use its own current atlas");
        }
        try (NativeImage ship = picture(4, (pose, buffers) -> {
            pose.translate(0, -.5, 0);
            pose.rotate(Axis.XP.rotationDegrees(20));
            pose.rotate(Axis.YP.rotationDegrees(25));
            FluxShipEntity entity = FluxShipEntity.create(mc.level, 0, 0, 0);
            mc.getEntityRenderDispatcher().getRenderer(entity).render(entity, 0, 0, pose, buffers, LightTexture.FULL_BRIGHT);
        })) {
            require(bright(ship) > 200, "The ship must draw visible hull pixels");
            save(ship, "ship-" + runs);
        }
        try (NativeImage empty = blueprint(new ItemStack(Registration.BLUEPRINT.get()));
             NativeImage filled = blueprint(BlueprintItem.makeBluePrint(new ItemStack(Items.DIAMOND)))) {
            int changed = 0;
            for (int y = 0; y < empty.getHeight(); y++) {
                for (int x = 0; x < empty.getWidth(); x++) {
                    if (empty.getPixelRGBA(x, y) != filled.getPixelRGBA(x, y)) {
                        changed++;
                    }
                }
            }
            require(bright(empty) > 50 && changed > 100, "Blueprint base and its result preview must both be visible");
            save(filled, "blueprint-" + runs);
        }
        try (NativeImage recursive = blueprint(BlueprintItem.makeBluePrint(new ItemStack(Registration.BLUEPRINT.get())))) {
            require(bright(recursive) > 50, "A blueprint destination must not recurse");
        }
        for (int angle : new int[]{0, 35, -35}) {
            try (NativeImage tooltip = picture(.65F, (pose, buffers) -> {
                pose.rotate(Axis.YP.rotationDegrees(angle));
                pose.scale(.01F, .01F, .01F);
                pose.rotate(Axis.YP.rotationDegrees(180));
                pose.rotate(Axis.ZP.rotationDegrees(180));
                pose.translate(0, 0, -10);
                pose.scale(.4F, .4F, 1);
                HoloGuiRenderTools.renderToolTip(pose, buffers, new ItemStack(Items.DIAMOND), -90, -60,
                        (stack, lines) -> lines.add("Phase 3 tooltip"));
            })) {
                require(bright(tooltip) > 100, "Tooltip text must remain visible at angle " + angle);
                save(tooltip, "tooltip-" + angle + "-" + runs);
            }
        }
        ItemStack facade = new ItemStack(Registration.FACADE_ITEM.get());
        FacadeItemBlock.setMimicBlock(facade, Blocks.OAK_STAIRS.defaultBlockState());
        try (NativeImage image = blueprint(facade)) {
            require(bright(image) > 100, "Facade items must render their selected block state");
            save(image, "facade-" + runs);
        }
        renderSoldiers(mc);
        try (NativeImage image = picture(7, (pose, buffers) -> require(transfer.render(pose, type -> buffers.getBuffer(HoloGuiRenderTools.guiRenderType(type))), "Transfer animation must still be in flight"))) {
            require(bright(image) > 5, "Transfer animation must emit visible item pixels into the supplied buffer");
            save(image, "transfer-" + runs);
        }
        HoloGuiModConfigGui screen = new HoloGuiModConfigGui(null);
        mc.gui.setScreen(screen);
        require(screen.children().size() == 3, "The native config screen must expose both style selectors and Done");
        screen.onClose();
        runs++;
        ArienteWorld.setup.getLogger().info("SYSTEM RENDER CHECK PASSED: ship, blueprints, tooltips, atlas and config screen; pass=" + runs);
    }

    private static void renderSoldiers(Minecraft mc) {
        for (var weapon : new ItemStack[]{new ItemStack(Registration.ENERGY_SABRE.get()), new ItemStack(Registration.ENHANCED_ENERGY_SABRE.get())}) {
            var weaponModel = mc.getItemRenderer().getModel(weapon, mc.level, null, 0);
            require(Math.abs(weaponModel.getTransforms().getTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation.z()) > 1,
                    "Sabres must use the held-sword transform");
        }
        for (boolean leftHanded : new boolean[]{false, true}) {
            for (boolean aggressive : new boolean[]{false, true}) {
                try (NativeImage image = soldier(false, leftHanded, aggressive)) {
                    save(image, "soldier-sabre-" + (leftHanded ? "left-" : "right-") + (aggressive ? "attack-" : "idle-") + runs);
                }
                try (NativeImage image = soldier(false, leftHanded, aggressive, true)) {
                    require(bright(image) > 200, "A lance and its two-handed wielder must render visible pixels");
                    save(image, "soldier-lance-" + (leftHanded ? "left-" : "right-") + (aggressive ? "attack-" : "idle-") + runs);
                }
            }
        }
        try (NativeImage item = blueprint(new ItemStack(Registration.ENERGY_LANCE.get()));
             NativeImage blueprint = blueprint(BlueprintItem.makeBluePrint(new ItemStack(Registration.ENERGY_LANCE.get())));
             NativeImage armored = soldier(true, false, true, true)) {
            require(bright(item) > 50 && bright(blueprint) > 50, "The lance and populated blueprint must render in inventory");
            save(item, "energy-lance-" + runs);
            save(blueprint, "energy-lance-blueprint-" + runs);
            save(armored, "soldier-lance-armored-" + runs);
        }
        try (NativeImage bare = soldier(false, false, false); NativeImage armored = soldier(true, false, false)) {
            int changed = 0;
            for (int y = 0; y < bare.getHeight(); y++) {
                for (int x = 0; x < bare.getWidth(); x++) {
                    if (bare.getPixelRGBA(x, y) != armored.getPixelRGBA(x, y)) {
                        changed++;
                    }
                }
            }
            require(changed > 100, "A soldier's equipped armor must affect rendered pixels");
            save(armored, "soldier-armor-" + runs);
        }
    }

    private static void renderDimensionMobs(Minecraft mc) {
        for (var entry : ENTITIES.getEntries()) {
            Mob mob = (Mob) entry.get().create(mc.level);
            var renderer = mc.getEntityRenderDispatcher().getRenderer(mob);
            require(mc.getResourceManager().getResource(renderer.getTextureLocation(mob)).isPresent(),
                    "Dimension mob textures must resolve after the move: " + entry.getId());
            try (NativeImage image = picture(3.0F, (pose, buffers) -> {
                pose.rotate(Axis.XP.rotationDegrees(20));
                pose.rotate(Axis.YP.rotationDegrees(30));
                renderer.render(mob, 0, 0, pose, buffers, LightTexture.FULL_BRIGHT);
            })) {
                require(bright(image) > 100, "Dimension mobs must render visible textured pixels: " + entry.getId());
                save(image, entry.getId().getPath() + "-moved-" + runs);
            }
        }
    }

    private static TransferRender createTransfer() {
        return new TransferRender(new AutoFieldRenderInfo.TransferPath(PartPos.create(new BlockPos(-5, 0, 0), PartSlot.UP_NW),
                PartPos.create(new BlockPos(5, 0, 0), PartSlot.UP_NW)),
                new AutoFieldRenderInfo.Transfer(new ItemStack(Blocks.DIAMOND_BLOCK), 0), BlockPos.ZERO);
    }

    public static void checkModuleTooltips(Minecraft mc, IJeiRuntime runtime) {
        var jeiRenderer = runtime.getIngredientManager().getIngredientRenderer(VanillaTypes.ITEM_STACK);
        for (var item : ForgeRegistries.ITEMS.getValues()) {
            if (!(item instanceof ArmorModuleItem module)) {
                continue;
            }
            ItemStack stack = new ItemStack(module);
            String description = module.getInstallationDescription().getString();
            require(description.startsWith("Fits: ") && !description.contains("message.ariente"),
                    "Every module needs a translated compatibility hint: " + description);
            var details = new ArrayList<Component>();
            module.appendHoverText(stack, mc.level, details, TooltipFlag.Default.NORMAL);
            String effect = Component.translatable("message.ariente.armormodule.effect." + module.getType().getName()).getString();
            String manual = Component.translatable("message.ariente.armormodule.manual").getString();
            require(details.size() == 4 && details.stream().allMatch(line -> mc.font.width(line) <= 240)
                            && details.stream().map(Component::getString).anyMatch(effect::equals)
                            && details.stream().map(Component::getString).anyMatch(manual::equals),
                    "Module tooltips must stay compact and refer to the manual: " + module.getType().getName());
            require(stack.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL).stream().map(Component::getString).anyMatch(description::equals),
                    "The hologram's native item tooltip must include the module description");
            require(jeiRenderer.getTooltip(stack, TooltipFlag.Default.NORMAL).stream().map(Component::getString).anyMatch(description::equals),
                    "JEI must include the same module description");
            try (NativeImage tooltip = picture(.65F, (pose, buffers) -> {
                pose.scale(.01F, .01F, .01F);
                pose.rotate(Axis.YP.rotationDegrees(180));
                pose.rotate(Axis.ZP.rotationDegrees(180));
                pose.translate(0, 0, -10);
                pose.scale(.4F, .4F, 1);
                HoloGuiRenderTools.renderToolTip(pose, buffers, stack, -90, -60, (s, lines) -> lines.add("Click to install or remove"));
            })) {
                require(bright(tooltip) > 100, "The module tooltip must emit visible text");
                save(tooltip, "module-tooltip-" + module.getType().getName());
            }
        }
        ArienteWorld.setup.getLogger().info("MODULE TOOLTIP CHECK PASSED: every module describes compatible equipment and its effect in JEI and holograms");
    }

    private static NativeImage soldier(boolean armored, boolean leftHanded, boolean aggressive) {
        return soldier(armored, leftHanded, aggressive, false);
    }

    private static NativeImage soldier(boolean armored, boolean leftHanded, boolean aggressive, boolean lance) {
        Minecraft mc = Minecraft.getInstance();
        var soldier = ENTITY_SOLDIER.get().create(mc.level);
        soldier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(lance ? Registration.ENERGY_LANCE.get() : Registration.ENERGY_SABRE.get()));
        soldier.setLeftHanded(leftHanded);
        soldier.setAggressive(aggressive);
        if (armored) {
            soldier.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Registration.POWERSUIT_HEAD.get()));
            soldier.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Registration.POWERSUIT_CHEST.get()));
            soldier.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Registration.POWERSUIT_LEGS.get()));
            soldier.setItemSlot(EquipmentSlot.FEET, new ItemStack(Registration.POWERSUIT_FEET.get()));
        }
        SoldierRender renderer = (SoldierRender) mc.getEntityRenderDispatcher().getRenderer(soldier);
        var model = renderer.getModel();
        model.setupAnim(soldier, 0, 0, 0, 0, 0);
        var weaponArm = leftHanded ? model.leftArm : model.rightArm;
        var freeArm = leftHanded ? model.rightArm : model.leftArm;
        if (lance) {
            require(freeArm.xRot < -1 && weaponArm.xRot < -1,
                    "Lance wielders must keep both arms on the weapon, including while aggressive or left-handed");
        } else {
            require(freeArm.xRot > -.6F && (aggressive ? weaponArm.xRot < -1 : weaponArm.xRot > -1),
                    "Soldiers must hold a sword in their dominant hand without the zombie's two raised arms");
        }
        return picture(lance ? 2.2F : 1.8F, (pose, buffers) -> {
            pose.translate(0, -.9, 0);
            pose.rotate(Axis.YP.rotationDegrees(lance ? 60 : 20));
            mc.getEntityRenderDispatcher().getRenderer(soldier).render(soldier, 180, 0, pose, buffers, LightTexture.FULL_BRIGHT);
        });
    }

    private static NativeImage blueprint(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        return picture(.7F, (pose, buffers) -> mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0));
    }

    private static NativeImage picture(float radius, BiConsumer<PoseStack, RenderBufferSource> render) {
        Minecraft mc = Minecraft.getInstance();
        TextureTarget target = new TextureTarget(512, 512, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-radius, radius, -radius, radius, .1F, 100), sorting);
            target.setClearColor(0, 0, 0, 0);
            RenderSystem.depthMask(true);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            PoseStack pose = new PoseStack();
            pose.translate(0, 0, -10);
            var buffers = RenderBufferSource.immediate(new BufferBuilder(4096));
            render.accept(pose, buffers);
            buffers.endBatch();
            return Screenshot.takeScreenshot(target);
        } finally {
            target.destroyBuffers();
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            mc.gameRenderer.mainRenderTarget().bindWrite(true);
        }
    }

    private static int bright(NativeImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixelRGBA(x, y);
                if ((pixel & 255) > 60 || (pixel >> 8 & 255) > 60 || (pixel >> 16 & 255) > 60) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void save(NativeImage image, String name) {
        try {
            Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots/phase3");
            Files.createDirectories(directory);
            image.writeToFile(directory.resolve(name + ".png"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Phase 3 client check: " + message);
        }
    }
}
