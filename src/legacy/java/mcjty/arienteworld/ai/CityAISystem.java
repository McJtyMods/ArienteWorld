package mcjty.arienteworld.ai;

import mcjty.ariente.api.ICityAISystem;
import mcjty.lib.worlddata.AbstractWorldData;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class CityAISystem extends AbstractWorldData<CityAISystem> implements ICityAISystem {

    private static final String NAME = "ArienteCityAI";

    // Indexed by city center
    private Map<ChunkPos, CityAI> cityAIMap = new HashMap<>();

    public CityAISystem(String name) {
        super(name);
    }

    @Override
    public CityAI getCityAI(ChunkPos coord) {
        if (!cityAIMap.containsKey(coord)) {
            CityAI cityAI = new CityAI(coord);
            cityAIMap.put(coord, cityAI);
            save();
        }
        return cityAIMap.get(coord);
    }

    @Override
    public void saveSystem() {
        save();
    }

    @Nonnull
    public static CityAISystem getCityAISystem(World world) {
        return getData(world, () -> new CityAISystem(NAME), NAME);
    }

    @Override
    public void read(CompoundNBT compound) {
        ListNBT cityList = compound.getList("cities", Constants.NBT.TAG_COMPOUND);
        cityAIMap.clear();
        for (int i = 0 ; i < cityList.size() ; i++) {
            CompoundNBT nbt = cityList.getCompound(i);
            int chunkX = nbt.getInt("chunkx");
            int chunkZ = nbt.getInt("chunkz");
            ChunkPos coord = new ChunkPos(chunkX, chunkZ);
            CityAI ai = new CityAI(coord);
            ai.readFromNBT(nbt);
            cityAIMap.put(coord, ai);
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT compound) {
        ListNBT cityList = new ListNBT();
        for (Map.Entry<ChunkPos, CityAI> entry : cityAIMap.entrySet()) {
            CompoundNBT nbt = new CompoundNBT();
            nbt.putInt("chunkx", entry.getKey().x);
            nbt.putInt("chunkz", entry.getKey().z);
            entry.getValue().writeToNBT(nbt);
            cityList.add(nbt);
        }
        compound.put("cities", cityList);

        return compound;
    }
}
