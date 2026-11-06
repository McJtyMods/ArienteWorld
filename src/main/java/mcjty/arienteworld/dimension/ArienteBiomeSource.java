package mcjty.arienteworld.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mcjty.arienteworld.dimension.terrain.ArienteBiomeLayout;
import mcjty.arienteworld.dimension.terrain.BiomeProfile;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.stream.Stream;

public class ArienteBiomeSource extends BiomeSource {

    public static final Codec<ArienteBiomeSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Biome.CODEC.listOf().fieldOf("biomes").forGetter(source -> source.biomes)
    ).apply(instance, ArienteBiomeSource::new));

    private final List<Holder<Biome>> biomes;
    private ArienteBiomeLayout layout = new ArienteBiomeLayout(0);

    public ArienteBiomeSource(List<Holder<Biome>> biomes) {
        if (biomes.size() != BiomeProfile.VALUES.length) {
            throw new IllegalArgumentException("Ariente requires " + BiomeProfile.VALUES.length + " biomes in profile order");
        }
        this.biomes = List.copyOf(biomes);
    }

    public void initialize(long seed) {
        layout = new ArienteBiomeLayout(seed);
    }

    public ArienteBiomeLayout getLayout() {
        return layout;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return biomes.get(layout.get(x, z).ordinal());
    }
}
