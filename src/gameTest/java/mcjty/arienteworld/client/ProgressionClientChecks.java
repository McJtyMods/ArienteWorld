package mcjty.arienteworld.client;

import mcjty.ariente.Ariente;
import mcjty.ariente.api.MarbleColor;
import mcjty.ariente.blocks.decorative.DecorativeBlockModule;
import mcjty.ariente.blocks.utility.BlueprintStorageTile;
import mcjty.ariente.blocks.utility.ConstructorTile;
import mcjty.ariente.compat.jei.JeiConstructorRecipeCategory;
import mcjty.ariente.config.WorldgenConfiguration;
import mcjty.ariente.items.BlueprintItem;
import mcjty.ariente.recipes.BlueprintRecipeRegistry;
import mcjty.ariente.setup.Registration;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static mcjty.ariente.compat.jei.JeiPlugin.CONSTRUCTOR;

/** Opt-in client evidence for JEI and optional Overworld progression, including a run without ArienteWorld. */
@JeiPlugin
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ProgressionClientChecks implements IModPlugin {

    private static IJeiRuntime runtime;
    private static boolean started;
    private static volatile boolean serverChecked;
    private static int ticks;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(Ariente.MODID, "progression_check");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("ariente.progressionClientCheck") || event.phase != TickEvent.Phase.END
                || mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || runtime == null || mc.gui.overlay() != null) {
            return;
        }
        if (!started) {
            started = true;
            boolean companion = Boolean.getBoolean("ariente.expectCompanion");
            require(ModList.get().isLoaded("arienteworld") == companion, "Actual loaded mods must match the requested companion mode");
            var manager = runtime.getRecipeManager();
            require(manager.createRecipeLookup(CONSTRUCTOR).get().count() == BlueprintRecipeRegistry.getRecipes().size(), "JEI must index the complete Constructor catalog");
            var catalysts = manager.createRecipeCatalystLookup(CONSTRUCTOR).getItemStack().toList();
            require(catalysts.stream().anyMatch(s -> s.is(Registration.CONSTRUCTOR.get().asItem()))
                    && catalysts.stream().anyMatch(s -> s.is(Registration.AUTO_CONSTRUCTOR.get().asItem())), "JEI must list both Constructor catalysts");
            var helpers = runtime.getJeiHelpers();
            var category = new JeiConstructorRecipeCategory(helpers.getGuiHelper());
            for (var recipe : BlueprintRecipeRegistry.getRecipes()) {
                require(manager.createRecipeLayoutDrawable(category, recipe, helpers.getFocusFactory().getEmptyFocusGroup()).isPresent(),
                        "Every recipe must create a drawable JEI layout with counted ingredients and its required blueprint");
            }
            var ingredientHelper = runtime.getIngredientManager().getIngredientHelper(VanillaTypes.ITEM_STACK);
            String negative = ingredientHelper.getUniqueId(BlueprintItem.makeBluePrint(new ItemStack(Registration.NETCABLE_NEGARITE.get())), UidContext.Recipe);
            String positive = ingredientHelper.getUniqueId(BlueprintItem.makeBluePrint(new ItemStack(Registration.NETCABLE_POSIRITE.get())), UidContext.Recipe);
            require(!negative.equals(positive), "JEI must distinguish blueprint cable variants");
            var focus = helpers.getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, new ItemStack(Registration.NETCABLE_POSIRITE.get()));
            var focused = manager.createRecipeLookup(CONSTRUCTOR).limitFocus(List.of(focus)).get().toList();
            require(focused.size() == 1 && focused.get(0).getDestination().is(Registration.NETCABLE_POSIRITE.get()),
                    "Output lookup must find exactly the matching cable color");
            runtime.getRecipesGui().show(List.of(focus));
            mc.getSingleplayerServer().execute(() -> {
                require(WorldgenConfiguration.doWorldGen() == !companion && WorldgenConfiguration.doDungeonLoot() == !companion,
                        "DEFAULT progression settings must follow the actual companion presence");
                if (!companion) {
                    var level = mc.getSingleplayerServer().getLevel(Level.OVERWORLD);
                    Set<String> ores = new HashSet<>();
                    Set<String> deepOres = new HashSet<>();
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    for (int cx = 4000; cx < 4008; cx++) {
                        var chunk = level.getChunk(cx, 4000);
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                for (int y = -56; y < 88; y++) {
                                    String id = ForgeRegistries.BLOCKS.getKey(chunk.getBlockState(pos.set(cx * 16 + x, y, 64000 + z)).getBlock()).toString();
                                    if (id.startsWith("ariente:")) {
                                        ores.add(id);
                                        if (y < 0) {
                                            deepOres.add(id);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    for (String name : List.of("lithiumore", "manganeseore", "platinumore", "silverore", "siliconore", "negarite", "posirite")) {
                        require(ores.contains("ariente:" + name), "Fresh Overworld chunks must contain " + name);
                        require(deepOres.contains("ariente:" + name), "Fresh deepslate must contain " + name);
                    }
                    var table = level.getServer().getLootData().getLootTable(new ResourceLocation("minecraft", "chests/simple_dungeon"));
                    var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
                    ItemStack blueprint = ItemStack.EMPTY;
                    for (int i = 0; i < 128; i++) {
                        for (var stack : table.getRandomItems(params)) {
                            if (stack.is(Registration.BLUEPRINT.get())) {
                                blueprint = stack.copy();
                            }
                        }
                    }
                    require(!blueprint.isEmpty(), "Default dungeon loot must provide valid blueprints without the companion");
                    verifyCraftingBootstrap(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()), blueprint);
                    Ariente.setup.getLogger().info("STANDALONE PROGRESSION CHECK PASSED: seven ores in fresh chunks including deepslate, default dungeon blueprints, ordinary Constructor/Storage crafting and a loot-blueprint craft without ArienteWorld");
                }
                serverChecked = true;
            });
        }
        if (serverChecked && ++ticks == 40) {
            require(mc.gui.screen() != null, "JEI's actual recipe screen must open");
            try (var screenshot = Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget())) {
                Path directory = Path.of("..", "verification");
                Files.createDirectories(directory);
                screenshot.writeToFile(directory.resolve("constructor-jei-" + Boolean.getBoolean("ariente.expectCompanion") + ".png"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            Ariente.setup.getLogger().info("CONSTRUCTOR JEI CLIENT CHECKS PASSED: catalog, both catalysts, layouts, blueprint subtypes and real recipe screen");
            mc.stop();
        }
    }

    private static void verifyCraftingBootstrap(ServerPlayer player, ItemStack blueprint) {
        var level = player.serverLevel();
        var storage = craft(player, new ItemStack[]{
                new ItemStack(Registration.INGOT_SILVER.get()), new ItemStack(Blocks.GLASS_PANE), new ItemStack(Registration.INGOT_SILVER.get()),
                new ItemStack(Registration.DUST_SILICON.get()), new ItemStack(Blocks.CHEST), new ItemStack(Registration.DUST_SILICON.get()),
                new ItemStack(DecorativeBlockModule.MARBLE.get(MarbleColor.WHITE).get()),
                new ItemStack(DecorativeBlockModule.MARBLE.get(MarbleColor.BLACK).get()),
                new ItemStack(DecorativeBlockModule.MARBLE.get(MarbleColor.BLUE).get())});
        var constructor = craft(player, new ItemStack[]{
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Registration.INGOT_PLATINUM.get()), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Registration.INGOT_SILVER.get()), new ItemStack(Registration.INGOT_SILVER.get()), new ItemStack(Registration.INGOT_SILVER.get())});
        require(storage.is(Registration.BLUEPRINT_STORAGE.get().asItem()) && constructor.is(Registration.CONSTRUCTOR.get().asItem()),
                "Ordinary crafting must provide both bootstrap machines without blueprints");
        BlockPos pos = new BlockPos(64000, 200, 64000);
        level.setBlockAndUpdate(pos, Registration.CONSTRUCTOR.get().defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Registration.BLUEPRINT_STORAGE.get().defaultBlockState());
        var library = (BlueprintStorageTile) level.getBlockEntity(pos.above());
        library.getItems().setStackInSlot(0, blueprint);
        var recipe = BlueprintRecipeRegistry.findRecipe(BlueprintItem.getDestination(blueprint));
        require(recipe != null, "A default dungeon blueprint must resolve to a Constructor recipe");
        player.getInventory().clearContent();
        for (var ingredient : recipe.getIngredientList()) {
            require(player.getInventory().add(ingredient.copy()), "Recipe ingredients must fit the test player's inventory");
        }
        var machine = (ConstructorTile) level.getBlockEntity(pos);
        var slots = machine.createGui("", Ariente.guiHandler.getComponentRegistry()).findChild("outputslots").orElseThrow();
        slots.hit(player, null, 2, 6);
        slots.hit(player, null, 2, 6);
        var result = recipe.getDestination();
        require(player.getInventory().countItem(result.getItem()) == result.getCount(),
                "The dungeon blueprint must craft its complete batch through the actual Constructor control");
        require(library.getItems().getStackInSlot(0).getCount() == 1, "Crafting must retain the dungeon blueprint");
    }

    private static ItemStack craft(ServerPlayer player, ItemStack[] ingredients) {
        var grid = new TransientCraftingContainer(player.inventoryMenu, 3, 3);
        for (int slot = 0; slot < ingredients.length; slot++) {
            grid.setItem(slot, ingredients[slot]);
        }
        var level = player.serverLevel();
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow().assemble(grid, level.registryAccess());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Progression client regression: " + message);
        }
    }
}
