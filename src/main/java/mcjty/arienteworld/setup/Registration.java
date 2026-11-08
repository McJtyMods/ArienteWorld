package mcjty.arienteworld.setup;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import mcjty.arienteworld.ModSounds;
import mcjty.arienteworld.blocks.CrystalBlock;
import mcjty.arienteworld.blocks.DummyBlock;
import mcjty.arienteworld.blocks.plants.BlockArientePlant;
import mcjty.arienteworld.dimension.ArienteBiomeSource;
import mcjty.arienteworld.dimension.ArienteChunkGenerator;
import mcjty.arienteworld.dimension.CityFeature;
import mcjty.arienteworld.dimension.FlowerHeightPlacement;
import mcjty.arienteworld.dimension.SwampBiomePlacement;
import mcjty.arienteworld.dimension.features.CrystalFormationFeature;
import mcjty.arienteworld.dimension.features.SpiralTreeFeature;
import mcjty.arienteworld.entities.boss.CoreGuardianEntity;
import mcjty.arienteworld.entities.darkbat.DarkbatEntity;
import mcjty.arienteworld.entities.drone.DroneEntity;
import mcjty.arienteworld.entities.drone.SentinelDroneEntity;
import mcjty.arienteworld.entities.soldier.MasterSoldierEntity;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import mcjty.arienteworld.oregen.OverworldDungeonFeature;
import mcjty.lib.blocks.BaseBlock;
import mcjty.lib.builder.BlockBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import static mcjty.arienteworld.ArienteWorld.MODID;

public class Registration {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, MODID);
    public static final Supplier<MapCodec<? extends ChunkGenerator>> GENERATOR = GENERATORS.register("ariente", () -> ArienteChunkGenerator.CODEC);
    public static final Supplier<MapCodec<? extends BiomeSource>> BIOME_SOURCE = BIOME_SOURCES.register("ariente", () -> ArienteBiomeSource.CODEC);

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MODID);

    public static final Supplier<EntityType<DarkbatEntity>> ENTITY_DARKBAT = ENTITIES.register("darkbat", () -> EntityType.Builder.of(DarkbatEntity::new, MobCategory.MONSTER)
            .sized(0.9F * DarkbatEntity.SCALE, 0.5F * DarkbatEntity.SCALE)
            .clientTrackingRange(8)
            .build("darkbat"));

    public static final Supplier<EntityType<MasterSoldierEntity>> ENTITY_MASTER_SOLDIER = ENTITIES.register("master_soldier", () -> EntityType.Builder.of(MasterSoldierEntity::new, MobCategory.MONSTER)
            .sized(0.7F, 2.7F)
            .setShouldReceiveVelocityUpdates(false)
            .build("master_soldier"));
    public static final Supplier<EntityType<SoldierEntity>> ENTITY_SOLDIER = ENTITIES.register("soldier", () -> EntityType.Builder.of(SoldierEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(16)
            .setShouldReceiveVelocityUpdates(false)
            .build("soldier"));
    public static final Supplier<EntityType<SentinelDroneEntity>> ENTITY_SENTINEL_DRONE = ENTITIES.register("ariente_sentinel_drone", () -> EntityType.Builder.of(SentinelDroneEntity::new, MobCategory.MISC)
            .sized(1.3F, 1.3F)
            .setShouldReceiveVelocityUpdates(false)
            .build("ariente_sentinel_drone"));
    public static final Supplier<EntityType<DroneEntity>> ENTITY_DRONE = ENTITIES.register("ariente_drone", () -> EntityType.Builder.of(DroneEntity::new, MobCategory.MISC)
            .sized(2.0F, 2.0F)
            .setShouldReceiveVelocityUpdates(false)
            .build("ariente_drone"));

    public static final Supplier<EntityType<CoreGuardianEntity>> ENTITY_CORE_GUARDIAN = ENTITIES.register("core_guardian",
            () -> EntityType.Builder.of(CoreGuardianEntity::new, MobCategory.MISC).sized(4, 4)
                    .fireImmune().clientTrackingRange(8).updateInterval(1).build("core_guardian"));

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(BuiltInRegistries.FEATURE, MODID);
    public static final Supplier<OverworldDungeonFeature> ENTRANCE = FEATURES.register("overworld_dungeon", OverworldDungeonFeature::new);
    public static final Supplier<CityFeature> CITY = FEATURES.register("city", CityFeature::new);
    public static final Supplier<SpiralTreeFeature> SPIRAL_TREE = FEATURES.register("spiral_tree", SpiralTreeFeature::new);
    public static final Supplier<CrystalFormationFeature> CRYSTAL_FORMATION = FEATURES.register("crystal_formation", CrystalFormationFeature::new);

    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENTS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, MODID);
    public static final Supplier<PlacementModifierType<FlowerHeightPlacement>> FLOWER_HEIGHT = PLACEMENTS.register("flower_height", () -> () -> FlowerHeightPlacement.CODEC);
    public static final Supplier<PlacementModifierType<SwampBiomePlacement>> SWAMP_BIOME = PLACEMENTS.register("swamp_biome", () -> () -> SwampBiomePlacement.CODEC);

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MODID);

    public static Item.Properties createStandardProperties() {
        return new Item.Properties();
    }

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final Supplier<CreativeModeTab> TAB = TABS.register("arienteworld", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arienteworld"))
            .icon(() -> new ItemStack(Registration.GLOWLOG.get()))
            .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ENTITY_DARKBAT.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DarkbatEntity::checkDarkbatSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ENTITY_SOLDIER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SoldierEntity::checkSoldierSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ENTITY_MASTER_SOLDIER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SoldierEntity::checkSoldierSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ENTITY_CORE_GUARDIAN.get(), CoreGuardianEntity.registerAttributes().build());
        event.put(ENTITY_DARKBAT.get(), DarkbatEntity.registerAttributes().build());
        event.put(ENTITY_SOLDIER.get(), SoldierEntity.registerAttributes().build());
        event.put(ENTITY_MASTER_SOLDIER.get(), MasterSoldierEntity.registerAttributes().build());
        event.put(ENTITY_DRONE.get(), DroneEntity.registerAttributes().build());
        event.put(ENTITY_SENTINEL_DRONE.get(), SentinelDroneEntity.registerAttributes().build());
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(Registration::registerAttributes);
        bus.addListener(Registration::registerSpawnPlacements);
        ModSounds.register();
        GENERATORS.register(bus);
        BIOME_SOURCES.register(bus);
        FEATURES.register(bus);
        PLACEMENTS.register(bus);
        TABS.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
    }

    public static final Supplier<BaseBlock> GUARD_DUMMY = BLOCKS.register("guard_dummy", () -> new DummyBlock(new BlockBuilder()));
    public static final Supplier<Item> GUARD_DUMMY_ITEM = ITEMS.register("guard_dummy", () -> new BlockItem(GUARD_DUMMY.get(), createStandardProperties()));

    public static final Supplier<BaseBlock> SOLDIER_DUMMY = BLOCKS.register("solider_dummy", () -> new DummyBlock(new BlockBuilder()));
    public static final Supplier<Item> SOLDIER_DUMMY_ITEM = ITEMS.register("solider_dummy", () -> new BlockItem(SOLDIER_DUMMY.get(), createStandardProperties()));

    public static final Supplier<BaseBlock> MASTER_SOLDIER_DUMMY = BLOCKS.register("master_solider_dummy", () -> new DummyBlock(new BlockBuilder()));
    public static final Supplier<Item> MASTER_SOLDIER_DUMMY_ITEM = ITEMS.register("master_solider_dummy", () -> new BlockItem(MASTER_SOLDIER_DUMMY.get(), createStandardProperties()));

    public static final Supplier<Block> GLOWLOG = BLOCKS.register("glowlog", () -> new Block(Block.Properties.of().mapColor(MapColor.WOOD).strength(2).sound(SoundType.WOOD).lightLevel(state -> 10)));
    public static final Supplier<Item> GLOWLOG_ITEM = ITEMS.register("glowlog", () -> new BlockItem(GLOWLOG.get(), createStandardProperties()));

    public static final Supplier<Block> BLUELOG = BLOCKS.register("bluelog", () -> new Block(Block.Properties.of().mapColor(MapColor.WOOD).strength(2).sound(SoundType.WOOD)));
    public static final Supplier<Item> BLUELOG_ITEM = ITEMS.register("bluelog", () -> new BlockItem(BLUELOG.get(), createStandardProperties()));

    public static final Supplier<LeavesBlock> GLOWLEAVES = BLOCKS.register("glowleaves", () -> new LeavesBlock(Block.Properties.of().mapColor(MapColor.PLANT).strength(.2f).sound(SoundType.GRASS).noOcclusion().randomTicks().lightLevel(state -> 10)));
    public static final Supplier<Item> GLOWLEAVES_ITEM = ITEMS.register("glowleaves", () -> new BlockItem(GLOWLEAVES.get(), createStandardProperties()));

    public static final Supplier<LeavesBlock> BLUELEAVES = BLOCKS.register("blueleaves", () -> new LeavesBlock(Block.Properties.of().mapColor(MapColor.PLANT).strength(.2f).sound(SoundType.GRASS).noOcclusion().randomTicks()));
    public static final Supplier<Item> BLUELEAVES_ITEM = ITEMS.register("blueleaves", () -> new BlockItem(BLUELEAVES.get(), createStandardProperties()));

    public static final Supplier<LeavesBlock> DARKLEAVES = BLOCKS.register("darkleaves", () -> new LeavesBlock(Block.Properties.of().mapColor(MapColor.PLANT).strength(.2f).sound(SoundType.GRASS).noOcclusion().randomTicks()));
    public static final Supplier<Item> DARKLEAVES_ITEM = ITEMS.register("darkleaves", () -> new BlockItem(DARKLEAVES.get(), createStandardProperties()));

    public static final Supplier<BlockArientePlant> BLACKBUSH = BLOCKS.register("black_bush", BlockArientePlant::new);
    public static final Supplier<Item> BLACKBUSH_ITEM = ITEMS.register("black_bush", () -> new BlockItem(BLACKBUSH.get(), createStandardProperties()));

    public static final Supplier<BlockArientePlant> DARKGRASS = BLOCKS.register("dark_grass", BlockArientePlant::new);
    public static final Supplier<Item> DARKGRASS_ITEM = ITEMS.register("dark_grass", () -> new BlockItem(DARKGRASS.get(), createStandardProperties()));

    public static final Supplier<DoublePlantBlock> TALLDARKGRASS = BLOCKS.register("tall_dark_grass", () -> new DoublePlantBlock(Block.Properties.of()
            .mapColor(MapColor.PLANT).noCollission().instabreak().replaceable().sound(SoundType.GRASS)));
    public static final Supplier<Item> TALLDARKGRASS_ITEM = ITEMS.register("tall_dark_grass", () -> new DoubleHighBlockItem(TALLDARKGRASS.get(), createStandardProperties()));

    public static final Supplier<BlockArientePlant> SMALLFLOWER = BLOCKS.register("small_flower", BlockArientePlant::new);
    public static final Supplier<Item> SMALLFLOWER_ITEM = ITEMS.register("small_flower", () -> new BlockItem(SMALLFLOWER.get(), createStandardProperties()));

    public static final Supplier<Block> LAPISORE = BLOCKS.register("lapisore", () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(3.0f)));
    public static final Supplier<Item> LAPISORE_ITEM = ITEMS.register("lapisore", () -> new BlockItem(LAPISORE.get(), createStandardProperties()));

    public static final Supplier<Block> GLOWSTONE = BLOCKS.register("glowstoneore", () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(3.0f)));
    public static final Supplier<Item> GLOWSTONE_ITEM = ITEMS.register("glowstoneore", () -> new BlockItem(GLOWSTONE.get(), createStandardProperties()));

    public static final Supplier<CrystalBlock> NEGARITE_CRYSTAL = BLOCKS.register("negarite_crystal", () -> new CrystalBlock(MapColor.COLOR_YELLOW));
    public static final Supplier<Item> NEGARITE_CRYSTAL_ITEM = ITEMS.register("negarite_crystal", () -> new BlockItem(NEGARITE_CRYSTAL.get(), createStandardProperties()));

    public static final Supplier<CrystalBlock> POSIRITE_CRYSTAL = BLOCKS.register("posirite_crystal", () -> new CrystalBlock(MapColor.COLOR_BLUE));
    public static final Supplier<Item> POSIRITE_CRYSTAL_ITEM = ITEMS.register("posirite_crystal", () -> new BlockItem(POSIRITE_CRYSTAL.get(), createStandardProperties()));

}
