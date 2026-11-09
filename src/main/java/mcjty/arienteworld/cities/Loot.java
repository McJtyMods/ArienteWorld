package mcjty.arienteworld.cities;

import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;

public class Loot {

    @Nullable private final Identifier id;
    private final int meta;
    private final boolean blueprint;
    private final int maxAmount;
    private final float chance;

    // null id means a random blueprint
    public Loot(@Nullable Identifier id, int meta, boolean blueprint, int maxAmount, float chance) {
        this.id = id;
        this.meta = meta;
        this.blueprint = blueprint;
        this.maxAmount = maxAmount;
        this.chance = chance;
    }

    @Nullable
    public Identifier getId() {
        return id;
    }

    public int getMeta() {
        return meta;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public float getChance() {
        return chance;
    }

    public boolean isBlueprint() {
        return blueprint;
    }
}
