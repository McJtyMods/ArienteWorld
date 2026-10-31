package mcjty.arienteworld;

import com.google.gson.JsonArray;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.world.entity.EntityType;
import com.google.gson.JsonObject;
import mcjty.ariente.blocks.utility.wireless.WirelessLockTile;
import mcjty.ariente.cables.GenericCableTileEntity;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.Palette;
import mcjty.arienteworld.cities.PaletteIndex;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.EditMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class EditorRegressionTests {

    @GameTest(template = "empty")
    public static void captureRestoresPropertiesEquipmentAndMetadata(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO).above(100);
        BuildingPart original = new BuildingPart("editor_fixture", 2, 2,
                new BuildingPart.PalettedSlice[]{new BuildingPart.PalettedSlice(new PaletteIndex[]{
                        new PaletteIndex(' ', ' '), new PaletteIndex(' ', ' '), new PaletteIndex(' ', ' '), new PaletteIndex(' ', ' ')})}, Map.of());
        JsonObject json = original.writeToJSon();
        JsonArray meta = new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("key", "test");
        entry.addProperty("string", "preserved");
        meta.add(entry);
        json.add("meta", meta);
        original = new BuildingPart(json);
        var stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        level.setBlockAndUpdate(pos, stair);
        level.setBlockAndUpdate(pos.east(), Registration.WIRELESS_LOCK.get().defaultBlockState());
        var lock = (WirelessLockTile) level.getBlockEntity(pos.east());
        lock.setHorizontalRange(7);
        lock.setVerticalRange(4);
        lock.setChannel(17);
        lock.setDesiredChannel(17);
        level.setBlockAndUpdate(pos.south(), Blocks.SPAWNER.defaultBlockState());
        var spawner = (SpawnerBlockEntity) level.getBlockEntity(pos.south());
        spawner.setEntityId(EntityType.SKELETON, level.random);
        level.setBlockAndUpdate(pos.south().east(), Registration.NETCABLE.get().defaultBlockState());
        ((GenericCableTileEntity) level.getBlockEntity(pos.south().east())).setMimicBlock(stair);
        Palette palette = new Palette("editor_fixture_palette");
        var captured = EditMode.capturePart(level, pos, original, palette);
        var parsed = new BuildingPart(captured.writeToJSon());
        var parsedPalette = new Palette(palette.writeToJSon());
        BlockPos copy = pos.east(4);
        EditMode.restorePart(level, copy, parsed, parsedPalette);
        helper.assertTrue(level.getBlockState(copy).equals(stair), "Editor exports must retain full native blockstate properties");
        var copiedLock = (WirelessLockTile) level.getBlockEntity(copy.east());
        helper.assertTrue(copiedLock.getHorizontalRange() == 7 && copiedLock.getVerticalRange() == 4 && copiedLock.getDesiredChannel() == 17,
                "Equipment settings must survive capture, JSON and restore");
        var copiedSpawner = (SpawnerBlockEntity) level.getBlockEntity(copy.south());
        helper.assertTrue(copiedSpawner.saveWithoutMetadata().getCompound("SpawnData").getCompound("entity").getString("id").equals("minecraft:skeleton"),
                "Spawner entity type must survive export and restore");
        helper.assertTrue(((GenericCableTileEntity) level.getBlockEntity(copy.south().east())).getMimicBlock().equals(stair),
                "Facade state properties must survive equipment export and restore");
        helper.assertTrue("preserved".equals(parsed.getMetaString("test")), "Part metadata must survive edits");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void commandsVariantsExportAndReload(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var level = server.getLevel(DimensionRegister.DIMENSION);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("80f0303b-086c-481f-8069-4e2e16b267f5"), "EditorFixture"));
        player.setGameMode(GameType.CREATIVE);
        player.moveTo(90248.5, 31, 90248.5, 0, 0); // Center of an isolated station grid.
        var commands = server.getCommands();
        helper.assertTrue(commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(0), "arworld editmode") == 0,
                "Authoring commands must reject non-operators");
        helper.assertTrue(commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "arworld editmode") == 1,
                "The alias must open a real creative edit session");
        var session = EditMode.session(player);
        Path export = session.exportPath();
        byte[] previous = Files.exists(export) ? Files.readAllBytes(export) : null;
        try {
            var placement = session.getPlacements().get(0);
            BlockPos edited = placement.origin().above();
            player.moveTo(edited.getX() + .5, edited.getY(), edited.getZ() + .5, 0, 0);
            level.setBlockAndUpdate(edited, Blocks.GOLD_BLOCK.defaultBlockState());
            helper.assertTrue(commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "arworld syncchunk") == 1,
                    "syncchunk must capture and copy the selected part");
            helper.assertTrue(commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "arworld variantcreate fixture 2") == 1,
                    "Variant creation must use Brigadier's bounded count");
            helper.assertTrue(commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "arworld variantset fixture") == 1,
                    "A palette must accept an existing variant");
            helper.assertTrue(commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "arworld variantswitch fixture 1") == 1,
                    "The alternate part must be selectable");
            helper.assertTrue(commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "arworld savecity") == 1 && Files.size(export) > 0,
                    "savecity must write a complete export");
            level.setBlockAndUpdate(edited, Blocks.DIAMOND_BLOCK.defaultBlockState());
            helper.assertTrue(commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "arworld editmode reload") == 1
                            && level.getBlockState(edited).is(Blocks.GOLD_BLOCK),
                    "Reloading the export must restore authored blocks and selected variants");
        } finally {
            EditMode.end(player);
            if (previous == null) {
                Files.deleteIfExists(export);
            } else {
                Files.write(export, previous);
            }
        }
        helper.succeed();
    }
}
