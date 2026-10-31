package mcjty.arienteworld;

import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in fixture for a disposable dedicated server, never a normal player world. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID)
public class MultiplayerFixture {

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("ariente.multiplayerFixture") || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.getServer().getPlayerList().op(player.getGameProfile());
        if (!player.getPersistentData().getBoolean("ariente.phase4.initialized")) {
            player.getPersistentData().putBoolean("ariente.phase4.initialized", true);
            player.setGameMode(GameType.SURVIVAL);
            for (BlockPos pos : BlockPos.betweenClosed(-5, 199, -5, 5, 199, 5)) {
                player.serverLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
            ItemStack head = new ItemStack(Registration.POWERSUIT_HEAD.get());
            head.getOrCreateTag().putBoolean(ArmorUpgradeType.NIGHTVISION.getModuleKey(), true);
            head.getTag().putInt(ArmorUpgradeType.NIGHTVISION.getHotkeyKey(), 1);
            head.getTag().putBoolean(ArmorUpgradeType.AUTOFEED.getModuleKey(), true);
            player.setItemSlot(EquipmentSlot.HEAD, head);
            ItemStack holder = new ItemStack(Registration.ENERGY_HOLDER.get());
            holder.getOrCreateTag().putInt("negarite", 3);
            holder.getTag().putInt("posirite", 4);
            player.getInventory().setItem(9, holder);
        }
        player.teleportTo(player.serverLevel(), .5, 200, .5, 0, 0);
        player.setRespawnPosition(player.level().dimension(), new BlockPos(0, 200, 0), 0, true, false);
        ArienteWorld.setup.getLogger().info("MULTIPLAYER FIXTURE LOGIN: {}", player.getGameProfile().getName());
    }
}
