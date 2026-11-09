package mcjty.arienteworld.tutorial;

import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import mcjty.ariente.api.MarbleColor;
import mcjty.ariente.blocks.decorative.DecorativeBlockModule;
import mcjty.ariente.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Checks the native tool and transfer APIs against the tutorial's real blocks and inventories. */
public final class TutorialPortChecks {

    private TutorialPortChecks() {
    }

    public static void mining(ServerLevel level, Player player, BlockPos pos) {
        player.setOnGround(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
        float cobblestone = Blocks.COBBLESTONE.defaultBlockState().getDestroyProgress(player, level, pos);
        float marble = DecorativeBlockModule.MARBLE.get(MarbleColor.GRAY).get().defaultBlockState().getDestroyProgress(player, level, pos);
        require(marble >= cobblestone, "Marble mines more slowly than cobblestone with a netherite pickaxe");
        float core = Registration.AICORE.get().defaultBlockState().getDestroyProgress(player, level, pos);
        require(Math.abs(1 / core - 200) < 1, "AI Core must take about ten seconds with a netherite pickaxe; got " + 1 / core + " ticks");
    }

    public static void inventoryTransactions(ServerLevel level, BlockPos pos) {
        var inventory = level.getCapability(Capabilities.Item.BLOCK, pos, null);
        require(inventory != null, "Missing native item capability at " + pos);
        var before = new ArrayList<ItemStack>();
        int occupied = -1;
        for (int slot = 0; slot < inventory.size(); slot++) {
            before.add(inventory.getResource(slot).toStack((int) inventory.getAmountAsLong(slot)));
            if (occupied < 0 && inventory.getAmountAsLong(slot) > 1) occupied = slot;
        }
        require(occupied >= 0, "Inventory transaction fixture is empty at " + pos);
        var resource = inventory.getResource(occupied);
        long amount = inventory.getAmountAsLong(occupied);
        try (var outer = Transaction.openRoot()) {
            require(inventory.extract(occupied, resource, 1, outer) == 1, "Native extraction failed");
            try (var nested = Transaction.open(outer)) {
                require(inventory.insert(occupied, resource, 1, nested) == 1, "Native insertion failed");
                nested.commit();
            }
            require(inventory.getAmountAsLong(occupied) == amount, "Nested commit lost an item");
            try (var nested = Transaction.open(outer)) {
                require(inventory.extract(occupied, resource, 1, nested) == 1, "Nested extraction failed");
            }
            require(inventory.getAmountAsLong(occupied) == amount, "Nested rollback lost an item");
            inventory.extract(occupied, resource, 1, outer);
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            var actual = inventory.getResource(slot).toStack((int) inventory.getAmountAsLong(slot));
            require(ItemStack.matches(before.get(slot), actual), "Root rollback changed inventory slot " + slot);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
