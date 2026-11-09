package mcjty.arienteworld.dimension.features;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/** Retains the original patch distribution and random sequence after vanilla removed random_patch. */
public class PlantPatchFeature extends Feature<PlantPatchConfiguration> {

    public PlantPatchFeature() {
        super(PlantPatchConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<PlantPatchConfiguration> context) {
        var config = context.config();
        var random = context.random();
        var position = new BlockPos.MutableBlockPos();
        int horizontal = config.xzSpread() + 1;
        int vertical = config.ySpread() + 1;
        int placed = 0;
        for (int attempt = 0; attempt < config.tries(); attempt++) {
            position.setWithOffset(context.origin(), random.nextInt(horizontal) - random.nextInt(horizontal),
                    random.nextInt(vertical) - random.nextInt(vertical), random.nextInt(horizontal) - random.nextInt(horizontal));
            if (config.feature().value().place(context.level(), context.chunkGenerator(), random, position)) {
                placed++;
            }
        }
        return placed > 0;
    }
}
