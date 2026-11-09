package mcjty.arienteworld.dimension;

import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;

public final class SpawnSelection {

    private final ServerLevel level;
    private final BlockPos pos;
    private final MobCategory category;
    private final List<Weighted<MobSpawnSettings.SpawnerData>> entries;

    public SpawnSelection(ServerLevel level, BlockPos pos, MobCategory category, List<Weighted<MobSpawnSettings.SpawnerData>> entries) {
        this.level = level;
        this.pos = pos;
        this.category = category;
        this.entries = new ArrayList<>(entries);
    }

    public Level getLevel() { return level; }

    public BlockPos getPos() { return pos; }

    public MobCategory getMobCategory() { return category; }

    public List<Weighted<MobSpawnSettings.SpawnerData>> getSpawnerDataList() { return entries; }

    public void removeSpawnerData(Weighted<MobSpawnSettings.SpawnerData> entry) { entries.remove(entry); }

    public void addSpawnerData(Weighted<MobSpawnSettings.SpawnerData> entry) { entries.add(entry); }
}
