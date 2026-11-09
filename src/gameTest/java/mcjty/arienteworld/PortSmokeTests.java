package mcjty.arienteworld;

import com.mojang.serialization.Codec;
import mcjty.ariente.varia.SavedDataMigration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import mcjty.arienteworld.dimension.DungeonDisplays;
import mcjty.ariente.display.DisplayModuleItem;
import mcjty.ariente.display.DisplayBaseTile;
import java.util.Objects;
import mcjty.ariente.Ariente;
import mcjty.ariente.compat.arienteworld.ArienteWorldCompat;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

@EventBusSubscriber(modid = ArienteWorld.MODID)
public class PortSmokeTests {

    @SubscribeEvent
    public static void functions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> {
            registry.register(Identifier.parse("arienteworld:dimension_and_integration"), PortSmokeTests::dimensionAndIntegration);
            registry.register(Identifier.parse("arienteworld:worldgen_displays_use_the_generation_lookup"), PortSmokeTests::worldgenDisplaysUseTheGenerationLookup);
            registry.register(Identifier.parse("arienteworld:legacy_saved_data"), PortSmokeTests::legacySavedData);
        });
    }

    @SubscribeEvent
    public static void tests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(Identifier.parse("arienteworld:port"));
        event.registerTest(Identifier.parse("arienteworld:dimension_and_integration"), data ->
                new FunctionGameTestInstance(ResourceKey.create(
                        Registries.TEST_FUNCTION, Identifier.parse("arienteworld:dimension_and_integration")), data),
                new TestData<>(environment, Identifier.parse("arienteworld:empty"), 200, 0, true));
        event.registerTest(Identifier.parse("arienteworld:worldgen_displays_use_the_generation_lookup"), data ->
                new FunctionGameTestInstance(ResourceKey.create(
                        Registries.TEST_FUNCTION, Identifier.parse("arienteworld:worldgen_displays_use_the_generation_lookup")), data),
                new TestData<>(environment, Identifier.parse("arienteworld:empty"), 200, 0, true));
        event.registerTest(Identifier.parse("arienteworld:legacy_saved_data"), data ->
                new FunctionGameTestInstance(ResourceKey.create(
                        Registries.TEST_FUNCTION, Identifier.parse("arienteworld:legacy_saved_data")), data),
                new TestData<>(environment, Identifier.parse("arienteworld:empty"), 200, 0, true));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }

    public static void dimensionAndIntegration(GameTestHelper helper) {
        ServerLevel dimension = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        check(helper, dimension != null, "Ariente dimension must load from its data pack");
        dimension.getChunk(0, 0);
        int surface = dimension.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        check(helper, surface > dimension.getMinY(), "Basic dimension must generate terrain");
        check(helper, Ariente.guiHandler != null, "HoloGui integration must initialize");
        check(helper, ArienteWorldCompat.getArienteWorld() != null, "Companion integration must initialize");
        ChunkPos expected = CityTools.isCityCenter(dimension.getSeed(), 8, 8) ? new ChunkPos(8, 8) : null;
        check(helper, Objects.equals(ArienteWorldCompat.getArienteWorld().getNearestCityCenter(new ChunkPos(0, 0)), expected),
                "City lookup must respect configured presence on the original sixteen-chunk grid");
        BlockPos testPos = new BlockPos(8, surface + 2, 8);
        dimension.setBlockAndUpdate(testPos, Registration.GLOWLOG.get().defaultBlockState());
        check(helper, dimension.getBlockState(testPos).getBlock() == Registration.GLOWLOG.get(),
                "Companion block must place in its dimension");
        helper.succeed();
    }

    public static void worldgenDisplaysUseTheGenerationLookup(GameTestHelper helper) {
        var block = mcjty.ariente.setup.Registration.DISPLAY_BASE.get();
        var display = new DisplayBaseTile(BlockPos.ZERO, block.defaultBlockState());
        DungeonDisplays.configure(display,
                Map.of("AlienText", "Generation-time console"), helper.getLevel().registryAccess());
        check(helper, display.getLevel() == null, "Generation can initialize a prototype before it is bound to a world");
        check(helper, display.modules().size() == 1 && "Generation-time console".equals(
                DisplayModuleItem.config(display.modules().getFirst().stack()).getStringOr("Text", "")),
                "The generated console retains its module and item configuration");
        var saved = display.saveWithoutMetadata(helper.getLevel().registryAccess());
        var restored = new DisplayBaseTile(BlockPos.ZERO, block.defaultBlockState());
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), saved));
        check(helper, restored.getLevel() == null && restored.modules().size() == 1
                        && "Generation-time console".equals(DisplayModuleItem.config(restored.modules().getFirst().stack()).getStringOr("Text", "")),
                "Prototype displays must save and reload with the supplied registry context before joining a Level");
        helper.succeed();
    }

    public static void legacySavedData(GameTestHelper helper) {
        for (ServerLevel level : new ServerLevel[]{helper.getLevel(), helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION)}) {
            check(helper, level != null, "Both dimensions must exist for saved-data migration");
            Path directory = DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT)).resolve("data");
            String legacyName = "ArientePortLegacyTest";
            Path legacy = directory.resolve(legacyName + ".dat");
            Identifier migratedId = Identifier.parse("arienteworld:port_migrated_data");
            Identifier existingId = Identifier.parse("arienteworld:port_existing_data");
            try {
                writeSavedData(legacy, 123);
                writeSavedData(existingId.withSuffix(".dat").resolveAgainst(directory), 77);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            SavedDataMigration.migrate(level, migratedId, legacyName);
            var migrated = level.getDataStorage().get(migrationType(migratedId));
            check(helper, migrated != null && migrated.value == 123, "The native storage must read the migrated legacy payload");
            check(helper, Files.isRegularFile(legacy), "Migration must retain the original file");
            SavedDataMigration.migrate(level, existingId, legacyName);
            var existing = level.getDataStorage().get(migrationType(existingId));
            check(helper, existing != null && existing.value == 77, "Existing modern saved data must take precedence");
        }
        helper.succeed();
    }

    private static void writeSavedData(Path path, int value) throws IOException {
        Files.createDirectories(path.getParent());
        CompoundTag data = new CompoundTag();
        data.putInt("value", value);
        CompoundTag saved = new CompoundTag();
        saved.put("data", data);
        saved.putInt("DataVersion", 3955);
        NbtIo.writeCompressed(saved, path);
    }

    private static SavedDataType<MigrationData> migrationType(Identifier id) {
        return new SavedDataType<>(id, MigrationData::new,
                Codec.INT.fieldOf("value").codec().xmap(MigrationData::new, data -> data.value));
    }

    private static class MigrationData extends SavedData {

        private final int value;

        private MigrationData() {
            this(0);
        }

        private MigrationData(int value) {
            this.value = value;
        }
    }
}
