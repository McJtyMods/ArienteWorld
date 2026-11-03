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
import mcjty.ariente.blocks.utility.SmelterTile;
import mcjty.ariente.blocks.utility.WarperTile;
import mcjty.ariente.blocks.utility.AlarmTile;
import mcjty.ariente.blocks.utility.autofield.AbstractNodeTile;
import mcjty.ariente.blocks.utility.autofield.AutoFieldTile;
import mcjty.ariente.blocks.utility.autofield.InputItemNodeTile;
import mcjty.ariente.blocks.utility.autofield.ItemFilterCategory;
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
import mcjty.ariente.display.DisplayAppearance;
import mcjty.ariente.display.DisplayBackground;
import mcjty.ariente.display.DisplayBaseTile;
import mcjty.ariente.display.DisplayGeometry;
import mcjty.ariente.display.DisplayModuleItem;
import mcjty.ariente.display.InstalledModule;
import mcjty.ariente.entities.fluxship.FluxShipEntity;
import mcjty.ariente.entities.levitator.FluxLevitatorEntity;
import mcjty.ariente.items.BlueprintItem;
import mcjty.ariente.items.KeyCardItem;
import mcjty.ariente.multipart.MultipartTE;
import mcjty.ariente.power.PowerReceiverSupport;
import mcjty.ariente.power.PowerSenderSupport;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.ArienteLandscapeCity;
import mcjty.arienteworld.dimension.CityChunk;
import mcjty.lib.multipart.PartSlot;
import mcjty.lib.tileentity.GenericTileEntity;
import mcjty.lib.varia.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
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
    private static final BlockPos POWER_DISPLAY = new BlockPos(129, 65, 120);
    private static final BlockPos WELCOME_DISPLAY = new BlockPos(135, 65, 138);
    private static final BlockPos WELCOME_STORAGE = new BlockPos(138, 65, 137);
    private static final BlockPos INGREDIENT_STORAGE = new BlockPos(115, 65, 138);
    private static final BlockPos KEYCARD_DOOR = new BlockPos(148, 65, 139);
    private static final BlockPos KEYCARD_LOCK = new BlockPos(147, 66, 140);
    private static final BlockPos SHIELD_BUTTON = new BlockPos(147, 66, 126);
    private static final Set<BlockPos> CABLES = new HashSet<>();
    private static int ticks;
    private static boolean running;
    private static final BlockPos INPUT = new BlockPos(115, 66, 120);
    private static final BlockPos SMELTER = new BlockPos(120, 66, 117);
    private static final BlockPos NEGATIVE = new BlockPos(121, 66, 120);
    private static final BlockPos POSITIVE = new BlockPos(125, 66, 120);
    private static final BlockPos WOOD = new BlockPos(115, 66, 114);
    private static final BlockPos INGOTS = new BlockPos(120, 66, 114);
    private static final BlockPos OTHER = new BlockPos(125, 66, 114);

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
            // The saved introduction batch is for players; restart the deterministic verification batch.
            IItemHandler source = inventory(level, INPUT);
            for (int slot = 0; slot < source.getSlots(); slot++) {
                source.extractItem(slot, 64, false);
            }
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

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) throws IOException {
        if (!Boolean.getBoolean("ariente.tutorial")) {
            return;
        }
        var root = event.getServer().getWorldPath(LevelResource.ROOT);
        if (Files.exists(root.resolve("Tutorial.complete"))) {
            // Dedicated servers create saves with cheats disabled. Enable them for the singleplayer tutorial.
            var levelFile = root.resolve("level.dat").toFile();
            CompoundTag saved = NbtIo.readCompressed(levelFile);
            saved.getCompound("Data").putBoolean("allowCommands", true);
            NbtIo.writeCompressed(saved, levelFile);
            ArienteWorld.setup.getLogger().info("TUTORIAL: saved world permits survival/creative commands");
        }
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
        displays(level);
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
        require(!CityTools.isCityCenter(level.getSeed(), 8, 8), "Tutorial seed must leave the teaching city free of dungeons");
        for (int chunkX = 4; chunkX <= 12; chunkX++) {
            for (int chunkZ = 4; chunkZ <= 12; chunkZ++) {
                if (chunkX >= 7 && chunkX <= 9 && chunkZ >= 7 && chunkZ <= 9) {
                    continue;
                }
                CityChunk city = new CityChunk(level, new ChunkPos(chunkX, chunkZ));
                city.fill(marble(MarbleColor.BLACK), FLOOR - 3, ArienteLandscapeCity.CITY_LEVEL - 2);
                ArienteLandscapeCity.generate(chunkX, chunkZ, city);
            }
        }
        // Only the central nine chunks are cleared for the teaching plaza.
        for (int x = 112; x < 160; x++) {
            for (int z = 112; z < 160; z++) {
                for (int y = FLOOR - 3; y <= FLOOR; y++) {
                    BlockState state = marble(MarbleColor.BLACK);
                    if (y == FLOOR) {
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
            }
        }
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
        DisplayBaseTile[] screen = displayRow(level, POWER_DISPLAY, 6, 4,
                new DisplayAppearance(DisplayBackground.TRANSPARENT, 0x102c3b, 70, true));
        install(screen[0], text("01 / POWER", 2, 0xd2faff), 4, 4);
        install(screen[3], text("8 tanks per tower\nFuel barrels north", 1, 0xd2faff), 4, 4);
        install(screen[0], text("Combiner: 6,000/t in -> 5,880/t out each / 2% loss", 1, 0xd2faff), 4, 40);
        for (int column : new int[]{0, 3}) {
            BlockPos generator = new BlockPos(column == 0 ? 130 : 134, 65, 114);
            install(screen[column], powerModule(level, generator, 1), 24, 72);
            install(screen[column], powerModule(level, generator, 2), 24, 144);
        }
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
        field.setHeight(7);
        for (int x = 113; x <= 126; x++) {
            for (int z = 113; z <= 121; z++) {
                BlockPos pos = new BlockPos(x, 65, z);
                if ((x == 113 || x == 126 || z == 113 || z == 121) && !pos.equals(FIELD)) {
                    part(level, pos, Registration.FIELD_MARKER.get().defaultBlockState(), PartSlot.DOWN);
                }
            }
        }
        supply(level, FIELD);
        put(level, INPUT, Registration.STORAGE.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        OutputItemNodeTile output = (OutputItemNodeTile) node(level, INPUT.north(), Registration.OUTPUT_ITEM_NODE.get(), NodeOrientation.SOUTH_DE);
        CompoundTag tag = output.saveWithoutMetadata();
        tag.getCompound("Info").putBoolean("outBlacklist", true);
        output.load(tag);
        part(level, INPUT.north(), Registration.ROUND_ROBIN_NODE.get().defaultBlockState()
                .setValue(AbstractNodeTile.ORIENTATION, NodeOrientation.SOUTH_DE), PartSlot.SOUTH_DE_BACK);
        SensorItemNodeTile sensor = (SensorItemNodeTile) node(level, INPUT.south(), Registration.SENSOR_ITEM_NODE.get(), NodeOrientation.NORTH_DE);
        tag = sensor.saveWithoutMetadata();
        tag.getCompound("Info").putInt("op", 3);
        tag.getCompound("Info").putInt("amount", 1);
        sensor.load(tag);
        tag = output.saveWithoutMetadata();
        tag.getCompound("Info").putInt("f0", 0);
        output.load(tag);

        put(level, SMELTER, Registration.SMELTER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        categoryInput(level, SMELTER.west(), NodeOrientation.EAST_DN, ItemFilterCategory.SMELTABLE);
        OutputItemNodeTile products = (OutputItemNodeTile) node(level, SMELTER.north(), Registration.OUTPUT_ITEM_NODE.get(), NodeOrientation.SOUTH_DE);
        tag = products.saveWithoutMetadata();
        tag.getCompound("Info").putBoolean("outBlacklist", true);
        products.load(tag);
        BlockPos combiner = new BlockPos(123, 66, 120);
        put(level, combiner, Registration.POWER_COMBINER.get());
        ((PowerCombinerTile) level.getBlockEntity(combiner)).setPowerTransfer(2500);
        for (boolean negative : new boolean[]{true, false}) {
            BlockPos generator = negative ? NEGATIVE : POSITIVE;
            put(level, generator, (negative ? Registration.NEGARITE_GENERATOR : Registration.POSIRITE_GENERATOR).get());
            ((GenericTileEntity) level.getBlockEntity(generator)).setRSMode(RedstoneMode.REDSTONE_IGNORED);
            stock(level, generator, new ItemStack((negative ? Registration.DUST_NEGARITE : Registration.DUST_POSIRITE).get(), 16));
            for (int y = 1; y <= 3; y++) {
                put(level, generator.above(y), (negative ? Registration.NEGARITE_TANK : Registration.POSIRITE_TANK).get());
            }
            cable(level, generator.offset(negative ? 1 : -1, 0, 0), negative ? CableColor.NEGARITE : CableColor.POSIRITE, true);
            InputItemNodeTile fuel = (InputItemNodeTile) node(level, generator.south(), Registration.INPUT_ITEM_NODE.get(), NodeOrientation.NORTH_DE);
            fuel.getInputFilter().set(0, new ItemStack((negative ? Registration.DUST_NEGARITE : Registration.DUST_POSIRITE).get()));
        }
        // A visible overhead yellow bus keeps this smelter supply separate from the plaza bus.
        for (int y = 67; y <= 69; y++) {
            cable(level, new BlockPos(123, y, 120), CableColor.COMBINED, y == 67);
            cable(level, new BlockPos(120, y, 117), CableColor.COMBINED, y == 67);
        }
        for (int z = 117; z <= 120; z++) {
            cable(level, new BlockPos(123, 69, z), CableColor.COMBINED, false);
        }
        for (int x = 120; x <= 123; x++) {
            cable(level, new BlockPos(x, 69, 117), CableColor.COMBINED, false);
        }
        for (BlockPos destination : new BlockPos[]{WOOD, INGOTS, OTHER}) {
            Block block = destination.equals(WOOD) ? Registration.HOLO_STORAGE_1.get()
                    : destination.equals(INGOTS) ? Registration.HOLO_STORAGE_2.get() : Registration.HOLO_STORAGE_3.get();
            put(level, destination, block);
            categoryInput(level, destination.south(), NodeOrientation.NORTH_DE,
                    destination.equals(WOOD) ? ItemFilterCategory.WOOD : destination.equals(INGOTS) ? ItemFilterCategory.INGOTS : ItemFilterCategory.OTHER);
        }
        label(level, 115, 67, 120, "INPUT / 2x2", "Four types at a time");
        label(level, 115, 67, 114, "WOOD", "Non-smeltable wood");
        label(level, 120, 67, 114, "INGOTS", "All tagged metals");
        label(level, 125, 67, 114, "VARIOUS", "Other finished items");
        label(level, 115, 65, 123, "02 / AUTOMATION", "2x2 cube: add items", "Output = extract", "Sensor: source >=1");
        label(level, 120, 65, 123, "SMELT + REFUEL", "Recipes -> smelter", "Dust -> generators", "Own overhead power");
        label(level, 125, 65, 123, "SORTED STORAGE", "Wood / ingots / other", "Full? Wait in source", "Logs smelt to charcoal");
    }

    private static void categoryInput(ServerLevel level, BlockPos pos, NodeOrientation orientation, ItemFilterCategory category) {
        InputItemNodeTile input = (InputItemNodeTile) node(level, pos, Registration.INPUT_ITEM_NODE.get(), orientation);
        input.getInputFilter().set(0, new ItemStack(Registration.DUST_NEGARITE.get()));
        input.getInputFilter().set(1, new ItemStack(Registration.DUST_POSIRITE.get()));
        CompoundTag tag = input.saveWithoutMetadata();
        tag.getCompound("Info").putBoolean("inBlacklist", true);
        input.load(tag);
        input.setInputCategory(category);
    }

    private static void displays(ServerLevel level) {
        // Three independent four-base screens, with a walkway between the booth and its instructions.
        DisplayBaseTile[] glass = displayRow(level, new BlockPos(114, 65, 126), 4, 3,
                new DisplayAppearance(DisplayBackground.TRANSPARENT, 0x64dce8, 78, true));
        install(glass[0], text("LIVE / PROCESSING", 2, 0x83f2ff), 4, 4);
        install(glass[0], text("Meter + inventory list", 1, 0xffffff), 4, 40);
        install(glass[0], powerModule(level, SMELTER, 1), 0, 68);
        install(glass[2], storageModule(level, INGOTS, false), 0, 68);
        install(glass[0], text("Transparent 78% / cyan / border", 1, 0x83f2ff), 4, 164);

        DisplayBaseTile[] solid = displayRow(level, new BlockPos(119, 65, 126), 4, 3,
                new DisplayAppearance(DisplayBackground.SOLID, 0xe8eee3, 0, false));
        install(solid[0], text("STORAGE / GRID", 2, 0x123c32), 4, 4);
        install(solid[0], text("Icons + compact power numbers", 1, 0x123c32), 4, 40);
        ItemStack grid = storageModule(level, OTHER, true);
        DisplayModuleItem.config(grid).putInt("Color", 0x123c32);
        install(solid[0], grid, 4, 68);
        ItemStack numbers = powerModule(level, NEGATIVE, 0);
        DisplayModuleItem.config(numbers).putInt("Color", 0x123c32);
        install(solid[2], numbers, 4, 68);
        install(solid[0], text("Solid / light green / no border", 1, 0x123c32), 4, 164);

        DisplayBaseTile[] floating = displayRow(level, new BlockPos(124, 65, 126), 4, 3,
                new DisplayAppearance(DisplayBackground.INVISIBLE, 0xffffff, 100, false));
        install(floating[0], text("FLUX HISTORY", 2, 0xffc36b), 4, 4);
        install(floating[0], text("Live generator output / 32 seconds", 1, 0xffe3bc), 4, 40);
        install(floating[0], powerModule(level, POSITIVE, 2), 0, 68);
        install(floating[2], powerModule(level, POSITIVE, 0), 4, 68);
        install(floating[0], text("Invisible / amber / no border", 1, 0xffc36b), 4, 164);
        label(level, 121, 65, 131, "10 / DISPLAYS", "Click base: surface", "Click module: settings", "Hold + drag: layout");
    }

    private static ItemStack text(String text, int scale, int color) {
        ItemStack stack = new ItemStack(Registration.DISPLAY_TEXT.get());
        CompoundTag config = DisplayModuleItem.config(stack);
        config.putString("Text", text);
        config.putInt("Scale", scale);
        config.putInt("Color", color);
        return stack;
    }

    private static ItemStack linkedModule(ServerLevel level, Item item, BlockPos target) {
        ItemStack stack = new ItemStack(item);
        CompoundTag link = new CompoundTag();
        link.putString("Dimension", level.dimension().location().toString());
        link.putLong("Pos", target.asLong());
        link.putInt("Side", Direction.UP.get3DDataValue());
        link.putString("Block", ForgeRegistries.BLOCKS.getKey(level.getBlockState(target).getBlock()).toString());
        DisplayModuleItem.config(stack).put("Link", link);
        return stack;
    }

    private static ItemStack powerModule(ServerLevel level, BlockPos target, int mode) {
        ItemStack stack = linkedModule(level, Registration.DISPLAY_POWER.get(), target);
        CompoundTag config = DisplayModuleItem.config(stack);
        config.putInt("Mode", mode);
        config.putBoolean("Available", true);
        config.putBoolean("Usage", target.equals(SMELTER));
        config.putBoolean("Output", !target.equals(SMELTER));
        config.putBoolean("Fuel", !target.equals(SMELTER));
        return stack;
    }

    private static ItemStack storageModule(ServerLevel level, BlockPos target, boolean grid) {
        ItemStack stack = linkedModule(level, Registration.DISPLAY_STORAGE.get(), target);
        CompoundTag config = DisplayModuleItem.config(stack);
        config.putInt("Count", grid ? 8 : 3);
        config.putInt("Columns", 4);
        config.putBoolean("Grid", grid);
        return stack;
    }

    private static DisplayBaseTile[] displayRow(ServerLevel level, BlockPos left, int width, int height, DisplayAppearance appearance) {
        DisplayBaseTile[] bases = new DisplayBaseTile[width];
        for (int column = 0; column < width; column++) {
            BlockPos pos = left.east(column);
            require(level.isEmptyBlock(pos), "Display overlaps another exhibit at " + pos);
            put(level, pos, Registration.DISPLAY_BASE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            DisplayBaseTile base = (DisplayBaseTile) level.getBlockEntity(pos);
            CompoundTag tag = base.saveWithoutMetadata();
            tag.putInt("Height", height);
            appearance.write(tag);
            base.load(tag);
            base.changed();
            bases[column] = base;
        }
        return bases;
    }

    private static void install(DisplayBaseTile base, ItemStack stack, int x, int y) {
        InstalledModule module = new InstalledModule(stack, x, y);
        require(DisplayGeometry.canPlace(base, module.size(), x, y), "Module does not fit its tutorial display at " + base.getBlockPos());
        CompoundTag tag = base.saveWithoutMetadata();
        tag.getList("Modules", Tag.TAG_COMPOUND).add(module.save(false));
        base.load(tag);
        base.changed();
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
        put(level, INGREDIENT_STORAGE, Registration.STORAGE.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        stock(level, INGREDIENT_STORAGE, new ItemStack(Registration.DUST_SILICON.get(), 64), new ItemStack(Registration.INGOT_MANGANESE.get(), 64),
                new ItemStack(Registration.DUST_NEGARITE.get(), 64), new ItemStack(Registration.DUST_POSIRITE.get(), 64));
        label(level, 115, 65, 141, "03 / CONSTRUCTION", "Take storage inputs", "Open constructor", "Double-click circuit");
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
        level.setBlock(SHIELD_BUTTON.north().below(), marble(MarbleColor.DARKBLUE), Block.UPDATE_ALL);
        level.setBlock(SHIELD_BUTTON.north(), marble(MarbleColor.DARKBLUE), Block.UPDATE_ALL);
        put(level, SHIELD_BUTTON, Registration.WIRELESS_BUTTON.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        WirelessButtonTile button = (WirelessButtonTile) level.getBlockEntity(SHIELD_BUTTON);
        button.setLocked(true);
        ((SignalReceiverTile) level.getBlockEntity(receiverPos)).setChannel(button.getChannel(false));
        for (int x = 147; x <= 155; x++) {
            for (int z = 115; z <= 123; z++) {
                if (x == 147 || x == 155 || z == 115 || z == 123) {
                    put(level, new BlockPos(x, 65, z), Registration.BLUE_GLASS_FENCE.get());
                }
            }
        }
        label(level, 149, 65, 126, "04 / FORCEFIELD", "Radius 3 shield", "Keep outside rail", "Button left toggles");
    }

    private static void doorFrame(ServerLevel level, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int y = 65; y <= 68; y++) {
                if (dx != 0 || y == 68) {
                    level.setBlock(new BlockPos(x + dx, y, z), marble(MarbleColor.DARKBLUE), Block.UPDATE_ALL);
                }
            }
        }
        put(level, new BlockPos(x, 65, z), Registration.DOOR_MARKER.get());
    }

    private static void doors(ServerLevel level) {
        doorFrame(level, KEYCARD_DOOR.getX(), KEYCARD_DOOR.getZ());
        put(level, KEYCARD_LOCK, Registration.LOCK.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        LockTile lock = (LockTile) level.getBlockEntity(KEYCARD_LOCK);
        lock.setHorizontalRange(3);
        lock.setVerticalRange(3);
        lock.setKeyId("Tutorial");
        lock.setLocked(true);
        BlockPos alarmPos = new BlockPos(154, 66, 134);
        level.setBlock(alarmPos, marble(MarbleColor.DARKBLUE), Block.UPDATE_ALL);
        alarmPos = alarmPos.south();
        put(level, alarmPos, Registration.ALARM.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.SOUTH));
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
        // The 1.3-block-wide cart needs an exposed rail, not a slot sunk into a full-block floor.
        for (int x = 129; x <= 141; x++) {
            for (int z : new int[]{146, 156}) {
                put(level, new BlockPos(x, 65, z), Registration.FLUX_BEAM.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            }
        }
        for (int z = 147; z < 156; z++) {
            for (int x : new int[]{129, 141}) {
                put(level, new BlockPos(x, 65, z), Registration.FLUX_BEAM.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            }
        }
        int[][] corners = {{129, 146}, {141, 146}, {141, 156}, {129, 156}};
        Direction[] facings = {Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH};
        for (int i = 0; i < corners.length; i++) {
            put(level, new BlockPos(corners[i][0], 65, corners[i][1]), Registration.FLUX_BEND_BEAM.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facings[i]));
        }
        FluxLevitatorEntity cart = FluxLevitatorEntity.create(level, 132.5, 65.0625, 146.5);
        level.addFreshEntity(cart);
        FluxShipEntity ship = FluxShipEntity.create(level, 135, 65, 151.5);
        level.addFreshEntity(ship);
        label(level, 131, 65, 158, "08 / TRANSPORT", "W: accelerate", "S: slow down", "Panels: reverse/stop");
        label(level, 138, 65, 158, "FLUX SHIP", "WASD moves/turns", "Space/Backspace", "Sneak: exit");
    }

    private static ItemStack guide() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", "Ariente Tutorial");
        tag.putString("author", "Ariente");
        ListTag pages = new ListTag();
        for (String page : new String[]{
                "Welcome to Tutorial!\n\nA 9 x 9 chunk city platform in the void. The central 3 x 3 chunks contain the teaching stations.\n\nStart north at Power, then follow the numbered displays. The Holo Storage beside this lectern contains tools and the full manual.",
                "01 POWER (north)\n\nEach generator has eight matching tanks and a fuel barrel feeding a hopper. Blue = Negarite; red = Posirite. Each tank adds 1,000 flux/tick.\n\nThe combiner takes 6,000 of each and outputs 5,880 of each to the buried yellow network. The glass hatch shows its route.",
                "02 ITEMS (northwest)\n\nThe field uses only a closed marker border; the inside needs no markers.\n\nPut up to four item types in the 2x2 input cube. Output nodes EXTRACT; input nodes INSERT. A sensor enables extraction when items are present.\n\nSmeltables go to the Smelter. Its fuel towers and overhead bus supply power. Both raw ores and dust can be added: smelted dust refuels the matching generator.",
                "02 SORTING\n\nNon-smeltables go to Wood, Ingots or Various holo cubes. Categories follow current furnace recipes and item tags. Fuel dust is excluded from storage.\n\nLogs smelt to charcoal; planks go to Wood. If the smelter or destination fills, items wait safely in the input. Add more supplies to keep processing. The welcome storage has sample batches.",
                "10 DISPLAYS (west of center)\n\nThree independent joined screens show transparent cyan, solid pale green and invisible amber surfaces. Each is four bases wide and three blocks tall.\n\nCompare text sizes, a power meter, numbers, live history, storage lists and item grids. Click a base or module to configure; hold right-click and drag to rearrange modules.",
                "03 CRAFTING (west)\n\nTake four ingredients from the Storage Cube into YOUR inventory. Open the manual Constructor and double-click the circuit blueprint.\n\nThe Auto Constructor has its own ingredients, power and an adjacent Blueprint Storage. Inspect its circuit outputs. Extra materials are in the Ariente Storage.",
                "04 SHIELD (northeast)\n\nA working radius-3 forcefield is behind the rail. Stay outside: active shield panels hurt.\n\nUse the wireless button beside the display to switch it off before approaching the generator. Right-click the generator to change radius or redstone mode. Increase power for larger shields.",
                "05 SECURITY (east)\n\nTake the Tutorial keycard from the welcome storage. Use it on the lock, then walk near the door. The invisible door blocks belong to the marker under the opening.\n\nThe AI Core and safe Alarm show city equipment. AI behavior belongs to the Ariente dimension; this Overworld exhibit does not spawn defenders.",
                "06 WIRELESS (southwest)\n\nThe left wireless button controls a lamp and the nearby Wireless Lock. Walk near the door after unlocking.\n\nThe right lever supplies the transmitter; its remote receiver controls the southern lamp. These are separate pre-linked channels. Use the tablet to copy/reassign channels.",
                "07 ELEVATOR (southeast)\n\nWalk onto the beam and choose a floor in its hologram. The upper Level Marker provides the observation-deck stop.\n\n08 TRANSPORT (south)\nBoard the cart. Hold Forward (W) to accelerate where you face, Back (S) to slow down; release to coast. Turn around and hold W to reverse gradually. Both panels also set speed or stop instantly. Movement key remaps work.",
                "09 EXPLORATION (center)\n\nThe charged Warper connects to the actual Ariente dimension. Right-click and choose Warp. The destination Warper returns you here.\n\nCheats are enabled: /gamemode survival or /gamemode creative. Daylight, no natural mob spawning and keep-inventory are enabled for teaching. The surrounding 72 chunks use ArienteWorld city generation, including buildings, parks and levitator corridors. Copy this save before experimenting."
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
        put(level, WELCOME_STORAGE, Registration.HOLO_STORAGE_1.get());
        ItemStack card = new ItemStack(Registration.KEY_CARD.get());
        KeyCardItem.addSecurityTag(card, "Tutorial");
        card.setHoverName(Component.literal("Tutorial door key"));
        ItemStack manual = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("patchouli", "guide_book")));
        manual.getOrCreateTag().putString("patchouli:book", "ariente:manual");
        stock(level, WELCOME_STORAGE, guide(), card, new ItemStack(Registration.TABLET.get()),
                new ItemStack(Registration.FLUX_LEVITATOR.get()), new ItemStack(Registration.FLUX_SHIP.get()), manual);
        for (Item item : new Item[]{Registration.POWERSUIT_HEAD.get(), Registration.POWERSUIT_CHEST.get(), Registration.POWERSUIT_LEGS.get(),
                Registration.POWERSUIT_FEET.get(), Registration.ENERGY_HOLDER.get(), Registration.ENERGY_LANCE.get(), Registration.ENERGY_SABRE.get()}) {
            stock(level, WELCOME_STORAGE, new ItemStack(item));
        }
        stock(level, WELCOME_STORAGE, new ItemStack(Registration.DUST_NEGARITE.get(), 64), new ItemStack(Registration.DUST_POSIRITE.get(), 64));
        for (Item item : new Item[]{Items.RAW_IRON, Items.SAND, Items.OAK_PLANKS, Items.DIAMOND, Items.RAW_GOLD,
                Items.OAK_LOG, Registration.ORE_NEGARITE.get().asItem(), Registration.ORE_POSIRITE.get().asItem()}) {
            stock(level, WELCOME_STORAGE, new ItemStack(item, 16));
        }
        stock(level, WELCOME_STORAGE, new ItemStack(Registration.DISPLAY_BASE.get(), 8), text("Your display\nClick to edit", 1, 0x83f2ff),
                new ItemStack(Registration.DISPLAY_POWER.get()), new ItemStack(Registration.DISPLAY_STORAGE.get()));
        BlockPos warper = new BlockPos(140, 64, 132);
        put(level, warper, Registration.WARPER.get());
        ((WarperTile) level.getBlockEntity(warper)).setCharges(UtilityConfiguration.WARPER_MAX_CHARGES.get());
        label(level, 140, 65, 130, "09 / WARPER", "Visit Ariente", "Return via Warper", "Remembers this spot");
        label(level, WELCOME_DISPLAY, 2, "WELCOME", "Read the lectern", "Storage to right", "Displays: booth 10");
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
        IItemHandler handler = inventory(level, pos);
        for (ItemStack stack : stacks) {
            require(ItemHandlerHelper.insertItem(handler, stack.copy(), false).isEmpty(), "Cannot stock " + stack + " at " + pos);
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
        label(level, new BlockPos(x, y, z), 1, lines);
    }

    private static void label(ServerLevel level, BlockPos center, int height, String... lines) {
        ItemStack module = text(String.join("\n", lines), 1, 0xd2faff);
        int width = (((DisplayModuleItem) module.getItem()).type().size(module).width() + 4 + 63) / 64;
        DisplayBaseTile[] bases = displayRow(level, center.west(width / 2), width, height,
                new DisplayAppearance(DisplayBackground.SOLID, 0x102c3b, 0, true));
        install(bases[0], module, 4, 4);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) throws IOException {
        if (!running || event.phase != TickEvent.Phase.END) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        ticks++;
        automationChecks(level);
        if (ticks == 200) {
            require(((ForceFieldTile) level.getBlockEntity(SHIELD)).getUsingPower() > 0, "Shield is not running before its control check");
            ((WirelessButtonTile) level.getBlockEntity(SHIELD_BUTTON)).setLocked(false);
        }
        if (ticks == 220) {
            require(((ForceFieldTile) level.getBlockEntity(SHIELD)).getUsingPower() == 0, "Remote shield button did not switch the field off");
            ((WirelessButtonTile) level.getBlockEntity(SHIELD_BUTTON)).setLocked(true);
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
            // Leave four useful input types ready to process when the player opens the save.
            stock(level, INPUT, new ItemStack(Items.RAW_IRON, 64), new ItemStack(Registration.DUST_NEGARITE.get(), 32),
                    new ItemStack(Registration.DUST_POSIRITE.get(), 32), new ItemStack(Items.OAK_PLANKS, 64));
            Files.writeString(event.getServer().getWorldPath(LevelResource.ROOT).resolve("Tutorial.complete"),
                    "Tutorial: 9x9 city platform; 3x3 teaching plaza. Verified displays, recipe sorting, smelting, refueling, power, crafting, shield, two cart end panels and machine coverage.\n");
            running = false;
            ArienteWorld.setup.getLogger().info("TUTORIAL VERIFIED: displays, smelting, refueling, sorted storage, circuits, shield, wireless and two cart end panels; saving map");
            event.getServer().halt(false);
        }
    }

    private static int count(ServerLevel level, BlockPos pos, Item item) {
        IItemHandler inventory = inventory(level, pos);
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).is(item)) {
                count += inventory.getStackInSlot(slot).getCount();
            }
        }
        return count;
    }

    private static void automationChecks(ServerLevel level) {
        if (ticks == 20) {
            // Restart verification begins with a stocked input. Clear it using normal extraction.
            IItemHandler source = inventory(level, INPUT);
            for (int slot = 0; slot < source.getSlots(); slot++) {
                source.extractItem(slot, 64, false);
            }
            stock(level, INPUT, new ItemStack(Items.RAW_IRON, 2), new ItemStack(Items.SAND, 2),
                    new ItemStack(Registration.DUST_NEGARITE.get(), 4), new ItemStack(Registration.DUST_POSIRITE.get(), 4));
        }
        if (ticks == 180) {
            require(count(level, INPUT, Items.RAW_IRON) == 0 && count(level, INPUT, Items.SAND) == 0, "Initial smelting batch did not drain");
            require(count(level, INPUT, Registration.DUST_NEGARITE.get()) == 0 && count(level, INPUT, Registration.DUST_POSIRITE.get()) == 0,
                    "Fuel dust did not leave the source");
            require(count(level, NEGATIVE, Registration.DUST_NEGARITE.get()) > 16 && count(level, POSITIVE, Registration.DUST_POSIRITE.get()) > 16,
                    "Fuel dust was not inserted into the matching generator");
            stock(level, INPUT, new ItemStack(Items.RAW_GOLD, 2), new ItemStack(Items.CHICKEN, 2),
                    new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.DIAMOND, 2));
        }
        if (ticks == 340) {
            stock(level, INPUT, new ItemStack(Registration.ORE_NEGARITE.get(), 2), new ItemStack(Registration.ORE_POSIRITE.get(), 2),
                    new ItemStack(Items.OAK_LOG, 2));
        }
        if (ticks == 600) {
            require(((SmelterTile) level.getBlockEntity(SMELTER)).getStack(SmelterTile.SLOT_INPUT).isEmpty()
                    && ((SmelterTile) level.getBlockEntity(SMELTER)).getStack(SmelterTile.SLOT_OUTPUT).isEmpty(), "Smelter backlog did not drain");
            require(count(level, INPUT, Registration.ORE_NEGARITE.get().asItem()) == 0 && count(level, INPUT, Registration.ORE_POSIRITE.get().asItem()) == 0,
                    "Fuel ores were not processed");
            require(count(level, NEGATIVE, Registration.DUST_NEGARITE.get()) > 18 && count(level, POSITIVE, Registration.DUST_POSIRITE.get()) > 18,
                    "Smelted fuel dust did not reach the generators");
            for (BlockPos pos : new BlockPos[]{WOOD, INGOTS, OTHER}) {
                require(count(level, pos, Registration.DUST_NEGARITE.get()) == 0 && count(level, pos, Registration.DUST_POSIRITE.get()) == 0,
                        "Fuel leaked into storage");
                IItemHandler handler = inventory(level, pos);
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    require(stack.isEmpty() || !ItemFilterCategory.SMELTABLE.matches(level, stack), "Unprocessed item leaked into storage: " + stack);
                }
            }
        }
    }

    private static void verify(ServerLevel level) {
        if (Boolean.getBoolean("ariente.tutorial.verify")) {
            require(level.getServer().getWorldData().getAllowCommands(), "Reloaded Tutorial must permit survival/creative commands");
        }
        require(level.getBlockState(WELCOME_DISPLAY).getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.SOUTH
                && WELCOME_DISPLAY.getZ() < SPAWN.getZ(), "Welcome display must face the player at spawn");
        require(level.getBlockState(WELCOME_STORAGE).is(Registration.HOLO_STORAGE_1.get())
                && count(level, WELCOME_STORAGE, Registration.KEY_CARD.get()) == 1, "Welcome supplies need populated Ariente storage");
        require(level.getBlockState(INGREDIENT_STORAGE).is(Registration.STORAGE.get())
                && count(level, INGREDIENT_STORAGE, Registration.DUST_SILICON.get()) == 64
                && count(level, INGREDIENT_STORAGE, Registration.INGOT_MANGANESE.get()) == 64,
                "Construction ingredients need four-type Ariente storage");
        require(level.getBlockState(KEYCARD_LOCK).getValue(BlockStateProperties.FACING) == Direction.SOUTH
                && level.getBlockState(SHIELD_BUTTON).getValue(BlockStateProperties.FACING) == Direction.SOUTH,
                "Door lock and shield button must face the reader");
        DisplayBaseTile power = (DisplayBaseTile) level.getBlockEntity(POWER_DISPLAY);
        require(power.getHeight() == 4 && DisplayGeometry.row(power).size() == 6, "Power station needs its large four-block-tall display");
        require(power.background() == DisplayBackground.TRANSPARENT && power.appearance().transparency() == 70,
                "Power display must remain 70% transparent");
        require(level.getBlockState(new BlockPos(154, 66, 135)).getValue(BlockStateProperties.FACING) == Direction.SOUTH,
                "AI demo alarm must face its reader");
        long powerModules = DisplayGeometry.row(power).stream().flatMap(base -> base.modules().stream())
                .filter(module -> module.stack().is(Registration.DISPLAY_POWER.get())).count();
        require(powerModules == 4, "Power station needs four live generator monitors");
        require(PowerReceiverSupport.getPowerAvailable(level, FIELD, true) > 0, "Automation field has no real flux supply");
        for (int x = 113; x <= 126; x++) {
            for (int z = 113; z <= 121; z++) {
                BlockPos pos = new BlockPos(x, 65, z);
                if (!pos.equals(FIELD)) {
                    boolean marker = level.getBlockEntity(pos) instanceof MultipartTE multipart
                            && multipart.getParts().values().stream().anyMatch(part -> part.getState().is(Registration.FIELD_MARKER.get()));
                    require(marker == (x == 113 || x == 126 || z == 113 || z == 121),
                            "Tutorial must have markers only on its closed border at " + pos);
                }
            }
        }
        require(PowerReceiverSupport.getPowerAvailable(level, SHIELD, true) > 0, "Shield has no real flux supply");
        require(PowerReceiverSupport.getPowerAvailable(level, SMELTER, true) > 0, "Smelter has no independent power supply");
        require(count(level, WOOD, Items.OAK_PLANKS) >= 2, "Planks did not reach wood storage");
        require(count(level, INGOTS, Items.IRON_INGOT) >= 2 && count(level, INGOTS, Items.GOLD_INGOT) >= 2,
                "Smelted metals did not reach ingot storage");
        require(count(level, OTHER, Items.GLASS) >= 2 && count(level, OTHER, Items.COOKED_CHICKEN) >= 2
                && count(level, OTHER, Items.CHARCOAL) >= 2 && count(level, OTHER, Items.DIAMOND) >= 2,
                "Finished products did not reach various storage");
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
        require(level.getBlockState(KEYCARD_DOOR.above()).is(Registration.INVISIBLE_DOOR.get()), "Door marker did not create its door");
        Set<ResourceLocation> coverage = new HashSet<>();
        for (int x = 7; x <= 9; x++) {
            for (int z = 7; z <= 9; z++) {
                for (BlockEntity tile : level.getChunk(x, z).getBlockEntities().values()) {
                    require(!(tile instanceof SignBlockEntity), "A vanilla sign remains in the plaza");
                    if (tile instanceof DisplayBaseTile display) {
                        for (InstalledModule module : display.modules()) {
                            require(DisplayGeometry.fits(display, module.x(), module.y(), module.size()), "A saved display module does not fit");
                            require(!module.snapshot().contains("Status"), "Display source failed: " + module.snapshot());
                        }
                    }
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
        int tallCityChunks = 0;
        for (int chunkX = 4; chunkX <= 12; chunkX++) {
            for (int chunkZ = 4; chunkZ <= 12; chunkZ++) {
                if (chunkX >= 7 && chunkX <= 9 && chunkZ >= 7 && chunkZ <= 9) {
                    continue;
                }
                int highest = 0;
                for (int x = 2; x < 16; x += 4) {
                    for (int z = 2; z < 16; z += 4) {
                        highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                chunkX * 16 + x, chunkZ * 16 + z));
                    }
                }
                if (highest > 75) {
                    tallCityChunks++;
                }
            }
        }
        require(tallCityChunks >= 8, "The outer city must contain real buildings and parks, not an empty platform");
        for (BlockPos marker : new BlockPos[]{KEYCARD_DOOR, new BlockPos(119, 65, 151)}) {
            require(level.getBlockState(marker).is(Registration.DOOR_MARKER.get()) && !level.isEmptyBlock(marker.below()),
                    "Door markers must rest on the walking floor");
        }
        ArienteWorld.setup.getLogger().info("TUTORIAL CITY CHECK: {} outer chunks have raised city architecture", tallCityChunks);
        require(level.isEmptyBlock(new BlockPos(63, 64, 136)) && level.isEmptyBlock(new BlockPos(208, 64, 136)),
                "Expected void beyond the nine-chunk platform");
        int carts = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FluxLevitatorEntity cart) {
                carts++;
                var front = cart.getHoloGuiFront();
                var back = cart.getHoloGuiBack();
                require(front != null && back != null && front != back, "Cart needs two distinct end panels");
                Entity frontEntity = front.getEntity();
                Entity backEntity = back.getEntity();
                require(frontEntity.isAlive() && backEntity.isAlive()
                                && frontEntity.getVehicle() == cart && backEntity.getVehicle() == cart,
                        "Both cart panels must remain attached after saving and reloading");
                require(Math.abs(frontEntity.position().distanceTo(backEntity.position()) - 2) < .001
                                && frontEntity.getLookAngle().dot(backEntity.getLookAngle()) < -.999,
                        "Cart panels must occupy opposite ends, not overlap");
                require(cart.getPassengers().size() == 2, "Unoccupied tutorial cart must have exactly its two panels");
                ArienteWorld.setup.getLogger().info("TUTORIAL CART CHECK: two attached panels on opposite ends");
            }
            if (entity instanceof FluxLevitatorEntity || entity instanceof FluxShipEntity) {
                require(entity.getX() >= 112 && entity.getX() < 160 && entity.getZ() >= 112 && entity.getZ() < 160,
                        "Vehicle left the central plaza");
            }
        }
        require(carts == 1, "Tutorial must contain exactly one Flux Levitator");
        ArienteWorld.setup.getLogger().info("TUTORIAL CHECK: {} circuits; {} shield panels; {} registered machine types", circuits, panels, Registration.TILES.getEntries().size());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("TUTORIAL: " + message);
        }
    }
}
