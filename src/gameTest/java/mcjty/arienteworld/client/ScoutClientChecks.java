package mcjty.arienteworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import mcjty.ariente.entities.LaserEntity;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.entities.scout.ScoutPilot;
import mcjty.arienteworld.entities.scout.ScoutRoutes;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import mcjty.arienteworld.entities.soldier.SoldierRender;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;

/** Opt-in integrated flight, pilot rendering and cover check in an isolated development world. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class ScoutClientChecks {

    private static volatile UUID shipId;
    private static volatile UUID pilotId;
    private static volatile Throwable failure;
    private static boolean started;
    private static int ticks;
    private static int pilotRenders;
    private static int serverTicks;
    private static int coveredShots;
    private static Vec3 coveredSighting;
    private static int frames;
    private static int previousMotionTick;
    private static Vec3 previousTickVelocity;
    private static double maximumVelocityChange;
    private static final StringBuilder MOTION = new StringBuilder("nanos,tick,partial,x,y,z,oldx,oldy,oldz,currentx,currenty,currentz,yaw,pitch\n");
    private static final AtomicInteger SHOTS = new AtomicInteger();
    private static final Set<ScoutPilot.Phase> PHASES = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void flightFrame(RenderLevelStageEvent event) {
        if (!Boolean.getBoolean("arienteworld.scoutClientCheck") || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || shipId == null || Minecraft.getInstance().level == null) {
            return;
        }
        for (var entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (entity instanceof FluxShipEntity ship && ship.getUUID().equals(shipId)) {
                Vec3 position = ship.getPosition(event.getPartialTick());
                long now = System.nanoTime();
                MOTION.append(now).append(',').append(ship.tickCount).append(',').append(event.getPartialTick()).append(',')
                        .append(position.x).append(',').append(position.y).append(',').append(position.z).append(',')
                        .append(ship.xo).append(',').append(ship.yo).append(',').append(ship.zo).append(',')
                        .append(ship.getX()).append(',').append(ship.getY()).append(',').append(ship.getZ()).append(',')
                        .append(ship.getFlightYaw(event.getPartialTick())).append(',').append(ship.getFlightPitch(event.getPartialTick())).append('\n');
                if (ship.tickCount != previousMotionTick) {
                    Vec3 velocity = ship.position().subtract(ship.xo, ship.yo, ship.zo);
                    if (previousTickVelocity != null && ship.tickCount == previousMotionTick + 1 && ship.tickCount > 30) {
                        maximumVelocityChange = Math.max(maximumVelocityChange, velocity.distanceTo(previousTickVelocity));
                    }
                    previousTickVelocity = velocity;
                    previousMotionTick = ship.tickCount;
                }
                frames++;
                break;
            }
        }
    }

    @SubscribeEvent
    public static void projectile(EntityJoinLevelEvent event) {
        if (Boolean.getBoolean("arienteworld.scoutClientCheck") && !event.getLevel().isClientSide && !event.loadedFromDisk()
                && pilotId != null && event.getEntity() instanceof LaserEntity laser && laser.isShipShot()
                && laser.getOwner() != null && laser.getOwner().getUUID().equals(pilotId)) {
            SHOTS.incrementAndGet();
        }
    }

    @SubscribeEvent
    public static void server(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("arienteworld.scoutClientCheck") || event.phase != TickEvent.Phase.END || shipId == null) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            ServerLevel level = mc.getSingleplayerServer().overworld();
            if (!(level.getEntity(shipId) instanceof FluxShipEntity ship)
                    || !(ship.getControllingPassenger() instanceof SoldierEntity soldier)) {
                return;
            }
            PHASES.add(soldier.getScoutPilot().getPhase());
            var player = level.players().get(0);
            serverTicks++;
            if (serverTicks == 400) {
                for (BlockPos pos : BlockPos.betweenClosed(-16, 204, -16, 16, 204, 16)) {
                    level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
                }
            } else if (serverTicks == 410) {
                coveredShots = SHOTS.get();
                coveredSighting = soldier.getScoutPilot().getLastSeen();
                player.teleportTo(4.5, player.getY(), .5);
            } else if (serverTicks == 470) {
                require(soldier.getScoutPilot().getPhase() == ScoutPilot.Phase.SEARCH && SHOTS.get() == coveredShots
                                && soldier.getScoutPilot().getLastSeen().equals(coveredSighting),
                        "Real flight must stop firing and search the old sighting while the player moves under a roof");
            } else if (serverTicks == 500) {
                for (BlockPos pos : BlockPos.betweenClosed(-16, 204, -16, 16, 204, 16)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            } else if (serverTicks == 560) {
                player.teleportTo(4.5, 242, .5);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            } else if (serverTicks == 680) {
                require(soldier.getScoutPilot().getLastSeen().y > 240,
                        "A scout must follow renewed sightings when the player takes flight");
            }
        } catch (Throwable error) {
            failure = error;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("arienteworld.scoutClientCheck") || event.phase != TickEvent.Phase.END) {
            return;
        }
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
        require(failure == null, "Scout server fixture failed: " + failure);
        if (!started) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.fov().set(70);
            mc.options.hideGui = true;
            mc.setScreen(null);
            mc.mouseHandler.releaseMouse();
            UUID playerId = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var level = mc.getSingleplayerServer().overworld();
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                    level.setDayTime(6000);
                    level.setWeatherParameters(6000, 0, false, false);
                    for (BlockPos pos : BlockPos.betweenClosed(-24, 199, -24, 24, 199, 24)) {
                        level.setBlock(pos, Blocks.WHITE_CONCRETE.defaultBlockState(), 2);
                    }
                    for (BlockPos pos : BlockPos.betweenClosed(-16, 204, -16, 16, 204, 16)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                    for (var entity : level.getEntitiesOfClass(FluxShipEntity.class, new AABB(-400, 150, -400, 400, 320, 400))) {
                        entity.getPassengers().forEach(passenger -> passenger.discard());
                        entity.discard();
                    }
                    var player = level.getServer().getPlayerList().getPlayer(playerId);
                    player.stopRiding();
                    player.setGameMode(GameType.SURVIVAL);
                    player.getAbilities().invulnerable = true;
                    player.getAbilities().mayfly = true;
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(level, .5, 201, .5, 180, -20);
                    var ship = FluxShipEntity.create(level, .5, 241, -105);
                    var soldier = Registration.ENTITY_SOLDIER.get().create(level);
                    ChunkPos home = new ChunkPos(level.random.nextInt(100000) + 50000, level.random.nextInt(100000) + 50000);
                    soldier.startScout(new ScoutPilot(home,
                            new ScoutRoutes.FlightPlan(home, List.of(new Vec3(.5, 241, 400)))));
                    soldier.setPos(ship.position());
                    shipId = ship.getUUID();
                    pilotId = soldier.getUUID();
                    level.addFreshEntity(ship);
                    level.addFreshEntity(soldier);
                    require(soldier.startRiding(ship, true), "The scout's soldier must mount the existing ship entity");
                    ship.positionRider(soldier);
                } catch (Throwable error) {
                    failure = error;
                }
            });
            return;
        }
        FluxShipEntity ship = null;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof FluxShipEntity candidate && candidate.getUUID().equals(shipId)) {
                ship = candidate;
                break;
            }
        }
        if (ship == null) {
            return;
        }
        ticks++;
        if (ticks >= 300 && ticks <= 340 && ship.getControllingPassenger() instanceof SoldierEntity soldier) {
            mc.setCameraEntity(soldier);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            soldier.setYRot(ship.getYRot() - 55);
            soldier.setXRot(-20);
        } else {
            mc.setCameraEntity(mc.player);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            Vec3 direction = ship.position().subtract(mc.player.getEyePosition());
            float yaw = (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
            float pitch = (float) (-Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);
            mc.player.setYRot(mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()) * .25f);
            mc.player.setXRot(Mth.lerp(.25f, mc.player.getXRot(), pitch));
        }
        if (ticks == 320) {
            screenshot(mc, "scout-pilot-exterior");
        } else if (ticks == 150) {
            screenshot(mc, "scout-attack-run");
        } else if (ticks == 700) {
            require(pilotRenders > 10 && SHOTS.get() >= 4 && PHASES.containsAll(Set.of(ScoutPilot.Phase.SETUP,
                            ScoutPilot.Phase.ATTACK, ScoutPilot.Phase.EGRESS, ScoutPilot.Phase.SEARCH)),
                    "Real scout flight must render a seated soldier, shoot, pull out and search: phases=" + PHASES + ", shots=" + SHOTS);
            ArienteWorld.setup.getLogger().info("SCOUT CLIENT CHECKS PASSED: phases={}, shots={}, pilot renders={}, cover and airborne reacquisition",
                    PHASES, SHOTS, pilotRenders);
            require(maximumVelocityChange < .25, "A flyby must not halt and surge with position packets: maximum tick velocity change=" + maximumVelocityChange);
            ArienteWorld.setup.getLogger().info("SCOUT FRAME MOTION CHECK PASSED: {} frames, maximum tick velocity change={}", frames, maximumVelocityChange);
            try {
                Files.writeString(mc.gameDirectory.toPath().resolve("scout-motion.csv"), MOTION);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
            mc.stop();
        }
    }

    private static void screenshot(Minecraft mc, String name) {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(mc.gameDirectory.toPath().resolve(name + ".png"));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class PilotRendering {

        @SubscribeEvent
        public static void addLayer(EntityRenderersEvent.AddLayers event) {
            if (!Boolean.getBoolean("arienteworld.scoutClientCheck")) {
                return;
            }
            SoldierRender renderer = (SoldierRender) event.getRenderer(Registration.ENTITY_SOLDIER.get());
            renderer.addLayer(new RenderLayer<SoldierEntity, HumanoidModel<SoldierEntity>>(renderer) {

                @Override
                public void render(PoseStack pose, MultiBufferSource buffers, int light, SoldierEntity soldier, float swing,
                                   float amount, float partialTick, float age, float yaw, float pitch) {
                    if (soldier.getUUID().equals(pilotId) && soldier.getVehicle() instanceof FluxShipEntity ship) {
                        // Inspect inside the renderer, after all Pre listeners and before Post restores the pose.
                        require(Math.abs(Mth.wrapDegrees(soldier.yBodyRot - ship.getFlightYaw(partialTick))) < .001
                                        && Math.abs(Mth.wrapDegrees(soldier.yHeadRot - soldier.yBodyRot)) < .001 && soldier.getXRot() == 0,
                                "The soldier model must face the hull while its rendered body banks and pitches");
                        require(getParentModel().riding, "The soldier must use the seated pose during the actual render");
                        pilotRenders++;
                    }
                }
            });
        }
    }
}
