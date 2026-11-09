package mcjty.arienteworld;

import mcjty.ariente.compat.ArienteTOPCompatibility;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.ariente.entities.levitator.FluxLevitatorEntity;
import mcjty.ariente.gui.ModGuis;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.Transform;
import mcjty.arienteworld.dimension.DungeonInteriors;
import mcjty.arienteworld.entities.boss.CoreGuardianEntity;
import mcjty.hologui.gui.HoloGuiEntity;
import mcjty.theoneprobe.apiimpl.TheOneProbeImp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = ArienteWorld.MODID)
public class CubeAndVehicleTests {

    @SubscribeEvent
    public static void functions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> {
            registry.register(Identifier.parse("arienteworld:cube_bottom_passages"), CubeAndVehicleTests::allFourBottomHallEndsHaveThreeByTwoPassages);
            registry.register(Identifier.parse("arienteworld:cube_guardian_pursuit"), CubeAndVehicleTests::guardianPursuesPlayersAtAllFourHallEnds);
            registry.register(Identifier.parse("arienteworld:vehicle_probe_blacklist"), CubeAndVehicleTests::probeHidesVehicleHudsAndOnlyTheRidersHull);
        });
    }

    @SubscribeEvent
    public static void tests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(Identifier.parse("arienteworld:cube_and_vehicle"));
        event.registerTest(Identifier.parse("arienteworld:cube_bottom_passages"), data ->
                new FunctionGameTestInstance(ResourceKey.create(
                        Registries.TEST_FUNCTION, Identifier.parse("arienteworld:cube_bottom_passages")), data),
                new TestData<>(environment, Identifier.parse("arienteworld:empty"), 200, 0, true));
        event.registerTest(Identifier.parse("arienteworld:cube_guardian_pursuit"), data ->
                new FunctionGameTestInstance(ResourceKey.create(
                        Registries.TEST_FUNCTION, Identifier.parse("arienteworld:cube_guardian_pursuit")), data),
                new TestData<>(environment, Identifier.parse("arienteworld:empty"), 200, 0, true));
        event.registerTest(Identifier.parse("arienteworld:vehicle_probe_blacklist"), data ->
                new FunctionGameTestInstance(ResourceKey.create(
                        Registries.TEST_FUNCTION, Identifier.parse("arienteworld:vehicle_probe_blacklist")), data),
                new TestData<>(environment, Identifier.parse("arienteworld:empty"), 200, 0, true));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }

    public static void allFourBottomHallEndsHaveThreeByTwoPassages(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        String[] ends = {"c5_E1", "c5_F1", "c5_G1", "c5_H1"};
        String[] rings = {"c5_b1", "c5_d1", "c5_f1", "c5_h1"};
        Transform[] transforms = {Transform.ROTATE_NONE, Transform.ROTATE_270, Transform.ROTATE_90, Transform.ROTATE_180};
        int[][] endChunks = {{8, 5}, {5, 8}, {11, 8}, {8, 11}};
        int[][] ringChunks = {{8, 4}, {4, 8}, {12, 8}, {8, 12}};
        for (int seed = 0; seed < 16; seed++) {
            for (int side = 0; side < 4; side++) {
                BuildingPart end = DungeonInteriors.vary(AssetRegistries.PARTS.get(ends[side]), palette,
                        seed, endChunks[side][0], endChunks[side][1]);
                BuildingPart ring = DungeonInteriors.vary(AssetRegistries.PARTS.get(rings[side]), palette,
                        seed, ringChunks[side][0], ringChunks[side][1]);
                for (int x = 6; x <= 8; x++) {
                    for (int y = 0; y <= 3; y++) {
                        BlockPos inside = transforms[side].transform(new BlockPos(x, y, 0));
                        BlockPos outside = transforms[side].transform(new BlockPos(x, y, 15));
                        boolean air = palette.getPalette().get(end.getC(inside.getX(), y, inside.getZ())).isAir();
                        check(helper, air == (y == 1 || y == 2), "Doorway needs exactly two blocks of headroom with a floor and lintel: " + ends[side]);
                        if (y <= 2) {
                            check(helper, palette.getPalette().get(ring.getC(outside.getX(), y, outside.getZ())).isAir() == (y > 0),
                                    "The doorway must connect to a walkable ring corridor: " + rings[side]);
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    public static void guardianPursuesPlayersAtAllFourHallEnds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos home = new BlockPos(1500500, 240, 1500500);
        level.getChunkAt(home);
        CoreGuardianEntity guardian = mcjty.arienteworld.setup.Registration.ENTITY_CORE_GUARDIAN.get().create(level, EntitySpawnReason.TRIGGERED);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        guardian.bindToCore(new ChunkPos(home.getX() >> 4, home.getZ() >> 4), home.below(8));
        Vec3 origin = guardian.position();
        try {
            for (Vec3 offset : new Vec3[]{new Vec3(72, 0, 0), new Vec3(-72, 0, 0), new Vec3(0, 0, 72), new Vec3(0, 0, -72)}) {
                guardian.setPos(origin);
                guardian.setDeltaMovement(Vec3.ZERO);
                player.setPos(origin.add(offset));
                level.getChunkAt(player.blockPosition());
                guardian.setTarget(player);
                guardian.getSensing().tick();
                guardian.tick();
                check(helper, guardian.getTarget() == player, "The guardian must retain its target at a distant hall end");
                Vec3 wanted = new Vec3(guardian.getMoveControl().getWantedX(), guardian.getMoveControl().getWantedY(), guardian.getMoveControl().getWantedZ());
                check(helper, wanted.distanceToSqr(origin) > 16, "The guardian must move towards the distant player");
            }
            player.setPos(origin.add(96, 0, 0));
            guardian.tick();
            check(helper, guardian.getTarget() == null, "The encounter must still have a bounded home leash");
        } finally {
            guardian.discard();
            player.discard();
        }
        helper.succeed();
    }

    public static void probeHidesVehicleHudsAndOnlyTheRidersHull(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        TheOneProbeImp probe = new TheOneProbeImp();
        new ArienteTOPCompatibility.GetTheOneProbe().apply(probe);
        HoloGuiEntity panel = new HoloGuiEntity(mcjty.hologui.setup.Registration.HOLOGUI_ENTITY_TYPE.get(), level);
        FluxShipEntity ship = new FluxShipEntity(Registration.ENTITY_FLUX_SHIP.get(), level);
        FluxLevitatorEntity cart = new FluxLevitatorEntity(Registration.ENTITY_FLUX_LEVITATOR.get(), level);
        try {
            panel.setGuiId(ModGuis.GUI_FLUX_SHIP);
            check(helper, probe.isEntityBlacklisted(player, panel), "Ship HUD must be hidden");
            panel.setGuiId(ModGuis.GUI_LEVITATOR);
            check(helper, probe.isEntityBlacklisted(player, panel), "Cart HUD must be hidden");
            panel.setGuiId(ModGuis.GUI_ARMOR);
            check(helper, !probe.isEntityBlacklisted(player, panel), "Other holograms must remain available");
            check(helper, !probe.isEntityBlacklisted(player, ship) && !probe.isEntityBlacklisted(player, cart), "Unridden vehicles must remain visible to the probe");
            check(helper, player.startRiding(ship, true, true), "Fixture must mount the ship");
            check(helper, probe.isEntityBlacklisted(player, ship) && !probe.isEntityBlacklisted(player, cart), "Only the ridden hull must be hidden");
            player.stopRiding();
            check(helper, player.startRiding(cart, true, true), "Fixture must mount the cart");
            check(helper, probe.isEntityBlacklisted(player, cart) && !probe.isEntityBlacklisted(player, ship), "Cart riding must have the same filtering behavior");
        } finally {
            player.stopRiding();
            panel.discard();
            ship.discard();
            cart.discard();
            player.discard();
        }
        helper.succeed();
    }
}
