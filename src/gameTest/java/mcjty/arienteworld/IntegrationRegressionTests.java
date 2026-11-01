package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.blocks.generators.NegariteGeneratorTile;
import mcjty.ariente.blocks.generators.PosiriteGeneratorTile;
import mcjty.ariente.cables.CableColor;
import mcjty.ariente.cables.GenericCableBlock;
import mcjty.ariente.config.UtilityConfiguration;
import mcjty.ariente.entities.levitator.FluxLevitatorEntity;
import mcjty.ariente.items.armor.PowerSuitEffects;
import mcjty.ariente.power.IPowerBlob;
import mcjty.ariente.power.PowerReceiverSupport;
import mcjty.ariente.power.PowerSenderSupport;
import mcjty.ariente.power.PowerSystem;
import mcjty.ariente.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class IntegrationRegressionTests {

    @GameTest(template = "empty")
    public static void pairedPowerAcrossSixChunksAndNetworkRepair(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = new BlockPos(90880, 230, 90880);
        BlockPos consumer = start.east(96);
        // This fixture also runs against a previously saved GameTest world.
        for (int x = -1; x <= 96; x++) {
            for (int z : new int[]{-1, 1}) {
                BlockPos pos = start.offset(x, 0, z);
                level.getChunkAt(pos);
                level.removeBlock(pos.above(), false);
                level.removeBlock(pos, false);
            }
        }
        for (int x = 0; x <= 96; x++) {
            for (int z : new int[]{-1, 1}) {
                BlockPos pos = start.offset(x, 0, z);
                level.getChunkAt(pos);
                level.setBlockAndUpdate(pos, Registration.NETCABLE.get().defaultBlockState()
                        .setValue(GenericCableBlock.COLOR, z < 0 ? CableColor.NEGARITE : CableColor.POSIRITE));
            }
        }
        level.setBlockAndUpdate(start.north().west(), Registration.NEGARITE_GENERATOR.get().defaultBlockState());
        level.setBlockAndUpdate(start.south().west(), Registration.POSIRITE_GENERATOR.get().defaultBlockState());
        level.setBlockAndUpdate(start.north().west().above(), Registration.NEGARITE_TANK.get().defaultBlockState());
        level.setBlockAndUpdate(start.south().west().above(), Registration.POSIRITE_TANK.get().defaultBlockState());
        level.setBlockAndUpdate(consumer.north(), Registration.CONNECTOR.get().defaultBlockState()
                .setValue(GenericCableBlock.COLOR, CableColor.NEGARITE));
        level.setBlockAndUpdate(consumer.south(), Registration.CONNECTOR.get().defaultBlockState()
                .setValue(GenericCableBlock.COLOR, CableColor.POSIRITE));
        var negative = (NegariteGeneratorTile) level.getBlockEntity(start.north().west());
        var positive = (PosiriteGeneratorTile) level.getBlockEntity(start.south().west());
        PowerSenderSupport.fixNetworks(level, start);
        helper.assertTrue(negative.getCableId() >= 0
                        && ((IPowerBlob) level.getBlockEntity(consumer.north())).getCableId() == negative.getCableId(),
                "The entire negarite line must share its generator's network");
        negative.feedDust(2);
        positive.feedDust(1);
        PowerSystem power = PowerSystem.getPowerSystem(level);
        for (int tick = 0; tick < 650; tick++) {
            power.tick();
            negative.tickServer();
            positive.tickServer();
            if (tick < 600) {
                helper.assertTrue(PowerReceiverSupport.consumePower(level, consumer, 1000, true),
                        "Both real generators must supply a distant consumer, tick " + tick);
                helper.assertTrue(!PowerReceiverSupport.consumePower(level, consumer, 1, true),
                        "The same supply cannot be spent twice");
            }
        }
        helper.assertTrue(PowerReceiverSupport.getPowerAvailable(level, consumer, true) == 0,
                "Exhausting posirite must stop paired supply despite remaining negarite fuel");
        positive.feedDust(2);
        BlockPos gap = start.east(48).north();
        level.removeBlock(gap, false);
        helper.assertTrue(((IPowerBlob) level.getBlockEntity(consumer.north())).getCableId() != negative.getCableId(),
                "Breaking a cable must split the distant network");
        power.tick();
        negative.tickServer();
        positive.tickServer();
        power.tick();
        negative.tickServer();
        positive.tickServer();
        helper.assertTrue(PowerReceiverSupport.getPowerAvailable(level, consumer, true) == 0,
                "A disconnected consumer must not receive power from the other network half");
        level.setBlockAndUpdate(gap, Registration.NETCABLE.get().defaultBlockState()
                .setValue(GenericCableBlock.COLOR, CableColor.NEGARITE));
        Registration.NETCABLE.get().setPlacedBy(level, gap, level.getBlockState(gap), null,
                new ItemStack(Registration.NETCABLE_NEGARITE.get()));
        power.tick();
        negative.tickServer();
        positive.tickServer();
        helper.assertTrue(PowerReceiverSupport.consumePower(level, consumer, 1000, true),
                "Rejoining a multi-chunk network must restore paired supply");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedHolderSaveAndPlayerLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        FakePlayer wearer = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "gear-wearer"));
        FakePlayer other = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "other-player"));
        ItemStack helmet = new ItemStack(Registration.POWERSUIT_HEAD.get());
        helmet.getOrCreateTag().putBoolean(ArmorUpgradeType.NIGHTVISION.getModuleKey(), true);
        ItemStack holder = new ItemStack(Registration.ENERGY_HOLDER.get());
        holder.getOrCreateTag().putInt("negarite", 3);
        holder.getTag().putInt("posirite", 4);
        wearer.setItemSlot(EquipmentSlot.HEAD, helmet);
        wearer.getInventory().setItem(9, holder);
        int ticksPerPair = Math.max(1, UtilityConfiguration.POWERSUIT_TICKS.get() / ArmorUpgradeType.NIGHTVISION.getPowerUsage());
        try {
            for (int tick = 0; tick < ticksPerPair * 3; tick++) {
                if (tick == ticksPerPair + 13) {
                    helmet = ItemStack.of(helmet.save(new CompoundTag()));
                    holder = ItemStack.of(holder.save(new CompoundTag()));
                    wearer.setItemSlot(EquipmentSlot.HEAD, helmet);
                    wearer.getInventory().setItem(9, holder);
                }
                helmet.getItem().inventoryTick(helmet, level, wearer, EquipmentSlot.HEAD.getIndex(), false);
                PowerSuitEffects.tick(wearer);
                PowerSuitEffects.tick(other);
                helper.assertTrue(wearer.hasEffect(MobEffects.NIGHT_VISION) && !other.hasEffect(MobEffects.NIGHT_VISION),
                        "Long equipment operation and stack reload must preserve player isolation");
            }
            helmet.getItem().inventoryTick(helmet, level, wearer, EquipmentSlot.HEAD.getIndex(), false);
            PowerSuitEffects.tick(wearer);
            helper.assertTrue(!wearer.hasEffect(MobEffects.NIGHT_VISION) && holder.getTag().getInt("posirite") == 1,
                    "Unequal fuel exhaustion must stop effects without wasting the surplus component");
            holder.getTag().putInt("negarite", 1);
            helmet.getItem().inventoryTick(helmet, level, wearer, EquipmentSlot.HEAD.getIndex(), false);
            PowerSuitEffects.tick(wearer);
            helper.assertTrue(wearer.hasEffect(MobEffects.NIGHT_VISION), "Refueling must resume the effect");
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(wearer));
            helper.assertTrue(!wearer.hasEffect(MobEffects.NIGHT_VISION), "The logout event must clear suit-owned effects");
            PowerSuitEffects.tick(wearer);
            helper.assertTrue(wearer.hasEffect(MobEffects.NIGHT_VISION), "Equipment must resume after login ticks");
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(wearer, wearer.damageSources().generic()));
            helper.assertTrue(!wearer.hasEffect(MobEffects.NIGHT_VISION), "The death event must clear suit-owned effects");
        } finally {
            PowerSuitEffects.clear(wearer);
            PowerSuitEffects.clear(other);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void levitatorLongRouteAndEntityReload(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = new BlockPos(91136, 220, 91136);
        for (int x = 0; x <= 192; x++) {
            BlockPos pos = start.east(x);
            level.getChunkAt(pos);
            for (int y = 1; y <= 4; y++) {
                level.setBlock(pos.above(y), Blocks.AIR.defaultBlockState(), 2);
            }
            level.setBlock(pos, Registration.FLUX_BEAM.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 2);
        }
        FluxLevitatorEntity cart = FluxLevitatorEntity.create(level, start.getX() + 2.5, start.getY() + .0625, start.getZ() + .5);
        level.addFreshEntity(cart);
        var rider = helper.makeMockPlayer();
        try {
            cart.tick();
            rider.startRiding(cart);
            cart.changeSpeed(10);
            for (int tick = 0; tick < 560; tick++) {
                if (tick == 200) {
                    CompoundTag saved = cart.saveWithoutId(new CompoundTag());
                    rider.stopRiding();
                    cart.discard();
                    cart = FluxLevitatorEntity.create(level, 0, 0, 0);
                    cart.load(saved);
                    level.addFreshEntity(cart);
                    cart.tick();
                    rider.startRiding(cart);
                }
                cart.tick();
                cart.positionRider(rider);
                helper.assertTrue(rider.distanceToSqr(cart) < 4, "The passenger must remain aboard across chunks and entity reload");
            }
            helper.assertTrue(cart.getX() > start.getX() + 160 && Math.abs(cart.getZ() - start.getZ() - .5) < .1,
                    "A reloaded cabin must follow more than ten chunk boundaries: " + cart.position());
        } finally {
            rider.stopRiding();
            cart.discard();
        }
        helper.succeed();
    }
}
