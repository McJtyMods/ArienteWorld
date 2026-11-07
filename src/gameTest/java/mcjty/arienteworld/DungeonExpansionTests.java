package mcjty.arienteworld;

import mcjty.ariente.blocks.utility.ElevatorTile;
import mcjty.ariente.display.DisplayBaseTile;
import mcjty.ariente.display.DisplayGeometry;
import mcjty.ariente.display.DisplayModuleItem;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.dimension.CityFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import mcjty.arienteworld.cities.PaletteIndex;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.dimension.ArienteLandscapeCity;
import mcjty.arienteworld.dimension.DungeonInteriors;
import mcjty.arienteworld.dimension.DungeonDisplays;
import mcjty.arienteworld.cities.Transform;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class DungeonExpansionTests {

    @GameTest(template = "empty")
    public static void arenaAndProceduralDecks(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        BuildingPart original = AssetRegistries.PARTS.get("c5_e1");
        String before = original.writeToJSon().toString();
        for (long seed : new long[]{1, 91823, -17}) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    BuildingPart arena = DungeonInteriors.vary(original, palette, seed, 6 + dx, 6 + dz);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int ax = dx * 16 + x;
                            int az = dz * 16 + z;
                            if (ax >= 4 && ax <= 27 && az >= 4 && az <= 27) {
                                helper.assertTrue(!palette.getPalette().get(arena.getC(x, 0, z)).isAir(), "Arena floor missing");
                                for (int y = 1; y <= 30; y++) {
                                    helper.assertTrue(palette.getPalette().get(arena.getC(x, y, z)).isAir(), "Arena obstructed");
                                }
                            }
                        }
                    }
                }
            }
        }
        BuildingPart first = DungeonInteriors.vary(original, palette, 1, 5, 5);
        String repeated = DungeonInteriors.vary(original, palette, 1, 5, 5).writeToJSon().toString();
        helper.assertTrue(first.writeToJSon().toString().equals(repeated), "Generation depends on call order");
        boolean different = false;
        for (int seed = 2; seed < 10; seed++) {
            different |= !repeated.equals(DungeonInteriors.vary(original, palette, seed, 5, 5).writeToJSon().toString());
        }
        helper.assertTrue(different, "Interior has no seed variation");
        helper.assertTrue(before.equals(original.writeToJSon().toString()), "Shared authored part changed");
        for (int deck : new int[]{0, 10, 20}) {
            // All four exits have a floor and three blocks of headroom.
            for (int[] exit : new int[][]{{0, 8}, {15, 8}, {8, 0}, {8, 15}}) {
                helper.assertTrue(!palette.getPalette().get(first.getC(exit[0], deck, exit[1])).isAir(), "Corridor floor missing");
                for (int h = 1; h <= 3; h++) {
                    helper.assertTrue(palette.getPalette().get(first.getC(exit[0], deck + h, exit[1])).isAir(), "Corridor exit obstructed");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cubeStorageIsRecessedAndReachable(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        int caches = 0;
        var facings = new HashSet<Direction>();
        for (int seed = 0; seed < 16; seed++) {
            for (int[] chunk : new int[][]{{5, 5}, {10, 10}, {6, 6}, {6, 7}, {7, 6}, {7, 7}}) {
                BuildingPart part = DungeonInteriors.vary(AssetRegistries.PARTS.get("c5_e1"), palette, seed, chunk[0], chunk[1]);
                for (int y = 1; y < part.getSliceCount() - 1; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            var state = palette.getPalette().get(part.getC(x, y, z));
                            if (!state.is(Registration.STORAGE.get())) {
                                continue;
                            }
                            caches++;
                            Direction facing = state.getValue(BlockStateProperties.FACING);
                            facings.add(facing);
                            BlockPos cache = new BlockPos(x, y, z);
                            for (Direction side : Direction.values()) {
                                if (side == facing) {
                                    continue;
                                }
                                BlockPos frame = cache.relative(side);
                                helper.assertTrue(palette.getPalette().get(part.getC(frame.getX(), frame.getY(), frame.getZ()))
                                                .isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                                        "Storage must have a solid back, sill, header and side frame: " + cache);
                            }
                            if (chunk[0] >= 6 && chunk[0] <= 7 && chunk[1] >= 6 && chunk[1] <= 7) {
                                BlockPos backing = cache.relative(facing.getOpposite());
                                for (int h = y + 2; h <= 30; h++) {
                                    helper.assertTrue(palette.getPalette().get(part.getC(backing.getX(), h, backing.getZ()))
                                                    .isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                                            "Arena cache backing must join a structural rib up to the ceiling");
                                }
                            }
                            // Flood the walking plane from the central route to the cube's exposed face.
                            int feet = y - 1;
                            var reached = new HashSet<BlockPos>();
                            var queue = new ArrayDeque<BlockPos>();
                            BlockPos start = new BlockPos(8, feet, 8);
                            reached.add(start);
                            queue.add(start);
                            while (!queue.isEmpty()) {
                                BlockPos pos = queue.removeFirst();
                                for (Direction step : Direction.Plane.HORIZONTAL) {
                                    BlockPos next = pos.relative(step);
                                    if (next.getX() >= 0 && next.getX() < 16 && next.getZ() >= 0 && next.getZ() < 16
                                            && palette.getPalette().get(part.getC(next.getX(), feet, next.getZ())).isAir()
                                            && palette.getPalette().get(part.getC(next.getX(), feet + 1, next.getZ())).isAir()
                                            && reached.add(next)) {
                                        queue.add(next);
                                    }
                                }
                            }
                            BlockPos standing = cache.below().relative(facing);
                            helper.assertTrue(reached.contains(standing), "Storage controls must be reachable from the main route");
                            helper.assertTrue(palette.getPalette().get(part.getC(standing.getX(), feet - 1, standing.getZ()))
                                            .isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                                    "Storage needs a solid floor in front of its controls");
                        }
                    }
                }
            }
        }
        helper.assertTrue(caches > 64 && facings.size() == 4, "Check arena caches, room caches and every control orientation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void generatedElevatorHasStopsWithoutPowerPods(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        BuildingPart part = DungeonInteriors.vary(AssetRegistries.PARTS.get("c5_w1"), palette, 42, 6, 6);
        BlockPos origin = new BlockPos(130000, 170, 130000);
        var level = helper.getLevel();
        level.getChunkAt(origin);
        CityFeature.placePart(level, origin, part, palette);
        ElevatorTile elevator = (ElevatorTile) level.getBlockEntity(origin.offset(1, 0, 7));
        helper.assertTrue(elevator != null && elevator.getHeight() == 23, "Elevator must reach all decks");
        for (int y = 1; y < 30; y++) {
            helper.assertTrue(level.getBlockState(origin.offset(1, y, 7)).isAir(), "Elevator beam is obstructed");
            if (y != 10 && y != 20) {
                helper.assertTrue(level.getBlockState(origin.offset(0, y, 7)).isAir(), "Elevator backing blocks the entrance");
            }
        }
        for (int deck : new int[]{0, 10, 20}) {
            helper.assertTrue(level.getBlockState(origin.offset(2, deck, 7)).isCollisionShapeFullBlock(level, origin.offset(2, deck, 7)),
                    "Elevator needs a solid landing at every deck");
            helper.assertTrue(level.getBlockState(origin.offset(2, deck + 1, 7)).isAir()
                    && level.getBlockState(origin.offset(2, deck + 2, 7)).isAir(), "Landing headroom blocked");
            helper.assertTrue(level.getBlockState(origin.offset(0, deck + 1, 5)).is(Registration.LEVEL_MARKER.get()),
                    "Missing floor selector at deck " + deck);
        }
        for (int y = 0; y < part.getSliceCount(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    helper.assertTrue(!palette.getPalette().get(part.getC(x, y, z)).is(Blocks.LADDER), "Vanilla ladder in alien dungeon");
                }
            }
        }
        helper.assertTrue(level.getBlockState(origin.offset(1, 1, 6)).isAir()
                && level.getBlockState(origin.offset(1, 1, 8)).isAir(), "Old elevator power tanks remain");
        elevator.tickServer();
        ElevatorTile restored = new ElevatorTile(elevator.getBlockPos(), elevator.getBlockState());
        restored.load(elevator.saveWithoutMetadata());
        helper.assertTrue(restored.getHeight() == 23, "Elevator travel height must persist");
        int displays = 0;
        for (var entry : part.getTeInfo().entrySet()) {
            if (!(entry.getValue().get("AlienText") instanceof String text)) {
                continue;
            }
            BlockPos pos = origin.offset(entry.getKey());
            DisplayBaseTile display = (DisplayBaseTile) level.getBlockEntity(pos);
            helper.assertTrue(display != null && display.modules().size() == 1, "Generated consoles need an installed module");
            var module = display.modules().get(0);
            helper.assertTrue(module.stack().is(Registration.DISPLAY_ALIEN_TEXT.get())
                            && DisplayGeometry.fits(display, module.x(), module.y(), module.size()),
                    "The alien module must fit its generated display");
            helper.assertTrue(DisplayModuleItem.config(module.stack()).getString("Text").equals(text),
                    "Generated symbols must reach the module item");
            DisplayBaseTile saved = new DisplayBaseTile(pos, display.getBlockState());
            saved.load(display.saveWithoutMetadata());
            helper.assertTrue(saved.modules().size() == 1
                            && DisplayModuleItem.config(saved.modules().get(0).stack()).equals(DisplayModuleItem.config(module.stack())),
                    "Alien glyphs and color must survive a world reload");
            level.destroyBlock(pos, true);
            long recovered = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).stream()
                    .filter(drop -> drop.getItem().is(Registration.DISPLAY_ALIEN_TEXT.get())
                            && DisplayModuleItem.config(drop.getItem()).equals(DisplayModuleItem.config(module.stack()))).count();
            helper.assertTrue(recovered == 1, "Breaking a ruin console must salvage exactly one configured alien module");
            displays++;
        }
        helper.assertTrue(displays > 0, "Control desks must include alien displays");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void skylineHasSolidSupportsAndRestrainedPanels(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("landscapecities");
        var panel = LegacyBlockStates.read("ariente:blacktech@type=panel").getBlock();
        for (String prefix : new String[]{"building", "park"}) {
            int count = prefix.equals("building") ? 13 : 9;
            for (int i = 1; i <= count; i++) {
                BuildingPart original = AssetRegistries.PARTS.get(prefix + i);
                BuildingPart variant = AssetRegistries.PARTS.get(prefix + (i + count));
                // Flood from authored supports to catch detached assemblies while allowing
                // side-mounted slopes, lights and fences on a continuous structural core.
                var connected = new HashSet<BlockPos>();
                var queue = new ArrayDeque<BlockPos>();
                for (int y = 0; y < original.getSliceCount(); y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            if (!palette.getPalette().get(original.getC(x, y, z)).isAir()) {
                                BlockPos pos = new BlockPos(x, y, z);
                                connected.add(pos);
                                queue.add(pos);
                            }
                        }
                    }
                }
                while (!queue.isEmpty()) {
                    BlockPos pos = queue.removeFirst();
                    for (Direction direction : Direction.values()) {
                        BlockPos next = pos.relative(direction);
                        if (next.getX() >= 0 && next.getX() < 16 && next.getZ() >= 0 && next.getZ() < 16
                                && next.getY() >= 0 && next.getY() < variant.getSliceCount()
                                && !palette.getPalette().get(variant.getC(next.getX(), next.getY(), next.getZ())).isAir()
                                && connected.add(next)) {
                            queue.add(next);
                        }
                    }
                }
                for (int y = 0; y < variant.getSliceCount(); y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            var actual = palette.getPalette().get(variant.getC(x, y, z));
                            var before = y < original.getSliceCount() ? palette.getPalette().get(original.getC(x, y, z)) : Blocks.AIR.defaultBlockState();
                            if (!before.isAir()) {
                                helper.assertTrue(actual.equals(before), "Authored building detail overwritten");
                            } else if (!actual.isAir()) {
                                helper.assertTrue(!actual.is(panel), "New spires must not be covered in panels");
                                helper.assertTrue(connected.contains(new BlockPos(x, y, z)),
                                        "Unsupported spire at " + variant.getName() + " " + x + "," + y + "," + z);
                            }
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wallDetailsKeepRoutesOpen(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        var details = new HashSet<String>();
        for (int seed = 0; seed < 12; seed++) {
            BuildingPart part = DungeonInteriors.vary(AssetRegistries.PARTS.get("c5_e1"), palette, seed, 5, 5);
            for (int deck : new int[]{0, 10, 20}) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int h = 1; h < 10; h++) {
                            var state = palette.getPalette().get(part.getC(x, deck + h, z));
                            if (h <= 3 && (x >= 6 && x <= 9 || z >= 6 && z <= 9)) {
                                helper.assertTrue(state.isAir(), "Decoration obstructs a corridor");
                            }
                            if (h >= 4) {
                                details.add(state.getBlock().getDescriptionId());
                            }
                        }
                    }
                }
            }
        }
        for (String name : new String[]{"tech_fence", "blue_glass_fence", "flatlight", "blacktech_darkblue_blue", "blacktech_darkblue_red"}) {
            helper.assertTrue(details.contains("block.ariente." + name), "Missing wall detail: " + name);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void skylineHasDifferentTerminals(GameTestHelper helper) {
        var palette = AssetRegistries.PALETTES.get("landscapecities");
        var terminals = new HashSet<String>();
        var fences = new HashSet<String>();
        for (int i = 14; i <= 26; i++) {
            BuildingPart part = AssetRegistries.PARTS.get("building" + i);
            boolean foundTop = false;
            for (int y = part.getSliceCount() - 1; y >= 0; y--) {
                boolean occupied = false;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        var state = palette.getPalette().get(part.getC(x, y, z));
                        if (!state.isAir()) {
                            occupied = true;
                            if (!foundTop) {
                                terminals.add(state.getBlock().getDescriptionId());
                            }
                            if (y >= 26 && state.getBlock().getDescriptionId().endsWith("_fence")) {
                                fences.add(state.getBlock().getDescriptionId());
                            }
                        }
                    }
                }
                foundTop |= occupied;
            }
        }
        helper.assertTrue(terminals.size() >= 4 && fences.size() >= 3, "Spire terminals are too uniform");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cubeDoorsElevatorsAndOpenHalls(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        int elevators = 0;
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                if (x == 4 || z == 4) {
                    continue;
                }
                BuildingPart part = DungeonInteriors.vary(AssetRegistries.PARTS.get("c5_e1"), palette, 42, x + 4, z + 4);
                BuildingPart basement = DungeonInteriors.vary(AssetRegistries.PARTS.get("c5_n1"), palette, 42, x + 4, z + 4);
                if (palette.getPalette().get(basement.getC(1, 1, 7)).is(Registration.ELEVATOR.get())) {
                    elevators++;
                    helper.assertTrue(x > 1 && x < 7 && z > 1 && z < 7, "Elevator in outer doorway approach");
                    helper.assertTrue(palette.getPalette().get(part.getC(1, 0, 7)).isAir(), "Through lift must open through the main floor");
                }
            }
        }
        helper.assertTrue(elevators == 3, "Keep only three main-floor lifts outside the arena");
        for (String name : new String[]{"c5_t1", "c5_v1", "c5_x1", "c5_z1"}) {
            BuildingPart part = DungeonInteriors.vary(AssetRegistries.PARTS.get(name), palette, 42, 5, 5);
            boolean alongX = name.equals("c5_t1") || name.equals("c5_z1");
            int wall = name.equals("c5_t1") || name.equals("c5_v1") ? 15 : 0;
            String neighbor = switch (name) {
                case "c5_t1" -> "c5_L1";
                case "c5_v1" -> "c5_K1";
                case "c5_x1" -> "c5_M1";
                default -> "c5_N1";
            };
            BuildingPart inner = DungeonInteriors.vary(AssetRegistries.PARTS.get(neighbor), palette, 42, 5, 5);
            for (int t = 7; t <= 8; t++) {
                int x = alongX ? t : wall;
                int z = alongX ? wall : t;
                helper.assertTrue(palette.getPalette().get(part.getC(x, 1, z)).is(Registration.DOOR_MARKER.get()),
                        "Missing raised inner threshold markers: " + name);
                helper.assertTrue(palette.getPalette().get(part.getC(x, 0, z))
                        .isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), "Door must rest on a solid floor");
                for (int h = 1; h <= 3; h++) {
                    helper.assertTrue(palette.getPalette().get(inner.getC(alongX ? t : 15 - wall, h, alongX ? 15 - wall : t)).isAir(),
                            "Second door plane or obstruction at adjoining cross end: " + neighbor);
                }
                for (int h = 2; h <= 3; h++) {
                    helper.assertTrue(palette.getPalette().get(part.getC(x, h, z)).isAir(), "Door headroom obstructed");
                }
            }
        }

        BuildingPart outer = DungeonInteriors.vary(AssetRegistries.PARTS.get("c5_t1"), palette, 42, 5, 5);
        for (int t = 6; t <= 7; t++) {
            helper.assertTrue(!palette.getPalette().get(outer.getC(0, 0, t)).is(Registration.DOOR_MARKER.get()),
                    "Blue dot threshold must stay open");
            for (int h = 1; h <= 3; h++) {
                helper.assertTrue(palette.getPalette().get(outer.getC(0, h, t)).isAir(), "Blue dot passage obstructed");
            }
        }
        BuildingPart hall = DungeonInteriors.vary(AssetRegistries.PARTS.get("c5_b1"), palette, 42, 5, 5);
        helper.assertTrue(palette.getPalette().get(hall.getC(7, 0, 7)).is(Registration.FLUX_GLOW.get()), "Hall needs inset flux lighting");
        helper.assertTrue(hall.getC(0, 10, 15).equals(new PaletteIndex('a', ' ')), "Preserve white wall ribs");
        for (int x = 0; x < 16; x++) {
            for (int z = 4; z < 16; z++) {
                helper.assertTrue(!hall.getC(x, 31, z).equals(new PaletteIndex('a', ' ')), "Pale ceiling remains");
            }
        }
        for (String name : new String[]{"c5_C1", "c5_I1"}) {
            BuildingPart original = AssetRegistries.PARTS.get(name);
            helper.assertTrue(original.writeToJSon().equals(DungeonInteriors.vary(original, palette, 42, 8, 8).writeToJSon()),
                    "Keep the central machinery unchanged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void outerCorridorBaysVaryAndStayConnected(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        for (String name : new String[]{"c5_t1", "c5_v1", "c5_x1", "c5_z1", "c5_s1", "c5_u1", "c5_y1", "c5_01"}) {
            BuildingPart original = AssetRegistries.PARTS.get(name);
            String before = original.writeToJSon().toString();
            var silhouettes = new HashSet<String>();
            for (int chunk = 5; chunk <= 8; chunk++) {
                BuildingPart part = DungeonInteriors.vary(original, palette, 42, chunk, 5);
                helper.assertTrue(part.writeToJSon().equals(DungeonInteriors.vary(original, palette, 42, chunk, 5).writeToJSon()),
                        "Corridor details must be deterministic");
                StringBuilder silhouette = new StringBuilder();
                int visibleAdditions = 0;
                for (int y = 1; y <= 10; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            var state = palette.getPalette().get(part.getC(x, y, z));
                            boolean added = palette.getPalette().get(original.getC(x, y, z)).isAir()
                                    && !state.isAir() && !state.is(Registration.DOOR_MARKER.get());
                            silhouette.append(added ? '1' : '0');
                            if (added && y <= 5) {
                                visibleAdditions++;
                            }
                            if (x == 0 || x == 15 || z == 0 || z == 15) {
                                if (palette.getPalette().get(original.getC(x, y, z)).isAir()) {
                                    helper.assertTrue(state.isAir() || state.is(Registration.DOOR_MARKER.get()),
                                            "Authored ring opening blocked: " + name);
                                }
                            }
                        }
                    }
                }
                helper.assertTrue(visibleAdditions >= 12, "Missing eye-level corridor fittings: " + name + " / " + chunk);
                silhouettes.add(silhouette.toString());
                boolean[][] reachable = reachableFloor(part, palette);
                boolean[][] previous = reachableFloor(original, palette);
                for (int t = 0; t < 16; t++) {
                    for (int[] point : new int[][]{{0, t}, {15, t}, {t, 0}, {t, 15}}) {
                        helper.assertTrue(!previous[point[0]][point[1]] || reachable[point[0]][point[1]],
                                "Corridor fitting disconnected a door: " + name);
                    }
                }
            }
            helper.assertTrue(silhouettes.size() == 4, "Neighboring corridor bays must have distinct geometry: " + name);
            helper.assertTrue(original.writeToJSon().toString().equals(before), "Corridor template was modified");
        }
        helper.succeed();
    }

    private static boolean[][] reachableFloor(BuildingPart part, Palette palette) {
        boolean[][] visited = new boolean[16][16];
        var pending = new ArrayDeque<BlockPos>();
        pending.add(new BlockPos(8, 1, 8));
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            int x = pos.getX();
            int z = pos.getZ();
            if (x < 0 || x >= 16 || z < 0 || z >= 16 || visited[x][z]) {
                continue;
            }
            var feet = palette.getPalette().get(part.getC(x, 1, z));
            if (palette.getPalette().get(part.getC(x, 0, z)).isAir()
                    || !feet.isAir() && !feet.is(Registration.DOOR_MARKER.get())
                    || !palette.getPalette().get(part.getC(x, 2, z)).isAir()) {
                continue;
            }
            visited[x][z] = true;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                pending.add(pos.relative(direction));
            }
        }
        return visited;
    }

    @GameTest(template = "empty")
    public static void crossEndsHavePlatformsAndClearApproaches(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city5_1");
        for (String name : new String[]{"c5_E1", "c5_F1", "c5_G1", "c5_H1", "c5_K1", "c5_L1", "c5_M1", "c5_N1"}) {
            BuildingPart original = AssetRegistries.PARTS.get(name);
            BuildingPart part = DungeonInteriors.vary(original, palette, 42, 5, 5);
            int additions = 0;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (palette.getPalette().get(original.getC(x, y, z)).isAir()
                                && !palette.getPalette().get(part.getC(x, y, z)).isAir()) {
                            additions++;
                        }
                        if (x >= 6 && x <= 9 && z >= 6 && z <= 9 && y >= 1 && y <= 3) {
                            helper.assertTrue(palette.getPalette().get(part.getC(x, y, z)).isAir(), "Cross-end route obstructed");
                        }
                    }
                }
            }
            helper.assertTrue(additions > 80, "Unfinished cross end: " + name);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void approachesAndVegetation(GameTestHelper helper) {
        helper.assertTrue(CityFeature.approachHeight(80, 64, 0) == 81, "Low ground must meet the entrance");
        helper.assertTrue(CityFeature.approachHeight(80, 110, 1) == 81, "High ground must clear the entrance");
        for (int natural : new int[]{64, 110}) {
            helper.assertTrue(CityFeature.approachHeight(80, natural, 11) == natural, "Apron must rejoin terrain");
        }
        var palette = AssetRegistries.PALETTES.get("city5_1");
        var slice = new BuildingPart.PalettedSlice(new PaletteIndex[]{new PaletteIndex('!', '0')});
        var part = new BuildingPart("vegetation_regression", 1, 1,
                new BuildingPart.PalettedSlice[]{slice, slice.copy()}, Map.of());
        BlockPos origin = new BlockPos(130032, 170, 130000);
        var level = helper.getLevel();
        level.getChunkAt(origin);
        level.setBlock(origin.below(), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(origin, Blocks.GRASS.defaultBlockState(), 2);
        CityFeature.placePart(level, origin, part, palette);
        helper.assertTrue(level.getBlockState(origin).isAir(), "Leading authored air must clear vegetation");
        helper.assertTrue(level.getBlockState(origin.below()).is(Blocks.DIRT), "Ground below the part must survive");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void skylineAndDungeonVariants(GameTestHelper helper) {
        helper.assertTrue(ArienteLandscapeCity.NUM_BUILDINGS >= 26 && ArienteLandscapeCity.NUM_PARKS >= 18, "City variety not doubled");
        for (int i = 14; i <= 26; i++) {
            BuildingPart part = AssetRegistries.PARTS.get("building" + i);
            helper.assertTrue(part != null && part.getSliceCount() > 26, "Missing taller skyline variant");
        }
        Palette cityPalette = AssetRegistries.PALETTES.get(ArienteLandscapeCity.CITY_PALETTE);
        int cityDisplays = 0;
        int undecoratedBuildings = 0;
        for (int i = 1; i <= 26; i++) {
            BuildingPart authored = AssetRegistries.PARTS.get("building" + i);
            String before = authored.writeToJSon().toString();
            BuildingPart decorated = authored.createCopy(authored.getName());
            DungeonDisplays.decorate(decorated, cityPalette, 42, 8, 8);
            BuildingPart repeated = authored.createCopy(authored.getName());
            DungeonDisplays.decorate(repeated, cityPalette, 42, 8, 8);
            helper.assertTrue(decorated.writeToJSon().equals(repeated.writeToJSon()), "Building consoles must be deterministic");
            helper.assertTrue(authored.writeToJSon().toString().equals(before), "Building consoles must preserve the shared template");
            int count = (int) decorated.getTeInfo().values().stream().filter(data -> data.containsKey("AlienText")).count();
            if (count > 0 && cityDisplays == 0) {
                verifyAlienConsoles(helper, decorated, cityPalette, new BlockPos(132000, 170, 132000), Transform.MIRROR_90_X);
            }
            helper.assertTrue(count <= 1, "City buildings should have at most one alien console");
            cityDisplays += count;
            undecoratedBuildings += count == 0 ? 1 : 0;
        }
        helper.assertTrue(cityDisplays > 0 && undecoratedBuildings > 0, "Only some city buildings should receive alien consoles");
        for (String plan : new String[]{"city0_0", "city0_1", "city1_1", "city1_2", "city2_1", "city3_1", "city4_1", "station"}) {
            Palette palette = AssetRegistries.PALETTES.get(plan);
            boolean different = false;
            boolean alienDisplays = false;
            for (var parts : AssetRegistries.CITYPLANS.get(plan).getPartPalette().values()) {
                BuildingPart part = AssetRegistries.PARTS.get(parts.getPalette().get(0));
                String original = part.writeToJSon().toString();
                for (int seed = 0; seed < 8; seed++) {
                    BuildingPart variant = DungeonInteriors.vary(part, palette, seed, 8, 8);
                    different |= !original.equals(variant.writeToJSon().toString());
                    part.getTeInfo().forEach((pos, data) -> helper.assertTrue(data.equals(variant.getTeInfo().get(pos)), "Authored equipment settings changed"));
                    for (var entry : variant.getTeInfo().entrySet()) {
                        if (!part.getTeInfo().containsKey(entry.getKey())) {
                            BlockPos pos = entry.getKey();
                            helper.assertTrue(entry.getValue().get("AlienText") instanceof String
                                            && palette.getPalette().get(variant.getC(pos.getX(), pos.getY(), pos.getZ())).is(Registration.DISPLAY_BASE.get()),
                                    "Additional equipment must be an alien console");
                            if (plan.equals("city4_1") && !alienDisplays) {
                                verifyAlienConsoles(helper, variant, palette, new BlockPos(132032, 170, 132000), Transform.ROTATE_90);
                            }
                            alienDisplays = true;
                        }
                    }
                    helper.assertTrue(part.getSliceCount() == variant.getSliceCount(), "Dungeon joins changed height");
                }
            }
            helper.assertTrue(different, "No visible alternate for " + plan);
            helper.assertTrue(!plan.equals("city3_1") && !plan.equals("city4_1") || alienDisplays,
                    "Underground and floating dungeons need alien displays");
        }
        helper.succeed();
    }

    private static void verifyAlienConsoles(GameTestHelper helper, BuildingPart part, Palette palette, BlockPos origin, Transform transform) {
        var level = helper.getLevel();
        level.getChunkAt(origin);
        CityFeature.placePart(level, origin, part, palette, transform);
        for (var entry : part.getTeInfo().entrySet()) {
            if (!(entry.getValue().get("AlienText") instanceof String text)) {
                continue;
            }
            BlockPos pos = origin.offset(transform.transform(entry.getKey()));
            DisplayBaseTile display = (DisplayBaseTile) level.getBlockEntity(pos);
            helper.assertTrue(display != null && display.modules().size() == 1, "Rotated city and floating consoles must contain a module");
            var module = display.modules().get(0);
            helper.assertTrue(DisplayGeometry.fits(display, module.x(), module.y(), module.size())
                            && DisplayModuleItem.config(module.stack()).getString("Text").equals(text),
                    "Rotated alien consoles must preserve their glyphs and fit their screen");
            BlockPos front = pos.relative(DisplayGeometry.facing(display));
            for (int y = -1; y <= 1; y++) {
                helper.assertTrue(level.getBlockState(front.offset(0, y, 0)).isAir(), "Alien consoles must leave clear standing room");
            }
            helper.assertTrue(level.getBlockState(front.below(2)).isCollisionShapeFullBlock(level, front.below(2)),
                    "Alien consoles must face a supported floor");
        }
    }
}
