package mcjty.arienteworld;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import mcjty.ariente.api.EnumFacingUpDown;
import mcjty.ariente.blocks.BlockProperties;
import mcjty.ariente.blocks.utility.door.DoorMarkerTile;
import mcjty.ariente.blocks.utility.wireless.RedstoneChannels;
import mcjty.ariente.blocks.utility.wireless.WirelessButtonTile;
import mcjty.ariente.blocks.utility.wireless.WirelessLockTile;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.dimension.DungeonInteriors;
import mcjty.arienteworld.dimension.CityFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class UndergroundDungeonTests {

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void guardianWingRequiresButtonBeforeWalkingToCore(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city3_1");
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "underground-walker"));
        for (int seed = 0; seed < 8; seed++) {
            BlockPos origin = new BlockPos(1600000 + seed * 64, 120, 1600000);
            for (int dx = 0; dx < 2; dx++) {
                BuildingPart original = AssetRegistries.PARTS.get(dx == 0 ? "c3_1_e" : "c3_1_f");
                String before = original.writeToJSon().toString();
                BuildingPart part = DungeonInteriors.vary(original, palette, seed, 6 + dx, 7);
                CityFeature.placePart(level, origin.offset(dx * 16, 0, 0), part, palette);
                helper.assertTrue(original.writeToJSon().toString().equals(before), "Circulation must preserve shared templates");
            }
            for (int dz = 1; dz <= 2; dz++) {
                BuildingPart part = DungeonInteriors.vary(AssetRegistries.PARTS.get(dz == 1 ? "c3_1_i" : "c3_1_m"),
                        palette, seed, 6, 7 + dz);
                CityFeature.placePart(level, origin.offset(0, 0, dz * 16), part, palette);
            }
            for (int dx = 0; dx < 4; dx++) {
                BuildingPart part = DungeonInteriors.vary(AssetRegistries.PARTS.get("c3_1_" + (char) ('a' + dx)),
                        palette, seed, 6 + dx, 6);
                CityFeature.placePart(level, origin.offset(dx * 16, 0, -16), part, palette);
            }
            if (seed == 0) {
                player.setPos(origin.getX() + 31.5, origin.getY() + 7, origin.getZ() + 9.5);
                level.addNewPlayer(player);
            }
            List<DoorMarkerTile> doors = new ArrayList<>();
            for (int z : new int[]{-11, -10, 7, 8, 9, 10}) {
                DoorMarkerTile door = (DoorMarkerTile) level.getBlockEntity(origin.offset(15, 12, z));
                helper.assertTrue(door != null && door.isLocked() && !door.isOpen(),
                        "Both guardian entrances must start closed before signal initialization: z=" + z + ", door="
                                + (door == null ? "missing" : door.saveWithoutMetadata()));
                doors.add(door);
            }
            assertWesternWallClosed(helper, player, origin);
            WirelessButtonTile button = (WirelessButtonTile) level.getBlockEntity(origin.offset(47, 8, -8));
            int channel = RedstoneChannels.getChannels(level).newChannel();
            button.setChannel(channel);
            List<WirelessLockTile> locks = new ArrayList<>();
            for (BlockPos position : List.of(origin.offset(47, 8, -13), origin.offset(16, 13, -12), origin.offset(16, 13, 6))) {
                WirelessLockTile lock = (WirelessLockTile) level.getBlockEntity(position);
                helper.assertTrue(lock != null && lock.getDesiredChannel() == button.getDesiredChannel(),
                        "Every guardian entrance must use the same authored button channel");
                lock.setChannel(channel);
                lock.tickServer();
                locks.add(lock);
            }
            player.setPos(origin.getX() + 31.5, origin.getY() + 7, origin.getZ() + 9.5);
            player.setDeltaMovement(Vec3.ZERO);
            player.setOnGround(true);
            for (int step = 0; step < 200; step++) {
                doors.forEach(DoorMarkerTile::tickServer);
                player.move(MoverType.SELF, new Vec3(-0.1, -0.08, 0));
            }
            helper.assertTrue(player.getX() >= origin.getX() + 16 && doors.stream().allMatch(door -> door.isLocked() && !door.isOpen()),
                    "Walking up the obvious staircase must stop at the locked door until the button is pressed");
            player.setPos(origin.getX() + 46.5, origin.getY() + 7, origin.getZ() - 7.5);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            WirelessButtonTile.onBlockActivatedWithToggle(level, button.getBlockPos(), player, InteractionHand.MAIN_HAND);
            locks.forEach(WirelessLockTile::tickServer);
            helper.assertTrue(doors.stream().noneMatch(DoorMarkerTile::isLocked),
                    "The prominent button must unlock the staircase door and northern entrance together");
            for (int lane = 7; lane <= 10; lane++) {
                player.setPos(origin.getX() + 31.5, origin.getY() + 7, origin.getZ() + lane + 0.5);
                player.setDeltaMovement(Vec3.ZERO);
                player.setOnGround(true);
                for (int step = 0; step < 200; step++) {
                    doors.forEach(DoorMarkerTile::tickServer);
                    player.move(MoverType.SELF, new Vec3(-0.1, -0.08, 0));
                }
                helper.assertTrue(player.getX() < origin.getX() + 13 && Math.abs(player.getY() - origin.getY() - 12) < 0.01,
                        "After pressing the button, every stair lane must walk through the open guardian door: seed "
                                + seed + ", lane " + lane + ", position " + player.position().subtract(Vec3.atLowerCornerOf(origin)));
                helper.assertTrue(level.noCollision(player), "The entrance must leave full player headroom");
                for (int step = 0; step < 10; step++) {
                    player.move(MoverType.SELF, new Vec3(0.1, -0.08, 0));
                }
                for (int step = 0; step < (44 - lane) * 10; step++) {
                    player.move(MoverType.SELF, new Vec3(0, -0.08, 0.1));
                }
                helper.assertTrue(player.getZ() > origin.getZ() + 44 && Math.abs(player.getY() - origin.getY() - 12) < 0.01,
                        "The marked balcony must stay clear past the guardian to the core chamber: seed " + seed
                                + ", position " + player.position().subtract(Vec3.atLowerCornerOf(origin)));
                for (int step = 0; step < 50; step++) {
                    player.move(MoverType.SELF, new Vec3(-0.1, -0.08, 0));
                }
                helper.assertTrue(player.getX() < origin.getX() + 8 && Math.abs(player.getY() - origin.getY() - 16) < 0.01,
                        "The existing core-chamber ramps must complete the route: seed " + seed + ", position "
                                + player.position().subtract(Vec3.atLowerCornerOf(origin)));
            }
            WirelessButtonTile.onBlockActivatedWithToggle(level, button.getBlockPos(), player, InteractionHand.MAIN_HAND);
            locks.forEach(WirelessLockTile::tickServer);
            helper.assertTrue(doors.stream().allMatch(door -> door.isLocked() && !door.isOpen()),
                    "Switching the button off must close both guardian entrances again");
        }
        player.discard();
        helper.succeed();
    }

    private static void assertWesternWallClosed(GameTestHelper helper, Player player, BlockPos origin) {
        // Half-block sampling includes the gaps that disconnected glass-fence posts would leave.
        for (double z = .5; z < 64; z += .5) {
            for (double y = 3; y <= 20; y += .5) {
                boolean blocked = false;
                for (double x : new double[]{14.5, 15.5}) {
                    AABB body = new AABB(origin.getX() + x - .3, origin.getY() + y, origin.getZ() + z - 16 - .3,
                            origin.getX() + x + .3, origin.getY() + y + 1.5, origin.getZ() + z - 16 + .3);
                    blocked |= helper.getLevel().getBlockCollisions(player, body).iterator().hasNext();
                }
                helper.assertTrue(blocked, "A crouching player must not bypass the button through the western partition at Y=" + y + ", Z=" + z);
            }
        }
    }

    @GameTest(template = "empty")
    public static void prominentTowerButtonControlsItsOriginalDoor(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city3_1");
        var level = helper.getLevel();
        BlockPos origin = new BlockPos(1601000, 120, 1601000);
        for (int dx = 0; dx < 2; dx++) {
            BuildingPart part = DungeonInteriors.vary(AssetRegistries.PARTS.get(dx == 0 ? "c3_1_c" : "c3_1_d"),
                    palette, 42, 8 + dx, 6);
            CityFeature.placePart(level, origin.offset(dx * 16, 0, 0), part, palette);
        }
        BlockPos controlPos = origin.offset(15, 8, 8);
        WirelessButtonTile button = (WirelessButtonTile) level.getBlockEntity(controlPos);
        WirelessLockTile lock = (WirelessLockTile) level.getBlockEntity(origin.offset(15, 8, 3));
        helper.assertTrue(button != null && button.getDesiredChannel() == 2 && button.getDesiredChannel() == lock.getDesiredChannel(),
                "The moved button must preserve the authored door channel");
        helper.assertTrue(level.getBlockState(origin.offset(14, 2, 4)).isAir(), "The obscure original button must be removed");
        helper.assertTrue(level.getBlockState(controlPos.above()).is(Registration.FLAT_LIGHT.get()),
                "The button must have a dedicated light above it");
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(12, 7, 7), origin.offset(14, 9, 9))) {
            helper.assertTrue(level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(),
                    "The door control needs a clear approach: " + pos.subtract(origin) + " contains " + level.getBlockState(pos));
        }
        int channel = RedstoneChannels.getChannels(level).newChannel();
        button.setChannel(channel);
        lock.setChannel(channel);
        lock.tickServer();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "underground-control"));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (int z : new int[]{4, 5}) {
            DoorMarkerTile door = (DoorMarkerTile) level.getBlockEntity(origin.offset(16, 7, z));
            helper.assertTrue(door.isLocked(), "The tower door starts locked");
        }
        WirelessButtonTile.onBlockActivatedWithToggle(level, controlPos, player, InteractionHand.MAIN_HAND);
        lock.tickServer();
        for (int z : new int[]{4, 5}) {
            DoorMarkerTile door = (DoorMarkerTile) level.getBlockEntity(origin.offset(16, 7, z));
            helper.assertTrue(!door.isLocked(), "The visible button must unlock both tower door panels without a keycard");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wirelessButtonSelectionMatchesEveryModelOrientation(GameTestHelper helper) throws IOException {
        JsonObject variants = resource("blockstates/wireless_button").getAsJsonObject("variants");
        JsonObject element = resource("models/block/base/flatglow").getAsJsonArray("elements").get(0).getAsJsonObject();
        JsonArray from = element.getAsJsonArray("from");
        JsonArray to = element.getAsJsonArray("to");
        for (Direction facing : Direction.values()) {
            for (boolean powered : new boolean[]{false, true}) {
                JsonObject variant = variants.getAsJsonObject("facing=" + facing.getName() + ",power=" + powered);
                double rx = variant.has("x") ? Math.toRadians(-variant.get("x").getAsInt()) : 0;
                double ry = variant.has("y") ? Math.toRadians(-variant.get("y").getAsInt()) : 0;
                AABB expected = null;
                for (int corner = 0; corner < 8; corner++) {
                    double x = ((corner & 1) == 0 ? from : to).get(0).getAsDouble() / 16 - .5;
                    double y = ((corner & 2) == 0 ? from : to).get(1).getAsDouble() / 16 - .5;
                    double z = ((corner & 4) == 0 ? from : to).get(2).getAsDouble() / 16 - .5;
                    double rotatedY = y * Math.cos(rx) - z * Math.sin(rx);
                    double rotatedZ = y * Math.sin(rx) + z * Math.cos(rx);
                    Vec3 point = new Vec3(x * Math.cos(ry) + rotatedZ * Math.sin(ry) + .5, rotatedY + .5,
                            -x * Math.sin(ry) + rotatedZ * Math.cos(ry) + .5);
                    AABB bounds = new AABB(point, point);
                    expected = expected == null ? bounds : expected.minmax(bounds);
                }
                BlockState state = Registration.WIRELESS_BUTTON.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, facing).setValue(BlockProperties.POWER, powered);
                var shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                AABB actual = shape.bounds();
                helper.assertTrue(new Vec3(actual.minX, actual.minY, actual.minZ).distanceToSqr(new Vec3(expected.minX, expected.minY, expected.minZ)) < 1e-12
                                && new Vec3(actual.maxX, actual.maxY, actual.maxZ).distanceToSqr(new Vec3(expected.maxX, expected.maxY, expected.maxZ)) < 1e-12,
                        "Button selection must match the visible model on " + facing + ": " + actual + " vs " + expected);
                Vec3 direction = Vec3.atLowerCornerOf(facing.getNormal());
                var hit = shape.clip(new Vec3(.5, .5, .5).add(direction.scale(2)), new Vec3(.5, .5, .5).subtract(direction.scale(2)), BlockPos.ZERO);
                helper.assertTrue(hit != null && hit.getDirection() == facing, "The visible button face must be selectable on " + facing);
            }
        }
        helper.succeed();
    }

    private static JsonObject resource(String path) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(Registration.class.getResourceAsStream("/assets/ariente/" + path + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @GameTest(template = "empty")
    public static void ceilingPanelsContinueAcrossEveryChunkJoin(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city3_1");
        BuildingPart original = AssetRegistries.PARTS.get("c3_1_top");
        String before = original.writeToJSon().toString();
        BlockState[][] ceiling = new BlockState[64][64];
        Map<BlockState, Integer> counts = new HashMap<>();
        for (int dz = 0; dz < 4; dz++) {
            for (int dx = 0; dx < 4; dx++) {
                BuildingPart part = DungeonInteriors.vary(original, palette, 42, 6 + dx, -10 + dz);
                BuildingPart translated = DungeonInteriors.vary(original, palette, 123, -26 + dx, 22 + dz);
                helper.assertTrue(part.writeToJSon().equals(translated.writeToJSon()),
                        "The ceiling must be continuous and identical at positive and negative dungeon sites");
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = palette.getPalette().get(part.getC(x, 0, z));
                        ceiling[dz * 16 + z][dx * 16 + x] = state;
                        counts.merge(state, 1, Integer::sum);
                    }
                }
            }
        }
        helper.assertTrue(counts.getOrDefault(LegacyBlockStates.read("ariente:marble@color=blue"), 0) == 63 * 8,
                "Nine by seven panels must each have a two-by-four blue marble inset");
        helper.assertTrue(counts.getOrDefault(LegacyBlockStates.read("ariente:marbletech_variation1@facing=down"), 0) == 63 * 28,
                "Every panel must retain its Marble Pattern 1 frame facing into the room");
        helper.assertTrue(counts.getOrDefault(LegacyBlockStates.read("ariente:blacktech@type=lines_glow"), 0) == 80,
                "Blue ceiling ribs must meet in glowing intersections, including the outer edges");
        // This panel crosses both the X=16 and Z=48 chunk seams.
        helper.assertTrue(ceiling[47][17].getValue(EnumFacingUpDown.FACING) == EnumFacingUpDown.NORTH_UP
                        && ceiling[52][18].getValue(EnumFacingUpDown.FACING) == EnumFacingUpDown.SOUTH_UP
                        && ceiling[50][16].getValue(EnumFacingUpDown.FACING) == EnumFacingUpDown.WEST_UP
                        && ceiling[50][19].getValue(EnumFacingUpDown.FACING) == EnumFacingUpDown.EAST_UP,
                "Inverted slopes must frame the same recess on both sides of chunk seams");
        helper.assertTrue(original.writeToJSon().toString().equals(before), "Shared ceiling template must remain unchanged");
        BuildingPart lightRoom = AssetRegistries.PARTS.get("c3_1_k");
        BlockState light = palette.getPalette().get(lightRoom.getC(8, 35, 8));
        helper.assertTrue(light.equals(LegacyBlockStates.read("ariente:flatlight@facing=down")),
                "The ceiling light must lie flat against the ceiling and face down");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hallGuardianFitsAboveRailingsAndProceduralDetails(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city3_1");
        BuildingPart original = AssetRegistries.PARTS.get("c3_1_i");
        for (int seed = 0; seed < 32; seed++) {
            BuildingPart part = DungeonInteriors.vary(original, palette, seed, 6, 8);
            for (BlockPos pos : BlockPos.betweenClosed(6, 14, 6, 10, 17, 10)) {
                helper.assertTrue(state(part, palette, pos).getCollisionShape(EmptyBlockGetter.INSTANCE, pos).isEmpty(),
                        "The guardian's entire spawn volume must clear railings and generated machinery, seed " + seed);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void undergroundDisplaysHaveBackingAndClearAccess(GameTestHelper helper) {
        Palette palette = AssetRegistries.PALETTES.get("city3_1");
        int displays = 0;
        for (char room = 'a'; room <= 'p'; room++) {
            BuildingPart original = AssetRegistries.PARTS.get("c3_1_" + room);
            int dx = (room - 'a') % 4;
            int dz = (room - 'a') / 4;
            BuildingPart part = DungeonInteriors.vary(original, palette, 42, 6 + dx, 6 + dz);
            for (var entry : part.getTeInfo().entrySet()) {
                if (!entry.getValue().containsKey("AlienText")) {
                    continue;
                }
                displays++;
                BlockPos pos = entry.getKey();
                BlockState state = state(part, palette, pos);
                helper.assertTrue(state.is(Registration.DISPLAY_BASE.get()), "Alien glyph modules need a display base");
                Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                for (int y = -1; y <= 1; y++) {
                    helper.assertTrue(state(part, palette, pos.relative(facing).offset(0, y, 0)).isAir(),
                            "Every new holo display must have unobstructed controls and viewing space");
                }
                helper.assertTrue(state(part, palette, pos.below()).isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                        "Every holo display needs a solid console or pedestal");
            }
        }
        helper.assertTrue(displays >= 24, "The underground complex needs more than occasional console-bank displays: " + displays);
        helper.succeed();
    }

    private static BlockState state(BuildingPart part, Palette palette, BlockPos pos) {
        return palette.getPalette().get(part.getC(pos.getX(), pos.getY(), pos.getZ()));
    }
}
