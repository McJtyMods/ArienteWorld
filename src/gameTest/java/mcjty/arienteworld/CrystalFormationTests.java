package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.api.MarbleColor;
import mcjty.ariente.blocks.decorative.DecorativeBlockModule;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.PortalGenerator;
import mcjty.arienteworld.dimension.terrain.ArienteBiomeLayout;
import mcjty.arienteworld.dimension.terrain.BiomeProfile;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static mcjty.ariente.setup.Registration.DUST_NEGARITE;
import static mcjty.ariente.setup.Registration.DUST_POSIRITE;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class CrystalFormationTests {

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void formationsAreConnectedVariedAndPreserveObstructions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(91200, 150, 91200);
        var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(new ResourceLocation(ArienteWorld.MODID, "crystal_formation"));
        Set<Block> minerals = new HashSet<>();
        Map<BlockPos, BlockState> first = null;
        for (int seed = 0; seed < 16; seed++) {
            clear(level, origin);
            helper.assertTrue(feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed * 81235131L), origin),
                    "A crystal formation must grow on clear marble");
            Map<BlockPos, BlockState> shape = read(level, origin);
            Set<BlockPos> connected = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(BlockPos.ZERO);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                if (!shape.containsKey(pos) || !connected.add(pos)) {
                    continue;
                }
                for (Direction direction : Direction.values()) {
                    queue.add(pos.relative(direction));
                }
            }
            helper.assertTrue(connected.equals(shape.keySet()) && shape.size() > 3,
                    "All crystal blocks must connect to the root: " + connected.size() + "/" + shape.size());
            int height = shape.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow() + 1;
            helper.assertTrue(height >= 4 && height <= 6
                            && shape.keySet().stream().allMatch(pos -> Math.abs(pos.getX()) <= 3 && Math.abs(pos.getZ()) <= 3),
                    "Scaled formations must stay compact, with four to six blocks of height: " + height);
            Set<Block> types = new HashSet<>();
            shape.values().forEach(state -> types.add(state.getBlock()));
            helper.assertTrue(types.size() == 1, "Every formation must contain a single mineral");
            minerals.addAll(types);
            if (seed == 0) {
                first = shape;
            } else {
                helper.assertTrue(!first.equals(shape), "Placement seeds must vary the formation");
            }
        }
        helper.assertTrue(minerals.size() == 2, "Both mineral formations must occur");
        clear(level, origin);
        feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(0), origin);
        helper.assertTrue(first.equals(read(level, origin)), "Formation shape and mineral must be deterministic");
        clear(level, origin);
        level.setBlock(origin.east().below(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(origin.east().below(2), Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(0), origin)
                        && isCrystal(level.getBlockState(origin.east().below(2))),
                "Roots must extend down to marble on uneven terrain");
        clear(level, origin);
        for (int depth = 1; depth <= 4; depth++) {
            level.setBlock(origin.east().below(depth), Blocks.AIR.defaultBlockState(), 2);
        }
        helper.assertTrue(!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(0), origin)
                        && read(level, origin).isEmpty(), "Cliff edges must not leave unsupported formations");
        for (Block obstruction : new Block[]{Blocks.STONE, Blocks.WATER, Blocks.CHEST}) {
            clear(level, origin);
            level.setBlock(origin.above(), obstruction.defaultBlockState(), 2);
            helper.assertTrue(!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(0), origin)
                            && read(level, origin).isEmpty() && level.getBlockState(origin.above()).is(obstruction),
                    "Obstructions must be preserved without leaving partial formations");
        }
        clear(level, origin);
        level.setBlock(origin.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        helper.assertTrue(!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(0), origin),
                "Formations must require exposed marble");
        BlockPos high = origin.atY(level.getMaxBuildHeight() - 3);
        level.setBlock(high.below(), DecorativeBlockModule.MARBLE.get(MarbleColor.GRAY).get().defaultBlockState(), 2);
        helper.assertTrue(!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(0), high)
                        && level.isEmptyBlock(high), "The world ceiling must reject a formation before any blocks are written");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void miningYieldsSmallMatchingDropsAndRings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "crystal-miner"));
        player.setGameMode(GameType.SURVIVAL);
        for (Block block : new Block[]{Registration.NEGARITE_CRYSTAL.get(), Registration.POSIRITE_CRYSTAL.get()}) {
            var state = block.defaultBlockState();
            var dust = block == Registration.NEGARITE_CRYSTAL.get() ? DUST_NEGARITE.get() : DUST_POSIRITE.get();
            helper.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state)
                            && !new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state)
                            && state.requiresCorrectToolForDrops(), "Crystals must require at least a stone pickaxe");
            helper.assertTrue(state.getSoundType(level, pos, player).getBreakSound() == SoundEvents.EXPERIENCE_ORB_PICKUP,
                    "Mining crystals must use the ringing experience-orb sound");
            for (int tool = 0; tool < 3; tool++) {
                level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).forEach(ItemEntity::discard);
                ItemStack pick = new ItemStack(tool == 0 ? Items.WOODEN_PICKAXE : Items.STONE_PICKAXE);
                if (tool == 2) {
                    pick.enchant(Enchantments.SILK_TOUCH, 1);
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, pick);
                level.setBlockAndUpdate(pos, state);
                helper.assertTrue(player.gameMode.destroyBlock(pos), "The player must be able to mine the crystal");
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
                helper.assertTrue(tool == 0 ? drops.isEmpty() : drops.size() == 1
                                && drops.get(0).getItem().getCount() == 1
                                && drops.get(0).getItem().is(tool == 2 ? block.asItem() : dust),
                        "Mining must yield no loot with wood, one matching dust normally, or the crystal with Silk Touch");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1800)
    public static void crystalsGenerateNaturallyInRoughBiome(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        var placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                .getHolderOrThrow(ResourceKey.create(Registries.PLACED_FEATURE,
                        new ResourceLocation(ArienteWorld.MODID, "crystal_formations")));
        for (var entry : level.registryAccess().registryOrThrow(Registries.BIOME).entrySet()) {
            if (entry.getKey().location().getNamespace().equals(ArienteWorld.MODID)) {
                boolean rough = entry.getKey().location().getPath().equals("ariente_rough");
                helper.assertTrue(entry.getValue().getGenerationSettings().hasFeature(placed.value()) == rough,
                        "Natural crystal placement must belong only to the rough biome");
                if (rough) {
                    helper.assertTrue(entry.getValue().getGenerationSettings().features()
                                    .get(GenerationStep.Decoration.SURFACE_STRUCTURES.ordinal()).contains(placed),
                            "Crystals must be surface formations");
                }
            }
        }
        var layout = new ArienteBiomeLayout(level.getSeed());
        int checked = 0;
        int crystals = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 4096; x < 4352 && checked < 48; x++) {
            for (int z = 4096; z < 4352 && checked < 48; z++) {
                if (layout.get(x * 4, z * 4) != BiomeProfile.ROUGH
                        || layout.get(x * 4 + 3, z * 4 + 3) != BiomeProfile.ROUGH
                        || CityTools.isDungeonChunk(level.getSeed(), x, z) || PortalGenerator.isPortalChunk(x, z)) {
                    continue;
                }
                var chunk = level.getChunk(x, z);
                checked++;
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        for (int y = 0; y < 256; y++) {
                            var state = chunk.getBlockState(pos.set(x * 16 + dx, y, z * 16 + dz));
                            if (isCrystal(state)) {
                                crystals++;
                            }
                        }
                    }
                }
            }
        }
        helper.assertTrue(checked == 48 && crystals > 30, "Fresh rough terrain must contain crystal formations: " + checked + "/" + crystals);
        ArienteWorld.setup.getLogger().info("CRYSTAL GENERATION PASSED: {} crystal blocks in {} rough chunks", crystals, checked);
        helper.succeed();
    }

    private static boolean isCrystal(BlockState state) {
        return state.is(Registration.NEGARITE_CRYSTAL.get()) || state.is(Registration.POSIRITE_CRYSTAL.get());
    }

    private static Map<BlockPos, BlockState> read(ServerLevel level, BlockPos origin) {
        Map<BlockPos, BlockState> shape = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -3, -8), origin.offset(8, 16, 8))) {
            var state = level.getBlockState(pos);
            if (isCrystal(state)) {
                shape.put(pos.subtract(origin), state);
            }
        }
        return shape;
    }

    private static void clear(ServerLevel level, BlockPos origin) {
        var marble = DecorativeBlockModule.MARBLE.get(MarbleColor.GRAY).get().defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -4, -8), origin.offset(8, 16, 8))) {
            level.setBlock(pos, pos.getY() < origin.getY() ? marble : Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
