package mcjty.arienteworld;

import com.google.gson.JsonParser;
import mcjty.ariente.blocks.utility.StorageTile;
import mcjty.ariente.items.BlueprintItem;
import mcjty.ariente.recipes.BlueprintRecipeRegistry;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.CityPlan;
import mcjty.arienteworld.cities.Loot;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.dimension.CityFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class LootBalanceTests {

    @GameTest(template = "empty")
    public static void cityBuildingCachesReceiveLootWithoutMetadata(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("landscapecities");
        int rewards = 0;
        int caches = 0;
        for (int building : new int[]{1, 2, 6, 7, 13}) {
            BlockPos origin = new BlockPos(180000 + building * 32, 160, 180000);
            var part = AssetRegistries.PARTS.get("building" + building);
            CityFeature.placePart(helper.getLevel(), origin, part, palette);
            for (int y = 0; y < part.getSliceCount(); y++) {
                for (int x = 0; x < part.getXSize(); x++) {
                    for (int z = 0; z < part.getZSize(); z++) {
                        BlockPos local = new BlockPos(x, y, z);
                        if (helper.getLevel().getBlockEntity(origin.offset(local)) instanceof StorageTile storage) {
                            helper.assertTrue(!part.getTeInfo().containsKey(local), "Exercise the metadata-free authored caches");
                            helper.assertTrue(!storage.isLocked(), "Landscape caches have no city core or storage key");
                            caches++;
                            for (int slot = 0; slot < 4; slot++) {
                                if (!storage.getTotalStack(slot).isEmpty()) {
                                    rewards++;
                                }
                            }
                        }
                    }
                }
            }
        }
        helper.assertTrue(caches == 6 && rewards > 0, "Authored city caches must actually receive their loot pool");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryLootCanReachConfiguredMaximum(GameTestHelper helper) {
        CityPlan plan = new CityPlan("quantity_check");
        plan.parseLoot(JsonParser.parseString("[{\"id\":\"ariente:ingot_silver\",\"max\":2,\"chance\":1}]").getAsJsonArray());
        boolean maximum = false;
        for (int i = 0; i < 100; i++) {
            StorageTile storage = cache(helper, i);
            CityAI.fillLoot(plan, storage);
            for (int slot = 0; slot < 4; slot++) {
                int amount = storage.getTotalStack(slot).getCount();
                helper.assertTrue(amount >= 0 && amount <= 2, "Ordinary rewards must respect the configured quantity bound");
                maximum |= amount == 2;
            }
        }
        helper.assertTrue(maximum, "The configured maximum must be obtainable in ordinary caches too");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void armorBlueprintsOutnumberFinishedArmor(GameTestHelper helper) {
        List<String> armor = List.of("powersuit_head", "powersuit_chest", "powersuit_legs", "powersuit_feet");
        for (String name : List.of("city1_2", "city2_1", "city3_1", "city4_1", "city5_1")) {
            CityPlan plan = AssetRegistries.CITYPLANS.get(name);
            Map<String, Integer> blueprints = new HashMap<>();
            Map<String, Integer> equipment = new HashMap<>();
            for (int i = 0; i < 4000; i++) {
                StorageTile storage = cache(helper, i);
                CityAI.fillLoot(plan, storage);
                for (int slot = 0; slot < 4; slot++) {
                    ItemStack stack = storage.getTotalStack(slot);
                    boolean blueprint = stack.is(Registration.BLUEPRINT.get());
                    ItemStack destination = blueprint ? BlueprintItem.getDestination(stack) : stack;
                    if (blueprint) {
                        helper.assertTrue(BlueprintRecipeRegistry.findRecipe(destination) != null, "Loot blueprints must be craftable");
                    }
                    for (String piece : armor) {
                        if (destination.is(ForgeRegistries.ITEMS.getValue(new ResourceLocation("ariente", piece)))) {
                            (blueprint ? blueprints : equipment).merge(piece, 1, Integer::sum);
                        }
                    }
                }
            }
            for (String piece : armor) {
                int crafted = blueprints.getOrDefault(piece, 0);
                int ready = equipment.getOrDefault(piece, 0);
                helper.assertTrue(ready > 0 && crafted > ready * 1.4 && crafted < ready * 2.7,
                        name + " must favor discovering each armor blueprint over finished armor: " + piece + " " + crafted + "/" + ready);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rareWeaponsImproveWithDungeonTier(GameTestHelper helper) {
        double previous = 0;
        for (String name : List.of("city0_1", "city1_1", "city1_2", "city2_1", "city3_1", "city4_1", "city5_1")) {
            CityPlan plan = AssetRegistries.CITYPLANS.get(name);
            double total = plan.getLoot().stream().mapToDouble(Loot::getChance).sum();
            double ready = 0;
            double blueprint = 0;
            for (Loot loot : plan.getLoot()) {
                if (new ResourceLocation("ariente", "energy_lance").equals(loot.getId())) {
                    if (loot.isBlueprint()) {
                        blueprint += loot.getChance();
                    } else {
                        ready += loot.getChance();
                    }
                }
            }
            double probability = ready / total;
            helper.assertTrue(probability + 0.000001 >= previous, "Larger loot pools must not dilute rare weapons below earlier tiers");
            helper.assertTrue(blueprint > ready * 1.99 && blueprint < ready * 2.01, "Lance blueprints must be twice as common as finished lances");
            previous = probability;
        }
        helper.succeed();
    }

    private static StorageTile cache(GameTestHelper helper, int index) {
        StorageTile storage = new StorageTile(helper.absolutePos(new BlockPos(index, 2, 0)), Registration.STORAGE.get().defaultBlockState());
        storage.setLevel(helper.getLevel());
        return storage;
    }
}
