package mcjty.arienteworld;

import java.util.function.Supplier;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class ModSounds {

    public static final Supplier<SoundEvent> MUSIC = Registration.SOUNDS.register("music",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "music")));
    public static final Supplier<SoundEvent> AMBIENT = Registration.SOUNDS.register("ambient",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "ambient")));

    public static final Supplier<SoundEvent> step = Registration.SOUNDS.register("step", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "step")));
    public static final Supplier<SoundEvent> droneAmbient = Registration.SOUNDS.register("drone_ambient", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "drone_ambient")));
    public static final Supplier<SoundEvent> droneHurt = Registration.SOUNDS.register("drone_hurt", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "drone_hurt")));
    public static final Supplier<SoundEvent> droneDeath = Registration.SOUNDS.register("drone_death", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "drone_death")));
    public static final Supplier<SoundEvent> sentinelDeath = Registration.SOUNDS.register("sentinel_death", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "sentinel_death")));
    public static final Supplier<SoundEvent> soldierHurt = Registration.SOUNDS.register("soldier_hurt", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "soldier_hurt")));
    public static final Supplier<SoundEvent> soldierDeath = Registration.SOUNDS.register("soldier_death", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "soldier_death")));
    public static final Supplier<SoundEvent> bossSoldierHurt = Registration.SOUNDS.register("boss_soldier_hurt", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "boss_soldier_hurt")));
    public static final Supplier<SoundEvent> bossSoldierDeath = Registration.SOUNDS.register("boss_soldier_death", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "boss_soldier_death")));

    public static void register() {
        // Initialize the sound entries before attaching the deferred register.
    }
}
