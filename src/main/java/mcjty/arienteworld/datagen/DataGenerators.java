package mcjty.arienteworld.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import mcjty.lib.datagen.ReloadableDataProvider;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.tags.TagsProvider;

import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import mcjty.lib.datagen.model.ModelProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import mcjty.ariente.api.MarbleColor;
import mcjty.ariente.blocks.decorative.DecorativeBlockModule;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.features.CrystalFormationFeature;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import static mcjty.ariente.setup.Registration.DUST_NEGARITE;
import static mcjty.ariente.setup.Registration.DUST_POSIRITE;

public class DataGenerators implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        var pack = generator.createPack();
        pack.addProvider((FabricDataGenerator.Pack.Factory<ItemDefinitions>) ItemDefinitions::new);
        var entries = new RegistrySetBuilder();
        entries.add(Registries.LOOT_TABLE, new LootTableProvider(Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK))));
        pack.addProvider((output, lookup) -> new ReloadableDataProvider(output, lookup, entries));
        pack.addProvider(BlockTags::new);
    }

    private static class BlockLoot extends BlockLootSubProvider {

        private final LootTableSubProvider.Context context;

        @Override
        public void run() {
            generate();
        }

        @Override
        public void add(Block block, LootTable.Builder table) {
            block.getLootTable().ifPresent(key -> context.accept(key, table));
        }


        private BlockLoot(LootTableSubProvider.Context provider) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), provider);
            context = provider;
        }

        @Override
        public void generate() {
            // Keep the existing authored drops, including leaves and editor markers.
            getKnownBlocks().forEach(block -> {
                if (block == Registration.NEGARITE_CRYSTAL.get() || block == Registration.POSIRITE_CRYSTAL.get()) {
                    var dust = block == Registration.NEGARITE_CRYSTAL.get() ? DUST_NEGARITE.get() : DUST_POSIRITE.get();
                    add(block, createSilkTouchDispatchTable(block, applyExplosionDecay(block, LootItem.lootTableItem(dust))));
                } else if (block instanceof DoublePlantBlock) {
                    add(block, createSinglePropConditionTable(block, DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                } else {
                    dropSelf(block);
                }
            });
        }

        protected Iterable<Block> getKnownBlocks() {
            return Registration.BLOCKS.getEntries().stream().map(entry -> (Block) entry.get()).toList();
        }
    }

    private static class BlockTags extends TagsProvider<Block> {

        private BlockTags(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, Registries.BLOCK, lookup);
        }

        private void addTag(TagKey<Block> key, Block... blocks) {
            var builder = tag(key);
            for (Block block : blocks) {
                builder.add(BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow());
            }
        }

        @Override
        protected void addTags(HolderLookup.Provider lookup) {
            for (var entry : Registration.BLOCKS.getEntries()) {
                Block block = entry.get();
                if (!(block instanceof LeavesBlock) && block.defaultBlockState().isSolid()) {
                    addTag(net.minecraft.tags.BlockTags.BLOCKS_MOTION_NO_LEAVES, block);
                }
            }
            addTag(net.minecraft.tags.BlockTags.LOGS, Registration.GLOWLOG.get(), Registration.BLUELOG.get());
            addTag(net.minecraft.tags.BlockTags.LEAVES, Registration.GLOWLEAVES.get(), Registration.BLUELEAVES.get(), Registration.DARKLEAVES.get());
            addTag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE, Registration.NEGARITE_CRYSTAL.get(), Registration.POSIRITE_CRYSTAL.get());
            addTag(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL, Registration.NEGARITE_CRYSTAL.get(), Registration.POSIRITE_CRYSTAL.get());
            addTag(CrystalFormationFeature.SUBSTRATE, DecorativeBlockModule.MARBLE.get(MarbleColor.GRAY).get(),
                    DecorativeBlockModule.MARBLE.get(MarbleColor.WHITE).get(), DecorativeBlockModule.MARBLE.get(MarbleColor.BLUE).get(),
                    DecorativeBlockModule.MARBLE.get(MarbleColor.BLACK).get());
        }
    }
    private record ItemDefinitions(PackOutput output) implements DataProvider {

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            var paths = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
            var tasks = Registration.ITEMS.getEntries().stream().map(entry -> {
                var id = BuiltInRegistries.ITEM.getKey(entry.get());
                var definition = ModelProvider.itemDefinition(id.withPrefix("item/"));
                if (entry.get() == Registration.NEGARITE_CRYSTAL.get().asItem()
                        || entry.get() == Registration.POSIRITE_CRYSTAL.get().asItem()) {
                    var tint = new JsonObject();
                    tint.addProperty("type", "minecraft:constant");
                    tint.addProperty("value", 0xd9ffffff);
                    var tints = new JsonArray();
                    tints.add(tint);
                    definition.getAsJsonObject("model").add("tints", tints);
                }
                return DataProvider.saveStable(cache, definition, paths.json(id));
            }).toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(tasks);
        }

        @Override
        public String getName() {
            return "ArienteWorld item definitions";
        }
    }
}
