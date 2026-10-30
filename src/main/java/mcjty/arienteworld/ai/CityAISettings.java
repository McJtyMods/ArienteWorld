package mcjty.arienteworld.ai;

import net.minecraft.nbt.CompoundTag;

/**
 * Randomized settings specific to a single city
 */
public class CityAISettings {

    private int numSentinels;

    public int getNumSentinels() {
        return numSentinels;
    }

    public void setNumSentinels(int numSentinels) {
        this.numSentinels = numSentinels;
    }

    public void readFromNBT(CompoundTag compound) {
        numSentinels = compound.getInt("sentinels");
    }

    public void writeToNBT(CompoundTag compound) {
        compound.putInt("sentinels", numSentinels);
    }

}
