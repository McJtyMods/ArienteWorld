package mcjty.arienteworld.entities.drone;

import net.minecraft.server.level.ServerLevel;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import mcjty.ariente.api.*;
import mcjty.arienteworld.entities.ArienteFlyingMob;
import net.minecraft.world.entity.*;
import javax.annotation.Nullable;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.ModSounds;
import mcjty.arienteworld.ai.CityAI;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import static mcjty.arienteworld.setup.Registration.ENTITY_SENTINEL_DRONE;

public class SentinelDroneEntity extends ArienteFlyingMob implements IForcefieldImmunity, ISentinel {

    public static final Identifier LOOT = Identifier.fromNamespaceAndPath(ArienteWorld.MODID, "entities/sentinel_drone");

    private int sentinelId;
    private ChunkPos cityCenter;

    public SentinelDroneEntity(EntityType<? extends ArienteFlyingMob> type, Level worldIn) {
        super(type, worldIn);

        this.xpReward = 5;
        this.moveControl = new DroneMoveControl(this);
    }

    public static SentinelDroneEntity create(Level world, int sentinelId, ChunkPos cityCenter) {
        SentinelDroneEntity entity = new SentinelDroneEntity(ENTITY_SENTINEL_DRONE.get(), world);
        entity.sentinelId = sentinelId;
        entity.cityCenter = cityCenter;
        return entity;
    }

    @Override
    public ChunkPos getCityCenter() {
        return cityCenter;
    }

    @Override
    public boolean isImmuneToForcefield(IForceFieldTile tile) {
        return true;
    }

    // Override this to make it less likely to despawn
    @Override
    public void checkDespawn() {
        Entity entity = this.level().getNearestPlayer(this, -1.0D);

        if (entity != null) {
            double d0 = entity.getX() - this.getX();
            double d1 = entity.getY() - this.getY();
            double d2 = entity.getZ() - this.getZ();
            double d3 = d0 * d0 + d1 * d1 + d2 * d2;

            double closest = entity.distanceToSqr(this);
            if (this.removeWhenFarAway(closest) && d3 > 16384.0D) {
                this.remove(RemovalReason.DISCARDED);
            }

            if (this.noActionTime > 900 && this.random.nextInt(800) == 0 && d3 > 2048.0D && this.removeWhenFarAway(closest)) {
                this.remove(RemovalReason.DISCARDED);
            } else if (d3 < 2048.0D) {
                this.noActionTime = 0;
            }
        }
    }

    @Override
    public void setTarget(@Nullable LivingEntity entitylivingbaseIn) {
        // This is called by EntityAIFindEntityNearestPlayer when it spots a player.
        // In this case we don't attack but notify the city AI
        if (entitylivingbaseIn instanceof Player && cityCenter != null) {
            ICityAISystem aiSystem = ArienteWorld.arienteWorldImplementation.getCityAISystem(level());
            ICityAI cityAI = aiSystem.getCityAI(cityCenter);
            cityAI.playerSpotted((Player) entitylivingbaseIn);
            aiSystem.saveSystem();
        }
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new AICircleCity(this));
        this.goalSelector.addGoal(7, new AILookAround(this));
        this.targetSelector.addGoal(1, new EntityAIScanForPlayer(this));
    }

    /**
     * Called to update the entity's position/logic.
     */
    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide() && this.level().getDifficulty() == Difficulty.PEACEFUL) {
            this.remove(RemovalReason.DISCARDED);
        }
    }

    /**
     * Called when the entity is attacked.
     */
    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float amount) {
        if (this.isInvulnerableTo(serverLevel, source)) {
            return false;
        } else {
            return super.hurtServer(serverLevel, source, amount);
        }
    }

    public static AttributeSupplier.Builder registerAttributes() {
        AttributeSupplier.Builder attributes = LivingEntity.createLivingAttributes();
        attributes
            .add(Attributes.MAX_HEALTH, 10.0D)
            .add(Attributes.FOLLOW_RANGE, 50.0D); // Configurable

        return attributes;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.droneAmbient.get();
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return ModSounds.droneHurt.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.sentinelDeath.get();
    }

    /**
     * Returns the volume for the sounds this mob makes.
     */
    @Override
    protected float getSoundVolume() {
        return 1.0F;
    }

    // @todo 1.18 @Override
    public boolean checkSpawnRules(Level worldIn, EntitySpawnReason spawnReasonIn) {
        boolean b = (this.random.nextInt(100) == 0) && super.checkSpawnRules(worldIn, spawnReasonIn) && this.level().getDifficulty() != Difficulty.PEACEFUL;
        return b;
    }

    // @todo 1.18 @Override
    public boolean checkSpawnObstruction(BlockGetter worldIn) {
        return true;
    }

    /**
     * Will return how many at most can spawn in a chunk at once.
     */
    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    /**
     * (abstract) Protected helper method to write subclass entity data to NBT.
     */
    public void writeEntityData(CompoundTag compound) {

        if (cityCenter != null) {
            compound.putInt("cityX", cityCenter.x());
            compound.putInt("cityZ", cityCenter.z());
        }
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    public void readEntityData(CompoundTag compound) {

        if (compound.contains("cityX")) {
            cityCenter = new ChunkPos(compound.getIntOr("cityX", 0), compound.getIntOr("cityZ", 0));
        }
    }

    static class AILookAround extends Goal {
        private final SentinelDroneEntity parentEntity;

        public AILookAround(SentinelDroneEntity drone) {
            this.parentEntity = drone;
        }

        /**
         * Returns whether the EntityAIBase should begin execution.
         */
        @Override
        public boolean canUse() {
            return true;
        }

        /**
         * Updates the task
         */
        @Override
        public void tick() {
            if (this.parentEntity.getTarget() == null) {
                this.parentEntity.setYRot(-((float) Mth.atan2(this.parentEntity.getDeltaMovement().x, this.parentEntity.getDeltaMovement().z)) * (180F / (float) Math.PI));
                this.parentEntity.yBodyRot = this.parentEntity.getYRot();
            } else {
                LivingEntity entitylivingbase = this.parentEntity.getTarget();
                double d0 = 64.0D;

                if (entitylivingbase.distanceToSqr(this.parentEntity) < 4096.0D) {
                    double d1 = entitylivingbase.getX() - this.parentEntity.getX();
                    double d2 = entitylivingbase.getZ() - this.parentEntity.getZ();
                    this.parentEntity.setYRot(-((float) Mth.atan2(d1, d2)) * (180F / (float) Math.PI));
                    this.parentEntity.yBodyRot = this.parentEntity.getYRot();
                }
            }
        }
    }

    static class AICircleCity extends Goal {
        private final SentinelDroneEntity parentEntity;

        public AICircleCity(SentinelDroneEntity drone) {
            this.parentEntity = drone;
        }

        /**
         * Returns whether the EntityAIBase should begin execution.
         */
        @Override
        public boolean canUse() {
            if (!parentEntity.getNavigation().isDone()) {
                return false;
            }
            MoveControl controller = this.parentEntity.getMoveControl();

            if (!controller.hasWanted()) {
                return true;
            } else {
                double d0 = controller.getWantedX() - this.parentEntity.getX();
                double d1 = controller.getWantedY() - this.parentEntity.getY();
                double d2 = controller.getWantedZ() - this.parentEntity.getZ();
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                return d3 < 1.0D || d3 > 3600.0D;
            }
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        @Override
        public boolean canContinueToUse() {
            return false;
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        @Override
        public void start() {
            if (parentEntity.cityCenter == null) {
                return;
            }
            ICityAISystem aiSystem = ArienteWorld.arienteWorldImplementation.getCityAISystem(parentEntity.level());
            ICityAI cityAI = aiSystem.getCityAI(parentEntity.cityCenter);
            BlockPos pos = cityAI instanceof CityAI ai && ai.isLargeCube()
                    ? ai.requestCubePatrolPosition(parentEntity)
                    : cityAI.requestNewSentinelPosition(parentEntity.level(), parentEntity.sentinelId);
            if (pos != null) {
                if (DroneMoveControl.canFlyTo(parentEntity, Vec3.atBottomCenterOf(pos))) {
                    parentEntity.getMoveControl().setWantedPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 2.0D);
                } else {
                    parentEntity.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 2.0D);
                }
            }
        }
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        readEntityData(input.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseGet(CompoundTag::new));
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        CompoundTag tag = new CompoundTag();
        writeEntityData(tag);
        output.store(tag);
    }

}
