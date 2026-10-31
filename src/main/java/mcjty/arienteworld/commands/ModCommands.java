package mcjty.arienteworld.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.oregen.OverworldDungeonGen;
import net.minecraft.world.level.Level;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityPlan;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.PortalGenerator;
import mcjty.arienteworld.dimension.ArienteLandscapeCity;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.stream.StreamSupport;

@Mod.EventBusSubscriber(modid = ArienteWorld.MODID)
public class ModCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal(ArienteWorld.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("findcity")
                        .executes(context -> findCity(context.getSource(), null))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Stream.concat(Stream.of("surface", "underground", "station"),
                                                StreamSupport.stream(AssetRegistries.CITYPLANS.getIterable().spliterator(), false)
                                                        .filter(CityPlan::isCity).map(CityPlan::getName)), builder))
                                .executes(context -> findCity(context.getSource(), StringArgumentType.getString(context, "name"))))));
        dispatcher.register(Commands.literal(ArienteWorld.MODID).requires(source -> source.hasPermission(2))
                .then(Commands.literal("findentrance").executes(context -> findEntrance(context.getSource()))));
        EditorCommands.register(root);
        dispatcher.register(Commands.literal("arworld").requires(source -> source.hasPermission(2)).redirect(root));
    }

    private static int findEntrance(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
        BlockPos start = BlockPos.containing(source.getPosition());
        BlockPos nearest = null;
        double distance = Double.MAX_VALUE;
        for (int x = (start.getX() >> 4) - 64; x <= (start.getX() >> 4) + 64; x++) {
            for (int z = (start.getZ() >> 4) - 64; z <= (start.getZ() >> 4) + 64; z++) {
                if (OverworldDungeonGen.isDungeonChunk(level, x, z)) {
                    BlockPos candidate = OverworldDungeonGen.getDungeonPos(level, x, z).above(2);
                    double d = candidate.distToCenterSqr(start.getX(), candidate.getY(), start.getZ());
                    if (d < distance) {
                        distance = d;
                        nearest = candidate;
                    }
                }
            }
        }
        if (nearest == null) {
            source.sendFailure(Component.literal("No entrance site within 1024 blocks"));
            return 0;
        }
        BlockPos target = nearest;
        String teleport = "/execute in minecraft:overworld run tp @s " + target.getX() + " " + target.getY() + " " + target.getZ();
        source.sendSuccess(() -> Component.literal("Overworld entrance site at " + target.getX() + ", " + target.getY() + ", " + target.getZ()
                + " (requires new chunks or an intact generated entrance)").withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, teleport))), false);
        return 1;
    }

    @Nullable
    public static BlockPos findDistrict(ServerLevel level, BlockPos start, boolean underground) {
        List<ChunkPos> candidates = new ArrayList<>();
        int cx = start.getX() >> 4;
        int cz = start.getZ() >> 4;
        for (int x = cx - 128; x <= cx + 128; x += 2) {
            for (int z = cz - 128; z <= cz + 128; z += 2) {
                if (ArienteLandscapeCity.isLandscapeCityChunk(level.getSeed(), x, z)
                        && !PortalGenerator.isPortalChunk(x, z)
                        && !CityTools.isDungeonChunk(level.getSeed(), x, z)) {
                    candidates.add(new ChunkPos(x, z));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> start.distToCenterSqr(pos.getMiddleBlockX(), start.getY(), pos.getMiddleBlockZ())));
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        for (ChunkPos candidate : candidates) {
            int x = candidate.getMiddleBlockX();
            int z = candidate.getMiddleBlockZ();
            boolean covered = !generator.getBaseColumn(x, z, level, random).getBlock(80).isAir();
            if (covered != underground) {
                continue;
            }
            if (underground) {
                // Prefer the interior of a covered district, not a single plot at an island's edge.
                boolean interior = true;
                for (int dx = -1; dx <= 1 && interior; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (!ArienteLandscapeCity.isLandscapeCityChunk(level.getSeed(), candidate.x + dx, candidate.z + dz)
                                || PortalGenerator.isPortalChunk(candidate.x + dx, candidate.z + dz)
                                || CityTools.isDungeonChunk(level.getSeed(), candidate.x + dx, candidate.z + dz)
                                || generator.getBaseColumn(x + dx * 16, z + dz * 16, level, random).getBlock(80).isAir()) {
                            interior = false;
                            break;
                        }
                    }
                }
                if (!interior) {
                    continue;
                }
            }
            int roof = ArienteLandscapeCity.getBuildingYOffset(candidate.x, candidate.z)
                    + AssetRegistries.PARTS.get(ArienteLandscapeCity.getBuildingPart(candidate.x, candidate.z).getLeft()).getSliceCount();
            int y = underground ? 73 : Math.max(roof, generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random)) + 16;
            return new BlockPos(x, y, z);
        }
        return null;
    }

    private static void reportLocation(CommandSourceStack source, String label, BlockPos pos) {
        String command = "/execute in arienteworld:ariente run tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        source.sendSuccess(() -> Component.literal(label + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))), false);
    }

    @Nullable
    public static ChunkPos findStation(ServerLevel level, BlockPos start) {
        ChunkPos origin = CityTools.getNearestDungeonCenter(start.getX() >> 4, start.getZ() >> 4);
        ChunkPos nearest = null;
        double distance = Double.MAX_VALUE;
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                ChunkPos candidate = new ChunkPos(origin.x + x * 16, origin.z + z * 16);
                if (CityTools.getStationPart(level.getSeed(), candidate.x, candidate.z) == null) {
                    continue;
                }
                double d = start.distToCenterSqr(candidate.getMiddleBlockX(), start.getY(), candidate.getMiddleBlockZ());
                if (d < distance) {
                    nearest = candidate;
                    distance = d;
                }
            }
        }
        return nearest;
    }

    private static int findCity(CommandSourceStack source, @Nullable String planName) {
        ServerLevel level = source.getServer().getLevel(DimensionRegister.DIMENSION);
        if (level == null) {
            source.sendFailure(Component.literal("Ariente dimension is not loaded"));
            return 0;
        }
        if ("surface".equals(planName) || "underground".equals(planName)) {
            BlockPos target = findDistrict(level, BlockPos.containing(source.getPosition()), "underground".equals(planName));
            if (target == null) {
                source.sendFailure(Component.literal("No " + planName + " city district within 2048 blocks"));
                return 0;
            }
            reportLocation(source, planName + " city district", target);
            return 1;
        }
        if ("station".equals(planName)) {
            ChunkPos center = findStation(level, BlockPos.containing(source.getPosition()));
            if (center == null) {
                source.sendFailure(Component.literal("No station within 2048 blocks"));
                return 0;
            }
            reportLocation(source, "Station (Y=30)", new BlockPos(center.getMiddleBlockX(), 33, center.getMiddleBlockZ()));
            return 1;
        }
        if (planName != null && (AssetRegistries.CITYPLANS.get(planName) == null || !AssetRegistries.CITYPLANS.get(planName).isCity())) {
            source.sendFailure(Component.literal("Unknown city plan: " + planName));
            return 0;
        }
        BlockPos start = BlockPos.containing(source.getPosition());
        ChunkPos origin = CityTools.getNearestDungeonCenter(start.getX() >> 4, start.getZ() >> 4);
        City nearest = null;
        double distance = Double.MAX_VALUE;
        // Query layout data only; searching must never generate or load chunks.
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                City city = CityTools.getCity(level.getSeed(), new ChunkPos(origin.x + x * 16, origin.z + z * 16));
                if (!CityTools.isCityCenter(level.getSeed(), city.getCenter().x, city.getCenter().z)) {
                    continue;
                }
                if (planName != null && !city.getPlan().getName().equals(planName)) {
                    continue;
                }
                double dx = city.getCenter().getMiddleBlockX() - start.getX();
                double dz = city.getCenter().getMiddleBlockZ() - start.getZ();
                double candidate = dx * dx + dz * dz;
                if (candidate < distance) {
                    nearest = city;
                    distance = candidate;
                }
            }
        }
        if (nearest == null) {
            source.sendFailure(Component.literal("No matching city within 2048 blocks"));
            return 0;
        }
        int x = nearest.getCenter().getMiddleBlockX();
        int y = CityTools.getCityHeight(level, nearest);
        int z = nearest.getCenter().getMiddleBlockZ();
        String message = nearest.getName() + " (" + nearest.getPlan().getName() + ") at " + x + ", " + y + ", " + z + " in Ariente";
        int roof = CityTools.getLowestHeight(level, nearest, nearest.getCenter().x, nearest.getCenter().z)
                + CityTools.getBuildingParts(level.getSeed(), nearest, nearest.getCenter().x, nearest.getCenter().z).stream()
                .mapToInt(BuildingPart::getSliceCount).sum();
        int terrain = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                level, level.getChunkSource().randomState());
        String teleport = "/execute in arienteworld:ariente run tp @s " + x + " " + (Math.max(roof, terrain) + 16) + " " + z;
        source.sendSuccess(() -> Component.literal(message).withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, teleport))), false);
        return 1;
    }
}
