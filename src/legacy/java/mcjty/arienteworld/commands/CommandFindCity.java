package mcjty.arienteworld.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;

import javax.annotation.Nonnull;
import java.util.Optional;

public class CommandFindCity implements Command<CommandSource> {

    private static final CommandFindCity CMD = new CommandFindCity();

    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("findcity")
                .requires(cs -> cs.hasPermissionLevel(2))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(CMD));
    }

    @Override
    public int run(CommandContext<CommandSource> context) throws CommandSyntaxException {
        PlayerEntity player = context.getSource().asPlayer();
        BlockPos start = player.getPosition();
        int cx = (start.getX() >> 4);
        int cz = (start.getZ() >> 4);
        Optional<ChunkPos> cityCenter;

        String name = context.getArgument("name", String.class);

        if (!name.isEmpty()) {
            cityCenter = findNearbyCityCenter(cx, cz, name);
        } else {
            cityCenter = findNearbyCityCenter(cx, cz);
        }

        if (!cityCenter.isPresent()) {
            player.sendMessage(new StringTextComponent("No nearby city!"));
        } else {
            player.sendMessage(new StringTextComponent("Nearest city at: " + cityCenter.get().x * 16 + "," + cityCenter.get().z * 16));
        }
        return 0;
    }


    @Nonnull
    private Optional<ChunkPos> findNearbyCityCenter(int cx, int cy, String cityType) {
        Optional<ChunkPos> center = findNearbyCityCenter(cx, cy);
        if (center.isPresent() && isCityOfType(center.get(), cityType)) {
            return center;
        }
        for (int d = 1 ; d < 5 ; d++) {
            for (int i = 1 ; i <= d*2 ; i++) {
                center = findNearbyCityCenter((cx-d+i)*8, (cy-d)*8);
                if (center.isPresent() && isCityOfType(center.get(), cityType)) {
                    return center;
                }
                center = findNearbyCityCenter((cx+d)*8, (cy-d+i)*8);
                if (center.isPresent() && isCityOfType(center.get(), cityType)) {
                    return center;
                }
                center = findNearbyCityCenter((cx+d-i)*8, (cy+d)*8);
                if (center.isPresent() && isCityOfType(center.get(), cityType)) {
                    return center;
                }
                center = findNearbyCityCenter((cx-d)*8, (cy+d-i)*8);
                if (center.isPresent() && isCityOfType(center.get(), cityType)) {
                    return center;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isCityOfType(ChunkPos coord, String cityType) {
        City city = CityTools.getCity(coord);
        return cityType.equals(city.getPlan().getName());
    }

    @Nonnull
    private Optional<ChunkPos> findNearbyCityCenter(int cx, int cz) {
        Optional<ChunkPos> cityCenter = CityTools.getNearestCityCenterO(cx, cz);
        if (cityCenter.isPresent()) {
            return cityCenter;
        }
        cityCenter = CityTools.getNearestCityCenterO(cx - 10, cz);
        if (cityCenter.isPresent()) {
            return cityCenter;
        }
        cityCenter = CityTools.getNearestCityCenterO(cx + 10, cz);
        if (cityCenter.isPresent()) {
            return cityCenter;
        }
        cityCenter = CityTools.getNearestCityCenterO(cx, cz - 10);
        if (cityCenter.isPresent()) {
            return cityCenter;
        }
        cityCenter = CityTools.getNearestCityCenterO(cx, cz + 10);
        return cityCenter;

    }
}
