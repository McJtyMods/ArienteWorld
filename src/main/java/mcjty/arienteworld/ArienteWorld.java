package mcjty.arienteworld;

import mcjty.ariente.api.IArienteWorld;
import mcjty.arienteworld.apiimpl.ArienteWorldImplementation;
import mcjty.arienteworld.config.Config;
import mcjty.arienteworld.setup.ClientSetup;
import mcjty.arienteworld.setup.ModSetup;
import mcjty.arienteworld.setup.Registration;
import mcjty.hologui.api.IHoloGuiHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.function.Function;
import java.util.function.Supplier;

@Mod(ArienteWorld.MODID)
public class ArienteWorld {
    public static final String MODID = "arienteworld";
    public static final String ARIENTE_MODID = "ariente";

    public static ModSetup setup = new ModSetup();

    public static ArienteWorldImplementation arienteWorldImplementation = new ArienteWorldImplementation();

    public static IHoloGuiHandler guiHandler;

    public ArienteWorld() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_CONFIG);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SERVER_CONFIG);
        Registration.register();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(setup::init);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientSetup::init);
        });
    }

    private void processIMC(final InterModProcessEvent event) {
        event.getIMCStream().forEach(message -> {
            if ("getArienteWorld".equalsIgnoreCase(message.getMethod())) {
                Supplier<Function<IArienteWorld, Void>> supplier = message.getMessageSupplier();
                supplier.get().apply(arienteWorldImplementation);
            }
        });
    }
}
