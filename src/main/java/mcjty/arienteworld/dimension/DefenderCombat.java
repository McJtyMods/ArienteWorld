package mcjty.arienteworld.dimension;

import mcjty.ariente.entities.LaserEntity;
import mcjty.ariente.entities.drone.DroneEntity;
import mcjty.ariente.entities.soldier.SoldierEntity;
import mcjty.ariente.items.EnergySabreItem;
import mcjty.ariente.items.EnergyLanceItem;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArienteWorld.MODID)
public class DefenderCombat {

    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity defender)
                || !(defender.level() instanceof ServerLevel level)
                || !level.dimension().equals(DimensionRegister.DIMENSION)
                || !(level.getChunkSource().getGenerator() instanceof ArienteChunkGenerator)) {
            return;
        }
        boolean laser = event.getSource().getDirectEntity() instanceof LaserEntity && defender instanceof DroneEntity;
        boolean sabre = event.getSource().is(DamageTypes.MOB_ATTACK) && defender instanceof SoldierEntity
                && defender.getMainHandItem().getItem() instanceof EnergySabreItem;
        boolean lance = event.getSource().getDirectEntity() instanceof LaserEntity shot && shot.isLanceShot()
                && defender instanceof SoldierEntity;
        if (!laser && !sabre && !lance) {
            return;
        }
        ChunkPos center = defender instanceof SoldierEntity soldier ? soldier.getCityCenter() : ((DroneEntity) defender).getCityCenter();
        // City defenders retain their strength throughout their patrol and pursuit range.
        City city = center == null ? SoldierSpawning.getDungeonAt(level, defender.blockPosition()) : CityTools.getCity(level.getSeed(), center);
        if (city != null) {
            float damage = laser ? city.getPlan().getDroneLaserDamage() : city.getPlan().getSoldierSabreDamage();
            if (lance) {
                damage *= EnergyLanceItem.DAMAGE_MULTIPLIER;
            }
            event.setAmount(Math.max(event.getAmount(), damage));
        }
    }
}
