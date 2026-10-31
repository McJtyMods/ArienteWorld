package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import mcjty.ariente.api.IDrone;
import mcjty.ariente.api.ISoldier;
import mcjty.ariente.api.ISentinel;
import mcjty.ariente.api.SoldierBehaviourType;
import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.blocks.aicore.AICoreTile;
import mcjty.ariente.blocks.generators.NegariteGeneratorTile;
import mcjty.ariente.blocks.generators.PosiriteGeneratorTile;
import mcjty.ariente.blocks.utility.StorageTile;
import mcjty.ariente.blocks.utility.WarperTile;
import mcjty.ariente.config.UtilityConfiguration;
import mcjty.ariente.entities.soldier.SoldierEntity;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.PortalGenerator;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Disposable dedicated-server fixture. Never enable this in a player world. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID)
public class WorldLifecycleFixture {

    private static final TicketType<ChunkPos> CORE_TICKET = TicketType.create("worldcheck", Comparator.comparingLong(ChunkPos::toLong));
    private static ChunkPos coreChunk;
    private static final UUID WATCHER = UUID.fromString("abbb0900-54a9-4b8e-bec7-bb59c9987104");
    private static final Set<UUID> ENTERING = new HashSet<>();
    private static final Set<UUID> ARRIVED = new HashSet<>();
    private static final Set<String> DONE = new HashSet<>();
    private static final List<ChunkPos> CHUNKS = new ArrayList<>();
    private static final Set<ChunkPos> UNLOADED = new HashSet<>();
    private static final List<BlockEntity> GENERATORS = new ArrayList<>();
    private static final Map<BlockPos, Integer> LAST_DUST = new HashMap<>();
    private static FixtureData data;
    private static ServerLevel level;
    private static City city;
    private static CityAI ai;
    private static FakePlayer watcher;
    private static ChunkPos partial;
    private static int ticks;
    private static int stage;
    private static int since;
    private static int pausedAlert;
    private static int maximumSoldiers;
    private static int maximumDrones;
    private static int negativeRefills;
    private static int positiveRefills;
    private static boolean failed;

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("arienteworld.lifecycleCheck")) {
            return;
        }
        event.getDispatcher().register(Commands.literal("worldverify")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("action", StringArgumentType.word()).executes(context -> {
                    try {
                        action(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "action"));
                    } catch (Throwable error) {
                        fail(context.getSource().getServer(), error);
                    }
                    return 1;
                })));
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("arienteworld.lifecycleCheck")) {
            return;
        }
        MinecraftServer server = event.getServer();
        data = server.overworld().getDataStorage().computeIfAbsent(FixtureData::load, FixtureData::new, "arienteworld_lifecycle_check");
        level = server.getLevel(DimensionRegister.DIMENSION);
        server.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        server.overworld().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        watcher = new FakePlayer(level, new GameProfile(WATCHER, "city-observer"));
        ArienteWorld.setup.getLogger().info("WORLD LIFECYCLE BOOT: persisted phase={}", data.tag.getInt("phase"));
        if (data.tag.getInt("phase") == 1) {
            try {
                // Check before the first game tick: saved forced chunks may already be loading.
                ChunkPos center = new ChunkPos(data.tag.getInt("cityX"), data.tag.getInt("cityZ"));
                city = CityTools.getCity(level.getSeed(), center);
                ai = CityAISystem.getCityAISystem(level).getCityAI(center);
                require(state().equals(data.tag.getCompound("ai")), "City SavedData must survive an actual process restart exactly");
                ArienteWorld.setup.getLogger().info("WORLD SAVEDDATA RESTART PASSED: exact city state before the first game tick");
            } catch (Throwable error) {
                fail(server, error);
            }
        }
    }

    public static BlockPos source(String role) {
        return new BlockPos(role.equals("Alpha") ? 4098 : 4104, 160, 4098);
    }

    private static String role(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        require(name.equals("WorldCheckAlpha") || name.equals("WorldCheckBeta"), "Only the two disposable clients may use this fixture");
        return name.endsWith("Alpha") ? "Alpha" : "Beta";
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("arienteworld.lifecycleCheck") || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        try {
            String role = role(player);
            player.server.getPlayerList().op(player.getGameProfile());
            CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            BlockPos source = source(role);
            if (!persisted.getBoolean("worldcheckInitialized")) {
                require(data.tag.getInt("phase") == 0, "Restart must load the original player data");
                ServerLevel overworld = player.server.overworld();
                overworld.setBlockAndUpdate(source, Registration.WARPER.get().defaultBlockState());
                overworld.setBlockAndUpdate(source.above(), Blocks.AIR.defaultBlockState());
                overworld.setBlockAndUpdate(source.above(2), Blocks.AIR.defaultBlockState());
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.setItemInHand(InteractionHand.MAIN_HAND,
                        new ItemStack(Registration.ARIENTE_PEARL.get(), UtilityConfiguration.WARPER_MAX_CHARGES.get()));
                ItemStack token = new ItemStack(Registration.KEY_CARD.get());
                token.getOrCreateTag().putString("worldcheckRole", role);
                player.getInventory().setItem(9, token);
                ItemStack helmet = new ItemStack(Registration.POWERSUIT_HEAD.get());
                helmet.getOrCreateTag().putBoolean(ArmorUpgradeType.NIGHTVISION.getModuleKey(), true);
                helmet.getTag().putInt("power", 30000);
                player.setItemSlot(EquipmentSlot.HEAD, helmet);
                player.teleportTo(overworld, source.getX() + .5, source.getY() + 1, source.getZ() + .5, 0, 0);
                persisted.putBoolean("worldcheckInitialized", true);
                player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            } else {
                require(player.getInventory().getItem(9).getOrCreateTag().getString("worldcheckRole").equals(role),
                        "Inventory identity must survive real disconnect/restart");
                require(returnPoint(player).equals(source.above()), "Entrance return must survive disconnect, death and restart");
            }
            ArienteWorld.setup.getLogger().info("WORLD NETWORK LOGIN: {} in {}, persisted phase={}", role, player.level().dimension().location(), data.tag.getInt("phase"));
        } catch (Throwable error) {
            fail(player.server, error);
        }
    }

    private static BlockPos returnPoint(ServerPlayer player) {
        return NbtUtils.readBlockPos(player.getPersistentData().getCompound("arienteReturn"));
    }

    private static void action(ServerPlayer player, String action) {
        String role = role(player);
        if (action.equals("enter")) {
            require(player.level().dimension().equals(Level.OVERWORLD), "Travel must start in the Overworld");
            WarperTile entrance = (WarperTile) player.server.overworld().getBlockEntity(source(role));
            if (entrance.getChargePercentage() < 100) {
                entrance.warp(player);
                require(player.level().dimension().equals(Level.OVERWORLD), "The server must reject an uncharged entrance");
                int attempts = UtilityConfiguration.WARPER_MAX_CHARGES.get();
                while (!player.getMainHandItem().isEmpty() && attempts-- > 0) {
                    ItemStack held = player.getMainHandItem();
                    require(held.is(Registration.ARIENTE_PEARL.get()), "Only the supplied pearls charge the entrance");
                    held.getItem().onItemUseFirst(held, new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(source(role)), Direction.UP, source(role), false)));
                }
            }
            require(entrance.getCharges() == UtilityConfiguration.WARPER_MAX_CHARGES.get(), "Entrance charge must persist across restart");
            ENTERING.add(player.getUUID());
            if (ENTERING.size() == 2) {
                for (UUID id : ENTERING) {
                    ServerPlayer traveler = player.server.getPlayerList().getPlayer(id);
                    ((WarperTile) player.server.overworld().getBlockEntity(source(role(traveler)))).warp(traveler);
                    require(traveler.level() == level && level.noCollision(traveler), "Both players must reach a safe shared arrival");
                }
                ENTERING.clear();
            }
        } else if (action.equals("arrived")) {
            require(player.level() == level, "Arrival acknowledgment must come from Ariente");
            ARRIVED.add(player.getUUID());
            if (ARRIVED.size() == 2) {
                for (UUID id : ARRIVED) {
                    player.server.getPlayerList().getPlayer(id).sendSystemMessage(Component.literal("WORLD CHECK: peers ready"));
                }
                ARRIVED.clear();
            }
        } else if (action.equals("die")) {
            require(player.level() == level && returnPoint(player).equals(source(role).above()), "Reconnect must preserve the Ariente entrance return");
            player.setRespawnPosition(DimensionRegister.DIMENSION, player.blockPosition(), 0, true, false);
            player.kill();
        } else if (action.equals("return")) {
            require(player.level() == level && returnPoint(player).equals(source(role).above()), "Death/respawn must preserve the per-player entrance return");
            BlockPos arrival = PortalGenerator.getArrival(level, source(role));
            WarperTile returnWarper = (WarperTile) level.getBlockEntity(new BlockPos(
                    (arrival.getX() & ~15) + 7, arrival.getY() - 1, (arrival.getZ() & ~15) + 7));
            returnWarper.warp(player);
            require(player.level().dimension().equals(Level.OVERWORLD) && player.blockPosition().equals(source(role).above()),
                    "Shared return warper must send each player to their own entrance");
        } else if (action.equals("done")) {
            require(player.level().dimension().equals(Level.OVERWORLD) && player.blockPosition().distSqr(source(role).above()) < 9,
                    "Returned client must remain at its own entrance");
            DONE.add(role);
            ArienteWorld.setup.getLogger().info("WORLD NETWORK SERVER PASSED: {}, restart={}", role, data.tag.getInt("phase") >= 1);
        } else {
            throw new IllegalArgumentException("Unknown verification action");
        }
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (event.getLevel() == level && CHUNKS.contains(event.getChunk().getPos())) {
            UNLOADED.add(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (data == null || failed || event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            ticks++;
            if (ticks == 20) {
                prepare();
            }
            if (city == null) {
                return;
            }
            level.getEntitiesOfClass(Mob.class, bounds()).forEach(mob -> {
                mob.setNoAi(true);
                mob.setNoGravity(true);
                mob.setPersistenceRequired();
            });
            if (data.tag.getInt("phase") >= 1) {
                if (ticks == 100) {
                    verifyCity();
                    ArienteWorld.setup.getLogger().info("WORLD PROCESS RESTART PASSED: city keys, loot, guards, alert state and generated chunks loaded from disk");
                }
                if (ticks >= 100 && DONE.size() == 2) {
                    finish(event.getServer(), "WORLD LIFECYCLE RESTART PASSED", 2);
                }
                return;
            }
            if (stage == 0 && ticks == 100) {
                data.tag.putString("storageKey", ai.getStorageKeyId());
                data.tag.put("loot", loot());
                data.tag.put("guards", guards());
                ai.highAlertMode(watcher);
                CityAISystem.getCityAISystem(level).saveSystem();
                data.tag.putInt("initialSoldiers", level.getEntitiesOfClass(LivingEntity.class, bounds(), entity -> entity instanceof ISoldier).size());
                CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
                level.getChunkSource().addRegionTicket(CORE_TICKET, coreChunk, 1, coreChunk);
                stage = 1;
                since = ticks;
            } else if (stage == 1 && level.getChunkSource().getChunkNow(partial.x, partial.z) == null) {
                pausedAlert = state().getInt("onAlert");
                stage = 2;
                since = ticks;
            } else if (stage == 2 && ticks - since >= 100) {
                require(state().getInt("onAlert") == pausedAlert, "A partially unloaded city must pause its AI");
                forceCity();
                level.getChunkSource().removeRegionTicket(CORE_TICKET, coreChunk, 1, coreChunk);
                stage = 3;
                since = ticks;
                ArienteWorld.setup.getLogger().info("WORLD PARTIAL FOOTPRINT PASSED: alert paused with a missing full footprint chunk");
            } else if (stage == 3 && ticks - since >= 100) {
                verifyCity();
                CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
                stage = 4;
                since = ticks;
            } else if (stage == 4 && UNLOADED.containsAll(CHUNKS) && CHUNKS.stream().allMatch(chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null)) {
                require(UNLOADED.containsAll(CHUNKS), "Every city chunk must produce an actual unload event");
                forceCity();
                stage = 5;
                since = ticks;
            } else if (stage == 5 && ticks - since >= 100) {
                verifyCity();
                for (ChunkPos chunk : CHUNKS) {
                    level.getChunk(chunk.x, chunk.z).getBlockEntities().values().stream()
                            .filter(tile -> tile instanceof NegariteGeneratorTile || tile instanceof PosiriteGeneratorTile).forEach(GENERATORS::add);
                }
                require(GENERATORS.stream().anyMatch(NegariteGeneratorTile.class::isInstance)
                        && GENERATORS.stream().anyMatch(PosiriteGeneratorTile.class::isInstance), "The native soak must exercise both generator fuels");
                stage = 6;
                since = ticks;
                ArienteWorld.setup.getLogger().info("WORLD FULL UNLOAD PASSED: loot, keys and guard UUIDs retained without duplicate initialization");
            } else if (stage == 6) {
                for (BlockEntity generator : GENERATORS) {
                    int dust = generator.saveWithoutMetadata().getCompound("Info").getInt("dust");
                    if (dust > LAST_DUST.getOrDefault(generator.getBlockPos(), dust)) {
                        if (generator instanceof NegariteGeneratorTile) {
                            negativeRefills++;
                        } else {
                            positiveRefills++;
                        }
                    }
                    LAST_DUST.put(generator.getBlockPos(), dust);
                }
                if ((ticks - since) % 200 == 0) {
                    ai.highAlertMode(watcher);
                    CityAISystem.getCityAISystem(level).saveSystem();
                }
                int soldiers = level.getEntitiesOfClass(LivingEntity.class, bounds(), entity -> entity instanceof ISoldier).size();
                int drones = level.getEntitiesOfClass(LivingEntity.class, bounds(), entity -> entity instanceof IDrone).size();
                maximumSoldiers = Math.max(maximumSoldiers, soldiers);
                maximumDrones = Math.max(maximumDrones, drones);
                require(soldiers <= Math.max(data.tag.getInt("initialSoldiers"), city.getPlan().getSoldiersWaveMax1() * 2)
                        && drones <= city.getPlan().getDronesWaveMax1(), "Stationary local reinforcements must remain bounded");
                if ((ticks - since) % 1200 == 0) {
                    ArienteWorld.setup.getLogger().info("WORLD AI SOAK: {} native ticks, peak soldiers/drones={}/{}, fuel refills={}/{}", ticks - since, maximumSoldiers, maximumDrones, negativeRefills, positiveRefills);
                }
                if (ticks - since >= Integer.getInteger("arienteworld.soakTicks", 12000) && DONE.size() == 2) {
                    verifyCity();
                    int minimumRefills = Integer.getInteger("arienteworld.soakTicks", 12000) / 1200;
                    require(negativeRefills >= minimumRefills && positiveRefills >= minimumRefills,
                            "Native city ticking must consume and replenish both generator fuels repeatedly");
                    data.tag.put("ai", state());
                    data.tag.putInt("peakSoldiers", maximumSoldiers);
                    data.tag.putInt("peakDrones", maximumDrones);
                    finish(event.getServer(), "WORLD LIFECYCLE PRE-RESTART PASSED", 1);
                }
            }
            require(stage >= 6 || ticks - since < 2400, "City chunk lifecycle stage timed out: " + stage);
        } catch (Throwable error) {
            fail(event.getServer(), error);
        }
    }

    private static void prepare() {
        if (data.tag.getInt("phase") >= 1) {
            ChunkPos center = new ChunkPos(data.tag.getInt("cityX"), data.tag.getInt("cityZ"));
            city = CityTools.getCity(level.getSeed(), center);
            ai = CityAISystem.getCityAISystem(level).getCityAI(center);
        } else {
            for (int x = 1032; x < 17032; x += 16) {
                if (CityTools.isCityCenter(level.getSeed(), x, -2200)) {
                    City candidate = CityTools.getCity(level.getSeed(), new ChunkPos(x, -2200));
                    if (candidate.getPlan().getName().equals("city3_1")) {
                        city = candidate;
                        break;
                    }
                }
            }
            require(city != null, "Find a fresh authored city for the lifecycle test");
            data.tag.putInt("cityX", city.getCenter().x);
            data.tag.putInt("cityZ", city.getCenter().z);
            ai = CityAISystem.getCityAISystem(level).getCityAI(city.getCenter());
        }
        int width = city.getPlan().getPlan().get(0).length();
        int depth = city.getPlan().getPlan().size();
        for (int x = city.getCenter().x - width / 2; x < city.getCenter().x - width / 2 + width; x++) {
            for (int z = city.getCenter().z - depth / 2; z < city.getCenter().z - depth / 2 + depth; z++) {
                CHUNKS.add(new ChunkPos(x, z));
            }
        }
        forceCity();
        for (ChunkPos chunk : CHUNKS) {
            for (BlockPos pos : level.getChunk(chunk.x, chunk.z).getBlockEntitiesPos()) {
                if (level.getBlockEntity(pos) instanceof AICoreTile core) {
                    coreChunk = chunk;
                    ai.tick(core);
                }
            }
        }
        partial = CHUNKS.stream().max(Comparator.comparingInt(chunk -> Math.max(Math.abs(chunk.x - coreChunk.x), Math.abs(chunk.z - coreChunk.z)))).orElseThrow();
        require(Math.max(Math.abs(partial.x - coreChunk.x), Math.abs(partial.z - coreChunk.z)) >= 2, "The fixture needs a chunk beyond the core ticket border");
        ArienteWorld.setup.getLogger().info("WORLD LIFECYCLE CITY: {} at {}, {} footprint chunks", city.getName(), city.getCenter(), CHUNKS.size());
    }

    private static void forceCity() {
        for (ChunkPos chunk : CHUNKS) {
            level.setChunkForced(chunk.x, chunk.z, true);
            level.getChunk(chunk.x, chunk.z);
        }
    }

    private static AABB bounds() {
        return new AABB(new BlockPos(city.getCenter().getMiddleBlockX(), 100, city.getCenter().getMiddleBlockZ())).inflate(100, 220, 100);
    }

    private static CompoundTag state() {
        CompoundTag result = new CompoundTag();
        ai.writeToNBT(result);
        return result;
    }

    private static ListTag loot() {
        ListTag result = new ListTag();
        for (ChunkPos chunk : CHUNKS) {
            for (BlockPos pos : level.getChunk(chunk.x, chunk.z).getBlockEntitiesPos().stream().sorted().toList()) {
                BlockEntity tile = level.getBlockEntity(pos);
                if (tile instanceof StorageTile storage) {
                    CompoundTag cube = NbtUtils.writeBlockPos(pos);
                    ListTag items = new ListTag();
                    for (int slot = 0; slot < StorageTile.STACKS; slot++) {
                        items.add(storage.getTotalStack(slot).save(new CompoundTag()));
                    }
                    cube.put("items", items);
                    result.add(cube);
                }
            }
        }
        require(!result.isEmpty(), "The generated fixture must have authored storage");
        return result;
    }

    private static ListTag guards() {
        ListTag result = new ListTag();
        level.getEntitiesOfClass(SoldierEntity.class, bounds(), soldier -> soldier.getBehaviourType() == SoldierBehaviourType.SOLDIER_GUARD)
                .stream().map(entity -> entity.getUUID().toString()).sorted().forEach(id -> result.add(StringTag.valueOf(id)));
        return result;
    }

    private static void verifyCity() {
        require(ai.getStorageKeyId().equals(data.tag.getString("storageKey")), "City reload must preserve its security key");
        require(loot().equals(data.tag.getList("loot", Tag.TAG_COMPOUND)), "Actual chunk reload must preserve every authored loot stack");
        require(guards().equals(data.tag.getList("guards", Tag.TAG_STRING)), "Actual entity reload must preserve guard UUIDs without duplication");
        int sentinels = level.getEntitiesOfClass(LivingEntity.class, bounds(), entity -> entity instanceof ISentinel).size();
        require(sentinels == state().getCompound("settings").getInt("sentinels"),
                "Entity reload must preserve the city's sentinel population without duplicate respawns");
        require(!ai.isDead(level), "Reload must retain the live city cores");
    }

    private static void finish(MinecraftServer server, String message, int phase) throws Exception {
        data.tag.putInt("phase", phase);
        data.tag.put("ai", state());
        data.setDirty();
        CityAISystem.getCityAISystem(level).saveSystem();
        Files.writeString(Path.of("result.txt"), message + "\n");
        ArienteWorld.setup.getLogger().info(message);
        server.halt(false);
    }

    private static void fail(MinecraftServer server, Throwable error) {
        failed = true;
        ArienteWorld.setup.getLogger().error("WORLD LIFECYCLE FAILED", error);
        server.halt(false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static class FixtureData extends SavedData {

        private CompoundTag tag = new CompoundTag();

        private static FixtureData load(CompoundTag tag) {
            FixtureData result = new FixtureData();
            result.tag = tag.copy();
            return result;
        }

        @Override
        public CompoundTag save(CompoundTag output) {
            return tag.copy();
        }
    }
}
