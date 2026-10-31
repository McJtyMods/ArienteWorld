package mcjty.arienteworld;

import mcjty.ariente.blocks.BlockProperties;
import mcjty.ariente.Ariente;
import mcjty.hologui.gui.HoloGuiEntity;
import mcjty.ariente.blocks.utility.ElevatorTile;
import mcjty.ariente.blocks.utility.autofield.AbstractNodeTile;
import mcjty.ariente.blocks.utility.autofield.AutoFieldTile;
import mcjty.ariente.blocks.utility.autofield.InputItemNodeTile;
import mcjty.ariente.blocks.utility.autofield.ItemFilterMatcher;
import mcjty.ariente.blocks.utility.autofield.NodeOrientation;
import mcjty.ariente.blocks.utility.autofield.OutputItemNodeTile;
import mcjty.ariente.blocks.utility.autofield.RoundRobinNodeTile;
import mcjty.ariente.blocks.utility.wireless.RedstoneChannels;
import mcjty.ariente.blocks.utility.wireless.SignalReceiverTile;
import mcjty.ariente.blocks.utility.wireless.SignalTransmitterTile;
import mcjty.ariente.blocks.utility.wireless.WirelessButtonTile;
import mcjty.ariente.blocks.utility.wireless.WirelessLockTile;
import mcjty.ariente.cables.GenericCableTileEntity;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.ariente.multipart.MultipartBlock;
import mcjty.ariente.multipart.MultipartTE;
import mcjty.ariente.power.IPowerBlob;
import mcjty.ariente.power.PowerSystem;
import mcjty.ariente.power.PowerType;
import mcjty.ariente.setup.Registration;
import mcjty.lib.multipart.PartSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class SystemRegressionTests {

    @GameTest(template = "empty")
    public static void tagDamageAndNbtFilters(GameTestHelper helper) {
        helper.assertTrue(ItemFilterMatcher.matches(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS), true, false, false),
                "Tag mode must match equivalent planks");
        helper.assertTrue(!ItemFilterMatcher.matches(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS), false, false, false),
                "Exact mode must distinguish plank species");
        helper.assertTrue(!ItemFilterMatcher.matches(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), true, false, false),
                "The broad forge:ingots tag must not equate unrelated metals");
        ItemStack filter = new ItemStack(Items.IRON_PICKAXE);
        ItemStack other = filter.copy();
        other.setDamageValue(7);
        helper.assertTrue(ItemFilterMatcher.matches(filter, other, false, false, true), "NBT-only matching must ignore the separate damage flag");
        helper.assertTrue(!ItemFilterMatcher.matches(filter, other, false, true, false), "Damage mode must compare durability");
        other.getOrCreateTag().putString("custom", "test");
        helper.assertTrue(!ItemFilterMatcher.matches(filter, other, false, false, true), "NBT mode must distinguish custom data");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realInventoryRoutingAndRoundRobin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        level.setBlockAndUpdate(origin, Registration.AUTOMATION_FIELD.get().defaultBlockState());
        AutoFieldTile field = (AutoFieldTile) level.getBlockEntity(origin);
        for (int x = 1; x <= 3; x++) {
            BlockPos marker = origin.east(x);
            add(level, marker, Registration.FIELD_MARKER.get().defaultBlockState(), PartSlot.DOWN);
            BlockState state = (x == 1 ? Registration.OUTPUT_ITEM_NODE : Registration.INPUT_ITEM_NODE).get().defaultBlockState()
                    .setValue(AbstractNodeTile.ORIENTATION, NodeOrientation.UP_NW);
            BlockEntity node = add(level, marker.above(), state, PartSlot.UP_NW);
            if (node instanceof OutputItemNodeTile output) {
                CompoundTag tag = output.saveWithoutMetadata();
                tag.getCompound("Info").putInt("outSS", 4);
                output.load(tag);
                output.getOutputFilter().set(0, new ItemStack(Items.IRON_INGOT));
                add(level, marker.above(), Registration.ROUND_ROBIN_NODE.get().defaultBlockState()
                        .setValue(AbstractNodeTile.ORIENTATION, NodeOrientation.UP_NW), PartSlot.UP_NW_BACK);
            } else if (node instanceof InputItemNodeTile input) {
                input.getInputFilter().set(0, new ItemStack(Items.IRON_INGOT));
                input.getInputFilter().set(1, new ItemStack(Items.IRON_INGOT));
            }
            level.setBlockAndUpdate(marker.above(2), Blocks.BARREL.defaultBlockState());
        }
        BarrelBlockEntity source = (BarrelBlockEntity) level.getBlockEntity(origin.east().above(2));
        BarrelBlockEntity first = (BarrelBlockEntity) level.getBlockEntity(origin.east(2).above(2));
        BarrelBlockEntity second = (BarrelBlockEntity) level.getBlockEntity(origin.east(3).above(2));
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        field.tickServer();
        helper.assertTrue(source.getItem(0).getCount() == 8 && first.isEmpty() && second.isEmpty(), "An unpowered field must not extract items");
        power(level, origin, 730001);
        field.tickServer();
        helper.assertTrue(source.isEmpty() && first.getItem(0).getCount() == 4 && second.getItem(0).getCount() == 4,
                "Round robin must route complete batches to distinct real inventories, without duplicate filter entries biasing it");
        MultipartTE saved = (MultipartTE) level.getBlockEntity(origin.east().above());
        MultipartTE restored = new MultipartTE(saved.getBlockPos(), saved.getBlockState());
        restored.setLevel(level);
        restored.load(saved.saveWithoutMetadata());
        RoundRobinNodeTile robin = (RoundRobinNodeTile) restored.getParts().get(PartSlot.UP_NW_BACK).getBlockEntity();
        helper.assertTrue(robin.fetchIndex() == 2, "Round-robin progress must survive a multipart save/load");
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        field.tickServer();
        helper.assertTrue(source.getItem(0).getCount() == 3, "A short batch must remain in the source");
        first.clearContent();
        for (int slot = 0; slot < second.getContainerSize(); slot++) {
            second.setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
        }
        BlockPos target = first.getBlockPos();
        var accepted = new AtomicInteger();
        var partial = new AtomicInteger();
        var handler = new ItemStackHandler(1) {
            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (simulate) {
                    return ItemStack.EMPTY;
                }
                int inserted = Math.min(partial.get(), stack.getCount());
                accepted.addAndGet(inserted);
                return stack.copyWithCount(stack.getCount() - inserted);
            }
        };
        level.setBlockEntity(new BarrelBlockEntity(target, Blocks.BARREL.defaultBlockState()) {
            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
                return capability == ForgeCapabilities.ITEM_HANDLER ? LazyOptional.of(() -> handler).cast() : super.getCapability(capability, side);
            }
        });
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        field.tickServer();
        helper.assertTrue(source.getItem(0).getCount() == 8 && accepted.get() == 0,
                "A destination that rejects an earlier simulated insert must return all extracted items to the source");
        partial.set(2);
        field.tickServer();
        helper.assertTrue(source.getItem(0).getCount() == 2 && accepted.get() == 6,
                "Partially rejected inserts must conserve items and leave a short final batch in the source");
        InputItemNodeTile input = (InputItemNodeTile) ((MultipartTE) level.getBlockEntity(origin.east(2).above())).getParts().get(PartSlot.UP_NW).getBlockEntity();
        CompoundTag inputTag = input.saveWithoutMetadata();
        inputTag.getCompound("Info").putBoolean("inNBT", true);
        input.load(inputTag);
        ItemStack named = new ItemStack(Items.IRON_INGOT, 4);
        named.getOrCreateTag().putString("custom", "different");
        source.setItem(0, named);
        field.notifyNode(input.getBlockPos());
        field.tickServer();
        helper.assertTrue(source.getItem(0).getCount() == 4 && accepted.get() == 6, "Destination NBT matching must reject a mismatched real source stack");
        OutputItemNodeTile output = (OutputItemNodeTile) ((MultipartTE) level.getBlockEntity(origin.east().above())).getParts().get(PartSlot.UP_NW).getBlockEntity();
        CompoundTag outputTag = output.saveWithoutMetadata();
        outputTag.getCompound("Info").putBoolean("outNBT", true);
        output.load(outputTag);
        inputTag.getCompound("Info").putBoolean("inNBT", false);
        input.load(inputTag);
        field.notifyNode(output.getBlockPos());
        field.tickServer();
        helper.assertTrue(source.getItem(0).getCount() == 4 && accepted.get() == 6, "Source NBT matching must also reject a mismatched real stack");
        outputTag.getCompound("Info").putBoolean("outNBT", false);
        outputTag.getCompound("Info").putBoolean("outOre", true);
        output.load(outputTag);
        output.getOutputFilter().set(0, new ItemStack(Items.OAK_PLANKS));
        inputTag.getCompound("Info").putBoolean("inOre", true);
        input.load(inputTag);
        input.getInputFilter().set(0, new ItemStack(Items.OAK_PLANKS));
        source.setItem(0, new ItemStack(Items.BIRCH_PLANKS, 8));
        BarrelBlockEntity taggedDestination = new BarrelBlockEntity(target, Blocks.BARREL.defaultBlockState());
        level.setBlockEntity(taggedDestination);
        field.notifyNode(output.getBlockPos());
        field.tickServer();
        helper.assertTrue(source.isEmpty() && taggedDestination.getItem(0).is(Items.BIRCH_PLANKS) && taggedDestination.getItem(0).getCount() == 8,
                "Tag mode on both nodes must route equivalent items between real inventories");


        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void multipartPlacementSelectionAndRemoval(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO).above(2);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.OUTPUT_ITEM_NODE_ITEM.get(), 2));
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(pos.getX() + .25, pos.getY(), pos.getZ() + .25), Direction.UP, pos.below(), false));
        player.getMainHandItem().useOn(context);
        helper.assertTrue(level.getBlockEntity(pos) instanceof MultipartTE, "Using a node item must create a container");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.ROUND_ROBIN_NODE_ITEM.get(), 2));
        UseOnContext modifier = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(pos.getX() + .25, pos.getY() + .2, pos.getZ() + .25), Direction.UP, pos, false));
        player.getMainHandItem().useOn(modifier);
        MultipartTE tile = (MultipartTE) level.getBlockEntity(pos);
        helper.assertTrue(tile.getParts().size() == 2 && tile.getParts().containsKey(PartSlot.DOWN_NW_BACK), "A modifier must fit behind the node at the original hit position");
        var selected = MultipartBlock.getHitPart(tile.getBlockState(), level, pos,
                new Vec3(pos.getX() + .25, pos.getY() + 2, pos.getZ() + .25), new Vec3(pos.getX() + .25, pos.getY() - 1, pos.getZ() + .25));
        helper.assertTrue(selected != null && selected.getState().is(Registration.OUTPUT_ITEM_NODE.get()), "Ray selection must choose the closest visible part");
        ((OutputItemNodeTile) selected.getBlockEntity()).markDirtyQuick();
        HoloGuiEntity panel = (HoloGuiEntity) Ariente.guiHandler.openHoloGuiEntity(level, pos, player, PartSlot.DOWN_NW.name(), 1);
        try {
            helper.assertTrue(panel.getGui(player) != null, "The selected multipart node must open its own hologram");
            panel.switchTag(PartSlot.DOWN_NW.name() + ":help");
            helper.assertTrue(panel.getGui(player) != null, "Node help must preserve multipart slot targeting");
        } finally {
            panel.discard();
        }

        ItemStack drop = MultipartBlock.getPartStack(selected);
        helper.assertTrue(drop.getOrCreateTag().contains("BlockEntityTag"), "Part drops must preserve configuration");
        var fake = FakePlayerFactory.getMinecraft(level);
        fake.setGameMode(GameType.SURVIVAL);
        fake.moveTo(pos.getX() + .25, pos.getY() + 2 - fake.getEyeHeight(), pos.getZ() + .25, 0, 90);
        fake.gameMode.destroyBlock(pos);
        helper.assertTrue(level.getBlockEntity(pos) == tile && tile.getParts().size() == 1, "Breaking a selected node must preserve the modifier");
        fake.setGameMode(GameType.CREATIVE);
        fake.gameMode.destroyBlock(pos);
        helper.assertTrue(level.isEmptyBlock(pos), "Removing the last part must remove the empty container");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void facadeRemovalKeepsCableNetwork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        var player = FakePlayerFactory.getMinecraft(level);
        for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            level.setBlockAndUpdate(pos, Registration.NETCABLE.get().defaultBlockState());
            GenericCableTileEntity cable = (GenericCableTileEntity) level.getBlockEntity(pos);
            cable.setCableId(730100);
            cable.setMimicBlock(Blocks.OAK_PLANKS.defaultBlockState());
            player.setGameMode(mode);
            player.gameMode.destroyBlock(pos);
            helper.assertTrue(level.getBlockEntity(pos) == cable && cable.getMimicBlock() == null && cable.getCableId() == 730100,
                    "Removing a facade must keep its cable entity and network in " + mode);
            player.gameMode.destroyBlock(pos);
            helper.assertTrue(level.isEmptyBlock(pos), "A second break must remove the exposed cable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wirelessSignalsVisualsAndReassignment(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(100);
        level.setBlockAndUpdate(origin, Registration.WIRELESS_BUTTON.get().defaultBlockState());
        level.setBlockAndUpdate(origin.east(2), Registration.SIGNAL_RECEIVER.get().defaultBlockState());
        level.setBlockAndUpdate(origin.east(4), Registration.WIRELESS_LOCK.get().defaultBlockState());
        var button = (WirelessButtonTile) level.getBlockEntity(origin);
        var receiver = (SignalReceiverTile) level.getBlockEntity(origin.east(2));
        var lock = (WirelessLockTile) level.getBlockEntity(origin.east(4));
        button.setLocked(true);
        int channel = button.getChannel(false);
        helper.assertTrue(channel >= 0, "First button use must allocate a channel");
        receiver.setChannel(channel);
        lock.setChannel(channel);
        receiver.tickServer();
        lock.tickServer();
        helper.assertTrue(receiver.getPowerOutput() == 15 && receiver.getBlockState().getValue(BlockProperties.POWER) && !lock.isLocked(),
                "Button signals must reach receiver output, native visuals and lock state");
        button.setLocked(false);
        receiver.tickServer();
        lock.tickServer();
        helper.assertTrue(receiver.getPowerOutput() == 0 && !receiver.getBlockState().getValue(BlockProperties.POWER)
                && lock.getBlockState().getValue(BlockProperties.LOCKED), "Clearing a channel must update receiver and lock visuals");
        button.setChannel(channel + 100);
        helper.assertTrue(RedstoneChannels.getChannels(level).getChannel(channel + 100).getValue() == 0, "Channel reassignment must publish the current button value");
        level.setBlockAndUpdate(origin.south(2), Registration.SIGNAL_TRANSMITTER.get().defaultBlockState());
        var transmitter = (SignalTransmitterTile) level.getBlockEntity(origin.south(2));
        transmitter.setPowerInput(15);
        transmitter.setChannel(channel + 200);
        transmitter.setChannel(channel + 201);
        helper.assertTrue(RedstoneChannels.getChannels(level).getChannel(channel + 201).getValue() == 15
                && transmitter.getBlockState().getValue(BlockProperties.POWER), "Transmitter reassignment must republish unchanged physical input");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void elevatorRequiresPower(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        level.setBlockAndUpdate(pos, Registration.ELEVATOR.get().defaultBlockState());
        var elevator = (ElevatorTile) level.getBlockEntity(pos);
        elevator.tickServer();
        helper.assertTrue(!elevator.isPowered() && elevator.getUsingPower() == 0, "An unpowered elevator must stay inactive");
        power(level, pos, 730200);
        elevator.tickServer();
        helper.assertTrue(elevator.isPowered() && elevator.getUsingPower() > 0, "Power must activate the beam and movement");
        var client = new ElevatorTile(pos, elevator.getBlockState());
        client.load(elevator.getUpdateTag());
        helper.assertTrue(client.isPowered(), "The beam's active state must reach client chunk data");
        level.removeBlock(pos.north(), false);
        level.removeBlock(pos.south(), false);
        elevator.tickServer();
        helper.assertTrue(!elevator.isPowered(), "Disconnecting power must stop the elevator");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fluxShipPilotMovementAndSave(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 start = new Vec3(helper.absolutePos(BlockPos.ZERO).getX() + .5, 201, helper.absolutePos(BlockPos.ZERO).getZ() + .5);
        for (BlockPos air : BlockPos.betweenClosed(BlockPos.containing(start).offset(-3, -1, -3), BlockPos.containing(start).offset(3, 9, 14))) {
            level.setBlock(air, Blocks.AIR.defaultBlockState(), 2);
        }
        FluxShipEntity ship = FluxShipEntity.create(level, start.x, start.y, start.z);
        level.addFreshEntity(ship);
        Player pilot = helper.makeMockPlayer();
        Player intruder = helper.makeMockPlayer();
        try {
            pilot.startRiding(ship);
            helper.assertTrue(ship.getControllingPassenger() == pilot && !intruder.startRiding(ship), "Only one pilot may board");
            ship.handleInput(intruder, 1, 0, 1);
            ship.tick();
            helper.assertTrue(ship.position().distanceToSqr(start) < .001, "Other players cannot control the ship");
            for (int i = 0; i < 12; i++) {
                ship.handleInput(pilot, 1, 0, 1);
                ship.tick();
            }
            helper.assertTrue(ship.getZ() > start.z + 3 && ship.getY() > start.y + 2, "Held controls must drive real horizontal and vertical movement: " + start + " -> " + ship.position() + "; motion=" + ship.getDeltaMovement() + "; pilot=" + (ship.getControllingPassenger() == pilot));
            ship.handleInput(pilot, 100, 0, 0);
            pilot.stopRiding();
            for (int i = 0; i < 30; i++) {
                ship.tick();
            }
            helper.assertTrue(ship.getDeltaMovement().lengthSqr() < .0001, "An unattended ship must brake and hover");
            CompoundTag saved = new CompoundTag();
            ship.saveWithoutId(saved);
            FluxShipEntity restored = FluxShipEntity.create(level, 0, 0, 0);
            restored.load(saved);
            helper.assertTrue(restored.position().distanceToSqr(ship.position()) < .001 && restored.getDeltaMovement().lengthSqr() == 0,
                    "A saved ship must retain its position and reload parked");
        } finally {
            pilot.stopRiding();
            intruder.stopRiding();
            ship.discard();
        }
        helper.succeed();
    }

    private static BlockEntity add(ServerLevel level, BlockPos pos, BlockState state, PartSlot slot) {
        if (!(level.getBlockEntity(pos) instanceof MultipartTE)) {
            level.setBlockAndUpdate(pos, Registration.MULTIPART_BLOCK.get().defaultBlockState());
        }
        MultipartTE tile = (MultipartTE) level.getBlockEntity(pos);
        BlockEntity child = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
        tile.addPart(slot, state, child);
        return child;
    }

    private static void power(ServerLevel level, BlockPos pos, int network) {
        level.setBlockAndUpdate(pos.north(), Registration.NEGARITE_GENERATOR.get().defaultBlockState());
        level.setBlockAndUpdate(pos.south(), Registration.POSIRITE_GENERATOR.get().defaultBlockState());
        ((IPowerBlob) level.getBlockEntity(pos.north())).fillCableId(network);
        ((IPowerBlob) level.getBlockEntity(pos.south())).fillCableId(network + 1);
        PowerSystem system = PowerSystem.getPowerSystem(level);
        system.addPower(network, 100000, PowerType.NEGARITE);
        system.addPower(network + 1, 100000, PowerType.POSIRITE);
    }
}
