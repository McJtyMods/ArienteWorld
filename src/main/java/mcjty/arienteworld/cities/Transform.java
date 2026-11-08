package mcjty.arienteworld.cities;

import java.util.Random;
import mcjty.ariente.api.EnumFacingUpDown;
import mcjty.lib.blocks.BaseBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public enum Transform {
    ROTATE_NONE(Rotation.NONE, Mirror.NONE),
    ROTATE_90(Rotation.CLOCKWISE_90, Mirror.NONE),
    ROTATE_180(Rotation.CLOCKWISE_180, Mirror.NONE),
    ROTATE_270(Rotation.COUNTERCLOCKWISE_90, Mirror.NONE),
    MIRROR_X(Rotation.NONE, Mirror.FRONT_BACK),
    MIRROR_Z(Rotation.NONE, Mirror.LEFT_RIGHT),
    MIRROR_90_X(Rotation.CLOCKWISE_90, Mirror.FRONT_BACK);

    private final Rotation rotation;
    private final Mirror mirror;

    Transform(Rotation rotation, Mirror mirror) {
        this.rotation = rotation;
        this.mirror = mirror;
    }

    public static Transform random(Random random) {
        return values()[random.nextInt(values().length)];
    }

    public BlockPos transform(BlockPos pos) {
        return new BlockPos(rotateX(pos.getX(), pos.getZ()), pos.getY(), rotateZ(pos.getX(), pos.getZ()));
    }

    public int rotateX(int x, int z) {
        return switch (this) {
            case ROTATE_NONE, MIRROR_Z -> x;
            case ROTATE_90 -> 15 - z;
            case ROTATE_180, MIRROR_X -> 15 - x;
            case ROTATE_270, MIRROR_90_X -> z;
        };
    }

    public int rotateZ(int x, int z) {
        return switch (this) {
            case ROTATE_NONE, MIRROR_X -> z;
            case ROTATE_90, MIRROR_90_X -> x;
            case ROTATE_180, MIRROR_Z -> 15 - z;
            case ROTATE_270 -> 15 - x;
        };
    }

    public BlockState transform(BlockState state) {
        if (this == ROTATE_NONE) {
            return state;
        }
        if (state.hasProperty(EnumFacingUpDown.FACING)) {
            EnumFacingUpDown facing = state.getValue(EnumFacingUpDown.FACING);
            String name = facing.getSerializedName();
            boolean up = name.endsWith("_up");
            Direction direction = Direction.byName(name.replace("_up", ""));
            direction = rotation.rotate(direction);
            if (mirror == Mirror.FRONT_BACK && direction.getAxis() == Direction.Axis.X
                    || mirror == Mirror.LEFT_RIGHT && direction.getAxis() == Direction.Axis.Z) {
                direction = direction.getOpposite();
            }
            return state.setValue(EnumFacingUpDown.FACING, EnumFacingUpDown.byName(direction.getName() + (up ? "_up" : "")));
        }
        state = state.rotate(rotation);
        if (state.getBlock() instanceof BaseBlock) {
            // BaseBlock rotates its facing but inherits Block's no-op mirror implementation.
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return state.setValue(BlockStateProperties.HORIZONTAL_FACING, mirror.mirror(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
            }
            if (state.hasProperty(BlockStateProperties.FACING)) {
                return state.setValue(BlockStateProperties.FACING, mirror.mirror(state.getValue(BlockStateProperties.FACING)));
            }
        }
        return state.mirror(mirror);
    }
}
