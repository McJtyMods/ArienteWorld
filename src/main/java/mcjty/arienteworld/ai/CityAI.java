package mcjty.arienteworld.ai;

import net.minecraft.world.entity.EntitySpawnReason;

import net.minecraft.core.UUIDUtil;
import mcjty.ariente.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import mcjty.ariente.Ariente;
import mcjty.ariente.blocks.storagecube.StorageCubeTile;
import mcjty.ariente.items.ItemData;
import mcjty.ariente.varia.WeightedRandom;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityPlan;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.cities.Loot;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.StructureLoot;
import mcjty.arienteworld.config.AIConfiguration;
import mcjty.arienteworld.dimension.CityFeature;
import mcjty.arienteworld.dimension.EditMode;
import mcjty.arienteworld.entities.ArienteMobs;
import mcjty.arienteworld.entities.boss.CoreGuardianEntity;
import mcjty.arienteworld.entities.drone.DroneEntity;
import mcjty.arienteworld.entities.drone.DroneMoveControl;
import mcjty.arienteworld.entities.drone.IDrone;
import mcjty.arienteworld.entities.drone.ISentinel;
import mcjty.arienteworld.entities.drone.SentinelDroneEntity;
import mcjty.arienteworld.entities.soldier.SoldierBehaviourType;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import mcjty.arienteworld.setup.Registration;
import mcjty.hologui.api.IHoloGuiEntity;
import mcjty.lib.tileentity.GenericTileEntity;
import mcjty.lib.varia.OrientationTools;
import mcjty.lib.varia.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static mcjty.ariente.setup.Registration.AICORE;
import static mcjty.ariente.setup.Registration.FLUX_BEAM;
import static mcjty.ariente.setup.Registration.KEY_CARD;
import static mcjty.ariente.setup.Registration.POWERSUIT_CHEST;
import static mcjty.ariente.setup.Registration.POWERSUIT_FEET;
import static mcjty.ariente.setup.Registration.POWERSUIT_HEAD;
import static mcjty.ariente.setup.Registration.POWERSUIT_LEGS;

public class CityAI implements ICityAI {

    private UUID guardianId;
    private boolean guardianDead;
    private ChunkPos guardianChunk;
    private long guardianMissingSince = -1;

    private int levitator = -1;
    private int levitatorTicker = 20;
    private BlockPos levitatorPrevPos;

    private final ChunkPos center;
    private final ServerLevel level;
    private final City city;
    private Integer cityHeight;
    private boolean initialized = false;

    private CityAISettings settings = null;

    private final Set<Long> initializedChunks = new HashSet<>();
    private final Set<Long> scannedChunks = new HashSet<>();
    private final Map<Integer, Integer> signalChannels = new HashMap<>();
    private boolean sentinelsInitialized;
    private Set<BlockPos> aiCores = new HashSet<>();
    private final Set<BlockPos> pendingCores = new HashSet<>();
    private Set<BlockPos> forceFields = new HashSet<>();
    private Set<BlockPos> alarms = new HashSet<>();
    private Set<BlockPos> negariteGenerators = new HashSet<>();
    private Set<BlockPos> posiriteGenerators = new HashSet<>();
    private Map<BlockPos, Direction> guardPositions = new HashMap<>();
    private Map<BlockPos, Direction> soldierPositions = new HashMap<>();
    private Map<BlockPos, Direction> masterSoldierPositions = new HashMap<>();
    private AABB soldierBoundingbox = null;

    private int sentinelMovementTicks = 6;
    private int sentinelAngleOffset = 0;

    private int droneTicker = 0;

    private String keyId;
    private String storageKeyId;
    private String forcefieldId;

    private int soldierTicker = 0;

    private int onAlert = 0;
    private boolean highAlert = false;
    private Map<UUID, BlockPos> watchingPlayers = new HashMap<>();  // Players we are watching as well as their last known position

    private static Random random = new Random();

    public CityAI(ServerLevel level, ChunkPos center) {
        this.level = level;
        this.center = center;
        this.city = CityTools.getCity(level.getSeed(), center);
    }

    public ChunkPos getCenter() {
        return center;
    }

    public boolean isLargeCube() {
        return city != null && city.getPlan().getName().equals("city5_1");
    }

    private boolean isUndergroundDungeon() {
        return city != null && city.getPlan().getName().equals("city3_1");
    }

    public boolean isCoreProtected() {
        return (isLargeCube() || isUndergroundDungeon()) && !guardianDead;
    }

    /** Run from a loaded core, without requiring the entire nine-chunk-wide complex to tick. */

    public void ensureGuardian(BlockPos core) {
        if (!isCoreProtected() || EditMode.isEditing(level, center)) {
            return;
        }
        // Dungeon-local (8,14,40) clears the western hall's railing beside the lower core chamber.
        // Either core owns the same encounter.
        BlockPos home = isUndergroundDungeon()
                ? new BlockPos(center.getMinBlockX() - 24, CityTools.getCityHeight(level, city) - 22, center.getMinBlockZ() + 8)
                : core.above(8);
        if (guardianId != null) {
            Entity existing = level.getEntity(guardianId);
            if (existing != null) {
                guardianMoved(guardianId, existing.chunkPosition());
                guardianMissingSince = -1;
                return;
            }
            ChunkPos lastChunk = guardianChunk != null ? guardianChunk : ChunkPos.containing(home);
            // A full terrain chunk does not imply that its asynchronously loaded entities are available.
            if (level.getChunkSource().getChunkNow(lastChunk.x(), lastChunk.z()) == null
                    || !level.areEntitiesLoaded(lastChunk.pack())) {
                guardianMissingSince = -1;
                return;
            }
            if (guardianMissingSince < 0) {
                guardianMissingSince = level.getGameTime();
                return;
            }
            if (level.getGameTime() - guardianMissingSince < 100) {
                return;
            }
        }
        if (!level.hasChunkAt(home)) {
            return;
        }
        CoreGuardianEntity guardian = Registration.ENTITY_CORE_GUARDIAN.get().create(level, EntitySpawnReason.TRIGGERED);
        if (guardian == null) {
            return;
        }
        // Reuse the encounter identity so a delayed restoration cannot create a second live guardian.
        if (guardianId != null) {
            guardian.setUUID(guardianId);
        }
        guardian.bindToCity(center, home);
        if (level.noCollision(guardian) && level.addFreshEntity(guardian)) {
            guardianId = guardian.getUUID();
            guardianChunk = guardian.chunkPosition();
            guardianMissingSince = -1;
            CityAISystem.getCityAISystem(level).saveSystem();
        }
    }

    public void guardianMoved(UUID id, ChunkPos chunk) {
        if (id.equals(guardianId) && !chunk.equals(guardianChunk)) {
            guardianChunk = chunk;
            CityAISystem.getCityAISystem(level).saveSystem();
        }
    }

    public boolean isGuardianDefeated() {
        return guardianDead;
    }

    public void guardianDefeated(UUID id) {
        if (id.equals(guardianId)) {
            guardianDead = true;
            CityAISystem.getCityAISystem(level).saveSystem();
        }
    }

    private void initializeCubePatrol(Mob mob, int floor) {
        if (mob.getPersistentData().getBooleanOr("arienteworldCubePatrol", false)) {
            return;
        }
        double dx = Math.abs(mob.getX() - center.getMiddleBlockX());
        double dz = Math.abs(mob.getZ() - center.getMiddleBlockZ());
        boolean inPatrolArea = mob.getY() >= floor + 33 && mob.getY() <= floor + 56
                && dx <= 52 && dz <= 52 && (dx <= 5 || dz <= 5);
        if (!inPatrolArea) {
            // Older cube defenders were directed below solid floors. Relocate them once, preserving the entity.
            for (int attempt = 0; attempt < 24; attempt++) {
                Vec3 destination = new Vec3(center.getMiddleBlockX() + 0.5 + random.nextInt(7) - 3,
                        floor + 36 + random.nextInt(16), center.getMiddleBlockZ() + 0.5 + random.nextInt(7) - 3);
                if (level.hasChunkAt(BlockPos.containing(destination))
                        && level.noCollision(mob, mob.getBoundingBox().move(destination.subtract(mob.position())))) {
                    mob.setPos(destination);
                    mob.setDeltaMovement(Vec3.ZERO);
                    mob.getNavigation().stop();
                    inPatrolArea = true;
                    break;
                }
            }
        }
        if (inPatrolArea) {
            mob.getPersistentData().putBoolean("arienteworldCubePatrol", true);
        }
    }

    @Nullable
    public BlockPos requestCubePatrolPosition(Mob mob) {
        int cx = center.getMiddleBlockX();
        int cz = center.getMiddleBlockZ();
        int floor = CityTools.getCityHeight(level, city);
        initializeCubePatrol(mob, floor);
        // The upper cross-shaped interior is open; the surrounding wings contain stacked rooms.
        for (int attempt = 0; attempt < 24; attempt++) {
            int along = random.nextInt(97) - 48;
            int across = random.nextInt(7) - 3;
            boolean eastWest = random.nextBoolean();
            BlockPos pos = new BlockPos(cx + (eastWest ? along : across), floor + 36 + random.nextInt(18),
                    cz + (eastWest ? across : along));
            if (DroneMoveControl.canFlyTo(mob, Vec3.atBottomCenterOf(pos))) {
                return pos;
            }
        }
        // Route around a temporary obstruction to return to the open cross.
        return new BlockPos(cx, floor + 42, cz);
    }

    private int getLowestHeight(int chunkX, int chunkZ) {
        if (cityHeight == null) {
            cityHeight = CityTools.getCityHeight(level, city);
        }
        return cityHeight - CityTools.getCellarHeight(level.getSeed(), city, chunkX, chunkZ);
    }

    private void setup(Level world) {
        if (!initialized) {
            initialize(world);
        }
        for (ChunkPos chunk : getCityChunks()) {
            if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null) {
                scannedChunks.remove(chunk.pack());
            } else {
                initializeChunk(chunk);
            }
        }
    }

    private List<ChunkPos> getCityChunks() {
        List<String> pattern = city.getPlan().getPlan();
        int width = pattern.get(0).length();
        int depth = pattern.size();
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = center.x() - width / 2; x < center.x() - width / 2 + width; x++) {
            for (int z = center.z() - depth / 2; z < center.z() - depth / 2 + depth; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    public boolean initializeChunk(ChunkPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        if (chunk == null || EditMode.isEditing(level, center)) {
            return false;
        }
        if (scannedChunks.contains(pos.pack())) {
            return true;
        }
        if (!initialized) {
            initialize(level);
        }
        findEquipment(chunk);
        return true;
    }

    public void chunkUnloaded(ChunkPos pos) {
        scannedChunks.remove(pos.pack());
    }

    /** Loaded outer rooms must keep defending themselves when the core's chunk is outside view distance. */

    public boolean tickLoadedSections() {
        IAICoreTile core = findFirstValidAICore(level);
        if (!initialized || EditMode.isEditing(level, center)
                || core != null && level.getChunkSource().isPositionTicking(ChunkPos.containing(core.getCorePos()).pack())
                || scannedChunks.stream().noneMatch(chunk -> level.getChunkSource()
                .getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)) != null)) {
            return false;
        }
        setup(level);
        if (isDead(level)) {
            return false;
        }
        handleAI(level);
        return true;
    }

    @Nullable
    private BlockEntity getLoadedBlockEntity(BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }

    @Override
    public void breakAICore(Level world, BlockPos pos) {
        if (EditMode.isEditing(world, center)) {
            return;
        }
        setup(world);
        if (initialized && !hasValidCoreExcept(world, pos)) {
            // There are no other valid AI cores. Spawn an item for the player
            // with the right security key
            ItemStack stack = new ItemStack(KEY_CARD.get());
            Ariente.arienteSystem.addSecurity(stack, getStorageKeyId());
            Ariente.arienteSystem.setDescription(stack, "City: " + city.getName());
            ItemEntity entityitem = new ItemEntity(world, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack);
            entityitem.setDefaultPickUpDelay();
            world.addFreshEntity(entityitem);
            setAlarmType(world, AlarmType.DEAD);
        }
        aiCores.remove(pos);
        pendingCores.remove(pos);
    }

    // Return true if we potentially have to save the city system state
    @Override
    public boolean tick(BlockEntity tile) {
        if (EditMode.isEditing(tile.getLevel(), center)) {
            return false;
        }
        if (tile instanceof IAICoreTile core) {
            ensureGuardian(core.getCorePos());
        }
        setup(tile.getLevel());
        // Only the first currently loaded core ticks the city; the level lifecycle handles coreless loaded sections.
        IAICoreTile core = findFirstValidAICore(tile.getLevel());
        if (core == null || !tile.getBlockPos().equals(core.getCorePos())) {
            return false;
        }
        handleAI(tile.getLevel());
        return true;
    }

    @Override
    public boolean isDead(Level world) {
        return !hasValidCoreExcept(world, null);
    }

    /** Unlike isDead(), patrol eligibility requires evidence of a real core, even before city initialization. */

    public boolean hasOperationalCore() {
        boolean changed = aiCores.removeIf(pos -> level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                && !(level.getBlockEntity(pos) instanceof IAICoreTile));
        if (aiCores.isEmpty()) {
            List<String> pattern = city.getPlan().getPlan();
            int width = pattern.get(0).length();
            int depth = pattern.size();
            for (int x = center.x() - width / 2; x < center.x() - width / 2 + width; x++) {
                for (int z = center.z() - depth / 2; z < center.z() - depth / 2 + depth; z++) {
                    var chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk == null) {
                        continue;
                    }
                    int bottom = getLowestHeight(x, z);
                    int top = bottom + CityTools.getBuildingParts(level.getSeed(), city, x, z).stream()
                            .mapToInt(BuildingPart::getSliceCount).sum();
                    for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                        if (pos.getY() >= bottom && pos.getY() < top && level.getBlockEntity(pos) instanceof IAICoreTile) {
                            changed |= aiCores.add(pos.immutable());
                        }
                    }
                }
            }
        }
        if (changed) {
            CityAISystem.getCityAISystem(level).saveSystem();
        }
        // Remember verified cores in unloaded chunks; breaking a core removes that record immediately.
        return !aiCores.isEmpty();
    }

    // Check if there is still a valid AI core except for the input parameter
    public boolean hasValidCoreExcept(Level world, @Nullable BlockPos exclude) {
        if (!initialized) {
            return true;        // If not initialized we assume this city is alive
        }
        Set<BlockPos> cores = new HashSet<>(aiCores);
        cores.addAll(pendingCores);
        for (BlockPos pos : cores) {
            if (!pos.equals(exclude)) {
                if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                    return true;
                }
                BlockEntity te = getLoadedBlockEntity(pos);
                if (te instanceof IAICoreTile) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private IAICoreTile findFirstValidAICore(Level world) {
        IAICoreTile firstLoaded = null;
        for (BlockPos pos : aiCores) {
            BlockEntity te = getLoadedBlockEntity(pos);
            if (te instanceof IAICoreTile core) {
                if (level.getChunkSource().isPositionTicking(ChunkPos.containing(pos).pack())) {
                    return core;
                }
                if (firstLoaded == null) {
                    firstLoaded = core;
                }
            }
        }
        return firstLoaded;
    }

    private AABB getSoldierBoundingbox() {
        if (soldierBoundingbox == null) {
            for (Map.Entry<BlockPos, Direction> entry : soldierPositions.entrySet()) {
                if (soldierBoundingbox == null) {
                    soldierBoundingbox = new AABB(entry.getKey());
                } else {
                    soldierBoundingbox = soldierBoundingbox.minmax(new AABB(entry.getKey()));
                }
            }
            for (Map.Entry<BlockPos, Direction> entry : masterSoldierPositions.entrySet()) {
                if (soldierBoundingbox == null) {
                    soldierBoundingbox = new AABB(entry.getKey());
                } else {
                    soldierBoundingbox = soldierBoundingbox.minmax(new AABB(entry.getKey()));
                }
            }
            for (Map.Entry<BlockPos, Direction> entry : guardPositions.entrySet()) {
                if (soldierBoundingbox == null) {
                    soldierBoundingbox = new AABB(entry.getKey());
                } else {
                    soldierBoundingbox = soldierBoundingbox.minmax(new AABB(entry.getKey()));
                }
            }
            if (soldierBoundingbox != null) {
                soldierBoundingbox = soldierBoundingbox.inflate(10);
            }
        }
        return soldierBoundingbox;
    }

    // Lazy way to count all entities
    private class EntityInfo {
        private int countSoldier = -1;
        private int countDrone = -1;
        private int countSentinel = -1;

        public int getCountSoldier(Level world) {
            if (countSoldier == -1) {
                countEntities(world);
            }
            return countSoldier;
        }

        public int getCountDrone(Level world) {
            if (countDrone == -1) {
                countEntities(world);
            }
            return countDrone;
        }

        public int getCountSentinel(Level world) {
            if (countSentinel == -1) {
                countEntities(world);
            }
            return countSentinel;
        }

        private void countEntities(Level world) {
            CityPlan plan = city.getPlan();
            List<String> pattern = plan.getPlan();
            int dimX = pattern.get(0).length() * 16 * 2;
            int dimZ = pattern.size() * 16 * 2;

            BlockPos ctr = new BlockPos(center.x() * 16 + 8, 50, center.z() * 16 + 8);

            // To avoid creating the list we use the filter as a consumer here to increment our counter
            AtomicInteger soldiers = new AtomicInteger(0);
            AtomicInteger drones = new AtomicInteger(0);
            AtomicInteger sentinels = new AtomicInteger(0);
            AABB boundingbox = getSoldierBoundingbox();
            world.getEntitiesOfClass(LivingEntity.class, new AABB(ctr).inflate(dimX, 200, dimZ),
                    entity -> {
                        if (!entity.isAlive()) {
                            return false;
                        }
                        if (entity instanceof ISoldier soldier && soldier.isReinforcementFor(center)) {
                            if (boundingbox != null) {
                                if (boundingbox.contains(entity.position())) {
                                    soldiers.incrementAndGet();
                                }
                            }
                        }
                        if (entity instanceof ISentinel sentinel && center.equals(sentinel.getCityCenter())) {
                            sentinels.incrementAndGet();
                        }
                        if (entity instanceof IDrone drone && center.equals(drone.getCityCenter())) {
                            drones.incrementAndGet();
                        }
                        return false;
                    });
            countSoldier = soldiers.intValue();
            countDrone = drones.intValue();
            countSentinel = sentinels.intValue();
        }
    }

    private void handleAI(Level world) {
        if (!sentinelsInitialized && level.getChunkSource().getChunkNow(center.x(), center.z()) != null) {
            initSentinels(world);
            sentinelsInitialized = true;
        }
        EntityInfo info = new EntityInfo();

        handlePower(world);
        handleSentinels(world, info);
        handleAlert(world);
        handleDrones(world, info);
        handleSoldiers(world, info);
        handleFluxLevitators(world);
    }

    private void handleFluxLevitators(Level world) {
        // First check if there is a levitator and if it is near its destination
        if (levitator != -1) {
            Entity entity = world.getEntity(levitator);
            if (!(entity instanceof IFluxLevitatorEntity)) {
                levitator = -1;
            } else {
                IFluxLevitatorEntity levitatorEntity = (IFluxLevitatorEntity) entity;
                BlockPos desiredDestination = levitatorEntity.getDesiredDestination();
                if (desiredDestination != null) {
                    double distanceSq = entity.blockPosition().distSqr(desiredDestination);
                    if (distanceSq < 5 * 5) {
                        // Arrived
                        dismountAndKill(levitatorEntity);
                    } else {
                        // Check if we actually moved since last time. If not we let the soldier get out and remove the flux levitator
                        if (levitatorPrevPos != null) {
                            distanceSq = entity.blockPosition().distSqr(levitatorPrevPos);
                            if (distanceSq <= 0.1) {
                                dismountAndKill(levitatorEntity);
                            }
                        }
                    }
                    levitatorPrevPos = entity.blockPosition();
                } else {
                    dismountAndKill(levitatorEntity);
                }
            }
            return;
        }

        levitatorTicker--;
        if (levitatorTicker <= 0) {
            levitatorTicker = 80;
            if (levitator != -1) {
                Entity entity = world.getEntity(levitator);
                if (entity != null) {
                    for (Entity passenger : entity.getPassengers()) {
                        if (!(passenger instanceof IHoloGuiEntity) && !(passenger instanceof Player)) {
                            passenger.discard();
                        }
                    }

                    entity.discard();
                }
                levitator = -1;
            } else {
                LevitatorPath path = findValidBeam(world);
                if (path != null) {
                    List<? extends ISoldier> entities = world.getEntitiesOfClass(SoldierEntity.class, new AABB(path.end).inflate(15));
                    if (entities.size() > 2) {
                        // Too many already
                        return;
                    }

                    BlockPos pos = path.start;
                    BlockState state = world.getBlockState(pos);
                    RailShape dir = Ariente.arienteSystem.getBeamDirection(state);
                    double d0 = 0.0D;

                    if (dir.isSlope()) {
                        d0 = 0.5D;
                    }

                    Entity entity = Ariente.arienteSystem.createFluxLevitatorEntity(world, pos.getX() + 0.5D, pos.getY() + 0.0625D + d0, pos.getZ() + 0.5D);
                    IFluxLevitatorEntity levitator = (IFluxLevitatorEntity) entity;
                    if (path.direction == Direction.SOUTH || path.direction == Direction.EAST) {
                        levitator.changeSpeed(-50);
                    } else {
                        levitator.changeSpeed(50);
                    }
                    levitator.setDesiredDestination(path.end);
                    world.addFreshEntity(entity);
                    this.levitator = entity.getId();

                    LivingEntity soldier = ArienteMobs.createSoldier(world, pos, path.direction, center, SoldierBehaviourType.SOLDIER_REINFORCEMENT, false);
                    soldier.startRiding(entity);
                }
            }
        }
    }

    private void dismountAndKill(IFluxLevitatorEntity levitatorEntity) {
        Entity ent = (Entity) levitatorEntity;
        for (Entity passenger : ent.getPassengers()) {
            if (!(passenger instanceof IHoloGuiEntity) && !(passenger instanceof Player)) {
                passenger.stopRiding();
            }
        }
        ent.discard();
        levitator = -1;
    }

    private BlockPos isValidBeam(Level world, ChunkPos c, Direction direction, int minOffset, int maxOffset) {
        for (int i = minOffset; i <= maxOffset; i++) {
            BlockPos pos = new BlockPos(c.x() * 16 + 8 + direction.getStepX() * i, 32, (c.z() * 16) + 8 + direction.getStepZ() * i);
            if (!world.hasChunkAt(pos)) {
                return null;
            }
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() == FLUX_BEAM.get()) {
                return pos;
            }
        }
        return null;
    }

    private static class LevitatorPath {
        private final Direction direction;
        private final BlockPos start;
        private final BlockPos end;

        public LevitatorPath(Direction direction, BlockPos start, BlockPos end) {
            this.direction = direction;
            this.start = start;
            this.end = end;
        }
    }

    private boolean isValidPath(Level world, BlockPos start, BlockPos end, Direction facing) {
        BlockPos p = start;
        while (!end.equals(p)) {
            if (!world.hasChunkAt(p)) {
                return false;
            }
            BlockState state = world.getBlockState(p);
            if (state.getBlock() != FLUX_BEAM.get()) {
                return false;
            }
            p = p.relative(facing.getOpposite());
        }
        List<? extends IFluxLevitatorEntity> entities = Ariente.arienteSystem.getLevitatorsWithinAABB(world, new AABB(start).minmax(new AABB(end)));
        return entities.isEmpty();
    }

    @Nullable
    private LevitatorPath findValidBeam(Level world) {
        CityAISystem system = CityAISystem.getCityAISystem(level);
        List<LevitatorPath> positions = new ArrayList<>();
        for (Direction facing : OrientationTools.HORIZONTAL_DIRECTION_VALUES) {
            ChunkPos otherCoord = new ChunkPos(center.x() + facing.getStepX() * 16,
                    center.z() + facing.getStepZ() * 16);
            if (!CityTools.isCityCenter(level.getSeed(), otherCoord.x(), otherCoord.z())) {
                continue;
            }
            CityAI otherCity = system.getCityAI(otherCoord);
            if (otherCity != null && otherCity.hasOperationalCore()) {
                BlockPos end = isValidBeam(world, center, facing, 1, 40);
                if (end != null) {
                    BlockPos start = isValidBeam(world, center, facing, 100, 120);
                    if (start != null) {
                        if (isValidPath(world, start, end, facing)) {
                            positions.add(new LevitatorPath(facing, start, end));
                        }
                    }
                }
            }
        }
        if (positions.isEmpty()) {
            return null;
        }
        if (positions.size() == 1) {
            return positions.get(0);
        }
        return positions.get(random.nextInt(positions.size()));
    }

    private void handleAlert(Level world) {
        // Handle alert mode
        if (onAlert > 0) {
            onAlert--;
        }

        if (onAlert > 0) {
            // Turn on forcefields if present
            for (BlockPos pos : forceFields) {
                BlockEntity te = getLoadedBlockEntity(pos);
                if (te instanceof IForceFieldTile) {
                    GenericTileEntity forcefield = (GenericTileEntity) te;
                    if (forcefield.getRSMode() != RedstoneMode.REDSTONE_IGNORED) {
                        forcefield.setRSMode(RedstoneMode.REDSTONE_IGNORED);
                    }
                }
            }
        } else {
            setAlarmType(world, AlarmType.SAFE);
            highAlert = false;
            watchingPlayers.clear();
            // Turn off forcefields if present
            for (BlockPos pos : forceFields) {
                BlockEntity te = getLoadedBlockEntity(pos);
                if (te instanceof IForceFieldTile) {
                    GenericTileEntity forcefield = (GenericTileEntity) te;
                    if (forcefield.getRSMode() != RedstoneMode.REDSTONE_ONREQUIRED) {
                        forcefield.setRSMode(RedstoneMode.REDSTONE_ONREQUIRED);
                    }
                }
            }
        }
    }

    @Nullable
    private BlockPos findRandomPlayer(Level world) {
        List<BlockPos> players = new ArrayList<>();
        for (Map.Entry<UUID, BlockPos> entry : watchingPlayers.entrySet()) {
            UUID uuid = entry.getKey();
            Player player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null && player.level().dimension().equals(world.dimension())) {
                BlockPos pos = entry.getValue();    // Use the last known position
                double sq = pos.distSqr(new BlockPos(center.x() * 16 + 8, 50, center.z() * 16 + 8));
                if (sq < 80 * 80) {
                    players.add(pos);
                }
            }
        }
        if (players.isEmpty()) {
            return null;
        }
        return players.get(random.nextInt(players.size()));
    }

    private void handleDrones(Level world, EntityInfo info) {
        if (onAlert > 0) {
            droneTicker--;
            if (droneTicker > 0) {
                return;
            }
            droneTicker = 10;

            CityPlan plan = city.getPlan();
            int droneHeight = plan.getDroneHeightOffset() + getLowestHeight(center.x(), center.z());

            int desiredMinimumCount = 0;
            int newWaveMaximum = 0;
            if (watchingPlayers.size() > 2) {
                desiredMinimumCount = plan.getDronesMinimumN();
                newWaveMaximum = plan.getDronesWaveMaxN();
            } else if (watchingPlayers.size() > 1) {
                desiredMinimumCount = plan.getDronesMinimum2();
                newWaveMaximum = plan.getDronesWaveMax2();
            } else {
                desiredMinimumCount = plan.getDronesMinimum1();
                newWaveMaximum = plan.getDronesWaveMax1();
            }

            int cnt = info.getCountDrone(world);
            while (cnt < desiredMinimumCount) {
                spawnDrone(world, droneHeight);
                cnt++;
            }

            if (cnt < newWaveMaximum && random.nextFloat() < 0.1f) {
                // Randomly spawn a new wave of drones

                while (cnt < newWaveMaximum) {
                    spawnDrone(world, droneHeight);
                    cnt++;
                }
            }
        }
    }

    private void handleSoldiers(Level world, EntityInfo info) {
        if (onAlert > 0) {
            soldierTicker--;
            if (soldierTicker > 0) {
                return;
            }
            soldierTicker = 10;

            CityPlan plan = city.getPlan();

            int desiredMinimumCount = 0;
            int newWaveMaximum = 0;
            if (watchingPlayers.size() > 2) {
                desiredMinimumCount = plan.getSoldiersMinimumN();
                newWaveMaximum = plan.getSoldiersWaveMaxN();
            } else if (watchingPlayers.size() > 1) {
                desiredMinimumCount = plan.getSoldiersMinimum2();
                newWaveMaximum = plan.getSoldiersWaveMax2();
            } else {
                desiredMinimumCount = plan.getSoldiersMinimum1();
                newWaveMaximum = plan.getSoldiersWaveMax1();
            }
            if (highAlert) {
                desiredMinimumCount *= 2;
                newWaveMaximum *= 2;
            }

            desiredMinimumCount = plan.scaleSoldierReinforcements(desiredMinimumCount);
            newWaveMaximum = plan.scaleSoldierReinforcements(newWaveMaximum);

            int cnt = info.getCountSoldier(world);
            while (cnt < desiredMinimumCount) {
                spawnSoldier(world);
                cnt++;
            }

            if (cnt < newWaveMaximum && random.nextFloat() < 0.2f) {
                // Randomly spawn a new wave of drones

                while (cnt < newWaveMaximum) {
                    spawnSoldier(world);
                    cnt++;
                }
            }
        }
    }

    private void spawnSoldier(Level world) {
        List<BlockPos> loadedPositions = soldierPositions.keySet().stream()
                .filter(pos -> level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null).toList();
        if (loadedPositions.isEmpty()) {
            return;
        }

        // Too few soldiers. Spawn a new one
        CityPlan plan = city.getPlan();

        BlockPos pos;
        // Avoid too close to player if possible
        int avoidNearby = 3;
        do {
            pos = loadedPositions.get(random.nextInt(loadedPositions.size()));
            Player closestPlayer = world.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false);
            if (closestPlayer == null) {
                avoidNearby = 0;
            } else {
                avoidNearby--;
            }
        } while (avoidNearby > 0);

        Direction facing = soldierPositions.get(pos);
        LivingEntity entity = ArienteMobs.createSoldier(world, pos, facing, center, SoldierBehaviourType.SOLDIER_REINFORCEMENT,
                random.nextDouble() < plan.getMasterChance());

        if (random.nextFloat() < plan.getPowerArmorChance()) {
            entity.setItemSlot(EquipmentSlot.HEAD, createNiceHelmet());
            entity.setItemSlot(EquipmentSlot.FEET, createNiceBoots());
            entity.setItemSlot(EquipmentSlot.CHEST, createNiceChestplate(plan));
            entity.setItemSlot(EquipmentSlot.LEGS, createNiceLegs());
        }
    }

    private ItemStack createNiceHelmet() {
        ItemStack helmet = new ItemStack(POWERSUIT_HEAD.get());
        CompoundTag compound = new CompoundTag();
        compound.putBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
        compound.putBoolean(ArmorUpgradeType.ARMOR.getWorkingKey(), compound.getBooleanOr(ArmorUpgradeType.ARMOR.getModuleKey(), false));
        ItemData.set(helmet, compound);
        return helmet;
    }

    private ItemStack createNiceBoots() {
        ItemStack helmet = new ItemStack(POWERSUIT_FEET.get());
        CompoundTag compound = new CompoundTag();
        compound.putBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
        compound.putBoolean(ArmorUpgradeType.ARMOR.getWorkingKey(), compound.getBooleanOr(ArmorUpgradeType.ARMOR.getModuleKey(), false));
        ItemData.set(helmet, compound);
        return helmet;
    }

    private ItemStack createNiceChestplate(CityPlan plan) {
        ItemStack helmet = new ItemStack(POWERSUIT_CHEST.get());
        CompoundTag compound = new CompoundTag();
        compound.putBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
        compound.putBoolean(ArmorUpgradeType.ARMOR.getWorkingKey(), compound.getBooleanOr(ArmorUpgradeType.ARMOR.getModuleKey(), false));
        if (random.nextFloat() < plan.getForcefieldChance()) {
            compound.putBoolean(ArmorUpgradeType.ENERGY.getModuleKey(), true);
            compound.putBoolean(ArmorUpgradeType.FORCEFIELD.getModuleKey(), true);
            compound.putBoolean(ArmorUpgradeType.FORCEFIELD.getWorkingKey(), compound.getBooleanOr(ArmorUpgradeType.FORCEFIELD.getModuleKey(), false));
        }
        ItemData.set(helmet, compound);
        return helmet;
    }

    private ItemStack createNiceLegs() {
        ItemStack helmet = new ItemStack(POWERSUIT_LEGS.get());
        CompoundTag compound = new CompoundTag();
        compound.putBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
        compound.putBoolean(ArmorUpgradeType.ARMOR.getWorkingKey(), compound.getBooleanOr(ArmorUpgradeType.ARMOR.getModuleKey(), false));
        ItemData.set(helmet, compound);
        return helmet;
    }

    private void spawnDrone(Level world, int height) {
        if (level.getChunkSource().getChunkNow(center.x(), center.z()) == null) {
            return;
        }
        // Too few drones. Spawn a new one
        LivingEntity entity = DroneEntity.create(world, center);
        int cx = center.x() * 16 + 8;
        int cy = isLargeCube() ? CityTools.getCityHeight(level, city) + 36 : height;
        int cz = center.z() * 16 + 8;
        entity.setPos(cx, cy, cz);
        world.addFreshEntity(entity);
    }

    private void handleSentinels(Level world, EntityInfo info) {
        // Sentinel movement
        sentinelMovementTicks--;
        if (sentinelMovementTicks <= 0) {
            sentinelMovementTicks = 6;
            sentinelAngleOffset++;
            if (sentinelAngleOffset >= 12) {
                sentinelAngleOffset = 0;
            }
        }

        // Small chance to revive sentinels if they are missing. Only revive if all are missing
        if (random.nextFloat() < .1f) {
            if (info.getCountSentinel(world) == 0) {
                CityPlan plan = city.getPlan();
                int droneHeight = plan.getDroneHeightOffset() + getLowestHeight(center.x(), center.z());
                for (int i = 0; i < settings.getNumSentinels(); i++) {
                    createSentinel(world, i, droneHeight);
                }
            }
        }
    }

    private void handlePower(Level world) {
        // Handle power
        for (BlockPos pos : negariteGenerators) {
            BlockEntity te = getLoadedBlockEntity(pos);
            if (te instanceof IGenerator) {
                ((IGenerator) te).feedDust(1);
            }
        }
        for (BlockPos pos : posiriteGenerators) {
            BlockEntity te = getLoadedBlockEntity(pos);
            if (te instanceof IGenerator) {
                ((IGenerator) te).feedDust(1);
            }
        }
    }

    @Override
    public BlockPos requestNewSoldierPosition(Level world, LivingEntity currentTarget) {
        // Sometimes we let a solider pick a different location independent of target
        if (random.nextFloat() > .6) {
            return null;
        }

        BlockPos target;
        if (currentTarget != null) {
            target = currentTarget.blockPosition();
        } else {
            target = findRandomPlayer(world);
        }
        if (target != null) {
            float angle = random.nextFloat() * 360.0f;
            float distance = 4;
            int cx = (int) (target.getX() + .5 + Math.cos(angle) * distance);
            int cz = (int) (target.getZ() + .5 + Math.sin(angle) * distance);
            return new BlockPos(cx, target.getY(), cz);
        }
        return null;
    }

    @Override
    public BlockPos requestNewDronePosition(Level world, LivingEntity currentTarget) {
        BlockPos target;
        if (currentTarget != null) {
            target = currentTarget.blockPosition();
        } else {
            target = findRandomPlayer(world);
        }
        if (target != null) {
            float angle = random.nextFloat() * 360.0f;
            float distance = 15;
            int cx = (int) (target.getX() + .5 + Math.cos(angle) * distance);
            int cz = (int) (target.getZ() + .5 + Math.sin(angle) * distance);
            return new BlockPos(cx, target.getY() + 3, cz);
        }
        return null;
    }

    @Override
    public BlockPos requestNewSentinelPosition(Level world, int sentinelId) {
        if (aiCores.isEmpty() || settings == null || settings.getNumSentinels() == 0) {
            return null;
        }

        CityPlan plan = city.getPlan();
        int droneHeight = plan.getSentinelRelHeight() + getLowestHeight(center.x(), center.z());

        int angleI = (sentinelAngleOffset + sentinelId * 12 / settings.getNumSentinels()) % 12;
        int cx = center.x() * 16 + 8;
        int cy = droneHeight;
        int cz = center.z() * 16 + 8;

        double angle = angleI * Math.PI * 2 / 12;
        float distance = plan.getSentinelDistance();
        cx = (int) (cx + Math.cos(angle) * distance);
        cz = (int) (cz + Math.sin(angle) * distance);
        return new BlockPos(cx, cy, cz);
    }

    public void pacify(Level world) {
        setAlarmType(world, AlarmType.SAFE);
        onAlert = 0;
        highAlert = false;
    }

    @Override
    public void playerSpotted(Player player) {
        // The scramble module helps protect against player allertness
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.getItem() == POWERSUIT_HEAD.get()) {
            if (Ariente.arienteSystem.hasWorkingUpgrade(helmet, ArmorUpgradeType.SCRAMBLE)) {
                return;
            }
        }

        alertCity(player);
    }

    @Override
    public void alertCity(Player player) {
        if (isDead(player.level())) {
            // City is dead
            return;
        }

        if (onAlert <= 0) {
            // Set alarm type in case it is not already set
            setAlarmType(player.level(), AlarmType.ALERT);
        }
        onAlert = AIConfiguration.ALERT_TIME.get();
        watchingPlayers.put(player.getUUID(), player.blockPosition());    // Register the last known position
    }

    public void highAlertMode(Player player) {
        alertCity(player);
        highAlert = true;
    }

    private void findEquipment(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        boolean firstTime = !initializedChunks.contains(pos.pack());
        int bottom = getLowestHeight(pos.x(), pos.z());
        int top = bottom + CityTools.getBuildingParts(level.getSeed(), city, pos.x(), pos.z()).stream()
                .mapToInt(BuildingPart::getSliceCount).sum();
        for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
            for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
                for (int y = bottom; y < top; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(p);
                    Block block = state.getBlock();
                    Direction facing;
                    if (block == Registration.GUARD_DUMMY.get() || block == Registration.SOLDIER_DUMMY.get()
                            || block == Registration.MASTER_SOLDIER_DUMMY.get()) {
                        facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                        if (block == Registration.GUARD_DUMMY.get()) {
                            guardPositions.put(p, facing);
                        } else if (block == Registration.MASTER_SOLDIER_DUMMY.get()) {
                            masterSoldierPositions.put(p, facing);
                        } else {
                            soldierPositions.put(p, facing);
                        }
                        level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                        if (firstTime && block != Registration.SOLDIER_DUMMY.get()) {
                            ArienteMobs.createSoldier(level, p, facing, center,
                                    block == Registration.GUARD_DUMMY.get() ? SoldierBehaviourType.SOLDIER_GUARD : SoldierBehaviourType.SOLDIER_FIGHTER,
                                    block == Registration.MASTER_SOLDIER_DUMMY.get());
                        }
                    } else {
                        BlockEntity te = CityFeature.hasBlockEntity(state) ? chunk.getBlockEntity(p) : null;
                        if (te instanceof ICityEquipment equipment) {
                            equipment.setup(this, level, firstTime);
                        }
                        if (firstTime && te instanceof ISignalChannel signal) {
                            int channel = signalChannels.computeIfAbsent(signal.getDesiredChannel(),
                                    desired -> Ariente.arienteSystem.getRedstoneChannels(level).newChannel());
                            signal.setChannel(channel);
                        }
                        if (te instanceof IAICoreTile core) {
                            aiCores.add(p);
                            core.setCityName(city.getName());
                        } else if (te instanceof IForceFieldTile field) {
                            field.setCityCenter(center);
                            forceFields.add(p);
                            if (firstTime && te instanceof GenericTileEntity tile) {
                                tile.setRSMode(onAlert > 0 ? RedstoneMode.REDSTONE_IGNORED : RedstoneMode.REDSTONE_ONREQUIRED);
                            }
                        } else if (te instanceof IAlarmTile alarm) {
                            alarms.add(p);
                            if (firstTime) {
                                alarm.setAlarmType(isDead(level) ? AlarmType.DEAD : onAlert > 0 ? AlarmType.ALERT : AlarmType.SAFE);
                            }
                        } else if (te instanceof IGenerator generator) {
                            if (generator.supportsNegarite()) {
                                negariteGenerators.add(p);
                            }
                            if (generator.supportsPosirite()) {
                                posiriteGenerators.add(p);
                            }
                            if (firstTime && te instanceof GenericTileEntity tile) {
                                Ariente.arienteSystem.fixNetworks(level, p);
                                tile.setRSMode(RedstoneMode.REDSTONE_IGNORED);
                            }
                        }
                    }
                }
            }
        }
        pendingCores.removeIf(core -> ChunkPos.containing(core).equals(pos));
        soldierBoundingbox = null;
        initializedChunks.add(pos.pack());
        scannedChunks.add(pos.pack());
        CityAISystem.getCityAISystem(level).saveSystem();
    }

    @Override
    public String getKeyId() {
        return keyId;
    }

    @Override
    public String getStorageKeyId() {
        return storageKeyId;
    }

    @Override
    public String getForcefieldId() {
        return forcefieldId;
    }

    @Override
    public String getCityName() {
        return city.getName();
    }

    @Override
    public void fillLoot(IStorageTile te) {
        if (te instanceof StorageCubeTile storage && StructureLoot.fill(storage)) {
            return;
        }
        CityPlan plan = city.getPlan();
        fillLoot(plan, te);
    }

    public static void fillLoot(CityPlan plan, IStorageTile te) {
        if (plan.getName().equals("city5_1") || plan.getName().equals("city0_0")) {
            GenericTileEntity tile = (GenericTileEntity) te;
            Random cache = new Random(((ServerLevel) tile.getLevel()).getSeed() ^ tile.getBlockPos().asLong());
            List<Loot> rewards = plan.getLoot();
            double totalWeight = rewards.stream().mapToDouble(Loot::getChance).sum();
            for (int slot = 0; slot < 4 && totalWeight > 0; slot++) {
                double choice = cache.nextDouble() * totalWeight;
                for (Loot reward : rewards) {
                    choice -= reward.getChance();
                    if (choice < 0) {
                        te.setLoot(reward.getId(), slot, reward.isBlueprint(),
                                1 + cache.nextInt(Math.max(1, reward.getMaxAmount())), reward.getMeta());
                        break;
                    }
                }
            }
            tile.setChanged();
            return;
        }
        WeightedRandom<Loot> randomLoot = plan.getRandomLoot();
        if (randomLoot.weightedList.isEmpty()) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            if (random.nextFloat() > .3f) {
                Loot l = randomLoot.getRandom();
                int amount;
                if (l.getMaxAmount() <= 1) {
                    amount = 1;
                } else {
                    amount = 1 + random.nextInt(l.getMaxAmount());
                }
                Identifier id = l.getId();
                te.setLoot(id, i, l.isBlueprint(), amount, l.getMeta());
            }
        }
        ((GenericTileEntity) te).setChanged();
    }

    private static int getMinMax(Random rnd, int min, int max) {
        if (min >= max) {
            return min;
        }
        return min + rnd.nextInt(max - min);
    }

    private void createSettings(Level world) {
        long seed = level.getSeed();
        Random rnd = new Random(seed + center.x() * 567000003533L + center.z() * 234516783139L);
        rnd.nextFloat();
        rnd.nextFloat();
        CityPlan plan = city.getPlan();
        settings = new CityAISettings();
        settings.setNumSentinels(getMinMax(rnd, plan.getMinSentinels(), plan.getMaxSentinels()));
    }

    private void initialize(Level world) {
        createSettings(world);
        keyId = Ariente.arienteSystem.getSecuritySystem(world).generateKeyId(world);
        storageKeyId = Ariente.arienteSystem.getSecuritySystem(world).generateKeyId(world);
        forcefieldId = Ariente.arienteSystem.getSecuritySystem(world).generateKeyId(world);
        // Remember authored cores before their chunks load, so taking one loaded core cannot
        // award the storage card while another core in the same complex is still unexplored.
        Palette palette = AssetRegistries.PALETTES.get(city.getPlan().getPalette());
        for (ChunkPos chunk : getCityChunks()) {
            int bottom = getLowestHeight(chunk.x(), chunk.z());
            for (BuildingPart part : CityTools.getBuildingParts(level.getSeed(), city, chunk.x(), chunk.z())) {
                for (int y = 0; y < part.getSliceCount(); y++) {
                    for (int x = 0; x < part.getXSize(); x++) {
                        for (int z = 0; z < part.getZSize(); z++) {
                            BlockState state = palette.getPalette().get(part.getC(x, y, z));
                            if (state != null && state.is(AICORE.get())) {
                                pendingCores.add(new BlockPos(chunk.getMinBlockX() + x, bottom + y, chunk.getMinBlockZ() + z));
                            }
                        }
                    }
                }
                bottom += part.getSliceCount();
            }
        }
        initialized = true;
        CityAISystem.getCityAISystem(level).saveSystem();
    }

    public void setAlarmType(Level world, AlarmType type) {
        for (BlockPos p : alarms) {
            BlockEntity te = getLoadedBlockEntity(p);
            if (te instanceof IAlarmTile) {
                ((IAlarmTile) te).setAlarmType(type);
            }
        }
    }

    private void initSentinels(Level world) {
        CityPlan plan = city.getPlan();
        int droneHeight = plan.getDroneHeightOffset() + getLowestHeight(center.x(), center.z());

        int numSentinels = settings.getNumSentinels();
        for (int i = 0; i < numSentinels; i++) {

            createSentinel(world, i, droneHeight);
        }
    }

    private void createSentinel(Level world, int i, int height) {
        if (level.getChunkSource().getChunkNow(center.x(), center.z()) == null) {
            return;
        }
        LivingEntity entity = SentinelDroneEntity.create(world, i, center);
        int cx = center.x() * 16 + 8;
        int cy = isLargeCube() ? CityTools.getCityHeight(level, city) + 36 : height;
        int cz = center.z() * 16 + 8;
        entity.setPos(cx, cy, cz);
        world.addFreshEntity(entity);
    }

    public void enableEditMode(Level world) {
        for (Map.Entry<BlockPos, Direction> entry : guardPositions.entrySet()) {
            world.setBlockAndUpdate(entry.getKey(), Registration.GUARD_DUMMY.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, entry.getValue()));
        }
        for (Map.Entry<BlockPos, Direction> entry : soldierPositions.entrySet()) {
            world.setBlockAndUpdate(entry.getKey(), Registration.SOLDIER_DUMMY.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, entry.getValue()));
        }
        for (Map.Entry<BlockPos, Direction> entry : masterSoldierPositions.entrySet()) {
            world.setBlockAndUpdate(entry.getKey(), Registration.MASTER_SOLDIER_DUMMY.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, entry.getValue()));
        }
    }

    public void readFromNBT(CompoundTag nbt) {
        guardianId = nbt.read("guardianId", UUIDUtil.CODEC).isPresent() ? nbt.read("guardianId", UUIDUtil.CODEC).orElse(null) : null;
        guardianDead = nbt.getBooleanOr("guardianDead", false);
        guardianChunk = nbt.contains("guardianChunk") ? ChunkPos.unpack(nbt.getLongOr("guardianChunk", 0)) : null;
        guardianMissingSince = -1;
        initialized = nbt.getBooleanOr("initialized", false);
        scannedChunks.clear();
        forceFields.clear();
        alarms.clear();
        negariteGenerators.clear();
        posiriteGenerators.clear();
        soldierBoundingbox = null;
        initializedChunks.clear();
        for (long chunk : nbt.getLongArray("initializedChunks").orElseGet(() -> new long[0])) {
            initializedChunks.add(chunk);
        }
        sentinelsInitialized = nbt.getBooleanOr("sentinelsInitialized", false);
        signalChannels.clear();
        for (Tag entry : nbt.getListOrEmpty("signalChannels")) {
            CompoundTag channel = (CompoundTag) entry;
            signalChannels.put(channel.getIntOr("desired", 0), channel.getIntOr("actual", 0));
        }
        settings = null;
        readSetFromNBT(nbt.getListOrEmpty("cores"), aiCores);
        readSetFromNBT(nbt.getListOrEmpty("pendingCores"), pendingCores);
        if (initialized) {
            if (nbt.contains("settings")) {
                settings = new CityAISettings();
                settings.readFromNBT(nbt.getCompoundOrEmpty("settings"));
            }
            keyId = nbt.getStringOr("keyId", "");
            storageKeyId = nbt.getStringOr("storageKeyId", "");
            forcefieldId = nbt.getStringOr("forcefieldId", "");
            watchingPlayers.clear();
            if (nbt.contains("players")) {
                ListTag list = nbt.getListOrEmpty("players");
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag tc = list.getCompoundOrEmpty(i);
                    UUID uuid = tc.read("id", UUIDUtil.CODEC).orElse(null);
                    BlockPos pos = readPosition(tc);
                    watchingPlayers.put(uuid, pos);
                }

            }
            sentinelMovementTicks = nbt.getIntOr("sentinelMovementTicks", 0);
            sentinelAngleOffset = nbt.getIntOr("sentinelAngleOffset", 0);
            levitatorTicker = nbt.getIntOr("levitatorTicker", 0);
            onAlert = nbt.getIntOr("onAlert", 0);
            highAlert = nbt.getBooleanOr("highAlert", false);
            droneTicker = nbt.getIntOr("droneTicker", 0);
            readMapFromNBT(nbt.getListOrEmpty("guards"), guardPositions);
            readMapFromNBT(nbt.getListOrEmpty("soldierPositions"), soldierPositions);
            readMapFromNBT(nbt.getListOrEmpty("masterSoldierPositions"), masterSoldierPositions);
        }
    }

    public void writeToNBT(CompoundTag compound) {
        if (guardianId != null) {
            compound.store("guardianId", UUIDUtil.CODEC, guardianId);
        }
        compound.putBoolean("guardianDead", guardianDead);
        if (guardianChunk != null) {
            compound.putLong("guardianChunk", guardianChunk.pack());
        }
        compound.putBoolean("initialized", initialized);
        compound.putLongArray("initializedChunks", initializedChunks.stream().mapToLong(Long::longValue).toArray());
        compound.putBoolean("sentinelsInitialized", sentinelsInitialized);
        ListTag channels = new ListTag();
        signalChannels.forEach((desired, actual) -> {
            CompoundTag channel = new CompoundTag();
            channel.putInt("desired", desired);
            channel.putInt("actual", actual);
            channels.add(channel);
        });
        compound.put("signalChannels", channels);
        compound.put("cores", writeSetToNBT(aiCores));
        compound.put("pendingCores", writeSetToNBT(pendingCores));
        if (initialized) {
            if (settings != null) {
                CompoundTag tc = new CompoundTag();
                settings.writeToNBT(tc);
                compound.put("settings", tc);
            }

            compound.putString("keyId", keyId);
            compound.putString("storageKeyId", storageKeyId);
            compound.putString("forcefieldId", forcefieldId);
            if (!watchingPlayers.isEmpty()) {
                ListTag list = new ListTag();
                for (Map.Entry<UUID, BlockPos> entry : watchingPlayers.entrySet()) {
                    CompoundTag tc = writePosition(entry.getValue());
                    tc.store("id", UUIDUtil.CODEC, entry.getKey());
                    list.add(tc);
                }
                compound.put("players", list);
            }
            compound.putInt("sentinelMovementTicks", sentinelMovementTicks);
            compound.putInt("sentinelAngleOffset", sentinelAngleOffset);
            compound.putInt("levitatorTicker", levitatorTicker);
            compound.putInt("onAlert", onAlert);
            compound.putBoolean("highAlert", highAlert);
            compound.putInt("droneTicker", droneTicker);
            compound.put("guards", writeMapToNBT(guardPositions));
            compound.put("soldierPositions", writeMapToNBT(soldierPositions));
            compound.put("masterSoldierPositions", writeMapToNBT(masterSoldierPositions));
        }
    }

    private ListTag writeSetToNBT(Set<BlockPos> set) {
        ListTag list = new ListTag();
        for (BlockPos pos : set) {
            list.add(writePosition(pos));
        }
        return list;
    }

    private void readSetFromNBT(ListTag list, Set<BlockPos> set) {
        set.clear();
        for (int i = 0; i < list.size(); i++) {
            BlockPos pos = readPosition(list.getCompoundOrEmpty(i));
            set.add(pos);
        }
    }

    private ListTag writeMapToNBT(Map<BlockPos, Direction> map) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Direction> entry : map.entrySet()) {
            CompoundTag tc = writePosition(entry.getKey());
            tc.putInt("facing", entry.getValue().ordinal());
            list.add(tc);
        }
        return list;
    }

    private void readMapFromNBT(ListTag list, Map<BlockPos, Direction> map) {
        map.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tc = list.getCompoundOrEmpty(i);
            BlockPos pos = readPosition(tc);
            Direction facing = OrientationTools.DIRECTION_VALUES[tc.getIntOr("facing", 0)];
            map.put(pos, facing);
        }
    }

    private static BlockPos readPosition(CompoundTag tag) {
        return new BlockPos(tag.getIntOr("X", 0), tag.getIntOr("Y", 0), tag.getIntOr("Z", 0));
    }

    private static CompoundTag writePosition(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        return tag;
    }

}
