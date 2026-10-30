package mcjty.arienteworld.client;

import mcjty.ariente.Ariente;
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
                || mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || runtime == null || mc.getOverlay() != null) {
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
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    for (int cx = 4000; cx < 4008; cx++) {
                        var chunk = level.getChunk(cx, 4000);
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                for (int y = 6; y < 80; y++) {
                                    String id = ForgeRegistries.BLOCKS.getKey(chunk.getBlockState(pos.set(cx * 16 + x, y, 64000 + z)).getBlock()).toString();
                                    if (id.startsWith("ariente:")) {
                                        ores.add(id);
                                    }
                                }
                            }
                        }
                    }
                    for (String name : List.of("lithiumore", "manganeseore", "platinumore", "silverore", "siliconore", "negarite", "posirite")) {
                        require(ores.contains("ariente:" + name), "Fresh Overworld chunks must contain " + name);
                    }
                    var table = level.getServer().getLootData().getLootTable(new ResourceLocation("minecraft", "chests/simple_dungeon"));
                    var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
                    boolean blueprint = false;
                    for (int i = 0; i < 128; i++) {
                        blueprint |= table.getRandomItems(params).stream().anyMatch(s -> s.is(Registration.BLUEPRINT.get()));
                    }
                    require(blueprint, "Default dungeon loot must provide valid blueprints without the companion");
                    Ariente.setup.getLogger().info("STANDALONE PROGRESSION CHECK PASSED: seven ores in fresh natural chunks and default dungeon blueprints, without ArienteWorld");
                }
                serverChecked = true;
            });
        }
        if (serverChecked && ++ticks == 40) {
            require(mc.screen != null, "JEI's actual recipe screen must open");
            try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Progression client regression: " + message);
        }
    }
}
