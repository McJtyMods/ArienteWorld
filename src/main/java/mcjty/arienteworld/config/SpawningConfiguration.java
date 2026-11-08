package mcjty.arienteworld.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SpawningConfiguration {

    public static ModConfigSpec.IntValue DARKBAT_MAX_PER_PLAYER;

    public static void init(ModConfigSpec.Builder serverBuilder) {
        serverBuilder.comment("Natural mob spawning settings").push("spawning");
        DARKBAT_MAX_PER_PLAYER = serverBuilder
                .comment("Maximum living Darkbats within 128 horizontal blocks of each nearby non-spectator player, at any height.",
                        "Natural spawning stops when any nearby player reaches this limit. Set to 0 to disable natural Darkbat spawning.",
                        "Existing Darkbats are not removed; summoned and spawner mobs count toward the limit but can still be created.")
                .defineInRange("darkbatMaxPerPlayer", 3, 0, 64);
        serverBuilder.pop();
    }
}
