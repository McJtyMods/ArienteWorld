package mcjty.arienteworld.entities.boss;

import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.EditMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArienteWorld.MODID)
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

    @SubscribeEvent
    public static void startMiningCore(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level && isProtected(level, event.getPos())) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(Component.translatable("message.arienteworld.core_guardian_protection"), true);
        }
    }

    @SubscribeEvent
    public static void breakCore(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && isProtected(level, event.getPos())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("message.arienteworld.core_guardian_protection"), true);
        }
    }

    @SubscribeEvent
    public static void destroyCore(LivingDestroyBlockEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level && isProtected(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void explodeCore(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> isProtected(level, pos));
        }
    }
}
