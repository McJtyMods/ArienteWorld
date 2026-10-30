package mcjty.arienteworld.dimension;

import mcjty.arienteworld.ArienteWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class DimensionRegister {

    public static final ResourceLocation DIMENSION_ID = new ResourceLocation(ArienteWorld.MODID, "ariente");
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, DIMENSION_ID);
}
