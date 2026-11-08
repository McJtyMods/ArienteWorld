package mcjty.arienteworld.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class LootConfiguration {

    private static final String CATEGORY_LOOT = "loot";

    public static ModConfigSpec.DoubleValue SOLDIER_CITYKEY_CHANCE;
    public static ModConfigSpec.DoubleValue SOLDIER_WEAPON_DROP_MULTIPLIER;

    public static void init(ModConfigSpec.Builder SERVER_BUILDER, ModConfigSpec.Builder CLIENT_BUILDER) {
        SERVER_BUILDER.comment("Loot settings").push(CATEGORY_LOOT);

        SOLDIER_CITYKEY_CHANCE = SERVER_BUILDER
                .comment("The chance that a killed soldier will drop a keycard")
                .defineInRange("soldierCitykeyChance", .2, 0, 1);
        SOLDIER_WEAPON_DROP_MULTIPLIER = SERVER_BUILDER
                .comment("Multiplier for natural soldier and Master Soldier Sabre/Lance equipment drops, including Looting.",
                        "Does not affect combat equipment, armor, keycards or guaranteed equipment drops.",
                        "0.05 makes finished weapons twenty times rarer than vanilla equipment drops.")
                .defineInRange("soldierWeaponDropMultiplier", .05, 0, 1);
        SERVER_BUILDER.pop();
    }
}
