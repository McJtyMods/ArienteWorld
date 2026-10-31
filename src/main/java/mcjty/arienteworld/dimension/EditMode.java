package mcjty.arienteworld.dimension;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mcjty.ariente.api.ICityEquipment;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityPlan;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.PaletteIndex;
import mcjty.arienteworld.cities.PartPalette;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Operator authoring sessions use private asset copies. Runtime generation is never changed implicitly. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID)
public class EditMode {

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final String INDEX_CHARS = " abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public record Placement(BlockPos origin, String part, @Nullable Character symbol) {
    }

    public static class Session {
        private final ServerLevel level;
        private final ChunkPos center;
        private final City city;
        private CityPlan plan;
        private Palette palette;
        private final Map<String, BuildingPart> parts = new LinkedHashMap<>();
        private final Map<String, Integer> variants = new LinkedHashMap<>();
        private final List<Placement> placements = new ArrayList<>();

        private Session(ServerLevel level, ChunkPos center, @Nullable City city, @Nullable CityPlan plan, String palette) {
            this.level = level;
            this.center = center;
            this.city = city;
            this.plan = plan == null ? null : new CityPlan(plan.writeToJSon());
            this.palette = new Palette(AssetRegistries.PALETTES.get(palette).writeToJSon());
            if (city != null) {
                variants.putAll(CityTools.getVariantSelections(level.getSeed(), city));
            }
        }

        private BuildingPart part(String name) {
            return parts.computeIfAbsent(name, key -> new BuildingPart(AssetRegistries.PARTS.get(key).writeToJSon()));
        }

        private void layout() {
            placements.clear();
            int width = plan.getPlan().get(0).length();
            int depth = plan.getPlan().size();
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    int cx = center.x + x - width / 2;
                    int cz = center.z + z - depth / 2;
                    int y = city == null ? CityTools.getStationHeight() : CityTools.getCityHeight(level, city);
                    List<Placement> column = new ArrayList<>();
                    for (List<String> layer : List.of(plan.getCellar(), plan.getPlan(), plan.getLayer2(), plan.getTop())) {
                        if (layer.isEmpty()) {
                            continue;
                        }
                        char symbol = layer.get(z).charAt(x);
                        if (symbol == ' ') {
                            continue;
                        }
                        PartPalette choice = plan.getPartPalette().get(symbol);
                        int index = variants.getOrDefault(choice.getVariant(), 0);
                        String name = choice.getPalette().get(Math.min(index, choice.getPalette().size() - 1));
                        BuildingPart part = part(name);
                        if (layer == plan.getCellar()) {
                            y -= part.getSliceCount();
                        }
                        column.add(new Placement(new BlockPos(cx * 16, y, cz * 16), name, symbol));
                        y += part.getSliceCount();
                    }
                    placements.addAll(column);
                }
            }
        }

        public List<Placement> getPlacements() {
            return List.copyOf(placements);
        }

        public String getName() {
            return plan == null ? placements.get(0).part : plan.getName();
        }

        public Path exportPath() {
            if (!getName().matches("[A-Za-z0-9_@.-]+")) {
                throw new IllegalStateException("Invalid export name");
            }
            return FMLPaths.CONFIGDIR.get().resolve("ariente/exports").resolve(getName() + ".json");
        }

        public void restore() {
            for (Placement placement : placements) {
                restorePart(level, placement.origin, part(placement.part), palette);
            }
        }

        private Placement current(BlockPos pos) {
            return placements.stream().filter(p -> {
                BuildingPart part = part(p.part);
                return pos.getX() >= p.origin.getX() && pos.getX() < p.origin.getX() + part.getXSize()
                        && pos.getZ() >= p.origin.getZ() && pos.getZ() < p.origin.getZ() + part.getZSize()
                        && pos.getY() >= p.origin.getY() && pos.getY() < p.origin.getY() + part.getSliceCount();
            }).findFirst().orElseThrow(() -> new IllegalStateException("Stand inside the part to edit"));
        }

        public void sync(BlockPos pos) {
            Placement placement = current(pos);
            parts.put(placement.part, capturePart(level, placement.origin, part(placement.part), palette));
            for (Placement copy : placements) {
                if (copy.part.equals(placement.part) && !copy.equals(placement)) {
                    restorePart(level, copy.origin, part(copy.part), palette);
                }
            }
        }

        private void captureAll() {
            Map<String, BuildingPart> captured = new LinkedHashMap<>();
            for (Placement placement : placements) {
                BuildingPart snapshot = capturePart(level, placement.origin, part(placement.part), palette);
                BuildingPart previous = captured.putIfAbsent(placement.part, snapshot);
                if (previous != null && !previous.writeToJSon().equals(snapshot.writeToJSon())) {
                    throw new IllegalStateException("Different copies of " + placement.part + ": stand in the intended copy and run syncchunk first");
                }
            }
            parts.putAll(captured);
        }

        public void save() throws IOException {
            // Every occurrence of a shared part must agree. syncchunk selects the authoritative occurrence.
            captureAll();
            JsonArray assets = new JsonArray();
            if (plan != null) {
                assets.add(plan.writeToJSon());
                plan.getPartPalette().values().forEach(choice -> choice.getPalette().forEach(this::part));
            }
            parts.values().stream().sorted(Comparator.comparing(BuildingPart::getName)).forEach(part -> assets.add(part.writeToJSon()));
            assets.add(palette.writeToJSon());
            Path destination = exportPath();
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), getName(), ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    new GsonBuilder().setPrettyPrinting().create().toJson(assets, writer);
                }
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }

        public void reload() throws IOException {
            // Parse all assets before changing the session or touching blocks.
            Map<String, BuildingPart> loaded = new LinkedHashMap<>();
            Palette loadedPalette = null;
            CityPlan loadedPlan = null;
            try (Reader reader = Files.newBufferedReader(exportPath(), StandardCharsets.UTF_8)) {
                for (var entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                    JsonObject object = entry.getAsJsonObject();
                    switch (object.get("type").getAsString()) {
                        case "part" -> {
                            BuildingPart part = new BuildingPart(object);
                            loaded.put(part.getName(), part);
                        }
                        case "palette" -> loadedPalette = new Palette(object);
                        case "plan" -> loadedPlan = new CityPlan(object);
                        default -> throw new IllegalStateException("Unknown asset type");
                    }
                }
            }
            if (loadedPalette == null || (plan == null) != (loadedPlan == null)
                    || plan != null && !loadedPlan.getName().equals(plan.getName())) {
                throw new IllegalStateException("Export does not match this session");
            }
            if (plan == null && placements.stream().anyMatch(placement -> !loaded.containsKey(placement.part))) {
                throw new IllegalStateException("Export is missing the edited part");
            }
            for (BuildingPart part : loaded.values()) {
                if (part.getXSize() < 1 || part.getXSize() > 16 || part.getZSize() < 1 || part.getZSize() > 16 || part.getSliceCount() < 1) {
                    throw new IllegalStateException("Invalid part dimensions: " + part.getName());
                }
                for (var slice : part.getSlices()) {
                    if (!loadedPalette.getPalette().keySet().containsAll(slice.getSlice())) {
                        throw new IllegalStateException("Missing palette entry in " + part.getName());
                    }
                }
            }
            if (loadedPlan != null) {
                for (PartPalette choice : loadedPlan.getPartPalette().values()) {
                    if (!loaded.keySet().containsAll(choice.getPalette())) {
                        throw new IllegalStateException("Export is missing a referenced part");
                    }
                }
            }
            parts.clear();
            parts.putAll(loaded);
            palette = loadedPalette;
            plan = loadedPlan;
            if (plan != null) {
                layout();
            }
            restore();
        }

        public String variant(BlockPos pos) {
            Placement placement = current(pos);
            if (plan == null || placement.symbol == null) {
                throw new IllegalStateException("Variants require a city or station plan");
            }
            return plan.getPartPalette().get(placement.symbol).getVariant();
        }

        public String variants() {
            return plan == null ? "No plan variants" : plan.getVariants().toString() + "; selected " + variants;
        }

        public void createVariant(String name, int count) {
            if (plan == null || plan.getVariants().containsKey(name)) {
                throw new IllegalStateException("No plan, or variant already exists");
            }
            plan.getVariants().put(name, count);
            variants.put(name, 0);
        }

        public void setVariant(BlockPos pos, String name) {
            if (plan == null || !plan.getVariants().containsKey(name)) {
                throw new IllegalStateException("Create the variant first");
            }
            captureAll();
            Placement placement = current(pos);
            PartPalette choice = plan.getPartPalette().get(placement.symbol);
            String original = choice.getPalette().get(0);
            int count = plan.getVariants().get(name);
            while (choice.getPalette().size() < count) {
                String copy = original + "@" + name + "_" + choice.getPalette().size();
                if (parts.containsKey(copy) || AssetRegistries.PARTS.get(copy) != null) {
                    throw new IllegalStateException("Part name already exists: " + copy);
                }
                BuildingPart part = new BuildingPart(part(original).writeToJSon());
                part.setName(copy);
                parts.put(copy, part);
                choice.getPalette().add(copy);
            }
            choice.setVariant(name);
            layout();
            restore();
        }

        public void switchVariant(String name, int index) {
            if (plan == null || index < 0 || index >= plan.getVariants().getOrDefault(name, 0)) {
                throw new IllegalStateException("Unknown variant or index outside its range");
            }
            captureAll();
            variants.put(name, index);
            layout();
            restore();
        }
    }

    public static Session begin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(DimensionRegister.DIMENSION) || !player.isCreative()) {
            throw new IllegalStateException("Edit mode requires creative mode in the Ariente dimension");
        }
        if (SESSIONS.containsKey(player.getUUID())) {
            throw new IllegalStateException("Already editing; savecity then editmode off to finish");
        }
        ChunkPos chunk = player.chunkPosition();
        ChunkPos center = CityTools.getNearestDungeonCenter(chunk.x, chunk.z);
        if (SESSIONS.values().stream().anyMatch(session -> session.level == level && session.center.equals(center))) {
            throw new IllegalStateException("Another editor is already using this city/station region");
        }
        City city = CityTools.getCity(level.getSeed(), center);
        Session session;
        boolean station = CityTools.isStationChunk(chunk.x, chunk.z) && player.getY() >= 30 && player.getY() < 48
                && CityTools.getStationPart(level.getSeed(), chunk.x, chunk.z) != null;
        if (station || CityTools.isDungeonChunk(level.getSeed(), chunk.x, chunk.z)) {
            CityPlan plan = station ? AssetRegistries.CITYPLANS.get("station") : city.getPlan();
            session = new Session(level, center, station ? null : city, plan, plan.getPalette());
            session.layout();
        } else if (ArienteLandscapeCity.isLandscapeCityChunk(level.getSeed(), chunk.x, chunk.z)) {
            String name = ArienteLandscapeCity.getBuildingPart(chunk.x, chunk.z).getLeft();
            session = new Session(level, center, null, null, ArienteLandscapeCity.CITY_PALETTE);
            session.part(name);
            session.placements.add(new Placement(new BlockPos(chunk.getMinBlockX(), ArienteLandscapeCity.getBuildingYOffset(chunk.x, chunk.z), chunk.getMinBlockZ()), name, null));
        } else {
            throw new IllegalStateException("Stand inside a city, station, or landscape city plot");
        }
        SESSIONS.put(player.getUUID(), session);
        try {
            session.restore();
        } catch (RuntimeException exception) {
            SESSIONS.remove(player.getUUID());
            throw exception;
        }
        if (!station && session.city != null) {
            CityAISystem.getCityAISystem(level).getCityAI(center).enableEditMode(level);
        }
        return session;
    }

    public static Session session(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.level != player.level() || !player.isCreative()) {
            throw new IllegalStateException("Enable editmode here in creative mode first");
        }
        return session;
    }

    public static void end(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    public static boolean isEditing(Level level, ChunkPos center) {
        return SESSIONS.values().stream().anyMatch(session -> session.level == level && session.center.equals(center) && session.city != null);
    }

    public static void restorePart(ServerLevel level, BlockPos origin, BuildingPart part, Palette palette) {
        for (int y = 0; y < part.getSliceCount(); y++) {
            for (int z = 0; z < part.getZSize(); z++) {
                for (int x = 0; x < part.getXSize(); x++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = palette.getPalette().get(part.getC(x, y, z));
                    if (state == null || !level.isInWorldBounds(pos)) {
                        throw new IllegalStateException("Invalid part state or height in " + part.getName());
                    }
                    // Clear stale inventories/configuration even when the block type stays the same.
                    if (level.getBlockEntity(pos) != null) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    Map<String, Object> data = part.getTeInfo().get(new BlockPos(x, y, z));
                    BlockEntity tile = level.getBlockEntity(pos);
                    if (data != null && tile instanceof ICityEquipment equipment) {
                        equipment.load(data);
                        tile.setChanged();
                    } else if (data != null && tile instanceof SpawnerBlockEntity spawner && data.get("mob") instanceof String mob) {
                        EntityType.byString(mob).ifPresent(type -> spawner.setEntityId(type, level.random));
                        spawner.setChanged();
                    }
                }
            }
        }
    }

    public static BuildingPart capturePart(ServerLevel level, BlockPos origin, BuildingPart original, Palette palette) {
        Map<BlockState, PaletteIndex> mapping = new HashMap<>();
        palette.getPalette().forEach((index, state) -> mapping.putIfAbsent(state, index));
        int next = 0;
        BuildingPart.PalettedSlice[] slices = new BuildingPart.PalettedSlice[original.getSliceCount()];
        Map<BlockPos, Map<String, Object>> data = new HashMap<>();
        for (int y = 0; y < slices.length; y++) {
            slices[y] = new BuildingPart.PalettedSlice();
            for (int z = 0; z < original.getZSize(); z++) {
                for (int x = 0; x < original.getXSize(); x++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Registration.INVISIBLE_DOOR.get())) {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    PaletteIndex index = mapping.get(state);
                    if (index == null) {
                        do {
                            if (next >= INDEX_CHARS.length() * INDEX_CHARS.length()) {
                                throw new IllegalStateException("Palette exhausted");
                            }
                            index = new PaletteIndex(INDEX_CHARS.charAt(next % INDEX_CHARS.length()), INDEX_CHARS.charAt(next / INDEX_CHARS.length()));
                            next++;
                        } while (palette.getPalette().containsKey(index));
                        palette.addMapping(index, state);
                        mapping.put(state, index);
                    }
                    slices[y].getSlice().add(index);
                    BlockEntity tile = level.getBlockEntity(pos);
                    if (tile instanceof ICityEquipment equipment && equipment.save() != null) {
                        data.put(new BlockPos(x, y, z), equipment.save());
                    } else if (tile instanceof SpawnerBlockEntity spawner) {
                        spawner.getSpawner().getOrCreateDisplayEntity(level, level.random, pos);
                        String mob = spawner.saveWithoutMetadata().getCompound("SpawnData").getCompound("entity").getString("id");
                        data.put(new BlockPos(x, y, z), Map.of("mob", mob));
                    }
                }
            }
        }
        BuildingPart part = new BuildingPart(original.getName(), original.getXSize(), original.getZSize(), slices, data);
        JsonObject json = part.writeToJSon();
        JsonObject old = original.writeToJSon();
        if (old.has("meta")) {
            json.add("meta", old.get("meta").deepCopy());
        }
        return new BuildingPart(json);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        SESSIONS.clear();
    }
}
