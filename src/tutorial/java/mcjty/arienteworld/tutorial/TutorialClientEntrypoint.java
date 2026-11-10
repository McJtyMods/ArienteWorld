package mcjty.arienteworld.tutorial;

import java.io.IOException;
import java.io.UncheckedIOException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class TutorialClientEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TutorialDisplayPositionCheck.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                TutorialPreview.tick();
                TutorialDisplayPositionCheck.tick();
                TutorialForcefieldSoundCheck.tick();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
