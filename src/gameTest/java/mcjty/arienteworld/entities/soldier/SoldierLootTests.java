package mcjty.arienteworld.entities.soldier;

import mcjty.ariente.items.KeyCardItem;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.config.LootConfiguration;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

import static mcjty.ariente.setup.Registration.ENERGY_LANCE;
import static mcjty.ariente.setup.Registration.ENERGY_SABRE;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class SoldierLootTests {

    @GameTest(template = "empty")
    public static void naturalWeaponDropsStayRareWithLootingAndPreserveGuaranteedEquipment(GameTestHelper helper) {
        helper.assertTrue(LootConfiguration.SOLDIER_WEAPON_DROP_MULTIPLIER.get() == .05, "The default weapon multiplier is one twentieth");
        for (var weapon : Arrays.asList(ENERGY_SABRE.get(), ENERGY_LANCE.get())) {
            for (int looting : new int[]{0, 3}) {
                RecordingSoldier soldier = new RecordingSoldier(helper.getLevel());
                soldier.getRandom().setSeed(48371 + looting);
                soldier.setDropChance(EquipmentSlot.MAINHAND, .085f);
                soldier.setDropChance(EquipmentSlot.HEAD, 2);
                for (int i = 0; i < 10000; i++) {
                    soldier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
                    soldier.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                    soldier.dropCustomDeathLoot(helper.getLevel().damageSources().generic(), looting, true);
                }
                helper.assertTrue(soldier.weaponDrops >= 15 && soldier.weaponDrops <= 100,
                        "Actual vanilla death-loot rolls must retain the twentyfold reduction with Looting: " + looting + "/" + soldier.weaponDrops
                                + "/" + LootConfiguration.SOLDIER_WEAPON_DROP_MULTIPLIER.get() + "/" + soldier.weaponChance());
                helper.assertTrue(soldier.armorDrops == 10000, "Weapon suppression must not interfere with armor drops");
                int before = soldier.weaponDrops;
                soldier.setDropChance(EquipmentSlot.MAINHAND, 2);
                soldier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
                soldier.dropCustomDeathLoot(helper.getLevel().damageSources().generic(), 3, false);
                helper.assertTrue(soldier.weaponDrops == before + 1,
                        "Player-provided guaranteed weapons must retain their normal drop guarantee");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidCityKeysSkipOnlyTheTaggedReward(GameTestHelper helper) {
        for (String key : Arrays.asList(null, "", "  ", "valid-city-key")) {
            CityAI city = new CityAI(helper.getLevel(), new ChunkPos(8, 8)) {

                @Override
                public String getKeyId() {
                    return key;
                }

                @Override
                public String getForcefieldId() {
                    return key;
                }
            };
            for (float roll : new float[]{0, .399f, .4f, .799f}) {
                ItemStack reward = SoldierEntity.createCityKeyCard(city, roll);
                if (key == null || key.isBlank()) {
                    helper.assertTrue(reward.isEmpty(), "An invalid tagged reward must be skipped without producing a blank card");
                } else {
                    helper.assertTrue(!reward.isEmpty() && KeyCardItem.getSecurityTags(reward).contains(key),
                            "Valid door and forcefield cards retain their security tag");
                }
            }
            ItemStack blank = SoldierEntity.createCityKeyCard(city, .8f);
            helper.assertTrue(!blank.isEmpty() && KeyCardItem.getSecurityTags(blank).isEmpty(),
                    "The independent blank-card roll is preserved");
        }
        helper.succeed();
    }

    private static class RecordingSoldier extends SoldierEntity {

        private int weaponDrops;
        private int armorDrops;

        private RecordingSoldier(ServerLevel level) {
            super(mcjty.arienteworld.setup.Registration.ENTITY_SOLDIER.get(), level);
        }

        private float weaponChance() {
            return getEquipmentDropChance(EquipmentSlot.MAINHAND);
        }

        @Override
        public ItemEntity spawnAtLocation(ItemStack stack, float offset) {
            if (stack.is(Items.IRON_HELMET)) {
                armorDrops++;
            } else {
                weaponDrops++;
            }
            return null;
        }
    }
}
