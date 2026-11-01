package mcjty.arienteworld.config;


import net.minecraftforge.common.ForgeConfigSpec;

public class WorldgenConfiguration {

    private static final String CATEGORY_WORLDGEN = "worldgen";

    public static ForgeConfigSpec.DoubleValue CITY_DUNGEON_CHANCE;
    public static ForgeConfigSpec.DoubleValue OVERWORLD_DUNGEON_CHANCE;

    public static void init(ForgeConfigSpec.Builder SERVER_BUILDER, ForgeConfigSpec.Builder CLIENT_BUILDER) {
        SERVER_BUILDER.comment("World generation settings").push(CATEGORY_WORLDGEN);

        CITY_DUNGEON_CHANCE = SERVER_BUILDER
                .comment("The chance that each seeded city grid spot has a dungeon complex (0 disables complexes, 1 enables every candidate).",
                        "Huge cube complexes additionally keep only half of their candidate sites.",
                        "Landscape city districts, stations, intercity routes and arrival portals are independent.",
                        "Applies to newly generated worlds; change before creating the world.")
                .worldRestart()
                .defineInRange("cityDungeonChance", .7, 0, 1);
        OVERWORLD_DUNGEON_CHANCE = SERVER_BUILDER
                .comment("The chance that a chunk in the overworld will have an Ariente dungeon")
                .defineInRange("overworldDungeonChance", .002, 0, 1);

        SERVER_BUILDER.pop();
    }
}
