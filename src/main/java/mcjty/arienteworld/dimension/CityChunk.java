package mcjty.arienteworld.dimension;

import net.minecraft.core.registries.Registries;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.Transform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Restricts the old local-coordinate generators to the chunk being decorated. */
public class CityChunk {

    private final WorldGenLevel level;
    private final ChunkPos chunk;

    public CityChunk(WorldGenLevel level, ChunkPos chunk) {
        this.level = level;
        this.chunk = chunk;
    }

    public WorldGenLevel getLevel() {
        return level;
    }

    public BlockPos absolute(BlockPos pos) {
        return pos.offset(chunk.getMinBlockX(), 0, chunk.getMinBlockZ());
    }

    public BlockState getBlockState(BlockPos pos) {
        return level.getBlockState(absolute(pos));
    }

    public void setBlockState(BlockPos pos, BlockState state, boolean moving) {
        if (pos.getX() < 0 || pos.getX() > 15 || pos.getZ() < 0 || pos.getZ() > 15) {
            throw new IllegalArgumentException("City generation crossed its chunk boundary: " + pos);
        }
        BlockPos target = absolute(pos);
        level.setBlock(target, state, Block.UPDATE_CLIENTS);
        if (state.hasBlockEntity() && !CityFeature.hasBlockEntity(state)) {
            level.getChunk(target).removeBlockEntity(target);
        }
    }

    public void fill(BlockState state, int bottom, int top) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bottom; y < top; y++) {
                    setBlockState(pos.set(x, y, z), state, false);
                }
            }
        }
    }

    public int placePart(String palette, BuildingPart part, Transform transform, int x, int y, int z) {
        CityFeature.placePart(level, absolute(new BlockPos(x, y, z)), part, AssetRegistries.PALETTES.get(palette), transform);
        return y + part.getSliceCount();
    }
}
