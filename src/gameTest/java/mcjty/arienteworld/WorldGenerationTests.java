package mcjty.arienteworld;

import mcjty.arienteworld.commands.ModCommands;
import mcjty.ariente.Ariente;
import mcjty.ariente.entities.levitator.FluxLevitatorEntity;
import mcjty.arienteworld.dimension.ArienteChunkGenerator;
import mcjty.arienteworld.dimension.ArienteLandscapeCity;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.PortalGenerator;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.cities.Transform;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import mcjty.arienteworld.cities.LegacyBlockStates;
import mcjty.arienteworld.dimension.terrain.ArienteBiomeLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static mcjty.ariente.setup.Registration.FLUX_BEAM;
import static mcjty.ariente.setup.Registration.STORAGE;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class WorldGenerationTests {

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void landscapeCitiesAndTransport(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        helper.assertTrue(level.getChunkSource().getGenerator() instanceof ArienteChunkGenerator,
                "Use a fresh test world to verify the restored terrain generator");
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        var facing = STORAGE.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST);
        // A mirror must transform machine facing as well as its position in an authored part.
        helper.assertTrue(Transform.MIRROR_X.transform(facing).getValue(BlockStateProperties.FACING) == Direction.WEST,
                "Mirrored landscape equipment must face the matching direction");
        int height = generator.getBaseHeight(1234, -5678, Heightmap.Types.WORLD_SURFACE_WG, level, random);
        // Evict the first chunk from the bounded terrain cache before querying it again.
        for (int i = 0; i < 140; i++) {
            generator.getBaseHeight(-3312 + i * 16, 778, Heightmap.Types.WORLD_SURFACE_WG, level, random);
        }
        helper.assertTrue(height == generator.getBaseHeight(1234, -5678, Heightmap.Types.WORLD_SURFACE_WG, level, random),
                "Terrain queries must not depend on generation order");
        ArienteBiomeLayout layout = new ArienteBiomeLayout(level.getSeed());
        ArienteBiomeLayout other = new ArienteBiomeLayout(level.getSeed() + 1);
        other.get(500, -600);
        helper.assertTrue(layout.get(500, -600) == new ArienteBiomeLayout(level.getSeed()).get(500, -600),
                "Biome layouts must not leak between worlds");
        for (boolean underground : new boolean[]{false, true}) {
            BlockPos target = ModCommands.findDistrict(level, BlockPos.ZERO, underground);
            helper.assertTrue(target != null, "Original terrain must contain " + (underground ? "underground" : "surface") + " districts");
            ChunkPos cp = new ChunkPos(target);
            helper.assertTrue(!PortalGenerator.isPortalChunk(cp.x, cp.z)
                            && !CityTools.isDungeonChunk(level.getSeed(), cp.x, cp.z),
                    "District search must skip plots replaced by portals or complexes");
            for (int z = 1; z >= -1; z--) {
                for (int x = 1; x >= -1; x--) {
                    level.getChunk(cp.x + x, cp.z + z);
                }
            }
            helper.assertTrue(ArienteLandscapeCity.isLandscapeCityChunk(level.getSeed(), cp.x, cp.z), "District must use city biome layout");
            helper.assertTrue(level.getBlockState(new BlockPos(target.getX(), 63, target.getZ())).is(
                    LegacyBlockStates.read("ariente:marble@color=black").getBlock()), "District must have its black-marble foundation at " + target
                    + ": " + level.getBlockState(new BlockPos(target.getX(), 63, target.getZ())));
            if (underground) {
                helper.assertTrue(level.getBlockState(new BlockPos(target.getX(), 73, target.getZ())).isAir(),
                        "Underground district must carve room for streets beneath terrain");
            }
            ArienteWorld.setup.getLogger().info("Verified {} district at {}", underground ? "underground" : "surface", target);
        }
        // A straight run between stations must join across both chunk boundaries.
        int cx = 8;
        int cz = 2;
        for (int z = cz - 1; z <= cz + 1; z++) {
            level.getChunk(cx, z);
        }
        for (int z = (cz - 1) * 16; z < (cz + 2) * 16; z++) {
            BlockPos beam = new BlockPos(cx * 16 + 8, 32, z);
            helper.assertTrue(level.getBlockState(beam).is(FLUX_BEAM.get()), "Intercity beam must cross chunk borders: " + beam);
            helper.assertTrue(level.getBlockState(beam.above()).isAir(), "Tunnel must have clearance above its beam");
        }
        helper.assertTrue(level.getBlockState(new BlockPos(128, 0, 32)).is(Blocks.BEDROCK), "Original terrain must have its bedrock floor");
        FluxLevitatorEntity vehicle = (FluxLevitatorEntity) Ariente.arienteSystem.createFluxLevitatorEntity(level, cx * 16 + 8.5, 32.0625, 31.5);
        Pig passenger = EntityType.PIG.create(level);
        passenger.startRiding(vehicle, true);
        vehicle.changeSpeed(10);
        for (int tick = 0; tick < 30; tick++) {
            vehicle.tick();
            vehicle.positionRider(passenger);
        }
        helper.assertTrue(vehicle.getZ() > 33 && vehicle.getY() >= 31 && vehicle.getY() <= 34,
                "Levitator must travel along the generated beam across a chunk border: " + vehicle.position());
        helper.assertTrue(passenger.distanceToSqr(vehicle) < 10, "A passenger must follow the moving levitator without recursion");
        passenger.discard();
        vehicle.discard();
        helper.succeed();
    }
}
