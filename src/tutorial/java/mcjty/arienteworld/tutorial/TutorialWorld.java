package mcjty.arienteworld.tutorial;

import mcjty.ariente.api.AlarmType;
import mcjty.ariente.api.MarbleColor;
import mcjty.ariente.blocks.aicore.AICoreTile;
import mcjty.ariente.blocks.decorative.DecorativeBlockModule;
import mcjty.ariente.blocks.defense.ForceFieldTile;
import mcjty.ariente.blocks.generators.PowerCombinerTile;
import mcjty.ariente.blocks.utility.AutoConstructorTile;
import mcjty.ariente.blocks.utility.ElevatorTile;
import mcjty.ariente.blocks.utility.LockTile;
import mcjty.ariente.blocks.utility.WarperTile;
import mcjty.ariente.blocks.utility.AlarmTile;
import mcjty.ariente.blocks.utility.autofield.AbstractNodeTile;
import mcjty.ariente.blocks.utility.autofield.AutoFieldTile;
import mcjty.ariente.blocks.utility.autofield.InputItemNodeTile;
import mcjty.ariente.blocks.utility.autofield.NodeOrientation;
import mcjty.ariente.blocks.utility.autofield.OutputItemNodeTile;
import mcjty.ariente.blocks.utility.autofield.SensorItemNodeTile;
import mcjty.ariente.blocks.utility.wireless.SignalReceiverTile;
import mcjty.ariente.blocks.utility.wireless.SignalTransmitterTile;
import mcjty.ariente.blocks.utility.wireless.WirelessButtonTile;
import mcjty.ariente.blocks.utility.wireless.WirelessLockTile;
import mcjty.ariente.cables.CableColor;
import mcjty.ariente.cables.GenericCableBlock;
import mcjty.ariente.config.UtilityConfiguration;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.ariente.entities.levitator.FluxLevitatorEntity;
import mcjty.ariente.items.BlueprintItem;
import mcjty.ariente.items.KeyCardItem;
import mcjty.ariente.multipart.MultipartTE;
import mcjty.ariente.power.PowerReceiverSupport;
import mcjty.ariente.power.PowerSenderSupport;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.lib.multipart.PartSlot;
import mcjty.lib.tileentity.GenericTileEntity;
import mcjty.lib.varia.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** An explicitly enabled authoring tool, never loaded by an ordinary game. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID)
public class TutorialWorld {

    private static final int FLOOR = 64;
    private static final BlockPos SPAWN = new BlockPos(136, 65, 140);
    private static final BlockPos FIELD = new BlockPos(113, 65, 117);
    private static final BlockPos SHIELD = new BlockPos(151, 65, 119);
    private static final BlockPos CRAFTER = new BlockPos(120, 65, 134);
    private static final BlockPos COMBINER = new BlockPos(132, 65, 114);
    private static final Set<BlockPos> CABLES = new HashSet<>();
    private static int ticks;
    private static boolean running;
    private static int[] previousItems;
    private static int transfers;

    @SubscribeEvent
    public static void start(ServerStartedEvent event) throws IOException {
        if (!Boolean.getBoolean("ariente.tutorial")) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        var marker = server.getWorldPath(LevelResource.ROOT).resolve("Tutorial.complete");
        if (Boolean.getBoolean("ariente.tutorial.verify")) {
            require(Files.exists(marker), "Verification requires a completed Tutorial map");
        } else {
            require(!Files.exists(marker), "Refusing to overwrite a completed Tutorial map");
            require(level.getBlockState(SPAWN.below()).isAir(), "Refusing to overwrite an existing platform");
            build(level);
        }
        for (int x = 7; x <= 9; x++) {
            for (int z = 7; z <= 9; z++) {
                level.setChunkForced(x, z, true);
            }
        }
        running = true;
        ArienteWorld.setup.getLogger().info("TUTORIAL: platform ready; observing real machine ticks");
    }

    private static void build(ServerLevel level) {
        level.setDefaultSpawnPos(SPAWN, 180);
        level.setDayTime(6000);
        level.setWeatherParameters(0, 0, false, false);
        GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, level.getServer());
        rules.getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
        rules.getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());
        platform(level);
        power(level);
        automation(level);
        crafting(level);
        shield(level);
        doors(level);
        wireless(level);
        elevator(level);
        transport(level);
        welcome(level);
        for (BlockPos pos : CABLES) {
            PowerSenderSupport.fixNetworks(level, pos);
        }
    }

    private static BlockState marble(MarbleColor color) {
        return DecorativeBlockModule.MARBLE_SMOOTH.get(color).get().defaultBlockState();
    }

    private static void platform(ServerLevel level) {
        for (int x = 64; x < 208; x++) {
            for (int z = 64; z < 208; z++) {
                int distance = Math.max(Math.abs(x - 135), Math.abs(z - 135));
                int top = distance <= 24 ? FLOOR : FLOOR - Math.min(3, (distance - 9) / 16);
                for (int y = top - 3; y <= top; y++) {
                    BlockState state = marble(MarbleColor.BLACK);
                    if (y == top) {
                        state = marble(MarbleColor.GRAY);
                        if (Math.floorMod(x, 16) <= 1 || Math.floorMod(z, 16) <= 1) {
                            state = marble(MarbleColor.DARKBLUE);
                        }
                        if ((x >= 134 && x <= 137) || (z >= 134 && z <= 137)) {
                            state = marble(MarbleColor.WHITE);
                        }
                        if ((x % 16 == 8 && z % 16 == 8) || (x % 16 == 1 && z % 16 == 1)) {
                            state = Registration.FLUX_GLOW.get().defaultBlockState();
                        }
                    }
                    level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
                }
                if (x == 64 || x == 207 || z == 64 || z == 207) {
                    put(level, new BlockPos(x, top + 1, z), Registration.TECH_FENCE.get());
                }
            }
        }
        // Shallow staircases connect all four city terraces without flight.
        for (int d = 25; d <= 71; d++) {
            int height = FLOOR - Math.min(3, (d - 9) / 16);
            for (int w = -1; w <= 2; w++) {
                for (BlockPos pos : new BlockPos[]{new BlockPos(135 + d, height, 135 + w),
                        new BlockPos(135 - d, height, 135 + w), new BlockPos(135 + w, height, 135 + d),
                        new BlockPos(135 + w, height, 135 - d)}) {
                    level.setBlock(pos, marble(MarbleColor.WHITE), Block.UPDATE_CLIENTS);
                }
            }
        }
        // A quiet spare building area on each outer ring; exhibits stay in the central nine chunks.
        label(level, 136, 65, 129, "TUTORIAL", "Ariente city lab", "9 x 9 chunks", "Start at the book");
    }

    private static void power(ServerLevel level) {
        put(level, COMBINER, Registration.POWER_COMBINER.get());
        ((PowerCombinerTile) level.getBlockEntity(COMBINER)).setPowerTransfer(6000);
        for (boolean negative : new boolean[]{true, false}) {
            int x = negative ? 130 : 134;
            BlockPos generator = new BlockPos(x, 65, 114);
            put(level, generator, (negative ? Registration.NEGARITE_GENERATOR : Registration.POSIRITE_GENERATOR).get());
            GenericTileEntity tile = (GenericTileEntity) level.getBlockEntity(generator);
            tile.setRSMode(RedstoneMode.REDSTONE_IGNORED);
            Item dust = (negative ? Registration.DUST_NEGARITE : Registration.DUST_POSIRITE).get();
            stock(level, generator, new ItemStack(dust, 64));
            for (int y = 1; y <= 8; y++) {
                put(level, generator.above(y), (negative ? Registration.NEGARITE_TANK : Registration.POSIRITE_TANK).get());
            }
            cable(level, generator.offset(negative ? 1 : -1, 0, 0), negative ? CableColor.NEGARITE : CableColor.POSIRITE, true);
            put(level, generator.north(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));
            put(level, generator.north().above(), Blocks.BARREL);
            Container fuel = (Container) level.getBlockEntity(generator.north().above());
            for (int slot = 0; slot < fuel.getContainerSize(); slot++) {
                fuel.setItem(slot, new ItemStack(dust, 64));
            }
        }
        for (int y = 62; y <= 64; y++) {
            cable(level, new BlockPos(132, y, 114), CableColor.COMBINED, y == 64);
        }
        label(level, 130, 65, 120, "01 / POWER", "Blue + red flux", "8 tanks per tower", "Fuel barrels north");
        label(level, 134, 65, 120, "POWER COMBINER", "6,000 in each", "5,880 out each", "2% combining loss");
        // The inspection hatch exposes the actual supply rather than decorative cabling.
        for (int z = 115; z <= 123; z++) {
            cable(level, new BlockPos(132, 62, z), CableColor.COMBINED, false);
            put(level, new BlockPos(132, 64, z), Blocks.GLASS);
        }
    }

    private static void supply(ServerLevel level, BlockPos machine) {
        // Shared bus, protected below the flat plaza, with a connector under each consumer.
        int x = 132;
        int z = 114;
        while (x != machine.getX()) {
            cable(level, new BlockPos(x, 62, z), CableColor.COMBINED, false);
            x += Integer.signum(machine.getX() - x);
        }
        while (z != machine.getZ()) {
            cable(level, new BlockPos(x, 62, z), CableColor.COMBINED, false);
            z += Integer.signum(machine.getZ() - z);
        }
        for (int y = 62; y < machine.getY(); y++) {
            cable(level, new BlockPos(x, y, z), CableColor.COMBINED, y == machine.getY() - 1);
        }
    }

    private static void automation(ServerLevel level) {
        put(level, FIELD, Registration.AUTOMATION_FIELD.get());
        AutoFieldTile field = (AutoFieldTile) level.getBlockEntity(FIELD);
        field.setHeight(5);
        for (int x = 114; x <= 124; x++) {
            for (int z = 114; z <= 120; z++) {
                part(level, new BlockPos(x, 65, z), Registration.FIELD_MARKER.get().defaultBlockState(), PartSlot.DOWN);
            }
        }
        supply(level, FIELD);
        // Source at y=66; destination hoppers at y=66 return items to it by gravity.
        BlockPos source = new BlockPos(115, 66, 117);
        put(level, source, Blocks.BARREL);
        ((Container) level.getBlockEntity(source)).setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        ((Container) level.getBlockEntity(source)).setItem(1, new ItemStack(Items.IRON_INGOT, 64));
        BlockPos outputPos = source.north();
        OutputItemNodeTile output = (OutputItemNodeTile) node(level, outputPos, Registration.OUTPUT_ITEM_NODE.get(), NodeOrientation.SOUTH_DE);
        output.getOutputFilter().set(0, new ItemStack(Items.IRON_INGOT));
        part(level, outputPos, Registration.ROUND_ROBIN_NODE.get().defaultBlockState()
                .setValue(AbstractNodeTile.ORIENTATION, NodeOrientation.SOUTH_DE), PartSlot.SOUTH_DE_BACK);
        for (int x = 116; x <= 123; x++) {
            put(level, new BlockPos(x, 66, 117), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
        }
        for (int x : new int[]{120, 123}) {
            BlockPos barrel = new BlockPos(x, 67, 117);
            put(level, barrel, Blocks.BARREL);
            InputItemNodeTile input = (InputItemNodeTile) node(level, barrel.north(), Registration.INPUT_ITEM_NODE.get(), NodeOrientation.SOUTH_DE);
            input.getInputFilter().set(0, new ItemStack(Items.IRON_INGOT));
        }
        SensorItemNodeTile sensor = (SensorItemNodeTile) node(level, source.south(), Registration.SENSOR_ITEM_NODE.get(), NodeOrientation.NORTH_DE);
        CompoundTag tag = sensor.saveWithoutMetadata();
        tag.getCompound("Info").putInt("op", 3);
        tag.getCompound("Info").putInt("amount", 1);
        sensor.load(tag);
        // The white sensor signal gates the output: items move only while source >= 1.
        tag = output.saveWithoutMetadata();
        tag.getCompound("Info").putInt("f0", 0);
        output.load(tag);
        label(level, 115, 65, 123, "02 / AUTOMATION", "Output = extract", "Input = insert", "Iron loops forever");
        label(level, 120, 65, 123, "MARKED COLUMNS", "Round robin: A/B", "Sensor: source >=1", "Hoppers return iron");
    }

    private static void crafting(ServerLevel level) {
        BlockPos manual = new BlockPos(115, 65, 134);
        put(level, manual, Registration.CONSTRUCTOR.get());
        put(level, manual.east(), Registration.BLUEPRINT_STORAGE.get());
        put(level, CRAFTER, Registration.AUTO_CONSTRUCTOR.get());
        ((GenericTileEntity) level.getBlockEntity(CRAFTER)).setRSMode(RedstoneMode.REDSTONE_IGNORED);
        put(level, CRAFTER.east(), Registration.BLUEPRINT_STORAGE.get());
        for (BlockPos blueprint : new BlockPos[]{manual.east(), CRAFTER.east()}) {
            stock(level, blueprint, BlueprintItem.makeBluePrint(new ItemStack(Registration.CIRCUIT.get())));
        }
        supply(level, CRAFTER);
        stock(level, CRAFTER, new ItemStack(Registration.DUST_SILICON.get(), 64), new ItemStack(Registration.INGOT_MANGANESE.get(), 64),
                new ItemStack(Registration.DUST_NEGARITE.get(), 64), new ItemStack(Registration.DUST_POSIRITE.get(), 64));
        BlockPos storage = new BlockPos(123, 65, 138);
        put(level, storage, Registration.STORAGE.get());
        stock(level, storage, new ItemStack(Registration.DUST_SILICON.get(), 64), new ItemStack(Registration.INGOT_MANGANESE.get(), 64),
                new ItemStack(Registration.DUST_NEGARITE.get(), 64), new ItemStack(Registration.DUST_POSIRITE.get(), 64));
        // Give the player materials to take; the manual constructor uses PLAYER inventory.
        BlockPos ingredients = new BlockPos(115, 65, 138);
        put(level, ingredients, Blocks.BARREL);
        Container container = (Container) level.getBlockEntity(ingredients);
        container.setItem(0, new ItemStack(Registration.DUST_SILICON.get(), 64));
        container.setItem(1, new ItemStack(Registration.INGOT_MANGANESE.get(), 64));
        container.setItem(2, new ItemStack(Registration.DUST_NEGARITE.get(), 64));
        container.setItem(3, new ItemStack(Registration.DUST_POSIRITE.get(), 64));
        label(level, 115, 65, 141, "03 / CONSTRUCTION", "Take barrel inputs", "Open constructor", "Double-click circuit");
        label(level, 121, 65, 141, "AUTO CONSTRUCTOR", "Adjacent blueprints", "Both fluxes below", "Outputs: circuits");
    }

    private static void shield(ServerLevel level) {
        put(level, SHIELD, Registration.FORCEFIELD.get());
        ForceFieldTile shield = (ForceFieldTile) level.getBlockEntity(SHIELD);
        shield.setScale(3);
        shield.setRSMode(RedstoneMode.REDSTONE_ONREQUIRED);
        supply(level, SHIELD);
        // A wireless receiver powers the field; its button is outside the shield radius.
        BlockPos receiverPos = SHIELD.north();
        put(level, receiverPos, Registration.SIGNAL_RECEIVER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
        BlockPos buttonPos = new BlockPos(145, 65, 119);
        put(level, buttonPos, Registration.WIRELESS_BUTTON.get());
        WirelessButtonTile button = (WirelessButtonTile) level.getBlockEntity(buttonPos);
        button.setLocked(true);
        ((SignalReceiverTile) level.getBlockEntity(receiverPos)).setChannel(button.getChannel(false));
        for (int x = 147; x <= 155; x++) {
            for (int z = 115; z <= 123; z++) {
                if (x == 147 || x == 155 || z == 115 || z == 123) {
                    put(level, new BlockPos(x, 65, z), Registration.BLUE_GLASS_FENCE.get());
                }
            }
        }
        label(level, 149, 65, 126, "04 / FORCEFIELD", "Radius 3 shield", "Keep outside rail", "Button west toggles");
    }

    private static void doorFrame(ServerLevel level, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int y = 65; y <= 68; y++) {
                if (dx != 0 || y == 68) {
                    level.setBlock(new BlockPos(x + dx, y, z), marble(MarbleColor.DARKBLUE), Block.UPDATE_ALL);
                }
            }
        }
        put(level, new BlockPos(x, 64, z), Registration.DOOR_MARKER.get());
    }

    private static void doors(ServerLevel level) {
        doorFrame(level, 148, 134);
        BlockPos lockPos = new BlockPos(146, 65, 134);
        put(level, lockPos, Registration.LOCK.get());
        LockTile lock = (LockTile) level.getBlockEntity(lockPos);
        lock.setHorizontalRange(3);
        lock.setVerticalRange(3);
        lock.setKeyId("Tutorial");
        lock.setLocked(true);
        BlockPos alarmPos = new BlockPos(154, 66, 134);
        level.setBlock(alarmPos.south(), marble(MarbleColor.DARKBLUE), Block.UPDATE_ALL);
        put(level, alarmPos, Registration.ALARM.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
        ((AlarmTile) level.getBlockEntity(alarmPos)).setAlarmType(AlarmType.SAFE);
        put(level, new BlockPos(155, 65, 138), Registration.AICORE.get());
        ((AICoreTile) level.getBlockEntity(new BlockPos(155, 65, 138))).setCityName("Tutorial exhibit");
        label(level, 147, 65, 141, "05 / KEYCARD DOOR", "Take Tutorial card", "Use card on lock", "Then approach door");
        label(level, 154, 65, 141, "AI CORE / ALARM", "City equipment", "AI runs in Ariente", "Visit via Warper");
    }

    private static void wireless(ServerLevel level) {
        doorFrame(level, 119, 151);
        BlockPos buttonPos = new BlockPos(114, 65, 148);
        put(level, buttonPos, Registration.WIRELESS_BUTTON.get());
        WirelessButtonTile button = (WirelessButtonTile) level.getBlockEntity(buttonPos);
        button.setLocked(true);
        BlockPos lockPos = new BlockPos(117, 65, 151);
        put(level, lockPos, Registration.WIRELESS_LOCK.get());
        WirelessLockTile lock = (WirelessLockTile) level.getBlockEntity(lockPos);
        lock.setHorizontalRange(3);
        lock.setVerticalRange(3);
        lock.setChannel(button.getChannel(false));
        BlockPos receiver = new BlockPos(115, 65, 148);
        put(level, receiver, Registration.SIGNAL_RECEIVER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
        ((SignalReceiverTile) level.getBlockEntity(receiver)).setChannel(button.getChannel(false));
        put(level, receiver.south(), Blocks.REDSTONE_LAMP);
        BlockPos transmitter = new BlockPos(123, 65, 148);
        put(level, transmitter, Registration.SIGNAL_TRANSMITTER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST));
        put(level, transmitter.east(), Blocks.LEVER.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
        BlockPos second = new BlockPos(123, 65, 153);
        put(level, second, Registration.SIGNAL_RECEIVER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
        SignalTransmitterTile tx = (SignalTransmitterTile) level.getBlockEntity(transmitter);
        ((SignalReceiverTile) level.getBlockEntity(second)).setChannel(tx.getChannel(true));
        put(level, second.south(), Blocks.REDSTONE_LAMP);
        label(level, 115, 65, 157, "06 / WIRELESS", "Button: lamp/door", "Lever: transmitter", "Remote lamp south");
        label(level, 122, 65, 157, "TWO CHANNELS", "Pre-linked pairs", "Tablet can re-link", "Doors open nearby");
    }

    private static void elevator(ServerLevel level) {
        BlockPos pos = new BlockPos(149, 64, 150);
        put(level, pos, Registration.ELEVATOR.get());
        for (int x = 146; x <= 154; x++) {
            for (int z = 147; z <= 153; z++) {
                if (x != 149 || z != 150) {
                    level.setBlock(new BlockPos(x, 71, z), marble(MarbleColor.DARKBLUE), Block.UPDATE_ALL);
                }
                if (x == 146 || x == 154 || z == 147 || z == 153) {
                    put(level, new BlockPos(x, 72, z), Registration.BLUE_GLASS_FENCE.get());
                }
            }
        }
        elevatorStops(level);
        label(level, 148, 65, 155, "07 / ELEVATOR", "Walk onto beam", "Select upper floor", "No power required");
        label(level, 151, 72, 152, "OBSERVATION DECK", "Level marker stops", "Select ground floor", "in elevator panel");
    }

    private static void elevatorStops(ServerLevel level) {
        ((ElevatorTile) level.getBlockEntity(new BlockPos(149, 64, 150))).setHeight(9);
        put(level, new BlockPos(149, 71, 150), Blocks.AIR);
        put(level, new BlockPos(150, 65, 150), Registration.LEVEL_MARKER.get());
        put(level, new BlockPos(150, 72, 150), Registration.LEVEL_MARKER.get());
    }

    private static void transport(ServerLevel level) {
        for (int x = 130; x <= 140; x++) {
            put(level, new BlockPos(x, 64, 150), Registration.FLUX_BEAM.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        }
        put(level, new BlockPos(140, 64, 150), Registration.FLUX_BEND_BEAM.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        for (int z = 151; z <= 156; z++) {
            put(level, new BlockPos(140, 64, z), Registration.FLUX_BEAM.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        }
        FluxLevitatorEntity cart = FluxLevitatorEntity.create(level, 132.5, 64.0625, 150.5);
        level.addFreshEntity(cart);
        FluxShipEntity ship = FluxShipEntity.create(level, 133.5, 65, 154.5);
        level.addFreshEntity(ship);
        label(level, 131, 65, 158, "08 / TRANSPORT", "Board flux cart", "Panel: speed/direction", "Corner on east end");
        label(level, 138, 65, 158, "FLUX SHIP", "WASD moves/turns", "Space/Backspace", "Sneak: exit");
    }

    private static ItemStack guide() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", "Ariente Tutorial");
        tag.putString("author", "Ariente");
        ListTag pages = new ListTag();
        for (String page : new String[]{
                "Welcome to Tutorial!\n\nA 9 x 9 chunk city platform in the void. The central 3 x 3 chunks contain the teaching stations.\n\nStart north at Power, then follow the numbered signs. The supply barrel beside this lectern contains tools and the full manual.",
                "01 POWER (north)\n\nEach generator has eight matching tanks and a fuel barrel feeding a hopper. Blue = Negarite; red = Posirite. Each tank adds 1,000 flux/tick.\n\nThe combiner takes 6,000 of each and outputs 5,880 of each to the buried yellow network. The glass hatch shows its route.",
                "02 ITEMS (northwest)\n\nOutput nodes EXTRACT; input nodes INSERT. Both filter iron. The round-robin modifier alternates destinations.\n\nA sensor gates extraction while the source contains items. Hoppers return iron to the source, so this exhibit recirculates. Open the field controller to see its height and outline.",
                "03 CRAFTING (west)\n\nTake four ingredients from the barrel into YOUR inventory. Open the manual Constructor and double-click the circuit blueprint.\n\nThe Auto Constructor has its own ingredients, power and an adjacent Blueprint Storage. Inspect its circuit outputs. Extra materials are in the Ariente Storage.",
                "04 SHIELD (northeast)\n\nA working radius-3 forcefield is behind the rail. Stay outside: active shield panels hurt.\n\nUse the wireless button WEST of the railing to switch it off before approaching the generator. Right-click the generator to change radius or redstone mode. Increase power for larger shields.",
                "05 SECURITY (east)\n\nTake the Tutorial keycard from the welcome barrel. Use it on the lock, then walk near the door. The invisible door blocks belong to the marker under the opening.\n\nThe AI Core and safe Alarm show city equipment. AI behavior belongs to the Ariente dimension; this Overworld exhibit does not spawn defenders.",
                "06 WIRELESS (southwest)\n\nThe left wireless button controls a lamp and the nearby Wireless Lock. Walk near the door after unlocking.\n\nThe right lever supplies the transmitter; its remote receiver controls the southern lamp. These are separate pre-linked channels. Use the tablet to copy/reassign channels.",
                "07 ELEVATOR (southeast)\n\nWalk onto the beam and choose a floor in its hologram. The upper Level Marker provides the observation-deck stop.\n\nElevators do not consume flux.\n\n08 TRANSPORT (south)\nBoard the cart and use its holograms. The track includes a bend. Try the Flux Ship from its landing pad.",
                "09 EXPLORATION (center)\n\nThe charged Warper connects to the actual Ariente dimension. Right-click and choose Warp. The destination Warper returns you here.\n\nCreative mode, daylight, no natural mob spawning and keep-inventory are enabled for teaching. Outer terraces are spare build space. Copy this save before experimenting."
        }) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
        }
        tag.put("pages", pages);
        return book;
    }

    private static void welcome(ServerLevel level) {
        BlockPos lectern = new BlockPos(135, 65, 137);
        put(level, lectern, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        ((LecternBlockEntity) level.getBlockEntity(lectern)).setBook(guide());
        level.setBlock(lectern, level.getBlockState(lectern).setValue(BlockStateProperties.HAS_BOOK, true), Block.UPDATE_ALL);
        BlockPos supplies = new BlockPos(138, 65, 137);
        put(level, supplies, Blocks.BARREL);
        Container barrel = (Container) level.getBlockEntity(supplies);
        barrel.setItem(0, guide());
        ItemStack card = new ItemStack(Registration.KEY_CARD.get());
        KeyCardItem.addSecurityTag(card, "Tutorial");
        card.setHoverName(Component.literal("Tutorial door key"));
        barrel.setItem(1, card);
        barrel.setItem(2, new ItemStack(Registration.TABLET.get()));
        barrel.setItem(3, new ItemStack(Registration.FLUX_LEVITATOR.get()));
        barrel.setItem(4, new ItemStack(Registration.FLUX_SHIP.get()));
        ItemStack manual = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("patchouli", "guide_book")));
        manual.getOrCreateTag().putString("patchouli:book", "ariente:manual");
        barrel.setItem(5, manual);
        int slot = 6;
        for (Item item : new Item[]{Registration.POWERSUIT_HEAD.get(), Registration.POWERSUIT_CHEST.get(), Registration.POWERSUIT_LEGS.get(),
                Registration.POWERSUIT_FEET.get(), Registration.ENERGY_HOLDER.get(), Registration.ENERGY_LANCE.get(), Registration.ENERGY_SABRE.get()}) {
            barrel.setItem(slot++, new ItemStack(item));
        }
        barrel.setItem(slot++, new ItemStack(Registration.DUST_NEGARITE.get(), 64));
        barrel.setItem(slot, new ItemStack(Registration.DUST_POSIRITE.get(), 64));
        BlockPos warper = new BlockPos(140, 64, 132);
        put(level, warper, Registration.WARPER.get());
        ((WarperTile) level.getBlockEntity(warper)).setCharges(UtilityConfiguration.WARPER_MAX_CHARGES.get());
        label(level, 140, 65, 130, "09 / WARPER", "Visit Ariente", "Return via Warper", "Remembers this spot");
        label(level, 135, 65, 143, "WELCOME", "Read the lectern", "Supplies to right", "Follow 01 to 09");
    }

    private static BlockEntity node(ServerLevel level, BlockPos pos, Block block, NodeOrientation orientation) {
        return part(level, pos, block.defaultBlockState().setValue(AbstractNodeTile.ORIENTATION, orientation), orientation.getSlot());
    }

    private static BlockEntity part(ServerLevel level, BlockPos pos, BlockState state, PartSlot slot) {
        if (!(level.getBlockEntity(pos) instanceof MultipartTE)) {
            put(level, pos, Registration.MULTIPART_BLOCK.get());
        }
        MultipartTE tile = (MultipartTE) level.getBlockEntity(pos);
        BlockEntity child = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
        tile.addPart(slot, state, child);
        return child;
    }

    private static void cable(ServerLevel level, BlockPos pos, CableColor color, boolean connector) {
        if (CABLES.add(pos)) {
            put(level, pos, (connector ? Registration.CONNECTOR : Registration.NETCABLE).get().defaultBlockState()
                    .setValue(GenericCableBlock.COLOR, color));
        }
    }

    private static void stock(ServerLevel level, BlockPos pos, ItemStack... stacks) {
        IItemHandlerModifiable handler = (IItemHandlerModifiable) inventory(level, pos);
        for (int slot = 0; slot < stacks.length; slot++) {
            handler.setStackInSlot(slot, stacks[slot]);
        }
        level.getBlockEntity(pos).setChanged();
    }

    private static IItemHandler inventory(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
    }

    private static void put(ServerLevel level, BlockPos pos, Block block) {
        put(level, pos, block.defaultBlockState());
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static void label(ServerLevel level, int x, int y, int z, String... lines) {
        BlockPos pos = new BlockPos(x, y, z);
        put(level, pos, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0));
        SignText text = new SignText();
        for (int i = 0; i < lines.length; i++) {
            text = text.setMessage(i, Component.literal(lines[i]));
        }
        SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(pos);
        sign.setText(text, true);
        sign.setText(text, false);
        sign.setChanged();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) throws IOException {
        if (!running || event.phase != TickEvent.Phase.END) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        ticks++;
        if (ticks % 10 == 0) {
            int[] items = new int[3];
            int index = 0;
            for (BlockPos pos : new BlockPos[]{new BlockPos(115, 66, 117), new BlockPos(120, 67, 117), new BlockPos(123, 67, 117)}) {
                Container barrel = (Container) level.getBlockEntity(pos);
                for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                    items[index] += barrel.getItem(slot).getCount();
                }
                index++;
            }
            if (previousItems != null && !Arrays.equals(items, previousItems)) {
                transfers++;
            }
            previousItems = items;
        }
        if (ticks == 200) {
            require(((ForceFieldTile) level.getBlockEntity(SHIELD)).getUsingPower() > 0, "Shield is not running before its control check");
            ((WirelessButtonTile) level.getBlockEntity(new BlockPos(145, 65, 119))).setLocked(false);
        }
        if (ticks == 220) {
            require(((ForceFieldTile) level.getBlockEntity(SHIELD)).getUsingPower() == 0, "Remote shield button did not switch the field off");
            ((WirelessButtonTile) level.getBlockEntity(new BlockPos(145, 65, 119))).setLocked(true);
        }
        if (ticks == 300 || ticks == 320) {
            BlockPos lever = new BlockPos(124, 65, 148);
            level.setBlockAndUpdate(lever, level.getBlockState(lever).setValue(BlockStateProperties.POWERED, ticks == 300));
        }
        if (ticks == 310 || ticks == 330) {
            require(level.getBlockState(new BlockPos(123, 65, 154)).getValue(BlockStateProperties.LIT) == (ticks == 310),
                    "Transmitter/receiver did not switch the remote lamp at tick " + ticks
                            + "; transmitter input=" + ((SignalTransmitterTile) level.getBlockEntity(new BlockPos(123, 65, 148))).getPowerLevel()
                            + "; receiver output=" + ((SignalReceiverTile) level.getBlockEntity(new BlockPos(123, 65, 153))).getPowerOutput()
                            + "; lever=" + level.getBlockState(new BlockPos(124, 65, 148))
                            + "; physical input=" + level.getBestNeighborSignal(new BlockPos(123, 65, 148)));
        }
        if (ticks == 1000) {
            verify(level);
            Files.writeString(event.getServer().getWorldPath(LevelResource.ROOT).resolve("Tutorial.complete"),
                    "Tutorial: 9x9 city platform; 3x3 teaching plaza. Verified power, routing, crafting, shield and registered machine coverage.\n");
            running = false;
            ArienteWorld.setup.getLogger().info("TUTORIAL VERIFIED: live flux, {} item observations, circuits, shield panels, wireless links; saving map", transfers);
            event.getServer().halt(false);
        }
    }

    private static void verify(ServerLevel level) {
        require(PowerReceiverSupport.getPowerAvailable(level, FIELD, true) > 0, "Automation field has no real flux supply");
        require(PowerReceiverSupport.getPowerAvailable(level, SHIELD, true) > 0, "Shield has no real flux supply");
        require(transfers > 3, "Item automation is not recirculating");
        IItemHandler crafter = inventory(level, CRAFTER);
        int circuits = 0;
        for (int slot = AutoConstructorTile.SLOT_OUTPUT; slot < crafter.getSlots(); slot++) {
            if (crafter.getStackInSlot(slot).is(Registration.CIRCUIT.get())) {
                circuits += crafter.getStackInSlot(slot).getCount();
            }
        }
        require(circuits > 0, "Auto Constructor has not produced circuits");
        ForceFieldTile shield = (ForceFieldTile) level.getBlockEntity(SHIELD);
        int panels = 0;
        for (var panel : shield.getPanelInfo()) {
            if (panel != null && panel.getLife() > 0) {
                panels++;
            }
        }
        require(panels == shield.getPanelInfo().length, "Forcefield is incomplete: panels=" + panels + ", enabled=" + shield.isMachineEnabled()
                + ", input=" + shield.getPowerLevel() + ", usage=" + shield.getUsingPower()
                + ", available=" + PowerReceiverSupport.getPowerAvailable(level, SHIELD, true)
                + ", receiver=" + ((SignalReceiverTile) level.getBlockEntity(SHIELD.north())).getPowerOutput());
        require(((SignalReceiverTile) level.getBlockEntity(new BlockPos(115, 65, 148))).getPowerOutput() == 15,
                "Wireless button/receiver pair does not send redstone");
        require(level.getBlockState(new BlockPos(148, 65, 134)).is(Registration.INVISIBLE_DOOR.get()), "Door marker did not create its door");
        Set<ResourceLocation> coverage = new HashSet<>();
        for (int x = 7; x <= 9; x++) {
            for (int z = 7; z <= 9; z++) {
                for (BlockEntity tile : level.getChunk(x, z).getBlockEntities().values()) {
                    coverage.add(ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(tile.getType()));
                    if (tile instanceof MultipartTE multipart) {
                        multipart.getParts().values().forEach(part -> coverage.add(
                                ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(part.getBlockEntity().getType())));
                    }
                }
            }
        }
        for (var type : Registration.TILES.getEntries()) {
            require(coverage.contains(type.getId()), "Missing saved machine exhibit: " + type.getId());
        }
        require(level.getBlockState(new BlockPos(115, 65, 149)).getValue(BlockStateProperties.LIT), "Wireless button lamp is dark");
        require(level.isEmptyBlock(new BlockPos(63, 64, 136)) && level.isEmptyBlock(new BlockPos(208, 64, 136)),
                "Expected void beyond the nine-chunk platform");
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FluxLevitatorEntity || entity instanceof FluxShipEntity) {
                require(entity.getX() >= 112 && entity.getX() < 160 && entity.getZ() >= 112 && entity.getZ() < 160,
                        "Vehicle left the central plaza");
            }
        }
        ArienteWorld.setup.getLogger().info("TUTORIAL CHECK: {} circuits; {} shield panels; {} registered machine types", circuits, panels, Registration.TILES.getEntries().size());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("TUTORIAL: " + message);
        }
    }
}
