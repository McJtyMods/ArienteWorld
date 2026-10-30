package mcjty.arienteworld.setup;

import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.RegisterDimensionsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DimensionSetup {

    @SubscribeEvent
    public static void onDimensionRegistry(RegisterDimensionsEvent event) {
        DimensionRegister.dimensionType = DimensionManager.registerOrGetDimension(DimensionRegister.DIMENSION_ID, Registration.DIMENSION.get(), null, true);
    }
}
