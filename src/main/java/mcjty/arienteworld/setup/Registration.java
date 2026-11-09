package mcjty.arienteworld.setup;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.SpawnPlacements;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import mcjty.arienteworld.dimension.features.PlantPatchFeature;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;

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
import mcjty.lib.setup.DeferredRegister;

import static mcjty.arienteworld.ArienteWorld.MODID;

public class Registration {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, MODID);
    public static final Supplier<MapCodec<? extends ChunkGenerator>> GENERATOR = GENERATORS.register("ariente", () -> ArienteChunkGenerator.CODEC);
    public static final Supplier<MapCodec<? extends BiomeSource>> BIOME_SOURCE = BIOME_SOURCES.register("ariente", () -> ArienteBiomeSource.CODEC);

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final Supplier<EntityType<DarkbatEntity>> ENTITY_DARKBAT = ENTITIES.register("darkbat", () -> EntityType.Builder.of(DarkbatEntity::new, MobCategory.MONSTER)
            .sized(0.9F * DarkbatEntity.SCALE, 0.5F * DarkbatEntity.SCALE)
            .clientTrackingRange(8)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "darkbat"))));

    public static final Supplier<EntityType<MasterSoldierEntity>> ENTITY_MASTER_SOLDIER = ENTITIES.register("master_soldier", () -> EntityType.Builder.of(MasterSoldierEntity::new, MobCategory.MONSTER)
            .sized(0.7F, 2.7F)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "master_soldier"))));
    public static final Supplier<EntityType<SoldierEntity>> ENTITY_SOLDIER = ENTITIES.register("soldier", () -> EntityType.Builder.of(SoldierEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(16)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "soldier"))));
    public static final Supplier<EntityType<SentinelDroneEntity>> ENTITY_SENTINEL_DRONE = ENTITIES.register("ariente_sentinel_drone", () -> EntityType.Builder.of(SentinelDroneEntity::new, MobCategory.MISC)
            .sized(1.3F, 1.3F)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "ariente_sentinel_drone"))));
    public static final Supplier<EntityType<DroneEntity>> ENTITY_DRONE = ENTITIES.register("ariente_drone", () -> EntityType.Builder.of(DroneEntity::new, MobCategory.MISC)
            .sized(2.0F, 2.0F)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "ariente_drone"))));

    public static final Supplier<EntityType<CoreGuardianEntity>> ENTITY_CORE_GUARDIAN = ENTITIES.register("core_guardian",
            () -> EntityType.Builder.of(CoreGuardianEntity::new, MobCategory.MISC).sized(4, 4)
                    .fireImmune().clientTrackingRange(8).updateInterval(1).build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "core_guardian"))));

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MODID);
    public static final Supplier<OverworldDungeonFeature> ENTRANCE = FEATURES.register("overworld_dungeon", OverworldDungeonFeature::new);
    public static final Supplier<PlantPatchFeature> PLANT_PATCH = FEATURES.register("plant_patch", PlantPatchFeature::new);
    public static final Supplier<CityFeature> CITY = FEATURES.register("city", CityFeature::new);
    public static final Supplier<SpiralTreeFeature> SPIRAL_TREE = FEATURES.register("spiral_tree", SpiralTreeFeature::new);
    public static final Supplier<CrystalFormationFeature> CRYSTAL_FORMATION = FEATURES.register("crystal_formation", CrystalFormationFeature::new);

    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENTS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, MODID);
    public static final Supplier<PlacementModifierType<FlowerHeightPlacement>> FLOWER_HEIGHT = PLACEMENTS.register("flower_height", () -> () -> FlowerHeightPlacement.CODEC);
    public static final Supplier<PlacementModifierType<SwampBiomePlacement>> SWAMP_BIOME = PLACEMENTS.register("swamp_biome", () -> () -> SwampBiomePlacement.CODEC);

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<TicketType> TICKETS = DeferredRegister.create(Registries.TICKET_TYPE, MODID);
    public static final Supplier<TicketType> SCOUT_FLIGHT = TICKETS.register("scout_flight",
            () -> new TicketType(60, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));
    public static final Supplier<TicketType> SCOUT_LAUNCH = TICKETS.register("scout_launch",
            () -> new TicketType(220, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    public static Item.Properties createStandardProperties() {
        return new Item.Properties();
    }

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final Supplier<CreativeModeTab> TAB = TABS.register("arienteworld", () -> FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arienteworld"))
            .icon(() -> new ItemStack(Registration.GLOWLOG.get()))
            .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    private static void registerSpawnPlacements() {
        SpawnPlacements.register(ENTITY_DARKBAT.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DarkbatEntity::checkDarkbatSpawnRules);
        SpawnPlacements.register(ENTITY_SOLDIER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SoldierEntity::checkSoldierSpawnRules);
        SpawnPlacements.register(ENTITY_MASTER_SOLDIER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SoldierEntity::checkSoldierSpawnRules);
    }

    private static void registerAttributes() {
        FabricDefaultAttributeRegistry.register(ENTITY_CORE_GUARDIAN.get(), CoreGuardianEntity.registerAttributes().build());
        FabricDefaultAttributeRegistry.register(ENTITY_DARKBAT.get(), DarkbatEntity.registerAttributes().build());
        FabricDefaultAttributeRegistry.register(ENTITY_SOLDIER.get(), SoldierEntity.registerAttributes().build());
        FabricDefaultAttributeRegistry.register(ENTITY_MASTER_SOLDIER.get(), MasterSoldierEntity.registerAttributes().build());
        FabricDefaultAttributeRegistry.register(ENTITY_DRONE.get(), DroneEntity.registerAttributes().build());
        FabricDefaultAttributeRegistry.register(ENTITY_SENTINEL_DRONE.get(), SentinelDroneEntity.registerAttributes().build());
    }

    public static void register() {
        ENTITIES.register();
        registerAttributes();
        registerSpawnPlacements();
        ModSounds.register();
        GENERATORS.register();
        BIOME_SOURCES.register();
        FEATURES.register();
        PLACEMENTS.register();
        TABS.register();
        BLOCKS.register();
        ITEMS.register();
        SOUNDS.register();
        TICKETS.register();
    }

    public static final Supplier<BaseBlock> GUARD_DUMMY = BLOCKS.registerBlock("guard_dummy", properties -> new DummyBlock(new BlockBuilder().properties(properties)));
    public static final Supplier<Item> GUARD_DUMMY_ITEM = ITEMS.registerItem("guard_dummy", properties -> new BlockItem(GUARD_DUMMY.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<BaseBlock> SOLDIER_DUMMY = BLOCKS.registerBlock("solider_dummy", properties -> new DummyBlock(new BlockBuilder().properties(properties)));
    public static final Supplier<Item> SOLDIER_DUMMY_ITEM = ITEMS.registerItem("solider_dummy", properties -> new BlockItem(SOLDIER_DUMMY.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<BaseBlock> MASTER_SOLDIER_DUMMY = BLOCKS.registerBlock("master_solider_dummy", properties -> new DummyBlock(new BlockBuilder().properties(properties)));
    public static final Supplier<Item> MASTER_SOLDIER_DUMMY_ITEM = ITEMS.registerItem("master_solider_dummy", properties -> new BlockItem(MASTER_SOLDIER_DUMMY.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<Block> GLOWLOG = BLOCKS.registerBlock("glowlog", properties -> new Block(properties.mapColor(MapColor.WOOD).strength(2).sound(SoundType.WOOD).lightLevel(state -> 10)));
    public static final Supplier<Item> GLOWLOG_ITEM = ITEMS.registerItem("glowlog", properties -> new BlockItem(GLOWLOG.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<Block> BLUELOG = BLOCKS.registerBlock("bluelog", properties -> new Block(properties.mapColor(MapColor.WOOD).strength(2).sound(SoundType.WOOD)));
    public static final Supplier<Item> BLUELOG_ITEM = ITEMS.registerItem("bluelog", properties -> new BlockItem(BLUELOG.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<LeavesBlock> GLOWLEAVES = BLOCKS.registerBlock("glowleaves", properties -> new TintedParticleLeavesBlock(0, properties.mapColor(MapColor.PLANT).strength(.2f).sound(SoundType.GRASS).noOcclusion().randomTicks().lightLevel(state -> 10)));
    public static final Supplier<Item> GLOWLEAVES_ITEM = ITEMS.registerItem("glowleaves", properties -> new BlockItem(GLOWLEAVES.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<LeavesBlock> BLUELEAVES = BLOCKS.registerBlock("blueleaves", properties -> new TintedParticleLeavesBlock(0, properties.mapColor(MapColor.PLANT).strength(.2f).sound(SoundType.GRASS).noOcclusion().randomTicks()));
    public static final Supplier<Item> BLUELEAVES_ITEM = ITEMS.registerItem("blueleaves", properties -> new BlockItem(BLUELEAVES.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<LeavesBlock> DARKLEAVES = BLOCKS.registerBlock("darkleaves", properties -> new TintedParticleLeavesBlock(0, properties.mapColor(MapColor.PLANT).strength(.2f).sound(SoundType.GRASS).noOcclusion().randomTicks()));
    public static final Supplier<Item> DARKLEAVES_ITEM = ITEMS.registerItem("darkleaves", properties -> new BlockItem(DARKLEAVES.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<BlockArientePlant> BLACKBUSH = BLOCKS.registerBlock("black_bush", BlockArientePlant::new);
    public static final Supplier<Item> BLACKBUSH_ITEM = ITEMS.registerItem("black_bush", properties -> new BlockItem(BLACKBUSH.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<BlockArientePlant> DARKGRASS = BLOCKS.registerBlock("dark_grass", BlockArientePlant::new);
    public static final Supplier<Item> DARKGRASS_ITEM = ITEMS.registerItem("dark_grass", properties -> new BlockItem(DARKGRASS.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<DoublePlantBlock> TALLDARKGRASS = BLOCKS.registerBlock("tall_dark_grass", properties -> new DoublePlantBlock(properties
            .mapColor(MapColor.PLANT).noCollision().instabreak().replaceable().sound(SoundType.GRASS)));
    public static final Supplier<Item> TALLDARKGRASS_ITEM = ITEMS.registerItem("tall_dark_grass", properties -> new DoubleHighBlockItem(TALLDARKGRASS.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<BlockArientePlant> SMALLFLOWER = BLOCKS.registerBlock("small_flower", BlockArientePlant::new);
    public static final Supplier<Item> SMALLFLOWER_ITEM = ITEMS.registerItem("small_flower", properties -> new BlockItem(SMALLFLOWER.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<Block> LAPISORE = BLOCKS.registerBlock("lapisore", properties -> new Block(properties.mapColor(MapColor.STONE).strength(3.0f)));
    public static final Supplier<Item> LAPISORE_ITEM = ITEMS.registerItem("lapisore", properties -> new BlockItem(LAPISORE.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<Block> GLOWSTONE = BLOCKS.registerBlock("glowstoneore", properties -> new Block(properties.mapColor(MapColor.STONE).strength(3.0f)));
    public static final Supplier<Item> GLOWSTONE_ITEM = ITEMS.registerItem("glowstoneore", properties -> new BlockItem(GLOWSTONE.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<CrystalBlock> NEGARITE_CRYSTAL = BLOCKS.registerBlock("negarite_crystal", properties -> new CrystalBlock(properties, MapColor.COLOR_YELLOW));
    public static final Supplier<Item> NEGARITE_CRYSTAL_ITEM = ITEMS.registerItem("negarite_crystal", properties -> new BlockItem(NEGARITE_CRYSTAL.get(), properties.useBlockDescriptionPrefix()));

    public static final Supplier<CrystalBlock> POSIRITE_CRYSTAL = BLOCKS.registerBlock("posirite_crystal", properties -> new CrystalBlock(properties, MapColor.COLOR_BLUE));
    public static final Supplier<Item> POSIRITE_CRYSTAL_ITEM = ITEMS.registerItem("posirite_crystal", properties -> new BlockItem(POSIRITE_CRYSTAL.get(), properties.useBlockDescriptionPrefix()));

}
