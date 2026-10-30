package mcjty.arienteworld.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mcjty.arienteworld.dimension.EditMode;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.text.StringTextComponent;

public class CommandSyncChunk implements Command<CommandSource> {

    private static final CommandSyncChunk CMD = new CommandSyncChunk();

    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("sync")
                .requires(cs -> cs.hasPermissionLevel(2))
                .executes(CMD);
    }

    @Override
    public int run(CommandContext<CommandSource> context) throws CommandSyntaxException {
        PlayerEntity player = context.getSource().asPlayer();
        EditMode.syncChunk(player);
        player.sendMessage(new StringTextComponent("Chunk synced to other parts!"));
        return 0;
    }
}
