package mcjty.arienteworld.oregen;

import java.util.Random;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class OverworldDungeonFeature extends Feature<NoneFeatureConfiguration> {

    public OverworldDungeonFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        ChunkPos chunk = new ChunkPos(context.origin());
        if (!OverworldDungeonGen.isDungeonChunk(context.level(), chunk.x, chunk.z)) {
            return false;
        }
        OverworldDungeonGen.generate(new Random(context.level().getSeed() + chunk.x * 341873128712L + chunk.z * 132897987541L),
                chunk.x, chunk.z, context.level());
        return true;
    }
}
