package mcjty.arienteworld;

import java.util.Map;
import mcjty.arienteworld.dimension.DungeonDisplays;
import mcjty.ariente.display.DisplayModuleItem;
import mcjty.ariente.display.DisplayBaseTile;
import java.util.Objects;
import mcjty.ariente.Ariente;
import mcjty.ariente.compat.arienteworld.ArienteWorldCompat;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class PortSmokeTests {

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void dimensionAndIntegration(GameTestHelper helper) {
        ServerLevel dimension = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        helper.assertTrue(dimension != null, "Ariente dimension must load from its data pack");
        dimension.getChunk(0, 0);
        int surface = dimension.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        helper.assertTrue(surface > dimension.getMinBuildHeight(), "Basic dimension must generate terrain");
        helper.assertTrue(Ariente.guiHandler != null, "HoloGui integration must initialize");
        helper.assertTrue(ArienteWorldCompat.getArienteWorld() != null, "Companion integration must initialize");
        ChunkPos expected = CityTools.isCityCenter(dimension.getSeed(), 8, 8) ? new ChunkPos(8, 8) : null;
        helper.assertTrue(Objects.equals(ArienteWorldCompat.getArienteWorld().getNearestCityCenter(new ChunkPos(0, 0)), expected),
                "City lookup must respect configured presence on the original sixteen-chunk grid");
        BlockPos testPos = new BlockPos(8, surface + 2, 8);
        dimension.setBlockAndUpdate(testPos, Registration.GLOWLOG.get().defaultBlockState());
        helper.assertTrue(dimension.getBlockState(testPos).getBlock() == Registration.GLOWLOG.get(),
                "Companion block must place in its dimension");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldgenDisplaysUseTheGenerationLookup(GameTestHelper helper) {
        var block = mcjty.ariente.setup.Registration.DISPLAY_BASE.get();
        var display = new DisplayBaseTile(BlockPos.ZERO, block.defaultBlockState());
        DungeonDisplays.configure(display,
                Map.of("AlienText", "Generation-time console"), helper.getLevel().registryAccess());
        helper.assertTrue(display.getLevel() == null, "Generation can initialize a prototype before it is bound to a world");
        helper.assertTrue(display.modules().size() == 1 && "Generation-time console".equals(
                DisplayModuleItem.config(display.modules().getFirst().stack()).getString("Text")),
                "The generated console retains its module and item configuration");
        helper.succeed();
    }

}
