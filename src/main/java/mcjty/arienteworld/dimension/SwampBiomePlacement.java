package mcjty.arienteworld.dimension;

import com.mojang.serialization.Codec;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/** Checks each plant in a patch so swamp foliage cannot spill into neighboring biomes. */
public class SwampBiomePlacement extends PlacementModifier {

    public static final Codec<SwampBiomePlacement> CODEC = Codec.unit(SwampBiomePlacement::new);
    private static final ResourceKey<Biome> SWAMP = ResourceKey.create(Registries.BIOME,
            new ResourceLocation(ArienteWorld.MODID, "ariente_swamp"));

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        return context.getLevel().getBiome(pos).is(SWAMP) ? Stream.of(pos) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return Registration.SWAMP_BIOME.get();
    }
}
