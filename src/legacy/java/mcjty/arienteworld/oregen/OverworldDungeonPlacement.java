package mcjty.arienteworld.oregen;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationSettings;
import net.minecraft.world.gen.placement.NoPlacementConfig;
import net.minecraft.world.gen.placement.Placement;

import java.util.Random;
import java.util.stream.Stream;

public class OverworldDungeonPlacement extends Placement<NoPlacementConfig> {

    public OverworldDungeonPlacement() {
        super(NoPlacementConfig::deserialize);
    }

    @Override
    public Stream<BlockPos> getPositions(IWorld worldIn, ChunkGenerator<? extends GenerationSettings> generatorIn, Random random, NoPlacementConfig configIn, BlockPos pos) {
        if (worldIn.getDimension().getType() != DimensionType.OVERWORLD) {
            return Stream.empty();
        }
        ChunkPos cp = new ChunkPos(pos);
        if (OverworldDungeonGen.isDungeonChunk(worldIn, cp.x, cp.z)) {
            return Stream.of(OverworldDungeonGen.getDungeonPos(worldIn, cp.x, cp.z));
        } else {
            return Stream.empty();
        }
    }
}
