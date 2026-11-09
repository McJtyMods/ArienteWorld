package mcjty.arienteworld.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;

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
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
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
        pack.addProvider((output, lookup) -> new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)), lookup));
        pack.addProvider(BlockTags::new);
    }

    private static class BlockLoot extends BlockLootSubProvider {

        private BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output;

        @Override
        public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output) {
            this.output = output;
            generate();
        }

        @Override
        public void add(Block block, LootTable.Builder table) {
            block.getLootTable().ifPresent(key -> output.accept(key, table));
        }


        private BlockLoot(HolderLookup.Provider provider) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), provider);
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

    private static class BlockTags extends IntrinsicHolderTagsProvider<Block> {

        private BlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, Registries.BLOCK, lookup, value -> BuiltInRegistries.BLOCK.getResourceKey(value).orElseThrow());
        }

        @Override
        protected void addTags(HolderLookup.Provider lookup) {
            tag(net.minecraft.tags.BlockTags.LOGS).add(Registration.GLOWLOG.get(), Registration.BLUELOG.get());
            tag(net.minecraft.tags.BlockTags.LEAVES).add(Registration.GLOWLEAVES.get(), Registration.BLUELEAVES.get(), Registration.DARKLEAVES.get());
            tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE).add(Registration.NEGARITE_CRYSTAL.get(), Registration.POSIRITE_CRYSTAL.get());
            tag(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL).add(Registration.NEGARITE_CRYSTAL.get(), Registration.POSIRITE_CRYSTAL.get());
            tag(CrystalFormationFeature.SUBSTRATE).add(DecorativeBlockModule.MARBLE.get(MarbleColor.GRAY).get(),
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
