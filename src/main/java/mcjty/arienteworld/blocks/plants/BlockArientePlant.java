package mcjty.arienteworld.blocks.plants;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockArientePlant extends BushBlock {

    private static final VoxelShape SHAPE = Shapes.box(0.1, 0.0, 0.1, 0.9, 0.8, 0.9);

    public BlockArientePlant() {
        super(Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().replaceable().sound(SoundType.GRASS));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return MapCodec.unit(BlockArientePlant::new);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public boolean isShearable(Player player, ItemStack item, Level world, BlockPos pos) {
        return true;
    }

    public List<ItemStack> onSheared(Player player, ItemStack item, Level world, BlockPos pos) {
        return List.of(new ItemStack(this));
    }
}
