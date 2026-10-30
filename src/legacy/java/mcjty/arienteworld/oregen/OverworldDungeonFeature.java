package mcjty.arienteworld.oregen;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationSettings;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;

import java.util.Random;

public class OverworldDungeonFeature extends Feature<NoFeatureConfig> {

    public OverworldDungeonFeature() {
        super(NoFeatureConfig::deserialize);
    }

    @Override
    public boolean place(IWorld worldIn, ChunkGenerator<? extends GenerationSettings> generator, Random rand, BlockPos pos, NoFeatureConfig config) {
        ChunkPos cp = new ChunkPos(pos);
        OverworldDungeonGen.generate(rand, cp.x, cp.z, worldIn, generator);
        return true;
    }
}
