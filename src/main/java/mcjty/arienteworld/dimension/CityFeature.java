package mcjty.arienteworld.dimension;

import mcjty.ariente.api.ICityEquipment;
import mcjty.ariente.api.IStorageTile;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.dimension.terrain.NoiseGeneratorPerlin;
import net.minecraft.world.level.chunk.ChunkGenerator;
import java.util.Random;
import mcjty.ariente.api.MarbleColor;
import mcjty.ariente.facade.IFacadeSupport;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.Transform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static mcjty.ariente.setup.Registration.DOOR_MARKER;

/** Places only the current chunk, so adjacent city sections never depend on decoration order. */
public class CityFeature extends Feature<NoneFeatureConfiguration> {

    private static final Map<Block, Boolean> BLOCK_ENTITIES = new ConcurrentHashMap<>();

    public CityFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    public static boolean hasBlockEntity(BlockState state) {
        return state.hasBlockEntity() && BLOCK_ENTITIES.computeIfAbsent(state.getBlock(),
                block -> ((EntityBlock) block).newBlockEntity(BlockPos.ZERO, state) != null);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!level.getLevel().dimension().equals(DimensionRegister.DIMENSION)) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        // Native tree features also place decorative BaseBlocks without tile suppliers.
        var generated = level.getChunk(chunk.x, chunk.z);
        for (BlockPos pos : List.copyOf(generated.getBlockEntitiesPos())) {
            if (!hasBlockEntity(generated.getBlockState(pos))) {
                generated.removeBlockEntity(pos);
            }
        }
        CityChunk writer = new CityChunk(level, chunk);
        if (PortalGenerator.isPortalChunk(chunk.x, chunk.z)) {
            PortalGenerator.generate(writer, chunk, context.chunkGenerator());
            return true;
        }
        if (ArienteLandscapeCity.isLandscapeCityChunk(level.getSeed(), chunk.x, chunk.z)) {
            ArienteLandscapeCity.generate(chunk.x, chunk.z, writer);
        } else {
            generateCityWalls(writer, chunk);
        }
        City city = CityTools.getCity(level.getSeed(), CityTools.getNearestDungeonCenter(chunk.x, chunk.z));
        List<BuildingPart> parts = CityTools.getBuildingParts(level.getSeed(), city, chunk.x, chunk.z);
        if (parts.isEmpty()) {
            if (!ArienteLandscapeCity.isLandscapeCityChunk(level.getSeed(), chunk.x, chunk.z)) {
                blendNearbyCity(level, chunk, context.chunkGenerator());
            }
            LevitatorNetworkGenerator.generate(chunk.x, chunk.z, writer, context.chunkGenerator());
            return true;
        }
        int floor = CityTools.getCityHeight(city, context.chunkGenerator(), level, level.getLevel().getChunkSource().randomState());
        int bottom = floor - CityTools.getCellarHeight(level.getSeed(), city, chunk.x, chunk.z);
        int y = bottom;
        if (!city.getPlan().isFloating() && !city.getPlan().isUnderground()) {
            prepareTerrain(level, chunk, bottom);
        }
        Palette palette = AssetRegistries.PALETTES.get(city.getPlan().getPalette());
        for (BuildingPart part : parts) {
            placePart(level, new BlockPos(chunk.getMinBlockX(), y, chunk.getMinBlockZ()), part, palette);
            y += part.getSliceCount();
        }
        LevitatorNetworkGenerator.generate(chunk.x, chunk.z, writer, context.chunkGenerator());
        return true;
    }

    private static void generateCityWalls(CityChunk writer, ChunkPos chunk) {
        BlockState wall = LegacyBlockStates.read("ariente:marble@color=black");
        BlockState cap = LegacyBlockStates.read("ariente:marble_smooth@color=black");
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!ArienteLandscapeCity.isLandscapeCityChunk(writer.getLevel().getSeed(),
                    chunk.x + direction.getStepX(), chunk.z + direction.getStepZ())) {
                continue;
            }
            for (int i = 0; i < 16; i++) {
                int x = direction == Direction.WEST ? 0 : direction == Direction.EAST ? 15 : i;
                int z = direction == Direction.NORTH ? 0 : direction == Direction.SOUTH ? 15 : i;
                for (int y = 63; y < 71; y++) {
                    writer.setBlockState(new BlockPos(x, y, z), wall, false);
                }
                writer.setBlockState(new BlockPos(x, 71, z), cap, false);
            }
        }
    }

    private static void blendNearbyCity(WorldGenLevel level, ChunkPos chunk, ChunkGenerator generator) {
        int[][] heights = new int[3][3];
        boolean nearby = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                heights[dx + 1][dz + 1] = -1;
                int x = chunk.x + dx;
                int z = chunk.z + dz;
                if (CityTools.isDungeonChunk(level.getSeed(), x, z)) {
                    City city = CityTools.getCity(level.getSeed(), CityTools.getNearestDungeonCenter(x, z));
                    if (!city.getPlan().isUnderground()) {
                        heights[dx + 1][dz + 1] = CityTools.getCityHeight(city, generator, level, level.getLevel().getChunkSource().randomState());
                        nearby = true;
                    }
                }
            }
        }
        if (!nearby) {
            return;
        }
        NoiseGeneratorPerlin noise = new NoiseGeneratorPerlin(new Random((level.getSeed() + 516) * 314), 4);
        double[] variation = noise.getRegion(null, chunk.getMinBlockX(), chunk.getMinBlockZ(), 16, 16, 1.0 / 16, 1.0 / 16, 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int distance = 1000;
                int height = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (heights[dx + 1][dz + 1] < 0) {
                            continue;
                        }
                        int candidate = Math.max(dx < 0 ? x : dx > 0 ? 15 - x : 0, dz < 0 ? z : dz > 0 ? 15 - z : 0);
                        if (candidate < distance) {
                            distance = candidate;
                            height = heights[dx + 1][dz + 1];
                        }
                    }
                }
                double variance = variation[x + z * 16] / (distance <= 1 ? 3 : distance <= 2 ? 2 : distance <= 3 ? 1.5 : 1);
                for (int y = height + distance * 2 + (int) variance; y < 128; y++) {
                    level.setBlock(pos.set(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void prepareTerrain(WorldGenLevel level, ChunkPos chunk, int bottom) {
        BlockState foundation = LegacyBlockStates.read("ariente:marble_bricks@color=black");
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                for (int y = bottom; y < surface; y++) {
                    level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                // A continuous foundation joins sections over valleys and water.
                for (int y = bottom - 1; y >= level.getMinBuildHeight(); y--) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isSolidRender(level, pos)) {
                        break;
                    }
                    level.setBlock(pos, foundation, Block.UPDATE_CLIENTS);
                    level.getChunk(pos).removeBlockEntity(pos);
                }
            }
        }
    }

    private static BlockState orientDoor(BuildingPart part, Palette palette, int x, int y, int z, BlockState state) {
        // Original parts sometimes give adjacent markers conflicting normals.
        // Resolve their width in the authored part before rotating or mirroring it.
        boolean widthX = isDoorMarker(part, palette, x - 1, y, z) || isDoorMarker(part, palette, x + 1, y, z);
        boolean widthZ = isDoorMarker(part, palette, x, y, z - 1) || isDoorMarker(part, palette, x, y, z + 1);
        if (!widthX && !widthZ) {
            // Single markers have no neighboring markers to reveal the opening.
            // Use paired jambs above the floor, where the door panels will stand.
            for (int height = 1; height <= 2 && widthX == widthZ; height++) {
                widthX = isDoorJamb(part, palette, x - 1, y + height, z) && isDoorJamb(part, palette, x + 1, y + height, z);
                widthZ = isDoorJamb(part, palette, x, y + height, z - 1) && isDoorJamb(part, palette, x, y + height, z + 1);
            }
        }
        if (widthX != widthZ) {
            Direction normal = widthX ? Direction.NORTH : Direction.WEST;
            if (state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis() != normal.getAxis()) {
                return state.setValue(BlockStateProperties.HORIZONTAL_FACING, normal);
            }
        }
        return state;
    }

    private static boolean isDoorJamb(BuildingPart part, Palette palette, int x, int y, int z) {
        if (x < 0 || x >= part.getXSize() || z < 0 || z >= part.getZSize() || y >= part.getSliceCount()) {
            return false;
        }
        BlockState state = palette.getPalette().get(part.getC(x, y, z));
        return !state.is(DOOR_MARKER.get()) && state.canOcclude();
    }

    private static boolean isDoorMarker(BuildingPart part, Palette palette, int x, int y, int z) {
        return x >= 0 && x < part.getXSize() && z >= 0 && z < part.getZSize()
                && palette.getPalette().get(part.getC(x, y, z)).is(DOOR_MARKER.get());
    }

    public static void placePart(WorldGenLevel level, BlockPos origin, BuildingPart part, Palette palette) {
        placePart(level, origin, part, palette, Transform.ROTATE_NONE);
    }

    public static void placePart(WorldGenLevel level, BlockPos origin, BuildingPart part, Palette palette, Transform transform) {
        for (int x = 0; x < part.getXSize(); x++) {
            for (int z = 0; z < part.getZSize(); z++) {
                boolean started = false;
                for (int y = 0; y < part.getSliceCount(); y++) {
                    BlockState state = palette.getPalette().get(part.getC(x, y, z));
                    if (state.is(DOOR_MARKER.get())) {
                        state = orientDoor(part, palette, x, y, z, state);
                    }
                    // Preserve ground beneath a part, but carve its rooms after the first solid block.
                    if (!started && state.isAir()) {
                        continue;
                    }
                    started = true;
                    BlockPos pos = origin.offset(transform.transform(new BlockPos(x, y, z)));
                    state = transform.transform(state);
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    // BaseBlock also implements EntityBlock for decorative blocks with no supplier.
                    // Remove their placeholder NBT instead of trying to promote thousands of null tiles.
                    if (state.hasBlockEntity() && !hasBlockEntity(state)) {
                        level.getChunk(pos).removeBlockEntity(pos);
                    }
                    Map<String, Object> data = part.getTeInfo().get(new BlockPos(x, y, z));
                    if (data != null) {
                        BlockEntity blockEntity = level.getBlockEntity(pos);
                        if (blockEntity instanceof ICityEquipment equipment) {
                            Map<String, Object> settings = new HashMap<>(data);
                            BlockState mimic = null;
                            if (data.get("mimic") instanceof String id) {
                                int meta = (Integer) data.getOrDefault("meta", 0);
                                if (id.matches("ariente:(marble|marble_smooth|marble_pilar|marble_bricks|marble_slab|double_marble_slab)")) {
                                    mimic = LegacyBlockStates.read(id + "@color=" + MarbleColor.VALUES[meta].getSerializedName());
                                } else {
                                    mimic = LegacyBlockStates.read(id);
                                }
                                settings.put("mimic", ForgeRegistries.BLOCKS.getKey(mimic.getBlock()).toString());
                            }
                            equipment.load(settings);
                            if (mimic != null && blockEntity instanceof IFacadeSupport facade) {
                                facade.setMimicBlock(mimic);
                            }
                            if (palette.getName().equals(ArienteLandscapeCity.CITY_PALETTE) && equipment instanceof IStorageTile storage) {
                                CityAI.fillLoot(AssetRegistries.CITYPLANS.get("landscapecities"), storage);
                            }
                            blockEntity.setChanged();
                        } else if (blockEntity instanceof SpawnerBlockEntity spawner && data.get("mob") instanceof String mob) {
                            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(mob));
                            if (type == null) {
                                throw new IllegalArgumentException("Unknown city spawner mob: " + mob);
                            }
                            spawner.setEntityId(type, level.getRandom());
                            spawner.setChanged();
                        }
                    }
                }
            }
        }
    }
}
