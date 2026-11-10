package mcjty.arienteworld;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import mcjty.ariente.compat.arienteworld.ArienteWorldCompat;
import mcjty.arienteworld.apiimpl.ArienteWorldImplementation;
import mcjty.arienteworld.config.Config;
import mcjty.arienteworld.setup.FabricEvents;
import mcjty.arienteworld.setup.ModSetup;
import mcjty.arienteworld.setup.Registration;
import mcjty.hologui.HoloGui;
import mcjty.hologui.api.IHoloGuiHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.fml.config.ModConfig;

public class ArienteWorld implements ModInitializer {

    public static final String MODID = "arienteworld";
    public static final String ARIENTE_MODID = "ariente";
    public static final ModSetup setup = new ModSetup();
    public static final ArienteWorldImplementation arienteWorldImplementation = new ArienteWorldImplementation();
    public static IHoloGuiHandler guiHandler;

    @Override
    public void onInitialize() {
        NeoForgeConfigRegistry.INSTANCE.register(MODID, ModConfig.Type.CLIENT, Config.CLIENT_CONFIG);
        NeoForgeConfigRegistry.INSTANCE.register(MODID, ModConfig.Type.SERVER, Config.SERVER_CONFIG);
        Registration.register();
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_STRUCTURES, ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.parse("arienteworld:overworld_dungeon")));
        guiHandler = HoloGui.guiHandler;
        new ArienteWorldCompat.GetArienteWorld().apply(arienteWorldImplementation);
        setup.init();
        FabricEvents.register();
    }
}
