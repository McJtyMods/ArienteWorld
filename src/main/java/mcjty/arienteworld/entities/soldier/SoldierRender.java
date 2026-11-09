package mcjty.arienteworld.entities.soldier;

import mcjty.ariente.items.EnergyLanceItem;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;

import com.mojang.blaze3d.vertex.PoseStack;
import javax.annotation.Nonnull;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;

public class SoldierRender extends HumanoidMobRenderer<SoldierEntity, SoldierRender.State, SoldierModel> {

    private Identifier mobTexture = Identifier.fromNamespaceAndPath(ArienteWorld.MODID, "textures/entity/soldier.png");

    public static final Factory FACTORY = new Factory();

    public static final MasterFactory MASTER_FACTORY = new MasterFactory();

    public SoldierRender(Context rendermanagerIn, float scale) {
        super(rendermanagerIn, new SoldierModel(scale-1.0f, false), 0.5F * scale);
        addLayer(new HumanoidArmorLayer<>(this,
                ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR,
                        rendermanagerIn.getModelSet(), HumanoidModel::new),
                rendermanagerIn.getEquipmentRenderer()));
    }

    @Override
    protected void scale(State state, PoseStack matrixStackIn) {
        if (state.master) {
            matrixStackIn.scale(1.4F, 1.4F, 1.4F);
        }
    }

    @Override
    @Nonnull
    public Identifier getTextureLocation(@Nonnull State state) {
        return mobTexture;
    }

    public static class State extends HumanoidRenderState {
        public boolean aiming;
        public boolean aggressive;
        public boolean master;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SoldierEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.aiming = EnergyLanceItem.canFire(entity);
        state.aggressive = entity.isAggressive();
        state.master = entity instanceof MasterSoldierEntity;
        state.rightArmPose = entity.getItemHeldByArm(HumanoidArm.RIGHT).isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        state.leftArmPose = entity.getItemHeldByArm(HumanoidArm.LEFT).isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
    }

    public static class Factory implements EntityRendererProvider<SoldierEntity> {

        @Override
        public EntityRenderer<SoldierEntity, ?> create(Context manager) {
            return new SoldierRender(manager, 1.0f);
        }

    }

    public static class MasterFactory implements EntityRendererProvider<SoldierEntity> {

        @Override
        public EntityRenderer<SoldierEntity, ?> create(Context manager) {
            return new SoldierRender(manager, 1.5f);
        }

    }

}
