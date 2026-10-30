package mcjty.arienteworld;

import mcjty.ariente.Ariente;
import mcjty.ariente.api.IGenerator;
import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.gui.ModGuis;
import mcjty.ariente.blocks.generators.NegariteGeneratorTile;
import mcjty.ariente.blocks.generators.NegariteTankTile;
import mcjty.ariente.blocks.generators.PosiriteGeneratorTile;
import mcjty.ariente.blocks.generators.PosiriteTankTile;
import mcjty.ariente.entities.levitator.FluxLevitatorEntity;
import mcjty.ariente.setup.Registration;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import mcjty.ariente.cables.ConnectorType;
import mcjty.ariente.cables.GenericCableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import mcjty.ariente.blocks.defense.ForceFieldTile;
import mcjty.ariente.blocks.utility.StorageTile;
import mcjty.ariente.blocks.utility.ElevatorTile;
import mcjty.hologui.gui.HoloGuiEntity;
import mcjty.lib.varia.RedstoneMode;
import mcjty.lib.tileentity.GenericTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import static mcjty.ariente.setup.Registration.FORCEFIELD;
import static mcjty.ariente.setup.Registration.NETCABLE;
import static mcjty.ariente.setup.Registration.STORAGE;
import static mcjty.ariente.blocks.BlockProperties.LOWER;
import static mcjty.ariente.blocks.BlockProperties.UPPER;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class MachineInteractionTests {

    @GameTest(template = "empty")
    public static void armorModuleHologramControls(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)).add(0, 2, 0));
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        var pieces = new ItemStack[]{new ItemStack(Registration.POWERSUIT_HEAD.get()), new ItemStack(Registration.POWERSUIT_CHEST.get()),
                new ItemStack(Registration.POWERSUIT_LEGS.get()), new ItemStack(Registration.POWERSUIT_FEET.get())};
        String[] guis = {ModGuis.GUI_ARMOR_HELMET, ModGuis.GUI_ARMOR_CHEST, ModGuis.GUI_ARMOR_LEGS, ModGuis.GUI_ARMOR_BOOTS};
        for (int i = 0; i < slots.length; i++) {
            player.setItemSlot(slots[i], pieces[i]);
        }
        player.getInventory().setItem(9, new ItemStack(Registration.MODULE_ENERGY.get(), 4));
        HoloGuiEntity holo = (HoloGuiEntity) Ariente.guiHandler.openHoloGui(player, ModGuis.GUI_ARMOR, 1);
        holo.setCloseStrategy(0);
        var upgrade = ArmorUpgradeType.ENERGY;
        try {
            for (int i = 0; i < slots.length; i++) {
                holo.switchGui(ModGuis.GUI_ARMOR);
                click(player, holo, 3, 3 + i);
                helper.assertTrue(holo.getGuiId().equals(guis[i]), "Configure must open the selected worn armor piece");
                click(player, holo, 1.5, 2.3);
                var tag = pieces[i].getOrCreateTag();
                helper.assertTrue(tag.contains(upgrade.getModuleKey()) && !tag.getBoolean(upgrade.getModuleKey())
                                && player.getInventory().countItem(Registration.MODULE_ENERGY.get()) == 3,
                        "Clicking the module icon must install one module from inventory, initially disabled");
                click(player, holo, 2.5, 2.3);
                helper.assertTrue(tag.getBoolean(upgrade.getModuleKey()), "The adjacent checkbox must enable the installed module");
                click(player, holo, 3.5, 2.3);
                helper.assertTrue(tag.getInt(upgrade.getHotkeyKey()) == 1, "The last button must select a module hotkey");
                tag.putBoolean(upgrade.getWorkingKey(), true);
                click(player, holo, 1.5, 2.3);
                helper.assertTrue(!tag.contains(upgrade.getModuleKey()) && !tag.contains(upgrade.getWorkingKey())
                                && !tag.contains(upgrade.getHotkeyKey()) && player.getInventory().countItem(Registration.MODULE_ENERGY.get()) == 4,
                        "Removing a module must return it and clear its enabled/working/hotkey state");
            }
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            click(player, holo, 1.5, 2.3);
            helper.assertTrue(player.getInventory().countItem(Registration.MODULE_ENERGY.get()) == 4
                            && !player.getItemBySlot(EquipmentSlot.FEET).getOrCreateTag().contains(upgrade.getModuleKey()),
                    "A stale armor GUI must not consume modules or modify unrelated equipment");
        } finally {
            holo.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tankStacksAndGeneratorSync(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        for (boolean negarite : new boolean[]{true, false}) {
            Block generator = (negarite ? Registration.NEGARITE_GENERATOR : Registration.POSIRITE_GENERATOR).get();
            Block tank = (negarite ? Registration.NEGARITE_TANK : Registration.POSIRITE_TANK).get();
            for (int y = 0; y <= 4; y++) {
                level.setBlock(pos.above(y), Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(pos, generator.defaultBlockState(), 3);
            for (int y = 1; y <= 3; y++) {
                var state = Block.updateFromNeighbourShapes(tank.defaultBlockState(), level, pos.above(y));
                level.setBlock(pos.above(y), state, 3);
            }
            for (int y = 1; y <= 3; y++) {
                var state = level.getBlockState(pos.above(y));
                helper.assertTrue(state.getValue(UPPER) == (y < 3) && state.getValue(LOWER) == (y > 1),
                        "Stacked tank frames must retain only the top/bottom rims");
                helper.assertTrue(!state.canOcclude(), "Transparent tanks must not hide adjacent machine faces");
                helper.assertTrue(state.hasPostProcess(level, pos.above(y)), "Generated tanks must resolve their stack shapes after placement");
            }
            GenericTileEntity source = (GenericTileEntity) level.getBlockEntity(pos);
            ((IGenerator) source).feedDust(8);
            if (source instanceof NegariteGeneratorTile negative) {
                negative.tickServer();
            } else {
                ((PosiriteGeneratorTile) source).tickServer();
            }
            helper.assertTrue(source.getUpdateTag().getCompound("Info").getInt("dust") > 0,
                    "The generator must synchronize its active fuel cycle to the client");
            GenericTileEntity display = (GenericTileEntity) source.getType().create(pos, source.getBlockState());
            display.setLevel(level);
            display.handleUpdateTag(source.getUpdateTag());
            helper.assertTrue(negarite ? ((NegariteGeneratorTile) display).isWorking() : ((PosiriteGeneratorTile) display).isWorking(),
                    "A joining client must see active tank beams");
            IItemHandler restoredFuel = display.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
            helper.assertTrue(restoredFuel.getStackInSlot(0).getCount() == 7, "Generator fuel inventory must survive saving/loading with the current cycle");
            var betweenCycles = display.saveWithoutMetadata();
            betweenCycles.getCompound("Info").putInt("dust", 0);
            display.load(betweenCycles);
            if (display instanceof NegariteGeneratorTile negative) {
                negative.tickServer();
            } else {
                ((PosiriteGeneratorTile) display).tickServer();
            }
            helper.assertTrue(display.getUpdateTag().getCompound("Info").getInt("dust") == 600
                            && restoredFuel.getStackInSlot(0).getCount() == 6,
                    "Reloaded generators must start their next fuel cycle and keep tank beams active");
            var upperTank = level.getBlockEntity(pos.above(3));
            helper.assertTrue(negarite ? ((NegariteTankTile) upperTank).isWorking() : ((PosiriteTankTile) upperTank).isWorking(),
                    "Every tank in a stack must find its active generator");
            source.setRSMode(RedstoneMode.REDSTONE_ONREQUIRED);
            display.onDataPacket(null, source.getUpdatePacket());
            helper.assertTrue(negarite ? !((NegariteGeneratorTile) display).isWorking() : !((PosiriteGeneratorTile) display).isWorking(),
                    "Disabling a generator must stop client tank beams");
            level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(!level.getBlockState(pos.above()).getValue(UPPER)
                            && !level.getBlockState(pos.above(3)).getValue(LOWER),
                    "Removing a tank must restore the exposed frame rims");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hologramsPreserveStandingSpace(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos machine = helper.absolutePos(BlockPos.ZERO);
        BlockPos standing = machine.above(5);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 3; y++) {
                    level.setBlock(standing.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        Player player = helper.makeMockPlayer();
        for (var block : new Block[]{Registration.WARPER.get(), Registration.ELEVATOR.get()}) {
            level.setBlock(machine, block.defaultBlockState(), 2);
            for (int yaw : new int[]{45, 135, 225, 315}) {
                // At a diagonal the one-block GUI bounds overlap the player's head.
                // This must remain pickable without becoming a physical obstacle.
                player.moveTo(standing.getX() + .5, standing.getY(), standing.getZ() + .5, yaw, 0);
                var body = player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1.0E-7);
                helper.assertTrue(level.noCollision(player, body), "Player must have standing room before opening the GUI");
                for (int opened = 0; opened < 2; opened++) {
                    HoloGuiEntity holo = (HoloGuiEntity) Ariente.guiHandler.openHoloGuiEntity(level, machine, player, "default", 1);
                    helper.assertTrue(holo.getBoundingBox().intersects(body), "The regression must overlap a freshly opened hologram");
                    helper.assertTrue(level.getEntitiesOfClass(HoloGuiEntity.class, body).contains(holo), "The hologram must participate in spatial entity queries");
                    helper.assertTrue(holo.isPickable(), "A non-solid hologram must still accept button clicks");
                    helper.assertTrue(level.noCollision(player, body), "Opening or reopening a Warper/Elevator GUI must not force the player into a lower pose");
                    holo.discard();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void elevatorHeightClicksAndSync(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, Registration.ELEVATOR.get());
        ElevatorTile elevator = (ElevatorTile) helper.getBlockEntity(BlockPos.ZERO);
        Player player = helper.makeMockPlayer();
        player.moveTo(Vec3.atCenterOf(elevator.getBlockPos()).add(0, 1, -2));
        HoloGuiEntity holo = (HoloGuiEntity) Ariente.guiHandler.openHoloGuiEntity(helper.getLevel(), elevator.getBlockPos(), player, "default", 1);
        ElevatorTile display = new ElevatorTile(elevator.getBlockPos(), elevator.getBlockState());
        display.setLevel(helper.getLevel());
        display.getRenderBoundingBox(); // Cache the initial height before receiving an update.
        for (int yaw : new int[]{0, 90, 180, 270}) {
            holo.setYRot(yaw);
            int before = elevator.getHeight();
            click(player, holo, 5.5, 4.5);
            helper.assertTrue(elevator.getHeight() == before + 1, "Elevator arrow must update server height at yaw " + yaw);
            display.onDataPacket(null, elevator.getUpdatePacket());
            helper.assertTrue(display.getHeight() == elevator.getHeight(), "Changed elevator height must reach the hologram's client tile");
            helper.assertTrue(display.getRenderBoundingBox().maxY == elevator.getBlockPos().getY() + elevator.getHeight() + 3,
                    "A height update must invalidate the cached beam bounds");
        }
        ElevatorTile joining = new ElevatorTile(elevator.getBlockPos(), elevator.getBlockState());
        joining.handleUpdateTag(elevator.getUpdateTag());
        helper.assertTrue(joining.getHeight() == elevator.getHeight(), "Initial chunk data must include configured elevator height");
        elevator.setHeight(1);
        click(player, holo, 1.5, 4.5);
        helper.assertTrue(elevator.getHeight() == 1, "Elevator height must retain its lower limit");
        elevator.setHeight(256);
        click(player, holo, 6.5, 4.5);
        helper.assertTrue(elevator.getHeight() == 256, "Elevator height must retain its upper limit");
        holo.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void levitatorControlsAndCorner(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = new BlockPos(90208, 200, 90100);
        for (int i = 0; i <= 48; i++) {
            BlockPos pos = i <= 16 ? origin.offset(i, 0, 0) : origin.offset(16, 0, i - 16);
            level.getChunkAt(pos);
            for (int y = 1; y <= 4; y++) {
                level.setBlock(pos.above(y), Blocks.AIR.defaultBlockState(), 2);
            }
            level.setBlock(pos, (i == 16 ? Registration.FLUX_BEND_BEAM : Registration.FLUX_BEAM).get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, i <= 16 ? Direction.NORTH : Direction.EAST), 2);
        }
        FluxLevitatorEntity cart = FluxLevitatorEntity.create(level, origin.getX() + 2.5, origin.getY() + .0625, origin.getZ() + .5);
        level.addFreshEntity(cart);
        Player player = helper.makeMockPlayer();
        try {
            cart.tick();
            HoloGuiEntity front = (HoloGuiEntity) cart.getHoloGuiFront();
            HoloGuiEntity back = (HoloGuiEntity) cart.getHoloGuiBack();
            helper.assertTrue(front.getVehicle() == cart && back.getVehicle() == cart, "Both cart control panels must remain attached");
            helper.assertTrue(front.distanceToSqr(back) > 1, "Cart control panels must occupy opposite ends");
            cart.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.getVehicle() == cart, "A player must be able to board alongside the control panels");
            click(player, front, 6.5, 2.5);
            helper.assertTrue(cart.getSpeed() == 10, "Front panel must accelerate the cart");
            for (int i = 0; i < 85; i++) {
                cart.tick();
                cart.positionRider(player);
            }
            helper.assertTrue(Math.abs(cart.getX() - (origin.getX() + 16.5)) < .6 && cart.getZ() > origin.getZ() + 3,
                    "A cart must follow a straight track through a corner: " + cart.position());
            helper.assertTrue(player.distanceToSqr(cart) < 4, "A rider must follow the cart through the corner");
            click(player, back, 4.5, 2.5);
            helper.assertTrue(cart.getSpeed() == 0, "Back panel must stop the cart");
            for (int i = 0; i < 12; i++) {
                cart.tick();
            }
            helper.assertTrue(cart.getDeltaMovement().horizontalDistanceSqr() < .001, "Stopped carts must settle on the beam");
        } finally {
            player.stopRiding();
            cart.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cableNeighborConnections(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, NETCABLE.get());
        helper.setBlock(BlockPos.ZERO.east(), NETCABLE.get());
        helper.assertTrue(helper.getBlockState(BlockPos.ZERO).getValue(GenericCableBlock.EAST) == ConnectorType.CABLE,
                "Placing an adjacent cable must update the existing cable's model state");
        helper.setBlock(BlockPos.ZERO.east(), Blocks.AIR);
        helper.assertTrue(helper.getBlockState(BlockPos.ZERO).getValue(GenericCableBlock.EAST) == ConnectorType.NONE,
                "Removing the neighbor must disconnect the cable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void storageDisplayUpdates(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, STORAGE.get());
        StorageTile storage = (StorageTile) helper.getBlockEntity(BlockPos.ZERO);
        IItemHandler inventory = storage.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        inventory.insertItem(0, new ItemStack(Items.DIAMOND, 64), false);
        inventory.insertItem(1, new ItemStack(Items.DIAMOND, 64), false);
        inventory.insertItem(StorageTile.STACKS_PER_TYPE, new ItemStack(Items.APPLE, 12), false);
        storage.setLocked(true);

        StorageTile display = new StorageTile(storage.getBlockPos(), storage.getBlockState());
        display.setLevel(helper.getLevel());
        display.handleUpdateTag(storage.getUpdateTag());
        helper.assertTrue(display.getTotalStack(0).is(Items.DIAMOND) && display.getTotalStack(0).getCount() == 128,
                "Initial chunk data must include Storage Cube item types and totals above one stack");
        helper.assertTrue(display.getTotalStack(1).is(Items.APPLE) && display.isLocked(),
                "Initial chunk data must include other display slots and lock state");

        inventory.extractItem(0, 16, false);
        inventory.extractItem(StorageTile.STACKS_PER_TYPE, 12, false);
        storage.setLocked(false);
        display.onDataPacket(null, storage.getUpdatePacket());
        helper.assertTrue(display.getTotalStack(0).getCount() == 112,
                "Block update packets must refresh displayed totals");
        helper.assertTrue(display.getTotalStack(1).isEmpty() && !display.isLocked(),
                "Block update packets must clear emptied slots and refresh lock state");

        StorageTile restored = new StorageTile(storage.getBlockPos(), storage.getBlockState());
        restored.load(storage.saveWithoutMetadata());
        helper.assertTrue(restored.getTotalStack(0).getCount() == 112,
                "Display synchronization must preserve the persistent inventory format");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void forceFieldHologramClicks(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, FORCEFIELD.get());
        ForceFieldTile generator = (ForceFieldTile) helper.getBlockEntity(BlockPos.ZERO);
        Player player = helper.makeMockPlayer();
        player.moveTo(Vec3.atCenterOf(generator.getBlockPos()).add(0, 1, -2));
        HoloGuiEntity holo = (HoloGuiEntity) Ariente.guiHandler.openHoloGuiEntity(helper.getLevel(),
                generator.getBlockPos(), player, "default", 1);
        helper.assertTrue(holo != null && holo.isPickable(), "ForceField hologram must be targetable");
        holo.setCloseStrategy(0);

        ForceFieldTile display = new ForceFieldTile(generator.getBlockPos(), generator.getBlockState());
        display.setLevel(helper.getLevel());
        for (int yaw : new int[]{0, 90, 180, 270}) {
            holo.setYRot(yaw);
            int before = generator.getScale();
            click(player, holo, 5.5, 2.5);
            helper.assertTrue(generator.getScale() == before + 1,
                    "Attacking the right arrow must change the server radius at yaw " + yaw);
            display.onDataPacket(null, generator.getUpdatePacket());
            helper.assertTrue(display.getScale() == generator.getScale(),
                    "Arrow changes must reach the client's radius display");
        }
        click(player, holo, 7.5, 6.5);
        display.onDataPacket(null, generator.getUpdatePacket());
        helper.assertTrue(display.getRSMode() == generator.getRSMode() && display.getRSMode() != RedstoneMode.REDSTONE_IGNORED,
                "The redstone-mode button must update both server and client state");
        ForceFieldTile joiningDisplay = new ForceFieldTile(generator.getBlockPos(), generator.getBlockState());
        joiningDisplay.handleUpdateTag(generator.getUpdateTag());
        helper.assertTrue(joiningDisplay.getScale() == generator.getScale(),
                "Joining clients must receive the current radius");
        holo.discard();
        helper.succeed();
    }

    private static void click(Player player, HoloGuiEntity holo, double x, double y) {
        Vec3 normal = holo.getLookAngle();
        Vec3 right = normal.cross(new Vec3(0, 1, 0));
        float factor = 1 - (1 - holo.getScale()) * 1.6f;
        float offset = .8f + (1 - holo.getScale()) * 13.2f;
        Vec3 target = holo.position().add(right.scale((x + offset) * factor / 10 - 0.5)).add(0, 1 - (y + offset) * factor / 10, 0);
        Vec3 eyes = target.subtract(normal);
        player.moveTo(eyes.x, eyes.y - player.getEyeHeight(), eyes.z, holo.getYRot(), 0);
        player.attack(holo);
    }
}
