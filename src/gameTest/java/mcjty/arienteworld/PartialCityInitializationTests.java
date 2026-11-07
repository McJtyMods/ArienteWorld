package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.blocks.aicore.AICoreTile;
import mcjty.ariente.blocks.storagecube.StorageCubeTile;
import mcjty.ariente.items.KeyCardItem;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.entities.soldier.SoldierBehaviourType;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class PartialCityInitializationTests {

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void visibleCoreOutsideSimulationDistanceDoesNotStopWaves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City city = findCity(level, "city5_1", 50008);
        ChunkPos center = city.getCenter();
        ChunkPos wing = new ChunkPos(center.x - 3, center.z - 3);
        level.setChunkForced(wing.x, wing.z, true);
        level.getChunk(wing.x, wing.z);
        helper.runAfterDelay(4, () -> {
            level.getChunk(center.x, center.z);
            helper.assertTrue(!level.getChunkSource().isPositionTicking(center.toLong()),
                    "The core chunk must be present but outside block simulation");
            CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(center);
            FakePlayer observer = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "visible-core-test"));
            observer.setPos(wing.getMiddleBlockX(), CityTools.getCityHeight(level, city) + 1, wing.getMiddleBlockZ());
            ai.alertCity(observer);
            helper.runAfterDelay(12, () -> {
                try {
                    helper.assertTrue(!level.getEntitiesOfClass(SoldierEntity.class, new AABB(observer.blockPosition()).inflate(40),
                            soldier -> soldier.isReinforcementFor(center)).isEmpty(),
                            "A visible, non-ticking core must not suppress defense of active outer rooms");
                    helper.assertTrue(!level.getChunkSource().isPositionTicking(center.toLong()),
                            "Defending a loaded section must not force the core to tick");
                } finally {
                    observer.discard();
                    level.setChunkForced(wing.x, wing.z, false);
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void outerRoomsDefendThemselvesWithoutLoadingTheCore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City city = findCity(level, "city5_1", 48008);
        ChunkPos center = city.getCenter();
        ChunkPos wing = new ChunkPos(center.x - 3, center.z - 3);
        level.setChunkForced(wing.x, wing.z, true);
        level.getChunk(wing.x, wing.z);
        helper.runAfterDelay(4, () -> {
            CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(center);
            helper.assertTrue(countGuards(level, wing) > 0, "Outer rooms must initialize through the chunk lifecycle");
            helper.assertTrue(level.getChunkSource().getChunkNow(center.x, center.z) == null, "The core chunk must stay unloaded");
            FakePlayer observer = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "outer-room-test"));
            observer.setPos(wing.getMiddleBlockX(), CityTools.getCityHeight(level, city) + 1, wing.getMiddleBlockZ());
            ai.alertCity(observer);
            helper.runAfterDelay(12, () -> {
                try {
                    helper.assertTrue(!level.getEntitiesOfClass(SoldierEntity.class, new AABB(observer.blockPosition()).inflate(40),
                            soldier -> soldier.isReinforcementFor(center)).isEmpty(),
                            "The level lifecycle must sustain local reinforcements with no core chunk loaded: dead=" + ai.isDead(level)
                                    + ", difficulty=" + level.getDifficulty() + ", state=" + save(ai));
                    helper.assertTrue(level.getChunkSource().getChunkNow(center.x, center.z) == null,
                            "Reinforcement handling must not load the core chunk");
                } finally {
                    observer.discard();
                    level.setChunkForced(wing.x, wing.z, false);
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void cubeSectionsActivateAndPersistWithoutThePerimeter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City city = findCity(level, "city5_1", 42008);
        ChunkPos center = city.getCenter();
        ChunkPos wing = new ChunkPos(center.x - 3, center.z - 3);
        BlockPos corePos = new BlockPos(center.getMiddleBlockX(), CityTools.getCityHeight(level, city) + 27,
                center.getMiddleBlockZ());
        level.setChunkForced(center.x, center.z, true);
        level.setChunkForced(wing.x, wing.z, true);
        LevelChunk coreChunk = level.getChunk(center.x, center.z);
        AICoreTile core = (AICoreTile) coreChunk.getBlockEntity(corePos);
        CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(center);
        ai.tick(core);
        StorageCubeTile trophy = (StorageCubeTile) coreChunk.getBlockEntity(corePos.west());
        helper.assertTrue(trophy != null && trophy.isLocked() && trophy.hasGeneratedLoot(),
                "A loaded core section must fill and secure its trophy without waiting for the perimeter");
        helper.assertTrue(trophy.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new).extractItem(0, 1, false).isEmpty(),
                "The initialized trophy must deny automation");
        level.getChunk(wing.x, wing.z);
        helper.runAfterDelay(4, () -> {
            FakePlayer observer = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "partial-city"));
            try {
                CompoundTag state = save(ai);
                helper.assertTrue(Arrays.stream(state.getLongArray("initializedChunks")).anyMatch(value -> value == wing.toLong()),
                        "The chunk-load lifecycle must initialize a wing even without a core in that chunk");
                int guardCount = countGuards(level, wing);
                helper.assertTrue(guardCount > 0 && state.getList("soldierPositions", Tag.TAG_COMPOUND).size() > 0,
                        "Newly loaded room decks must deploy guards and discover reinforcement posts");
                helper.assertTrue(!allCubeChunksLoaded(level, center), "The fixture must retain unloaded perimeter chunks");
                observer.setPos(Vec3.atBottomCenterOf(corePos.above(5)));
                ai.alertCity(observer);
                ai.tick(core);
                helper.assertTrue(!level.getEntitiesOfClass(SoldierEntity.class, new AABB(corePos).inflate(160),
                        soldier -> soldier.isReinforcementFor(center)).isEmpty(), "Alert waves must run with only part of the city loaded");
                helper.assertTrue(!allCubeChunksLoaded(level, center), "AI work must not force the perimeter to load");

                ItemStack card = new ItemStack(Registration.KEY_CARD.get());
                KeyCardItem.addSecurityTag(card, ai.getStorageKeyId());
                trophy.acceptKeyCard(card);
                trophy.getItems().setStackInSlot(0, ItemStack.EMPTY);
                state = save(ai);
                CityAI restored = new CityAI(level, center);
                restored.readFromNBT(state);
                restored.initializeChunk(center);
                restored.initializeChunk(wing);
                helper.assertTrue(!trophy.isLocked() && trophy.getItems().getStackInSlot(0).isEmpty(),
                        "Reloading incremental city state must neither refill nor relock captured loot");
                helper.assertTrue(countGuards(level, wing) == guardCount, "Reloading city state must not duplicate authored guards");
                helper.assertTrue(restored.getStorageKeyId().equals(ai.getStorageKeyId()), "City keys survive reload");
            } finally {
                observer.discard();
                level.setChunkForced(center.x, center.z, false);
                level.setChunkForced(wing.x, wing.z, false);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void unloadedSecondCorePreventsEarlyStorageCard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City city = findCity(level, "city3_1", 46008);
        ChunkPos firstChunk = new ChunkPos(city.getCenter().x + 1, city.getCenter().z - 2);
        level.setChunkForced(firstChunk.x, firstChunk.z, true);
        LevelChunk loaded = level.getChunk(firstChunk.x, firstChunk.z);
        BlockPos first = loaded.getBlockEntitiesPos().stream()
                .filter(pos -> loaded.getBlockEntity(pos) instanceof AICoreTile).findFirst().orElseThrow();
        CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(city.getCenter());
        ai.initializeChunk(firstChunk);
        CompoundTag state = save(ai);
        ListTag pending = state.getList("pendingCores", Tag.TAG_COMPOUND);
        helper.assertTrue(pending.size() == 1, "The second authored core must be remembered before its chunk loads");
        BlockPos second = NbtUtils.readBlockPos(pending.getCompound(0));
        helper.assertTrue(level.getChunkSource().getChunkNow(second.getX() >> 4, second.getZ() >> 4) == null,
                "The second core's chunk must remain unloaded");
        String storageKey = ai.getStorageKeyId();
        level.setBlockAndUpdate(first, Blocks.AIR.defaultBlockState());
        helper.assertTrue(cards(level, first).isEmpty() && !ai.isDead(level), "Removing one core must not conquer an unexplored second core");
        ai.readFromNBT(save(ai));
        helper.assertTrue(ai.getStorageKeyId().equals(storageKey), "Partial-city security must survive save and reload");
        ChunkPos secondPos = new ChunkPos(second);
        level.setChunkForced(secondPos.x, secondPos.z, true);
        LevelChunk secondChunk = level.getChunkAt(second);
        ai.initializeChunk(secondChunk.getPos());
        helper.assertTrue(secondChunk.getBlockEntity(second) instanceof AICoreTile, "The pending core must match real generated equipment");
        level.setBlockAndUpdate(second, Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(4, () -> {
            try {
                List<ItemEntity> cards = cards(level, second);
                helper.assertTrue(cards.size() == 1 && KeyCardItem.getSecurityTags(cards.get(0).getItem()).contains(storageKey),
                        "Destroying the last core must award exactly one matching storage card: count=" + cards.size()
                                + ", state=" + save(ai));
                helper.assertTrue(ai.isDead(level) && save(ai).getList("pendingCores", Tag.TAG_COMPOUND).isEmpty(),
                        "Conquering the city clears its remaining-core state");
            } finally {
                level.setChunkForced(firstChunk.x, firstChunk.z, false);
                level.setChunkForced(secondPos.x, secondPos.z, false);
            }
            helper.succeed();
        });
    }

    private static City findCity(ServerLevel level, String plan, int z) {
        for (int x = 42008; x < 74008; x += 16) {
            City city = CityTools.getCity(level.getSeed(), new ChunkPos(x, z));
            if (city.getPlan().getName().equals(plan) && CityTools.isCityCenter(level.getSeed(), x, z)) {
                return city;
            }
        }
        throw new IllegalStateException("No fixture for " + plan);
    }

    private static CompoundTag save(CityAI ai) {
        CompoundTag state = new CompoundTag();
        ai.writeToNBT(state);
        return state;
    }

    private static int countGuards(ServerLevel level, ChunkPos chunk) {
        return level.getEntitiesOfClass(SoldierEntity.class,
                new AABB(chunk.getMinBlockX(), 0, chunk.getMinBlockZ(), chunk.getMaxBlockX() + 1, 256, chunk.getMaxBlockZ() + 1),
                soldier -> soldier.saveWithoutId(new CompoundTag()).getInt("behaviour") == SoldierBehaviourType.SOLDIER_GUARD.ordinal()).size();
    }

    private static boolean allCubeChunksLoaded(ServerLevel level, ChunkPos center) {
        for (int x = center.x - 4; x <= center.x + 4; x++) {
            for (int z = center.z - 4; z <= center.z + 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<ItemEntity> cards(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3), item -> item.getItem().is(Registration.KEY_CARD.get()));
    }
}
