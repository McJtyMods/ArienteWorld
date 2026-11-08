package mcjty.arienteworld.entities.darkbat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class DarkbatRender extends MobRenderer<DarkbatEntity, PhantomModel<DarkbatEntity>> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "textures/entity/darkbat.png");

    public DarkbatRender(EntityRendererProvider.Context context) {
        super(context, new PhantomModel<>(context.bakeLayer(ModelLayers.PHANTOM)), 0.75F * DarkbatEntity.SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(DarkbatEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(DarkbatEntity entity, PoseStack poses, float partialTick) {
        poses.scale(DarkbatEntity.SCALE, DarkbatEntity.SCALE, DarkbatEntity.SCALE);
        poses.translate(0.0F, 1.3125F, 0.1875F);
    }

    @Override
    protected void setupRotations(DarkbatEntity entity, PoseStack poses, float age, float yaw, float partialTick, float scale) {
        super.setupRotations(entity, poses, age, yaw, partialTick, scale);
        poses.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
    }
}
