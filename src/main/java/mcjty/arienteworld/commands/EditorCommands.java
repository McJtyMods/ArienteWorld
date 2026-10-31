package mcjty.arienteworld.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import mcjty.ariente.Ariente;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.EditMode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class EditorCommands {

    @FunctionalInterface
    private interface Action {

        String run(ServerPlayer player) throws Exception;
    }

    public static void register(LiteralCommandNode<CommandSourceStack> root) {
        root.addChild(Commands.literal("editmode").executes(ctx -> run(ctx.getSource(), player -> "Editing " + EditMode.begin(player).getName()
                        + "; template blocks restored. Use syncchunk, savecity, editmode reload/off."))
                .then(Commands.literal("off").executes(ctx -> run(ctx.getSource(), player -> {
                    EditMode.end(player);
                    return "Edit session ended; savecity exports must be installed separately to change generation";
                })))
                .then(Commands.literal("reload").executes(ctx -> run(ctx.getSource(), player -> {
                    EditMode.session(player).reload();
                    return "Reloaded exported template into the edit area";
                }))).build());
        root.addChild(Commands.literal("savecity").executes(ctx -> run(ctx.getSource(), player -> {
            var session = EditMode.session(player);
            session.save();
            return "Exported " + session.exportPath();
        })).build());
        root.addChild(Commands.literal("syncchunk").executes(ctx -> run(ctx.getSource(), player -> {
            EditMode.session(player).sync(player.blockPosition());
            return "Copied this part's blocks and equipment settings to its other occurrences";
        })).build());
        root.addChild(Commands.literal("variantcreate").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 16)).executes(ctx -> run(ctx.getSource(), player -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    if (!name.matches("[a-z0-9_]+")) {
                        throw new IllegalArgumentException("Use lowercase letters, digits and underscores in variant names");
                    }
                    EditMode.session(player).createVariant(name, IntegerArgumentType.getInteger(ctx, "count"));
                    return "Created variant " + name;
                })))).build());
        root.addChild(Commands.literal("variantset").then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> run(ctx.getSource(), player -> {
                    var session = EditMode.session(player);
                    session.sync(player.blockPosition());
                    session.setVariant(player.blockPosition(), StringArgumentType.getString(ctx, "name"));
                    return "Assigned variant to this part palette";
                }))).build());
        root.addChild(Commands.literal("variantget").executes(ctx -> run(ctx.getSource(), player ->
                "Variant: " + EditMode.session(player).variant(player.blockPosition()))).build());
        root.addChild(Commands.literal("variantlist").executes(ctx -> run(ctx.getSource(), player -> EditMode.session(player).variants())).build());
        root.addChild(Commands.literal("variantswitch").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.argument("index", IntegerArgumentType.integer(0, 15)).executes(ctx -> run(ctx.getSource(), player -> {
                    var session = EditMode.session(player);
                    session.sync(player.blockPosition());
                    session.switchVariant(StringArgumentType.getString(ctx, "name"), IntegerArgumentType.getInteger(ctx, "index"));
                    return "Selected variant index (zero based)";
                })))).build());
        root.addChild(Commands.literal("info").executes(ctx -> run(ctx.getSource(), player -> {
            City city = currentCity(player);
            return "City: " + city.getName() + "; plan: " + city.getPlan().getName() + "; center chunk: " + city.getCenter()
                    + "; local chunk: " + player.chunkPosition();
        })).build());
        root.addChild(Commands.literal("pacify").executes(ctx -> run(ctx.getSource(), player -> {
            City city = currentCity(player);
            var system = CityAISystem.getCityAISystem(player.serverLevel());
            system.getCityAI(city.getCenter()).pacify(player.level());
            system.saveSystem();
            return "Pacified " + city.getName();
        })).build());
        root.addChild(Commands.literal("citycard").executes(ctx -> run(ctx.getSource(), player -> {
            City city = currentCity(player);
            String key = CityAISystem.getCityAISystem(player.serverLevel()).getCityAI(city.getCenter()).getStorageKeyId();
            if (key == null || key.isEmpty()) {
                throw new IllegalStateException("Load the complete city and let its AI initialize first");
            }
            ItemStack card = new ItemStack(Registration.KEY_CARD.get());
            Ariente.arienteSystem.addSecurity(card, key);
            Ariente.arienteSystem.setDescription(card, "City: " + city.getName());
            if (!player.getInventory().add(card)) {
                player.drop(card, false);
            }
            return "Created storage access card for " + city.getName();
        })).build());
    }

    private static City currentCity(ServerPlayer player) {
        if (!player.level().dimension().equals(DimensionRegister.DIMENSION)) {
            throw new IllegalStateException("This command requires the Ariente dimension");
        }
        var chunk = player.chunkPosition();
        if (!CityTools.isDungeonChunk(player.serverLevel().getSeed(), chunk.x, chunk.z)) {
            throw new IllegalStateException("Stand inside a city complex");
        }
        return CityTools.getCity(player.serverLevel().getSeed(), CityTools.getNearestDungeonCenter(chunk.x, chunk.z));
    }

    private static int run(CommandSourceStack source, Action action) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            String result = action.run(player);
            source.sendSuccess(() -> Component.literal(result), false);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
            return 0;
        }
    }
}
