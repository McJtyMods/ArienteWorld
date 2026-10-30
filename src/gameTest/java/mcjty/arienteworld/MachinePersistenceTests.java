package mcjty.arienteworld;

import mcjty.ariente.blocks.utility.autofield.AbstractNodeTile;
import mcjty.ariente.blocks.utility.autofield.InputItemNodeTile;
import mcjty.ariente.blocks.utility.autofield.OutputItemNodeTile;
import mcjty.ariente.setup.Registration;
import mcjty.lib.tileentity.GenericTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.UUID;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class MachinePersistenceTests {

    @GameTest(template = "empty")
    public static void machineInventoriesSurviveReload(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        for (Block block : new Block[]{Registration.AUTO_CONSTRUCTOR.get(), Registration.BLUEPRINT_STORAGE.get(), Registration.STORAGE.get()}) {
            GenericTileEntity source = (GenericTileEntity) ((EntityBlock) block).newBlockEntity(pos, block.defaultBlockState());
            source.setLevel(helper.getLevel());
            var inventory = (IItemHandlerModifiable) source.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
            ItemStack first = new ItemStack(block == Registration.BLUEPRINT_STORAGE.get() ? Registration.BLUEPRINT.get() : Items.DIAMOND, 9);
            first.getOrCreateTag().putString("reloadCheck", "first");
            ItemStack last = new ItemStack(block == Registration.BLUEPRINT_STORAGE.get() ? Registration.BLUEPRINT.get() : Items.GOLD_INGOT, 3);
            last.getOrCreateTag().putString("reloadCheck", "last");
            inventory.setStackInSlot(0, first.copy());
            inventory.setStackInSlot(inventory.getSlots() - 1, last.copy());
            GenericTileEntity restored = (GenericTileEntity) source.getType().create(pos, source.getBlockState());
            restored.setLevel(helper.getLevel());
            restored.load(source.saveWithoutMetadata());
            var saved = restored.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
            helper.assertTrue(ItemStack.matches(first, saved.getStackInSlot(0))
                            && ItemStack.matches(last, saved.getStackInSlot(saved.getSlots() - 1)),
                    "Machine inventory, counts and item NBT must survive reload: " + block);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void machineSettingsAndParentDataSurviveReload(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        int checked = 0;
        for (var entry : Registration.BLOCKS.getEntries()) {
            Block block = entry.get();
            if (!(block instanceof EntityBlock entityBlock) || !(entityBlock.newBlockEntity(pos, block.defaultBlockState()) instanceof GenericTileEntity source)) {
                continue;
            }
            source.setLevel(helper.getLevel());
            CompoundTag expected = settings(entry.getId().getPath());
            source.load(expected.copy());
            if (source instanceof AbstractNodeTile node) {
                node.getFilters()[0] = DyeColor.BLUE;
                node.getFilters()[3] = DyeColor.RED;
            }
            if (source instanceof InputItemNodeTile input) {
                input.getInputFilter().set(0, new ItemStack(Items.DIAMOND, 5));
            }
            if (source instanceof OutputItemNodeTile output) {
                output.getOutputFilter().set(11, new ItemStack(Items.GOLD_INGOT, 7));
            }
            GenericTileEntity restored = (GenericTileEntity) source.getType().create(pos, source.getBlockState());
            restored.setLevel(helper.getLevel());
            restored.load(source.saveWithoutMetadata());
            helper.assertTrue(NbtUtils.compareNbt(expected, restored.saveWithoutMetadata(), true),
                    "Machine settings, owner and security data must survive reload: " + entry.getId());
            if (restored instanceof AbstractNodeTile node) {
                helper.assertTrue(node.getFilters()[0] == DyeColor.BLUE && node.getFilters()[3] == DyeColor.RED,
                        "Node routing colors must survive reload: " + entry.getId());
            }
            if (restored instanceof InputItemNodeTile input) {
                helper.assertTrue(ItemStack.matches(input.getInputFilter().get(0), new ItemStack(Items.DIAMOND, 5)), "Input filter items must survive reload");
            }
            if (restored instanceof OutputItemNodeTile output) {
                helper.assertTrue(ItemStack.matches(output.getOutputFilter().get(11), new ItemStack(Items.GOLD_INGOT, 7)), "Output filter items must survive reload");
            }
            checked++;
        }
        helper.assertTrue(checked >= 20, "The persistence audit must exercise all registered machine families");
        ArienteWorld.setup.getLogger().info("Machine persistence audit passed for {} registered block entity types", checked);
        helper.succeed();
    }

    private static CompoundTag settings(String block) {
        CompoundTag tag = new CompoundTag();
        CompoundTag info = new CompoundTag();
        info.putString("owner", "Persistence check");
        info.putUUID("ownerId", UUID.fromString("c6b714d0-3921-4b90-a3d5-7cf1a45c9e50"));
        info.putInt("secChannel", 71);
        info.putByte("powered", (byte) 2);
        tag.put("Info", info);
        switch (block) {
            case "lock" -> {
                info.putBoolean("locked", true);
                info.putString("keyId", "persisted-key");
                info.putInt("horizontal", 11);
                info.putInt("vertical", 6);
            }
            case "power_combiner" -> info.putInt("transfer", 1234);
            case "elevator" -> info.putInt("height", 17);
            case "warper" -> info.putInt("charges", 7);
            case "aicore" -> info.putString("cityName", "Persistence city");
            case "forcefield" -> info.putInt("scale", 14);
            case "alarm" -> tag.putInt("alarm", 1);
            case "round_robin_node" -> tag.putInt("index", 7);
            case "auto_constructor" -> {
                tag.putInt("craftIndex", 3);
                tag.putInt("busy", 8);
            }
            case "automation_field" -> {
                tag.putLong("accPower", 4321);
                info.putInt("height", 4);
                info.putBoolean("renderItems", false);
                info.putBoolean("renderOutline", false);
            }
            case "wireless_lock" -> {
                tag.putBoolean("locked", true);
                info.putInt("horizontal", 13);
                info.putInt("vertical", 4);
            }
            case "wireless_button" -> {
                info.putBoolean("locked", true);
                tag.putInt("prevIn", 7);
            }
            case "signal_receiver" -> tag.putInt("rs", 14);
            case "signal_transmitter" -> tag.putInt("prevIn", 3);
        }
        return tag;
    }
}
