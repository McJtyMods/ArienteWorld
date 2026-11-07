package mcjty.arienteworld;

import mcjty.arienteworld.oregen.OverworldDungeonGen;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static mcjty.ariente.setup.Registration.WARPER;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class OverworldDungeonTests {

    @GameTest(template = "empty")
    public static void naturalDoorwayWinsOverTunnelAndKeepsHorizontalPosition(GameTestHelper helper) {
        Terrain terrain = new Terrain(helper.getLevel());
        terrain.cave(40, Direction.NORTH, 7);
        terrain.cave(-24, Direction.EAST, 11);
        terrain.generate();
        helper.assertTrue(terrain.warper().equals(terrain.center.atY(41)),
                "A natural doorway must win over a tunnel, keeping the original x/z");
        helper.assertTrue(terrain.state(terrain.center.atY(41).north(7)).isAir(),
                "The doorway must open into the existing cave");
        Terrain repeated = new Terrain(helper.getLevel());
        repeated.cave(40, Direction.NORTH, 7);
        repeated.cave(-24, Direction.EAST, 11);
        repeated.generate();
        helper.assertTrue(terrain.writes.equals(repeated.writes),
                "The same carved terrain and seed must produce the same room and decoration");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shortTunnelReachesDryCaveBelowZero(GameTestHelper helper) {
        Terrain terrain = new Terrain(helper.getLevel());
        terrain.cave(-24, Direction.EAST, 11);
        terrain.generate();
        helper.assertTrue(terrain.warper().equals(terrain.center.atY(-23)),
                "Portal rooms must be able to use suitable caves below Y=0");
        for (int distance = 6; distance <= 12; distance++) {
            BlockPos feet = terrain.center.atY(-23).east(distance);
            helper.assertTrue(terrain.state(feet).isAir() && terrain.state(feet.above()).isAir()
                            && !terrain.state(feet.below()).isAir(),
                    "The entire connection must have headroom and a supported floor at " + feet);
        }
        helper.assertTrue(terrain.state(terrain.center.atY(-23).east(10)).is(Blocks.CAVE_AIR),
                "A stone barrier between the doorway and cave must be excavated");
        helper.assertTrue(!terrain.writes.containsKey(terrain.center.atY(-23).east(11)),
                "The existing cave beyond the tunnel must be preserved");
        Terrain distant = new Terrain(helper.getLevel());
        distant.cave(-24, Direction.EAST, 15);
        distant.generate();
        helper.assertTrue(distant.warper().getY() == distant.originalHeight() + 1,
                "Distant caves must not trigger tunnels beyond the length limit");
        for (BlockState ground : List.of(Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.CLAY.defaultBlockState(),
                Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Blocks.CALCITE.defaultBlockState())) {
            Terrain caveFloor = new Terrain(helper.getLevel());
            caveFloor.cave(-24, Direction.EAST, 11);
            for (BlockPos pos : BlockPos.betweenClosed(caveFloor.center.offset(-6, -24, -6), caveFloor.center.offset(6, -24, 6))) {
                caveFloor.blocks.put(pos.immutable(), ground);
            }
            caveFloor.generate();
            helper.assertTrue(caveFloor.warper().getY() == -23,
                    "Natural cave ground must allow a room: " + ground);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unsafeConnectionsRetainOriginalPlacement(GameTestHelper helper) {
        for (BlockState obstruction : List.of(Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState(),
                Blocks.BEDROCK.defaultBlockState(), Blocks.CHEST.defaultBlockState())) {
            Terrain terrain = new Terrain(helper.getLevel());
            terrain.cave(-24, Direction.EAST, 11);
            BlockPos blocked = terrain.center.atY(-23).east(9);
            terrain.blocks.put(blocked, obstruction);
            terrain.generate();
            helper.assertTrue(terrain.warper().getY() == terrain.originalHeight() + 1,
                    "An unsafe tunnel must retain the original height: " + obstruction);
            helper.assertTrue(terrain.state(blocked).equals(obstruction),
                    "Rejected tunnels must preserve their obstruction");
        }
        Terrain floodedRoom = new Terrain(helper.getLevel());
        floodedRoom.cave(-24, Direction.EAST, 11);
        floodedRoom.blocks.put(floodedRoom.center.atY(-22).west(3), Blocks.WATER.defaultBlockState());
        floodedRoom.generate();
        helper.assertTrue(floodedRoom.warper().getY() == floodedRoom.originalHeight() + 1,
                "A dry doorway must not allow a flooded room");
        Terrain unsupported = new Terrain(helper.getLevel());
        unsupported.cave(-24, Direction.EAST, 11);
        for (BlockPos pos : BlockPos.betweenClosed(unsupported.center.offset(-6, -24, -6), unsupported.center.offset(6, -24, 6))) {
            unsupported.blocks.put(pos.immutable(), Blocks.AIR.defaultBlockState());
        }
        unsupported.generate();
        helper.assertTrue(unsupported.warper().getY() == unsupported.originalHeight() + 1,
                "A cave connection must not leave a room floating in a cavern");
        Terrain missingDoorstep = new Terrain(helper.getLevel());
        missingDoorstep.cave(-24, Direction.EAST, 7);
        missingDoorstep.blocks.put(missingDoorstep.center.atY(-24).east(6), Blocks.AIR.defaultBlockState());
        missingDoorstep.generate();
        helper.assertTrue(missingDoorstep.warper().getY() == missingDoorstep.originalHeight() + 1,
                "An otherwise supported room must not leave a hole in its doorway floor");
        Terrain surface = new Terrain(helper.getLevel());
        surface.cave(40, Direction.NORTH, 7);
        surface.surface = 46;
        surface.generate();
        helper.assertTrue(surface.warper().getY() == surface.originalHeight() + 1,
                "Rooms must retain rock cover instead of breaking through the surface");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void locatorAndCommandFindActualAndSavedLegacyHeights(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos site = site(level);
        var chunk = level.getChunk(site.x, site.z);
        for (BlockPos pos : new ArrayList<>(chunk.getBlockEntitiesPos())) {
            if (chunk.getBlockState(pos).is(WARPER.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        BlockPos entrance = new BlockPos(site.getMiddleBlockX(), -23, site.getMiddleBlockZ());
        level.setBlock(entrance, WARPER.get().defaultBlockState(), 2);
        helper.assertTrue(entrance.equals(OverworldDungeonGen.getNearestDungeon(level, entrance)),
                "The pearl must find an intact warper outside the old height range");
        List<Component> messages = new ArrayList<>();
        CommandSource output = new CommandSource() {

            @Override
            public void sendSystemMessage(Component message) {
                messages.add(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        var source = level.getServer().createCommandSourceStack().withSource(output).withLevel(level)
                .withPosition(Vec3.atCenterOf(entrance));
        helper.assertTrue(level.getServer().getCommands().performPrefixedCommand(source, "arienteworld findentrance") == 1
                        && messages.stream().anyMatch(message -> message.getString().contains(entrance.getX() + ", -22, " + entrance.getZ())),
                "The entrance command must report a landing above the actual warper");
        level.setBlock(entrance, Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(OverworldDungeonGen.findDungeon(level, site.x, site.z) == null,
                "A removed warper must not attract the locator");
        BlockPos legacy = entrance.atY(new Terrain(level).originalHeight() + 1);
        level.setBlock(legacy, WARPER.get().defaultBlockState(), 2);
        var saved = level.getBlockEntity(legacy).saveWithFullMetadata();
        chunk.removeBlockEntity(legacy);
        chunk.setBlockEntityNbt(saved);
        helper.assertTrue(legacy.equals(OverworldDungeonGen.findDungeon(level, site.x, site.z)),
                "Lookup must load saved legacy warpers without recomputing cave placement");
        helper.succeed();
    }

    private static ChunkPos site(ServerLevel level) {
        for (int x = 100; x < 10000; x++) {
            if (OverworldDungeonGen.isDungeonChunk(level, x, 100)
                    && !OverworldDungeonGen.isDungeonChunk(level, x + 1, 100)
                    && !OverworldDungeonGen.isDungeonChunk(level, x - 1, 100)
                    && !OverworldDungeonGen.isDungeonChunk(level, x, 99)
                    && !OverworldDungeonGen.isDungeonChunk(level, x, 101)) {
                return new ChunkPos(x, 100);
            }
        }
        throw new AssertionError("No seeded entrance fixture found");
    }

    // Use carved-terrain fixtures: the GameTest Overworld is flat and has no caves.
    // Reject every out-of-bounds read/write to enforce the feature's 3x3 region.
    private static class Terrain {

        private final ServerLevel level;
        private final BlockPos center;
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final Map<BlockPos, BlockState> writes = new HashMap<>();
        private int surface = 96;

        private Terrain(ServerLevel level) {
            this.level = level;
            ChunkPos chunk = site(level);
            center = new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
        }

        private void cave(int floor, Direction direction, int distance) {
            BlockPos start = center.atY(floor + 1).relative(direction, distance);
            BlockPos end = start.relative(direction, 3).above();
            for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
                blocks.put(pos.immutable(), Blocks.AIR.defaultBlockState());
            }
        }

        private BlockState state(BlockPos pos) {
            if (Math.abs((pos.getX() >> 4) - (center.getX() >> 4)) > 1
                    || Math.abs((pos.getZ() >> 4) - (center.getZ() >> 4)) > 1) {
                throw new AssertionError("Feature accessed a chunk outside its generation region: " + pos);
            }
            return blocks.getOrDefault(pos, pos.getY() < surface ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }

        private int originalHeight() {
            Random random = new Random(level.getSeed() + (center.getX() >> 4) * 23567813L + (center.getZ() >> 4) * 923568029L);
            random.nextFloat();
            return random.nextInt(25) + 8;
        }

        private void generate() {
            var chunk = new ProtoChunk(new ChunkPos(center), UpgradeData.EMPTY, level,
                    level.registryAccess().registryOrThrow(Registries.BIOME), null);
            WorldGenLevel world = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getLevel" -> level;
                        case "getSeed" -> level.getSeed();
                        case "getMinBuildHeight" -> -64;
                        case "getMaxBuildHeight" -> 320;
                        case "getHeight" -> surface;
                        case "getBlockState" -> state((BlockPos) args[0]);
                        case "isEmptyBlock" -> state((BlockPos) args[0]).isAir();
                        case "getChunk" -> chunk;
                        case "setBlock" -> {
                            BlockPos pos = ((BlockPos) args[0]).immutable();
                            state(pos);
                            writes.put(pos, (BlockState) args[1]);
                            blocks.put(pos, (BlockState) args[1]);
                            yield true;
                        }
                        default -> throw new AssertionError("Unexpected world query: " + method.getName());
                    });
            OverworldDungeonGen.generate(new Random(1234), center.getX() >> 4, center.getZ() >> 4, world);
        }

        private BlockPos warper() {
            return writes.entrySet().stream().filter(entry -> entry.getValue().is(WARPER.get()))
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
        }
    }
}
