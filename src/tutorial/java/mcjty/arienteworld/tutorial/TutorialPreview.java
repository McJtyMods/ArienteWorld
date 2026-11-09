package mcjty.arienteworld.tutorial;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import mcjty.lib.varia.CapabilityTools;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EntitySpawnReason;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import mcjty.ariente.Ariente;
import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.blocks.defense.ForceFieldRenderer;
import mcjty.ariente.blocks.defense.ForceFieldTile;
import mcjty.ariente.blocks.defense.PentakisDodecahedron;
import mcjty.ariente.blocks.utility.LockTile;
import mcjty.ariente.blocks.utility.autofield.NodeOrientation;
import mcjty.ariente.blocks.utility.camera.CameraTile;
import mcjty.ariente.blocks.utility.door.DoorMarkerTile;
import mcjty.ariente.blocks.utility.wireless.WirelessButtonTile;
import mcjty.ariente.client.textures.SurfaceQuad;
import mcjty.ariente.client.textures.SurfaceTextures;
import mcjty.ariente.display.DisplayBaseTile;
import mcjty.ariente.entities.LaserEntity;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.ariente.entities.levitator.FluxLevitatorEntity;
import mcjty.ariente.items.ItemData;
import mcjty.ariente.items.armor.PersonalForcefield;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Screenshots a separate copy of the map; the installable save stays at its welcome point. */
@EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class TutorialPreview {

    private static final String[] SCENES = {"Tutorial-city", "Tutorial-plaza", "Tutorial-bend",
            "Tutorial-beams", "Tutorial-elevator", "Tutorial-cockpit", "Tutorial-displays", "Tutorial-automation", "Tutorial-filter", "Tutorial-welcome", "Tutorial-power", "Tutorial-construction", "Tutorial-forcefield", "Tutorial-security",
            "Tutorial-outline-displays", "Tutorial-module-left", "Tutorial-module-right", "Tutorial-alarm",
            "Tutorial-city-circuit", "Tutorial-slope-rider", "Tutorial-slope-cockpit",
            "Tutorial-processing", "Tutorial-cameras", "Tutorial-charger",
            "Tutorial-southern-booths", "Tutorial-wireless", "Tutorial-guide",
            "Tutorial-forcefield-empty", "Tutorial-forcefield-rebuilt", "Tutorial-personal-first-person",
            "Tutorial-personal-third-person", "Tutorial-camera-perspective", "Tutorial-camera-live",
            "Tutorial-resource-reload", "Tutorial-ariente-sky", "Tutorial-defenders", "Tutorial-ship-flight"};
    private static int scene = Integer.getInteger("ariente.tutorial.scene", 0);
    private static final BlockPos SHIELD = new BlockPos(151, 65, 119);
    private static final BlockPos EMPTY_SHIELD = new BlockPos(151, 65, 125);
    private static final BlockPos SHIELD_BUTTON = new BlockPos(147, 66, 126);
    private static int ticks;
    private static int waitingTicks;
    private static CompletableFuture<Void> reload;
    private static boolean started;
    private static volatile boolean ready;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws IOException {
        if (!Boolean.getBoolean("ariente.tutorial.preview")) {
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
                        || button.getMessage().getString().equals("Continue") || button.getMessage().getString().equals("I know what I'm doing!"))) {
                    button.onPress(new KeyEvent(257, 0, 0));
                    break;
                }
            }
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) {
            if (++waitingTicks > 2400) {
                throw new IllegalStateException("Tutorial preview did not enter the world");
            }
            return;
        }
        if (!started) {
            started = true;
            verifySurfaceUvs(mc);
            mc.options.hideGui = true;
            if (Boolean.getBoolean("ariente.tutorial.fabulous")) {
                mc.options.graphicsPreset().set(GraphicsPreset.FABULOUS);
                mc.levelRenderer.allChanged();
            }
            mc.options.renderDistance().set(12);
            mc.options.simulationDistance().set(5);
            prepare(mc);
        }
        if (scene == 36) {
            GLFW.glfwFocusWindow(mc.getWindow().handle());
            flightInput(true);
        }
        if (reload != null) {
            if (!reload.isDone()) {
                return;
            }
            reload.join();
            reload = null;
            verifySurfaceUvs(mc);
        }
        if (ready && scene == 28) {
            ForceFieldRenderer.damageField(SHIELD, 0, Vec3.atCenterOf(SHIELD)
                    .add(PentakisDodecahedron.getTriangle(0).getMid().scale(3)));
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
        if (scene == 9 && !mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            throw new IllegalStateException("Tutorial client did not receive gamemode permission");
        }
        if (scene == 27) {
            for (BlockPos pos : new BlockPos[]{SHIELD, EMPTY_SHIELD}) {
                if (!(mc.level.getBlockEntity(pos) instanceof ForceFieldTile field)
                        || Arrays.stream(field.getPanelInfo()).anyMatch(Objects::nonNull)) {
                    throw new IllegalStateException("Tutorial empty forcefield regression did not have an empty panel array");
                }
            }
        }
        if (scene == 28 && (!(mc.level.getBlockEntity(SHIELD) instanceof ForceFieldTile field)
                || Arrays.stream(field.getPanelInfo()).noneMatch(Objects::nonNull))) {
            throw new IllegalStateException("Tutorial forcefield did not rebuild");
        }
        if (scene == 36 && (!(mc.player.getVehicle() instanceof FluxShipEntity ship) || ship.getSpeed() < 3
                || ship.getExhaustStrength(1) < .1f)) {
            throw new IllegalStateException("Tutorial flight preview did not produce ship thrust");
        }
        if (scene == 36 && mc.level.getEntitiesOfClass(LaserEntity.class, mc.player.getBoundingBox().inflate(150)).isEmpty()) {
            throw new IllegalStateException("Tutorial flight preview did not fire the cannon");
        }
        if ((scene == 29 || scene == 30) && !PersonalForcefield.isActive(mc.player)) {
            throw new IllegalStateException("Tutorial personal forcefield was not active");
        }
        ArienteWorld.setup.getLogger().info("TUTORIAL PREVIEW: validated {}", SCENES[scene]);
        Path directory = Path.of("..", Boolean.getBoolean("ariente.tutorial.fabulous") ? "screenshots-fabulous" : "screenshots");
        Files.createDirectories(directory);
        Path destination = directory.resolve(SCENES[scene] + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), screenshot -> {
            try (screenshot) {
                screenshot.writeToFile(destination);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
        if (++scene == SCENES.length) {
            ArienteWorld.setup.getLogger().info("TUTORIAL PREVIEW: screenshot sequence complete");
            flightInput(false);
            mc.stop();
        } else {
            ready = false;
            ticks = 0;
            prepare(mc);
        }
    }

    private static void flightInput(boolean pressed) {
        KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_W), pressed);
        KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_SPACE), pressed);
    }

    private static void verifySurfaceUvs(Minecraft mc) {
        Identifier texture = Identifier.fromNamespaceAndPath("ariente", "block/marbletech/marbletech1");
        SurfaceTextures.Rule rule = Objects.requireNonNull(SurfaceTextures.get(texture), "Tutorial connected texture rule missing");
        var model = mc.getModelManager().getBlockStateModelSet().get(Blocks.STONE.defaultBlockState());
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(BlockAndTintGetter.EMPTY, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), RandomSource.create(0), parts);
        int checked = 0;
        for (Direction direction : Direction.values()) {
            for (BakedQuad quad : parts.stream().flatMap(part -> part.getQuads(direction).stream()).toList()) {
                verifyUvBounds(SurfaceQuad.retexture(quad, texture));
                for (int mask = 0; mask < 256; mask++) {
                    for (BakedQuad transformed : SurfaceQuad.transform(quad, rule, mask)) {
                        verifyUvBounds(transformed);
                        checked++;
                    }
                }
            }
        }
        if (checked < 6000) {
            throw new IllegalStateException("Tutorial connected texture check did not cover all faces and neighbor masks");
        }
        ArienteWorld.setup.getLogger().info("TUTORIAL UV CHECK: {} connected quads and six retextured faces stay inside their atlas sprites", checked);
    }

    private static void verifyUvBounds(BakedQuad quad) {
        var sprite = quad.materialInfo().sprite();
        for (int vertex = 0; vertex < 4; vertex++) {
            float u = UVPair.unpackU(quad.packedUV(vertex));
            float v = UVPair.unpackV(quad.packedUV(vertex));
            if (!Float.isFinite(u) || !Float.isFinite(v) || u < sprite.getU0() - .000001f || u > sprite.getU1() + .000001f
                    || v < sprite.getV0() - .000001f || v > sprite.getV1() + .000001f) {
                throw new IllegalStateException("Tutorial texture coordinates escaped " + sprite.contents().name());
            }
        }
    }

    private static void prepare(Minecraft mc) {
        int current = scene;
        var playerId = mc.player.getUUID();
        if (current == 33) {
            reload = mc.reloadResourcePacks();
        }
        mc.options.setCameraType(current == 5 || current == 19 || current == 30 || current == 36 ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            player.stopRiding();
            player.setGameMode((current == 5 || current == 9 || current == 19 || current == 20 || current >= 29 && current <= 32 || current == 36) ? GameType.CREATIVE : GameType.SPECTATOR);
            switch (current) {
                case 0 -> TutorialSupport.teleport(player, player.level(), 236, 183, 236, 135, 40);
                case 1 -> TutorialSupport.teleport(player, player.level(), 178, 116, 185, 139, 42);
                case 2 -> TutorialSupport.teleport(player, player.level(), 142.5, 68, 144.5, 27, 60);
                case 3 -> TutorialSupport.teleport(player, player.level(), 134.5, 66, 140.5, -144, 7);
                case 4 -> TutorialSupport.teleport(player, player.level(), 148.5, 65, 141.5, 0, 0);
                case 5 -> {
                    var ships = player.level().getEntitiesOfClass(FluxShipEntity.class, new AABB(128, 60, 144, 144, 80, 160));
                    if (ships.isEmpty()) {
                        throw new IllegalStateException("Tutorial has no parked Flux Ship");
                    }
                    FluxShipEntity ship = ships.get(0);
                    TutorialSupport.teleport(player, player.level(), ship.getX(), ship.getY(), ship.getZ(), 135, 15);
                    player.startRiding(ship, true, true);
                }
                case 6 -> TutorialSupport.teleport(player, player.level(), 121, 65.8, 134.5, 180, 7);
                case 7 -> TutorialSupport.teleport(player, player.level(), 112, 69, 125.5, -135, 28);
                case 8 -> {
                    TutorialSupport.teleport(player, player.level(), 115.5, 65, 118.5, 180, 0);
                    var panel = Ariente.guiHandler.openHoloGuiEntity(player.level(), new BlockPos(115, 66, 115), player,
                            NodeOrientation.NORTH_DE.getSlot().name(), 1.0);
                    panel.setPinned(true);
                }
                case 9 -> {
                    if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
                    TutorialSupport.teleport(player, player.level(), 136.5, 65, 140.5, 180, 0);
                }
                case 10 -> TutorialSupport.teleport(player, player.level(), 132, 65.8, 127, 180, -2);
                case 11 -> TutorialSupport.teleport(player, player.level(), 117.5, 66, 144.5, 180, 12);
                case 12 -> TutorialSupport.teleport(player, player.level(), 148.5, 65, 129.5, 180, 0);
                case 13 -> {
                    var storage = Objects.requireNonNull(CapabilityTools.itemHandler(player.level().getCapability(Capabilities.Item.BLOCK, new BlockPos(138, 65, 137), null)));
                    ItemStack card = ItemStack.EMPTY;
                    for (int slot = 0; slot < storage.getSlots(); slot++) {
                        if (storage.getStackInSlot(slot).is(Registration.KEY_CARD.get())) {
                            card = storage.getStackInSlot(slot);
                            break;
                        }
                    }
                    LockTile lock = (LockTile) player.level().getBlockEntity(new BlockPos(147, 66, 140));
                    DoorMarkerTile door = (DoorMarkerTile) player.level().getBlockEntity(new BlockPos(148, 65, 139));
                    lock.acceptKeyCard(card);
                    if (lock.isLocked() || door.isLocked()) {
                        throw new IllegalStateException("The welcome keycard did not unlock the relocated door");
                    }
                    lock.acceptKeyCard(card);
                    if (!lock.isLocked() || !door.isLocked()) {
                        throw new IllegalStateException("The welcome keycard did not relock the relocated door");
                    }
                    ArienteWorld.setup.getLogger().info("TUTORIAL SECURITY: welcome keycard unlocks/relocks relocated door");
                    TutorialSupport.teleport(player, player.level(), 147.5, 65, 144.5, 180, 0);
                }
                case 14 -> TutorialSupport.teleport(player, player.level(), 128.6, 65, 121, 58, 0);
                case 15 -> TutorialSupport.teleport(player, player.level(), 118.8, 65.6, 130.5, -154, 6);
                case 16 -> TutorialSupport.teleport(player, player.level(), 124.5, 65.6, 130.5, 142, 6);
                case 17 -> TutorialSupport.teleport(player, player.level(), 154.5, 65.5, 138.5, 180, 10);
                case 18 -> TutorialSupport.teleport(player, player.level(), 226, 145, 224, 135, 33);
                case 19, 20 -> {
                    var carts = player.level().getEntitiesOfClass(FluxLevitatorEntity.class,
                            new AABB(64, 60, 64, 208, 110, 208));
                    if (carts.size() != 1) {
                        throw new IllegalStateException("Tutorial slope preview needs its parked cart");
                    }
                    FluxLevitatorEntity cart = carts.get(0);
                    cart.setPos(160.5, 83.5625, 166.5);
                    cart.setDeltaMovement(0, 0, 0);
                    cart.changeSpeed(current == 20 ? 30 : 0);
                    TutorialSupport.teleport(player, player.level(), cart.getX(), cart.getY(), cart.getZ(),
                            current == 19 ? 0 : -90, current == 19 ? 10 : -45);
                    player.startRiding(cart, true, true);
                }
                case 21 -> TutorialSupport.teleport(player, player.level(), 120, 70, 123, 180, 38);
                case 22 -> TutorialSupport.teleport(player, player.level(), 141, 66, 130, 180, 0);
                case 23 -> TutorialSupport.teleport(player, player.level(), 130, 68, 157, -135, 27);
                case 24 -> TutorialSupport.teleport(player, player.level(), 136, 67, 137, 0, 25);
                case 25 -> TutorialSupport.teleport(player, player.level(), 119, 65, 140, 0, 12);
                case 26 -> TutorialSupport.teleport(player, player.level(), 134, 65, 142, 180, 8);
                case 27 -> {
                    ((WirelessButtonTile) player.level().getBlockEntity(SHIELD_BUTTON)).setLocked(false);
                    player.level().setBlockAndUpdate(EMPTY_SHIELD, Registration.FORCEFIELD.get().defaultBlockState());
                    TutorialSupport.teleport(player, player.level(), 151.5, 65, 130.5, 180, 0);
                }
                case 28 -> {
                    player.level().setBlockAndUpdate(EMPTY_SHIELD, Blocks.AIR.defaultBlockState());
                    ((WirelessButtonTile) player.level().getBlockEntity(SHIELD_BUTTON)).setLocked(true);
                    TutorialSupport.teleport(player, player.level(), 151.5, 65, 126.5, 180, 0);
                }
                case 29, 30 -> {
                    ItemStack chest = new ItemStack(Registration.POWERSUIT_CHEST.get());
                    ItemData.update(chest, data -> data.putBoolean(ArmorUpgradeType.FORCEFIELD.getModuleKey(), true));
                    player.setItemSlot(EquipmentSlot.CHEST, chest);
                    ItemStack holder = new ItemStack(Registration.ENERGY_HOLDER.get());
                    ItemData.update(holder, data -> {
                        data.putInt("negarite", 64);
                        data.putInt("posirite", 64);
                    });
                    player.setItemSlot(EquipmentSlot.MAINHAND, holder);
                    TutorialSupport.teleport(player, player.level(), 136.5, 65, 140.5, 180, 5);
                }
                case 31, 32 -> {
                    player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                    TutorialSupport.teleport(player, player.level(), 141, 66, 130, 180, 0);
                    DisplayBaseTile display = (DisplayBaseTile) player.level().getBlockEntity(new BlockPos(139, 65, 123));
                    var module = display.modules().stream().filter(m -> m.stack().is(Registration.DISPLAY_CAMERA.get())).findFirst().orElseThrow();
                    if (!display.configureModule(player, module.id(), data -> data.putBoolean("Perspective", true))) {
                        throw new IllegalStateException("Tutorial camera module could not be configured");
                    }
                    for (BlockPos pos : new BlockPos[]{new BlockPos(127, 70, 118), new BlockPos(137, 70, 148)}) {
                        ((CameraTile) player.level().getBlockEntity(pos)).setLive(current == 32);
                    }
                }
                case 33 -> TutorialSupport.teleport(player, player.level(), 141, 66, 130, 180, 0);
                case 34 -> TutorialSupport.teleport(player, Objects.requireNonNull(player.level().getServer().getLevel(DimensionRegister.DIMENSION)),
                        1048, 150, 1048, 0, 25);
                case 35 -> {
                    var level = Objects.requireNonNull(player.level().getServer().getLevel(DimensionRegister.DIMENSION));
                    player.level().getServer().setDifficulty(Difficulty.NORMAL, true);
                    EntityType<?>[] types = {
                            mcjty.arienteworld.setup.Registration.ENTITY_DARKBAT.get(),
                            mcjty.arienteworld.setup.Registration.ENTITY_SOLDIER.get(),
                            mcjty.arienteworld.setup.Registration.ENTITY_MASTER_SOLDIER.get(),
                            mcjty.arienteworld.setup.Registration.ENTITY_DRONE.get(),
                            mcjty.arienteworld.setup.Registration.ENTITY_SENTINEL_DRONE.get(),
                            mcjty.arienteworld.setup.Registration.ENTITY_CORE_GUARDIAN.get()};
                    for (int index = 0; index < types.length; index++) {
                        Mob mob = (Mob) Objects.requireNonNull(types[index].create(level, EntitySpawnReason.TRIGGERED));
                        mob.setNoAi(true);
                        mob.setNoGravity(true);
                        mob.setPersistenceRequired();
                        mob.setInvulnerable(true);
                        mob.snapTo(1038 + index * 4, 150, 1048, 0, 0);
                        level.addFreshEntity(mob);
                    }
                    TutorialSupport.teleport(player, level, 1048, 152, 1065, 180, 5);
                }
                case 36 -> {
                    var level = player.level().getServer().overworld();
                    FluxShipEntity ship = Objects.requireNonNull(Registration.ENTITY_FLUX_SHIP.get().create(level, EntitySpawnReason.TRIGGERED));
                    ship.snapTo(136, 110, 140, 180, 0);
                    level.addFreshEntity(ship);
                    TutorialSupport.teleport(player, level, ship.getX(), ship.getY(), ship.getZ(), 180, 0);
                    if (!player.startRiding(ship, true, true)) {
                        throw new IllegalStateException("Tutorial flight preview pilot could not mount");
                    }
                }
                default -> throw new IllegalStateException("Unknown Tutorial preview scene");
            }
            ready = true;
        });
    }
}
