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

public class CommandVariantSwitch implements Command<CommandSource> {

    private static final CommandVariantSwitch CMD = new CommandVariantSwitch();

    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("variant_switch")
                .requires(cs -> cs.hasPermissionLevel(2))
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("index", StringArgumentType.word())
                                .executes(CMD)));
    }

    @Override
    public int run(CommandContext<CommandSource> context) throws CommandSyntaxException {
        PlayerEntity player = context.getSource().asPlayer();
        String name = context.getArgument("name", String.class);
        String index = context.getArgument("index", String.class);
        EditMode.switchVariant(player, name, index);
//                EditMode.createVariant(player, arg1, arg2);
        return 0;
    }
}
