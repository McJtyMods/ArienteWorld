package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.entities.LaserEntity;
import mcjty.ariente.entities.fluxship.FluxShipLaser;
import mcjty.ariente.items.EnergyLanceItem;
import mcjty.ariente.items.armor.PersonalForcefield;
import mcjty.ariente.items.modules.ModuleSupport;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.CityPlan;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.entities.drone.DroneEntity;
import mcjty.arienteworld.entities.soldier.SoldierBehaviourType;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

import static mcjty.arienteworld.setup.Registration.ENTITY_DARKBAT;
import static mcjty.arienteworld.setup.Registration.ENTITY_DRONE;
import static mcjty.arienteworld.setup.Registration.ENTITY_MASTER_SOLDIER;
import static mcjty.arienteworld.setup.Registration.ENTITY_SOLDIER;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class PowerArmorBalanceTests {

    @GameTest(template = "empty")
    public static void fullPoweredArmorSurvivesEveryExistingEnemyWithoutEnchantments(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        Difficulty previous = level.getDifficulty();
        ChunkPos center = findAdvancedCity(level.getSeed());
        SoldierEntity normal = ENTITY_SOLDIER.get().create(level);
        SoldierEntity master = ENTITY_MASTER_SOLDIER.get().create(level);
        SoldierEntity advanced = SoldierEntity.create(level, center, SoldierBehaviourType.SOLDIER_GUARD);
        DroneEntity drone = ENTITY_DRONE.get().create(level);
        DroneEntity advancedDrone = DroneEntity.create(level, center);
        Mob bat = ENTITY_DARKBAT.get().create(level);
        try {
            for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
                level.getServer().setDifficulty(difficulty, true);
                // Verify both independent armor protection and a forcefield weakened by another hit.
                for (boolean field : List.of(false, true)) {
                    Player player = target(level, field);
                    try {
                        for (SoldierEntity soldier : List.of(normal, master, advanced)) {
                            ItemStack sabre = new ItemStack(Registration.ENERGY_SABRE.get());
                            soldier.setItemSlot(EquipmentSlot.MAINHAND, sabre);
                            soldier.getAttributes().addTransientAttributeModifiers(sabre.getAttributeModifiers(EquipmentSlot.MAINHAND));
                            reset(player, field);
                            soldier.doHurtTarget(player);
                            float damage = assertSurvives(helper, player, "sabre", difficulty);
                            if (soldier == normal && !field) {
                                helper.assertTrue(damage >= 4 && damage <= 6,
                                        "Normal soldier melee must deal 2-3 hearts through full powered armor");
                            }
                            soldier.getAttributes().removeAttributeModifiers(sabre.getAttributeModifiers(EquipmentSlot.MAINHAND));
                            soldier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Registration.ENERGY_LANCE.get()));
                            reset(player, field);
                            laserHit(player, soldier, EnergyLanceItem.getShotDamage(soldier), false, true);
                            assertSurvives(helper, player, "lance", difficulty);
                        }
                        for (DroneEntity attacker : List.of(drone, advancedDrone)) {
                            reset(player, field);
                            laserHit(player, attacker, 10, false, false);
                            assertSurvives(helper, player, "drone", difficulty);
                        }
                        reset(player, field);
                        laserHit(player, normal, FluxShipLaser.getShotDamage(normal), true, false);
                        assertSurvives(helper, player, "scout cannon", difficulty);
                        reset(player, field);
                        bat.doHurtTarget(player);
                        assertSurvives(helper, player, "darkbat", difficulty);
                    } finally {
                        player.discard();
                    }
                }
                verifyScoutReactionTime(helper, level, normal, difficulty);
            }
        } finally {
            level.getServer().setDifficulty(previous, true);
            for (LivingEntity entity : List.of(normal, master, advanced, drone, advancedDrone, bat)) {
                entity.discard();
            }
        }
        helper.succeed();
    }

    private static void verifyScoutReactionTime(GameTestHelper helper, ServerLevel level, SoldierEntity scout, Difficulty difficulty) {
        Player player = target(level, false);
        player.setHealth(20);
        int shots = 0;
        try {
            while (player.isAlive() && shots < 30) {
                laserHit(player, scout, FluxShipLaser.getShotDamage(scout), true, false);
                shots++;
                if (shots == 5) {
                    helper.assertTrue(player.isAlive(),
                            "A full suit must survive the first second of accurate scout fire even on Hard");
                }
            }
            helper.assertTrue(shots >= 7 && player.isDeadOrDying(),
                    "Scouts must allow reaction time but remain lethal if every shot keeps landing");
            ArienteWorld.setup.getLogger().info("POWER ARMOR BALANCE: {} scout burst lethal after {} hits ({} seconds from first hit, no healing)",
                    difficulty, shots, (shots - 1) * FluxShipLaser.SHOT_INTERVAL / 20.0);
        } finally {
            player.discard();
        }
    }

    private static void reset(Player player, boolean coolingField) {
        player.setHealth(200);
        player.invulnerableTime = 0;
        PersonalForcefield.updateState(player, coolingField ? player.level().getGameTime() + 100 : 0, 0);
    }

    private static float assertSurvives(GameTestHelper helper, Player player, String attack, Difficulty difficulty) {
        float damage = 200 - player.getHealth();
        helper.assertTrue(damage > 0 && damage < 20,
                "Full powered armor must take damage but survive one " + attack + " hit on " + difficulty + ": " + damage);
        return damage;
    }

    private static void laserHit(Player player, LivingEntity attacker, float damage, boolean ship, boolean lance) {
        ServerLevel level = (ServerLevel) player.level();
        LaserEntity laser = ship ? LaserEntity.createShipShot(level, attacker, Vec3.ZERO, new Vec3(0, 0, 1), damage)
                : lance ? LaserEntity.createLanceShot(level, attacker, new Vec3(0, 0, 1), damage)
                : LaserEntity.create(level, attacker, 0, 0, 1);
        try {
            DamageSource source = ship ? new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(LaserEntity.SHIP_DAMAGE), laser, attacker) : level.damageSources().indirectMagic(laser, attacker);
            if (ship && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
                throw new AssertionError("Scout balance must include every rapid hit, without ordinary damage immunity");
            }
            player.hurt(source, damage);
        } finally {
            laser.discard();
        }
    }

    private static Player target(ServerLevel level, boolean forcefield) {
        Player player = new Player(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "power-armor-target")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        ItemStack holder = new ItemStack(Registration.ENERGY_HOLDER.get());
        holder.getOrCreateTag().putInt("negarite", 1000);
        holder.getOrCreateTag().putInt("posirite", 1000);
        player.getInventory().setItem(9, holder);
        List<ItemStack> armor = List.of(new ItemStack(Registration.POWERSUIT_FEET.get()), new ItemStack(Registration.POWERSUIT_LEGS.get()),
                new ItemStack(Registration.POWERSUIT_CHEST.get()), new ItemStack(Registration.POWERSUIT_HEAD.get()));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                continue;
            }
            ItemStack stack = armor.get(slot.getIndex());
            stack.getOrCreateTag().putBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
            if (slot == EquipmentSlot.CHEST && forcefield) {
                stack.getTag().putBoolean(ArmorUpgradeType.FORCEFIELD.getModuleKey(), true);
            }
            player.setItemSlot(slot, stack);
            ModuleSupport.updateWorkingState(stack, player);
            player.getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(slot));
        }
        return player;
    }

    private static ChunkPos findAdvancedCity(long seed) {
        CityPlan plan = AssetRegistries.CITYPLANS.get("city2_1");
        for (int i = 0; i < 1000; i++) {
            ChunkPos center = new ChunkPos(32008 + i * 16, 32008);
            if (CityTools.isCityCenter(seed, center.x, center.z) && CityTools.getCity(seed, center).getPlan() == plan) {
                return center;
            }
        }
        throw new AssertionError("No advanced city for combat verification");
    }
}
