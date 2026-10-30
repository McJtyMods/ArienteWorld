package mcjty.arienteworld;

import mcjty.arienteworld.setup.Registration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {

    public static final RegistryObject<SoundEvent> MUSIC = Registration.SOUNDS.register("music",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArienteWorld.MODID, "music")));
    public static final RegistryObject<SoundEvent> AMBIENT = Registration.SOUNDS.register("ambient",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArienteWorld.MODID, "ambient")));

    public static void register() {
        // Initialize the sound entries before attaching the deferred register.
    }
}
