package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.blocks.utility.door.DoorMarkerTile;
import mcjty.ariente.blocks.utility.door.InvisibleDoorTile;
import mcjty.ariente.blocks.generators.NegariteGeneratorTile;
import mcjty.ariente.blocks.generators.PosiriteGeneratorTile;
import mcjty.ariente.api.EnumFacingUpDown;
import mcjty.ariente.cables.GenericCableBlock;
import mcjty.ariente.cables.CableColor;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.Transform;
import mcjty.arienteworld.dimension.CityFeature;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.PaneConnections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class RenderingRegressionTests {

    @GameTest(template = "empty")
    public static void generatedSingleDoorOrientation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        BlockPos origin = new BlockPos(90448, 180, 90384);
        level.getChunkAt(origin);
        var part = AssetRegistries.PARTS.get("c4_1_e");
        var palette = AssetRegistries.PALETTES.get("city4_1");
        for (Transform transform : Transform.values()) {
            CityFeature.placePart(level, origin, part, palette, transform);
            for (BlockPos local : new BlockPos[]{new BlockPos(7, 2, 0), new BlockPos(0, 2, 7), new BlockPos(14, 2, 7), new BlockPos(7, 2, 14)}) {
                BlockPos pos = origin.offset(transform.transform(local));
                Direction authoredNormal = local.getX() == 7 ? Direction.NORTH : Direction.WEST;
                var expected = transform.transform(Registration.DOOR_MARKER.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, authoredNormal)).getValue(BlockStateProperties.HORIZONTAL_FACING);
                var facing = level.getBlockState(pos).getValue(BlockStateProperties.HORIZONTAL_FACING);
                helper.assertTrue(facing.getAxis() == expected.getAxis(), "Single ship doors must face across their wall opening: " + transform + "/" + local);
                DoorMarkerTile marker = (DoorMarkerTile) level.getBlockEntity(pos);
                marker.tickServer();
                helper.assertTrue(level.getBlockState(pos.above()).getValue(BlockStateProperties.HORIZONTAL_FACING) == facing,
                        "Single ship door panels must inherit the repaired marker facing");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void slopesAndBeamShapes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        for (EnumFacingUpDown facing : EnumFacingUpDown.values()) {
            var state = Registration.SLOPE.get().defaultBlockState().setValue(EnumFacingUpDown.FACING, facing);
            level.setBlock(pos, state, 2);
            helper.assertTrue(!state.canOcclude(), "A slope must not occlude its neighbors: " + facing);
            for (Direction side : Direction.values()) {
                BlockPos solid = pos.relative(side);
                helper.assertTrue(Block.shouldRenderFace(Blocks.STONE.defaultBlockState(), level, solid, side.getOpposite(), pos),
                        "A solid block beside a slope must retain its visible face: " + facing + "/" + side);
            }
        }
        helper.assertTrue(!Registration.RAMP.get().defaultBlockState().canOcclude(), "Ramps must retain adjacent block faces too");
        for (var block : new Block[]{Registration.FLUX_BEAM.get(), Registration.FLUX_BEND_BEAM.get()}) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var state = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
                var outline = state.getShape(level, pos);
                helper.assertTrue(outline.bounds().minY == 0 && outline.bounds().maxY == 3.0 / 16,
                        "Beam selection must match its three-pixel height: " + facing);
                helper.assertTrue(state.getCollisionShape(level, pos).isEmpty(), "Flux beams intentionally allow passage, as in 1.12");
                helper.assertTrue(!Shapes.joinIsNotEmpty(outline, Shapes.block(), BooleanOp.ONLY_FIRST)
                                && Shapes.joinIsNotEmpty(Shapes.block(), outline, BooleanOp.ONLY_FIRST),
                        "Beam outline must occupy only the rendered part of its block");
                if (block == Registration.FLUX_BEAM.get()) {
                    helper.assertTrue((facing.getAxis() == Direction.Axis.Z ? outline.bounds().getXsize() : outline.bounds().getZsize()) == 1,
                            "Straight beam outline must follow its rendered axis");
                } else {
                    helper.assertTrue(outline.toAabbs().stream().mapToDouble(box -> box.getXsize() * box.getYsize() * box.getZsize()).sum() == 288.0 / 4096,
                            "Curved beam outline must follow its L-shaped mesh");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void generatedDoorOrientation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        BlockPos origin = new BlockPos(90400, 180, 90384);
        level.getChunkAt(origin);
        var part = AssetRegistries.PARTS.get("c3_1_c");
        var palette = AssetRegistries.PALETTES.get("city3_1");
        for (Transform transform : Transform.values()) {
            CityFeature.placePart(level, origin, part, palette, transform);
            int checked = 0;
            for (int x = 0; x < part.getXSize(); x++) {
                for (int z = 0; z < part.getZSize(); z++) {
                    for (int y = 0; y < part.getSliceCount(); y++) {
                        if (!palette.getPalette().get(part.getC(x, y, z)).is(Registration.DOOR_MARKER.get())) {
                            continue;
                        }
                        BlockPos pos = origin.offset(transform.transform(new BlockPos(x, y, z)));
                        boolean widthX = level.getBlockState(pos.west()).is(Registration.DOOR_MARKER.get())
                                || level.getBlockState(pos.east()).is(Registration.DOOR_MARKER.get());
                        boolean widthZ = level.getBlockState(pos.north()).is(Registration.DOOR_MARKER.get())
                                || level.getBlockState(pos.south()).is(Registration.DOOR_MARKER.get());
                        if (widthX == widthZ) {
                            continue;
                        }
                        var facing = level.getBlockState(pos).getValue(BlockStateProperties.HORIZONTAL_FACING);
                        helper.assertTrue(facing.getAxis() == (widthX ? Direction.Axis.Z : Direction.Axis.X),
                                "Generated underground doors must face across their opening before ticking: " + transform + "/" + pos);
                        DoorMarkerTile door = (DoorMarkerTile) level.getBlockEntity(pos);
                        door.tickServer();
                        helper.assertTrue(level.getBlockState(pos.above()).getValue(BlockStateProperties.HORIZONTAL_FACING) == facing,
                                "Door panels must inherit the generated marker direction");
                        checked++;
                    }
                }
            }
            helper.assertTrue(checked > 0, "Underground fixture must contain wide doors");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void generatedPaneConnections(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        BlockPos left = new BlockPos(89999, 240, 90008);
        BlockPos right = left.east();
        level.getChunkAt(left);
        level.getChunkAt(right);
        level.setBlock(left, Blocks.GLASS_PANE.defaultBlockState(), 2);
        level.setBlock(right, Registration.BLUE_GLASS_FENCE.get().defaultBlockState(), 2);
        PaneConnections.repair(level, new ChunkPos(left));
        PaneConnections.repair(level, new ChunkPos(right));
        helper.assertTrue(level.getBlockState(left).getValue(IronBarsBlock.EAST)
                        && level.getBlockState(right).getValue(IronBarsBlock.WEST),
                "Authored pane states must connect across a loaded chunk border");
        level.setBlock(right, Blocks.AIR.defaultBlockState(), 2);
        PaneConnections.repair(level, new ChunkPos(left));
        helper.assertTrue(!level.getBlockState(left).getValue(IronBarsBlock.EAST), "Removed panes must disconnect after repair");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void automaticDoorsAndClientState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        BlockPos pos = new BlockPos(90008, 240, 90008);
        level.getChunkAt(pos);
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "door-test"));
        player.moveTo(pos.getX() + .5, pos.getY() + 1, pos.getZ() + 2);
        helper.assertTrue(level.addFreshEntity(player), "The door fixture must register its own nearby player");
        // Fresh chunks need a tick before the entity section is visible to proximity queries.
        helper.runAfterDelay(2, () -> {
            try {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    level.removeBlock(pos, false);
                    level.setBlock(pos, Registration.DOOR_MARKER.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 3);
                    level.setBlock(pos.above(), Registration.INVISIBLE_DOOR.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getClockWise()), 2);
                    level.setBlock(pos.above(4), Blocks.STONE.defaultBlockState(), 2);
                    DoorMarkerTile door = (DoorMarkerTile) level.getBlockEntity(pos);
                    door.tickServer();
                    helper.assertTrue(door.isOpen(), "A nearby player must open an unlocked door: facing=" + facing
                            + ", locked=" + door.isLocked() + ", player=" + player.position() + ", removed=" + player.isRemoved());
                    for (int y = 1; y <= 3; y++) {
                        var state = level.getBlockState(pos.above(y));
                        helper.assertTrue(state.is(Registration.INVISIBLE_DOOR.get())
                                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing, "All door segments must inherit marker orientation");
                        helper.assertTrue(InvisibleDoorTile.getCollisionShape(state, level, pos.above(y)).isEmpty(), "Open doors must allow passage");
                    }
                    DoorMarkerTile display = new DoorMarkerTile(pos, door.getBlockState());
                    display.handleUpdateTag(door.getUpdateTag());
                    helper.assertTrue(display.isOpen(), "Initial chunk data must synchronize the automatic opening animation");
                    door.setLocked(true);
                    display.onDataPacket(null, door.getUpdatePacket());
                    helper.assertTrue(!display.isOpen() && display.isLocked(), "Closing/locking must update the client");
                    helper.assertTrue(!InvisibleDoorTile.getCollisionShape(level.getBlockState(pos.above()), level, pos.above()).isEmpty(), "Locked doors must stop passage");
                    door.setLocked(false);
                    player.moveTo(pos.getX() + 20, pos.getY() + 1, pos.getZ() + 20);
                    door.tickServer();
                    helper.assertTrue(!door.isOpen(), "Doors must close when the detection area is empty");
                    player.moveTo(pos.getX() + .5, pos.getY() + 1, pos.getZ() + 2);
                }
            } finally {
                player.discard();
            }
            createVisualFixture(level);
            helper.succeed();
        });
    }

    private static void createVisualFixture(ServerLevel level) {
        for (int x = 90016; x < 90028; x++) {
            for (int z = 90016; z < 90028; z++) {
                level.setBlock(new BlockPos(x, 240, z), LegacyBlockStates.read("ariente:marbletech@type=variation1"), 2);
            }
        }
        for (int x = 90016; x < 90024; x++) {
            for (int y = 241; y < 245; y++) {
                level.setBlock(new BlockPos(x, y, 90027), LegacyBlockStates.read("ariente:blacktech@type=lines_glow"), 2);
            }
        }
        level.setBlock(new BlockPos(90025, 241, 90022), Registration.RAMP.get().defaultBlockState(), 2);
        level.setBlock(new BlockPos(90020, 241, 90024), Registration.AICORE.get().defaultBlockState(), 3);
        level.setBlock(new BlockPos(90019, 241, 90024), Registration.ALARM.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP), 3);
        level.setBlock(new BlockPos(90019, 243, 90026), Registration.FLAT_LIGHT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH), 3);
        BlockPos generator = new BlockPos(90023, 241, 90020);
        for (int x = 90020; x <= 90024; x++) {
            for (int y = 241; y <= 245; y++) {
                level.setBlock(new BlockPos(x, y, 90020), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.setBlock(generator, Registration.NEGARITE_GENERATOR.get().defaultBlockState(), 2);
        for (int y = 1; y <= 3; y++) {
            level.setBlock(generator.above(y), Block.updateFromNeighbourShapes(Registration.NEGARITE_TANK.get().defaultBlockState(), level, generator.above(y)), 3);
        }
        NegariteGeneratorTile negarite = (NegariteGeneratorTile) level.getBlockEntity(generator);
        negarite.feedDust(64);
        negarite.tickServer();
        BlockPos positive = generator.west();
        level.setBlock(positive, Registration.POSIRITE_GENERATOR.get().defaultBlockState(), 3);
        for (int y = 1; y <= 3; y++) {
            level.setBlock(positive.above(y), Block.updateFromNeighbourShapes(Registration.POSIRITE_TANK.get().defaultBlockState(), level, positive.above(y)), 3);
        }
        PosiriteGeneratorTile posirite = (PosiriteGeneratorTile) level.getBlockEntity(positive);
        posirite.feedDust(64);
        posirite.tickServer();
        level.setBlock(generator.offset(3, 0, 3), Registration.NEGARITE_TANK.get().defaultBlockState(), 3);
        level.setBlock(generator.east(), Registration.CONNECTOR.get().defaultBlockState().setValue(GenericCableBlock.COLOR, CableColor.NEGARITE), 2);
        level.setBlock(generator.east().above(), Registration.NETCABLE.get().defaultBlockState().setValue(GenericCableBlock.COLOR, CableColor.NEGARITE), 2);
        level.setBlock(generator.east().above(2), Registration.NETCABLE.get().defaultBlockState().setValue(GenericCableBlock.COLOR, CableColor.NEGARITE), 2);
        int index = 0;
        for (EnumFacingUpDown facing : EnumFacingUpDown.values()) {
            BlockPos slope = new BlockPos(90017 + index++, 243, 90018);
            level.setBlock(slope, Registration.SLOPE.get().defaultBlockState().setValue(EnumFacingUpDown.FACING, facing), 2);
            level.setBlock(slope.above(), LegacyBlockStates.read("ariente:marble@color=black"), 2);
        }
    }
}
