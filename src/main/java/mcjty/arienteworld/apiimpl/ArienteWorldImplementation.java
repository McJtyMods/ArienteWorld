package mcjty.arienteworld.apiimpl;

import javax.annotation.Nullable;
import mcjty.ariente.api.IArienteWorld;
import mcjty.ariente.api.ICityAI;
import mcjty.ariente.api.ICityAISystem;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.PortalGenerator;
import mcjty.arienteworld.oregen.OverworldDungeonGen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class ArienteWorldImplementation implements IArienteWorld {

    private static final ICityAISystem EMPTY_CITIES = new ICityAISystem() {

        @Nullable
        @Override
        public ICityAI getCityAI(ChunkPos center) {
            return null;
        }

        @Override
        public void saveSystem() {
            // Other dimensions and the client have no city AI.
        }
    };

    @Override
    public ResourceKey<Level> getDimension() {
        return DimensionRegister.DIMENSION;
    }

    @Override
    public BlockPos getNearestTeleportationSpot(BlockPos pos) {
        ServerLevel level = ServerLifecycleHooks.getCurrentServer().getLevel(getDimension());
        if (level == null) {
            throw new IllegalStateException("Ariente dimension is not loaded");
        }
        return PortalGenerator.getArrival(level, pos);
    }

    @Nullable
    @Override
    public ChunkPos getNearestCityCenter(ChunkPos center) {
        var server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.getLevel(getDimension());
        ChunkPos candidate = CityTools.getNearestDungeonCenter(center.x, center.z);
        return level != null && CityTools.isCityCenter(level.getSeed(), candidate.x, candidate.z) ? candidate : null;
    }

    @Nullable
    @Override
    public BlockPos getNearestDungeon(Level world, BlockPos pos) {
        return world instanceof ServerLevel level ? OverworldDungeonGen.getNearestDungeon(level, pos) : null;
    }

    @Override
    public ICityAISystem getCityAISystem(Level world) {
        return world instanceof ServerLevel level && level.dimension().equals(getDimension())
                ? CityAISystem.getCityAISystem(level) : EMPTY_CITIES;
    }
}
