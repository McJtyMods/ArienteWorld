package mcjty.arienteworld.client;

import mcjty.ariente.Ariente;
import mcjty.ariente.blocks.utility.ElevatorTile;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.hologui.api.CloseStrategy;
import mcjty.hologui.gui.HoloGuiEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Runs only in an explicitly enabled integrated development client. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class MachineGuiClientChecks {

    private static final BlockPos ELEVATOR = new BlockPos(90020, 241, 90023);
    private static final BlockPos WARPER = ELEVATOR.east(2);
    private static volatile int guiId = -1;
    private static int phase;
    private static int ticks;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("ariente.machineGuiCheck") || phase == 5 || event.phase != TickEvent.Phase.END
                || mc.level == null || mc.player == null || mc.getSingleplayerServer() == null
                || mc.isPaused() || mc.getOverlay() != null) {
            return;
        }
        var server = mc.getSingleplayerServer();
        var playerId = mc.player.getUUID();
        if (phase == 0) {
            if (!mc.level.dimension().equals(DimensionRegister.DIMENSION) || !mc.level.hasChunkAt(ELEVATOR)) {
                return;
            }
            phase = 1;
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var level = player.serverLevel();
                level.setBlock(ELEVATOR, Registration.ELEVATOR.get().defaultBlockState(), 3);
                level.setBlock(WARPER, Registration.WARPER.get().defaultBlockState(), 3);
                ((ElevatorTile) level.getBlockEntity(ELEVATOR)).setHeight(9);
                level.getEntitiesOfClass(HoloGuiEntity.class, new AABB(ELEVATOR).inflate(10)).forEach(HoloGuiEntity::discard);
                player.teleportTo(level, 90018.5, 241, 90020.5, -45, 0);
            });
            return;
        }
        if (++ticks > 200) {
            throw new IllegalStateException("Timed out waiting for machine GUI client check phase " + phase);
        }
        if (ticks < 20 || (phase > 1 && !(mc.level.getEntity(guiId) instanceof HoloGuiEntity))) {
            return;
        }
        if (mc.player.getPose() != Pose.STANDING || mc.player.getEyeHeight() < 1.6f) {
            var body = mc.player.getDimensions(Pose.STANDING).makeBoundingBox(mc.player.position()).deflate(1.0E-7);
            throw new IllegalStateException("Machine GUI camera check phase " + phase + " must preserve standing camera height: " + mc.player.getPose()
                    + "; position=" + mc.player.position() + "; swimming=" + mc.player.isSwimming()
                    + "; forced=" + mc.player.getForcedPose() + "; noCollision=" + mc.level.noCollision(mc.player, body)
                    + "; entities=" + mc.level.getEntities(mc.player, body).stream()
                    .map(entity -> entity.getType() + "@" + entity.position() + ":collidable=" + entity.canBeCollidedWith()).toList()
                    + "; blocks=" + BlockPos.betweenClosedStream(BlockPos.containing(body.minX, body.minY, body.minZ),
                    BlockPos.containing(body.maxX, body.maxY, body.maxZ)).map(pos -> pos + "=" + mc.level.getBlockState(pos)).toList());
        }
        if (phase == 1) {
            phase = 2;
            ticks = 0;
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var holo = Ariente.guiHandler.openHoloGuiEntity(player.serverLevel(), ELEVATOR, player, "default", 1);
                holo.setCloseStrategy(CloseStrategy.NEVER);
                guiId = holo.getEntity().getId();
            });
        } else if (phase == 2) {
            phase = 3;
            ticks = 0;
            server.execute(() -> {
                var level = server.getPlayerList().getPlayer(playerId).serverLevel();
                ((ElevatorTile) level.getBlockEntity(ELEVATOR)).setHeight(17);
            });
        } else if (phase == 3 && mc.level.getBlockEntity(ELEVATOR) instanceof ElevatorTile elevator && elevator.getHeight() == 17) {
            phase = 4;
            ticks = 0;
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var level = player.serverLevel();
                level.getEntity(guiId).discard();
                var holo = Ariente.guiHandler.openHoloGuiEntity(level, WARPER, player, "default", 1);
                holo.setCloseStrategy(CloseStrategy.NEVER);
                guiId = holo.getEntity().getId();
            });
        } else if (phase == 4) {
            phase = 5;
            ArienteWorld.setup.getLogger().info("MACHINE GUI CLIENT CHECK PASSED: fresh Elevator/Warper GUIs preserve standing camera height; elevator update reaches client");
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                player.serverLevel().getEntity(guiId).discard();
                player.teleportTo(player.serverLevel(), 90026.5, 241.2, 90017.5, 40, 17);
            });
        }
    }
}
