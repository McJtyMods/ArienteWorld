package mcjty.arienteworld.ai;

import mcjty.ariente.api.ICityAISystem;
import mcjty.lib.worlddata.AbstractWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

public class CityAISystem extends AbstractWorldData<CityAISystem> implements ICityAISystem {

    private static final String NAME = "ArienteCityAI";
    private final ServerLevel level;
    private final Map<ChunkPos, CityAI> cities = new HashMap<>();

    private CityAISystem(ServerLevel level) {
        this.level = level;
    }

    public static CityAISystem getCityAISystem(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> load(level, tag), () -> new CityAISystem(level), NAME);
    }

    private static CityAISystem load(ServerLevel level, CompoundTag tag) {
        CityAISystem system = new CityAISystem(level);
        ListTag list = tag.getList("cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag cityTag = list.getCompound(i);
            ChunkPos center = new ChunkPos(cityTag.getInt("chunkx"), cityTag.getInt("chunkz"));
            CityAI city = new CityAI(level, center);
            city.readFromNBT(cityTag);
            system.cities.put(center, city);
        }
        return system;
    }

    @Override
    public CityAI getCityAI(ChunkPos center) {
        return cities.computeIfAbsent(center, key -> {
            setDirty();
            return new CityAI(level, key);
        });
    }

    @Override
    public void saveSystem() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        cities.forEach((center, city) -> {
            CompoundTag cityTag = new CompoundTag();
            cityTag.putInt("chunkx", center.x);
            cityTag.putInt("chunkz", center.z);
            city.writeToNBT(cityTag);
            list.add(cityTag);
        });
        tag.put("cities", list);
        return tag;
    }
}
