package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.api.SoldierBehaviourType;
import mcjty.ariente.entities.LaserEntity;
import mcjty.ariente.entities.drone.DroneEntity;
import mcjty.ariente.entities.soldier.SoldierEntity;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.CityPlan;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class DefenderCombatTests {

    private static final List<Difficulty> DIFFICULTIES = List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD);
    private static final Set<String> ADVANCED_PLANS = Set.of("city2_1", "city3_1", "city4_1", "city5_1");

    @GameTest(template = "empty")
    public static void sabresKillUnarmoredPlayersOnEveryDifficulty(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        try {
            for (Difficulty difficulty : DIFFICULTIES) {
                level.getServer().setDifficulty(difficulty, true);
                for (var type : List.of(Registration.ENTITY_SOLDIER.get(), Registration.ENTITY_MASTER_SOLDIER.get())) {
                    for (var weapon : List.of(Registration.ENERGY_SABRE.get(), Registration.ENHANCED_ENERGY_SABRE.get())) {
                        SoldierEntity soldier = type.create(level);
                        soldier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
                        Player unarmored = target(level, false, false);
                        Player armored = target(level, true, false);
                        try {
                            soldier.doHurtTarget(unarmored);
                            helper.assertTrue(unarmored.isDeadOrDying(), "A sabre must kill an unarmored player in one hit on " + difficulty);
                            soldier.doHurtTarget(armored);
                            helper.assertTrue(armored.isAlive(), "Ordinary soldiers and masters must allow plain netherite to survive one hit");
                        } finally {
                            soldier.discard();
                            unarmored.discard();
                            armored.discard();
                        }
                    }
                }
                SoldierEntity unarmed = Registration.ENTITY_SOLDIER.get().create(level);
                Player player = target(level, false, false);
                try {
                    unarmed.doHurtTarget(player);
                    helper.assertTrue(player.isAlive(), "The lethal sabre rule must not apply to unarmed attacks");
                } finally {
                    unarmed.discard();
                    player.discard();
                }
            }
        } finally {
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void advancedDroneLasersKillPlainNetheriteButRespectEnchantments(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        Difficulty previous = level.getDifficulty();
        try {
            for (CityPlan plan : AssetRegistries.CITYPLANS.getIterable()) {
                if (!plan.isCity()) {
                    continue;
                }
                CityPlan exported = new CityPlan(plan.writeToJSon());
                helper.assertTrue(exported.getSoldierSabreDamage() == plan.getSoldierSabreDamage()
                                && exported.getDroneLaserDamage() == plan.getDroneLaserDamage(),
                        "Editor export must preserve defender strength");
                ChunkPos center = findCity(level.getSeed(), plan);
                DroneEntity original = DroneEntity.create(level, center);
                CompoundTag saved = new CompoundTag();
                original.saveWithoutId(saved);
                DroneEntity restored = Registration.ENTITY_DRONE.get().create(level);
                restored.load(saved);
                try {
                    for (Difficulty difficulty : DIFFICULTIES) {
                        level.getServer().setDifficulty(difficulty, true);
                        for (DroneEntity drone : List.of(original, restored)) {
                            LaserEntity laser = LaserEntity.create(level, drone, 1, 0, 0);
                            Player player = target(level, true, false);
                            Player protectedPlayer = target(level, true, true);
                            try {
                                player.hurt(level.damageSources().indirectMagic(laser, drone), 10.0F);
                                protectedPlayer.hurt(level.damageSources().indirectMagic(laser, drone), 10.0F);
                                helper.assertTrue(player.isDeadOrDying() == ADVANCED_PLANS.contains(plan.getName()),
                                        "Only advanced-base drones must kill plain netherite in one shot: " + plan.getName() + "/" + difficulty);
                                helper.assertTrue(protectedPlayer.isAlive(), "Protection IV must still mitigate advanced drone lasers");
                            } finally {
                                laser.discard();
                                player.discard();
                                protectedPlayer.discard();
                            }
                        }
                        SoldierEntity guard = SoldierEntity.create(level, center, SoldierBehaviourType.SOLDIER_GUARD);
                        Player player = target(level, true, false);
                        try {
                            guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Registration.ENERGY_SABRE.get()));
                            guard.doHurtTarget(player);
                            helper.assertTrue(player.isAlive(), "Even advanced foot guards must let plain netherite survive one hit");
                        } finally {
                            guard.discard();
                            player.discard();
                        }
                    }
                } finally {
                    original.discard();
                    restored.discard();
                }
            }
        } finally {
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    private static ChunkPos findCity(long seed, CityPlan plan) {
        for (int i = 0; i < 1000; i++) {
            ChunkPos center = new ChunkPos(32008 + i * 16, 32008);
            if (CityTools.isCityCenter(seed, center.x, center.z) && CityTools.getCity(seed, center).getPlan() == plan) {
                return center;
            }
        }
        throw new AssertionError("No city for " + plan.getName());
    }

    private static Player target(ServerLevel level, boolean netherite, boolean enchanted) {
        Player player = new Player(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "defender-target")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        if (netherite) {
            for (var entry : List.of(
                    Map.entry(EquipmentSlot.HEAD, Items.NETHERITE_HELMET),
                    Map.entry(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE),
                    Map.entry(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS),
                    Map.entry(EquipmentSlot.FEET, Items.NETHERITE_BOOTS))) {
                ItemStack armor = new ItemStack(entry.getValue());
                if (enchanted) {
                    armor.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
                }
                player.setItemSlot(entry.getKey(), armor);
                player.getAttributes().addTransientAttributeModifiers(armor.getAttributeModifiers(entry.getKey()));
            }
        }
        return player;
    }
}
