package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class SwampGenerationTests {

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void shallowPoolsAndDenseUnderstory(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        var swamp = biomes.get(new ResourceLocation(ArienteWorld.MODID, "ariente_swamp"));
        var plains = biomes.get(new ResourceLocation(ArienteWorld.MODID, "ariente_plains"));
        helper.assertTrue(swamp != null && swamp.getFogColor() != plains.getFogColor(),
                "The swamp must register its own atmospheric fog color");
        int fog = swamp.getFogColor();
        helper.assertTrue((fog >> 16 & 255) > (fog >> 8 & 255) && (fog & 255) > (fog >> 16 & 255),
                "Swamp fog must have a blue-purple tint");
        ArienteBiomeLayout layout = new ArienteBiomeLayout(level.getSeed());
        int checked = 0;
        int water = 0;
        int dryLand = 0;
        int foliage = 0;
        int flowers = 0;
        int tallGrass = 0;
        int logs = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 2048; x < 2560 && checked < 12; x++) {
            for (int z = 2048; z < 2560 && checked < 12; z++) {
                if (!isSwampChunk(layout, x, z) || CityTools.isDungeonChunk(level.getSeed(), x, z)
                        || PortalGenerator.isPortalChunk(x, z)) {
                    continue;
                }
                var chunk = level.getChunk(x, z);
                checked++;
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        pos.set(x * 16 + dx, 62, z * 16 + dz);
                        if (chunk.getBlockState(pos).is(Blocks.WATER)) {
                            water++;
                            helper.assertTrue(!chunk.getBlockState(pos.below(2)).is(Blocks.WATER)
                                            && chunk.getBlockState(pos.below(2)).isSolid(),
                                    "Swamp pools must be at most two blocks deep: " + pos);
                        }
                        for (int y = 63; y < 128; y++) {
                            var state = chunk.getBlockState(pos.setY(y));
                            if (y <= 64 && state.is(Blocks.GRASS_BLOCK)) {
                                dryLand++;
                            }
                            if (state.is(Registration.BLACKBUSH.get()) || state.is(Registration.DARKGRASS.get())) {
                                foliage++;
                            } else if (state.is(Registration.TALLDARKGRASS.get())) {
                                boolean lower = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;
                                var other = chunk.getBlockState(lower ? pos.above() : pos.below());
                                helper.assertTrue(other.is(Registration.TALLDARKGRASS.get())
                                                && other.getValue(DoublePlantBlock.HALF) != state.getValue(DoublePlantBlock.HALF),
                                        "Tall dark grass must generate both halves: " + pos);
                                if (lower) {
                                    tallGrass++;
                                    helper.assertTrue(level.getBiome(pos).unwrapKey().orElseThrow().location().getPath().equals("ariente_swamp"),
                                            "Tall dark grass must only generate inside the swamp: " + pos);
                                }
                            } else if (state.is(Registration.SMALLFLOWER.get())) {
                                flowers++;
                            } else if (state.is(Registration.BLUELOG.get()) || state.is(Registration.GLOWLOG.get())) {
                                logs++;
                            }
                        }
                    }
                }
            }
        }
        helper.assertTrue(checked == 12 && water > 50 && dryLand > 50,
                "Natural swamp terrain must mix shallow pools and dry ground: " + checked + "/" + water + "/" + dryLand);
        helper.assertTrue(foliage > 50 && foliage > flowers * 3 && logs > 0 && tallGrass > 0,
                "Swamps must have trees and plentiful foliage dominated by bushes and dark grass: "
                        + logs + "/" + foliage + "/" + flowers + "/" + tallGrass);
        ArienteWorld.setup.getLogger().info("SWAMP GENERATION PASSED: {} chunks, {} pool columns, {} dry columns, {} foliage, {} flowers, {} logs, {} tall grass",
                checked, water, dryLand, foliage, flowers, logs, tallGrass);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tallDarkGrassPlacesAndDropsAsOnePlant(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = Registration.TALLDARKGRASS.get();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "tall-grass-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block, 4));
        for (int i = 0; i < 3; i++) {
            BlockPos pos = helper.absolutePos(new BlockPos(2 + i * 4, 4, 2));
            level.setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false));
            helper.assertTrue(player.getMainHandItem().useOn(context).consumesAction(),
                    "Collected tall dark grass must be replantable outside the swamp");
            helper.assertTrue(level.getBlockState(pos).is(block)
                            && level.getBlockState(pos).getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER
                            && level.getBlockState(pos.above()).is(block)
                            && level.getBlockState(pos.above()).getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER,
                    "Planting the item must create both halves");
            if (i == 2) {
                level.destroyBlock(pos.below(), false);
            } else {
                player.gameMode.destroyBlock(i == 0 ? pos : pos.above());
            }
            helper.assertTrue(level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above()),
                    "Removing either half or its supporting soil must clear the whole plant");
            int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5)).stream()
                    .map(ItemEntity::getItem).filter(stack -> stack.is(block.asItem())).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(drops == 1, "A broken tall plant must drop exactly one item: " + drops);
        }
        BlockPos blocked = helper.absolutePos(new BlockPos(14, 4, 2));
        level.setBlockAndUpdate(blocked.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(blocked, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(blocked.above(), Blocks.STONE.defaultBlockState());
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(blocked.below()), Direction.UP, blocked.below(), false));
        helper.assertTrue(!player.getMainHandItem().useOn(context).consumesAction()
                        && level.isEmptyBlock(blocked) && level.getBlockState(blocked.above()).is(Blocks.STONE),
                "A tall plant must not place without headroom or replace an occupied upper block");
        helper.succeed();
    }

    private static boolean isSwampChunk(ArienteBiomeLayout layout, int chunkX, int chunkZ) {
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                if (layout.get(chunkX * 4 + x, chunkZ * 4 + z) != BiomeProfile.SWAMP) {
                    return false;
                }
            }
        }
        return true;
    }
}
