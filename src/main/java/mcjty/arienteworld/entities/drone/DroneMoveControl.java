package mcjty.arienteworld.entities.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Straight flight with swept collision checks, also used by the core guardian. */
public class DroneMoveControl extends MoveControl {

    public DroneMoveControl(Mob mob) {
        super(mob);
    }

    public static boolean canFlyTo(Mob mob, Vec3 destination) {
        Vec3 delta = destination.subtract(mob.position());
        int steps = Math.max(1, (int) Math.ceil(delta.length() * 2));
        AABB bounds = mob.getBoundingBox();
        Vec3 step = delta.scale(1.0 / steps);
        for (int i = 0; i < steps; i++) {
            bounds = bounds.move(step);
            if (!mob.level().hasChunkAt(BlockPos.containing(bounds.getCenter()))
                    || !mob.level().noCollision(mob, bounds)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void tick() {
        if (operation != Operation.MOVE_TO) {
            return;
        }
        Vec3 destination = new Vec3(wantedX, wantedY, wantedZ);
        Vec3 delta = destination.subtract(mob.position());
        if (delta.lengthSqr() < 0.04 || !canFlyTo(mob, mob.position().add(delta.normalize().scale(Math.min(0.6, delta.length()))))) {
            operation = Operation.WAIT;
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.5));
            return;
        }
        Vec3 desired = delta.normalize().scale(Math.min(delta.length() * 0.12, speedModifier * 0.22));
        mob.setDeltaMovement(mob.getDeltaMovement().scale(0.8).add(desired.scale(0.2)));
    }
}
