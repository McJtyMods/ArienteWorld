package mcjty.arienteworld.ai;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public class CityAIEvents {

    public static void chunkLoaded(Level world, LevelChunk chunk) {
        if (world instanceof ServerLevel level && level.dimension().equals(DimensionRegister.DIMENSION)) {
            ChunkPos pos = chunk.getPos();
            // Chunk events can arrive before promotion to a full chunk. Never scan or load neighbors here.
            level.getServer().schedule(new TickTask(level.getServer().getTickCount(),
                    () -> CityAISystem.getCityAISystem(level).chunkLoaded(pos)));
        }
    }

    public static void chunkUnloaded(Level world, LevelChunk chunk) {
        if (world instanceof ServerLevel level && level.dimension().equals(DimensionRegister.DIMENSION)) {
            ChunkPos pos = chunk.getPos();
            level.getServer().schedule(new TickTask(level.getServer().getTickCount(),
                    () -> CityAISystem.getCityAISystem(level).chunkUnloaded(pos)));
        }
    }

    public static void levelTick(Level world) {
        if (world instanceof ServerLevel level
                && level.dimension().equals(DimensionRegister.DIMENSION)) {
            CityAISystem system = CityAISystem.getCityAISystem(level);
            system.initializeLoadedChunks();
            system.tickLoadedSections();
        }
    }
}
