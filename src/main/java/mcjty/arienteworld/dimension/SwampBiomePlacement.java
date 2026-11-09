package mcjty.arienteworld.dimension;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;


/** Checks each plant in a patch so swamp foliage cannot spill into neighboring biomes. */
public class SwampBiomePlacement implements PlacementModifier {

    public static final MapCodec<SwampBiomePlacement> CODEC = MapCodec.unit(SwampBiomePlacement::new);
    private static final ResourceKey<Biome> SWAMP = ResourceKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(ArienteWorld.MODID, "ariente_swamp"));

    @Override
    public void modify(PlacementContext context, RandomSource random, BlockPos pos, Consumer<BlockPos> output) {
        if (context.getLevel().getBiome(pos).is(SWAMP)) output.accept(pos);
    }

    @Override
    public MapCodec<SwampBiomePlacement> codec() {
        return CODEC;
    }
}
