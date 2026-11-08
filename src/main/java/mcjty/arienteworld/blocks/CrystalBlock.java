package mcjty.arienteworld.blocks;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.material.MapColor;

public class CrystalBlock extends TransparentBlock {

    private static final SoundType CRYSTAL_SOUND = new SoundType(.8f, 1.1f,
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundEvents.AMETHYST_BLOCK_STEP,
            SoundEvents.AMETHYST_BLOCK_PLACE, SoundEvents.AMETHYST_BLOCK_HIT, SoundEvents.AMETHYST_BLOCK_FALL);

    public CrystalBlock(MapColor color) {
        super(Properties.of().mapColor(color).strength(1.5f, 3).requiresCorrectToolForDrops()
                .noOcclusion().sound(CRYSTAL_SOUND));
    }
}
