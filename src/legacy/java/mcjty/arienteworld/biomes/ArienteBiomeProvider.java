package mcjty.arienteworld.biomes;

import com.google.common.collect.ImmutableList;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.IExtendedNoiseRandom;
import net.minecraft.world.gen.LazyAreaLayerContext;
import net.minecraft.world.gen.area.IArea;
import net.minecraft.world.gen.area.IAreaFactory;
import net.minecraft.world.gen.area.LazyArea;
import net.minecraft.world.gen.layer.Layer;
import net.minecraft.world.gen.layer.ZoomLayer;

import java.util.HashSet;
import java.util.Set;
import java.util.function.LongFunction;

public class ArienteBiomeProvider extends BiomeProvider {

    private static Set<Biome> BIOMES;
    private final Layer genBiomes;

    public ArienteBiomeProvider(World world) {
        super(getBiomes());

        //generates the world and biome layouts
        Layer[] agenlayer = buildOverworldProcedure(world.getSeed(), world.getWorldType());
        this.genBiomes = agenlayer[0];
        ArienteBiomeLayer.setSeed(world.getSeed());

        // @todo 1.15
//        getBiomesToSpawnIn().clear();
//        getBiomesToSpawnIn().add(Registration.ARIENTE_PLAINS.get());
//        getBiomesToSpawnIn().add(Registration.ARIENTE_HILLS.get());
//        getBiomesToSpawnIn().add(Registration.ARIENTE_OCEAN.get());
//        getBiomesToSpawnIn().add(Registration.ARIENTE_FOREST.get());
//        getBiomesToSpawnIn().add(Registration.ARIENTE_ROUGH.get());
//        getBiomesToSpawnIn().add(Registration.ARIENTE_CITY.get());
//        makeLayers(world.getSeed());
    }

    public static Layer[] buildOverworldProcedure(long seed, WorldType typeIn) {
        ImmutableList<IAreaFactory<LazyArea>> immutablelist = buildOverworldProcedure(typeIn, (p_215737_2_) -> {
            return new LazyAreaLayerContext(25, seed, p_215737_2_);
        });
        Layer genlayer = new Layer(immutablelist.get(0));
        Layer genlayer1 = new Layer(immutablelist.get(1));
        Layer genlayer2 = new Layer(immutablelist.get(2));
        return new Layer[]{genlayer, genlayer1, genlayer2};
    }

    public static <T extends IArea, C extends IExtendedNoiseRandom<T>> ImmutableList<IAreaFactory<T>> buildOverworldProcedure(WorldType worldTypeIn, LongFunction<C> contextFactory) {
        IAreaFactory<T> layer = ArienteBiomeLayer.INSTANCE.apply(contextFactory.apply(200L));
        layer = ZoomLayer.FUZZY.apply(contextFactory.apply(2000L), layer);
        layer = ZoomLayer.NORMAL.apply(contextFactory.apply(1001L), layer);
        layer = ZoomLayer.NORMAL.apply(contextFactory.apply(1001L), layer);
        return ImmutableList.of(layer, layer, layer);
    }


    private static Set<Biome> getBiomes() {
        if (BIOMES == null) {
            BIOMES = new HashSet<>();
            BIOMES.add(Registration.ARIENTE_PLAINS.get());
            BIOMES.add(Registration.ARIENTE_HILLS.get());
            BIOMES.add(Registration.ARIENTE_OCEAN.get());
            BIOMES.add(Registration.ARIENTE_FOREST.get());
            BIOMES.add(Registration.ARIENTE_ROUGH.get());
            BIOMES.add(Registration.ARIENTE_CITY.get());
        }
        return BIOMES;
    }

    @Override
    public Biome getNoiseBiome(int x, int y, int z) {
        return genBiomes.func_215738_a(x, z);
    }

//    private void makeLayers(long seed) {
        // @todo 1.15
//        GenLayer biomes = new GenLayerArienteBiomes(1L);
//
//        biomes = new GenLayerZoom(1000, biomes);
//        biomes = new GenLayerZoom(1001, biomes);
//
//        biomes = new GenLayerZoom(1002, biomes);
//        biomes = new GenLayerZoom(1003, biomes);
//        biomes = new GenLayerZoom(1004, biomes);
//        biomes = new GenLayerZoom(1005, biomes);
//        biomes = new GenLayerZoom(1006, biomes);
//
//        GenLayer genlayervoronoizoom = new GenLayerVoronoiZoom(10L, biomes);
//
//        biomes.initWorldGenSeed(seed);
//        genlayervoronoizoom.initWorldGenSeed(seed);
//
//        genBiomes = biomes;
//        biomeIndexLayer = genlayervoronoizoom;
//    }

}
