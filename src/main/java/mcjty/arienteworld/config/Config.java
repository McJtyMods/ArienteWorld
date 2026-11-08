package mcjty.arienteworld.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    static {
        AIConfiguration.init(SERVER_BUILDER, CLIENT_BUILDER);
        WorldgenConfiguration.init(SERVER_BUILDER, CLIENT_BUILDER);
        LootConfiguration.init(SERVER_BUILDER, CLIENT_BUILDER);
        SpawningConfiguration.init(SERVER_BUILDER);

        SERVER_CONFIG = SERVER_BUILDER.build();
        CLIENT_CONFIG = CLIENT_BUILDER.build();
    }

    public static ModConfigSpec SERVER_CONFIG;
    public static ModConfigSpec CLIENT_CONFIG;

    public static String[] ASSETS = new String[] {
            "/assets/arienteworld/citydata/city0_0.json",
            "/assets/arienteworld/citydata/city0_1.json",
            "/assets/arienteworld/citydata/city1_1.json",
            "/assets/arienteworld/citydata/city1_2.json",
            "/assets/arienteworld/citydata/city2_1.json",
            "/assets/arienteworld/citydata/city3_1.json",
            "/assets/arienteworld/citydata/city4_1.json",
            "/assets/arienteworld/citydata/city5_1.json",
            "/assets/arienteworld/citydata/general.json",
            "/assets/arienteworld/citydata/station.json",
            "/assets/arienteworld/citydata/landscapecities.json",
            "$ariente/userassets.json"
    };

}
