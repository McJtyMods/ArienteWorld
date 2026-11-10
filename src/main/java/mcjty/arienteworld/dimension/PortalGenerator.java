package mcjty.arienteworld.dimension;

import static mcjty.ariente.setup.Registration.WARPER;

import mcjty.ariente.api.IWarper;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.cities.Transform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

public class PortalGenerator {

    public static boolean isPortalChunk(int x, int z) {
        return (x & 15) == 0 && (z & 15) == 0;
    }

    public static int getHeight(ChunkPos chunk, ChunkGenerator generator, LevelHeightAccessor level, RandomState random) {
        int height = 160;
        for (int x = 4; x <= 10; x++) {
            for (int z = 4; z <= 10; z++) {
                height = Math.min(height, generator.getBaseHeight(chunk.getMinBlockX() + x, chunk.getMinBlockZ() + z,
                        Heightmap.Types.WORLD_SURFACE_WG, level, random));
            }
        }
        return Math.max(level.getMinBuildHeight() + 2, height);
    }

    public static void generate(CityChunk writer, ChunkPos chunk, ChunkGenerator generator) {
        int height = getHeight(chunk, generator, writer.getLevel(), writer.getLevel().getLevel().getChunkSource().randomState());
        writer.placePart("portal", AssetRegistries.PARTS.get("portal"), Transform.ROTATE_NONE, 4, height, 4);
        var foundation = LegacyBlockStates.read("ariente:marble_smooth@color=black");
        for (int x = 4; x <= 10; x++) {
            for (int z = 4; z <= 10; z++) {
                for (int y = height - 1; y > writer.getLevel().getMinBuildHeight(); y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (writer.getBlockState(pos).isSolidRender(writer.getLevel(), writer.absolute(pos))) {
                        break;
                    }
                    writer.setBlockState(pos, foundation, false);
                }
            }
        }
    }

    public static BlockPos getArrival(ServerLevel level, BlockPos source) {
        ChunkPos chunk = new ChunkPos((source.getX() >> 4) & ~15, (source.getZ() >> 4) & ~15);
        level.getChunk(chunk.x, chunk.z);
        int x = chunk.getMinBlockX();
        int z = chunk.getMinBlockZ();
        for (int y = level.getMaxBuildHeight() - 4; y > level.getMinBuildHeight(); y--) {
            BlockPos warper = new BlockPos(x + 7, y, z + 7);
            if (level.getBlockState(warper).is(WARPER.get()) && level.getBlockEntity(warper) instanceof IWarper
                    && level.isEmptyBlock(new BlockPos(x + 8, y + 1, z + 8))
                    && level.isEmptyBlock(new BlockPos(x + 8, y + 2, z + 8))) {
                return new BlockPos(x + 8, y + 1, z + 8);
            }
        }
        // Older chunks may predate portal generation. Build a clear arrival platform above them.
        int height = level.getMinBuildHeight();
        for (int dx = 4; dx <= 10; dx++) {
            for (int dz = 4; dz <= 10; dz++) {
                height = Math.max(height, level.getHeight(Heightmap.Types.WORLD_SURFACE, x + dx, z + dz));
            }
        }
        height++;
        if (height + 5 >= level.getMaxBuildHeight()) {
            throw new IllegalStateException("No vertical room for an Ariente arrival portal");
        }
        CityFeature.placePart(level, new BlockPos(x + 4, height, z + 4), AssetRegistries.PARTS.get("portal"), AssetRegistries.PALETTES.get("portal"));
        return new BlockPos(x + 8, height + 1, z + 8);
    }
}
