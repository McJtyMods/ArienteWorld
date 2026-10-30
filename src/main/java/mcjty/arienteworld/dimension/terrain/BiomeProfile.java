package mcjty.arienteworld.dimension.terrain;

public enum BiomeProfile {
    PLAINS("ariente_plains", .125f, .05f),
    HILLS("ariente_hills", .45f, .3f),
    OCEAN("ariente_ocean", -1, .1f),
    FOREST("ariente_forest", .125f, .05f),
    ROUGH("ariente_rough", .25f, .5f),
    CITY("ariente_city", .125f, .05f);

    public static final BiomeProfile[] VALUES = values();
    private final String id;
    private final float height;
    private final float variation;

    BiomeProfile(String id, float height, float variation) {
        this.id = id;
        this.height = height;
        this.variation = variation;
    }

    public String getId() {
        return id;
    }

    public float getBaseHeight() {
        return height;
    }

    public float getHeightVariation() {
        return variation;
    }
}
