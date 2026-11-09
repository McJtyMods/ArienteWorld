package mcjty.arienteworld.cities;

import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import mcjty.arienteworld.ArienteWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Repair saved spawners from before the soldiers moved to ArienteWorld. */
public class LegacySpawnerData {

    public static void loadChunk(SerializableChunkData data) {
        // Chunk NBT is private to this asynchronous load. Avoid accessing the level.
        for (CompoundTag blockEntity : data.blockEntities()) {
            if ("minecraft:mob_spawner".equals(blockEntity.getStringOr("id", ""))) {
                boolean changed = migrateSpawnData(blockEntity.getCompoundOrEmpty("SpawnData"));
                for (Tag potential : blockEntity.getListOrEmpty("SpawnPotentials")) {
                    changed |= migrateSpawnData(((CompoundTag) potential).getCompoundOrEmpty("data"));
                }
                if (changed) {
                    // The migrated NBT will be saved with the loaded chunk.
                }
            }
        }
    }

    private static boolean migrateSpawnData(CompoundTag spawnData) {
        CompoundTag entity = spawnData.getCompoundOrEmpty("entity");
        String replacement = switch (entity.getStringOr("id", "")) {
            case "ariente:soldier" -> "arienteworld:soldier";
            case "ariente:master_soldier" -> "arienteworld:master_soldier";
            default -> null;
        };
        if (replacement == null) {
            return false;
        }
        entity.putString("id", replacement);
        return true;
    }
}
