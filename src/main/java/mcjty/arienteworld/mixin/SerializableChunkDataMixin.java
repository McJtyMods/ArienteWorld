package mcjty.arienteworld.mixin;

import mcjty.arienteworld.cities.LegacySpawnerData;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SerializableChunkData.class)
abstract class SerializableChunkDataMixin {

    @Inject(method = "read", at = @At("HEAD"))
    private void arienteworld$migrateSpawners(CallbackInfoReturnable<ProtoChunk> cir) {
        LegacySpawnerData.loadChunk((SerializableChunkData) (Object) this);
    }
}
