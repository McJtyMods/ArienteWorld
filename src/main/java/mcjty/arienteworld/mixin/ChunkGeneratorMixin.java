package mcjty.arienteworld.mixin;

import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.lib.varia.LevelTools;
import net.minecraft.world.level.Level;

import mcjty.arienteworld.dimension.ArienteChunkGenerator;
import mcjty.arienteworld.dimension.SoldierSpawning;
import mcjty.arienteworld.dimension.SpawnSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.util.random.WeightedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {

    @Inject(method = "getMobsAt", at = @At("RETURN"), cancellable = true)
    private void arienteworld$dungeonSpawns(Level world, StructureManager structures, MobCategory category, BlockPos pos, CallbackInfoReturnable<WeightedList<MobSpawnSettings.SpawnerData>> cir) {
        if ((Object) this instanceof ArienteChunkGenerator) {
            var level = LevelTools.getLevel(DimensionRegister.DIMENSION);
            if (level != null) {
                var event = new SpawnSelection(level, pos, category, cir.getReturnValue().unwrap());
                SoldierSpawning.potentialSpawns(event);
                cir.setReturnValue(WeightedList.of(event.getSpawnerDataList()));
            }
        }
    }
}
