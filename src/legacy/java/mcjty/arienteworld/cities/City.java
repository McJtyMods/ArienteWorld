package mcjty.arienteworld.cities;

import mcjty.arienteworld.dimension.ChunkHeightmap;
import mcjty.arienteworld.dimension.IArienteChunkGenerator;
import net.minecraft.util.math.ChunkPos;

public class City {

    private final ChunkPos center;
    private final CityPlan plan;
    private final String name;
    private int height;

    public City(ChunkPos center, CityPlan plan, String name, int height) {
        this.center = center;
        this.plan = plan;
        this.name = name;
        this.height = height;
    }

    public ChunkPos getCenter() {
        return center;
    }

    public String getName() {
        return name;
    }

    public CityPlan getPlan() {
        return plan;
    }

    public int getHeight() {
        return height;
    }

    public int getHeight(IArienteChunkGenerator generator) {
        if (height == -1) {
            if (plan.isUnderground()) {
                height = 40;
            } else if (plan.isFloating()) {
                height = 100;
            } else {
                ChunkHeightmap heightmap = generator.getHeightmap(center.x, center.z);
                height = heightmap.getAverageHeight();
            }
        }
        return height;
    }
}
