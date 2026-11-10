package mcjty.arienteworld.mixin;

import mcjty.arienteworld.cities.LegacySpawnerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChunkSerializer.class)
abstract class SerializableChunkDataMixin {

    @ModifyVariable(method = "read", at = @At("HEAD"), argsOnly = true)
    private static CompoundTag arienteworld$migrateSpawners(CompoundTag tag) {
        LegacySpawnerData.loadChunk(tag);
        return tag;
    }
}
