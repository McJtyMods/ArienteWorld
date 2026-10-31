package mcjty.arienteworld;

import mcjty.ariente.Ariente;
import mcjty.ariente.api.MarbleColor;
import mcjty.ariente.blocks.decorative.DecorativeBlockModule;
import mcjty.ariente.config.Config;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import mcjty.ariente.blocks.utility.AutoConstructorTile;
import mcjty.ariente.blocks.utility.ConstructorTile;
import mcjty.ariente.blocks.utility.BlueprintStorageTile;
import mcjty.ariente.config.WorldgenConfiguration;
import mcjty.ariente.config.YesNoDefault;
import mcjty.ariente.items.BlueprintItem;
import mcjty.ariente.oregen.WorldGen;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import mcjty.ariente.power.IPowerBlob;
import mcjty.ariente.power.PowerSystem;
import mcjty.ariente.power.PowerType;
import mcjty.ariente.recipes.BlueprintRecipeRegistry;
import mcjty.ariente.recipes.ConstructorCrafting;
import mcjty.ariente.recipes.ConstructorRecipe;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class ProgressionRegressionTests {

    @GameTest(template = "empty")
    public static void oreDropsAndProcessing(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        Block[] ores = {Registration.ORE_LITHIUM.get(), Registration.ORE_MANGANESE.get(), Registration.ORE_PLATINUM.get(),
                Registration.ORE_SILVER.get(), Registration.ORE_NEGARITE.get(), Registration.ORE_POSIRITE.get()};
        Item[] outputs = {Registration.INGOT_LITHIUM.get(), Registration.INGOT_MANGANESE.get(), Registration.INGOT_PLATINUM.get(),
                Registration.INGOT_SILVER.get(), Registration.DUST_NEGARITE.get(), Registration.DUST_POSIRITE.get()};
        ItemStack pick = new ItemStack(Items.STONE_PICKAXE);
        for (int i = 0; i < ores.length; i++) {
            var state = ores[i].defaultBlockState();
            helper.assertTrue(pick.isCorrectToolForDrops(state) && !new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state),
                    "Resource ores must require a stone-tier pickaxe");
            var drops = Block.getDrops(state, level, pos, null, null, pick);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(ores[i].asItem()), "Metal/fuel ores must drop an ore for processing");
            var input = new SimpleContainer(new ItemStack(ores[i]));
            var smelting = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level).orElseThrow();
            var blasting = level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, input, level).orElseThrow();
            int count = i < 4 ? 1 : 4;
            helper.assertTrue(smelting.getResultItem(level.registryAccess()).is(outputs[i])
                            && smelting.getResultItem(level.registryAccess()).getCount() == count
                            && blasting.getResultItem(level.registryAccess()).getCount() == count,
                    "Furnace and blast furnace must retain the original ore yields");
        }
        var silicon = Registration.ORE_SILICON.get().defaultBlockState();
        ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        helper.assertTrue(Block.getDrops(silicon, level, pos, null, null, silk).get(0).is(silicon.getBlock().asItem()), "Silk Touch must preserve silicon ore");
        ItemStack fortune = new ItemStack(Items.DIAMOND_PICKAXE);
        fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
        int total = 0;
        for (int i = 0; i < 64; i++) {
            var drops = Block.getDrops(silicon, level, pos, null, null, fortune);
            helper.assertTrue(drops.get(0).is(Registration.DUST_SILICON.get()), "Silicon must be obtainable by mining");
            total += drops.get(0).getCount();
        }
        helper.assertTrue(total > 64, "Fortune must increase silicon drops");
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        FurnaceBlockEntity furnace = (FurnaceBlockEntity) level.getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Registration.ORE_NEGARITE.get(), 2));
        furnace.setItem(1, new ItemStack(Items.COAL));
        for (int tick = 0; tick < 405; tick++) {
            AbstractFurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), furnace);
        }
        helper.assertTrue(furnace.getItem(2).is(Registration.DUST_NEGARITE.get()) && furnace.getItem(2).getCount() == 8,
                "Two actual furnace operations must produce eight dust, including stacking into existing output");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blueprintCatalogAndIngredientReservation(GameTestHelper helper) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (ConstructorRecipe recipe : BlueprintRecipeRegistry.getRecipes()) {
            helper.assertTrue(!recipe.getDestination().isEmpty() && ids.add(recipe.getId()), "Every catalog recipe needs a distinct, nonempty result/ID");
            for (ItemStack ingredient : recipe.getIngredientList()) {
                helper.assertTrue(!ingredient.isEmpty(), "Catalog ingredients must reference obtainable items");
            }
            helper.assertTrue(BlueprintRecipeRegistry.findRecipe(BlueprintItem.getDestination(BlueprintItem.makeBluePrint(recipe.getDestination()))) == recipe,
                    "Every blueprint must resolve its exact modern variant");
        }
        for (Item item : List.of(Registration.CONNECTOR_NEGARITE.get(), Registration.CONNECTOR_POSIRITE.get(), Registration.CONNECTOR_COMBINED.get())) {
            var recipe = BlueprintRecipeRegistry.findRecipe(new ItemStack(item));
            helper.assertTrue(recipe != null && !recipe.getIngredientList().get(0).isEmpty(), "Each connector must require its matching cable variant");
        }
        var duplicate = new ConstructorRecipe(new ItemStack(Items.DIAMOND), 1, new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.IRON_INGOT, 3));
        var inventory = new ItemStackHandler(2);
        inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        helper.assertTrue(ConstructorCrafting.planIngredients(duplicate, inventory, 0, 2) == null, "Repeated ingredients must not double-count the same inventory items");
        inventory.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 2));
        int[] reserved = ConstructorCrafting.planIngredients(duplicate, inventory, 0, 2);
        helper.assertTrue(reserved != null && reserved[0] + reserved[1] == 5, "Split stacks must satisfy total ingredient demand");
        ItemStack invalid = new ItemStack(Registration.BLUEPRINT.get());
        invalid.getOrCreateTag().putString("destination", "{broken");
        helper.assertTrue(BlueprintItem.getDestination(invalid).isEmpty(), "Malformed blueprints must not crash machine screens");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void automaticBatchCapacityAndPower(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        level.setBlockAndUpdate(pos, Registration.AUTO_CONSTRUCTOR.get().defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Registration.BLUEPRINT_STORAGE.get().defaultBlockState());
        level.setBlockAndUpdate(pos.north(), Registration.NEGARITE_GENERATOR.get().defaultBlockState());
        level.setBlockAndUpdate(pos.south(), Registration.POSIRITE_GENERATOR.get().defaultBlockState());
        var negative = (IPowerBlob) level.getBlockEntity(pos.north());
        var positive = (IPowerBlob) level.getBlockEntity(pos.south());
        negative.fillCableId(710001);
        positive.fillCableId(710002);
        AutoConstructorTile machine = (AutoConstructorTile) level.getBlockEntity(pos);
        var input = (IItemHandlerModifiable) machine.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        var storage = (BlueprintStorageTile) level.getBlockEntity(pos.above());
        ItemStack blueprint = BlueprintItem.makeBluePrint(new ItemStack(Registration.NETCABLE_NEGARITE.get()));
        for (int slot = 0; slot < storage.getItems().getSlots(); slot++) {
            storage.getItems().setStackInSlot(slot, blueprint.copy());
        }
        input.setStackInSlot(0, new ItemStack(Registration.DUST_SILICON.get(), 10));
        input.setStackInSlot(1, new ItemStack(Registration.INGOT_SILVER.get(), 10));
        input.setStackInSlot(2, new ItemStack(Blocks.GLASS_PANE, 30));
        input.setStackInSlot(3, new ItemStack(Registration.DUST_NEGARITE.get(), 20));
        PowerSystem power = PowerSystem.getPowerSystem(level);
        power.addPower(negative.getCableId(), 10000, PowerType.NEGARITE);
        power.addPower(positive.getCableId(), 10000, PowerType.POSIRITE);
        for (int tick = 0; tick < 10; tick++) {
            machine.tickServer();
        }
        helper.assertTrue(input.getStackInSlot(AutoConstructorTile.SLOT_OUTPUT).getCount() == 8, "Empty output must receive the canonical eight-cable batch");
        for (int tick = 0; tick < 10; tick++) {
            machine.tickServer();
        }
        helper.assertTrue(input.getStackInSlot(AutoConstructorTile.SLOT_OUTPUT).getCount() == 16 && input.getStackInSlot(0).getCount() == 8,
                "Existing output must receive eight more cables and consume one recipe's inputs");
        for (int slot = AutoConstructorTile.SLOT_OUTPUT; slot < input.getSlots(); slot++) {
            input.setStackInSlot(slot, new ItemStack(Registration.NETCABLE_NEGARITE.get(), 57));
        }
        for (int tick = 0; tick < 10; tick++) {
            machine.tickServer();
        }
        helper.assertTrue(input.getStackInSlot(AutoConstructorTile.SLOT_OUTPUT).getCount() == 57 && input.getStackInSlot(0).getCount() == 8,
                "A batch must not overflow or consume inputs when every output has only seven spaces");
        input.setStackInSlot(AutoConstructorTile.SLOT_OUTPUT, ItemStack.EMPTY);
        power.removeId(negative.getCableId());
        power.removeId(positive.getCableId());
        power.addPower(negative.getCableId(), 50, PowerType.NEGARITE);
        power.addPower(positive.getCableId(), 50, PowerType.POSIRITE);
        for (int tick = 0; tick < 10; tick++) {
            machine.tickServer();
        }
        helper.assertTrue(input.getStackInSlot(AutoConstructorTile.SLOT_OUTPUT).isEmpty() && input.getStackInSlot(0).getCount() == 8,
                "Idle power without enough crafting power must never consume recipe inputs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void manualConstructorUsesCanonicalBatch(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        level.setBlockAndUpdate(pos, Registration.CONSTRUCTOR.get().defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Registration.BLUEPRINT_STORAGE.get().defaultBlockState());
        var storage = (BlueprintStorageTile) level.getBlockEntity(pos.above());
        storage.getItems().setStackInSlot(0, BlueprintItem.makeBluePrint(new ItemStack(Registration.NETCABLE_POSIRITE.get())));
        var recipe = BlueprintRecipeRegistry.findRecipe(new ItemStack(Registration.NETCABLE_POSIRITE.get()));
        var player = helper.makeMockPlayer();
        for (ItemStack ingredient : recipe.getIngredientList()) {
            player.getInventory().add(ingredient.copy());
        }
        var machine = (ConstructorTile) level.getBlockEntity(pos);
        var panel = machine.createGui("", Ariente.guiHandler.getComponentRegistry());
        var slots = panel.findChild("outputslots").orElseThrow();
        slots.hit(player, null, 2, 6);
        slots.hit(player, null, 2, 6);
        helper.assertTrue(player.getInventory().countItem(Registration.NETCABLE_POSIRITE.get()) == 8,
                "Real Constructor double-click must craft eight cables even when a city blueprint encodes count one");
        helper.assertTrue(player.getInventory().countItem(Registration.DUST_POSIRITE.get()) == 0,
                "The batch must consume exactly one recipe's ingredients");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entityResourceLoot(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockPlayer();
        for (EntityType<?> type : List.of(Registration.ENTITY_SOLDIER.get(), Registration.ENTITY_MASTER_SOLDIER.get(),
                Registration.ENTITY_DRONE.get(), Registration.ENTITY_SENTINEL_DRONE.get())) {
            Mob mob = (Mob) type.create(level);
            var table = level.getServer().getLootData().getLootTable(mob.getLootTable());
            var context = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, mob)
                    .withParameter(LootContextParams.ORIGIN, Vec3.ZERO).withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player).create(LootContextParamSets.ENTITY);
            int lithium = 0;
            for (int i = 0; i < 128; i++) {
                for (ItemStack stack : table.getRandomItems(context)) {
                    helper.assertTrue(stack.is(Registration.INGOT_LITHIUM.get()), "Resource table must drop lithium ingots");
                    lithium += stack.getCount();
                }
            }
            helper.assertTrue(lithium > 20 && lithium < 110, "Every soldier/drone loot hook must reach its restored 50% table");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void configuredOverworldOreFeature(GameTestHelper helper) {
        try (var configScope = new TestServerConfig()) {
            var level = helper.getLevel();
            var old = WorldgenConfiguration.OVERWORLD_OREGEN.get();
            BlockPos origin = new BlockPos(121600, 0, 121600);
            var registry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
            var features = List.of("marble", "lithium", "manganese", "platinum", "silicon", "silver", "negarite", "posirite")
                    .stream().map(name -> registry.get(new ResourceLocation("ariente", "overworld_" + name))).toList();
            helper.assertTrue(features.stream().allMatch(feature -> feature != null), "Every ore must have its own loaded datapack feature");
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -56, -8), origin.offset(24, 88, 24))) {
                level.setBlock(pos, (pos.getY() < 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState(), 2);
            }
            try {
                WorldgenConfiguration.OVERWORLD_OREGEN.set(YesNoDefault.DEFAULT);
                for (var placed : features) {
                    helper.assertTrue(!placed.placeWithBiomeCheck(level, level.getChunkSource().getGenerator(), RandomSource.create(123), origin),
                            "DEFAULT must suppress every ore when the companion is installed");
                }
                WorldgenConfiguration.OVERWORLD_OREGEN.set(YesNoDefault.YES);
                for (int i = 0; i < 8; i++) {
                    for (int ore = 0; ore < features.size(); ore++) {
                        features.get(ore).placeWithBiomeCheck(level, level.getChunkSource().getGenerator(), RandomSource.create(123 + 31L * i + ore), origin);
                    }
                }
                Set<Block> found = new HashSet<>();
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -56, -8), origin.offset(24, 88, 24))) {
                    found.add(level.getBlockState(pos).getBlock());
                }
                for (Block ore : List.of(Registration.ORE_LITHIUM.get(), Registration.ORE_MANGANESE.get(), Registration.ORE_PLATINUM.get(),
                        Registration.ORE_SILVER.get(), Registration.ORE_SILICON.get(), Registration.ORE_NEGARITE.get(), Registration.ORE_POSIRITE.get())) {
                    helper.assertTrue(found.contains(ore), "Enabled native ore feature must place " + ore);
                }
                WorldgenConfiguration.OVERWORLD_OREGEN.set(YesNoDefault.NO);
                for (var placed : features) {
                    helper.assertTrue(!placed.placeWithBiomeCheck(level, level.getChunkSource().getGenerator(), RandomSource.create(123), origin),
                            "NO must disable every ore at placement time");
                }
            } finally {
                WorldgenConfiguration.OVERWORLD_OREGEN.set(old);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void standaloneCraftingHasABlueprintStorageEntryPoint(GameTestHelper helper) {
        var level = helper.getLevel();
        var grid = new TransientCraftingContainer(FakePlayerFactory.getMinecraft(level).inventoryMenu, 3, 3);
        ItemStack[] ingredients = {
                new ItemStack(Registration.INGOT_SILVER.get()), new ItemStack(Blocks.GLASS_PANE), new ItemStack(Registration.INGOT_SILVER.get()),
                new ItemStack(Registration.DUST_SILICON.get()), new ItemStack(Blocks.CHEST), new ItemStack(Registration.DUST_SILICON.get()),
                new ItemStack(DecorativeBlockModule.MARBLE.get(MarbleColor.WHITE).get()),
                new ItemStack(DecorativeBlockModule.MARBLE.get(MarbleColor.BLACK).get()),
                new ItemStack(DecorativeBlockModule.MARBLE.get(MarbleColor.BLUE).get())
        };
        for (int slot = 0; slot < ingredients.length; slot++) {
            grid.setItem(slot, ingredients[slot]);
        }
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow();
        helper.assertTrue(recipe.assemble(grid, level.registryAccess()).is(Registration.BLUEPRINT_STORAGE.get().asItem()),
                "The first Blueprint Storage must be craftable from Overworld materials and mixed marble colors, without a blueprint");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void datapackOreTargetsAndDepthReadConfigOnlyAtPlacement(GameTestHelper helper) {
        try (var scope = new TestServerConfig()) {
            var level = helper.getLevel();
            var previous = WorldgenConfiguration.OVERWORLD_OREGEN.get();
            try {
                WorldgenConfiguration.OVERWORLD_OREGEN.set(YesNoDefault.NO);
                var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
                var json = JsonParser.parseString("""
                        {"feature":{"type":"ariente:overworld_ores","config":{
                          "size":24,"discard_chance_on_air_exposure":0,
                          "targets":[{"target":{"predicate_type":"minecraft:tag_match","tag":"minecraft:deepslate_ore_replaceables"},
                                      "state":{"Name":"minecraft:diamond_block"}}]}},
                         "placement":[{"type":"minecraft:count","count":1},
                          {"type":"minecraft:height_range","height":{"type":"minecraft:uniform",
                            "min_inclusive":{"absolute":-40},"max_inclusive":{"absolute":-40}}}]}
                        """);
                var placed = PlacedFeature.DIRECT_CODEC.parse(ops, json).getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
                BlockPos origin = new BlockPos(123200, -40, 123200);
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-16, -8, -8), origin.offset(24, 8, 24))) {
                    level.setBlock(pos, (pos.getX() < origin.getX() ? Blocks.STONE : Blocks.DEEPSLATE).defaultBlockState(), 2);
                }
                helper.assertTrue(!placed.place(level, level.getChunkSource().getGenerator(), RandomSource.create(42), origin.east(8)),
                        "A decoded datapack feature must still obey NO when actually placed");
                WorldgenConfiguration.OVERWORLD_OREGEN.set(YesNoDefault.YES);
                helper.assertTrue(!placed.place(level, level.getChunkSource().getGenerator(), RandomSource.create(42), origin.west(8)),
                        "Datapack deepslate-only targets must reject stone");
                helper.assertTrue(placed.place(level, level.getChunkSource().getGenerator(), RandomSource.create(42), origin.east(8)),
                        "The same decoded feature must obey a later YES and use its custom below-zero target");
                int found = 0;
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-16, -8, -8), origin.offset(24, 8, 24))) {
                    if (level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK)) {
                        helper.assertTrue(pos.getX() >= origin.getX() && pos.getY() < 0,
                                "Custom output must be confined to its datapack target and deep height");
                        found++;
                    }
                }
                helper.assertTrue(found > 0, "Datapack output must not be hardcoded to an Ariente ore");
                var ariente = level.getServer().getLevel(DimensionRegister.DIMENSION);
                helper.assertTrue(!placed.place(ariente, ariente.getChunkSource().getGenerator(), RandomSource.create(42), origin),
                        "YES must keep the standalone feature restricted to the Overworld");
            } finally {
                WorldgenConfiguration.OVERWORLD_OREGEN.set(previous);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void configuredDungeonLootAndCityCatalog(GameTestHelper helper) {
        try (var configScope = new TestServerConfig()) {
            var config = WorldgenConfiguration.OVERWORLD_DUNGEON_LOOT.get();
            int blueprints = WorldgenConfiguration.OVERWORLD_LOOT_BLUEPRINTS.get();
            int results = WorldgenConfiguration.OVERWORLD_LOOT_ITEMS.get();
            int suits = WorldgenConfiguration.OVERWORLD_LOOT_SUIT.get();
            var level = helper.getLevel();
            var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
            var table = level.getServer().getLootData().getLootTable(new ResourceLocation("minecraft", "chests/simple_dungeon"));
            try {
                WorldgenConfiguration.OVERWORLD_DUNGEON_LOOT.set(YesNoDefault.YES);
                WorldgenConfiguration.OVERWORLD_LOOT_BLUEPRINTS.set(1000000);
                WorldgenConfiguration.OVERWORLD_LOOT_ITEMS.set(0);
                WorldgenConfiguration.OVERWORLD_LOOT_SUIT.set(0);
                boolean found = false;
                for (int i = 0; i < 20; i++) {
                    for (ItemStack stack : table.getRandomItems(params)) {
                        if (stack.is(Registration.BLUEPRINT.get())) {
                            found = true;
                            helper.assertTrue(BlueprintRecipeRegistry.findRecipe(BlueprintItem.getDestination(stack)) != null, "Dungeon blueprints must refer to valid modern recipes");
                        }
                    }
                }
                helper.assertTrue(found, "Registered global modifier must add configured dungeon blueprints");
                WorldgenConfiguration.OVERWORLD_LOOT_BLUEPRINTS.set(0);
                WorldgenConfiguration.OVERWORLD_LOOT_ITEMS.set(1000000);
                boolean resultFound = false;
                for (int i = 0; i < 20; i++) {
                    for (ItemStack stack : table.getRandomItems(params)) {
                        if (ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace().equals(Ariente.MODID)) {
                            resultFound = true;
                            helper.assertTrue(!stack.is(Registration.BLUEPRINT.get()) && BlueprintRecipeRegistry.findRecipe(stack) != null,
                                    "Result weight must supply a valid crafted result instead of a blueprint");
                        }
                    }
                }
                helper.assertTrue(resultFound, "Result weight must enable crafted-item loot");
                WorldgenConfiguration.OVERWORLD_LOOT_ITEMS.set(0);
                WorldgenConfiguration.OVERWORLD_LOOT_SUIT.set(1000000);
                boolean suitFound = false;
                Set<Item> gear = Set.of(Registration.POWERSUIT_HEAD.get(), Registration.POWERSUIT_CHEST.get(), Registration.POWERSUIT_LEGS.get(),
                        Registration.POWERSUIT_FEET.get(), Registration.ENERGY_SABRE.get(), Registration.ENHANCED_ENERGY_SABRE.get());
                for (int i = 0; i < 20; i++) {
                    for (ItemStack stack : table.getRandomItems(params)) {
                        if (ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace().equals(Ariente.MODID)) {
                            suitFound = true;
                            helper.assertTrue(gear.contains(stack.getItem()), "Suit weight must select one of the original six equipment entries");
                        }
                    }
                }
                helper.assertTrue(suitFound, "Suit weight must enable equipment loot");
                WorldgenConfiguration.OVERWORLD_LOOT_SUIT.set(0);
                helper.assertTrue(table.getRandomItems(params).stream().noneMatch(stack -> ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace().equals(Ariente.MODID)),
                        "All zero weights must preserve vanilla loot without additions");
                WorldgenConfiguration.OVERWORLD_LOOT_BLUEPRINTS.set(1000000);
                var other = level.getServer().getLootData().getLootTable(new ResourceLocation("minecraft", "chests/village/village_armorer"));
                helper.assertTrue(other.getRandomItems(params).stream().noneMatch(stack -> stack.is(Registration.BLUEPRINT.get())),
                        "The modifier must not affect unrelated chest tables");
                WorldgenConfiguration.OVERWORLD_DUNGEON_LOOT.set(YesNoDefault.NO);
                for (ItemStack stack : table.getRandomItems(params)) {
                    helper.assertTrue(!stack.is(Registration.BLUEPRINT.get()), "NO must disable the modifier at loot-roll time");
                }
            } finally {
                WorldgenConfiguration.OVERWORLD_DUNGEON_LOOT.set(config);
                WorldgenConfiguration.OVERWORLD_LOOT_BLUEPRINTS.set(blueprints);
                WorldgenConfiguration.OVERWORLD_LOOT_ITEMS.set(results);
                WorldgenConfiguration.OVERWORLD_LOOT_SUIT.set(suits);
            }
            for (var plan : AssetRegistries.CITYPLANS.getIterable()) {
                for (var loot : plan.getLoot()) {
                    if (loot.getId() != null) {
                        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(loot.getId()), "City loot must reference a modern registered item: " + loot.getId());
                        if (loot.isBlueprint()) {
                            helper.assertTrue(BlueprintRecipeRegistry.findRecipe(new ItemStack(ForgeRegistries.ITEMS.getValue(loot.getId()))) != null,
                                    "Explicit city blueprint must have a usable recipe: " + loot.getId());
                        }
                    }
                }
            }
            helper.assertTrue(Ariente.setup.arienteWorld, "Companion suite must retain actual local ArienteWorld integration");
            helper.succeed();
        }
    }
    /** Runtime config toggles must not race NightConfig's file autoreload thread. */
    private static class TestServerConfig implements AutoCloseable {

        private final CommentedConfig original;

        private TestServerConfig() {
            original = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER).stream()
                    .filter(config -> config.getSpec() == Config.SERVER_CONFIG).findFirst().orElseThrow().getConfigData();
            Config.SERVER_CONFIG.setConfig(copyConfig(original));
        }

        private static CommentedConfig copyConfig(UnmodifiableConfig source) {
            CommentedConfig copy = CommentedConfig.inMemory();
            for (var entry : source.entrySet()) {
                Object value = entry.getValue();
                copy.set(List.of(entry.getKey()), value instanceof UnmodifiableConfig nested ? copyConfig(nested) : value);
            }
            return copy;
        }

        @Override
        public void close() {
            Config.SERVER_CONFIG.setConfig(original);
        }
    }

}
