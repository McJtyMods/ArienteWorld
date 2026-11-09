package mcjty.arienteworld.entities;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public abstract class ArienteFlyingMob extends Mob {
    protected ArienteFlyingMob(EntityType<? extends ArienteFlyingMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void checkFallDamage(double verticalMovement, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public void travel(Vec3 input) {
        if (this.isLocalInstanceAuthoritative()) {
            if (this.isInWater()) {
                this.moveRelative(0.02F, input);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8F));
            } else if (this.isInLava()) {
                this.moveRelative(0.02F, input);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
            } else {
                BlockPos ground = getBlockPosBelowThatAffectsMyMovement();
                float friction = 0.91F;
                if (this.onGround()) {
                    friction = this.level().getBlockState(ground).getFriction(this.level(), ground, this) * 0.91F;
                }

                float groundAcceleration = 0.16277137F / (friction * friction * friction);
                friction = 0.91F;
                if (this.onGround()) {
                    friction = this.level().getBlockState(ground).getFriction(this.level(), ground, this) * 0.91F;
                }

                this.moveRelative(this.onGround() ? 0.1F * groundAcceleration : 0.02F, input);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(friction));
            }
        }
    }

    @Override
    public boolean onClimbable() {
        return false;
    }
}
