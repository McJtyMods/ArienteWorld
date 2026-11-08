package mcjty.arienteworld.entities.soldier;

import mcjty.ariente.api.IInhibitable;
import mcjty.ariente.entities.LaserEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import static mcjty.arienteworld.setup.Registration.ENTITY_MASTER_SOLDIER;

public class MasterSoldierEntity extends SoldierEntity implements IInhibitable {

    private int noregenCounter = 0;

    public MasterSoldierEntity(EntityType<? extends Monster> type, Level worldIn) {
        super(type, worldIn);
        // @todo 1.14 move to type
    }

    public static MasterSoldierEntity create(Level world, ChunkPos cityCenter, SoldierBehaviourType behaviourType) {
        MasterSoldierEntity entity = new MasterSoldierEntity(ENTITY_MASTER_SOLDIER.get(), world);
        entity.cityCenter = cityCenter;
        entity.behaviourType = behaviourType;
        return entity;
    }

    public static AttributeSupplier.Builder registerAttributes() {
        AttributeSupplier.Builder attributes = Monster.createMonsterAttributes();
        attributes
            .add(Attributes.FOLLOW_RANGE, 35.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.32D)
            .add(Attributes.MAX_HEALTH, 150.0D)
            .add(Attributes.ATTACK_DAMAGE, 8.0D)
            .add(Attributes.ARMOR, 6.0D);

        return attributes;
    }

    @Override
    public void tick() {
        super.tick();

        if (noregenCounter > 0) {
            noregenCounter--;
        } else {
            MobEffectInstance effect = getEffect(MobEffects.REGENERATION);
            if (effect == null || effect.getDuration() <= 0) {
                addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 3, false, false));
            }
        }
    }

    @Override
    public void inhibitRegeneration(int ticks) {
        this.noregenCounter = ticks;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        boolean rc = super.isInvulnerableTo(source);
        if (rc) {
            return rc;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return true;
        }
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            // Lance shots use magic damage, but must still hurt master soldiers.
            return !(source.getDirectEntity() instanceof LaserEntity laser && laser.isLanceShot());
        }
        return false;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        noregenCounter = compound.getInt("noregen");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("noregen", noregenCounter);
    }

    @Override
    protected boolean isMaster() {
        return true;
    }
}
