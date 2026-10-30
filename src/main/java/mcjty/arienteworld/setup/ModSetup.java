package mcjty.arienteworld.setup;

import mcjty.arienteworld.cities.AssetRegistries;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import mcjty.lib.compat.MainCompatHandler;
import mcjty.lib.setup.DefaultModSetup;

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
