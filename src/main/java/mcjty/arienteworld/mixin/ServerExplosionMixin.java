package mcjty.arienteworld.mixin;

import java.util.List;
import mcjty.arienteworld.entities.boss.CoreProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin {

    @Shadow @Final private ServerLevel level;

    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"))
    private void arienteworld$protectCore(CallbackInfoReturnable<List<BlockPos>> cir) {
        cir.getReturnValue().removeIf(pos -> CoreProtection.isProtected(level, pos));
    }
}
