package mcjty.arienteworld.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mcjty.arienteworld.ArienteStuff;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.dimension.EditMode;
import mcjty.arienteworld.setup.ModSetup;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public class CommandCityCard implements Command<CommandSource> {

    private static final CommandCityCard CMD = new CommandCityCard();

    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("citycard")
                .requires(cs -> cs.hasPermissionLevel(2))
                .executes(CMD);
    }

    @Override
    public int run(CommandContext<CommandSource> context) throws CommandSyntaxException {
        PlayerEntity player = context.getSource().asPlayer();
        City city = EditMode.getCurrentDungeon(player);
        if (city == null) {
            return 0;
        }
        CityAI cityAI = CityAISystem.getCityAISystem(context.getSource().getWorld()).getCityAI(city.getCenter());
        ItemStack stack = new ItemStack(ArienteStuff.keyCardItem);
        ModSetup.arienteSystem.addSecurity(stack, cityAI.getStorageKeyId());
        ModSetup.arienteSystem.setDescription(stack, "City: " + city.getName());
        if (!player.inventory.addItemStackToInventory(stack)) {
            player.entityDropItem(stack, 1.05f);
        }
        return 0;
    }
}
