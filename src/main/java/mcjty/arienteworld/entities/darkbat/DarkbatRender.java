package mcjty.arienteworld.entities.darkbat;

import net.minecraft.client.renderer.entity.state.PhantomRenderState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.model.monster.phantom.PhantomModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class DarkbatRender extends MobRenderer<DarkbatEntity, PhantomRenderState, PhantomModel> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(ArienteWorld.MODID, "textures/entity/darkbat.png");

    public DarkbatRender(EntityRendererProvider.Context context) {
        super(context, new PhantomModel(context.bakeLayer(ModelLayers.PHANTOM)), 0.75F * DarkbatEntity.SCALE);
    }

    @Override
    public PhantomRenderState createRenderState() {
        return new PhantomRenderState();
    }

    @Override
    public void extractRenderState(DarkbatEntity entity, PhantomRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.flapTime = entity.getUniqueFlapTickOffset() + state.ageInTicks;
        state.size = 0;
    }

    @Override
    public Identifier getTextureLocation(PhantomRenderState state) {
        return TEXTURE;
    }

    @Override
    protected void scale(PhantomRenderState state, PoseStack poses) {
        poses.scale(DarkbatEntity.SCALE, DarkbatEntity.SCALE, DarkbatEntity.SCALE);
        poses.translate(0.0F, 1.3125F, 0.1875F);
    }

    @Override
    protected void setupRotations(PhantomRenderState state, PoseStack poses, float yaw, float scale) {
        super.setupRotations(state, poses, yaw, scale);
        poses.mulPose(Axis.XP.rotationDegrees(state.xRot));
    }
}
