package mcjty.arienteworld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class WorldDataTests {

    @GameTest(template = "empty")
    public static void authoredWorldDataCodecRoundTrips(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        int count = roundTrip(helper, level, Registries.BIOME, Biome.DIRECT_CODEC)
                + roundTrip(helper, level, Registries.CONFIGURED_FEATURE, ConfiguredFeature.DIRECT_CODEC)
                + roundTrip(helper, level, Registries.PLACED_FEATURE, PlacedFeature.DIRECT_CODEC)
                + roundTrip(helper, level, Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC);
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        var generator = level.getChunkSource().getGenerator();
        var encoded = ChunkGenerator.CODEC.encodeStart(ops, generator).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
        var restored = ChunkGenerator.CODEC.parse(ops, encoded).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
        helper.assertTrue(generator.getClass() == restored.getClass(), "Dimension codec must retain the custom terrain generator");
        ArienteWorld.setup.getLogger().info("WORLD DATA CODEC AUDIT PASSED: {} authored registry entries and custom generator", count);
        helper.succeed();
    }

    private static <T> int roundTrip(GameTestHelper helper, ServerLevel level, ResourceKey<? extends Registry<T>> key, Codec<T> codec) {
        Registry<T> registry = level.registryAccess().registryOrThrow(key);
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        int count = 0;
        for (var entry : registry.entrySet()) {
            if (!entry.getKey().location().getNamespace().equals(ArienteWorld.MODID)) {
                continue;
            }
            var encoded = codec.encodeStart(ops, entry.getValue()).getOrThrow(false, message -> {
                throw new AssertionError(entry.getKey() + ": " + message);
            });
            var restored = codec.parse(ops, encoded).getOrThrow(false, message -> {
                throw new AssertionError(entry.getKey() + ": " + message);
            });
            var repeated = codec.encodeStart(ops, restored).getOrThrow(false, message -> {
                throw new AssertionError(message);
            });
            helper.assertTrue(encoded.equals(repeated), "World data must serialize without drift: " + entry.getKey());
            count++;
        }
        helper.assertTrue(count > 0, "Audit must find the companion's " + key.location() + " entries");
        return count;
    }
}
