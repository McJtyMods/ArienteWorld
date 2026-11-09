package mcjty.arienteworld.mixin;

import mcjty.ariente.items.armor.DamageContext;
import mcjty.arienteworld.dimension.DefenderCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {

    @ModifyVariable(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), argsOnly = true)
    private float arienteworld$defenderDamage(float damage, DamageSource source, float original) {
        var event = new DamageContext((LivingEntity) (Object) this, source, damage);
        DefenderCombat.onSoldierSabreAttack(event);
        DefenderCombat.onSoldierLanceAttack(event);
        DefenderCombat.hurt(event);
        return event.getNewDamage();
    }
}
