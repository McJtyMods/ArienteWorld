package mcjty.arienteworld.cities;

import mcjty.arienteworld.ArienteWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

/** Repair saved spawners from before the soldiers moved to ArienteWorld. */
@EventBusSubscriber(modid = ArienteWorld.MODID)
public class LegacySpawnerData {

    @SubscribeEvent
    public static void loadChunk(ChunkDataEvent.Load event) {
        // Chunk NBT is private to this asynchronous load. Avoid accessing the level.
        for (Tag entry : event.getData().getList("block_entities", Tag.TAG_COMPOUND)) {
            CompoundTag blockEntity = (CompoundTag) entry;
            if ("minecraft:mob_spawner".equals(blockEntity.getString("id"))) {
                boolean changed = migrateSpawnData(blockEntity.getCompound("SpawnData"));
                for (Tag potential : blockEntity.getList("SpawnPotentials", Tag.TAG_COMPOUND)) {
                    changed |= migrateSpawnData(((CompoundTag) potential).getCompound("data"));
                }
                if (changed) {
                    event.getChunk().setUnsaved(true);
                }
            }
        }
    }

    private static boolean migrateSpawnData(CompoundTag spawnData) {
        CompoundTag entity = spawnData.getCompound("entity");
        String replacement = switch (entity.getString("id")) {
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
