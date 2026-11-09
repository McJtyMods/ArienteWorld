package mcjty.arienteworld.ai;

import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = ArienteWorld.MODID)
public class CityAIEvents {

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DimensionRegister.DIMENSION)) {
            ChunkPos pos = event.getChunk().getPos();
            // Chunk events can arrive before promotion to a full chunk. Never scan or load neighbors here.
            level.getServer().schedule(new TickTask(level.getServer().getTickCount(),
                    () -> CityAISystem.getCityAISystem(level).chunkLoaded(pos)));
        }
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DimensionRegister.DIMENSION)) {
            ChunkPos pos = event.getChunk().getPos();
            level.getServer().schedule(new TickTask(level.getServer().getTickCount(),
                    () -> CityAISystem.getCityAISystem(level).chunkUnloaded(pos)));
        }
    }

    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(DimensionRegister.DIMENSION)) {
            CityAISystem system = CityAISystem.getCityAISystem(level);
            system.initializeLoadedChunks();
            system.tickLoadedSections();
        }
    }
}
