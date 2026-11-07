package mcjty.arienteworld.tutorial;

import com.mojang.blaze3d.platform.NativeImage;
import mcjty.ariente.Ariente;
import mcjty.ariente.blocks.utility.LockTile;
import mcjty.ariente.blocks.utility.autofield.NodeOrientation;
import mcjty.ariente.blocks.utility.door.DoorMarkerTile;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.ariente.entities.levitator.FluxLevitatorEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Screenshots a separate copy of the map; the installable save stays at its welcome point. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class TutorialPreview {

    private static final String[] SCENES = {"Tutorial-city", "Tutorial-plaza", "Tutorial-bend",
            "Tutorial-beams", "Tutorial-elevator", "Tutorial-cockpit", "Tutorial-displays", "Tutorial-automation", "Tutorial-filter", "Tutorial-welcome", "Tutorial-power", "Tutorial-construction", "Tutorial-forcefield", "Tutorial-security",
            "Tutorial-outline-displays", "Tutorial-module-left", "Tutorial-module-right", "Tutorial-alarm",
            "Tutorial-city-circuit", "Tutorial-slope-rider", "Tutorial-slope-cockpit",
            "Tutorial-processing", "Tutorial-cameras", "Tutorial-charger"};
    private static int scene = Integer.getInteger("ariente.tutorial.scene", 0);
    private static int ticks;
    private static boolean started;
    private static volatile boolean ready;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) throws IOException {
        if (!Boolean.getBoolean("ariente.tutorial.preview") || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        if (mc.player == null && mc.screen != null) {
            for (var child : mc.screen.children()) {
                if (child instanceof Button button && (button.getMessage().getString().equals("Proceed")
                        || button.getMessage().getString().equals("Continue"))) {
                    button.onPress();
                    break;
                }
            }
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) {
            return;
        }
        if (!started) {
            started = true;
            mc.options.hideGui = true;
            if (Boolean.getBoolean("ariente.tutorial.fabulous")) {
                mc.options.graphicsMode().set(GraphicsStatus.FABULOUS);
                mc.levelRenderer.allChanged();
            }
            mc.options.renderDistance().set(12);
            mc.options.simulationDistance().set(5);
            prepare(mc);
        }
        if (!ready || ++ticks < (scene == 20 ? 30 : 160)) {
            if (ready && (scene == 19 || scene == 20) && ticks > 20) {
                if (!(mc.player.getVehicle() instanceof FluxLevitatorEntity cart) || mc.player.distanceToSqr(cart) >= 1) {
                    throw new IllegalStateException("Tutorial slope preview rider left the cabin");
                }
                if (scene == 19) {
                    var front = cart.getHoloGuiFront();
                    var back = cart.getHoloGuiBack();
                    var head = mc.player.getEyePosition().subtract(cart.position().add(0, .375, 0));
                    if (front == null || back == null || Math.abs(head.dot(front.getEntity().position()
                            .subtract(back.getEntity().position()).normalize())) >= .95) {
                        throw new IllegalStateException("Tutorial slope preview rider's head reached an end wall");
                    }
                }
            }
            return;
        }
        if (scene == 9 && !mc.player.hasPermissions(2)) {
            throw new IllegalStateException("Tutorial client did not receive gamemode permission");
        }
        Path directory = Path.of("..", "screenshots");
        Files.createDirectories(directory);
        try (NativeImage screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            screenshot.writeToFile(directory.resolve(SCENES[scene] + ".png"));
        }
        if (++scene == SCENES.length) {
            ArienteWorld.setup.getLogger().info("TUTORIAL PREVIEW: screenshot sequence complete");
            mc.stop();
        } else {
            ready = false;
            ticks = 0;
            prepare(mc);
        }
    }

    private static void prepare(Minecraft mc) {
        int current = scene;
        var playerId = mc.player.getUUID();
        mc.options.setCameraType(current == 5 || current == 19 ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            player.stopRiding();
            player.setGameMode((current == 5 || current == 9 || current == 19 || current == 20) ? GameType.CREATIVE : GameType.SPECTATOR);
            switch (current) {
                case 0 -> player.teleportTo(player.serverLevel(), 236, 183, 236, 135, 40);
                case 1 -> player.teleportTo(player.serverLevel(), 178, 116, 185, 139, 42);
                case 2 -> player.teleportTo(player.serverLevel(), 142.5, 68, 144.5, 27, 60);
                case 3 -> player.teleportTo(player.serverLevel(), 134.5, 66, 140.5, -144, 7);
                case 4 -> player.teleportTo(player.serverLevel(), 151.5, 67, 157.5, 180, 10);
                case 5 -> {
                    var ships = player.serverLevel().getEntitiesOfClass(FluxShipEntity.class, new AABB(128, 60, 144, 144, 80, 160));
                    if (ships.isEmpty()) {
                        throw new IllegalStateException("Tutorial has no parked Flux Ship");
                    }
                    FluxShipEntity ship = ships.get(0);
                    player.teleportTo(player.serverLevel(), ship.getX(), ship.getY(), ship.getZ(), 135, 15);
                    player.startRiding(ship, true);
                }
                case 6 -> player.teleportTo(player.serverLevel(), 121, 65.8, 134.5, 180, 7);
                case 7 -> player.teleportTo(player.serverLevel(), 112, 69, 125.5, -135, 28);
                case 8 -> {
                    player.teleportTo(player.serverLevel(), 115.5, 65, 118.5, 180, 0);
                    var panel = Ariente.guiHandler.openHoloGuiEntity(player.serverLevel(), new BlockPos(115, 66, 115), player,
                            NodeOrientation.NORTH_DE.getSlot().name(), 1.0);
                    panel.setPinned(true);
                }
                case 9 -> {
                    if (!player.hasPermissions(2)) {
                        throw new IllegalStateException("Tutorial player cannot change game mode");
                    }
                    var commands = mc.getSingleplayerServer().getCommands();
                    commands.performPrefixedCommand(player.createCommandSourceStack(), "gamemode survival");
                    if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
                        throw new IllegalStateException("Tutorial /gamemode survival failed");
                    }
                    commands.performPrefixedCommand(player.createCommandSourceStack(), "gamemode creative");
                    if (player.gameMode.getGameModeForPlayer() != GameType.CREATIVE) {
                        throw new IllegalStateException("Tutorial /gamemode creative failed");
                    }
                    ArienteWorld.setup.getLogger().info("TUTORIAL PERMISSIONS: player commands switched survival -> creative");
                    player.teleportTo(player.serverLevel(), 136.5, 65, 140.5, 180, 0);
                }
                case 10 -> player.teleportTo(player.serverLevel(), 132, 65.8, 127, 180, -2);
                case 11 -> player.teleportTo(player.serverLevel(), 117.5, 66, 144.5, 180, 12);
                case 12 -> player.teleportTo(player.serverLevel(), 148.5, 65, 129.5, 180, 0);
                case 13 -> {
                    var storage = player.serverLevel().getBlockEntity(new BlockPos(138, 65, 137))
                            .getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
                    ItemStack card = ItemStack.EMPTY;
                    for (int slot = 0; slot < storage.getSlots(); slot++) {
                        if (storage.getStackInSlot(slot).is(Registration.KEY_CARD.get())) {
                            card = storage.getStackInSlot(slot);
                            break;
                        }
                    }
                    LockTile lock = (LockTile) player.serverLevel().getBlockEntity(new BlockPos(147, 66, 140));
                    DoorMarkerTile door = (DoorMarkerTile) player.serverLevel().getBlockEntity(new BlockPos(148, 65, 139));
                    lock.acceptKeyCard(card);
                    if (lock.isLocked() || door.isLocked()) {
                        throw new IllegalStateException("The welcome keycard did not unlock the relocated door");
                    }
                    lock.acceptKeyCard(card);
                    if (!lock.isLocked() || !door.isLocked()) {
                        throw new IllegalStateException("The welcome keycard did not relock the relocated door");
                    }
                    ArienteWorld.setup.getLogger().info("TUTORIAL SECURITY: welcome keycard unlocks/relocks relocated door");
                    player.teleportTo(player.serverLevel(), 147.5, 65, 144.5, 180, 0);
                }
                case 14 -> player.teleportTo(player.serverLevel(), 128.6, 65, 121, 58, 0);
                case 15 -> player.teleportTo(player.serverLevel(), 118.8, 65.6, 130.5, -154, 6);
                case 16 -> player.teleportTo(player.serverLevel(), 124.5, 65.6, 130.5, 142, 6);
                case 17 -> player.teleportTo(player.serverLevel(), 154.5, 65.5, 138.5, 180, 10);
                case 18 -> player.teleportTo(player.serverLevel(), 226, 145, 224, 135, 33);
                case 19, 20 -> {
                    var carts = player.serverLevel().getEntitiesOfClass(FluxLevitatorEntity.class,
                            new AABB(64, 60, 64, 208, 110, 208));
                    if (carts.size() != 1) {
                        throw new IllegalStateException("Tutorial slope preview needs its parked cart");
                    }
                    FluxLevitatorEntity cart = carts.get(0);
                    cart.setPos(160.5, 83.5625, 166.5);
                    cart.setDeltaMovement(0, 0, 0);
                    cart.changeSpeed(current == 20 ? 30 : 0);
                    player.teleportTo(player.serverLevel(), cart.getX(), cart.getY(), cart.getZ(),
                            current == 19 ? 0 : -90, current == 19 ? 10 : -45);
                    player.startRiding(cart, true);
                }
                case 21 -> player.teleportTo(player.serverLevel(), 120, 70, 123, 180, 38);
                case 22 -> player.teleportTo(player.serverLevel(), 141, 66, 130, 180, 0);
                case 23 -> player.teleportTo(player.serverLevel(), 130, 68, 157, -135, 27);
                default -> throw new IllegalStateException("Unknown Tutorial preview scene");
            }
            ready = true;
        });
    }
}
