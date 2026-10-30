package mcjty.arienteworld.blocks.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.IForgeShearable;

import java.util.List;

public class BlockArientePlant extends BushBlock implements IForgeShearable {

    private static final VoxelShape SHAPE = Shapes.box(0.1, 0.0, 0.1, 0.9, 0.8, 0.9);

    public BlockArientePlant() {
        super(Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().replaceable().sound(SoundType.GRASS));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean isShearable(ItemStack item, Level world, BlockPos pos) {
        return true;
    }

    @Override
    public List<ItemStack> onSheared(Player player, ItemStack item, Level world, BlockPos pos, int fortune) {
        return List.of(new ItemStack(this));
    }
}
