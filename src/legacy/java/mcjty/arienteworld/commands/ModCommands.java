package mcjty.arienteworld.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import mcjty.arienteworld.ArienteWorld;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralCommandNode<CommandSource> commands = dispatcher.register(
                Commands.literal(ArienteWorld.MODID)
                        .then(CommandCityCard.register(dispatcher))
                        .then(CommandEditMode.register(dispatcher))
                        .then(CommandFindCity.register(dispatcher))
                        .then(CommandInfo.register(dispatcher))
                        .then(CommandPacify.register(dispatcher))
                        .then(CommandSaveCity.register(dispatcher))
                        .then(CommandSyncChunk.register(dispatcher))
                        .then(CommandVariantCreate.register(dispatcher))
                        .then(CommandVariantGet.register(dispatcher))
                        .then(CommandVariantList.register(dispatcher))
                        .then(CommandVariantSet.register(dispatcher))
                        .then(CommandVariantSwitch.register(dispatcher))
        );

        dispatcher.register(Commands.literal("arworld").redirect(commands));
    }

}
