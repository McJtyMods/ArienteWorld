package mcjty.arienteworld.dimension;

import com.mojang.serialization.Codec;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/** Original flower patches sample from Y=0 up to the surface plus 32, not just the surface. */
public class FlowerHeightPlacement extends PlacementModifier {

    public static final Codec<FlowerHeightPlacement> CODEC = Codec.unit(FlowerHeightPlacement::new);

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int height = context.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ()) + 32;
        return height > 0 ? Stream.of(pos.atY(random.nextInt(height))) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return Registration.FLOWER_HEIGHT.get();
    }
}
