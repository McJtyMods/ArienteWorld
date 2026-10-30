package mcjty.arienteworld.setup;

import mcjty.arienteworld.ModSounds;
import mcjty.arienteworld.oregen.OverworldDungeonFeature;
import com.mojang.serialization.Codec;
import mcjty.arienteworld.dimension.ArienteBiomeSource;
import mcjty.arienteworld.dimension.ArienteChunkGenerator;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import mcjty.arienteworld.dimension.CityFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import mcjty.arienteworld.blocks.DummyBlock;
import mcjty.arienteworld.blocks.plants.BlockArientePlant;
import mcjty.lib.blocks.BaseBlock;
import mcjty.lib.blocks.RotationType;
import mcjty.lib.builder.BlockBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static mcjty.arienteworld.ArienteWorld.MODID;

public class Registration {

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);
    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, MODID);
    public static final RegistryObject<Codec<? extends ChunkGenerator>> GENERATOR = GENERATORS.register("ariente", () -> ArienteChunkGenerator.CODEC);
    public static final RegistryObject<Codec<? extends BiomeSource>> BIOME_SOURCE = BIOME_SOURCES.register("ariente", () -> ArienteBiomeSource.CODEC);

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, MODID);
    public static final RegistryObject<OverworldDungeonFeature> ENTRANCE = FEATURES.register("overworld_dungeon", OverworldDungeonFeature::new);
    public static final RegistryObject<CityFeature> CITY = FEATURES.register("city", CityFeature::new);

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static Item.Properties createStandardProperties() {
        return new Item.Properties();
    }

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("arienteworld", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arienteworld"))
            .icon(() -> new ItemStack(Registration.GLOWLOG.get()))
            .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    public static void register() {
        ModSounds.register();
        GENERATORS.register(FMLJavaModLoadingContext.get().getModEventBus());
        BIOME_SOURCES.register(FMLJavaModLoadingContext.get().getModEventBus());
        FEATURES.register(FMLJavaModLoadingContext.get().getModEventBus());
        TABS.register(FMLJavaModLoadingContext.get().getModEventBus());
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        SOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }


    public static final RegistryObject<BaseBlock> GUARD_DUMMY = BLOCKS.register("guard_dummy", () -> new DummyBlock(new BlockBuilder()));
    public static final RegistryObject<Item> GUARD_DUMMY_ITEM = ITEMS.register("guard_dummy", () -> new BlockItem(GUARD_DUMMY.get(), createStandardProperties()));

    public static final RegistryObject<BaseBlock> SOLDIER_DUMMY = BLOCKS.register("solider_dummy", () -> new DummyBlock(new BlockBuilder()));
    public static final RegistryObject<Item> SOLDIER_DUMMY_ITEM = ITEMS.register("solider_dummy", () -> new BlockItem(SOLDIER_DUMMY.get(), createStandardProperties()));

    public static final RegistryObject<BaseBlock> MASTER_SOLDIER_DUMMY = BLOCKS.register("master_solider_dummy", () -> new DummyBlock(new BlockBuilder()));
    public static final RegistryObject<Item> MASTER_SOLDIER_DUMMY_ITEM = ITEMS.register("master_solider_dummy", () -> new BlockItem(MASTER_SOLDIER_DUMMY.get(), createStandardProperties()));

    public static final RegistryObject<Block> GLOWLOG = BLOCKS.register("glowlog", () -> new Block(Block.Properties.of().mapColor(MapColor.WOOD).strength(2).sound(SoundType.WOOD).lightLevel(state -> 10)));
    public static final RegistryObject<Item> GLOWLOG_ITEM = ITEMS.register("glowlog", () -> new BlockItem(GLOWLOG.get(), createStandardProperties()));

    public static final RegistryObject<Block> BLUELOG = BLOCKS.register("bluelog", () -> new Block(Block.Properties.of().mapColor(MapColor.WOOD).strength(2).sound(SoundType.WOOD)));
    public static final RegistryObject<Item> BLUELOG_ITEM = ITEMS.register("bluelog", () -> new BlockItem(BLUELOG.get(), createStandardProperties()));

    public static final RegistryObject<LeavesBlock> GLOWLEAVES = BLOCKS.register("glowleaves", () -> new LeavesBlock(Block.Properties.of().mapColor(MapColor.PLANT).strength(.2f).sound(SoundType.GRASS).noOcclusion().randomTicks().lightLevel(state -> 10)));
    public static final RegistryObject<Item> GLOWLEAVES_ITEM = ITEMS.register("glowleaves", () -> new BlockItem(GLOWLEAVES.get(), createStandardProperties()));

    public static final RegistryObject<LeavesBlock> BLUELEAVES = BLOCKS.register("blueleaves", () -> new LeavesBlock(Block.Properties.of().mapColor(MapColor.PLANT).strength(.2f).sound(SoundType.GRASS).noOcclusion().randomTicks()));
    public static final RegistryObject<Item> BLUELEAVES_ITEM = ITEMS.register("blueleaves", () -> new BlockItem(BLUELEAVES.get(), createStandardProperties()));

    public static final RegistryObject<LeavesBlock> DARKLEAVES = BLOCKS.register("darkleaves", () -> new LeavesBlock(Block.Properties.of().mapColor(MapColor.PLANT).strength(.2f).sound(SoundType.GRASS).noOcclusion().randomTicks()));
    public static final RegistryObject<Item> DARKLEAVES_ITEM = ITEMS.register("darkleaves", () -> new BlockItem(DARKLEAVES.get(), createStandardProperties()));

    public static final RegistryObject<BlockArientePlant> BLACKBUSH = BLOCKS.register("black_bush", BlockArientePlant::new);
    public static final RegistryObject<Item> BLACKBUSH_ITEM = ITEMS.register("black_bush", () -> new BlockItem(BLACKBUSH.get(), createStandardProperties()));

    public static final RegistryObject<BlockArientePlant> DARKGRASS = BLOCKS.register("dark_grass", BlockArientePlant::new);
    public static final RegistryObject<Item> DARKGRASS_ITEM = ITEMS.register("dark_grass", () -> new BlockItem(DARKGRASS.get(), createStandardProperties()));

    public static final RegistryObject<BlockArientePlant> SMALLFLOWER = BLOCKS.register("small_flower", BlockArientePlant::new);
    public static final RegistryObject<Item> SMALLFLOWER_ITEM = ITEMS.register("small_flower", () -> new BlockItem(SMALLFLOWER.get(), createStandardProperties()));

    public static final RegistryObject<Block> LAPISORE = BLOCKS.register("lapisore", () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(3.0f)));
    public static final RegistryObject<Item> LAPISORE_ITEM = ITEMS.register("lapisore", () -> new BlockItem(LAPISORE.get(), createStandardProperties()));

    public static final RegistryObject<Block> GLOWSTONE = BLOCKS.register("glowstoneore", () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(3.0f)));
    public static final RegistryObject<Item> GLOWSTONE_ITEM = ITEMS.register("glowstoneore", () -> new BlockItem(GLOWSTONE.get(), createStandardProperties()));

}
