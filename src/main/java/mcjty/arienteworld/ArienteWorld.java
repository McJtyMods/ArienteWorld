package mcjty.arienteworld;

import java.util.function.Function;
import java.util.function.Supplier;
import mcjty.ariente.api.IArienteWorld;
import mcjty.arienteworld.apiimpl.ArienteWorldImplementation;
import mcjty.arienteworld.config.Config;
import mcjty.arienteworld.setup.ClientSetup;
import mcjty.arienteworld.setup.ModSetup;
import mcjty.arienteworld.setup.Registration;
import mcjty.hologui.api.IHoloGuiHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;

@Mod(ArienteWorld.MODID)
public class ArienteWorld {
    public static final String MODID = "arienteworld";
    public static final String ARIENTE_MODID = "ariente";

    public static ModSetup setup = new ModSetup();

    public static ArienteWorldImplementation arienteWorldImplementation = new ArienteWorldImplementation();

    public static IHoloGuiHandler guiHandler;

    public ArienteWorld(ModContainer mod, IEventBus bus, Dist dist) {
        mod.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_CONFIG);
        mod.registerConfig(ModConfig.Type.SYNCED, Config.SERVER_CONFIG, "arienteworld-server.toml");
        Registration.register(bus);

        bus.addListener(setup::init);
        bus.addListener(this::processIMC);
        if (dist.isClient()) {
            bus.addListener(ClientSetup::init);
        }
    }

    private void processIMC(final InterModProcessEvent event) {
        event.getIMCStream().forEach(message -> {
            if ("getArienteWorld".equalsIgnoreCase(message.method())) {
                Supplier<Function<IArienteWorld, Void>> supplier = (Supplier<Function<IArienteWorld, Void>>) (Supplier<?>) message.messageSupplier();
                supplier.get().apply(arienteWorldImplementation);
            }
        });
    }
}
