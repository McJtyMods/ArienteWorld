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

    public static final RegistryObject<SoundEvent> step = Registration.SOUNDS.register("step", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArienteWorld.MODID, "step")));
    public static final RegistryObject<SoundEvent> droneAmbient = Registration.SOUNDS.register("drone_ambient", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArienteWorld.MODID, "drone_ambient")));
    public static final RegistryObject<SoundEvent> droneHurt = Registration.SOUNDS.register("drone_hurt", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArienteWorld.MODID, "drone_hurt")));
    public static final RegistryObject<SoundEvent> droneDeath = Registration.SOUNDS.register("drone_death", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArienteWorld.MODID, "drone_death")));
    public static final RegistryObject<SoundEvent> sentinelDeath = Registration.SOUNDS.register("sentinel_death", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArienteWorld.MODID, "sentinel_death")));
    public static final RegistryObject<SoundEvent> soldierHurt = Registration.SOUNDS.register("soldier_hurt", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArienteWorld.MODID, "soldier_hurt")));
    public static final RegistryObject<SoundEvent> soldierDeath = Registration.SOUNDS.register("soldier_death", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArienteWorld.MODID, "soldier_death")));
    public static final RegistryObject<SoundEvent> bossSoldierHurt = Registration.SOUNDS.register("boss_soldier_hurt", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArienteWorld.MODID, "boss_soldier_hurt")));
    public static final RegistryObject<SoundEvent> bossSoldierDeath = Registration.SOUNDS.register("boss_soldier_death", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArienteWorld.MODID, "boss_soldier_death")));

    public static void register() {
        // Initialize the sound entries before attaching the deferred register.
    }
}
