package mcjty.arienteworld.dimension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import mcjty.ariente.api.ICityEquipment;
import mcjty.arienteworld.ArienteStuff;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.*;
import mcjty.lib.varia.BlockPosTools;
import mcjty.lib.varia.Logging;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.MobSpawnerTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class EditMode {

    public static final PaletteIndex PALETTE_AIR = new PaletteIndex(' ', ' ');
    public static boolean editMode = false;

    public static Pair<BuildingPart, Integer> getCurrentPart(City city, World world, BlockPos pos) {
        IArienteChunkGenerator generator = (IArienteChunkGenerator) (((ServerWorld) world).getChunkProvider().getChunkGenerator());
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        List<BuildingPart> parts = CityTools.getBuildingParts(city, cx, cz);
        if (!parts.isEmpty()) {
            BuildingPart found = null;
            int partY = -1;
            int lowesty = CityTools.getLowestHeight(city, generator, cx, cz);
            for (BuildingPart part : parts) {
                int count = part.getSliceCount();
                if (pos.getY() >= lowesty && pos.getY() < lowesty + count) {
                    found = part;
                    partY = lowesty;
                    break;
                }
                lowesty += count;

            }
            if (found != null) {
                return Pair.of(found, partY);
            }
        }
        return null;
    }

    public static void syncChunk(PlayerEntity player) {
        City city = getCurrentDungeon(player);
        if (city == null) {
            player.sendStatusMessage(new StringTextComponent(TextFormatting.RED + "No city!"), false);
            return;
        }
        World world = player.getEntityWorld();
        BlockPos pos = player.getPosition();
        int cx = (pos.getX() >> 4);
        int cz = (pos.getZ() >> 4);

        Pair<BuildingPart, Integer> pair = EditMode.getCurrentPart(city, world, pos);
        if (pair != null) {
            BuildingPart part = pair.getLeft();
            int y = pair.getRight();
            for (int relX = 0; relX < 16; relX++) {
                for (int relZ = 0; relZ < 16; relZ++) {
                    for (int relY = y; relY < y + part.getSliceCount(); relY++) {
                        BlockState state = world.getBlockState(new BlockPos(cx * 16 + relX, relY, cz * 16 + relZ));
                        EditMode.copyBlock(city, world, state, pair.getKey(), relX, relY-y, relZ);
                    }
                }
            }
        }
    }

    public static void setVariant(PlayerEntity player, String variant) {
        City city = getCurrentDungeon(player);
        if (city == null) {
            player.sendStatusMessage(new StringTextComponent(TextFormatting.RED + "No city!"), false);
            return;
        }
        BlockPos pos = player.getPosition();
        int cx = (pos.getX() >> 4);
        int cz = (pos.getZ() >> 4);

        PartPalette found = getCurrentPartPalette(player, city, pos, cx, cz);

        if (found == null) {
            player.sendStatusMessage(new StringTextComponent(TextFormatting.RED + "No part palette!"), false);
            return;
        }

        found.setVariant(variant);

        List<String> palette = found.getPalette();
        Map<String, Integer> variants = city.getPlan().getVariants();
        int count = variants.getOrDefault(variant, 0);
        String firstPartName = palette.get(0);
        BuildingPart firstPart = AssetRegistries.PARTS.get(firstPartName);
        while (palette.size() < count) {
            String newpartname = findValidNewPartNameBasedOnOther(firstPartName, palette.size());
            BuildingPart copy = firstPart.createCopy(newpartname);
            AssetRegistries.PARTS.put(newpartname, copy);
            player.sendStatusMessage(new StringTextComponent("Created part: " + newpartname), false);
            palette.add(newpartname);
        }

        player.sendStatusMessage(new StringTextComponent("Variant set to: " + variant), false);
        updateCity(player, city);
    }

    private static String findValidNewPartNameBasedOnOther(String oldname, int number) {
        if (oldname.contains("@")) {
            oldname = oldname.substring(0, oldname.indexOf("@"));
        }
        String newpartName = oldname + "@" + number;
        while (AssetRegistries.PARTS.get(newpartName) != null) {
            number++;
            newpartName = oldname + "@" + number;
        }
        return newpartName;
    }

    public static void getVariant(PlayerEntity player) {
        City city = getCurrentDungeon(player);
        if (city == null) {
            player.sendStatusMessage(new StringTextComponent(TextFormatting.RED + "No city!"), false);
            return;
        }
        BlockPos pos = player.getPosition();
        int cx = (pos.getX() >> 4);
        int cz = (pos.getZ() >> 4);

        PartPalette found = getCurrentPartPalette(player, city, pos, cx, cz);

        if (found == null) {
            player.sendStatusMessage(new StringTextComponent(TextFormatting.RED + "No part palette!"), false);
            return;
        }

        player.sendStatusMessage(new StringTextComponent("Variant is: " + found.getVariant()), false);

    }

    public static void listVariants(PlayerEntity player) {
        City city = getCurrentDungeon(player);
        if (city == null) {
            player.sendStatusMessage(new StringTextComponent(TextFormatting.RED + "No city!"), false);
            return;
        }

        Map<String, Integer> baseVariants = city.getPlan().getVariants();
        Map<String, Integer> variantSelections = CityTools.getVariantSelections(city.getCenter());
        for (Map.Entry<String, Integer> entry : variantSelections.entrySet()) {
            Integer maxCount = baseVariants.get(entry.getKey());
            player.sendStatusMessage(new StringTextComponent("Variant: " + entry.getKey() + " (" + entry.getValue() + "/" + maxCount + ")"), false);
        }
    }

    public static void createVariant(PlayerEntity player, String variant, String maxS) {
        Integer max = Integer.parseInt(maxS);

        City city = getCurrentDungeon(player);
        if (city == null) {
            player.sendStatusMessage(new StringTextComponent(TextFormatting.RED + "No city!"), false);
            return;
        }

        Map<String, Integer> variants = city.getPlan().getVariants();
        Map<String, Integer> variantSelections = CityTools.getVariantSelections(city.getCenter());
        variants.put(variant, max);
        variantSelections.put(variant, 0);
    }

    public static void switchVariant(PlayerEntity player, String variant, String indexS) {
        Integer index = Integer.parseInt(indexS);

        City city = getCurrentDungeon(player);
        if (city == null) {
            player.sendStatusMessage(new StringTextComponent(TextFormatting.RED + "No city!"), false);
            return;
        }

        Map<String, Integer> variants = city.getPlan().getVariants();
        Map<String, Integer> variantSelections = CityTools.getVariantSelections(city.getCenter());
        if (!variantSelections.containsKey(variant)) {
            player.sendStatusMessage(new StringTextComponent(TextFormatting.RED + "Variant not found!"), false);
            return;
        }

        saveCity(player);

        variantSelections.put(variant, Math.min(index, variants.get(variant) - 1));
        player.sendStatusMessage(new StringTextComponent("Variant " + variant + " set to " + index), false);

        updateCity(player, city);
    }

    private static void updateCity(PlayerEntity player, City city) {
        player.sendStatusMessage(new StringTextComponent(TextFormatting.GREEN + "Updated city!"), false);
        IArienteChunkGenerator generator = (IArienteChunkGenerator) (((ServerWorld) player.getEntityWorld()).getChunkProvider().getChunkGenerator());

        CityPlan plan = city.getPlan();

        loadCityOrStation(player, city.getCenter(), plan, 0,
                (x, z) -> CityTools.getLowestHeight(city, generator, x, z),
                (x, z) -> CityTools.getBuildingParts(city, x, z), false);
    }

    private static PartPalette getCurrentPartPalette(PlayerEntity player, City city, BlockPos pos, int cx, int cz) {
        IArienteChunkGenerator generator = (IArienteChunkGenerator) (((ServerWorld) player.getEntityWorld()).getChunkProvider().getChunkGenerator());
        int lowesty = CityTools.getLowestHeight(city, generator, cx, cz);
        List<PartPalette> partPalettes = CityTools.getPartPalettes(city, cx, cz);
        PartPalette found = null;
        int partY = -1;
        for (PartPalette partPalette : partPalettes) {
            List<String> palette = partPalette.getPalette();
            int count = palette.isEmpty() ? 0 : AssetRegistries.PARTS.get(palette.get(0)).getSliceCount();
            if (pos.getY() >= lowesty && pos.getY() < lowesty + count) {
                found = partPalette;
                partY = lowesty;
                break;
            }
            lowesty += count;
        }
        return found;
    }

    public static void enableEditMode(PlayerEntity player) {

        BlockPos start = player.getPosition();
        int cx = (start.getX() >> 4);
        int cz = (start.getZ() >> 4);

        City city = CityTools.getNearestDungeon(cx, cz);
        if (city == null || !CityTools.isDungeonChunk(cx, cz)) {
            // Check if it is a landscape city chunk
            Pair<String, Transform> part = ArienteLandscapeCity.getBuildingPart(cx, cz);
            BuildingPart buildingPart = AssetRegistries.PARTS.get(part.getKey());
            int height = ArienteLandscapeCity.getBuildingYOffset(cx, cz);
            // We restore our building part with the default NONE transform so that we can edit is in a consistent way
            restorePart(buildingPart, player.getEntityWorld(), new BlockPos(cx * 16 + 8, height /*unused*/, cz * 16 + 8),
                    height, AssetRegistries.PALETTES.get(ArienteLandscapeCity.CITY_PALETTE));

            player.sendMessage(new StringTextComponent("Editing: " + part));
            return;
        }

        // Restore city from parts
        if (!loadCity(player)) {
            player.sendMessage(new StringTextComponent(TextFormatting.RED + "Error enabling edit mode!"));
            return;
        }
        player.sendMessage(new StringTextComponent("Editing: " + city.getPlan().getName()));

        editMode = true;
        city = getCurrentDungeon(player);
        if (city == null) {
            return;
        }

        CityAI cityAI = CityAISystem.getCityAISystem(player.getEntityWorld()).getCityAI(city.getCenter());
        cityAI.enableEditMode(player.getEntityWorld());
    }

    public static City getCurrentDungeon(PlayerEntity player) {
        City city = CityTools.getNearestDungeon(player.getEntityWorld(), player.getPosition());
        if (city == null) {
            player.sendMessage(new StringTextComponent("No city can be found!"));
            return null;
        }
        return city;
    }

    public static void dungeonInfo(PlayerEntity player) {
        BlockPos start = player.getPosition();
        int cx = (start.getX() >> 4);
        int cz = (start.getZ() >> 4);

        player.sendMessage(new StringTextComponent("Info for chunk " + cx + "," + cz));

        City city = getCurrentDungeon(player);
        if (city != null) {
            if (!CityTools.isDungeonChunk(cx, cz)) {
                city = null;
            }
        }

        if (city == null) {
            IArienteChunkGenerator generator = (IArienteChunkGenerator) (((ServerWorld) player.getEntityWorld()).getChunkProvider().getChunkGenerator());

            if (ArienteLandscapeCity.isLandscapeCityChunk(cx, cz, player.getEntityWorld(), generator.getBiomes())) {
                Pair<String, Transform> part = ArienteLandscapeCity.getBuildingPart(cx, cz);
                player.sendMessage(new StringTextComponent("Building part: " + part.getKey() + " (" + part.getRight() + ")"));
                if (ArienteLandscapeCity.isCityLevitatorChunk(cx, cz)) {
                    Pair<String, Transform> pair = ArienteLandscapeCity.getCityLevitatorPart(cx, cz);
                    player.sendMessage(new StringTextComponent("City levitator part: " + pair.getLeft() + " (" +
                            pair.getRight().name() + ")"));
                }
            }
            return;
        }

        player.sendMessage(new StringTextComponent("City name: " + city.getName()));

        BlockPos pos = player.getPosition();
        ChunkPos coord = BlockPosTools.getChunkCoordFromPos(pos);
        IArienteChunkGenerator generator = (IArienteChunkGenerator) (((ServerWorld) player.getEntityWorld()).getChunkProvider().getChunkGenerator());
        int lowesty = CityTools.getLowestHeight(city, generator, coord.x, coord.z);
        List<BuildingPart> parts = CityTools.getBuildingParts(city, coord.x, coord.z);

        BuildingPart found = null;
        int partY = -1;
        for (BuildingPart part : parts) {
            int count = part.getSliceCount();
            if (pos.getY() >= lowesty && pos.getY() < lowesty + count) {
                found = part;
                partY = lowesty;
                break;
            }
            lowesty += count;
        }

        if (found != null) {
            player.sendMessage(new StringTextComponent("Part: " + found.getName()));
        }
    }

    public static void copyBlock(City city, World world, BlockState placedBlock, BuildingPart found, int relX, int relY, int relZ) {
        int cx;
        int cz;
        CityPlan plan = city.getPlan();
        List<String> pattern = plan.getPlan();
        int dimX = pattern.get(0).length();
        int dimZ = pattern.size();

        cx = city.getCenter().x;
        cz = city.getCenter().z;

        IArienteChunkGenerator generator = (IArienteChunkGenerator) (((ServerWorld) world).getChunkProvider().getChunkGenerator());

        for (int dx = cx - dimX / 2 - 1; dx <= cx + dimX / 2 + 1; dx++) {
            for (int dz = cz - dimZ / 2 - 1; dz <= cz + dimZ / 2 + 1; dz++) {
                int y = CityTools.getLowestHeight(city, generator, dx, dz);
                List<BuildingPart> parts = CityTools.getBuildingParts(city, dx, dz);
                for (BuildingPart part : parts) {
                    if (part == found) {
                        world.setBlockState(new BlockPos(dx * 16 + relX, y + relY, dz * 16 + relZ), placedBlock, 3);
                    }
                    y += part.getSliceCount();
                }
            }
        }
    }

    public static void breakBlock(City city, World world, BuildingPart found, int relX, int relY, int relZ) {
        int cx;
        int cz;
        CityPlan plan = city.getPlan();
        List<String> pattern = plan.getPlan();
        int dimX = pattern.get(0).length();
        int dimZ = pattern.size();

        cx = city.getCenter().x;
        cz = city.getCenter().z;

        IArienteChunkGenerator generator = (IArienteChunkGenerator) (((ServerWorld) world).getChunkProvider().getChunkGenerator());

        for (int dx = cx - dimX / 2 - 1; dx <= cx + dimX / 2 + 1; dx++) {
            for (int dz = cz - dimZ / 2 - 1; dz <= cz + dimZ / 2 + 1; dz++) {
                int y = CityTools.getLowestHeight(city, generator, dx, dz);
                List<BuildingPart> parts = CityTools.getBuildingParts(city, dx, dz);
                for (BuildingPart part : parts) {
                    if (part == found) {
                        world.setBlockState(new BlockPos(dx * 16 + relX, y + relY, dz * 16 + relZ), Blocks.AIR.getDefaultState());
                    }
                    y += part.getSliceCount();
                }
            }
        }
    }

    public static boolean loadCity(PlayerEntity player) {
        BlockPos start = player.getPosition();
        int cx = (start.getX() >> 4);
        int cz = (start.getZ() >> 4);

        if (CityTools.isStationChunk(cx, cz) && start.getY() >= CityTools.getStationHeight() && start.getY() <= CityTools.getStationHeight() + 10 /* @todo */) {
            loadStation(player);
            return true;
        }

        City city = CityTools.getNearestDungeon(cx, cz);
        if (city == null) {
            return false;
        }

        CityPlan plan = city.getPlan();

        IArienteChunkGenerator generator = (IArienteChunkGenerator) (((ServerWorld) player.getEntityWorld()).getChunkProvider().getChunkGenerator());
        loadCityOrStation(player, city.getCenter(), plan, 0,
                (x, z) -> CityTools.getLowestHeight(city, generator, x, z),
                (x, z) -> CityTools.getBuildingParts(city, x, z), true);
        return true;
    }

    public static void loadStation(PlayerEntity player) {
        BlockPos start = player.getPosition();
        int cx = (start.getX() >> 4);
        int cz = (start.getZ() >> 4);
        CityPlan plan = AssetRegistries.CITYPLANS.get("station");
        loadCityOrStation(player, CityTools.getNearestStationCenter(cx, cz), plan, 0,
                (x, z) -> CityTools.getStationHeight(),
                CityTools::getStationParts, true);
    }

    private static void loadCityOrStation(PlayerEntity player,
                                          ChunkPos center, CityPlan plan, int offset,
                                          BiFunction<Integer, Integer, Integer> heightGetter,
                                          BiFunction<Integer, Integer, List<BuildingPart>> partsGetter,
                                          boolean doVoid) {
        List<String> pattern = plan.getPlan();
        int dimX = pattern.get(0).length();
        int dimZ = pattern.size();

        Palette palette = new Palette(plan.getPalette());
        CompiledPalette compiledPalette = CompiledPalette.getCompiledPalette(plan.getPalette());
        for (PaletteIndex character : compiledPalette.getCharacters()) {
            BlockState state = compiledPalette.getStraight(character);
            if (state != null) {
                palette.addMapping(character, state);
            }
        }

        int cx = center.x;
        int cz = center.z;

        for (int dx = cx - dimX / 2 - 1 - offset; dx <= cx + dimX / 2 + 1 - offset; dx++) {
            for (int dz = cz - dimZ / 2 - 1 - offset; dz <= cz + dimZ / 2 + 1 - offset; dz++) {
                int y = heightGetter.apply(dx, dz);
                List<BuildingPart> parts = partsGetter.apply(dx, dz);
                if (parts.isEmpty()) {
                    // Void this chunk
                    if (doVoid) {
                        voidChunk(player.world, dx, dz);
                    }
                } else {
                    for (BuildingPart part : parts) {
                        restorePart(part, player.world, new BlockPos(dx * 16 + 8, y /*unused*/, dz * 16 + 8),
                                y, palette);
                        y += part.getSliceCount();
                    }
                }
            }
        }
    }

    private static void voidChunk(World world, int chunkX, int chunkZ) {
        for (int y = 1; y < 100; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    world.setBlockState(new BlockPos(chunkX * 16 + x, y, chunkZ * 16 + z), Blocks.AIR.getDefaultState());
                }
            }
        }
    }

    private static void restorePart(BuildingPart part, World world, BlockPos start, int y, Palette palette) {
        part.clearVSlices();
        BlockPos.Mutable pos = new BlockPos.Mutable(0, 0, 0);
        for (int x = 0; x < 16; x++) {
            int cx = (start.getX() >> 4) * 16;

            for (int z = 0; z < 16; z++) {
                int cz = (start.getZ() >> 4) * 16;

                BuildingPart.PalettedSlice vs = part.getVSlice(x, z);
                if (vs != null) {

                    for (int f = 0; f < part.getSliceCount(); f++) {
                        int cy = y + f;
                        if (cy > 255) {
                            break;
                        }
                        pos.setPos(cx + x, cy, cz + z);
                        PaletteIndex c = vs.getSlice().get(f);
                        BlockState original = palette.getPalette().get(c);
                        BlockState current = world.getBlockState(pos);

                        if (!current.equals(original) && original != null) {
                            world.setBlockState(pos, original, 3);
                        }
                    }
                }
            }
        }
    }


    public static void saveCity(PlayerEntity player) {
        BlockPos start = player.getPosition();
        int cx = (start.getX() >> 4);
        int cz = (start.getZ() >> 4);

        if (CityTools.isStationChunk(cx, cz) && start.getY() >= CityTools.getStationHeight() && start.getY() <= CityTools.getStationHeight() + 10 /* @todo */) {
            try {
                saveStation(player);
            } catch (FileNotFoundException e) {
                player.sendMessage(new StringTextComponent(TextFormatting.RED + "Error saving station!"));
                Logging.logError("Error saving station!", e);
            }
            return;
        }

        IArienteChunkGenerator generator = (IArienteChunkGenerator) (((ServerWorld) player.getEntityWorld()).getChunkProvider().getChunkGenerator());
        City city = CityTools.getNearestDungeon(cx, cz);
        if (city == null || !CityTools.isDungeonChunk(cx, cz)) {
            try {
                saveLandscapeCityPart(player, generator, cx, cz);
            } catch (FileNotFoundException e) {
                player.sendMessage(new StringTextComponent(TextFormatting.RED + "Error saving landscape city part!"));
                Logging.logError("Error saving landscape city part!", e);
            }
//            player.sendMessage(new StringTextComponent("No city or station can be found!"));
            return;
        }

        CityPlan plan = city.getPlan();

        try {
            saveCityOrStation(player, city.getCenter(), plan, 0,
                    (x, z) -> CityTools.getLowestHeight(city, generator, x, z),
                    (x, z) -> CityTools.getBuildingParts(city, x, z));
        } catch (FileNotFoundException e) {
            player.sendMessage(new StringTextComponent(TextFormatting.RED + "Error saving city!"));
            Logging.logError("Error saving city!", e);
        }
    }

    private static void saveLandscapeCityPart(PlayerEntity player, IArienteChunkGenerator generator, int cx, int cz) throws FileNotFoundException {
        CityPlan dummyPlan = new CityPlan("dummy");
        dummyPlan.addPlan("a");
        dummyPlan.setPalette(ArienteLandscapeCity.CITY_PALETTE);

        boolean levitatorChunk = ArienteLandscapeCity.isCityLevitatorChunk(cx, cz);
        int height = ArienteLandscapeCity.getBuildingYOffset(cx, cz);
        if (levitatorChunk && player.getPosition().getY() < height) {
            // Save the station part instead of the building part
            Pair<String, Transform> pair = ArienteLandscapeCity.getCityLevitatorPart(cx, cz);
            String buildingPart = pair.getKey();
            dummyPlan.addToPartPalette('a', buildingPart);
            saveCityOrStation(player, new ChunkPos(cx, cz), dummyPlan, 0,
                    (x, z) -> height - ArienteLandscapeCity.CITYLEV_HEIGHT,
                    (x, z) -> x == cx && z == cz ? Collections.singletonList(AssetRegistries.PARTS.get(buildingPart)) : Collections.emptyList());
            player.sendMessage(new StringTextComponent("Saved part: " + buildingPart));
        } else {
            Pair<String, Transform> buildingPart = ArienteLandscapeCity.getBuildingPart(cx, cz);
            dummyPlan.addToPartPalette('a', buildingPart.getKey());
            // We save ignoring transform because we assume the user has done a restore and then the transform will be ignored too
            saveCityOrStation(player, new ChunkPos(cx, cz), dummyPlan, 0,
                    ArienteLandscapeCity::getBuildingYOffset,
                    (x, z) -> x == cx && z == cz ? Collections.singletonList(AssetRegistries.PARTS.get(buildingPart.getKey())) : Collections.emptyList());
            player.sendMessage(new StringTextComponent("Saved part: " + buildingPart));
        }
    }

    private static void saveStation(PlayerEntity player) throws FileNotFoundException {
        BlockPos start = player.getPosition();
        int cx = (start.getX() >> 4);
        int cz = (start.getZ() >> 4);
        CityPlan plan = AssetRegistries.CITYPLANS.get("station");
        saveCityOrStation(player, CityTools.getNearestStationCenter(cx, cz), plan, 0,
                (x, z) -> CityTools.getStationHeight(),
                CityTools::getStationParts);
    }

    private static void saveCityOrStation(PlayerEntity player,
                                          ChunkPos center, CityPlan plan, int offset,
                                          BiFunction<Integer, Integer, Integer> heightGetter,
                                          BiFunction<Integer, Integer, List<BuildingPart>> partsGetter)
            throws FileNotFoundException {
        List<String> pattern = plan.getPlan();
        int dimX = pattern.get(0).length();
        int dimZ = pattern.size();

        JsonArray array = new JsonArray();
        AtomicInteger idx = new AtomicInteger(1);
        Map<BlockState, PaletteIndex> mapping = new HashMap<>();
        Palette palette = new Palette(plan.getPalette());
        CompiledPalette compiledPalette = CompiledPalette.getNewCompiledPalette(plan.getPalette());
        for (PaletteIndex character : compiledPalette.getCharacters()) {
            BlockState state = compiledPalette.getStraight(character);
            if (state != null) {
                palette.addMapping(character, state);
                mapping.put(state, character);
            }
        }

        array.add(plan.writeToJSon());

        int cx = center.x;
        int cz = center.z;

        Set<PaletteIndex> paletteUsage = new HashSet<>();
        Map<String, BuildingPart> editedParts = new HashMap<>();
        for (int dx = cx - dimX / 2 - 1 - offset; dx <= cx + dimX / 2 + 1 - offset; dx++) {
            for (int dz = cz - dimZ / 2 - 1 - offset; dz <= cz + dimZ / 2 + 1 - offset; dz++) {
                int y = heightGetter.apply(dx, dz);
                List<BuildingPart> parts = partsGetter.apply(dx, dz);
                for (BuildingPart part : parts) {
                    BuildingPart newpart = exportPart(part, player.world, new BlockPos(dx * 16 + 8, y /*unused*/, dz * 16 + 8),
                            y, palette, paletteUsage, mapping, idx);
                    editedParts.put(newpart.getName(), newpart);
                    AssetRegistries.PARTS.put(newpart.getName(), newpart);
                    y += part.getSliceCount();
                }
            }
        }

        StringBuilder affectedParts = new StringBuilder();
        plan.getPartPalette().values()
                .stream()
                .flatMap(partPalette -> partPalette.getPalette().stream())
                .collect(Collectors.toSet())
                .stream()
                .sorted(String::compareTo)
                .map(name -> {
                    if (editedParts.containsKey(name)) {
                        affectedParts.append(name);
                        affectedParts.append(' ');
//                        return editedParts.get(name).writeToJSon();
                    }
                    return AssetRegistries.PARTS.get(name).writeToJSon();
                })
                .forEach(array::add);

        palette.optimize(paletteUsage);
        array.add(palette.writeToJSon());
        AssetRegistries.PALETTES.register(palette);
        System.out.println("Affected parts " + affectedParts);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (PrintWriter writer = new PrintWriter(new File(plan.getName() + ".json"))) {
            writer.print(gson.toJson(array));
            writer.flush();
        }
        player.sendMessage(new StringTextComponent("Save city/station '" + plan.getName() + "'!"));
    }

    private static PaletteIndex createNewIndex(int i) {
        String palettechars = " abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        return new PaletteIndex(palettechars.charAt(i % palettechars.length()),
                palettechars.charAt(i / palettechars.length()));
    }

    private static BuildingPart exportPart(BuildingPart part, World world, BlockPos start, int y, Palette palette,
                                           Set<PaletteIndex> paletteUsage,
                                           Map<BlockState, PaletteIndex> mapping, AtomicInteger idx) {
        Map<BlockPos, Map<String, Object>> teData = new HashMap<>();
        List<Slice> slices = new ArrayList<>();
        for (int f = 0; f < part.getSliceCount(); f++) {
            int cx = (start.getX() >> 4) * 16;
            int cy = y + f;
            int cz = (start.getZ() >> 4) * 16;
            if (cy > 255) {
                break;
            }
            Slice slice = new Slice();
            slices.add(slice);
            BlockPos.Mutable pos = new BlockPos.Mutable(cx, cy, cz);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.setPos(cx + x, cy, cz + z);
                    BlockState state = world.getBlockState(pos);
                    // Make sure the state doesn't contain any extended stuff
//                    state = state.getBlock().getStateFromMeta(state.getBlock().getMetaFromState(state));
                    state = state.getBlock().getDefaultState(); // @todo 1.15 meta
                    PaletteIndex character;

                    if (state.getBlock() == ArienteStuff.invisibleDoorBlock) {
                        character = PALETTE_AIR;
                    } else {
                        character = mapping.get(state);
                    }
                    if (character == null) {
                        while (true) {
                            character = (state.getBlock() == Blocks.AIR || state.getBlock() == Blocks.WATER)
                                    ? PALETTE_AIR : createNewIndex(idx.getAndIncrement());
                            if (!palette.getPalette().containsKey(character)) {
                                break;
                            }
                        }
                        palette.addMapping(character, state);
                        mapping.put(state, character);
                    }
                    paletteUsage.add(character);
                    slice.sequence[z * 16 + x] = character;
                    TileEntity te = world.getTileEntity(pos);
                    if (te instanceof ICityEquipment) {
                        Map<String, Object> saved = ((ICityEquipment) te).save();
                        if (saved != null) {
                            teData.put(new BlockPos(x, f, z), saved);
                        }
                    } else if (te instanceof MobSpawnerTileEntity) {
                        MobSpawnerTileEntity spawner = (MobSpawnerTileEntity) te;
                        ResourceLocation entityId = spawner.getSpawnerBaseLogic().getSpawnerEntity().getType().getRegistryName();
                        Map<String, Object> data = new HashMap<>();
                        data.put("mob", entityId.toString());
                        teData.put(new BlockPos(x, f, z), data);
                    }
                }
            }
        }

        BuildingPart.PalettedSlice[] sl = new BuildingPart.PalettedSlice[part.getSliceCount()];
        for (int i = 0; i < part.getSliceCount(); i++) {
            sl[i] = new BuildingPart.PalettedSlice(slices.get(i).sequence);
        }

        return new BuildingPart(part.getName(), 16, 16, sl, teData);
    }

    public static class Slice {
        PaletteIndex sequence[] = new PaletteIndex[256];
    }
}
