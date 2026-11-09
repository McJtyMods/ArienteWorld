package mcjty.arienteworld.dimension;

import java.util.List;
import net.minecraft.util.valueproviders.ConstantInt;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = ArienteWorld.MODID)
public class SoldierSpawning {

    private static final Map<MobSpawnSettings, List<Weighted<MobSpawnSettings.SpawnerData>>> SMALL_DUNGEON_SPAWNS = new WeakHashMap<>();

    private static final ResourceKey<Biome> CITY_BIOME = ResourceKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(ArienteWorld.MODID, "ariente_city"));

    @SubscribeEvent
    public static void potentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER || !(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(DimensionRegister.DIMENSION)
                || !(level.getChunkSource().getGenerator() instanceof ArienteChunkGenerator)) {
            return;
        }
        City city = getDungeonAt(level, event.getPos());
        if (city == null) {
            return;
        }
        var settings = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(CITY_BIOME)
                .value().getAttributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, EnvironmentAttributes.NATURAL_MOB_SPAWNS.defaultValue());
        var entries = settings.getMobsToSpawn(MobCategory.MONSTER).unwrap();
        if (city.getPlan().isSmallDungeon()) {
            // Single-soldier patrols keep small rooms and islands from filling with packs.
            // Cache the entries: pack-position validation must see the same selected spawn data.
            entries = SMALL_DUNGEON_SPAWNS.computeIfAbsent(settings, source -> source.getMobsToSpawn(MobCategory.MONSTER).unwrap().stream().map(entry -> {
                var spawn = entry.value();
                if (spawn.type() == Registration.ENTITY_SOLDIER.get() || spawn.type() == Registration.ENTITY_MASTER_SOLDIER.get()) {
                    return new Weighted<>(new MobSpawnSettings.SpawnerData(spawn.type(), ConstantInt.of(1)), entry.weight());
                }
                return entry;
            }).toList());
        }
        List.copyOf(event.getSpawnerDataList()).forEach(event::removeSpawnerData);
        entries.forEach(event::addSpawnerData);
    }

    @Nullable
    static City getDungeonAt(ServerLevel level, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (PortalGenerator.isPortalChunk(chunkX, chunkZ)) {
            return null;
        }
        City city = CityTools.getCity(level.getSeed(), CityTools.getNearestDungeonCenter(chunkX, chunkZ));
        List<BuildingPart> parts = CityTools.getBuildingParts(level.getSeed(), city, chunkX, chunkZ);
        if (parts.isEmpty()) {
            return null;
        }
        int bottom = CityTools.getLowestHeight(level, city, chunkX, chunkZ);
        int top = bottom + parts.stream().mapToInt(BuildingPart::getSliceCount).sum();
        if (pos.getY() < bottom || pos.getY() >= top) {
            return null;
        }
        return city;
    }
}
