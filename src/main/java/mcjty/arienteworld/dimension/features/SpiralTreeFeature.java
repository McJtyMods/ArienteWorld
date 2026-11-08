package mcjty.arienteworld.dimension.features;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A winding blue trunk supporting three offset, hollow rings of foliage. */
public class SpiralTreeFeature extends Feature<NoneFeatureConfiguration> {

    public SpiralTreeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        BlockPos origin = context.origin();
        if (!level.getBlockState(origin.below()).is(BlockTags.DIRT)) {
            return false;
        }
        RandomSource random = context.random();
        int height = 9 + random.nextInt(3);
        double phase = random.nextDouble() * Math.PI * 2;
        int twist = random.nextBoolean() ? 1 : -1;
        Set<BlockPos> logs = new LinkedHashSet<>();
        Set<BlockPos> leaves = new LinkedHashSet<>();
        BlockPos[] stem = new BlockPos[height + 1];
        BlockPos previous = origin;
        for (int y = 0; y <= height; y++) {
            double angle = phase + twist * y * Math.PI / 3;
            double radius = y < 2 ? y * .5 : 1.2;
            BlockPos pos = origin.offset((int) Math.round(Math.cos(angle) * radius), y,
                    (int) Math.round(Math.sin(angle) * radius));
            connect(logs, previous, pos);
            stem[y] = pos;
            previous = pos;
        }
        for (int tier = 0; tier < 3; tier++) {
            BlockPos anchor = stem[height - 6 + tier * 3];
            double angle = phase + twist * tier * Math.PI * 2 / 3;
            BlockPos center = anchor.offset((int) Math.round(Math.cos(angle) * 2), 0,
                    (int) Math.round(Math.sin(angle) * 2));
            int radius = tier == 2 ? 2 : 3;
            connect(logs, anchor.below(), center.below());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                connect(logs, center.below(), center.relative(direction, radius - 1).below());
            }
            addRing(leaves, center, radius, random);
        }
        leaves.removeAll(logs);
        // Validate every wooden segment before writing anything, preserving buildings and other trunks.
        for (BlockPos pos : logs) {
            if (level.isOutsideBuildHeight(pos) || !TreeFeature.validTreePos(level, pos)) {
                return false;
            }
        }
        for (BlockPos pos : leaves) {
            if (level.isOutsideBuildHeight(pos)) {
                return false;
            }
        }
        leaves.removeIf(pos -> !TreeFeature.validTreePos(level, pos));
        Map<BlockPos, Integer> distances = leafDistances(logs, leaves);
        var log = Registration.BLUELOG.get().defaultBlockState();
        var foliage = Registration.BLUELEAVES.get().defaultBlockState();
        for (BlockPos pos : logs) {
            level.setBlock(pos, log, 19);
        }
        for (BlockPos pos : leaves) {
            Integer distance = distances.get(pos);
            if (distance != null) {
                level.setBlock(pos, foliage.setValue(LeavesBlock.DISTANCE, distance), 19);
            }
        }
        return true;
    }

    private static void connect(Set<BlockPos> logs, BlockPos from, BlockPos to) {
        BlockPos current = from;
        logs.add(current);
        // Face-connected steps keep every bend and branch attached to the root.
        while (!current.equals(to)) {
            if (current.getY() != to.getY()) {
                current = current.offset(0, Integer.signum(to.getY() - current.getY()), 0);
            } else if (current.getX() != to.getX()) {
                current = current.offset(Integer.signum(to.getX() - current.getX()), 0, 0);
            } else {
                current = current.offset(0, 0, Integer.signum(to.getZ() - current.getZ()));
            }
            logs.add(current);
        }
    }

    private static void addRing(Set<BlockPos> leaves, BlockPos center, int radius, RandomSource random) {
        int inner = (radius - 1) * (radius - 1);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int distance = x * x + z * z;
                if (distance < inner || distance > radius * radius + 1) {
                    continue;
                }
                leaves.add(center.offset(x, 0, z));
                if (distance <= radius * radius) {
                    leaves.add(center.offset(x, 1, z));
                }
                if (distance >= radius * radius - 1 && random.nextInt(4) == 0) {
                    leaves.add(center.offset(x, -1, z));
                }
            }
        }
    }

    private static Map<BlockPos, Integer> leafDistances(Set<BlockPos> logs, Set<BlockPos> leaves) {
        Map<BlockPos, Integer> distances = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(logs);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            int nextDistance = distances.getOrDefault(pos, 0) + 1;
            if (nextDistance >= 7) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (leaves.contains(next) && !distances.containsKey(next)) {
                    distances.put(next, nextDistance);
                    queue.addLast(next);
                }
            }
        }
        return distances;
    }
}
