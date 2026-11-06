package mcjty.arienteworld.cities;

import mcjty.ariente.blocks.storagecube.StorageCubeTile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Vanilla loot tables for authored Holo Storage Cubes, filled once by their city AI. */
public final class StructureLoot {

    private StructureLoot() {
    }

    public static void setLootTable(StorageCubeTile storage, ResourceLocation table) {
        storage.setLootTable(table);
    }

    public static boolean fill(StorageCubeTile storage) {
        ResourceLocation id = storage.getLootTable();
        if (id == null) {
            return false;
        }
        if (!storage.hasGeneratedLoot()) {
            ServerLevel level = (ServerLevel) storage.getLevel();
            LootTable table = level.getServer().getLootData().getLootTable(id);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(storage.getBlockPos()))
                    .create(LootContextParamSets.CHEST);
            long seed = level.getSeed() ^ storage.getBlockPos().asLong() ^ 0x436f6e7175657265L;
            List<ItemStack> loot = table.getRandomItems(params, seed);
            for (int slot = 0; slot < loot.size(); slot++) {
                storage.getItems().setStackInSlot(slot, loot.get(slot));
            }
            storage.markLootGenerated();
        }
        return true;
    }
}
