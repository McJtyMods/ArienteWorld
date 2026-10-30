package mcjty.arienteworld;

import mcjty.ariente.Ariente;
import mcjty.ariente.compat.arienteworld.ArienteWorldCompat;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
        helper.assertTrue(ArienteWorldCompat.getArienteWorld().getNearestCityCenter(new ChunkPos(0, 0)).equals(new ChunkPos(8, 8)),
                "City lookup must use the original sixteen-chunk grid");
        BlockPos testPos = new BlockPos(8, surface + 2, 8);
        dimension.setBlockAndUpdate(testPos, Registration.GLOWLOG.get().defaultBlockState());
        helper.assertTrue(dimension.getBlockState(testPos).getBlock() == Registration.GLOWLOG.get(),
                "Companion block must place in its dimension");
        helper.succeed();
    }
}
