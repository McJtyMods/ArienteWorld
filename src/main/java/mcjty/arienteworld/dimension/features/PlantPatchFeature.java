package mcjty.arienteworld.dimension.features;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;

/** Retains the original patch distribution and random sequence after vanilla removed random_patch. */
public record PlantPatchFeature(PlantPatchConfiguration config) implements Feature {

    public static final MapCodec<PlantPatchFeature> CODEC = PlantPatchConfiguration.CODEC.xmap(PlantPatchFeature::new, PlantPatchFeature::config);

    @Override
    public MapCodec<PlantPatchFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        var placer = new FeaturePlacer(level, generator);
        var position = new BlockPos.MutableBlockPos();
        int horizontal = config.xzSpread() + 1;
        int vertical = config.ySpread() + 1;
        int placed = 0;
        for (int attempt = 0; attempt < config.tries(); attempt++) {
            position.setWithOffset(origin, random.nextInt(horizontal) - random.nextInt(horizontal),
                    random.nextInt(vertical) - random.nextInt(vertical), random.nextInt(horizontal) - random.nextInt(horizontal));
            if (placer.place(config.feature().value(), random, position)) {
                placed++;
            }
        }
        return placed > 0;
    }
}
