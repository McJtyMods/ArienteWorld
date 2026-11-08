package mcjty.arienteworld.setup;

import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.lib.compat.MainCompatHandler;
import mcjty.lib.setup.DefaultModSetup;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

public class ModSetup extends DefaultModSetup {

    @Override
    public void init(FMLCommonSetupEvent event) {
        super.init(event);
        event.enqueueWork(AssetRegistries::initialize);
    }

    @Override
    protected void setupModCompat() {
        MainCompatHandler.registerTOP();
    }
}
