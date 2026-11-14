package mcjty.arienteworld.dimension;

import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.Transform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;

/** The two-block landing extensions are placed by their own exterior chunk. */
public final class CubeLandings {

    private CubeLandings() {
    }

    public static void generate(CityChunk writer, ChunkPos chunk, City city, ChunkGenerator generator) {
        if (!city.getPlan().getName().equals("city5_1")) {
            return;
        }
        for (Direction outward : Direction.Plane.HORIZONTAL) {
            int neighborX = chunk.x() - outward.getStepX();
            int neighborZ = chunk.z() - outward.getStepZ();
            String entrance = switch (outward) {
                case NORTH -> "c5_t1";
                case SOUTH -> "c5_z1";
                case WEST -> "c5_v1";
                default -> "c5_x1";
            };
            var parts = CityTools.getBuildingParts(writer.getLevel().getSeed(), city, neighborX, neighborZ);
            if (parts.stream().noneMatch(part -> part.getName().equals(entrance))) {
                continue;
            }
            int y = CityTools.getCityHeight(city, generator, writer.getLevel(),
                    writer.getLevel().getLevel().getChunkSource().randomState())
                    - CityTools.getCellarHeight(writer.getLevel().getSeed(), city, neighborX, neighborZ);
            for (BuildingPart part : parts) {
                if (part.getName().equals(entrance)) {
                    Transform transform = switch (outward) {
                        case NORTH -> Transform.ROTATE_NONE;
                        case SOUTH -> Transform.ROTATE_180;
                        case WEST -> Transform.ROTATE_270;
                        default -> Transform.ROTATE_90;
                    };
                    Palette palette = AssetRegistries.PALETTES.get(city.getPlan().getPalette());
                    CityFeature.placePart(writer.getLevel(), new BlockPos(chunk.getMinBlockX(), y, chunk.getMinBlockZ()),
                            AssetRegistries.PARTS.get("c5_landing"), palette, transform);
                    break;
                }
                y += part.getSliceCount();
            }
        }
    }
}
