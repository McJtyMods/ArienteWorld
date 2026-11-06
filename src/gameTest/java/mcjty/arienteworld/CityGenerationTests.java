package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.Ariente;
import mcjty.ariente.blocks.storagecube.StorageCubeTile;
import mcjty.arienteworld.entities.drone.EntityAIScanForPlayer;
import mcjty.ariente.blocks.utility.StorageTile;
import mcjty.ariente.items.BlueprintItem;
import mcjty.ariente.items.KeyCardItem;
import mcjty.ariente.recipes.BlueprintRecipeRegistry;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import mcjty.arienteworld.entities.drone.SentinelDroneEntity;
import mcjty.arienteworld.entities.soldier.SoldierBehaviourType;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import mcjty.ariente.api.IAICoreTile;
import mcjty.ariente.api.ICityEquipment;
import mcjty.ariente.api.ISignalChannel;
import mcjty.ariente.blocks.aicore.AICoreTile;
import mcjty.ariente.blocks.defense.ForceFieldTile;
import mcjty.ariente.power.PowerSystem;
import mcjty.lib.tileentity.TickingTileEntity;
import mcjty.ariente.cables.GenericCableTileEntity;
import net.minecraft.world.level.block.EntityBlock;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityPlan;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.CityFeature;
import mcjty.arienteworld.dimension.DungeonInteriors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static mcjty.ariente.setup.Registration.HOLO_STORAGE_1;
import static mcjty.ariente.setup.Registration.HOLO_STORAGE_2;
import static mcjty.ariente.setup.Registration.HOLO_STORAGE_3;
import static mcjty.ariente.setup.Registration.KEY_CARD;
import static mcjty.ariente.setup.Registration.NETCABLE;
import static mcjty.ariente.setup.Registration.BLUEPRINT;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class CityGenerationTests {

    @GameTest(template = "empty")
    public static void legacyPalettesAndCityGrid(GameTestHelper helper) {
        AssetRegistries.validate();
        helper.assertTrue(LegacyBlockStates.read("ariente:marble@color=black")
                        .equals(LegacyBlockStates.read("ariente:marble_black")), "Marble variants must keep their color");
        helper.assertTrue(LegacyBlockStates.read("ariente:double_marble_slab@color=white,type=bottom")
                        .equals(LegacyBlockStates.read("ariente:marble_slab_white@type=double")), "Double slabs must remain full blocks");
        helper.assertTrue(CityTools.getNearestDungeonCenter(-1, -17).equals(new ChunkPos(-8, -24)),
                "Negative coordinates must use the same city grid");
        City original = CityTools.getCity(123, new ChunkPos(-8, -24));
        CityTools.getCity(456, original.getCenter());
        City repeated = CityTools.getCity(123, original.getCenter());
        helper.assertTrue(original.getPlan() == repeated.getPlan() && original.getName().equals(repeated.getName()),
                "Switching worlds must not change a seed's city layout");
        int commandResult = helper.getLevel().getServer().getCommands().performPrefixedCommand(
                helper.getLevel().getServer().createCommandSourceStack(), "arienteworld findcity city4_1");
        helper.assertTrue(commandResult == 1, "The city search command must find a floating city");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cityCableAppearanceAndNetworkData(GameTestHelper helper) {
        BlockState cableState = NETCABLE.get().defaultBlockState();
        EntityBlock block = (EntityBlock) cableState.getBlock();
        GenericCableTileEntity cable = (GenericCableTileEntity) block.newBlockEntity(BlockPos.ZERO, cableState);
        BlockState facade = LegacyBlockStates.read("ariente:double_marble_slab@color=white,type=bottom");
        cable.setMimicBlock(facade);
        cable.setCableId(42);
        GenericCableTileEntity restored = (GenericCableTileEntity) block.newBlockEntity(BlockPos.ZERO, cableState);
        restored.load(cable.saveWithoutMetadata());
        helper.assertTrue(restored.getCableId() == 42, "City cable must preserve its network ID");
        helper.assertTrue(facade.equals(restored.getMimicBlock()), "City facade must preserve its full block state");
        GenericCableTileEntity client = (GenericCableTileEntity) block.newBlockEntity(BlockPos.ZERO, cableState);
        client.handleUpdateTag(cable.getUpdateTag());
        helper.assertTrue(facade.equals(client.getMimicBlock()), "City facade must be included in the initial client chunk data");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void allCityPlansGenerateAndInitialize(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        FixtureData fixtures = level.getDataStorage().computeIfAbsent(FixtureData::load, FixtureData::new, "ariente_city_test_fixtures");
        int fixtureZ = -1272 - fixtures.iteration++ * 256;
        fixtures.setDirty();
        int index = 0;
        for (CityPlan plan : AssetRegistries.CITYPLANS.getIterable()) {
            if (!plan.isCity()) {
                continue;
            }
            City city = findCity(level.getSeed(), plan, fixtureZ);
            int tick = ++index * 5;
            helper.runAtTickTime(tick, () -> verifyCity(helper, level, city));
        }
        helper.runAtTickTime((index + 1) * 5, helper::succeed);
    }

    private static City findCity(long seed, CityPlan plan, int fixtureZ) {
        // Keep the generated fixtures away from the ordinary smoke-test chunks.
        for (int i = 0; i < 1000; i++) {
            City city = CityTools.getCity(seed, new ChunkPos(1032 + i * 16, fixtureZ));
            if (city.getPlan() == plan && CityTools.isCityCenter(seed, city.getCenter().x, city.getCenter().z)) {
                return city;
            }
        }
        throw new AssertionError("Could not find a city using " + plan.getName());
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void holoStorageUsesCityLootAndSecurity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        FixtureData fixtures = level.getDataStorage().computeIfAbsent(FixtureData::load, FixtureData::new, "ariente_city_test_fixtures");
        int fixtureZ = -5368 - fixtures.iteration++ * 256;
        fixtures.setDirty();
        City city = findCity(level.getSeed(), AssetRegistries.CITYPLANS.get("city0_0"), fixtureZ);
        ChunkPos chunk = city.getCenter();
        level.getChunk(chunk.x, chunk.z);
        int floor = CityTools.getLowestHeight(level, city, chunk.x, chunk.z);
        List<StorageCubeTile> cubes = new ArrayList<>();
        List<Block> tiers = List.of(HOLO_STORAGE_1.get(),
                HOLO_STORAGE_2.get(), HOLO_STORAGE_3.get());
        for (int i = 0; i < tiers.size(); i++) {
            BlockPos pos = new BlockPos(chunk.getMinBlockX() + 4 + i, floor + 1, chunk.getMinBlockZ() + 8);
            level.setBlockAndUpdate(pos, tiers.get(i).defaultBlockState());
            cubes.add((StorageCubeTile) level.getBlockEntity(pos));
        }
        AICoreTile core = level.getChunk(chunk.x, chunk.z).getBlockEntitiesPos().stream().map(level::getBlockEntity)
                .filter(tile -> tile instanceof AICoreTile).map(tile -> (AICoreTile) tile).findFirst().orElseThrow();
        CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(chunk);
        ai.tick(core);
        ItemStack card = new ItemStack(KEY_CARD.get());
        Ariente.arienteSystem.addSecurity(card, ai.getStorageKeyId());
        for (StorageCubeTile cube : cubes) {
            helper.assertTrue(cube.isLocked() && ai.getStorageKeyId().equals(cube.getKeyId()),
                    "Every holo tier must receive the active city's storage lock and key");
            for (int slot = 0; slot < 4; slot++) {
                helper.assertTrue(!cube.getItems().getStackInSlot(slot).isEmpty(), "City setup fills holo storage with loot");
            }
            StorageCubeTile restored = new StorageCubeTile(cube.getBlockPos(), cube.getBlockState());
            restored.load(cube.saveWithoutMetadata());
            helper.assertTrue(restored.isLocked() && cube.getKeyId().equals(restored.getKeyId()), "City security survives saving");
            helper.assertTrue(ItemStack.matches(cube.getItems().getStackInSlot(0), restored.getItems().getStackInSlot(0)),
                    "Security data must preserve the saved inventory");
            StorageCubeTile client = new StorageCubeTile(cube.getBlockPos(), cube.getBlockState());
            client.handleUpdateTag(cube.getUpdateTag());
            helper.assertTrue(client.isLocked(), "Clients receive the lock state without an inventory snapshot");
            cube.acceptKeyCard(new ItemStack(KEY_CARD.get()));
            helper.assertTrue(cube.isLocked(), "An unrelated card cannot unlock city storage");
            cube.acceptKeyCard(card);
            helper.assertTrue(!cube.isLocked(), "The city's storage card unlocks holo storage");
            ItemStack loot = cube.getItems().getStackInSlot(0).copy();
            cube.setup(ai, level, false);
            helper.assertTrue(!cube.isLocked() && ItemStack.matches(loot, cube.getItems().getStackInSlot(0)),
                    "Reloading city equipment must neither relock nor refill storage");
        }
        helper.succeed();
    }

    private static void verifyCity(GameTestHelper helper, ServerLevel level, City city) {
        CityPlan plan = city.getPlan();
        int width = plan.getPlan().get(0).length();
        int depth = plan.getPlan().size();
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                chunks.add(new ChunkPos(city.getCenter().x - width / 2 + x, city.getCenter().z - depth / 2 + z));
            }
        }
        Collections.shuffle(chunks, new Random(123));
        for (ChunkPos chunk : chunks) {
            level.getChunk(chunk.x, chunk.z);
        }
        Palette palette = AssetRegistries.PALETTES.get(plan.getPalette());
        AICoreTile core = null;
        int blocks = 0;
        for (ChunkPos chunk : chunks) {
            int floor = CityTools.getLowestHeight(level, city, chunk.x, chunk.z);
            for (BuildingPart authored : CityTools.getBuildingParts(level.getSeed(), city, chunk.x, chunk.z)) {
                BuildingPart part = DungeonInteriors.vary(authored, palette, level.getSeed(), chunk.x, chunk.z);
                for (int x = 0; x < part.getXSize(); x++) {
                    for (int z = 0; z < part.getZSize(); z++) {
                        for (int y = 0; y < part.getSliceCount(); y++) {
                            BlockState expected = palette.getPalette().get(part.getC(x, y, z));
                            if (expected.isAir()) {
                                continue;
                            }
                            BlockPos pos = new BlockPos(chunk.getMinBlockX() + x, floor + y, chunk.getMinBlockZ() + z);
                            int shaftZ = (chunk.z & 15) == 9 ? 12 : 3;
                            boolean shaft = !plan.isUnderground() && (chunk.x & 15) == 7
                                    && ((chunk.z & 15) == 9 || (chunk.z & 15) == 7
                                    && !CityTools.isDungeonChunk(level.getSeed(), chunk.x, chunk.z + 2))
                                    && x >= 1 && x <= 3 && z >= shaftZ && z <= shaftZ + 2
                                    && pos.getY() <= CityTools.getLowestHeight(level, city, chunk.x, chunk.z) + 1;
                            if (shaft) {
                                // Station elevator shafts deliberately open the authored entrance floor.
                                continue;
                            }
                            helper.assertTrue(level.getBlockState(pos).is(expected.getBlock()),
                                    plan.getName() + ": missing " + expected + " at " + pos + ", found " + level.getBlockState(pos));
                            blocks++;
                            BlockEntity tile = CityFeature.hasBlockEntity(expected) ? level.getBlockEntity(pos) : null;
                            if (tile instanceof AICoreTile aiCore) {
                                core = aiCore;
                            }
                            Map<String, Object> data = part.getTeInfo().get(new BlockPos(x, y, z));
                            if (data != null && tile instanceof ICityEquipment equipment) {
                                if (data.containsKey("channel") && tile instanceof ISignalChannel signal) {
                                    helper.assertTrue(signal.getDesiredChannel() == (Integer) data.get("channel"),
                                            "City equipment must retain its desired channel");
                                }
                                Map<String, Object> actual = equipment.save();
                                for (String key : List.of("height", "scale", "horizontal", "vertical")) {
                                    if (data.containsKey(key)) {
                                        helper.assertTrue(actual != null && data.get(key).equals(actual.get(key)),
                                                "City equipment must retain " + key + " at " + pos);
                                    }
                                }
                            }
                        }
                    }
                }
                floor += part.getSliceCount();
            }
        }
        helper.assertTrue(blocks > 0, "City must contain authored blocks: " + plan.getName());
        helper.assertTrue(core != null, "City must contain an AI core: " + plan.getName());
        CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(city.getCenter());
        ai.tick(core);
        helper.assertTrue(ai.getStorageKeyId() != null && !ai.getStorageKeyId().isEmpty(), "City must initialize its security");
        helper.assertTrue(((IAICoreTile) core).getCityName().equals(city.getName()), "Core must receive its city name");
        if (plan.getName().equals("city0_0")) {
            List<StorageTile> caches = level.getChunk(city.getCenter().x, city.getCenter().z).getBlockEntities().values().stream()
                    .filter(tile -> tile instanceof StorageTile).map(tile -> (StorageTile) tile).toList();
            helper.assertTrue(caches.size() == 1 && caches.get(0).getGridSize() == 2,
                    "The small outpost must contain one 2x2 storage cache");
            StorageTile cache = caches.get(0);
            for (int slot = 0; slot < 4; slot++) {
                helper.assertTrue(!cache.getTotalStack(slot).isEmpty(), "Outpost cache must always contain loot");
            }
            helper.assertTrue(cache.isLocked() && ai.getStorageKeyId().equals(cache.getKeyId()),
                    "Outpost supplies must use the city's storage security");
            helper.assertTrue(!level.getBlockState(core.getBlockPos().above(3)).isAir(), "Outpost core must have a canopy");
        }
        CompoundTag saved = new CompoundTag();
        ai.writeToNBT(saved);
        CityAI restored = new CityAI(level, city.getCenter());
        restored.readFromNBT(saved);
        helper.assertTrue(restored.getStorageKeyId().equals(ai.getStorageKeyId()), "City security must survive saving");
        helper.assertTrue(!restored.isDead(level), "Saved core positions must survive loading");
        restored.tick(core);
        helper.assertTrue(restored.getStorageKeyId().equals(ai.getStorageKeyId()), "Reload must not reinitialize city security");
        if (plan.isFloating()) {
            verifySentinelAlert(helper, level, city, ai, core);
            List<TickingTileEntity> machines = new ArrayList<>();
            List<ForceFieldTile> fields = new ArrayList<>();
            for (ChunkPos chunk : chunks) {
                for (BlockEntity tile : level.getChunk(chunk.x, chunk.z).getBlockEntities().values()) {
                    if (tile instanceof ForceFieldTile field) {
                        fields.add(field);
                    } else if (tile instanceof TickingTileEntity machine) {
                        machines.add(machine);
                    }
                }
            }
            for (int tick = 0; tick < 400; tick++) {
                PowerSystem.getPowerSystem(level).tick();
                machines.forEach(TickingTileEntity::tick);
                fields.forEach(ForceFieldTile::tickServer);
            }
            helper.assertTrue(!fields.isEmpty(), "Floating ship must contain a forcefield generator");
            for (ForceFieldTile field : fields) {
                long panels = Arrays.stream(field.getPanelInfo()).filter(panel -> panel != null && panel.getLife() > 0).count();
                helper.assertTrue(panels > 0, "Authored ship generators/cables must power its forcefield");
                ForceFieldTile display = new ForceFieldTile(field.getBlockPos(), field.getBlockState());
                display.handleUpdateTag(field.getUpdateTag());
                helper.assertTrue(Arrays.stream(display.getPanelInfo()).filter(panel -> panel != null && panel.getLife() > 0).count() == panels,
                        "Active shield panels must reach newly joining clients");
            }
        }
        int lootStacks = 0;
        int storageCount = 0;
        List<AICoreTile> cores = new ArrayList<>();
        for (ChunkPos chunk : chunks) {
            for (BlockEntity tile : level.getChunk(chunk.x, chunk.z).getBlockEntities().values()) {
                if (tile instanceof StorageTile storage) {
                    storageCount++;
                    helper.assertTrue(storage.isLocked(), "Generated city storage must start protected by its city security");
                    var inventory = storage.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
                    for (int slot = 0; slot < inventory.getSlots(); slot++) {
                        var stack = inventory.getStackInSlot(slot);
                        if (!stack.isEmpty()) {
                            lootStacks++;
                            if (stack.is(BLUEPRINT.get())) {
                                helper.assertTrue(BlueprintRecipeRegistry.findRecipe(BlueprintItem.getDestination(stack)) != null,
                                        "Generated city storage must not contain an unusable blueprint");
                            }
                        }
                    }
                } else if (tile instanceof AICoreTile aiCore) {
                    cores.add(aiCore);
                }
            }
        }
        if (plan.getName().equals("city5_1")) {
            helper.assertTrue(storageCount >= 4 && lootStacks >= storageCount * 4,
                    "Cube caches must contain four guaranteed end-game rewards");
            verifyCubeDefenses(helper, level, city, ai, core);
        }
        ArienteWorld.setup.getLogger().info("City {} has {} authored storage cubes and {} loot stacks", plan.getName(), storageCount, lootStacks);
        if (plan.getName().equals("city0_1") || plan.getName().equals("city5_1")) {
            for (AICoreTile aiCore : cores) {
                level.destroyBlock(aiCore.getBlockPos(), false);
            }
            helper.assertTrue(ai.isDead(level), "Destroying all generated cores must defeat the city AI");
            ItemStack card = level.getEntitiesOfClass(ItemEntity.class, new AABB(core.getBlockPos()).inflate(160)).stream()
                    .map(ItemEntity::getItem).filter(stack -> KeyCardItem.hasSecurityTag(stack, ai.getStorageKeyId()))
                    .findFirst().orElseThrow(() -> new AssertionError("Defeating generated city cores must drop its storage-access card"));
            FakePlayer collector = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "cube-loot-test"));
            for (ChunkPos chunk : chunks) {
                for (BlockEntity tile : level.getChunk(chunk.x, chunk.z).getBlockEntities().values()) {
                    if (tile instanceof StorageTile storage) {
                        helper.assertTrue(storage.isLocked(), "Storage must still require its keycard after defeating the AI");
                        collector.setItemInHand(InteractionHand.MAIN_HAND, card.copy());
                        var facing = storage.getBlockState().getValue(BlockStateProperties.FACING);
                        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(storage.getBlockPos()), facing, storage.getBlockPos(), false);
                        card.getItem().onItemUseFirst(collector.getMainHandItem(), new UseOnContext(collector, InteractionHand.MAIN_HAND, hit));
                        helper.assertTrue(!storage.isLocked(), "The final core's card must unlock every generated cube through normal item use");
                        for (int slot = 0; slot < StorageTile.STACKS; slot++) {
                            int count = storage.getTotalStack(slot).getCount();
                            if (count > 0) {
                                storage.giveToPlayer(slot, collector);
                                helper.assertTrue(storage.getTotalStack(slot).getCount() == count - 1, "Unlocked loot must be obtainable");
                                break;
                            }
                        }
                    }
                }
            }
        }
        ArienteWorld.setup.getLogger().info("Verified city {} at {}, {} ({} blocks)", plan.getName(),
                city.getCenter().getMiddleBlockX(), city.getCenter().getMiddleBlockZ(), blocks);
    }

    private static void verifyCubeDefenses(GameTestHelper helper, ServerLevel level, City city, CityAI ai, AICoreTile core) {
        CompoundTag state = new CompoundTag();
        ai.writeToNBT(state);
        helper.assertTrue(state.getList("guards", Tag.TAG_COMPOUND).size() == 10,
                "Cube must initialize ten fixed guards without filling the future boss arena");
        helper.assertTrue(state.getList("masterSoldierPositions", Tag.TAG_COMPOUND).size() == 5,
                "Cube must initialize five fixed master soldiers");
        helper.assertTrue(state.getList("soldierPositions", Tag.TAG_COMPOUND).size() == 48,
                "Every room deck must contribute usable reinforcement positions");
        AABB bounds = new AABB(core.getBlockPos()).inflate(180);
        long guards = level.getEntitiesOfClass(SoldierEntity.class, bounds).stream()
                .filter(soldier -> city.getCenter().equals(soldier.getCityCenter())
                        && soldier.getBehaviourType() == SoldierBehaviourType.SOLDIER_GUARD).count();
        helper.assertTrue(guards == 10, "The generated markers must create real fixed guards");
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "cube-defense-test"));
        player.moveTo(core.getBlockPos().getX() + .5, core.getBlockPos().getY() + 6, core.getBlockPos().getZ() + .5);
        ai.alertCity(player);
        for (int tick = 0; tick < 20; tick++) {
            ai.tick(core);
        }
        var reinforcements = level.getEntitiesOfClass(SoldierEntity.class, bounds,
                soldier -> soldier.isReinforcementFor(city.getCenter()));
        helper.assertTrue(reinforcements.size() >= 8 && reinforcements.size() <= 12,
                "Cube alert must produce an actual eight-to-twelve soldier wave");
        reinforcements.forEach(SoldierEntity::discard);
        for (int tick = 0; tick < 20; tick++) {
            ai.tick(core);
        }
        helper.assertTrue(level.getEntitiesOfClass(SoldierEntity.class, bounds,
                        soldier -> soldier.isReinforcementFor(city.getCenter())).size() >= 8,
                "Cube must replace defeated mobile troops while its core survives");
        ai.pacify(level);
        player.discard();
    }

    private static void verifySentinelAlert(GameTestHelper helper, ServerLevel level, City city, CityAI ai, AICoreTile core) {
        BlockPos pos = core.getBlockPos().above(60);
        SentinelDroneEntity sentinel = SentinelDroneEntity.create(level, 0, city.getCenter());
        sentinel.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "sentinel-test"));
        player.setGameMode(GameType.CREATIVE);
        player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + 10.5);
        level.addFreshEntity(player);
        EntityAIScanForPlayer scan = new EntityAIScanForPlayer(sentinel);
        BlockPos obstruction = pos.south(5).above();
        try {
            ai.pacify(level);
            ai.tick(core);
            helper.assertTrue(!scan.canUse(), "Sentinels must ignore creative players");
            player.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(!scan.canUse(), "Sentinels must ignore spectators");
            player.setGameMode(GameType.SURVIVAL);
            level.setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
            sentinel.getSensing().tick();
            helper.assertTrue(!scan.canUse(), "Sentinels must not spot players through walls");
            level.removeBlock(obstruction, false);
            sentinel.getSensing().tick();
            helper.assertTrue(scan.canUse(), "Sentinels must spot visible survival players");
            scan.start();
            CompoundTag alerted = new CompoundTag();
            ai.writeToNBT(alerted);
            helper.assertTrue(alerted.getInt("onAlert") > 0, "Spotting a player must alert the city");
            helper.assertTrue(sentinel.getLastHurtByMob() == null, "Spotting must not simulate being hurt by a player");
            helper.assertTrue(scan.canContinueToUse(), "Sentinels must keep watching visible players");
            ai.pacify(level);
            for (int tick = 0; tick < 20; tick++) {
                scan.tick();
            }
            ai.writeToNBT(alerted);
            helper.assertTrue(alerted.getInt("onAlert") > 0, "Continuing sightings must refresh the alert");
            player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + 60.5);
            helper.assertTrue(!scan.canContinueToUse(), "Sentinels must lose players beyond their detection range");
            scan.stop();
            helper.assertTrue(!scan.canUse(), "Players beyond 50 blocks must not be detected");
            ai.tick(core);
        } finally {
            level.removeBlock(obstruction, false);
            player.discard();
            sentinel.discard();
        }
    }

    private static class FixtureData extends SavedData {

        private int iteration;

        private static FixtureData load(CompoundTag tag) {
            FixtureData data = new FixtureData();
            data.iteration = tag.getInt("iteration");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("iteration", iteration);
            return tag;
        }
    }

}
