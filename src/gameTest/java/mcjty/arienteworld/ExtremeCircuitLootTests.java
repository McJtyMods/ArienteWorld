package mcjty.arienteworld;

import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.entities.boss.CoreGuardianEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static mcjty.arienteworld.setup.Registration.ENTITY_CORE_GUARDIAN;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class ExtremeCircuitLootTests {

    @GameTest(template = "empty")
    public static void guardianDeathAlwaysDropsOneCircuitRegardlessOfLooting(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        for (int looting : new int[]{0, 3, 10}) {
            ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
            if (looting > 0) {
                sword.enchant(Enchantments.MOB_LOOTING, looting);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            CoreGuardianEntity guardian = ENTITY_CORE_GUARDIAN.get().create(helper.getLevel());
            guardian.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 3, 1))));
            try {
                guardian.hurt(helper.getLevel().damageSources().playerAttack(player), Float.MAX_VALUE);
                List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, guardian.getBoundingBox().inflate(1));
                helper.assertTrue(guardian.isDeadOrDying() && drops.size() == 1
                        && drops.iterator().next().getItem().is(Registration.EXTREME_CIRCUIT.get())
                        && drops.iterator().next().getItem().getCount() == 1,
                        "A real guardian death must drop exactly one Extreme Circuit with Looting " + looting);
                drops.forEach(ItemEntity::discard);
            } finally {
                guardian.discard();
            }
        }
        helper.succeed();
    }
}
