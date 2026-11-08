package mcjty.arienteworld.dimension;

import java.util.List;
import javax.annotation.Nullable;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = ArienteWorld.MODID)
public class SoldierSpawning {

    private static final ResourceKey<Biome> CITY_BIOME = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "ariente_city"));

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
        // Reuse the city's datapack spawn table, including the same entries during pack-position validation.
        var spawns = level.registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(CITY_BIOME)
                .getMobSettings().getMobs(MobCategory.MONSTER);
        List.copyOf(event.getSpawnerDataList()).forEach(event::removeSpawnerData);
        spawns.unwrap().forEach(event::addSpawnerData);
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
