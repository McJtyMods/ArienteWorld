package mcjty.arienteworld;

import static mcjty.arienteworld.setup.Registration.ENTITY_SOLDIER;
import static mcjty.arienteworld.setup.Registration.ENTITY_MASTER_SOLDIER;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.blocks.utility.WarperTile;
import mcjty.ariente.config.UtilityConfiguration;
import mcjty.ariente.entities.EntityArientePearl;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.config.WorldgenConfiguration;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.PortalGenerator;
import mcjty.arienteworld.dimension.terrain.ArienteBiomeLayout;
import mcjty.arienteworld.dimension.terrain.BiomeProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Random;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class PopulationTests {

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void forestOresAndSpawnTables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        ArienteBiomeLayout layout = new ArienteBiomeLayout(level.getSeed());
        int origin = 3500 + (int) (level.getGameTime() % 100000);
        int checked = 0;
        int trees = 0;
        int plants = 0;
        int ores = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = origin; x < origin + 256 && checked < 8; x++) {
            for (int z = origin; z < origin + 256 && checked < 8; z++) {
                if (layout.get(x * 4 + 2, z * 4 + 2) != BiomeProfile.FOREST
                        || CityTools.isDungeonChunk(level.getSeed(), x, z) || PortalGenerator.isPortalChunk(x, z)) {
                    continue;
                }
                var chunk = level.getChunk(x, z);
                checked++;
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        for (int y = 2; y < 220; y++) {
                            var block = chunk.getBlockState(pos.set(x * 16 + dx, y, z * 16 + dz)).getBlock();
                            String id = ForgeRegistries.BLOCKS.getKey(block).toString();
                            if (id.equals("arienteworld:bluelog") || id.equals("arienteworld:glowlog")) {
                                trees++;
                            }
                            if (id.equals("arienteworld:small_flower") || id.equals("arienteworld:dark_grass") || id.equals("arienteworld:black_bush")) {
                                plants++;
                            }
                            if (id.startsWith("ariente:") && (id.endsWith("ore") || id.endsWith(":negarite") || id.endsWith(":posirite"))) {
                                ores++;
                            }
                        }
                    }
                }
            }
        }
        helper.assertTrue(checked == 8 && trees > 0 && plants > 0 && ores > 0,
                "Natural forest chunks must contain trees, plants and ores: " + checked + "/" + trees + "/" + plants + "/" + ores);
        helper.assertTrue(ENTITY_SOLDIER.get().getCategory() == MobCategory.MONSTER
                        && ENTITY_MASTER_SOLDIER.get().getCategory() == MobCategory.MONSTER,
                "City inhabitants must participate in hostile spawning and mob caps");
        var cityBiome = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
                .filter(holder -> holder.unwrapKey().orElseThrow().location().getPath().equals("ariente_city")).findFirst().orElseThrow();
        helper.assertTrue(cityBiome.value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream()
                        .anyMatch(entry -> entry.type == ENTITY_SOLDIER.get()), "City biomes must register their natural soldier spawn table");
        ArienteWorld.setup.getLogger().info("Population check: {} logs, {} plants, {} ore blocks", trees, plants, ores);
        verifyTreesRespectCityFloors(helper, level, new BlockPos(origin * 16 + 8, 180, origin * 16 + 8));
        helper.succeed();
    }

    private static void verifyTreesRespectCityFloors(GameTestHelper helper, ServerLevel level, BlockPos origin) {
        var marble = LegacyBlockStates.read("ariente:marble_smooth@color=white");
        for (int x = -3; x <= 4; x++) {
            for (int z = -3; z <= 4; z++) {
                level.setBlock(origin.offset(x, -1, z), marble, 2);
                for (int y = 0; y < 40; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        var features = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (String name : List.of("blue_tree", "blue_tree_tall", "dark_tree", "dark_tree_tall", "glow_tree", "variant_tree", "huge_blue_tree", "spiral_tree")) {
            var tree = features.get(new ResourceLocation(ArienteWorld.MODID, name + "_shape"));
            helper.assertTrue(!tree.place(level, level.getChunkSource().getGenerator(), RandomSource.create(123), origin),
                    name + " must not grow on an authored city floor");
            helper.assertTrue(level.getBlockState(origin.below()).equals(marble), "Tree roots must not replace marble with dirt");
        }
        // A giant trunk also changes the soil under its other three root blocks.
        level.setBlock(origin.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        var huge = features.get(new ResourceLocation(ArienteWorld.MODID, "huge_blue_tree_shape"));
        helper.assertTrue(!huge.place(level, level.getChunkSource().getGenerator(), RandomSource.create(123), origin),
                "Giant tree roots must respect adjacent city floors even when their origin is grass");
        helper.assertTrue(level.getBlockState(origin.below().east()).equals(marble), "Adjacent roots must preserve marble");
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void entrancesAndPortalRoundTrip(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        BlockPos entrance = null;
        for (int x = 2000; x < 2200 && entrance == null; x++) {
            for (int z = 2000; z < 2200; z++) {
                Random random = new Random(overworld.getSeed() + x * 198491317L + z * 776531419L);
                random.nextFloat();
                if (random.nextFloat() < WorldgenConfiguration.OVERWORLD_DUNGEON_CHANCE.get()) {
                    Random height = new Random(overworld.getSeed() + x * 23567813L + z * 923568029L);
                    height.nextFloat();
                    BlockPos candidate = new BlockPos(x * 16 + 8, height.nextInt(25) + 9, z * 16 + 8);
                    overworld.getChunk(x, z);
                    if (overworld.getBlockEntity(candidate) instanceof WarperTile) {
                        entrance = candidate;
                        break;
                    }
                }
            }
        }
        helper.assertTrue(entrance != null, "Overworld biome modifier must generate entrance rooms with warpers");
        WarperTile sourceTile = (WarperTile) overworld.getBlockEntity(entrance);
        sourceTile.setCharges(3);
        WarperTile display = new WarperTile(entrance, sourceTile.getBlockState());
        display.handleUpdateTag(sourceTile.getUpdateTag());
        helper.assertTrue(display.getCharges() == 3, "Entrance charge count must reach the client hologram");
        sourceTile.setCharges(UtilityConfiguration.WARPER_MAX_CHARGES.get());
        ServerLevel ariente = overworld.getServer().getLevel(DimensionRegister.DIMENSION);
        BlockPos arrival = PortalGenerator.getArrival(ariente, entrance);
        BlockPos returnWarper = new BlockPos((arrival.getX() & ~15) + 7, arrival.getY() - 1, (arrival.getZ() & ~15) + 7);
        helper.assertTrue(ariente.getBlockEntity(returnWarper) instanceof WarperTile, "Arrival building must contain a return warper");
        helper.assertTrue(ariente.isEmptyBlock(arrival) && ariente.isEmptyBlock(arrival.above()), "Portal landing must have headroom");
        ServerPlayer player = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "portal-test")) {

            @Override
            public void teleportTo(double x, double y, double z) {
                // Forge's fake connection drops teleport packets; apply their position locally.
                moveTo(x, y, z);
            }
        };
        overworld.addFreshEntity(player);
        try {
            helper.assertTrue(ArienteWorld.arienteWorldImplementation.getNearestDungeon(overworld, entrance).equals(entrance),
                    "Pearl locator must target the nearby intact overworld entrance");
            helper.assertTrue(ArienteWorld.arienteWorldImplementation.getNearestDungeon(ariente, arrival) == null,
                    "Overworld entrance lookup must not return Ariente city coordinates");
            BlockPos throwPos = overworld.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, entrance.east(16)).above(3);
            player.moveTo(throwPos.getX() + .5, throwPos.getY(), throwPos.getZ() + .5);
            player.setXRot(-90);
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.ARIENTE_PEARL.get(), 2));
            AtomicReference<EntityArientePearl> thrown = new AtomicReference<>();
            // Fresh distant chunks need another tick before entity queries can see them.
            Consumer<EntityJoinLevelEvent> onJoin = event -> {
                if (event.getLevel() == overworld && event.getEntity() instanceof EntityArientePearl pearl) {
                    thrown.set(pearl);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(onJoin);
            try {
                Registration.ARIENTE_PEARL.get().use(overworld, player, InteractionHand.MAIN_HAND);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(onJoin);
            }
            EntityArientePearl pearl = thrown.get();
            helper.assertTrue(pearl != null, "Using the pearl must spawn its locator entity; remaining items: " + player.getMainHandItem().getCount());
            helper.assertTrue(pearl instanceof ItemSupplier && ((ItemSupplier) pearl).getItem().is(Registration.ARIENTE_PEARL.get()),
                    "Pearl entity must satisfy the thrown-item renderer's ItemSupplier contract");
            helper.assertTrue(player.getMainHandItem().getCount() == 1, "Throwing in survival must consume one pearl");
            var start = pearl.position();
            for (int i = 0; i < 20; i++) {
                pearl.tick();
            }
            helper.assertTrue((pearl.getX() - start.x) * (entrance.getX() - start.x)
                    + (pearl.getZ() - start.z) * (entrance.getZ() - start.z) > 0,
                    "Pearl must move toward the overworld entrance");
            pearl.discard();
            player.teleportTo(entrance.getX() + .5, entrance.getY() + 1, entrance.getZ() + .5);
            ((WarperTile) overworld.getBlockEntity(entrance)).warp(player);
            helper.assertTrue(player.level().dimension().equals(DimensionRegister.DIMENSION), "Entrance must teleport into Ariente");
            ((WarperTile) ariente.getBlockEntity(returnWarper)).warp(player);
            helper.assertTrue(player.level().dimension().equals(Level.OVERWORLD) && player.blockPosition().distSqr(entrance) < 5,
                    "Return warper must reach the source entrance without a sleeping position: " + player.position() + " expected " + entrance);
            // Missing entrances and beds must still have a safe world-spawn fallback.
            ((WarperTile) overworld.getBlockEntity(entrance)).warp(player);
            overworld.setBlock(entrance, Blocks.AIR.defaultBlockState(), 3);
            BlockPos remainingEntrance = ArienteWorld.arienteWorldImplementation.getNearestDungeon(overworld, entrance);
            helper.assertTrue(remainingEntrance == null || !remainingEntrance.equals(entrance), "Broken entrance warpers must not attract pearls");
            ((WarperTile) ariente.getBlockEntity(returnWarper)).warp(player);
            helper.assertTrue(player.level().dimension().equals(Level.OVERWORLD), "Missing entrance and bed must allow a return to world spawn");
            overworld.setBlock(entrance, Registration.WARPER.get().defaultBlockState(), 3);
        } finally {
            player.discard();
        }
        BlockPos oldSource = entrance.offset(65536, 0, (int) (ariente.getGameTime() % 10000) * 256);
        BlockPos oldLanding = PortalGenerator.getArrival(ariente, oldSource);
        BlockPos oldWarper = new BlockPos((oldLanding.getX() & ~15) + 7, oldLanding.getY() - 1, (oldLanding.getZ() & ~15) + 7);
        ariente.setBlock(oldWarper, Blocks.AIR.defaultBlockState(), 3);
        BlockPos replacement = PortalGenerator.getArrival(ariente, oldSource);
        helper.assertTrue(replacement.getY() > oldLanding.getY(), "Missing arrival portals must be rebuilt above existing terrain/buildings");
        helper.assertTrue(PortalGenerator.getArrival(ariente, oldSource).equals(replacement), "Repeated arrivals must reuse the replacement portal");
        helper.succeed();
    }
}
