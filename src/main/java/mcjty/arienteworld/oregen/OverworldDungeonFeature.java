package mcjty.arienteworld.oregen;

import java.util.Random;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;


public class OverworldDungeonFeature implements Feature {

    public static final MapCodec<OverworldDungeonFeature> CODEC = MapCodec.unit(OverworldDungeonFeature::new);

    @Override
    public MapCodec<OverworldDungeonFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel world, ChunkGenerator chunkGenerator, RandomSource randomSource, BlockPos originPos) {
        ChunkPos chunk = ChunkPos.containing(originPos);
        if (!OverworldDungeonGen.isDungeonChunk(world, chunk.x(), chunk.z())) {
            return false;
        }
        OverworldDungeonGen.generate(new Random(world.getSeed() + chunk.x() * 341873128712L + chunk.z() * 132897987541L),
                chunk.x(), chunk.z(), world);
        return true;
    }
}
