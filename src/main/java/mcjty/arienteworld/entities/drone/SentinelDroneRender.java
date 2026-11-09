package mcjty.arienteworld.entities.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class SentinelDroneRender extends MobRenderer<SentinelDroneEntity, SentinelDroneRender.State, DroneModel> {

    public static final Factory FACTORY = new Factory();
    private static final Identifier TEXTURE = Identifier.parse("arienteworld:textures/entity/sentinel_drone.png");
    private static final Identifier SHOOTING = Identifier.parse("arienteworld:textures/entity/drone_shooting.png");

    public SentinelDroneRender(EntityRendererProvider.Context context) {
        super(context, new DroneModel(), 0.5f);
    }

    public static class State extends LivingEntityRenderState {
        private boolean attacking;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SentinelDroneEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.attacking = false;
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return state.attacking ? SHOOTING : TEXTURE;
    }

    @Override
    protected void scale(State state, PoseStack poses) {
        poses.scale(1.0f, 1.0f, 1.0f);
    }

    public static class Factory implements EntityRendererProvider<SentinelDroneEntity> {

        @Override
        public EntityRenderer<SentinelDroneEntity, ?> create(EntityRendererProvider.Context context) {
            return new SentinelDroneRender(context);
        }
    }
}
