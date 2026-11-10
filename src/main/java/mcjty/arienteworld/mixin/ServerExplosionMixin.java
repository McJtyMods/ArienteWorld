package mcjty.arienteworld.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mcjty.arienteworld.entities.boss.CoreProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
abstract class ServerExplosionMixin {

    @Shadow @Final private Level level;
    @Shadow @Final private ObjectArrayList<BlockPos> toBlow;

    @Inject(method = "explode", at = @At("RETURN"))
    private void arienteworld$protectCore(CallbackInfo ci) {
        if (level instanceof ServerLevel server)
            toBlow.removeIf(pos -> CoreProtection.isProtected(server, pos));
    }
}
