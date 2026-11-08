package mcjty.arienteworld.datagen;

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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import static mcjty.ariente.setup.Registration.DUST_NEGARITE;
import static mcjty.ariente.setup.Registration.DUST_POSIRITE;

@EventBusSubscriber(modid = ArienteWorld.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        event.getGenerator().addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)), event.getLookupProvider()));
        event.getGenerator().addProvider(event.includeServer(),
                new BlockTags(output, event.getLookupProvider(), event.getExistingFileHelper()));
    }

    private static class BlockLoot extends BlockLootSubProvider {

        private BlockLoot(HolderLookup.Provider provider) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), provider);
        }

        @Override
        protected void generate() {
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

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return Registration.BLOCKS.getEntries().stream().map(entry -> (Block) entry.get()).toList();
        }
    }

    private static class BlockTags extends BlockTagsProvider {

        private BlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper helper) {
            super(output, lookup, ArienteWorld.MODID, helper);
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
}
