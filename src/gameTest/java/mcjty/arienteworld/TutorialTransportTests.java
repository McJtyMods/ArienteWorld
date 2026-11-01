package mcjty.arienteworld;

import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.ariente.entities.levitator.FluxLevitatorEntity;
import mcjty.ariente.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class TutorialTransportTests {

    @GameTest(template = "empty")
    public static void cartCompletesExposedLoopWithRider(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = new BlockPos(150000, 240, 150000);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(22, 3, 16))) {
            level.setBlockAndUpdate(pos, pos.getY() < origin.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        for (int x = 0; x <= 20; x++) {
            for (int z : new int[]{0, 14}) {
                level.setBlockAndUpdate(origin.offset(x, 0, z), Registration.FLUX_BEAM.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            }
        }
        for (int z = 1; z < 14; z++) {
            for (int x : new int[]{0, 20}) {
                level.setBlockAndUpdate(origin.offset(x, 0, z), Registration.FLUX_BEAM.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            }
        }
        int[][] corners = {{0, 0}, {20, 0}, {20, 14}, {0, 14}};
        Direction[] facings = {Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH};
        for (int i = 0; i < corners.length; i++) {
            level.setBlockAndUpdate(origin.offset(corners[i][0], 0, corners[i][1]), Registration.FLUX_BEND_BEAM.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facings[i]));
        }
        for (int speed : new int[]{5, 10, 40, 80, -5, -10, -40, -80}) {
            FluxLevitatorEntity cart = FluxLevitatorEntity.create(level, origin.getX() + 2.5, origin.getY() + .0625, origin.getZ() + .5);
            level.addFreshEntity(cart);
            var rider = helper.makeMockPlayer();
            rider.startRiding(cart);
            cart.changeSpeed(speed);
            Set<BlockPos> passed = new HashSet<>();
            try {
                for (int tick = 0; tick < 1600 && passed.size() < 4; tick++) {
                    cart.tick();
                    cart.positionRider(rider);
                    BlockPos rail = BlockPos.containing(cart.getX(), origin.getY(), cart.getZ());
                    if (level.getBlockState(rail).is(Registration.FLUX_BEND_BEAM.get())) {
                        passed.add(rail);
                    }
                    helper.assertTrue(cart.getSpeed() == speed, "Valid slow movement must not be treated as a blocked cart: speed=" + speed + "; pos=" + cart.position());
                    helper.assertTrue(cart.getX() >= origin.getX() && cart.getX() <= origin.getX() + 21
                                    && cart.getZ() >= origin.getZ() && cart.getZ() <= origin.getZ() + 15,
                            "A cart must stay on its loop at speed " + speed + ": " + cart.position());
                    helper.assertTrue(rider.getVehicle() == cart && rider.distanceToSqr(cart) < 4, "The rider must follow every bend");
                }
                helper.assertTrue(passed.size() == 4, "A ridden cart must traverse all four bend orientations at speed " + speed + ": " + passed + "; pos=" + cart.position());
            } finally {
                rider.stopRiding();
                cart.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shipPilotSitsInsideCockpit(GameTestHelper helper) {
        var level = helper.getLevel();
        FluxShipEntity ship = FluxShipEntity.create(level, 150100.5, 240, 150100.5);
        var pilot = helper.makeMockPlayer();
        pilot.startRiding(ship);
        try {
            for (int yaw : new int[]{0, 90, 180, 270}) {
                ship.setYRot(yaw);
                ship.positionRider(pilot);
                double feet = pilot.getY() - ship.getY();
                helper.assertTrue(feet >= -.05 && feet <= .15,
                        "The seated pilot's feet must be near the cockpit floor, below its rim: " + feet);
                helper.assertTrue(new Vec3(pilot.getX(), ship.getY(), pilot.getZ()).distanceToSqr(ship.position()) < .25,
                        "The cockpit seat must stay inside the hull at every heading");
            }
        } finally {
            pilot.stopRiding();
            ship.discard();
        }
        helper.succeed();
    }
}
