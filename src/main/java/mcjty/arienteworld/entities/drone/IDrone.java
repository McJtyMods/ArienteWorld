package mcjty.arienteworld.entities.drone;

import mcjty.ariente.api.IArienteMob;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;

public interface IDrone extends IArienteMob {

    @Nullable
    ChunkPos getCityCenter();
}
