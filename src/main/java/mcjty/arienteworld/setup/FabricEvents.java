package mcjty.arienteworld.setup;

import mcjty.arienteworld.ai.CityAIEvents;
import mcjty.arienteworld.commands.ModCommands;
import mcjty.arienteworld.dimension.EditMode;
import mcjty.arienteworld.dimension.PaneConnections;
import mcjty.arienteworld.entities.boss.CoreProtection;
import mcjty.arienteworld.entities.scout.ScoutPatrols;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;

public final class FabricEvents {

    private FabricEvents() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, context, environment) -> ModCommands.register(dispatcher));
        ServerChunkEvents.CHUNK_LOAD.register(
                (level, chunk) -> {
                    CityAIEvents.chunkLoaded(level, chunk);
                    PaneConnections.chunkLoaded(level, chunk);
                });
        ServerChunkEvents.CHUNK_UNLOAD.register(CityAIEvents::chunkUnloaded);
        ServerTickEvents.END_WORLD_TICK.register(
                level -> {
                    CityAIEvents.levelTick(level);
                    PaneConnections.tick(level);
                    ScoutPatrols.levelTick(level);
                });
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> EditMode.logout(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> EditMode.stopping());
        PlayerBlockBreakEvents.BEFORE.register(
                (level, player, pos, state, tile) ->
                        CoreProtection.allowMining(player, level, pos));
        AttackBlockCallback.EVENT.register(
                (player, level, hand, pos, direction) ->
                        CoreProtection.allowMining(player, level, pos)
                                ? InteractionResult.PASS
                                : InteractionResult.FAIL);
    }
}
