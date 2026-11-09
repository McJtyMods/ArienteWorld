package mcjty.arienteworld.entities.drone;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class DroneModel extends EntityModel<LivingEntityRenderState> {

    public DroneModel() {
        super(createRoot());
    }

    private static ModelPart createRoot() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-8, -8, -8, 16, 16, 16), PartPose.offset(0, 9.6f, 0));
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }
}
