package mcjty.arienteworld;

import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class SpiralTreeTests {

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void connectedSpiralsKeepLeavesAndRespectObstructions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(90784, 150, 90784);
        var feature = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                .get(new ResourceLocation(ArienteWorld.MODID, "spiral_tree_shape"));
        Map<BlockPos, BlockState> expected = grow(helper, level, feature, origin, 1234);
        Set<BlockPos> logs = new HashSet<>();
        int leaves = 0;
        for (var entry : expected.entrySet()) {
            var state = entry.getValue();
            if (state.is(Registration.BLUELOG.get())) {
                logs.add(entry.getKey());
            } else {
                leaves++;
                helper.assertTrue(state.getValue(LeavesBlock.DISTANCE) < 7 && !state.getValue(LeavesBlock.PERSISTENT),
                        "Generated blue leaves must have normal log support and remain naturally decayable");
            }
        }
        Set<BlockPos> connected = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(BlockPos.ZERO);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!logs.contains(pos) || !connected.add(pos)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                queue.add(pos.relative(direction));
            }
        }
        helper.assertTrue(connected.equals(logs) && logs.size() > 30 && leaves > 100,
                "The spiral stem and all crown supports must connect to the ground: " + connected.size() + "/" + logs.size() + "/" + leaves);
        helper.assertTrue(!expected.equals(grow(helper, level, feature, origin, 5678)),
                "Spiral trees must vary with their placement seed");
        helper.assertTrue(expected.equals(grow(helper, level, feature, origin, 1234)),
                "Tree shape and leaf distances must be independent of previous generation calls");
        clear(level, origin);
        level.setBlock(origin.above(), Blocks.STONE.defaultBlockState(), 2);
        helper.assertTrue(!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(1234), origin)
                        && level.isEmptyBlock(origin) && level.getBlockState(origin.above()).is(Blocks.STONE)
                        && level.getBlockState(origin.below()).is(Blocks.GRASS_BLOCK),
                "A blocked trunk must fail without leaving a partial tree or changing its soil");
        BlockPos high = origin.atY(level.getMaxBuildHeight() - 6);
        level.setBlock(high.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        helper.assertTrue(!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(1234), high)
                        && level.isEmptyBlock(high), "A tree must fail cleanly when its rings exceed the world ceiling");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void sharedTreeMixUsesAboutHalfSpirals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(90848, 150, 90848);
        var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(new ResourceLocation(ArienteWorld.MODID, "trees"));
        RandomSource seeds = RandomSource.create(891725);
        int spirals = 0;
        for (int i = 0; i < 64; i++) {
            clear(level, origin);
            helper.assertTrue(feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seeds.nextLong()), origin),
                    "Every selected tree must grow on clear, suitable soil");
            // Original small trees have a straight lower trunk; original giant trees have four roots.
            boolean giant = level.getBlockState(origin.east()).is(Registration.BLUELOG.get());
            if (!giant) {
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 2, -1), origin.offset(1, 2, 1))) {
                    if (!pos.equals(origin.above(2)) && level.getBlockState(pos).is(Registration.BLUELOG.get())) {
                        spirals++;
                        break;
                    }
                }
            }
        }
        helper.assertTrue(spirals >= 20 && spirals <= 44,
                "The shared selector must retain both old trees and roughly half spiral trees: " + spirals + "/64");
        ArienteWorld.setup.getLogger().info("SPIRAL TREE MIX PASSED: {} spiral trees and {} original trees", spirals, 64 - spirals);
        helper.succeed();
    }

    private static Map<BlockPos, BlockState> grow(GameTestHelper helper, ServerLevel level, PlacedFeature feature,
                                                 BlockPos origin, long seed) {
        clear(level, origin);
        helper.assertTrue(feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), origin),
                "A spiral tree must grow on clear grass");
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, 0, -8), origin.offset(8, 40, 8))) {
            var state = level.getBlockState(pos);
            if (state.is(Registration.BLUELOG.get()) || state.is(Registration.BLUELEAVES.get())) {
                blocks.put(pos.subtract(origin), state);
            }
        }
        return blocks;
    }

    private static void clear(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -1, -8), origin.offset(8, 40, 8))) {
            level.setBlock(pos, pos.getY() < origin.getY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
