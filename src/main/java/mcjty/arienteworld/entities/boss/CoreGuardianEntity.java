package mcjty.arienteworld.entities.boss;

import javax.annotation.Nullable;
import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.api.IForceFieldTile;
import mcjty.ariente.api.IForcefieldImmunity;
import mcjty.ariente.items.armor.PersonalForcefield;
import mcjty.ariente.items.armor.PowerSuit;
import mcjty.ariente.items.modules.ModuleSupport;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.entities.drone.DroneMoveControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class CoreGuardianEntity extends FlyingMob implements IForcefieldImmunity, Enemy {

    public static final ResourceKey<DamageType> LIGHTNING_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "core_lightning"));
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(CoreGuardianEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VICTIM = SynchedEntityData.defineId(CoreGuardianEntity.class, EntityDataSerializers.INT);
    private static final int ATTACK_INTERVAL = 100;
    private static final int CHARGE_TIME = 30;
    private static final int PURSUIT_TIMEOUT = 20 * 20;
    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
    private BlockPos home;
    private ChunkPos cityCenter;
    private int attackCooldown = ATTACK_INTERVAL;
    private int lightningTicks;
    private LivingEntity pursuedTarget;
    private Vec3 lastSeenPosition;
    private int ticksWithoutShot;
    private int pathUpdateCooldown;

    public CoreGuardianEntity(EntityType<? extends CoreGuardianEntity> type, Level level) {
        super(type, level);
        moveControl = new DroneMoveControl(this);
        setPersistenceRequired();
        xpReward = 500;
    }

    public static AttributeSupplier.Builder registerAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 800)
                .add(Attributes.ARMOR, 12).add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    public void bindToCore(ChunkPos center, BlockPos core) {
        bindToCity(center, core.above(8));
    }

    public void bindToCity(ChunkPos center, BlockPos home) {
        cityCenter = center;
        this.home = home.immutable();
        setPos(Vec3.atBottomCenterOf(this.home));
    }

    @Nullable
    public BlockPos getHome() {
        return home;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flight = new FlyingPathNavigation(this, level) {

            @Override
            protected PathFinder createPathFinder(int maxNodes) {
                nodeEvaluator = new FlyNodeEvaluator() {

                    @Override
                    public int getNeighbors(Node[] neighbors, Node current) {
                        int count = super.getNeighbors(neighbors, current);
                        int accepted = 0;
                        for (int i = 0; i < count; i++) {
                            Node next = neighbors[i];
                            int changedAxes = (next.x == current.x ? 0 : 1) + (next.y == current.y ? 0 : 1)
                                    + (next.z == current.z ? 0 : 1);
                            if (changedAxes == 1) {
                                neighbors[accepted++] = next;
                            }
                        }
                        return accepted;
                    }
                };
                nodeEvaluator.setCanPassDoors(true);
                return new PathFinder(nodeEvaluator, maxNodes);
            }

            @Override
            protected void followThePath() {
                Vec3 position = getTempMobPos();
                // The sphere needs tight waypoints; vanilla's width-based tolerance cuts into corners.
                if (path != null && position.distanceToSqr(path.getNextEntityPos(mob)) < 0.25) {
                    path.advance();
                }
                doStuckDetection(position);
            }
        };
        flight.setCanFloat(true);
        return flight;
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                target -> home == null || target.distanceToSqr(Vec3.atCenterOf(home)) < 56 * 56));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGE, 0);
        builder.define(VICTIM, -1);
    }

    public int getCharge() {
        return entityData.get(CHARGE);
    }

    @Nullable
    public LivingEntity getLightningTarget() {
        return level().getEntity(entityData.get(VICTIM)) instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (home == null) {
            home = blockPosition();
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
        bossEvent.setName(getDisplayName());
        Vec3 origin = Vec3.atBottomCenterOf(home);
        if (pathUpdateCooldown > 0) {
            pathUpdateCooldown--;
        }
        LivingEntity candidate = getTarget();
        if (candidate != pursuedTarget && isValidTarget(candidate, origin) && getSensing().hasLineOfSight(candidate)) {
            pursuedTarget = candidate;
            lastSeenPosition = candidate.position();
            ticksWithoutShot = 0;
        }
        LivingEntity target = pursuedTarget;
        boolean valid = isValidTarget(target, origin);
        boolean visible = valid && distanceToSqr(target) < 64 * 64 && getSensing().hasLineOfSight(target);
        if (!valid || !visible && ticksWithoutShot >= PURSUIT_TIMEOUT) {
            pursuedTarget = null;
            lastSeenPosition = null;
            setTarget(null);
        } else {
            setTarget(target);
            ticksWithoutShot++;
            if (visible) {
                lastSeenPosition = target.position();
            }
        }
        if (!visible) {
            entityData.set(CHARGE, 0);
            entityData.set(VICTIM, -1);
            attackCooldown = Math.max(attackCooldown, CHARGE_TIME + 1);
        }

        // Keep ranged spacing in sight; search the last sighting behind cover before giving up.
        Vec3 destination = origin;
        if (visible && !hasSightFrom(origin, target.getEyePosition())) {
            // Hold a useful firing position instead of immediately flying back behind the same cover.
            destination = position();
        } else if (visible && target.distanceToSqr(origin) > 28 * 28) {
            Vec3 fromHome = target.position().add(0, 5, 0).subtract(origin);
            destination = origin.add(fromHome.normalize().scale(Math.min(40, Math.max(0, fromHome.length() - 24))));
        } else if (!visible && lastSeenPosition != null) {
            destination = findPursuitPosition(origin);
        }
        if (DroneMoveControl.canFlyTo(this, destination)) {
            navigation.stop();
            pathUpdateCooldown = 0;
            moveControl.setWantedPosition(destination.x, destination.y, destination.z, 1.8);
        } else if (pathUpdateCooldown == 0) {
            pathUpdateCooldown = 20;
            navigation.moveTo(destination.x, destination.y, destination.z, 1.8);
        }
        if (!visible) {
            return;
        }
        lookControl.setLookAt(target, 30, 30);
        entityData.set(VICTIM, target.getId());
        attackCooldown--;
        entityData.set(CHARGE, attackCooldown <= CHARGE_TIME ? CHARGE_TIME - attackCooldown + 1 : 0);
        if (attackCooldown == CHARGE_TIME) {
            level().playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2, 0.6f);
        }
        if (attackCooldown <= 0) {
            strike(target);
            attackCooldown = ATTACK_INTERVAL;
        }
    }

    private boolean isValidTarget(@Nullable LivingEntity target, Vec3 origin) {
        return target != null && !target.isRemoved() && target.isAlive() && target.level() == level()
                && canAttack(target) && target.distanceToSqr(origin) < 56 * 56;
    }

    private Vec3 findPursuitPosition(Vec3 origin) {
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        // Find space for the whole sphere near the remembered player, including beside or above cover.
        for (int height : new int[]{1, 5}) {
            for (int dx : new int[]{-4, 0, 4}) {
                for (int dz : new int[]{-4, 0, 4}) {
                    Vec3 point = lastSeenPosition.add(dx, height, dz);
                    if (point.distanceToSqr(origin) > 52 * 52 || point.distanceToSqr(position()) >= bestDistance
                            || !level().hasChunkAt(BlockPos.containing(point))
                            || !level().noCollision(this, getBoundingBox().move(point.subtract(position())))) {
                        continue;
                    }
                    if (hasSightFrom(point, lastSeenPosition.add(0, 1.5, 0))) {
                        best = point;
                        bestDistance = point.distanceToSqr(position());
                    }
                }
            }
        }
        if (best != null) {
            return best;
        }
        Vec3 fromHome = lastSeenPosition.add(0, 1, 0).subtract(origin);
        return origin.add(fromHome.normalize().scale(Math.min(52, fromHome.length())));
    }

    private boolean hasSightFrom(Vec3 position, Vec3 target) {
        return level().clip(new ClipContext(position.add(0, getEyeHeight(), 0), target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    public boolean strike(LivingEntity target) {
        if (!(level() instanceof ServerLevel server) || !target.isAlive() || !canAttack(target)
                || distanceToSqr(target) > 64 * 64 || !getSensing().hasLineOfSight(target)) {
            return false;
        }
        DamageSource source = new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(LIGHTNING_DAMAGE), this);
        // Ordinary armor cannot ground this overload. Working power armor and its field can.
        int upgrades = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack armor = target.getItemBySlot(slot);
                if (armor.getItem() instanceof PowerSuit && ModuleSupport.hasWorkingUpgrade(armor, ArmorUpgradeType.ARMOR)) {
                    upgrades++;
                }
            }
        }
        float damage = 30 * (1 - upgrades * 0.1f);
        if (PersonalForcefield.isActive(target)) {
            damage *= 2.0f / 3.0f;
        }
        boolean hit = target.hurt(source, damage);
        server.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 3, 0.8f);
        entityData.set(VICTIM, target.getId());
        server.broadcastEntityEvent(this, (byte) 61);
        if (target == pursuedTarget) {
            ticksWithoutShot = 0;
        }
        return hit;
    }

    public int getLightningTicks() {
        return lightningTicks;
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event == 61) {
            lightningTicks = 8;
        } else {
            super.handleEntityEvent(event);
        }
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel server && cityCenter != null) {
            CityAI ai = CityAISystem.getCityAISystem(server).getCityAI(cityCenter);
            if (ai.isGuardianDefeated() && isAlive()) {
                // Also reject an obsolete entity restored from an inconsistent backup after conquest.
                discard();
                return;
            }
            ai.guardianMoved(getUUID(), chunkPosition());
        }
        super.tick();
        if (lightningTicks > 0) {
            lightningTicks--;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server && cityCenter != null) {
            CityAISystem.getCityAISystem(server).getCityAI(cityCenter).guardianMoved(getUUID(), chunkPosition());
        }
        super.remove(reason);
    }

    @Override
    public void checkDespawn() {
        // A city has exactly one guardian, including across peaceful mode and chunk unloading.
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public boolean isImmuneToForcefield(IForceFieldTile tile) {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.LIGHTNING_BOLT) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.DROWN) || super.isInvulnerableTo(source);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!dead) {
            return;
        }
        if (level() instanceof ServerLevel server && cityCenter != null) {
            CityAISystem system = CityAISystem.getCityAISystem(server);
            system.getCityAI(cityCenter).guardianDefeated(getUUID());
        }
        bossEvent.removeAllPlayers();
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) {
            tag.put("Home", NbtUtils.writeBlockPos(home));
        }
        if (cityCenter != null) {
            tag.putInt("CityX", cityCenter.x);
            tag.putInt("CityZ", cityCenter.z);
        }
        tag.putInt("AttackCooldown", attackCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home = tag.contains("Home") ? NbtUtils.readBlockPos(tag, "Home").orElse(null) : null;
        cityCenter = tag.contains("CityX") ? new ChunkPos(tag.getInt("CityX"), tag.getInt("CityZ")) : null;
        attackCooldown = tag.contains("AttackCooldown") ? Math.max(CHARGE_TIME + 1, tag.getInt("AttackCooldown")) : ATTACK_INTERVAL;
        bossEvent.setName(getDisplayName());
    }
}
