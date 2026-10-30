package mcjty.arienteworld;

import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.EditMode;
import mcjty.arienteworld.dimension.EditModeClient;
import mcjty.lib.McJtyLib;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientForgeEventHandlers {

    private int ambienceTicks = 3000;

    @SubscribeEvent
    public void onRenderWorldEvent(RenderWorldLastEvent event) {
        if (EditMode.editMode) {
            EditModeClient.renderPart(event.getPartialTicks());
        }
    }

    @SubscribeEvent
    public void onWorldtick(TickEvent.WorldTickEvent event) {
        World world = McJtyLib.proxy.getClientWorld();
        if (world == null) {
            return;
        }

        ambienceTicks--;
        if (ambienceTicks <= 0) {
            ambienceTicks = world.rand.nextInt(12000) + 6000;
            if (world.getDimension().getType() == DimensionRegister.dimensionType) {
                PlayerEntity player = McJtyLib.proxy.getClientPlayer();
                world.playSound(player, player.getPosition(), ModSounds.ambient, SoundCategory.AMBIENT, 0.5f,
                        0.8F + world.rand.nextFloat() * 0.2F);
            }
        }
    }

}
