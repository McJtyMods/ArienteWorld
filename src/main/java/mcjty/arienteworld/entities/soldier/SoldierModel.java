package mcjty.arienteworld.entities.soldier;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.HumanoidArm;

public class SoldierModel extends HumanoidModel<SoldierRender.State> {

    public SoldierModel(float modelSize, boolean p_i1168_2_) {
        this(modelSize, 0.0F, 64, p_i1168_2_ ? 32 : 64);
    }

    protected SoldierModel(float modelSize, float yOffsetIn, int textureWidthIn, int textureHeightIn) {
        super(LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(modelSize), yOffsetIn),
                textureWidthIn, textureHeightIn).bakeRoot());
    }

    @Override
    public void setupAnim(SoldierRender.State state) {
        super.setupAnim(state);
        boolean rightHanded = state.mainArm == HumanoidArm.RIGHT;
        if (state.aiming) {
            AnimationUtils.animateCrossbowHold(rightArm, leftArm, head, rightHanded);
        } else if (state.aggressive) {
            AnimationUtils.swingWeaponDown(rightArm, leftArm, state.mainArm, state.swingAnimation, state.ageInTicks);
        }
    }
}
