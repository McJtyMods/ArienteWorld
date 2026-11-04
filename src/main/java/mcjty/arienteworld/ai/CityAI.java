package mcjty.arienteworld.ai;

import mcjty.arienteworld.entities.soldier.SoldierEntity;
import mcjty.arienteworld.entities.drone.SentinelDroneEntity;
import mcjty.arienteworld.entities.drone.DroneEntity;
import mcjty.arienteworld.entities.ArienteMobs;
import mcjty.ariente.api.*;
import mcjty.arienteworld.entities.drone.ISentinel;
import mcjty.arienteworld.entities.drone.IDrone;
import mcjty.arienteworld.entities.soldier.SoldierBehaviourType;
import mcjty.ariente.Ariente;
import mcjty.hologui.api.IHoloGuiEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.properties.RailShape;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityPlan;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.cities.Loot;
import mcjty.arienteworld.config.AIConfiguration;
import mcjty.arienteworld.setup.Registration;
import mcjty.arienteworld.dimension.EditMode;
import mcjty.arienteworld.dimension.CityFeature;
import mcjty.lib.tileentity.GenericTileEntity;
import mcjty.lib.varia.OrientationTools;
import mcjty.lib.varia.RedstoneMode;
import mcjty.ariente.varia.WeightedRandom;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static mcjty.ariente.setup.Registration.FLUX_BEAM;
import static mcjty.ariente.setup.Registration.KEY_CARD;
import static mcjty.ariente.setup.Registration.POWERSUIT_CHEST;
import static mcjty.ariente.setup.Registration.POWERSUIT_FEET;
import static mcjty.ariente.setup.Registration.POWERSUIT_HEAD;
import static mcjty.ariente.setup.Registration.POWERSUIT_LEGS;

public class CityAI implements ICityAI {

    private int levitator = -1;
    private int levitatorTicker = 20;
    private BlockPos levitatorPrevPos;

    private final ChunkPos center;
    private final ServerLevel level;
    private final City city;
    private Integer cityHeight;
    private boolean initialized = false;

    private CityAISettings settings = null;

    private boolean foundEquipment = false;
    private Set<BlockPos> aiCores = new HashSet<>();
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

    private int getLowestHeight(int chunkX, int chunkZ) {
        if (cityHeight == null) {
            cityHeight = CityTools.getCityHeight(level, city);
        }
        return cityHeight - CityTools.getCellarHeight(level.getSeed(), city, chunkX, chunkZ);
    }

    private boolean setup(Level world) {
        if (!areChunksLoaded()) {
            return false;
        }
        if (!initialized) {
            initialize(world);
            initialized = true;
            return false;
        } else {
            findEquipment(world, false);
            return true;
        }
    }

    private boolean areChunksLoaded() {
        List<String> pattern = city.getPlan().getPlan();
        int width = pattern.get(0).length();
        int depth = pattern.size();
        for (int x = center.x - width / 2; x < center.x - width / 2 + width; x++) {
            for (int z = center.z - depth / 2; z < center.z - depth / 2 + depth; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
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
    }

    // Return true if we potentially have to save the city system state
    @Override
    public boolean tick(BlockEntity tile) {
        if (EditMode.isEditing(tile.getLevel(), center)) {
            return false;
        }
        if (!areChunksLoaded()) {
            return false;
        }
        // We use the given AICoreTile parameter to make sure only one tick per city happens
        if (setup(tile.getLevel())) {
            // If there are no more ai cores the city AI is dead
            if (aiCores.isEmpty()) {
                return false;
            }

            IAICoreTile core = findFirstValidAICore(tile.getLevel());
            if (core == null) {
                // All cores are no longer valid and have been removed
                return false;
            }
            // Only tick for the first valid aicore
            if (!tile.getBlockPos().equals(core.getCorePos())) {
                return false;
            }

            handleAI(tile.getLevel());
            return true;
        }
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
            for (int x = center.x - width / 2; x < center.x - width / 2 + width; x++) {
                for (int z = center.z - depth / 2; z < center.z - depth / 2 + depth; z++) {
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
        for (BlockPos pos : aiCores) {
            if (!pos.equals(exclude)) {
                if (!world.hasChunkAt(pos)) {
                    return true;
                }
                BlockEntity te = world.getBlockEntity(pos);
                if (te instanceof IAICoreTile) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private IAICoreTile findFirstValidAICore(Level world) {
        for (BlockPos pos : aiCores) {
            if (!world.hasChunkAt(pos)) {
                continue;
            }
            BlockEntity te = world.getBlockEntity(pos);
            if (te instanceof IAICoreTile) {
                return (IAICoreTile) te;
            }
        }
        return null;
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

            BlockPos ctr = new BlockPos(center.x * 16 + 8, 50, center.z * 16 + 8);

            // To avoid creating the list we use the filter as a consumer here to increment our counter
            AtomicInteger soldiers = new AtomicInteger(0);
            AtomicInteger drones = new AtomicInteger(0);
            AtomicInteger sentinels = new AtomicInteger(0);
            AABB boundingbox = getSoldierBoundingbox();
            world.getEntitiesOfClass(LivingEntity.class, new AABB(ctr).inflate(dimX, 200, dimZ),
                    entity -> {
                        if (entity instanceof ISoldier soldier && entity.isAlive() && soldier.isReinforcementFor(center)) {
                            if (boundingbox != null) {
                                if (boundingbox.contains(entity.position())) {
                                    soldiers.incrementAndGet();
                                }
                            }
                        }
                        if (entity instanceof ISentinel) {
                            sentinels.incrementAndGet();
                        }
                        if (entity instanceof IDrone) {
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

                    if (dir.isAscending()) {
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
            BlockPos pos = new BlockPos(c.x * 16 + 8 + direction.getStepX() * i, 32, (c.z * 16) + 8 + direction.getStepZ() * i);
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
            ChunkPos otherCoord = new ChunkPos(center.x + facing.getStepX() * 16,
                    center.z + facing.getStepZ() * 16);
            if (!CityTools.isCityCenter(level.getSeed(), otherCoord.x, otherCoord.z)) {
                continue;
            }
            CityAI otherCity = system.getCityAI(otherCoord);
            if (otherCity != null && otherCity.areChunksLoaded() && !otherCity.isDead(world)) {
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
                BlockEntity te = world.getBlockEntity(pos);
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
                BlockEntity te = world.getBlockEntity(pos);
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
                double sq = pos.distSqr(new BlockPos(center.x * 16 + 8, 50, center.z * 16 + 8));
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
            int droneHeight = plan.getDroneHeightOffset() + getLowestHeight(center.x, center.z);

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
        if (soldierPositions.isEmpty()) {
            return;
        }

        // Too few soldiers. Spawn a new one
        CityPlan plan = city.getPlan();

        BlockPos pos;
        // Avoid too close to player if possible
        int avoidNearby = 3;
        do {
            pos = new ArrayList<>(soldierPositions.keySet()).get(random.nextInt(soldierPositions.size()));
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
        compound.putBoolean(ArmorUpgradeType.ARMOR.getWorkingKey(), compound.getBoolean(ArmorUpgradeType.ARMOR.getModuleKey()));
        helmet.setTag(compound);
        return helmet;
    }

    private ItemStack createNiceBoots() {
        ItemStack helmet = new ItemStack(POWERSUIT_FEET.get());
        CompoundTag compound = new CompoundTag();
        compound.putBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
        compound.putBoolean(ArmorUpgradeType.ARMOR.getWorkingKey(), compound.getBoolean(ArmorUpgradeType.ARMOR.getModuleKey()));
        helmet.setTag(compound);
        return helmet;
    }

    private ItemStack createNiceChestplate(CityPlan plan) {
        ItemStack helmet = new ItemStack(POWERSUIT_CHEST.get());
        CompoundTag compound = new CompoundTag();
        compound.putBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
        compound.putBoolean(ArmorUpgradeType.ARMOR.getWorkingKey(), compound.getBoolean(ArmorUpgradeType.ARMOR.getModuleKey()));
        if (random.nextFloat() < plan.getForcefieldChance()) {
            compound.putBoolean(ArmorUpgradeType.ENERGY.getModuleKey(), true);
            compound.putBoolean(ArmorUpgradeType.FORCEFIELD.getModuleKey(), true);
            compound.putBoolean(ArmorUpgradeType.FORCEFIELD.getWorkingKey(), compound.getBoolean(ArmorUpgradeType.FORCEFIELD.getModuleKey()));
        }
        helmet.setTag(compound);
        return helmet;
    }

    private ItemStack createNiceLegs() {
        ItemStack helmet = new ItemStack(POWERSUIT_LEGS.get());
        CompoundTag compound = new CompoundTag();
        compound.putBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
        compound.putBoolean(ArmorUpgradeType.ARMOR.getWorkingKey(), compound.getBoolean(ArmorUpgradeType.ARMOR.getModuleKey()));
        helmet.setTag(compound);
        return helmet;
    }

    private void spawnDrone(Level world, int height) {
        // Too few drones. Spawn a new one
        LivingEntity entity = DroneEntity.create(world, center);
        int cx = center.x * 16 + 8;
        int cy = height;
        int cz = center.z * 16 + 8;
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
                int droneHeight = plan.getDroneHeightOffset() + getLowestHeight(center.x, center.z);
                for (int i = 0; i < settings.getNumSentinels(); i++) {
                    createSentinel(world, i, droneHeight);
                }
            }
        }
    }

    private void handlePower(Level world) {
        // Handle power
        for (BlockPos pos : negariteGenerators) {
            BlockEntity te = world.getBlockEntity(pos);
            if (te instanceof IGenerator) {
                ((IGenerator) te).feedDust(1);
            }
        }
        for (BlockPos pos : posiriteGenerators) {
            BlockEntity te = world.getBlockEntity(pos);
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
        int droneHeight = plan.getSentinelRelHeight() + getLowestHeight(center.x, center.z);

        int angleI = (sentinelAngleOffset + sentinelId * 12 / settings.getNumSentinels()) % 12;
        int cx = center.x * 16 + 8;
        int cy = droneHeight;
        int cz = center.z * 16 + 8;

        float angle = angleI * 360.0f / 12;
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
        if (findFirstValidAICore(player.level()) == null) {
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

    private void findEquipment(Level world, boolean firstTime) {
        if (foundEquipment) {
            return;
        }

        if (firstTime) {
            keyId = Ariente.arienteSystem.getSecuritySystem(world).generateKeyId(world);
            storageKeyId = Ariente.arienteSystem.getSecuritySystem(world).generateKeyId(world);
            forcefieldId = Ariente.arienteSystem.getSecuritySystem(world).generateKeyId(world);
        }

        CityPlan plan = city.getPlan();
        List<String> pattern = plan.getPlan();
        int dimX = pattern.get(0).length();
        int dimZ = pattern.size();
        int cx = center.x;
        int cz = center.z;

        Map<Integer, Integer> desiredToReal = new HashMap<>();

        for (int dx = cx - dimX / 2; dx < cx - dimX / 2 + dimX; dx++) {
            for (int dz = cz - dimZ / 2; dz < cz - dimZ / 2 + dimZ; dz++) {
                int starty = getLowestHeight(dx, dz);
                int top = starty + CityTools.getBuildingParts(level.getSeed(), city, dx, dz).stream()
                        .mapToInt(BuildingPart::getSliceCount).sum();
                for (int x = dx * 16; x < dx * 16 + 16; x++) {
                    for (int z = dz * 16; z < dz * 16 + 16; z++) {
                        for (int y = starty; y < top; y++) {
                            BlockPos p = new BlockPos(x, y, z);
                            BlockState state = world.getBlockState(p);
                            Block block = state.getBlock();
                            if (block == Registration.GUARD_DUMMY.get()) {
                                guardPositions.put(p, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
                                world.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                            } else if (block == Registration.SOLDIER_DUMMY.get()) {
                                soldierPositions.put(p, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
                                world.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                            } else if (block == Registration.MASTER_SOLDIER_DUMMY.get()) {
                                masterSoldierPositions.put(p, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
                                world.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                            } else {
                                BlockEntity te = CityFeature.hasBlockEntity(world.getBlockState(p)) ? world.getBlockEntity(p) : null;
                                if (te instanceof ICityEquipment) {
                                    ((ICityEquipment) te).setup(this, world, firstTime);
                                }

                                if (firstTime && te instanceof ISignalChannel) {
                                    int desired = ((ISignalChannel) te).getDesiredChannel();
                                    if (!desiredToReal.containsKey(desired)) {
                                        // New channel is needed
                                        IRedstoneChannels redstoneChannels = Ariente.arienteSystem.getRedstoneChannels(world);
                                        int newChannel = redstoneChannels.newChannel();
                                        desiredToReal.put(desired, newChannel);
                                    }
                                    desired = desiredToReal.get(desired);
                                    ((ISignalChannel) te).setChannel(desired);
                                }

                                if (te instanceof IAICoreTile) {
                                    aiCores.add(p);
                                    ((IAICoreTile) te).setCityName(city.getName());
                                } else if (te instanceof IForceFieldTile) {
                                    // We already have this as equipment but we need it separate
                                    ((IForceFieldTile) te).setCityCenter(center);
                                    forceFields.add(p);
                                } else if (te instanceof IAlarmTile) {
                                    alarms.add(p);
                                } else if (te instanceof IGenerator) {
                                    if (((IGenerator) te).supportsNegarite()) {
                                        negariteGenerators.add(p);
                                    }
                                    if (((IGenerator) te).supportsPosirite()) {
                                        posiriteGenerators.add(p);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        foundEquipment = true;
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
        CityPlan plan = city.getPlan();
        fillLoot(plan, te);
    }

    public static void fillLoot(CityPlan plan, IStorageTile te) {
        if (plan.getName().equals("city5_1")) {
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
                    amount = 1 + random.nextInt(l.getMaxAmount() - 1);
                }
                ResourceLocation id = l.getId();
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
        Random rnd = new Random(seed + center.x * 567000003533L + center.z * 234516783139L);
        rnd.nextFloat();
        rnd.nextFloat();
        CityPlan plan = city.getPlan();
        settings = new CityAISettings();
        settings.setNumSentinels(getMinMax(rnd, plan.getMinSentinels(), plan.getMaxSentinels()));
    }

    private void initialize(Level world) {
        createSettings(world);
        findEquipment(world, true);
        initCityEquipment(world);
        initSentinels(world);
        initGuards(world);
        initMasterSoldiers(world);
    }

    private void initGuards(Level world) {
        for (Map.Entry<BlockPos, Direction> entry : guardPositions.entrySet()) {
            BlockPos pos = entry.getKey();
            Direction facing = entry.getValue();
            LivingEntity soldier = ArienteMobs.createSoldier(world, pos, facing, center, SoldierBehaviourType.SOLDIER_GUARD, false);
        }
    }

    private void initMasterSoldiers(Level world) {
        for (Map.Entry<BlockPos, Direction> entry : masterSoldierPositions.entrySet()) {
            BlockPos pos = entry.getKey();
            Direction facing = entry.getValue();
            LivingEntity soldier = ArienteMobs.createSoldier(world, pos, facing, center, SoldierBehaviourType.SOLDIER_FIGHTER, true);
        }
    }

    private void initCityEquipment(Level world) {

        setAlarmType(world, AlarmType.SAFE);

        for (BlockPos p : aiCores) {
            BlockEntity te = CityFeature.hasBlockEntity(world.getBlockState(p)) ? world.getBlockEntity(p) : null;
            if (te instanceof IAICoreTile) {
                ((IAICoreTile) te).setCityName(city.getName());
            }
        }

        for (BlockPos p : negariteGenerators) {
            BlockEntity te = CityFeature.hasBlockEntity(world.getBlockState(p)) ? world.getBlockEntity(p) : null;
            if (te instanceof GenericTileEntity) {
                GenericTileEntity generator = (GenericTileEntity) te;
                Ariente.arienteSystem.fixNetworks(world, p);
                generator.setRSMode(RedstoneMode.REDSTONE_IGNORED);
            }
        }
        for (BlockPos p : posiriteGenerators) {
            BlockEntity te = CityFeature.hasBlockEntity(world.getBlockState(p)) ? world.getBlockEntity(p) : null;
            if (te instanceof GenericTileEntity) {
                posiriteGenerators.add(p);
                GenericTileEntity generator = (GenericTileEntity) te;
                Ariente.arienteSystem.fixNetworks(world, p);
                generator.setRSMode(RedstoneMode.REDSTONE_IGNORED);
            }
        }
    }

    public void setAlarmType(Level world, AlarmType type) {
        for (BlockPos p : alarms) {
            BlockEntity te = CityFeature.hasBlockEntity(world.getBlockState(p)) ? world.getBlockEntity(p) : null;
            if (te instanceof IAlarmTile) {
                ((IAlarmTile) te).setAlarmType(type);
            }
        }
    }

    private void initSentinels(Level world) {
        CityPlan plan = city.getPlan();
        int droneHeight = plan.getDroneHeightOffset() + getLowestHeight(center.x, center.z);

        int numSentinels = settings.getNumSentinels();
        for (int i = 0; i < numSentinels; i++) {

            createSentinel(world, i, droneHeight);
        }
    }

    private void createSentinel(Level world, int i, int height) {
        LivingEntity entity = SentinelDroneEntity.create(world, i, center);
        int cx = center.x * 16 + 8;
        int cy = height;
        int cz = center.z * 16 + 8;
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
        initialized = nbt.getBoolean("initialized");
        settings = null;
        readSetFromNBT(nbt.getList("cores", Tag.TAG_COMPOUND), aiCores);
        if (initialized) {
            if (nbt.contains("settings")) {
                settings = new CityAISettings();
                settings.readFromNBT(nbt.getCompound("settings"));
            }
            keyId = nbt.getString("keyId");
            storageKeyId = nbt.getString("storageKeyId");
            forcefieldId = nbt.getString("forcefieldId");
            watchingPlayers.clear();
            if (nbt.contains("players")) {
                ListTag list = nbt.getList("players", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag tc = list.getCompound(i);
                    UUID uuid = tc.getUUID("id");
                    BlockPos pos = NbtUtils.readBlockPos(tc);
                    watchingPlayers.put(uuid, pos);
                }

            }
            sentinelMovementTicks = nbt.getInt("sentinelMovementTicks");
            sentinelAngleOffset = nbt.getInt("sentinelAngleOffset");
            levitatorTicker = nbt.getInt("levitatorTicker");
            onAlert = nbt.getInt("onAlert");
            highAlert = nbt.getBoolean("highAlert");
            droneTicker = nbt.getInt("droneTicker");
            readMapFromNBT(nbt.getList("guards", Tag.TAG_COMPOUND), guardPositions);
            readMapFromNBT(nbt.getList("soldierPositions", Tag.TAG_COMPOUND), soldierPositions);
            readMapFromNBT(nbt.getList("masterSoldierPositions", Tag.TAG_COMPOUND), masterSoldierPositions);
        }
    }

    public void writeToNBT(CompoundTag compound) {
        compound.putBoolean("initialized", initialized);
        compound.put("cores", writeSetToNBT(aiCores));
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
                    CompoundTag tc = NbtUtils.writeBlockPos(entry.getValue());
                    tc.putUUID("id", entry.getKey());
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
            list.add(NbtUtils.writeBlockPos(pos));
        }
        return list;
    }

    private void readSetFromNBT(ListTag list, Set<BlockPos> set) {
        set.clear();
        for (int i = 0; i < list.size(); i++) {
            BlockPos pos = NbtUtils.readBlockPos(list.getCompound(i));
            set.add(pos);
        }
    }

    private ListTag writeMapToNBT(Map<BlockPos, Direction> map) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Direction> entry : map.entrySet()) {
            CompoundTag tc = NbtUtils.writeBlockPos(entry.getKey());
            tc.putInt("facing", entry.getValue().ordinal());
            list.add(tc);
        }
        return list;
    }

    private void readMapFromNBT(ListTag list, Map<BlockPos, Direction> map) {
        map.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tc = list.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(tc);
            Direction facing = OrientationTools.DIRECTION_VALUES[tc.getInt("facing")];
            map.put(pos, facing);
        }
    }
}
