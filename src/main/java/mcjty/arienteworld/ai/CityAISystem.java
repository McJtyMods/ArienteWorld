package mcjty.arienteworld.ai;

import mcjty.ariente.varia.SavedDataMigration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mcjty.ariente.api.ICityAISystem;
import mcjty.arienteworld.cities.CityTools;
import mcjty.lib.worlddata.AbstractWorldData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public class CityAISystem extends AbstractWorldData<CityAISystem> implements ICityAISystem {

    private static final Identifier NAME = Identifier.parse("arienteworld:city_ai");
    private final ServerLevel level;
    private final Map<ChunkPos, CityAI> cities = new HashMap<>();
    private final Set<ChunkPos> pendingChunks = new HashSet<>();

    private CityAISystem(ServerLevel level) {
        this.level = level;
    }

    public static CityAISystem getCityAISystem(ServerLevel level) {
        SavedDataMigration.migrate(level, NAME, "ArienteCityAI");
        return level.getDataStorage().computeIfAbsent(new SavedDataType<CityAISystem>(NAME, () -> new CityAISystem(level), CompoundTag.CODEC.xmap(tag -> load(level, tag), system -> system.save(new CompoundTag(), level.registryAccess()))));
    }

    private static CityAISystem load(ServerLevel level, CompoundTag tag) {
        CityAISystem system = new CityAISystem(level);
        ListTag list = tag.getListOrEmpty("cities");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag cityTag = list.getCompoundOrEmpty(i);
            ChunkPos center = new ChunkPos(cityTag.getIntOr("chunkx", 0), cityTag.getIntOr("chunkz", 0));
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

    public void chunkLoaded(ChunkPos pos) {
        if (CityTools.isDungeonChunk(level.getSeed(), pos.x(), pos.z())) {
            pendingChunks.add(pos);
        }
    }

    public void chunkUnloaded(ChunkPos pos) {
        pendingChunks.remove(pos);
        CityAI city = cities.get(CityTools.getNearestDungeonCenter(pos.x(), pos.z()));
        if (city != null) {
            city.chunkUnloaded(pos);
        }
    }

    public void initializeLoadedChunks() {
        for (ChunkPos pos : List.copyOf(pendingChunks)) {
            if (level.getChunkSource().getChunkNow(pos.x(), pos.z()) != null
                    && getCityAI(CityTools.getNearestDungeonCenter(pos.x(), pos.z())).initializeChunk(pos)) {
                pendingChunks.remove(pos);
            }
        }
    }

    public void tickLoadedSections() {
        if (level.getGameTime() % 11 == 0) {
            // Reinforcements may discover neighboring cities while this snapshot is ticking.
            for (CityAI city : List.copyOf(cities.values())) {
                if (city.tickLoadedSections()) {
                    setDirty();
                }
            }
        }
    }

    @Override
    public void saveSystem() {
        setDirty();
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        cities.forEach((center, city) -> {
            CompoundTag cityTag = new CompoundTag();
            cityTag.putInt("chunkx", center.x());
            cityTag.putInt("chunkz", center.z());
            city.writeToNBT(cityTag);
            list.add(cityTag);
        });
        tag.put("cities", list);
        return tag;
    }
}
