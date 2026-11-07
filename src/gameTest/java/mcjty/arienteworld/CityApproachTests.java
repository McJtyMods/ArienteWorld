package mcjty.arienteworld;

import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.CityFeature;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.terrain.ArienteBiomeLayout;
import mcjty.arienteworld.dimension.terrain.BiomeProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class CityApproachTests {

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void oceanApproachesKeepWaterAndRaisedLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City city = findOceanCity(level);
        helper.assertTrue(city != null, "Find a surface structure in the ocean");
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        int seaLevel = generator.getSeaLevel();
        int floor = CityTools.getCityHeight(level, city);
        int wetColumns = 0;
        int landColumns = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                ChunkPos chunk = new ChunkPos(city.getCenter().x + dx, city.getCenter().z + dz);
                level.getChunk(chunk.x, chunk.z);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int distance = Math.max(dx < 0 ? 15 - x : dx > 0 ? x : 0,
                                dz < 0 ? 15 - z : dz > 0 ? z : 0);
                        if (distance >= 12) {
                            continue;
                        }
                        int wx = chunk.getMinBlockX() + x;
                        int wz = chunk.getMinBlockZ() + z;
                        int natural = generator.getBaseHeight(wx, wz, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                        int target = CityFeature.approachHeight(floor, natural, distance);
                        BlockPos ground = new BlockPos(wx, target - 1, wz);
                        helper.assertTrue(level.getBlockState(ground).isSolidRender(level, ground),
                                "The graded foundation must remain solid at " + ground);
                        for (int y = target; y < seaLevel; y++) {
                            BlockPos pos = new BlockPos(wx, y, wz);
                            var state = level.getBlockState(pos);
                            helper.assertTrue(state.is(Blocks.WATER) && state.getFluidState().isSource(),
                                    "Ocean water must fill the space above the graded slope at " + pos + ": " + state);
                        }
                        if (target < seaLevel) {
                            wetColumns++;
                        } else {
                            BlockPos above = new BlockPos(wx, target, wz);
                            helper.assertTrue(level.getFluidState(above).isEmpty(),
                                    "Raised approaches must stay above the waterline at " + above);
                            landColumns++;
                        }
                    }
                }
            }
        }
        helper.assertTrue(wetColumns > 0 && landColumns > 0, "Check both submerged slopes and raised land");
        ArienteWorld.setup.getLogger().info("CITY OCEAN APPROACHES PASSED: {} submerged and {} dry columns around {}",
                wetColumns, landColumns, city.getCenter());
        helper.succeed();
    }

    private static City findOceanCity(ServerLevel level) {
        var layout = new ArienteBiomeLayout(level.getSeed());
        for (int x = 4104; x < 8200; x += 16) {
            for (int z = 4104; z < 8200; z += 16) {
                if (!CityTools.isCityCenter(level.getSeed(), x, z)
                        || layout.get(x * 4 + 2, z * 4 + 2) != BiomeProfile.OCEAN) {
                    continue;
                }
                City city = CityTools.getCity(level.getSeed(), new ChunkPos(x, z));
                if ((city.getPlan().getName().equals("city0_0") || city.getPlan().getName().equals("city0_1"))
                        && CityTools.getCityHeight(level, city) == level.getChunkSource().getGenerator().getSeaLevel() + 1) {
                    return city;
                }
            }
        }
        return null;
    }
}
