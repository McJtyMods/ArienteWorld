package mcjty.arienteworld.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class AIConfiguration {

    private static final String CATEGORY_AI = "ai";

    public static ForgeConfigSpec.IntValue ALERT_TIME;
    public static ForgeConfigSpec.BooleanValue SCOUT_PATROLS;
    public static ForgeConfigSpec.IntValue SCOUT_LAUNCH_INTERVAL;

    public static void init(ForgeConfigSpec.Builder SERVER_BUILDER, ForgeConfigSpec.Builder CLIENT_BUILDER) {
        SERVER_BUILDER.comment("AI settings").push(CATEGORY_AI);

        ALERT_TIME = SERVER_BUILDER
                .comment("The amount of ticks (times 10) that the city will stay on alert after spotting a player. So 120 would be one minute")
                .defineInRange("alertTime", 400, 1, 100000000);
        SCOUT_PATROLS = SERVER_BUILDER.comment("Larger surface and floating bases occasionally launch soldier-piloted scout ships.",
                        "This is separate from natural mob spawning and does not run in Peaceful mode.")
                .define("scoutPatrols", true);
        SCOUT_LAUNCH_INTERVAL = SERVER_BUILDER.comment("Minimum seconds between scout launch opportunities at each base.",
                        "Repeat delays vary between this value and twice this value. First encounters are prepared within seconds.",
                        "Launches require distant players, clear space and room in the two-scout limit.")
                .defineInRange("scoutLaunchIntervalSeconds", 10, 5, 7200);

        SERVER_BUILDER.pop();
    }
}
