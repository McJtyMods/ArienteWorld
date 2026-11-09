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
        for (CompoundTag blockEntity : event.getData().blockEntities()) {
            if ("minecraft:mob_spawner".equals(blockEntity.getStringOr("id", ""))) {
                boolean changed = migrateSpawnData(blockEntity.getCompoundOrEmpty("SpawnData"));
                for (Tag potential : blockEntity.getListOrEmpty("SpawnPotentials")) {
                    changed |= migrateSpawnData(((CompoundTag) potential).getCompoundOrEmpty("data"));
                }
                if (changed) {
                    event.getChunk().markUnsaved();
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
