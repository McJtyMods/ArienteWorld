package mcjty.arienteworld.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import mcjty.ariente.client.ArienteRenderType;
import mcjty.ariente.entities.LaserEntity;
import mcjty.ariente.items.armor.PowerSuitModel;
import mcjty.ariente.setup.Registration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

public class SuitAndPanelRenderingChecks {

    public static void check(Minecraft mc) {
        checkPanels(mc);
        checkCore(mc);
        checkArmor(mc);
        checkLaser(mc);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Suit/panel rendering regression: " + message);
        }
    }

    private static void checkPanels(Minecraft mc) {
        for (var block : List.of(Registration.FLAT_LIGHT.get(), Registration.ALARM.get())) {
            for (Direction facing : Direction.values()) {
                var state = block.defaultBlockState().setValue(BlockStateProperties.FACING, facing);
                var model = mc.getBlockRenderer().getBlockModel(state);
                double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
                double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
                var sides = new ArrayList<Direction>(List.of(Direction.values()));
                sides.add(null);
                for (var layer : model.getRenderTypes(state, RandomSource.create(1), ModelData.EMPTY)) {
                    for (Direction side : sides) {
                        for (var quad : model.getQuads(state, side, RandomSource.create(1), ModelData.EMPTY, layer)) {
                            for (int vertex = 0; vertex < 4; vertex++) {
                                for (int axis = 0; axis < 3; axis++) {
                                    float value = Float.intBitsToFloat(quad.getVertices()[vertex * 8 + axis]);
                                    min[axis] = Math.min(min[axis], value);
                                    max[axis] = Math.max(max[axis], value);
                                }
                            }
                        }
                    }
                }
                AABB shape = state.getShape(mc.level, BlockPos.ZERO).bounds();
                double[] expectedMin = {shape.minX, shape.minY, shape.minZ};
                double[] expectedMax = {shape.maxX, shape.maxY, shape.maxZ};
                for (int axis = 0; axis < 3; axis++) {
                    require(Math.abs(min[axis] - expectedMin[axis]) < .001 && Math.abs(max[axis] - expectedMax[axis]) < .001,
                            "Selection shape must match rendered panel: " + state + ", axis=" + axis + ", rendered=" + min[axis] + ".." + max[axis] + ", shape=" + shape);
                }
            }
        }
    }

    private static void checkCore(Minecraft mc) {
        var state = Registration.AICORE.get().defaultBlockState();
        var model = mc.getBlockRenderer().getBlockModel(state);
        require(!state.canOcclude(), "AI core glass must not hide adjacent faces");
        var layers = model.getRenderTypes(state, RandomSource.create(1), ModelData.EMPTY);
        require(layers.contains(RenderType.solid()) && layers.contains(RenderType.translucent()), "AI core needs opaque frame and translucent glass passes");
        for (var layer : layers) {
            var quads = model.getQuads(state, null, RandomSource.create(1), ModelData.EMPTY, layer);
            require(!quads.isEmpty(), "AI core render pass must contain geometry");
            require(quads.stream().allMatch(quad -> quad.getSprite().contents().name().getPath().equals(
                    layer == RenderType.solid() ? "block/marble/blackmarble" : "block/machines/aicore_window")), "AI core glass and frame must stay in their own passes");
        }
    }

    private static void checkArmor(Minecraft mc) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        var items = List.of(Registration.POWERSUIT_HEAD.get(), Registration.POWERSUIT_CHEST.get(), Registration.POWERSUIT_LEGS.get(), Registration.POWERSUIT_FEET.get());
        int[] expectedVertices = {96, 120, 72, 48};
        for (int index = 0; index < slots.length; index++) {
            EquipmentSlot slot = slots[index];
            HumanoidModel<LivingEntity> original = new HumanoidModel<>(mc.getEntityModels().bakeLayer(
                    slot == EquipmentSlot.LEGS ? ModelLayers.PLAYER_INNER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR));
            original.setAllVisible(false);
            switch (slot) {
                case HEAD -> original.head.visible = original.hat.visible = true;
                case CHEST -> original.body.visible = original.leftArm.visible = original.rightArm.visible = true;
                case LEGS -> original.body.visible = original.leftLeg.visible = original.rightLeg.visible = true;
                case FEET -> original.leftLeg.visible = original.rightLeg.visible = true;
                default -> throw new IllegalStateException();
            }
            original.head.xRot = .3f;
            original.leftArm.xRot = .7f;
            var stack = new ItemStack(items.get(index));
            var model = IClientItemExtensions.of(stack).getGenericArmorModel(mc.player, stack, slot, original);
            require(model instanceof PowerSuitModel suit && Math.abs(suit.head.xRot - .3f) < .0001
                    && Math.abs(suit.leftArm.xRot - .7f) < .0001, "Custom armor must inherit the animated humanoid pose");
            BufferBuilder builder = new BufferBuilder(8192);
            builder.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.NEW_ENTITY);
            model.renderToBuffer(new PoseStack(), builder, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            var rendered = builder.end();
            try {
                require(rendered.drawState().vertexCount() == expectedVertices[index], "Every suit piece must render its original geometry: " + slot + ", vertices=" + rendered.drawState().vertexCount());
                for (int vertex = 0; vertex < rendered.drawState().vertexCount(); vertex++) {
                    for (int offset : new int[]{16, 20}) {
                        float uv = rendered.vertexBuffer().getFloat(vertex * DefaultVertexFormat.NEW_ENTITY.getVertexSize() + offset);
                        require(Float.isFinite(uv) && uv >= 0 && uv <= 1, "Suit UVs must fit the original 64x128 texture");
                    }
                }
            } finally {
                rendered.release();
            }
        }
    }

    private static void checkLaser(Minecraft mc) {
        LaserEntity laser = LaserEntity.create(mc.level, mc.player.getX() + 2, mc.player.getY() + 1, mc.player.getZ() + 2, 1, 0, 0);
        laser.setDeltaMovement(1, 0, 0);
        BufferBuilder builder = new BufferBuilder(1024);
        builder.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.NEW_ENTITY);
        PoseStack poses = new PoseStack();
        poses.translate(0, 0, -10);
        mc.getEntityRenderDispatcher().getRenderer(laser).render(laser, 0, 0, poses, type -> {
            require(type == ArienteRenderType.ARIENTE_ADDITIVE, "Laser must use its buffered additive material");
            return builder;
        }, LightTexture.FULL_BRIGHT);
        var rendered = builder.end();
        try {
            require(rendered.drawState().vertexCount() == 4, "Laser renderer must emit its beam quad");
        } finally {
            rendered.release();
        }
    }
}
