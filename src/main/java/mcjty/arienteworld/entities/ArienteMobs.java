package mcjty.arienteworld.entities;

import javax.annotation.Nullable;
import mcjty.arienteworld.entities.soldier.MasterSoldierEntity;
import mcjty.arienteworld.entities.soldier.SoldierBehaviourType;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class ArienteMobs {

    public static SoldierEntity createSoldier(Level world, BlockPos p, Direction facing, @Nullable ChunkPos center, SoldierBehaviourType behaviourType,
                                           boolean master) {
        SoldierEntity entity;
        if (master) {
            entity = MasterSoldierEntity.create(world, center, behaviourType);
        } else {
            entity = SoldierEntity.create(world, center, behaviourType);
        }
        entity.setPos(p.getX()+.5, p.getY(), p.getZ()+.5);
        float yaw = 0;
        switch (facing) {
            case NORTH:
                yaw = 0;
                break;
            case SOUTH:
                yaw = 90;
                break;
            case WEST:
                yaw = 180;
                break;
            case EAST:
                yaw = 270;
                break;
            default:
                break;
        }
        entity.moveTo(entity.getX(), entity.getY(), entity.getZ(), yaw, 0);
        entity.equipDefaultWeapon(world.getRandom());
        world.addFreshEntity(entity);
        return entity;
    }
}
