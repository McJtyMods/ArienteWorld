package mcjty.arienteworld.client;

import mcjty.ariente.Ariente;
import mcjty.ariente.blocks.aicore.AICoreTile;
import mcjty.ariente.blocks.utility.BlueprintStorageTile;
import mcjty.ariente.blocks.utility.ConstructorTile;
import mcjty.ariente.blocks.utility.StorageTile;
import mcjty.ariente.blocks.utility.WarperTile;
import mcjty.ariente.config.UtilityConfiguration;
import mcjty.ariente.entities.EntityArientePearl;
import mcjty.ariente.items.BlueprintItem;
import mcjty.ariente.items.KeyCardItem;
import mcjty.ariente.recipes.BlueprintRecipeRegistry;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.PortalGenerator;
import mcjty.arienteworld.dimension.terrain.ArienteBiomeLayout;
import mcjty.arienteworld.dimension.terrain.BiomeProfile;
import mcjty.arienteworld.oregen.OverworldDungeonGen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Opt-in fresh-save travel and natural-spawning fixture; supplies crafting inputs and moves the player. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class ExplorationClientChecks {

    private static boolean started;
    private static UUID playerId;
    private static BlockPos entrance;
    private static BlockPos observation;
    private static volatile boolean observing;
    private static volatile boolean returning;
    private static volatile Throwable failure;
    private static volatile boolean sawAriente;
    private static int ticks;
    private static int period;
    private static final int[] SPAWNS = new int[3];
    private static int masters;
    private static int maximumCount;
    private static int pearls;
    private static int returnTicks;
    private static City capturedCity;
    private static CityAI capturedAI;
    private static final List<BlockEntity> EQUIPMENT = new ArrayList<>();
    private static ItemStack capturedLoot = ItemStack.EMPTY;

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("arienteworld.phase2ClientCheck") || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        if (failure != null) {
            throw new IllegalStateException("Phase 2 exploration client check failed", failure);
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) {
            return;
        }
        if (!started) {
            started = true;
            playerId = mc.player.getUUID();
            mc.options.renderDistance().set(8);
            mc.getSingleplayerServer().execute(() -> {
                try {
                    enter(mc.getSingleplayerServer().getPlayerList().getPlayer(playerId));
                } catch (Throwable e) {
                    failure = e;
                }
            });
        }
        if (mc.level.dimension().equals(DimensionRegister.DIMENSION)) {
            sawAriente = true;
        }
        if (returning && mc.level.dimension().equals(Level.OVERWORLD) && ++returnTicks >= 40) {
            require(sawAriente, "The client must receive both dimension transitions");
            require(mc.player.getInventory().countItem(capturedLoot.getItem()) > 0
                    && mc.player.getMainHandItem().is(Registration.ENERGY_SABRE.get()), "City loot and crafted equipment must reach the returned client");
            ArienteWorld.setup.getLogger().info("PHASE 2 EXPLORATION CLIENT CHECK PASSED: crafted pearls, generated entrance/arrival, city capture/card/loot, Constructor/sabre attack and survival return; natural soldiers normal/hard/after-peaceful={}/{}/{}, masters={}, maximum monster count={}",
                    SPAWNS[0], SPAWNS[1], SPAWNS[2], masters, maximumCount);
            mc.stop();
        }
    }

    private static void enter(ServerPlayer player) {
        ServerLevel overworld = player.getServer().overworld();
        overworld.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.getServer());
        for (int x = 6000; x < 6200 && entrance == null; x++) {
            for (int z = 6000; z < 6200; z++) {
                if (OverworldDungeonGen.isDungeonChunk(overworld, x, z)) {
                    overworld.getChunk(x, z);
                    BlockPos pos = OverworldDungeonGen.getDungeonPos(overworld, x, z).above();
                    if (overworld.getBlockEntity(pos) instanceof WarperTile) {
                        entrance = pos;
                        break;
                    }
                }
            }
        }
        require(entrance != null, "A fresh generated Overworld entrance must exist");
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.teleportTo(overworld, entrance.getX() + .5, entrance.getY() + 1, entrance.getZ() + .5, 0, -90);
        int count = UtilityConfiguration.WARPER_MAX_CHARGES.get() + 1;
        var grid = new TransientCraftingContainer(player.inventoryMenu, 3, 3);
        for (int slot = 0; slot < 9; slot++) {
            grid.setItem(slot, new ItemStack(slot == 4 ? Items.ENDER_PEARL : Items.QUARTZ, count));
        }
        ItemStack crafted = ItemStack.EMPTY;
        for (int i = 0; i < count; i++) {
            var recipe = overworld.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, overworld).orElseThrow();
            ItemStack result = recipe.assemble(grid, overworld.registryAccess());
            require(result.is(Registration.ARIENTE_PEARL.get()), "Quartz and ender pearls must craft locator/charge pearls");
            if (crafted.isEmpty()) {
                crafted = result;
            } else {
                crafted.grow(result.getCount());
            }
            for (int slot = 0; slot < 9; slot++) {
                grid.removeItem(slot, 1);
            }
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, crafted);
        Registration.ARIENTE_PEARL.get().use(overworld, player, InteractionHand.MAIN_HAND);
        require(pearls == 1 && player.getMainHandItem().getCount() == count - 1, "A survival locator throw must create and consume one pearl");
        WarperTile warper = (WarperTile) overworld.getBlockEntity(entrance);
        warper.warp(player);
        require(player.level() == overworld, "An uncharged entrance must reject native server travel");
        for (int i = 0; i < count - 1; i++) {
            ItemStack held = player.getMainHandItem();
            held.getItem().onItemUseFirst(held, new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(entrance), Direction.UP, entrance, false)));
        }
        require(player.getMainHandItem().isEmpty() && warper.getChargePercentage() == 100, "Actual pearl use must unlock the entrance");
        warper.warp(player);
        require(player.level().dimension().equals(DimensionRegister.DIMENSION) && player.serverLevel().noCollision(player),
                "A charged entrance must place the survival player safely in Ariente");
        ArienteWorld.setup.getLogger().info("PHASE 2 NATIVE TRAVEL: {} -> {}", entrance, player.blockPosition());
        observation = findObservation(player.serverLevel());
        player.serverLevel().setBlockAndUpdate(observation.below(), Blocks.GLASS.defaultBlockState());
        player.teleportTo(player.serverLevel(), observation.getX() + .5, observation.getY(), observation.getZ() + .5, 0, 45);
        player.getServer().setDifficulty(Difficulty.NORMAL, true);
        player.serverLevel().setDayTime(18000);
        player.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.getServer());
        player.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, player.getServer());
        observing = true;
    }

    private static BlockPos findObservation(ServerLevel level) {
        ArienteBiomeLayout layout = new ArienteBiomeLayout(level.getSeed());
        for (int x = 512; x < 1024; x++) {
            for (int z = 512; z < 1024; z++) {
                boolean interior = true;
                for (int dx = -3; dx <= 3 && interior; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (layout.get((x + dx) * 4 + 2, (z + dz) * 4 + 2) != BiomeProfile.CITY
                                || CityTools.isDungeonChunk(level.getSeed(), x + dx, z + dz)) {
                            interior = false;
                            break;
                        }
                    }
                }
                if (interior) {
                    int y = level.getChunkSource().getGenerator().getBaseHeight(x * 16 + 8, z * 16 + 8,
                            Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
                    if (y > 64 && y < 90) {
                        return new BlockPos(x * 16 + 8, 135, z * 16 + 8);
                    }
                }
            }
        }
        throw new IllegalStateException("No natural city-biome observation area");
    }

    @SubscribeEvent
    public static void entityJoined(EntityJoinLevelEvent event) {
        if (!Boolean.getBoolean("arienteworld.phase2ClientCheck") || event.getLevel().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof EntityArientePearl) {
            pearls++;
        }
        if (observing && event.getLevel().dimension().equals(DimensionRegister.DIMENSION)
                && event.getEntity() instanceof Mob mob && mob.getSpawnType() == MobSpawnType.NATURAL
                && (mob.getType() == Registration.ENTITY_SOLDIER.get() || mob.getType() == Registration.ENTITY_MASTER_SOLDIER.get())) {
            require(mob.getMainHandItem().is(Registration.ENERGY_SABRE.get()), "Naturally spawned soldiers must be armed");
            SPAWNS[period]++;
            if (mob.getType() == Registration.ENTITY_MASTER_SOLDIER.get()) {
                masters++;
            }
        }
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!observing || failure != null || event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            ticks++;
            if (ticks > 3700) {
                if (ticks == 3730) {
                    captureAndCraft(player);
                    returnHome(player);
                }
                return;
            }
            var state = level.getChunkSource().getLastSpawnState();
            if (state != null) {
                maximumCount = Math.max(maximumCount, state.getMobCategoryCounts().getInt(MobCategory.MONSTER));
                require(maximumCount <= MobCategory.MONSTER.getMaxInstancesPerChunk(), "Natural monsters must respect the dimension's hostile cap");
            }
            if (ticks == 1200 || ticks == 2400) {
                require(SPAWNS[period] > 0, "Natural soldiers must spawn during normal and hard difficulty observation");
                ArienteWorld.setup.getLogger().info("PHASE 2 NATURAL SPAWNS: period={}, new soldiers={}, max monster count={}", period, SPAWNS[period], maximumCount);
                if (ticks == 1200) {
                    level.getEntitiesOfClass(Mob.class, new AABB(observation).inflate(160), mob -> mob.getType().getCategory() == MobCategory.MONSTER)
                            .forEach(Mob::discard);
                    period = 1;
                    player.getServer().setDifficulty(Difficulty.HARD, true);
                } else {
                    player.getServer().setDifficulty(Difficulty.PEACEFUL, true);
                }
            } else if (ticks == 2500) {
                require(level.getEntitiesOfClass(Mob.class, new AABB(observation).inflate(128),
                        mob -> mob.getType().getCategory() == MobCategory.MONSTER).isEmpty(), "Peaceful must clear nearby hostile inhabitants");
                period = 2;
                player.getServer().setDifficulty(Difficulty.NORMAL, true);
            } else if (ticks == 3700) {
                require(SPAWNS[2] > 0, "Natural soldier spawning must resume after peaceful difficulty");
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.getServer());
                prepareCity(player);
            }
        } catch (Throwable e) {
            failure = e;
        }
    }

    private static void prepareCity(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int x = 1032; x < 17032; x += 16) {
            City city = CityTools.getCity(level.getSeed(), new ChunkPos(x, -4408));
            if (city.getPlan().getName().equals("city4_1") && CityTools.isCityCenter(level.getSeed(), x, -4408)) {
                capturedCity = city;
                break;
            }
        }
        require(capturedCity != null, "Find a generated ship with authored city loot");
        int width = capturedCity.getPlan().getPlan().get(0).length();
        int depth = capturedCity.getPlan().getPlan().size();
        for (int x = capturedCity.getCenter().x - width / 2; x < capturedCity.getCenter().x - width / 2 + width; x++) {
            for (int z = capturedCity.getCenter().z - depth / 2; z < capturedCity.getCenter().z - depth / 2 + depth; z++) {
                level.setChunkForced(x, z, true);
                for (BlockPos pos : level.getChunk(x, z).getBlockEntitiesPos()) {
                    BlockEntity tile = level.getBlockEntity(pos);
                    if (tile != null) {
                        EQUIPMENT.add(tile);
                    }
                }
            }
        }
        AICoreTile core = EQUIPMENT.stream().filter(AICoreTile.class::isInstance).map(AICoreTile.class::cast).findFirst().orElseThrow();
        capturedAI = CityAISystem.getCityAISystem(level).getCityAI(capturedCity.getCenter());
        capturedAI.tick(core);
        player.teleportTo(level, core.getBlockPos().getX() + .5, core.getBlockPos().getY() + 70,
                core.getBlockPos().getZ() + .5, 0, 0);
    }

    private static void captureAndCraft(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BlockEntity tile : EQUIPMENT) {
            if (tile instanceof AICoreTile) {
                level.destroyBlock(tile.getBlockPos(), false, player);
            }
        }
        require(capturedAI.isDead(level), "All generated cores must be defeated");
        ItemEntity reward = level.getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(200)).stream()
                .filter(item -> KeyCardItem.hasSecurityTag(item.getItem(), capturedAI.getStorageKeyId())).findFirst().orElseThrow();
        ItemStack card = reward.getItem().copy();
        reward.discard();
        for (BlockEntity tile : EQUIPMENT) {
            if (!(tile instanceof StorageTile storage)) {
                continue;
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, card.copy());
            card.getItem().onItemUseFirst(card, new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(tile.getBlockPos()), Direction.UP, tile.getBlockPos(), false)));
            require(!storage.isLocked(), "The city's last-core card must unlock its storage");
            for (int slot = 0; slot < StorageTile.STACKS; slot++) {
                if (!storage.getTotalStack(slot).isEmpty()) {
                    capturedLoot = storage.getTotalStack(slot).copy();
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    storage.giveToPlayer(slot, player);
                    break;
                }
            }
            if (!capturedLoot.isEmpty()) {
                break;
            }
        }
        require(!capturedLoot.isEmpty() && player.getInventory().countItem(capturedLoot.getItem()) > 0,
                "A survival player must obtain actual generated city loot");

        // Supplied bench/blueprint/materials isolate equipment use from random loot rarity.
        BlockPos bench = player.blockPosition().east(2);
        level.setBlockAndUpdate(bench, Registration.CONSTRUCTOR.get().defaultBlockState());
        level.setBlockAndUpdate(bench.above(), Registration.BLUEPRINT_STORAGE.get().defaultBlockState());
        var blueprints = (BlueprintStorageTile) level.getBlockEntity(bench.above());
        blueprints.getItems().setStackInSlot(0, BlueprintItem.makeBluePrint(new ItemStack(Registration.ENERGY_SABRE.get())));
        var recipe = BlueprintRecipeRegistry.findRecipe(new ItemStack(Registration.ENERGY_SABRE.get()));
        for (ItemStack ingredient : recipe.getIngredientList()) {
            player.getInventory().add(ingredient.copy());
        }
        int before = player.getInventory().countItem(Registration.ENERGY_SABRE.get());
        var constructor = (ConstructorTile) level.getBlockEntity(bench);
        var slots = constructor.createGui("", Ariente.guiHandler.getComponentRegistry()).findChild("outputslots").orElseThrow();
        slots.hit(player, null, 2, 6);
        slots.hit(player, null, 2, 6);
        require(player.getInventory().countItem(Registration.ENERGY_SABRE.get()) == before + 1,
                "The real Constructor control must craft a usable sabre for the player");
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Registration.ENERGY_SABRE.get())) {
                player.getInventory().setItem(slot, player.getMainHandItem());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                break;
            }
        }
        var target = EntityType.ZOMBIE.create(level);
        target.setNoAi(true);
        target.moveTo(player.position().add(0, 0, 2));
        level.addFreshEntity(target);
        float health = target.getHealth();
        player.attack(target);
        require(target.getHealth() < health, "The crafted sabre must damage a real target through player attack");
        target.discard();
        ArienteWorld.setup.getLogger().info("PHASE 2 NATIVE CITY: {} captured, withdrew {}, crafted/used Energy Sabre", capturedCity.getName(), capturedLoot);
    }

    private static void returnHome(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        observing = false;
        BlockPos arrival = PortalGenerator.getArrival(level, entrance);
        player.teleportTo(level, arrival.getX() + .5, arrival.getY(), arrival.getZ() + .5, 0, 0);
        WarperTile returnWarper = (WarperTile) level.getBlockEntity(new BlockPos(
                (arrival.getX() & ~15) + 7, arrival.getY() - 1, (arrival.getZ() & ~15) + 7));
        returnWarper.warp(player);
        require(player.level().dimension().equals(Level.OVERWORLD) && player.blockPosition().equals(entrance.above())
                        && player.serverLevel().noCollision(player), "The survival player must return safely to the original entrance");
        int width = capturedCity.getPlan().getPlan().get(0).length();
        int depth = capturedCity.getPlan().getPlan().size();
        for (int x = capturedCity.getCenter().x - width / 2; x < capturedCity.getCenter().x - width / 2 + width; x++) {
            for (int z = capturedCity.getCenter().z - depth / 2; z < capturedCity.getCenter().z - depth / 2 + depth; z++) {
                level.setChunkForced(x, z, false);
            }
        }
        returning = true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
