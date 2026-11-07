package mcjty.arienteworld;

import mcjty.ariente.blocks.utility.ElevatorTile;
import mcjty.ariente.blocks.utility.LevelMarkerTile;
import mcjty.ariente.blocks.decorative.RampBlock;
import mcjty.ariente.blocks.decorative.SlopeBlock;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.PaletteIndex;
import mcjty.arienteworld.dimension.CityFeature;
import mcjty.arienteworld.dimension.DungeonInteriors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class CubeDeckTests {

    @GameTest(template = "empty")
    public static void decksPreserveShellAndContainOnlySupportedDecoration(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        var plan = AssetRegistries.CITYPLANS.get("city5_1");
        var flatLight = LegacyBlockStates.read("ariente:flatlight@facing=north").getBlock();
        var silhouettes = new HashSet<String>();
        for (boolean basement : new boolean[]{true, false}) {
            List<String> layout = basement ? plan.getCellar() : plan.getTop();
            for (int dx = 0; dx < 9; dx++) {
                for (int dz = 0; dz < 9; dz++) {
                    String name = "c5_" + layout.get(dz).charAt(dx) + "1";
                    BuildingPart original = AssetRegistries.PARTS.get(name);
                    String saved = original.writeToJSon().toString();
                    for (long seed : new long[]{42, -134, 928371}) {
                        BuildingPart part = DungeonInteriors.vary(original, palette, seed, 4 + dx, 4 + dz);
                        helper.assertTrue(saved.equals(original.writeToJSon().toString()), "District generation changed its shared template");
                        helper.assertTrue(part.writeToJSon().equals(DungeonInteriors.vary(original, palette, seed, 4 + dx, 4 + dz).writeToJSon()),
                                "District generation must be deterministic");
                        int ceiling = basement ? 15 : 28;
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                if (basement) {
                                    helper.assertTrue(part.getC(x, 0, z).equals(original.getC(x, 0, z)), "Basement foundation must remain intact");
                                } else {
                                    helper.assertTrue(part.getC(x, 28, z).equals(original.getC(x, 28, z))
                                                    && part.getC(x, 29, z).equals(original.getC(x, 29, z)),
                                            "Roof shell must remain intact");
                                }
                                for (int y = 1; y < ceiling; y++) {
                                    var state = palette.getPalette().get(part.getC(x, y, z));
                                    helper.assertTrue(state != null, "District contains an unmapped material");
                                    helper.assertTrue(!part.getC(x, y, z).equals(new PaletteIndex('a', ' ')), "Old pale pillar remains");
                                    helper.assertTrue(!CityFeature.hasBlockEntity(state)
                                                    || state.is(Registration.ELEVATOR.get()) || state.is(Registration.LEVEL_MARKER.get()),
                                            "Decorative district added equipment or loot: " + state);
                                    helper.assertTrue(!state.is(flatLight),
                                            "Deck lights must be inset into structures, not freestanding flat lights");
                                }
                                // Four-wide walks stay clear even beside a lift or a tall assembly.
                                boolean liftBeam = x == 1 && z == 7 && palette.getPalette().get(part.getC(x, 1, z)).isAir();
                                if (dx > 0 && dx < 8 && dz > 0 && dz < 8 && (x < 4 || x > 11 || z < 4 || z > 11) && !liftBeam) {
                                    helper.assertTrue(palette.getPalette().get(part.getC(x, 1, z))
                                                    .isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), "Street needs continuous paving");
                                    for (int y = 2; y <= 4; y++) {
                                        helper.assertTrue(palette.getPalette().get(part.getC(x, y, z))
                                                        .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty(), "Street headroom is blocked");
                                    }
                                }
                            }
                        }
                        assertSupported(helper, part, palette, ceiling);
                        if (dx == 3 && dz == 3) {
                            silhouettes.add(part.writeToJSon().toString());
                        }
                    }
                }
            }
        }
        helper.assertTrue(silhouettes.size() >= 4, "Both districts need seeded layout variation");
        helper.succeed();
    }

    private static void assertSupported(GameTestHelper helper, BuildingPart part, Palette palette, int ceiling) {
        boolean[] supported = new boolean[(ceiling + 1) * 256];
        var queue = new ArrayDeque<BlockPos>();
        for (int y : new int[]{0, ceiling}) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (!palette.getPalette().get(part.getC(x, y, z)).isAir()) {
                        supported[y * 256 + z * 16 + x] = true;
                        queue.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            for (Direction side : Direction.values()) {
                BlockPos neighbor = pos.relative(side);
                int x = neighbor.getX();
                int y = neighbor.getY();
                int z = neighbor.getZ();
                if (x < 0 || x > 15 || y < 0 || y > ceiling || z < 0 || z > 15) {
                    continue;
                }
                int index = y * 256 + z * 16 + x;
                if (!supported[index] && !palette.getPalette().get(part.getC(x, y, z)).isAir()) {
                    supported[index] = true;
                    queue.add(neighbor);
                }
            }
        }
        for (int y = 1; y < ceiling; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    helper.assertTrue(palette.getPalette().get(part.getC(x, y, z)).isAir() || supported[y * 256 + z * 16 + x],
                            "Floating decoration in " + part.getName() + " at " + new BlockPos(x, y, z));
                }
            }
        }
    }

    @GameTest(template = "empty")
    public static void deckProfilesUseShapedBlocksAndRestrainedAccents(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        var bricks = LegacyBlockStates.read("ariente:marble_bricks@color=black").getBlock();
        var panel = LegacyBlockStates.read("ariente:blacktech@type=panel").getBlock();
        var accentBlocks = new HashSet<Block>();
        for (String type : new String[]{"lines_glow", "red_lines_glow", "darkblue_blue", "darkblue_red"}) {
            accentBlocks.add(LegacyBlockStates.read("ariente:blacktech@type=" + type).getBlock());
        }
        int slopes = 0;
        int ramps = 0;
        int fences = 0;
        for (String name : new String[]{"c5_n1", "c5_51"}) {
            for (int seed = 0; seed < 32; seed++) {
                BuildingPart part = DungeonInteriors.vary(AssetRegistries.PARTS.get(name), palette, seed, 5, 5);
                int panels = 0;
                int accents = 0;
                for (int y = 1; y < part.getSliceCount() - 1; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            var state = palette.getPalette().get(part.getC(x, y, z));
                            helper.assertTrue(!state.is(bricks), "Deck surfaces must use smooth cladding instead of masonry");
                            if (state.is(panel)) {
                                panels++;
                                helper.assertTrue(!palette.getPalette().get(part.getC(x, y - 1, z)).is(panel),
                                        "Control panels must not form stacked columns");
                            }
                            if (accentBlocks.contains(state.getBlock())) {
                                accents++;
                            }
                            if (state.getBlock() instanceof SlopeBlock) {
                                slopes++;
                            } else if (state.getBlock() instanceof RampBlock) {
                                ramps++;
                            } else if (state.getBlock() instanceof IronBarsBlock) {
                                fences++;
                                var below = palette.getPalette().get(part.getC(x, y - 1, z));
                                helper.assertTrue(below.getBlock() instanceof IronBarsBlock
                                                || below.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                                                && !(below.getBlock() instanceof SlopeBlock) && !(below.getBlock() instanceof RampBlock),
                                        "Fence runs need a solid foot, not an air gap above a bevel");
                            }
                        }
                    }
                }
                helper.assertTrue(panels <= 2, "Panels must be occasional controls, not the main cladding");
                helper.assertTrue(accents <= 24, "Red and blue indicators must remain sparse");
            }
        }
        helper.assertTrue(slopes > 0 && ramps > 0 && fences > 0, "Deck silhouettes need slopes, ramps and fence structures");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hallLiftsConnectEightStopsThroughBothDistricts(GameTestHelper helper) throws Exception {
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        var level = helper.getLevel();
        int lifts = 0;
        for (int[] chunk : new int[][]{{6, 10}, {10, 6}, {10, 10}}) {
            BlockPos origin = new BlockPos(131000 + lifts * 32, 140, 131000);
            level.getChunkAt(origin);
            for (int y = 2; y <= 83; y++) {
                level.setBlock(origin.offset(1, y, 7), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            int bottom = 0;
            for (String name : new String[]{"c5_n1", "c5_e1", "c5_w1", "c5_51"}) {
                BuildingPart part = DungeonInteriors.vary(AssetRegistries.PARTS.get(name), palette, 42, chunk[0], chunk[1]);
                CityFeature.placePart(level, origin.above(bottom), part, palette);
                bottom += part.getSliceCount();
            }
            ElevatorTile elevator = (ElevatorTile) level.getBlockEntity(origin.offset(1, 1, 7));
            helper.assertTrue(elevator != null && elevator.getHeight() == 84, "Through lift must reach the roof district from the basement");
            for (int y = 2; y <= 83; y++) {
                helper.assertTrue(level.getBlockState(origin.offset(1, y, 7)).isAir(), "Obstructed through lift at " + y);
            }
            List<Integer> stops = List.of(2, 17, 27, 37, 49, 59, 69, 82);
            for (int feet : stops) {
                helper.assertTrue(level.getBlockEntity(origin.offset(0, feet, 5)) instanceof LevelMarkerTile, "Missing lift stop at " + feet);
                helper.assertTrue(level.getBlockState(origin.offset(2, feet - 1, 7))
                                .isCollisionShapeFullBlock(level, origin.offset(2, feet - 1, 7)), "Missing landing at " + feet);
                for (int x = 2; x <= 3; x++) {
                    helper.assertTrue(level.getBlockState(origin.offset(x, feet, 7)).isAir()
                                    && level.getBlockState(origin.offset(x, feet + 1, 7)).isAir(), "Landing exit is obstructed at " + feet);
                }
            }
            Method findFloors = ElevatorTile.class.getDeclaredMethod("findFloors");
            findFloors.setAccessible(true);
            helper.assertTrue(findFloors.invoke(elevator).equals(stops.stream().map(y -> origin.getY() + y).toList()),
                    "The elevator must discover exactly the two district stops and six room decks");
            ElevatorTile restored = new ElevatorTile(elevator.getBlockPos(), elevator.getBlockState());
            restored.load(elevator.saveWithoutMetadata());
            helper.assertTrue(restored.getHeight() == elevator.getHeight(), "Through lift height must survive reload");
            lifts++;
        }
        helper.assertTrue(lifts == 3, "Three hall lifts must serve both districts");
        helper.succeed();
    }
}
