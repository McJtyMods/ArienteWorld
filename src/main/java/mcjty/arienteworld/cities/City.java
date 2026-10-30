package mcjty.arienteworld.cities;

import net.minecraft.world.level.ChunkPos;

public class City {

    private final ChunkPos center;
    private final CityPlan plan;
    private final String name;

    public City(ChunkPos center, CityPlan plan, String name) {
        this.center = center;
        this.plan = plan;
        this.name = name;
    }

    public ChunkPos getCenter() {
        return center;
    }

    public CityPlan getPlan() {
        return plan;
    }

    public String getName() {
        return name;
    }
}
