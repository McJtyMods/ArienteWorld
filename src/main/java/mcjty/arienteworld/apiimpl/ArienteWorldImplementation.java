package mcjty.arienteworld.apiimpl;

import mcjty.ariente.api.IArienteWorld;
import mcjty.ariente.api.ICityAISystem;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.config.LootConfiguration;
import mcjty.arienteworld.config.WorldgenConfiguration;
import mcjty.arienteworld.oregen.OverworldDungeonGen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public class ArienteWorldImplementation implements IArienteWorld {

    @Override
    public int getDimension() {
        return WorldgenConfiguration.DIMENSION_ID.get();
    }

    @Override
    public BlockPos getNearestTeleportationSpot(BlockPos pos) {
        return CityTools.getNearestTeleportationSpot(pos);
    }

    @Override
    public ChunkPos getNearestCityCenter(ChunkPos cityCenter) {
        return CityTools.getNearestDungeonCenter(cityCenter.x, cityCenter.z);
    }

    @Override
    public BlockPos getNearestDungeon(World world, BlockPos pos) {
        return OverworldDungeonGen.getNearestDungeon(world, pos);
    }

    @Override
    public double getSoldierCityKeyChance() {
        return LootConfiguration.SOLDIER_CITYKEY_CHANCE.get();
    }

    @Override
    public ICityAISystem getCityAISystem(World world) {
        return CityAISystem.getCityAISystem(world);
    }
}
