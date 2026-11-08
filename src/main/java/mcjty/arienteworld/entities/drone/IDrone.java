package mcjty.arienteworld.entities.drone;

import javax.annotation.Nullable;
import mcjty.ariente.api.IArienteMob;
import net.minecraft.world.level.ChunkPos;

public interface IDrone extends IArienteMob {

    @Nullable
    ChunkPos getCityCenter();
}
