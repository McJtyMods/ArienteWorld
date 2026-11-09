package mcjty.arienteworld.dimension.features;

import java.util.LinkedHashSet;
import java.util.Set;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;


/** A rooted cluster of uneven, tapering spires that lean away from one another. */
public class CrystalFormationFeature implements Feature {

    private static final double FORMATION_SCALE = .4;

    public static final TagKey<Block> SUBSTRATE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(ArienteWorld.MODID, "crystal_substrate"));

    public static final MapCodec<CrystalFormationFeature> CODEC = MapCodec.unit(CrystalFormationFeature::new);

    @Override
    public MapCodec<CrystalFormationFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel world, ChunkGenerator chunkGenerator, RandomSource randomSource, BlockPos originPos) {
        var level = world;
        BlockPos origin = originPos;
        if (!level.getBlockState(origin.below()).is(SUBSTRATE)) {
            return false;
        }
        RandomSource random = randomSource;
        BlockState crystal = (random.nextBoolean() ? Registration.NEGARITE_CRYSTAL : Registration.POSIRITE_CRYSTAL)
                .get().defaultBlockState();
        Set<BlockPos> blocks = new LinkedHashSet<>();
        addSpire(blocks, origin, 7 + random.nextInt(6), 2 + random.nextDouble(),
                random.nextInt(5) - 2, random.nextInt(5) - 2, random);
        int offshoots = 3 + random.nextInt(4);
        double phase = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < offshoots; i++) {
            double angle = phase + i * Math.PI * 2 / offshoots + random.nextDouble() * .7;
            double spread = 2 + random.nextDouble() * 3;
            BlockPos root = origin.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
            connect(blocks, origin, root);
            addSpire(blocks, root, 3 + random.nextInt(7), 1 + random.nextDouble(),
                    (int) Math.round(Math.cos(angle) * spread), (int) Math.round(Math.sin(angle) * spread), random);
        }
        blocks = scaleFormation(blocks, origin);
        // Ground the lowermost lobes on uneven marble without hanging roots over cliffs.
        for (BlockPos base : new LinkedHashSet<>(blocks)) {
            if (base.getY() != origin.getY()) {
                continue;
            }
            BlockPos support = base.below();
            int depth = 0;
            while (depth < 3 && canReplace(level, support)) {
                blocks.add(support);
                support = support.below();
                depth++;
            }
            if (!level.getBlockState(support).is(SUBSTRATE)) {
                return false;
            }
        }
        // Validate the entire formation before writing, preserving water, terrain and structures.
        for (BlockPos pos : blocks) {
            if (level.isOutsideBuildHeight(pos) || !canReplace(level, pos)) {
                return false;
            }
        }
        for (BlockPos pos : blocks) {
            level.setBlock(pos, crystal, 2);
        }
        return true;
    }

    private static Set<BlockPos> scaleFormation(Set<BlockPos> blocks, BlockPos origin) {
        Set<BlockPos> scaled = new LinkedHashSet<>();
        for (BlockPos pos : blocks) {
            BlockPos offset = pos.subtract(origin);
            // Compress every connected segment together so rounding cannot detach an offshoot.
            scaled.add(origin.offset((int) Math.round(offset.getX() * FORMATION_SCALE),
                    (int) Math.round(offset.getY() * FORMATION_SCALE),
                    (int) Math.round(offset.getZ() * FORMATION_SCALE)));
        }
        return scaled;
    }

    private static boolean canReplace(LevelSimulatedReader level, BlockPos pos) {
        return level.isStateAtPosition(pos, state -> (state.isAir() || state.canBeReplaced())
                && state.getFluidState().isEmpty());
    }

    private static void addSpire(Set<BlockPos> blocks, BlockPos root, int height, double radius,
                                 int leanX, int leanZ, RandomSource random) {
        BlockPos previous = root;
        double widthX = .8 + random.nextDouble() * .4;
        double widthZ = .8 + random.nextDouble() * .4;
        for (int y = 0; y <= height; y++) {
            double progress = (double) y / height;
            BlockPos center = root.offset((int) Math.round(leanX * progress), y,
                    (int) Math.round(leanZ * progress));
            connect(blocks, previous, center);
            previous = center;
            double width = radius * (1 - progress) + random.nextDouble() * .3;
            int reach = (int) Math.ceil(width * 1.2);
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    if (Math.abs(x) / widthX + Math.abs(z) / widthZ <= width) {
                        blocks.add(center.offset(x, 0, z));
                    }
                }
            }
        }
    }

    private static void connect(Set<BlockPos> blocks, BlockPos from, BlockPos to) {
        BlockPos current = from;
        blocks.add(current);
        while (!current.equals(to)) {
            if (current.getY() != to.getY()) {
                current = current.offset(0, Integer.signum(to.getY() - current.getY()), 0);
            } else if (current.getX() != to.getX()) {
                current = current.offset(Integer.signum(to.getX() - current.getX()), 0, 0);
            } else {
                current = current.offset(0, 0, Integer.signum(to.getZ() - current.getZ()));
            }
            blocks.add(current);
        }
    }
}
