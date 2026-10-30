package mcjty.arienteworld;

import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.items.armor.PowerSuitEffects;
import mcjty.ariente.items.modules.ModuleSupport;
import mcjty.ariente.setup.Registration;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class EquipmentRegressionTests {

    @GameTest(template = "empty")
    public static void equippedIdentityAndFuelExhaustion(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack head = gear(Registration.POWERSUIT_HEAD.get(), ArmorUpgradeType.SCRAMBLE);
        head.getTag().putInt("power", 1);
        ItemStack backpack = head.copy();
        tick(backpack, player, EquipmentSlot.HEAD.getIndex());
        helper.assertTrue(!ModuleSupport.hasWorkingUpgrade(backpack, ArmorUpgradeType.SCRAMBLE)
                && backpack.getTag().getInt("power") == 1, "Backpack copies must not consume fuel or enable effects");
        player.setItemSlot(EquipmentSlot.HEAD, head);
        tick(head, player, EquipmentSlot.HEAD.getIndex());
        helper.assertTrue(ModuleSupport.hasWorkingUpgrade(head, ArmorUpgradeType.SCRAMBLE), "Last powered tick must work");
        tick(head, player, EquipmentSlot.HEAD.getIndex());
        helper.assertTrue(!ModuleSupport.hasWorkingUpgrade(head, ArmorUpgradeType.SCRAMBLE), "Exhaustion must clear Scramble");
        head.getTag().putInt("power", 3);
        tick(head, player, EquipmentSlot.HEAD.getIndex());
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        tick(head, player, 3);
        helper.assertTrue(!ModuleSupport.hasWorkingUpgrade(head, ArmorUpgradeType.SCRAMBLE), "Unequipped gear must clear working state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void atomicMixedAutofeed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack chest = gear(Registration.POWERSUIT_CHEST.get(), ArmorUpgradeType.FLIGHT, ArmorUpgradeType.AUTOFEED);
        chest.getTag().putInt("power", 0);
        ItemStack negarite = holder(3, 0);
        ItemStack posirite = holder(0, 4);
        player.getInventory().setItem(9, negarite);
        helper.assertTrue(!ModuleSupport.managePower(chest, player) && negarite.getTag().getInt("negarite") == 3,
                "Missing second component must not consume the first");
        player.getInventory().setItem(10, posirite);
        helper.assertTrue(ModuleSupport.managePower(chest, player)
                        && negarite.getTag().getInt("negarite") == 2 && posirite.getTag().getInt("posirite") == 3,
                "Each component must be drawn from its own holder");
        chest.getTag().putInt("power", 0);
        chest.getTag().putInt("negarite", 5);
        helper.assertTrue(ModuleSupport.managePower(chest, player) && chest.getTag().getInt("negarite") == 4
                        && negarite.getTag().getInt("negarite") == 2 && posirite.getTag().getInt("posirite") == 2,
                "Autofeed must supply only the missing component and retain surplus local fuel");
        chest.getTag().putInt("power", 0);
        chest.getTag().putInt("negarite", 0);
        player.getInventory().setItem(9, holder(2, 2));
        player.getInventory().setItem(10, ItemStack.EMPTY);
        helper.assertTrue(ModuleSupport.managePower(chest, player)
                        && player.getInventory().getItem(9).getTag().getInt("negarite") == 1
                        && player.getInventory().getItem(9).getTag().getInt("posirite") == 1,
                "One holder may supply both components");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void budgetToggleAndHotkeyState(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack chest = gear(Registration.POWERSUIT_CHEST.get(), ArmorUpgradeType.FLIGHT,
                ArmorUpgradeType.FORCEFIELD, ArmorUpgradeType.HOVER);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        tick(chest, player, 2);
        helper.assertTrue(!ModuleSupport.hasWorkingUpgrade(chest, ArmorUpgradeType.FLIGHT)
                        && chest.getTag().getInt("power") == 10, "Over-budget gear must stop without consuming fuel");
        chest.getTag().putBoolean(ArmorUpgradeType.ENERGY.getModuleKey(), true);
        tick(chest, player, 2);
        helper.assertTrue(ModuleSupport.hasWorkingUpgrade(chest, ArmorUpgradeType.FLIGHT), "Optimizer must permit a 120-usage chest");
        chest.getTag().putInt(ArmorUpgradeType.FLIGHT.getHotkeyKey(), 1);
        chest.getTag().putInt(ArmorUpgradeType.SPEED.getHotkeyKey(), 1);
        ModuleSupport.receivedHotkey(player, 1);
        helper.assertTrue(!ModuleSupport.hasWorkingUpgrade(chest, ArmorUpgradeType.FLIGHT)
                        && !chest.getTag().contains(ArmorUpgradeType.SPEED.getModuleKey()), "Hotkey must stop disabled modules and never install absent modules");
        ModuleSupport.receivedHotkey(player, 0);
        helper.assertTrue(!chest.getTag().contains(ArmorUpgradeType.SCRAMBLE.getModuleKey()), "Group zero must be rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sabreHeldSlotAndEnchantments(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack sabre = gear(Registration.ENHANCED_ENERGY_SABRE.get(), ArmorUpgradeType.LOOTING, ArmorUpgradeType.FIRE, ArmorUpgradeType.POWER);
        sabre.enchant(Enchantments.UNBREAKING, 2);
        sabre.enchant(Enchantments.MOB_LOOTING, 1);
        player.getInventory().selected = 5;
        player.getInventory().setItem(5, sabre);
        tick(sabre, player, 5);
        helper.assertTrue(ModuleSupport.hasWorkingUpgrade(sabre, ArmorUpgradeType.POWER)
                        && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MOB_LOOTING, sabre) == 3
                        && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, sabre) == 2,
                "A sabre held in hotbar slot 5 must work without erasing unrelated enchants");
        player.getInventory().setItem(5, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, sabre);
        tick(sabre, player, 0);
        helper.assertTrue(!ModuleSupport.hasWorkingUpgrade(sabre, ArmorUpgradeType.POWER)
                        && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MOB_LOOTING, sabre) == 1
                        && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, sabre) == 0
                        && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, sabre) == 2,
                "Offhand must stop modules and restore pre-module enchants");
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        player.getInventory().setItem(5, sabre);
        sabre.getTag().putInt("power", 1);
        tick(sabre, player, 5);
        tick(sabre, player, 5);
        helper.assertTrue(!ModuleSupport.hasWorkingUpgrade(sabre, ArmorUpgradeType.POWER)
                        && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MOB_LOOTING, sabre) == 1,
                "Fuel loss must remove only module-supplied enchantment levels");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void suitEffectOwnership(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack head = gear(Registration.POWERSUIT_HEAD.get(), ArmorUpgradeType.NIGHTVISION);
        player.setItemSlot(EquipmentSlot.HEAD, head);
        tick(head, player, 3);
        PowerSuitEffects.tick(player);
        helper.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION), "Powered helmet must supply night vision");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        PowerSuitEffects.tick(player);
        helper.assertTrue(!player.hasEffect(MobEffects.NIGHT_VISION), "Unequipping must remove suit-owned night vision");
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0));
        player.setItemSlot(EquipmentSlot.HEAD, head);
        PowerSuitEffects.tick(player);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        PowerSuitEffects.tick(player);
        helper.assertTrue(player.getEffect(MobEffects.NIGHT_VISION).getDuration() == 1200,
                "Equipment changes must preserve effects supplied by potions");
        player.removeAllEffects();
        player.setItemSlot(EquipmentSlot.HEAD, head);
        PowerSuitEffects.tick(player);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1400, 2));
        PowerSuitEffects.clear(player);
        helper.assertTrue(player.getEffect(MobEffects.NIGHT_VISION).getAmplifier() == 2,
                "Logout cleanup must preserve a stronger effect that replaced the suit effect");
        player.removeAllEffects();
        helper.succeed();
    }

    private static ItemStack gear(Item item, ArmorUpgradeType... modules) {
        ItemStack stack = new ItemStack(item);
        for (ArmorUpgradeType module : modules) {
            stack.getOrCreateTag().putBoolean(module.getModuleKey(), true);
        }
        stack.getOrCreateTag().putInt("power", 10);
        return stack;
    }

    private static ItemStack holder(int negarite, int posirite) {
        ItemStack stack = new ItemStack(Registration.ENERGY_HOLDER.get());
        stack.getOrCreateTag().putInt("negarite", negarite);
        stack.getTag().putInt("posirite", posirite);
        return stack;
    }

    private static void tick(ItemStack stack, Player player, int slot) {
        stack.getItem().inventoryTick(stack, player.level(), player, slot, false);
    }
}
