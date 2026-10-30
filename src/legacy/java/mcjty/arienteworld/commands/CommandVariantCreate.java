package mcjty.arienteworld.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mcjty.arienteworld.dimension.EditMode;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.PlayerEntity;

public class CommandVariantCreate implements Command<CommandSource> {

    private static final CommandVariantCreate CMD = new CommandVariantCreate();

    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("variant_create")
                .requires(cs -> cs.hasPermissionLevel(2))
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("max", StringArgumentType.word())
                                .executes(CMD)));
    }

    @Override
    public int run(CommandContext<CommandSource> context) throws CommandSyntaxException {
        PlayerEntity player = context.getSource().asPlayer();
        String name = context.getArgument("name", String.class);
        String max = context.getArgument("max", String.class);
        EditMode.createVariant(player, name, max);
        return 0;
    }
}
