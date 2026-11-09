package mcjty.arienteworld.dimension;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Resolve authored pane states after chunks are loaded, including older generated structures. */
@EventBusSubscriber(modid = ArienteWorld.MODID)
public class PaneConnections {

    private static final Map<ServerLevel, Set<ChunkPos>> PENDING = new WeakHashMap<>();

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DimensionRegister.DIMENSION)) {
            synchronized (PENDING) {
                PENDING.computeIfAbsent(level, key -> new HashSet<>()).add(event.getChunk().getPos());
            }
        }
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Set<ChunkPos> chunks;
        synchronized (PENDING) {
            chunks = PENDING.remove(level);
        }
        if (chunks == null) {
            return;
        }
        Set<ChunkPos> repair = new HashSet<>(chunks);
        for (ChunkPos chunk : chunks) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                repair.add(new ChunkPos(chunk.x() + direction.getStepX(), chunk.z() + direction.getStepZ()));
            }
        }
        for (ChunkPos chunk : repair) {
            repair(level, chunk);
        }
    }

    public static void repair(ServerLevel level, ChunkPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        if (chunk == null) {
            return;
        }
        for (int sectionIndex = 0; sectionIndex < chunk.getSections().length; sectionIndex++) {
            var section = chunk.getSections()[sectionIndex];
            if (!section.maybeHas(state -> state.getBlock() instanceof IronBarsBlock)) {
                continue;
            }
            int bottom = level.getSectionYFromSectionIndex(sectionIndex) * 16;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!(state.getBlock() instanceof IronBarsBlock)) {
                            continue;
                        }
                        BlockPos blockPos = new BlockPos(pos.getMinBlockX() + x, bottom + y, pos.getMinBlockZ() + z);
                        BlockState connected = state;
                        for (Direction direction : Direction.Plane.HORIZONTAL) {
                            BlockPos neighbor = blockPos.relative(direction);
                            if (level.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4) != null) {
                                connected = connected.updateShape(level, level, blockPos, direction, neighbor, level.getBlockState(neighbor), level.getRandom());
                            }
                        }
                        if (connected != state) {
                            level.setBlock(blockPos, connected, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }
}
