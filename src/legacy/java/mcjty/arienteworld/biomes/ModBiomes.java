package mcjty.arienteworld.biomes;

import mcjty.ariente.api.MarbleColor;
import mcjty.arienteworld.ArienteStuff;
import net.minecraft.block.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.surfacebuilders.ConfiguredSurfaceBuilder;
import net.minecraft.world.gen.surfacebuilders.SurfaceBuilder;
import net.minecraft.world.gen.surfacebuilders.SurfaceBuilderConfig;

public class ModBiomes {

    public static final SurfaceBuilderConfig SURFACE_BUILDER_CONFIG = new SurfaceBuilderConfig(
            Blocks.GRASS_BLOCK.getDefaultState(),
            ArienteStuff.marble.getDefaultState().with(MarbleColor.COLOR, MarbleColor.GRAY),
            ArienteStuff.marble.getDefaultState().with(MarbleColor.COLOR, MarbleColor.GRAY)
    );

    public static BiomeArienteCity createBiomeCity() {
        return new BiomeArienteCity(
                        new Biome.Builder()
                                .surfaceBuilder(new ConfiguredSurfaceBuilder(SurfaceBuilder.DEFAULT, SURFACE_BUILDER_CONFIG))
                                .precipitation(Biome.RainType.RAIN)
                                .category(Biome.Category.PLAINS)
                                .waterColor(0x55ff88)
                                .waterFogColor(0x55ff88)
                                .depth(0.125F)
                                .scale(0.05F)
                                .temperature(0.8F)
                                .downfall(0.4F));
    }

    public static BiomeArienteOcean createBiomeOcean() {
        return new BiomeArienteOcean(
                        new Biome.Builder()
                                .surfaceBuilder(new ConfiguredSurfaceBuilder(SurfaceBuilder.DEFAULT, SURFACE_BUILDER_CONFIG))
                                .precipitation(Biome.RainType.RAIN)
                                .category(Biome.Category.OCEAN)
                                .waterColor(0x55ff88)
                                .waterFogColor(0x55ff88)
                                .temperature(0.8f)
                                .downfall(0.4f)
                                .depth(-1.0F).scale(0.1F));
    }

    public static BiomeArienteRough createBiomeRough() {
        return new BiomeArienteRough(
                        new Biome.Builder()
                                .surfaceBuilder(new ConfiguredSurfaceBuilder(SurfaceBuilder.DEFAULT, SURFACE_BUILDER_CONFIG))
                                .precipitation(Biome.RainType.RAIN)
                                .category(Biome.Category.EXTREME_HILLS)
                                .waterColor(0x55ff88)
                                .waterFogColor(0x55ff88)
                                .depth(0.25F)
                                .scale(0.5F)
                                .temperature(0.8F)
                                .downfall(0.4F));
    }

    public static BiomeArienteHills createBiomeHills() {
        return new BiomeArienteHills(
                        new Biome.Builder()
                                .surfaceBuilder(new ConfiguredSurfaceBuilder(SurfaceBuilder.DEFAULT, SURFACE_BUILDER_CONFIG))
                                .precipitation(Biome.RainType.RAIN)
                                .category(Biome.Category.EXTREME_HILLS)
                                .waterColor(0x55ff88)
                                .waterFogColor(0x55ff88)
                                .depth(0.45F)
                                .scale(0.3F)
                                .temperature(0.8F)
                                .downfall(0.4F));
    }

    public static BiomeArienteForest createBiomeForest() {
        return new BiomeArienteForest(
                        new Biome.Builder()
                                .surfaceBuilder(new ConfiguredSurfaceBuilder(SurfaceBuilder.DEFAULT, SURFACE_BUILDER_CONFIG))
                                .precipitation(Biome.RainType.RAIN)
                                .category(Biome.Category.FOREST)
                                .waterColor(0x55ff88)
                                .waterFogColor(0x55ff88)
                                .depth(0.125F)
                                .scale(0.05F)
                                .temperature(0.8F)
                                .downfall(0.4F));
    }

    public static BiomeArientePlains createBiomePlains() {
        return new BiomeArientePlains(
                        new Biome.Builder()
                                .surfaceBuilder(new ConfiguredSurfaceBuilder(SurfaceBuilder.DEFAULT, SURFACE_BUILDER_CONFIG))
                                .precipitation(Biome.RainType.RAIN)
                                .category(Biome.Category.PLAINS)
                                .waterColor(0x55ff88)
                                .waterFogColor(0x55ff88)
                                .depth(0.125F)
                                .scale(0.05F)
                                .temperature(0.8F)
                                .downfall(0.4F));
    }
}
