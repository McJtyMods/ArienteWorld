package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.Ariente;
import mcjty.ariente.api.SoldierBehaviourType;
import mcjty.ariente.entities.soldier.SoldierEntity;
import mcjty.ariente.api.IDrone;
import mcjty.ariente.api.ISoldier;
import mcjty.ariente.blocks.aicore.AICoreTile;
import mcjty.ariente.blocks.defense.ForceFieldTile;
import mcjty.ariente.blocks.generators.NegariteGeneratorTile;
import mcjty.ariente.blocks.generators.PosiriteGeneratorTile;
import mcjty.ariente.blocks.utility.StorageTile;
import mcjty.ariente.blocks.utility.WarperTile;
import mcjty.ariente.config.UtilityConfiguration;
import mcjty.ariente.items.BlueprintItem;
import mcjty.ariente.items.KeyCardItem;
import mcjty.ariente.power.PowerSystem;
import mcjty.ariente.recipes.BlueprintRecipeRegistry;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.config.AIConfiguration;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.PortalGenerator;
import mcjty.lib.varia.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class ExplorationRegressionTests {

    @GameTest(template = "empty")
    public static void entranceReturnSurvivesPlayerClone(GameTestHelper helper) {
        var original = helper.makeMockPlayer();
        var replacement = helper.makeMockPlayer();
        BlockPos source = new BlockPos(-7301, 18, 4215);
        original.getPersistentData().put("arienteReturn", NbtUtils.writeBlockPos(source));
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, original, true));
        helper.assertTrue(NbtUtils.readBlockPos(replacement.getPersistentData().getCompound("arienteReturn")).equals(source),
                "A death/respawn clone must retain this player's entrance return");
        replacement.getPersistentData().getCompound("arienteReturn").putInt("X", 12);
        helper.assertTrue(NbtUtils.readBlockPos(original.getPersistentData().getCompound("arienteReturn")).equals(source),
                "The replacement must receive its own copy of the return point");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void naturallyInitializedSoldiersCarryWeapons(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        for (var type : List.of(Registration.ENTITY_SOLDIER.get(), Registration.ENTITY_MASTER_SOLDIER.get())) {
            Mob soldier = type.spawn(level, pos, MobSpawnType.NATURAL);
            helper.assertTrue(soldier != null && (soldier.getMainHandItem().is(Registration.ENERGY_SABRE.get()) || soldier.getMainHandItem().is(Registration.ENERGY_LANCE.get())),
                    "Spawn finalization must actually call the soldier/master equipment initializer");
            CompoundTag saved = new CompoundTag();
            soldier.saveWithoutId(saved);
            Mob restored = type.create(level);
            restored.load(saved);
            helper.assertTrue(ItemStack.matches(soldier.getMainHandItem(), restored.getMainHandItem()),
                    "Entity reload must preserve its weapon without running spawn finalization again");
            soldier.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void chargeThresholdAndIndependentReturnPoints(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel ariente = overworld.getServer().getLevel(DimensionRegister.DIMENSION);
        BlockPos first = new BlockPos(41842, 160, 41842);
        BlockPos second = first.east(6);
        for (BlockPos source : List.of(first, second)) {
            overworld.setBlockAndUpdate(source, Registration.WARPER.get().defaultBlockState());
            overworld.setBlockAndUpdate(source.above(), Blocks.AIR.defaultBlockState());
            overworld.setBlockAndUpdate(source.above(2), Blocks.AIR.defaultBlockState());
        }
        FakePlayer a = player(overworld, first.above());
        FakePlayer b = player(overworld, second.above());
        try {
            WarperTile source = (WarperTile) overworld.getBlockEntity(first);
            source.warp(a);
            helper.assertTrue(a.level() == overworld && !a.getPersistentData().contains("arienteReturn"),
                    "An uncharged entrance must reject server travel without changing the return point");
            int maximum = UtilityConfiguration.WARPER_MAX_CHARGES.get();
            a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.ARIENTE_PEARL.get(), maximum + 1));
            for (int i = 1; i <= maximum; i++) {
                useHeldItem(a, first);
                helper.assertTrue(source.getCharges() == i, "Each pearl must charge exactly once");
                if (i < maximum) {
                    source.warp(a);
                    helper.assertTrue(a.level() == overworld, "A partially charged/stale hologram must reject travel");
                }
            }
            useHeldItem(a, first);
            helper.assertTrue(a.getMainHandItem().getCount() == 1, "A fully charged entrance must not consume more pearls");
            source.load(source.saveWithoutMetadata());
            helper.assertTrue(source.getCharges() == maximum, "The unlock charge must survive block-entity reload");
            ((WarperTile) overworld.getBlockEntity(second)).setCharges(maximum);
            source.warp(a);
            ((WarperTile) overworld.getBlockEntity(second)).warp(b);
            helper.assertTrue(a.level() == ariente && b.level() == ariente && a.blockPosition().equals(b.blockPosition()),
                    "Two nearby entrances must share their grid arrival");
            BlockPos arrival = PortalGenerator.getArrival(ariente, first);
            WarperTile destination = (WarperTile) ariente.getBlockEntity(new BlockPos(
                    (arrival.getX() & ~15) + 7, arrival.getY() - 1, (arrival.getZ() & ~15) + 7));
            destination.warp(b);
            destination.warp(a);
            helper.assertTrue(a.blockPosition().equals(first.above()) && b.blockPosition().equals(second.above()),
                    "A shared arrival must preserve separate per-player entrance returns");
            helper.assertTrue(source.getCharges() == maximum && destination.getCharges() == maximum,
                    "Charging unlocks travel permanently; trips must not consume charges");

            // A valid bed wins when the remembered source has no headroom.
            BlockPos bed = first.east(32);
            for (BlockPos pos : BlockPos.betweenClosed(bed.offset(-2, -1, -2), bed.offset(3, 2, 2))) {
                overworld.setBlockAndUpdate(pos, (pos.getY() == bed.getY() - 1 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            }
            var bedState = Blocks.RED_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
            overworld.setBlockAndUpdate(bed, bedState.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
            overworld.setBlockAndUpdate(bed.east(), bedState.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
            a.setRespawnPosition(Level.OVERWORLD, bed.east(), 0, false, false);
            source.warp(a);
            overworld.setBlockAndUpdate(first.above(2), Blocks.STONE.defaultBlockState());
            destination.warp(a);
            helper.assertTrue(a.level() == overworld && a.blockPosition().distSqr(bed) < 16
                            && overworld.noCollision(a), "An obstructed entrance must fall back to a safe valid bed");
            overworld.setBlockAndUpdate(first.above(2), Blocks.AIR.defaultBlockState());
            a.moveTo(first.getX() + .5, first.getY() + 1, first.getZ() + .5);
            source.warp(a);
            overworld.removeBlock(first, false);
            overworld.removeBlock(bed, false);
            overworld.removeBlock(bed.east(), false);
            destination.warp(a);
            helper.assertTrue(a.level() == overworld && a.blockPosition().distSqr(overworld.getSharedSpawnPos()) < 4096
                            && overworld.noCollision(a), "A broken entrance and missing bed must allow a safe spawn return");
        } finally {
            a.discard();
            b.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void sustainedCityAlertsAndCapturedLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City city = null;
        for (int x = 3208; x < 19208; x += 16) {
            City candidate = CityTools.getCity(level.getSeed(), new ChunkPos(x, -3608));
            if (candidate.getPlan().getName().equals("city4_1") && CityTools.isCityCenter(level.getSeed(), x, -3608)) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    if (!CityTools.isCityCenter(level.getSeed(), x + direction.getStepX() * 16, -3608 + direction.getStepZ() * 16)) {
                        city = candidate;
                        break;
                    }
                }
                if (city != null) {
                    break;
                }
            }
        }
        helper.assertTrue(city != null, "Find a present generated ship for alert/security checks");
        City fixture = city;
        List<BlockEntity> equipment = new ArrayList<>();
        int width = city.getPlan().getPlan().get(0).length();
        int depth = city.getPlan().getPlan().size();
        for (int x = city.getCenter().x - width / 2; x < city.getCenter().x - width / 2 + width; x++) {
            for (int z = city.getCenter().z - depth / 2; z < city.getCenter().z - depth / 2 + depth; z++) {
                level.setChunkForced(x, z, true);
                for (BlockPos pos : level.getChunk(x, z).getBlockEntitiesPos()) {
                    BlockEntity tile = level.getBlockEntity(pos);
                    if (tile != null) {
                        equipment.add(tile);
                    }
                }
            }
        }
        List<AICoreTile> cores = equipment.stream().filter(AICoreTile.class::isInstance).map(AICoreTile.class::cast).toList();
        helper.assertTrue(!cores.isEmpty(), "The freshly generated ship must have AI cores");
        CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(city.getCenter());
        ai.tick(cores.get(0));
        helper.runAfterDelay(2, () -> {
            try {
                verifyAlertsAndCapture(helper, level, fixture, ai, cores, equipment);
            } finally {
                for (int x = fixture.getCenter().x - width / 2; x < fixture.getCenter().x - width / 2 + width; x++) {
                    for (int z = fixture.getCenter().z - depth / 2; z < fixture.getCenter().z - depth / 2 + depth; z++) {
                        level.setChunkForced(x, z, false);
                    }
                }
            }
        });
    }

    private static void verifyAlertsAndCapture(GameTestHelper helper, ServerLevel level, City city, CityAI ai,
                                                List<AICoreTile> cores, List<BlockEntity> equipment) {
        // Initialization itself reports a dirty state even if this is not the primary core.
        // Complete it before selecting the single core that is allowed to advance the AI.
        cores.forEach(ai::tick);
        AICoreTile tickingCore = cores.stream().filter(core -> ai.tick(core)).findFirst()
                .orElseThrow(() -> new AssertionError("Loaded city must have a ticking primary core"));
        AABB bounds = new AABB(new BlockPos(city.getCenter().getMiddleBlockX(), 100, city.getCenter().getMiddleBlockZ())).inflate(180, 230, 180);
        List<FakePlayer> players = new ArrayList<>();
        List<StorageTile> storage = equipment.stream().filter(StorageTile.class::isInstance).map(StorageTile.class::cast).toList();
        List<ForceFieldTile> fields = equipment.stream().filter(ForceFieldTile.class::isInstance).map(ForceFieldTile.class::cast).toList();
        List<List<ItemStack>> originalLoot = storage.stream().map(tile -> {
            List<ItemStack> stacks = new ArrayList<>();
            for (int slot = 0; slot < StorageTile.STACKS; slot++) {
                stacks.add(tile.getTotalStack(slot).copy());
            }
            return stacks;
        }).toList();
        try {
            verifyGuardsDoNotSuppressReinforcements(helper, level, city, ai, tickingCore, bounds);
            for (int count = 1; count <= 3; count++) {
                players.add(player(level, tickingCore.getBlockPos().above(80 + count * 3)));
                ai.pacify(level);
                ai.tick(tickingCore);
                for (FakePlayer player : players) {
                    ai.alertCity(player);
                }
                if (count == 3) {
                    ai.highAlertMode(players.get(0));
                }
                int soldierMin = count == 1 ? city.getPlan().getSoldiersMinimum1()
                        : count == 2 ? city.getPlan().getSoldiersMinimum2() : city.getPlan().getSoldiersMinimumN() * 2;
                int soldierMax = count == 1 ? city.getPlan().getSoldiersWaveMax1()
                        : count == 2 ? city.getPlan().getSoldiersWaveMax2() : city.getPlan().getSoldiersWaveMaxN() * 2;
                int droneMin = count == 1 ? city.getPlan().getDronesMinimum1()
                        : count == 2 ? city.getPlan().getDronesMinimum2() : city.getPlan().getDronesMinimumN();
                int droneMax = count == 1 ? city.getPlan().getDronesWaveMax1()
                        : count == 2 ? city.getPlan().getDronesWaveMax2() : city.getPlan().getDronesWaveMaxN();
                // Five casualty/refill cycles per player count; freeze movement to isolate the city's counters.
                for (int cycle = 0; cycle < 5; cycle++) {
                    level.getEntitiesOfClass(LivingEntity.class, bounds, entity -> entity instanceof ISoldier || entity instanceof IDrone)
                            .forEach(LivingEntity::discard);
                    for (int tick = 0; tick < 20; tick++) {
                        helper.assertTrue(ai.tick(tickingCore), "The primary core must advance the loaded city's alert");
                        level.getEntitiesOfClass(Mob.class, bounds).forEach(mob -> mob.setNoAi(true));
                    }
                    int soldiers = level.getEntitiesOfClass(LivingEntity.class, bounds, entity -> entity instanceof ISoldier).size();
                    int drones = level.getEntitiesOfClass(LivingEntity.class, bounds, entity -> entity instanceof IDrone).size();
                    helper.assertTrue(soldiers >= soldierMin && soldiers <= soldierMax && drones >= droneMin && drones <= droneMax,
                            "Alert waves must refill within the local plan limits for " + count + " players: " + soldiers + "/" + drones);
                    helper.assertTrue(level.getEntitiesOfClass(Mob.class, bounds, entity -> entity instanceof ISoldier).stream()
                                    .allMatch(mob -> (mob.getMainHandItem().is(Registration.ENERGY_SABRE.get()) || mob.getMainHandItem().is(Registration.ENERGY_LANCE.get()))), "Reinforcements must be armed");
                }
            }
            helper.assertTrue(!fields.isEmpty() && fields.stream().allMatch(field -> field.getRSMode() == RedstoneMode.REDSTONE_IGNORED),
                    "Sustained alerts must enable the generated ship's shields");
            CompoundTag saved = new CompoundTag();
            ai.writeToNBT(saved);
            CityAI restored = new CityAI(level, city.getCenter());
            restored.readFromNBT(saved);
            restored.tick(tickingCore);
            CompoundTag reloaded = new CompoundTag();
            restored.writeToNBT(reloaded);
            helper.assertTrue(reloaded.getBoolean("highAlert") && reloaded.getList("players", Tag.TAG_COMPOUND).size() == 3
                            && restored.getStorageKeyId().equals(ai.getStorageKeyId()), "Reload must retain alert players, escalation and security");
            for (int cube = 0; cube < storage.size(); cube++) {
                StorageTile tile = storage.get(cube);
                tile.load(tile.saveWithoutMetadata());
                for (int slot = 0; slot < StorageTile.STACKS; slot++) {
                    helper.assertTrue(ItemStack.matches(originalLoot.get(cube).get(slot), tile.getTotalStack(slot)),
                            "Reload must preserve generated loot without refilling or rerolling cubes");
                }
            }
            for (int tick = 0; tick <= AIConfiguration.ALERT_TIME.get(); tick++) {
                restored.tick(tickingCore);
            }
            restored.writeToNBT(reloaded = new CompoundTag());
            helper.assertTrue(reloaded.getInt("onAlert") == 0 && !reloaded.getBoolean("highAlert")
                            && reloaded.getList("players", Tag.TAG_COMPOUND).isEmpty()
                            && fields.stream().allMatch(field -> field.getRSMode() == RedstoneMode.REDSTONE_ONREQUIRED),
                    "An unrefreshed alert must expire, clear its players/escalation and release shields");
            ai.pacify(level);
            FakePlayer collector = players.get(0);
            ItemStack wrongCard = new ItemStack(Registration.KEY_CARD.get());
            KeyCardItem.addSecurityTag(wrongCard, ai.getKeyId());
            collector.setItemInHand(InteractionHand.MAIN_HAND, wrongCard);
            for (StorageTile tile : storage) {
                useHeldItem(collector, tile.getBlockPos());
                helper.assertTrue(tile.isLocked(), "Door access must not unlock the city's separate storage security");
            }
            for (AICoreTile core : cores) {
                level.destroyBlock(core.getBlockPos(), false);
            }
            helper.assertTrue(ai.isDead(level), "Destroying every generated core must defeat the city");
            ItemEntity reward = level.getEntitiesOfClass(ItemEntity.class, bounds).stream()
                    .filter(item -> KeyCardItem.hasSecurityTag(item.getItem(), ai.getStorageKeyId())).findFirst()
                    .orElseThrow(() -> new AssertionError("Destroying the last core must yield a storage access card"));
            collector.setItemInHand(InteractionHand.MAIN_HAND, reward.getItem().copy());
            reward.discard();
            int collected = 0;
            for (StorageTile tile : storage) {
                useHeldItem(collector, tile.getBlockPos());
                helper.assertTrue(!tile.isLocked(), "The last-core reward must unlock actual generated storage through card use");
                collector.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                for (int slot = 0; slot < StorageTile.STACKS; slot++) {
                    ItemStack stack = tile.getTotalStack(slot);
                    if (!stack.isEmpty()) {
                        if (stack.is(Registration.BLUEPRINT.get())) {
                            helper.assertTrue(BlueprintRecipeRegistry.findRecipe(BlueprintItem.getDestination(stack)) != null,
                                    "Captured blueprints must have usable Constructor recipes");
                        }
                        int before = stack.getCount();
                        tile.giveToPlayer(slot, collector);
                        helper.assertTrue(tile.getTotalStack(slot).getCount() == before - 1,
                                "A player must withdraw real loot after unlocking the cube");
                        collected++;
                    }
                }
                collector.setItemInHand(InteractionHand.MAIN_HAND, reward.getItem().copy());
            }
            helper.assertTrue(collected > 0, "Generated storage must supply obtainable loot");
            List<NegariteGeneratorTile> negative = equipment.stream().filter(NegariteGeneratorTile.class::isInstance)
                    .map(NegariteGeneratorTile.class::cast).toList();
            List<PosiriteGeneratorTile> positive = equipment.stream().filter(PosiriteGeneratorTile.class::isInstance)
                    .map(PosiriteGeneratorTile.class::cast).toList();
            helper.assertTrue(!negative.isEmpty() && !positive.isEmpty(), "The ship must have its authored generators");
            for (int tick = 0; tick < 1300; tick++) {
                ai.tick(tickingCore);
                negative.forEach(NegariteGeneratorTile::tickServer);
                positive.forEach(PosiriteGeneratorTile::tickServer);
                PowerSystem.getPowerSystem(level).tick();
            }
            helper.assertTrue(negative.stream().noneMatch(NegariteGeneratorTile::isWorking)
                            && positive.stream().noneMatch(PosiriteGeneratorTile::isWorking), "Defeated AI must stop refueling captured generators");
            var cities = CityAISystem.getCityAISystem(level).save(new CompoundTag()).getList("cities", Tag.TAG_COMPOUND);
            for (int index = 0; index < cities.size(); index++) {
                CompoundTag record = cities.getCompound(index);
                int x = record.getInt("chunkx");
                int z = record.getInt("chunkz");
                if (Math.abs(x - city.getCenter().x) + Math.abs(z - city.getCenter().z) == 16) {
                    helper.assertTrue(CityTools.isCityCenter(level.getSeed(), x, z),
                            "Reinforcement searches must not create phantom AI for chance-disabled neighbors");
                }
            }
            ArienteWorld.setup.getLogger().info("PHASE 2 CITY CHECK: 15 casualty/refill cycles, 1/2/3-player waves, high alert, expiry/reload, {} captured loot slots and depleted generators", collected);
        } finally {
            players.forEach(FakePlayer::discard);
        }
        helper.succeed();
    }

    private static void verifyGuardsDoNotSuppressReinforcements(GameTestHelper helper, ServerLevel level, City city,
                                                               CityAI ai, AICoreTile core, AABB bounds) {
        CompoundTag state = new CompoundTag();
        ai.writeToNBT(state);
        BlockPos spawn = NbtUtils.readBlockPos(state.getList("soldierPositions", Tag.TAG_COMPOUND).getCompound(0));
        List<LivingEntity> defenders = level.getEntitiesOfClass(LivingEntity.class, bounds, entity -> entity instanceof ISoldier);
        List<LivingEntity> extra = new ArrayList<>();
        FakePlayer observer = player(level, core.getBlockPos().above(80));
        try {
            // More fixed defenders than the wave maximum reproduced the old zero-fighter result.
            for (int i = 0; i <= city.getPlan().getSoldiersWaveMax1(); i++) {
                var guard = Ariente.arienteSystem.createSoldier(level, spawn, Direction.NORTH, city.getCenter(), SoldierBehaviourType.SOLDIER_GUARD, false);
                guard.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.ENERGY_SABRE.get()));
                extra.add(guard);
            }
            extra.add(Ariente.arienteSystem.createSoldier(level, spawn, Direction.NORTH,
                    new ChunkPos(city.getCenter().x + 16, city.getCenter().z), SoldierBehaviourType.SOLDIER_REINFORCEMENT, false));
            ai.alertCity(observer);
            for (int step = 0; step < 20; step++) {
                ai.tick(core);
                level.getEntitiesOfClass(Mob.class, bounds).forEach(mob -> mob.setNoAi(true));
            }
            List<LivingEntity> mobile = level.getEntitiesOfClass(LivingEntity.class, bounds,
                    entity -> entity instanceof ISoldier soldier && soldier.isReinforcementFor(city.getCenter()));
            helper.assertTrue(mobile.size() >= city.getPlan().getSoldiersMinimum1() && mobile.size() <= city.getPlan().getSoldiersWaveMax1(),
                    "An alerted dungeon must deploy its own mobile soldiers while guards, master posts and foreign troops remain present");
            helper.assertTrue(defenders.stream().noneMatch(LivingEntity::isRemoved), "Wave generation must leave authored defenders alive");
            SoldierEntity soldier = (SoldierEntity) mobile.get(0);
            CompoundTag saved = soldier.saveWithoutId(new CompoundTag());
            SoldierEntity restored = (SoldierEntity) soldier.getType().create(level);
            restored.load(saved);
            helper.assertTrue(restored.isReinforcementFor(city.getCenter())
                            && !restored.isReinforcementFor(new ChunkPos(city.getCenter().x + 16, city.getCenter().z)),
                    "Mobile role and city ownership must survive entity NBT reload");
            helper.assertTrue(SoldierBehaviourType.SOLDIER_GUARD.ordinal() == 0 && SoldierBehaviourType.SOLDIER_FIGHTER.ordinal() == 1,
                    "Adding the reinforcement role must preserve existing saved behavior ordinals");
        } finally {
            ai.pacify(level);
            ai.tick(core);
            observer.discard();
            extra.forEach(LivingEntity::discard);
        }
    }

    private static FakePlayer player(ServerLevel level, BlockPos pos) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "explorer-test")) {

            @Override
            public void teleportTo(double x, double y, double z) {
                moveTo(x, y, z);
            }
        };
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        level.addFreshEntity(player);
        return player;
    }

    private static void useHeldItem(FakePlayer player, BlockPos pos) {
        ItemStack stack = player.getMainHandItem();
        stack.getItem().onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }
}
