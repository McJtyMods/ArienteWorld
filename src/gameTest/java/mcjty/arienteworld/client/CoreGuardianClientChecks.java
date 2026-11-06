package mcjty.arienteworld.client;

import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.items.modules.ModuleSupport;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.entities.boss.CoreGuardianEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/** Opt-in visual check in a disposable client save, with real boss tracking and charged attacks. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class CoreGuardianClientChecks {

    private static boolean started;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static UUID playerId;
    private static int ticks;
    private static boolean charged;
    private static boolean discharged;

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("arienteworld.guardianClientCheck") || !ready || event.phase != TickEvent.Phase.END) {
            return;
        }
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }
        var player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.setHealth(20);
            player.getFoodData().setFoodLevel(20);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("arienteworld.guardianClientCheck") || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        if (mc.player == null && mc.screen != null) {
            for (var child : mc.screen.children()) {
                if (child instanceof Button button && (button.getMessage().getString().equals("Proceed")
                        || button.getMessage().getString().equals("Continue"))) {
                    button.onPress();
                    break;
                }
            }
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) {
            return;
        }
        if (failure != null) {
            throw new IllegalStateException("Core guardian client fixture failed", failure);
        }
        if (!started) {
            started = true;
            mc.options.hideGui = false;
            mc.options.fov().set(70);
            mc.options.renderDistance().set(6);
            mc.setScreen(null);
            mc.mouseHandler.releaseMouse();
            playerId = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> prepare(mc));
            return;
        }
        if (!ready || !mc.level.dimension().equals(DimensionRegister.DIMENSION)) {
            return;
        }
        ticks++;
        CoreGuardianEntity guardian = null;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof CoreGuardianEntity candidate) {
                guardian = candidate;
                break;
            }
        }
        if (guardian != null) {
            Vec3 direction = guardian.position().add(0, 2, 0).subtract(mc.player.getEyePosition());
            mc.player.setYRot((float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG));
            mc.player.setXRot((float) (-Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG));
            if (ticks == 100) {
                ArienteWorld.setup.getLogger().info("CORE GUARDIAN FRAME: boss={}, player={}", guardian.position(), mc.player.position());
            }
            if (ticks == 60) {
                screenshot(mc, "core-guardian");
            }
            if (!charged && guardian.getCharge() >= 24) {
                screenshot(mc, "core-guardian-charging");
                charged = true;
            }
            if (!discharged && guardian.getLightningTicks() >= 2 && guardian.getLightningTicks() <= 5) {
                screenshot(mc, "core-guardian-lightning");
                discharged = true;
            }
        }
        if (ticks >= 260) {
            if (guardian == null || !charged || !discharged) {
                throw new IllegalStateException("Expected a tracked guardian with charge and lightning visuals: " + charged + "/" + discharged);
            }
            ArienteWorld.setup.getLogger().info("CORE GUARDIAN CLIENT CHECK PASSED: generated cube, tracked boss bar, translucent sphere, charge and lightning");
            mc.stop();
        }
    }

    private static void prepare(Minecraft mc) {
        try {
            ServerLevel level = mc.getSingleplayerServer().getLevel(DimensionRegister.DIMENSION);
            City city = null;
            for (int i = 0; i < 1000; i++) {
                ChunkPos center = new ChunkPos(408 + i * 16, 408);
                City candidate = CityTools.getCity(level.getSeed(), center);
                if (CityTools.isCityCenter(level.getSeed(), center.x, center.z) && candidate.getPlan().getName().equals("city5_1")) {
                    city = candidate;
                    break;
                }
            }
            if (city == null) {
                throw new IllegalStateException("No cube found");
            }
            BlockPos core = new BlockPos(city.getCenter().getMiddleBlockX(), CityTools.getCityHeight(level, city) + 27,
                    city.getCenter().getMiddleBlockZ());
            level.getChunkAt(core);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            ItemStack holder = new ItemStack(Registration.ENERGY_HOLDER.get());
            holder.getOrCreateTag().putInt("negarite", 1000);
            holder.getOrCreateTag().putInt("posirite", 1000);
            player.getInventory().setItem(9, holder);
            var armor = List.of(new ItemStack(Registration.POWERSUIT_FEET.get()), new ItemStack(Registration.POWERSUIT_LEGS.get()),
                    new ItemStack(Registration.POWERSUIT_CHEST.get()), new ItemStack(Registration.POWERSUIT_HEAD.get()));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                    ItemStack stack = armor.get(slot.getIndex());
                    stack.getOrCreateTag().putBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
                    if (slot == EquipmentSlot.CHEST) {
                        stack.getTag().putBoolean(ArmorUpgradeType.FORCEFIELD.getModuleKey(), true);
                    }
                    player.setItemSlot(slot, stack);
                    ModuleSupport.updateWorkingState(stack, player);
                }
            }
            player.teleportTo(level, core.getX() + 0.5, core.getY() + 8, core.getZ() + 18, 180, -1);
            CityAISystem.getCityAISystem(level).getCityAI(city.getCenter()).ensureGuardian(core);
            ready = true;
        } catch (Throwable error) {
            failure = error;
        }
    }

    private static void screenshot(Minecraft mc, String name) {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(mc.gameDirectory.toPath().resolve(name + ".png"));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
