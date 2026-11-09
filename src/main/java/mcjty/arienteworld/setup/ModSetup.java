package mcjty.arienteworld.setup;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.lib.compat.MainCompatHandler;
import mcjty.lib.setup.DefaultModSetup;

public class ModSetup extends DefaultModSetup {

    @Override
    public void init() {
        super.init();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> AssetRegistries.initialize());
    }

    @Override
    protected void setupModCompat() {
        MainCompatHandler.registerTOP();
    }
}
