package mcjty.arienteworld.dimension.features;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public record PlantPatchConfiguration(int tries, int xzSpread, int ySpread, Holder<PlacedFeature> feature) implements FeatureConfiguration {

    public static final Codec<PlantPatchConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("tries", 128).forGetter(PlantPatchConfiguration::tries),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("xz_spread", 7).forGetter(PlantPatchConfiguration::xzSpread),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("y_spread", 3).forGetter(PlantPatchConfiguration::ySpread),
            PlacedFeature.CODEC.fieldOf("feature").forGetter(PlantPatchConfiguration::feature)
    ).apply(instance, PlantPatchConfiguration::new));
}
