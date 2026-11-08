package mcjty.arienteworld.entities.drone;

import mcjty.ariente.api.*;
import javax.annotation.Nullable;
import mcjty.ariente.entities.LaserEntity;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.ModSounds;
import mcjty.arienteworld.ai.CityAI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import static mcjty.arienteworld.setup.Registration.ENTITY_DRONE;

public class DroneEntity extends FlyingMob implements IForcefieldImmunity, IDrone {

    private static final EntityDataAccessor<Boolean> ATTACKING = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);

    public static final ResourceLocation LOOT = ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "entities/drone");

    // If this drone is controlled by a city then this will be set
    private ChunkPos cityCenter;

    public DroneEntity(EntityType<? extends FlyingMob> type, Level worldIn) {
        super(type, worldIn);
        this.xpReward = 5;
        this.moveControl = new DroneMoveControl(this);
    }

    public static DroneEntity create(Level world, ChunkPos cityCenter) {
        DroneEntity entity = new DroneEntity(ENTITY_DRONE.get(), world);
        entity.cityCenter = cityCenter;
        return entity;
    }

    @Override
    public ChunkPos getCityCenter() {
        return cityCenter;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(5, new AIDroneFly(this));
        this.goalSelector.addGoal(7, new AILookAround(this));
        this.goalSelector.addGoal(7, new AILaserAttack(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (p_213812_1_) -> {
            return Math.abs(p_213812_1_.getY() - this.getY()) <= 4.0D;
        }));
    }

    public boolean isAttacking() {
        return this.entityData.get(ATTACKING).booleanValue();
    }

    public void setAttacking(boolean attacking) {
        this.entityData.set(ATTACKING, Boolean.valueOf(attacking));
    }

    @Override
    public boolean isImmuneToForcefield(IForceFieldTile tile) {
        return true;
    }

    /**
     * Called to update the entity's position/logic.
     */
    @Override
    public void tick() {
        super.tick();

        if (!this.getCommandSenderWorld().isClientSide && this.getCommandSenderWorld().getDifficulty() == Difficulty.PEACEFUL) {
            this.remove(RemovalReason.DISCARDED);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACKING, Boolean.valueOf(false));
    }

    public static AttributeSupplier.Builder registerAttributes() {
        AttributeSupplier.Builder attributes = LivingEntity.createLivingAttributes();
        attributes
            .add(Attributes.MAX_HEALTH, 30.0D)
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
        return ModSounds.droneDeath.get();
    }

    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(Registries.LOOT_TABLE, LOOT);
    }

    /**
     * Returns the volume for the sounds this mob makes.
     */
    @Override
    protected float getSoundVolume() {
        return 1.0F;
    }

    // @todo 1.18 @Override
    public boolean checkSpawnRules(Level worldIn, MobSpawnType spawnReasonIn) {
        boolean b = (this.random.nextInt(100) == 0) && super.checkSpawnRules(worldIn, spawnReasonIn) && this.getCommandSenderWorld().getDifficulty() != Difficulty.PEACEFUL;
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

    @Override
    public void setTarget(@Nullable LivingEntity entitylivingbaseIn) {
        super.setTarget(entitylivingbaseIn);
        if (entitylivingbaseIn instanceof Player && cityCenter != null) {
            ICityAISystem aiSystem = ArienteWorld.arienteWorldImplementation.getCityAISystem(level());
            ICityAI cityAI = aiSystem.getCityAI(cityCenter);
            cityAI.playerSpotted((Player) entitylivingbaseIn);
            aiSystem.saveSystem();
        }
    }

    /**
     * (abstract) Protected helper method to write subclass entity data to NBT.
     */
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (cityCenter != null) {
            compound.putInt("cityX", cityCenter.x);
            compound.putInt("cityZ", cityCenter.z);
        }
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("cityX")) {
            cityCenter = new ChunkPos(compound.getInt("cityX"), compound.getInt("cityZ"));
        }
    }

    static class AILaserAttack extends Goal {
        private final DroneEntity drone;
        public int attackTimer;

        public AILaserAttack(DroneEntity drone) {
            this.drone = drone;
        }

        /**
         * Returns whether the EntityAIBase should begin execution.
         */
        @Override
        public boolean canUse() {
            return this.drone.getTarget() != null && this.drone.getTarget().isAlive();
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        @Override
        public void start() {
            this.attackTimer = 0;
        }

        /**
         * Resets the task
         */
        @Override
        public void stop() {
            this.drone.setAttacking(false);
        }

        /**
         * Updates the task
         */
        @Override
        public void tick() {
            LivingEntity target = this.drone.getTarget();
            double d0 = 64.0D;

            if (target.distanceToSqr(this.drone) < 4096.0D && this.drone.getSensing().hasLineOfSight(target)) {
                Level world = this.drone.getCommandSenderWorld();
                ++this.attackTimer;

                if (this.attackTimer == 10) {
                }

                if (this.attackTimer == 20) {
                    Vec3 center = drone.position().add(0, drone.getBbHeight() / 2.0D + 0.5D, 0);
                    Vec3 toTarget = target.getEyePosition().subtract(center);
                    Vec3 muzzle = center.add(toTarget.normalize().scale(Math.min(1.25D, toTarget.length() / 2.0D)));
                    Vec3 aim = target.getEyePosition().subtract(muzzle);
                    LaserEntity laser = LaserEntity.create(world, drone, aim.x, aim.y, aim.z);
                    laser.setPos(muzzle);

                    world.addFreshEntity(laser);
                    this.attackTimer = -40;
                }
            } else if (this.attackTimer > 0) {
                --this.attackTimer;
            }

            this.drone.setAttacking(this.attackTimer > 10);
        }
    }

    static class AILookAround extends Goal {
        private final DroneEntity parentEntity;

        public AILookAround(DroneEntity drone) {
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

    static class AIDroneFly extends Goal {
        private final DroneEntity parentEntity;

        public AIDroneFly(DroneEntity drone) {
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
                RandomSource random = this.parentEntity.getRandom();
                double d0 = this.parentEntity.getX() + ((random.nextFloat() * 2.0F - 1.0F) * 32.0F);
                double d1 = this.parentEntity.getY() + ((random.nextFloat() * 2.0F - 1.0F) * 32.0F);
                double d2 = this.parentEntity.getZ() + ((random.nextFloat() * 2.0F - 1.0F) * 32.0F);
                this.parentEntity.getMoveControl().setWantedPosition(d0, d1, d2, 1.0D);
            } else {
                // City controls movement
                ICityAISystem aiSystem = ArienteWorld.arienteWorldImplementation.getCityAISystem(parentEntity.level());
                ICityAI cityAI = aiSystem.getCityAI(parentEntity.cityCenter);
                BlockPos pos = cityAI instanceof CityAI ai && ai.isLargeCube()
                        ? ai.requestCubePatrolPosition(parentEntity)
                        : cityAI.requestNewDronePosition(parentEntity.level(), parentEntity.getTarget());
                if (pos != null) {
                    if (DroneMoveControl.canFlyTo(parentEntity, Vec3.atBottomCenterOf(pos))) {
                        parentEntity.getMoveControl().setWantedPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 2.0D);
                    } else {
                        parentEntity.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 2.0D);
                    }
                }
            }
        }
    }
}
