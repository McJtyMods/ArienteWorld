package mcjty.arienteworld;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import mcjty.ariente.api.EnumFacingUpDown;
import mcjty.ariente.blocks.decorative.SlopeBlock;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.Transform;
import mcjty.arienteworld.dimension.CityChunk;
import mcjty.arienteworld.dimension.CityFeature;
import mcjty.arienteworld.dimension.CubeLandings;
import mcjty.arienteworld.dimension.DungeonInteriors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class CubeLandingTests {

    private static final String[] ENTRANCES = {"c5_t1", "c5_x1", "c5_z1", "c5_v1"};
    private static final Transform[] ROTATIONS = {Transform.ROTATE_NONE, Transform.ROTATE_90,
            Transform.ROTATE_180, Transform.ROTATE_270};
    private static final Direction[] OUTWARD = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @GameTest(structure = "arienteworld:empty", maxTicks = 200)
    public void allTwentyEightEntrancesRetainShipClearanceAndTrim(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        var red = LegacyBlockStates.read("ariente:blacktech@type=red_lines_glow").getBlock();
        var blue = LegacyBlockStates.read("ariente:blacktech@type=lines_glow").getBlock();
        for (int side = 0; side < 4; side++) {
            BuildingPart original = AssetRegistries.PARTS.get(ENTRANCES[side]);
            String saved = original.writeToJSon().toString();
            Transform rotation = ROTATIONS[side];
            for (int bay = 5; bay <= 11; bay++) {
                for (long seed : new long[]{42, -134, 928371}) {
                    int chunkX = side == 1 ? 12 : side == 3 ? 4 : bay;
                    int chunkZ = side == 0 ? 4 : side == 2 ? 12 : bay;
                    BuildingPart part = DungeonInteriors.vary(original, palette, seed, chunkX, chunkZ);
                    helper.assertTrue(saved.equals(original.writeToJSon().toString()), "Shared entrance template was mutated");
                    for (int t = 5; t <= 10; t++) {
                        for (int depth = 0; depth <= 4; depth++) {
                            for (int y = 1; y <= 4; y++) {
                                helper.assertTrue(state(part, palette, rotation, t, y, depth).isAir(),
                                        "The six-wide, four-high ship opening is obstructed: " + part.getName());
                            }
                        }
                    }
                    for (int t : new int[]{4, 11}) {
                        for (int y = 1; y <= 4; y++) {
                            helper.assertTrue(state(part, palette, rotation, t, y, 1).is(red), "Missing red jamb light");
                        }
                    }
                    for (int t = 4; t <= 11; t++) {
                        helper.assertTrue(state(part, palette, rotation, t, 5, 0).getBlock() instanceof SlopeBlock,
                                "The raised lintel needs its sloped outer trim");
                    }
                    for (int depth = 0; depth <= 14; depth++) {
                        for (int t : new int[]{7, 8}) {
                            helper.assertTrue(state(part, palette, rotation, t, 0, depth).is(blue),
                                    "The paired blue guidance strip must lead into the corridor");
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(structure = "arienteworld:empty", maxTicks = 200)
    public void fluxShipsCanLandAndPassThroughEveryOrientation(GameTestHelper helper) {
        var level = helper.getLevel();
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        BuildingPart landing = AssetRegistries.PARTS.get("c5_landing");
        for (int side = 0; side < 4; side++) {
            Transform rotation = ROTATIONS[side];
            Direction outward = OUTWARD[side];
            BlockPos origin = new BlockPos(250000 + side * 64, 200, 250000);
            BlockPos platformOrigin = origin.relative(outward, 16);
            level.getChunkAt(origin);
            level.getChunkAt(platformOrigin);
            BuildingPart entrance = DungeonInteriors.vary(AssetRegistries.PARTS.get(ENTRANCES[side]), palette, 42, 8, 4);
            CityFeature.placePart(level, platformOrigin, landing, palette, rotation);
            CityFeature.placePart(level, origin, entrance, palette);
            for (int t = 4; t <= 11; t++) {
                BlockPos edge = origin.offset(rotation.transform(new BlockPos(t, 0, -2)));
                BlockState slope = level.getBlockState(edge);
                helper.assertTrue(slope.getBlock() instanceof SlopeBlock
                                && slope.getValue(EnumFacingUpDown.FACING).getSerializedName().equals(outward.getName() + "_up"),
                        "Outer platform bevel must face away from the cube");
            }
            FluxShipEntity ship = new FluxShipEntity(Registration.ENTITY_FLUX_SHIP.get(), level);
            BlockPos tangent = rotation.transform(new BlockPos(1, 0, 0)).subtract(rotation.transform(BlockPos.ZERO));
            BlockPos inward = rotation.transform(new BlockPos(0, 0, 1)).subtract(rotation.transform(BlockPos.ZERO));
            Vec3 start = Vec3.atBottomCenterOf(origin.offset(rotation.transform(new BlockPos(7, 1, 0))))
                    .add(tangent.getX() * .5, 0, tangent.getZ() * .5);
            try {
                for (int step = -16; step <= 32; step++) {
                    ship.setPos(start.add(inward.getX() * step * .25, 0, inward.getZ() * step * .25));
                    helper.assertTrue(level.noCollision(ship), "Flux Ship hull collides with landing/entrance on " + outward + " at step " + step);
                }
            } finally {
                ship.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(structure = "arienteworld:empty", maxTicks = 200)
    public void exteriorPlatformsStayInTheirChunkAndIgnorePlacementOrder(GameTestHelper helper) {
        var level = helper.getLevel();
        City city = null;
        for (int i = 0; i < 512; i++) {
            ChunkPos center = new ChunkPos(5000 + i * 16, 5000);
            City candidate = CityTools.getCity(level.getSeed(), center);
            if (candidate.getPlan().getName().equals("city5_1") && CityTools.isCityCenter(level.getSeed(), center.x(), center.z())) {
                city = candidate;
                break;
            }
        }
        helper.assertTrue(city != null, "Fixture must find a present cube");
        for (Direction outward : OUTWARD) {
            ChunkPos chunk = new ChunkPos(city.getCenter().x() + outward.getStepX() * 5,
                    city.getCenter().z() + outward.getStepZ() * 5);
            level.getChunk(chunk.x(), chunk.z());
            Map<BlockPos, BlockState> writes = new HashMap<>();
            WorldGenLevel guarded = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class}, (proxy, method, args) -> {
                        if (method.getName().equals("setBlock")) {
                            BlockPos pos = (BlockPos) args[0];
                            helper.assertTrue(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4).equals(chunk), "Platform generation wrote into its neighbor");
                            writes.put(pos.immutable(), (BlockState) args[1]);
                        }
                        try {
                            return method.invoke(level, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
            CubeLandings.generate(new CityChunk(guarded, chunk), chunk, city, level.getChunkSource().getGenerator());
            helper.assertTrue(writes.values().stream().anyMatch(state -> state.getBlock() instanceof SlopeBlock),
                    "Exterior chunk must generate its landing before the entrance exists");
            Map<BlockPos, BlockState> before = Map.copyOf(writes);
            int neighborX = chunk.x() - outward.getStepX();
            int neighborZ = chunk.z() - outward.getStepZ();
            int y = CityTools.getLowestHeight(level, city, neighborX, neighborZ);
            for (BuildingPart part : CityTools.getBuildingParts(level.getSeed(), city, neighborX, neighborZ)) {
                CityFeature.placePart(level, new BlockPos(neighborX * 16, y, neighborZ * 16), part,
                        AssetRegistries.PALETTES.get("city5_1"));
                y += part.getSliceCount();
            }
            writes.clear();
            CubeLandings.generate(new CityChunk(guarded, chunk), chunk, city, level.getChunkSource().getGenerator());
            helper.assertTrue(before.equals(writes), "Exterior landing depends on entrance chunk placement order");
        }
        helper.succeed();
    }

    private static BlockState state(BuildingPart part, Palette palette, Transform rotation, int x, int y, int z) {
        BlockPos pos = rotation.transform(new BlockPos(x, y, z));
        return palette.getPalette().get(part.getC(pos.getX(), y, pos.getZ()));
    }
}
