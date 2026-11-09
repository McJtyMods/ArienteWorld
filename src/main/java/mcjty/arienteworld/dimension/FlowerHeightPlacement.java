package mcjty.arienteworld.dimension;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;


/** Original flower patches sample from Y=0 up to the surface plus 32, not just the surface. */
public class FlowerHeightPlacement implements PlacementModifier {

    public static final MapCodec<FlowerHeightPlacement> CODEC = MapCodec.unit(FlowerHeightPlacement::new);

    @Override
    public void modify(PlacementContext context, RandomSource random, BlockPos pos, Consumer<BlockPos> output) {
        int height = context.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ()) + 32;
        if (height > 0) output.accept(pos.atY(random.nextInt(height)));
    }

    @Override
    public MapCodec<FlowerHeightPlacement> codec() {
        return CODEC;
    }
}
