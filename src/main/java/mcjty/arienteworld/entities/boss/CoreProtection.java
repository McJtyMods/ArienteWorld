package mcjty.arienteworld.entities.boss;

import mcjty.ariente.setup.Registration;
import mcjty.ariente.varia.Messages;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.EditMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class CoreProtection {

    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(DimensionRegister.DIMENSION)
                || !level.getBlockState(pos).is(Registration.AICORE.get())) {
            return false;
        }
        ChunkPos center = CityTools.getNearestDungeonCenter(pos.getX() >> 4, pos.getZ() >> 4);
        CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(center);
        return !EditMode.isEditing(level, center) && ai.isCoreProtected();
    }

    public static boolean allowMining(Player player, Level world, BlockPos pos) {
        if (world instanceof ServerLevel level && isProtected(level, pos)) {
            Messages.player(player, Component.translatable("message.arienteworld.core_guardian_protection"), true);
            return false;
        }
        return true;
    }
}
