package mcjty.arienteworld.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import mcjty.ariente.items.EnergyLanceItem;
import mcjty.ariente.items.LanceAim;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashSet;
import java.util.Set;

/** Checks the baked lance's real geometry in the native world and hand passes. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class LanceAlignmentClientChecks {

    private static Matrix4f worldToClip;
    private static Vec3 camera;
    private static final Set<String> CHECKED = new HashSet<>();

    @SubscribeEvent
    public static void frame(RenderLevelStageEvent event) {
        if (Boolean.getBoolean("ariente.lanceAlignmentCheck") && event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            worldToClip = new Matrix4f(event.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrixCopy())
                    .mul(event.getPoseStack().last().pose());
            if (!worldToClip.isFinite()) {
                worldToClip = null;
            }
            camera = event.getCamera().getPosition();
        }
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        if (Boolean.getBoolean("ariente.lanceAlignmentCheck") && event.getHand() == InteractionHand.MAIN_HAND) {
            check(Minecraft.getInstance().player, true, event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void living(RenderLivingEvent.Post<?, ?> event) {
        if (Boolean.getBoolean("ariente.lanceAlignmentCheck")) {
            check(event.getEntity(), false, event.getPartialTick());
        }
    }

    private static void check(LivingEntity holder, boolean firstPerson, float partialTick) {
        if (worldToClip == null || !EnergyLanceItem.canFire(holder)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean left = holder.getMainArm() == HumanoidArm.LEFT;
        ItemDisplayContext context = firstPerson
                ? (left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                : (left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        var model = mc.getItemRenderer().getModel(holder.getMainHandItem(), mc.level, holder, 0);
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int side = -1; side < 6; side++) {
            for (var quad : model.getQuads(null, side < 0 ? null : Direction.values()[side], RandomSource.create(42))) {
                int[] data = quad.getVertices();
                for (int vertex = 0; vertex < 4; vertex++) {
                    float y = Float.intBitsToFloat(data[vertex * (data.length / 4) + 1]);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        require(maxY > minY, "The actual baked lance geometry must be present");
        PoseStack poses = new PoseStack();
        poses.translate(3, -4, 7);
        model.applyTransform(context, poses, left);
        poses.translate(-.5, -.5, -.5);
        Vec3 eye = holder.getEyePosition(partialTick);
        Vec3 forward = holder.getViewVector(partialTick);
        if (holder instanceof SoldierEntity soldier && soldier.getTarget() != null) {
            forward = soldier.getTarget().getEyePosition(partialTick).subtract(eye);
        }
        LanceAim shot = LanceAim.calculate(holder, eye, forward, Mth.rotLerp(partialTick, holder.yRotO, holder.getYRot()));
        if (holder instanceof SoldierEntity && Math.abs(forward.y) < .01) {
            require(shot.muzzle().y < eye.y, "A stationary soldier's lance must stay below its head even before body and head rotations align");
        }
        float scale = firstPerson ? .75F : 1;
        Matrix4f toWorld = new Matrix4f(worldToClip).invert()
                .mul(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrixCopy());
        Vec3 tip = worldPoint(toWorld, poses, maxY);
        Vec3 butt = worldPoint(toWorld, poses, minY);
        require(tip.distanceTo(shot.muzzle()) < .001, "The rendered tip must coincide with the projectile muzzle: " + context
                + ", tip=" + tip + ", muzzle=" + shot.muzzle() + ", bounds=" + minY + "/" + maxY
                + ", direction=" + shot.direction() + ", world=" + worldToClip + ", projection=" + RenderSystem.getProjectionMatrix()
                + ", view=" + RenderSystem.getModelViewMatrixCopy() + ", model=" + poses.last().pose());
        require(tip.subtract(butt).normalize().dot(shot.direction()) > .999999,
                "The actual shaft must aim along the fired laser: " + context);
        require(Math.abs(tip.distanceTo(butt) - (maxY - minY) * scale) < .001,
                "Aiming must preserve the model's length");
        String condition = condition(holder);
        if (CHECKED.add(condition)) {
            ArienteWorld.setup.getLogger().info("LANCE ALIGNMENT CHECK PASSED: {}", condition);
        }
    }

    public static void verifyCurrentScenario() {
        require(CHECKED.contains(condition(Minecraft.getInstance().player)), "The current view, arm and FOV must run the geometry check");
    }

    private static String condition(LivingEntity holder) {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.getCameraType() + " " + holder.getMainArm() + " fov=" + mc.options.fov().get()
                + " yaw=" + Math.round(holder.getYRot()) + " pitch=" + Math.round(holder.getXRot());
    }

    private static Vec3 worldPoint(Matrix4f toWorld, PoseStack poses, float y) {
        Vector3f vertex = poses.last().pose().transformPosition(.5F, y, .5F, new Vector3f());
        Vector4f point = toWorld.transform(new Vector4f(vertex, 1));
        return new Vec3(point.x / point.w, point.y / point.w, point.z / point.w).add(camera);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Lance alignment check: " + message);
        }
    }
}
