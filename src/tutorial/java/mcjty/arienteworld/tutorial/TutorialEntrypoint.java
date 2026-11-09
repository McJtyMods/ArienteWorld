package mcjty.arienteworld.tutorial;

import java.io.IOException;
import java.io.UncheckedIOException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class TutorialEntrypoint implements ModInitializer {

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try { TutorialWorld.start(server); } catch (IOException e) { throw new UncheckedIOException(e); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { TutorialWorld.tick(server); } catch (IOException e) { throw new UncheckedIOException(e); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            try { TutorialWorld.stopped(server); } catch (IOException e) { throw new UncheckedIOException(e); }
        });
    }
}
