package mcjty.arienteworld.ai;

import net.minecraft.nbt.CompoundNBT;

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

    public void readFromNBT(CompoundNBT compound) {
        numSentinels = compound.getInt("sentinels");
    }

    public void writeToNBT(CompoundNBT compound) {
        compound.putInt("sentinels", numSentinels);
    }

}
