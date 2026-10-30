package mcjty.arienteworld.biomes;

import mcjty.arienteworld.ArienteStuff;
import mcjty.arienteworld.biomes.features.WorldGenArienteFlowers;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import java.util.Random;

import static net.minecraft.block.Blocks.BEDROCK;

public abstract class AbstractArienteBiome extends Biome implements IArienteBiome {

    private static final BlockState GLOW_TRUNK = Registration.GLOWLOG.get().getDefaultState();
    private static final BlockState GLOW_LEAVES = Registration.GLOWLEAVES.get().getDefaultState();
    private static final BlockState BLUE_TRUNK = Registration.BLUELOG.get().getDefaultState();
    private static final BlockState DARK_LEAVES = Registration.DARKLEAVES.get().getDefaultState();
    private static final BlockState BLUE_LEAVES = Registration.BLUELEAVES.get().getDefaultState();

    // @todo 1.15
//    private static final WorldGenArienteSmallTree GLASS_TREE = new WorldGenArienteSmallTree(GLOW_TRUNK, GLOW_LEAVES);
//    private static final WorldGenArienteSmallTree DARK_TREE = new WorldGenArienteSmallTree(BLUE_TRUNK, DARK_LEAVES);
//    private static final WorldGenArienteSmallTree SMALL_TREE = new WorldGenArienteSmallTree(BLUE_TRUNK, BLUE_LEAVES);
//    private static final WorldGenArienteSmallTree DARK_TREE_V2 = new WorldGenArienteSmallTree(6, BLUE_TRUNK, DARK_LEAVES, false);
//    private static final WorldGenArienteSmallTree SMALL_TREE_V2 = new WorldGenArienteSmallTree(6, BLUE_TRUNK, BLUE_LEAVES, false);
//    private static final WorldGenHugeTrees HUGE_TREE = new WorldGenBlueTree(10, 20, BLUE_TRUNK, BLUE_LEAVES);
//    private static final WorldGenArianteVariantTree VARIANT_TREE = new WorldGenArianteVariantTree();

    private WorldGenArienteFlowers flowers = new WorldGenArienteFlowers();

    public AbstractArienteBiome(Builder properties) {
        super(properties);
//        this.spawnableCreatureList.clear();
//        this.spawnableCaveCreatureList.clear();
//        this.spawnableWaterCreatureList.clear();
    }


    // @todo 1.15
//    @Override
//    public void genTerrainBlocks(World worldIn, Random rand, ChunkPrimer chunkPrimerIn, int x, int z, double noiseVal) {
//        generateBlocks(worldIn, rand, chunkPrimerIn, x, z, noiseVal);
//    }

    // @todo 1.15
//    @Override
//    public WorldGenAbstractTree getRandomTreeFeature(Random rand) {
//        switch (rand.nextInt(10)) {
//            case 0:
//                return HUGE_TREE;
//            case 1:
//            case 2:
//                return GLASS_TREE;
//            case 3:
//            case 4:
//                return SMALL_TREE;
//            case 5:
//                return SMALL_TREE_V2;
//            case 6:
//                return DARK_TREE_V2;
//            case 7:
//                return VARIANT_TREE;
//            default:
//                return DARK_TREE;
//        }
//    }


    @Override
    public int getGrassColor(double x, double z) {
        return 0x448877;
//        return 0x3377aa;
    }

    @Override
    public boolean isCityBiome() {
        return false;
    }

    // @todo 1.15
//    @Override
//    public int getWaterColorMultiplier() {
//        return 0x55ff88;
//    }
//
//    @Override
//    public int getFoliageColorAtPos(BlockPos pos) {
//        return 0x224477;
//    }

    protected final void generateBlocks(World world, Random rand, ChunkPrimer primer, int x, int z, double noiseVal) {
        int i = world.getSeaLevel();
        BlockState topState = null; // @todo 1.15 this.topBlock;
        BlockState fillerState = null; // @todo 1.15 this.fillerBlock;
        int j = -1;
        int k = (int) (noiseVal / 3.0D + 3.0D + rand.nextDouble() * 0.25D);
        int dz = x & 15;    // swapped?
        int dx = z & 15;
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int y = 255; y >= 0; --y) {
            if (y <= rand.nextInt(5)) {
                primer.setBlockState(pos.setPos(dx, y, dz), BEDROCK.getDefaultState(), false);
            } else {
                BlockState state = primer.getBlockState(pos.setPos(dx, y, dz));

                if (state.getMaterial() == Material.AIR) {
                    j = -1;
                } else if (state.getBlock() == ArienteStuff.marble) {
                    if (j == -1) {
                        if (k <= 0) {
                            topState = Blocks.AIR.getDefaultState();
                            fillerState = ArienteStuff.marble.getDefaultState();
                        } else if (y >= i - 4 && y <= i + 1) {
                            // @todo 1.15
//                            topState = this.topBlock;
//                            fillerState = this.fillerBlock;
                        }

                        if (y < i && (topState == null || topState.getMaterial() == Material.AIR)) {
                            if (this.getTemperature(pos.setPos(x, y, z)) < 0.15F) {
                                topState = Blocks.ICE.getDefaultState();
                            } else {
                                topState = Blocks.WATER.getDefaultState();
                            }
                        }

                        j = k;

                        if (y >= i - 1) {
                            primer.setBlockState(pos.setPos(dx, y, dz), topState, false);
                        } else if (y < i - 7 - k) {
                            topState = Blocks.AIR.getDefaultState();
                            fillerState = ArienteStuff.marble.getDefaultState();
                            primer.setBlockState(pos.setPos(dx, y, dz), Blocks.GRAVEL.getDefaultState(), false);
                        } else {
                            primer.setBlockState(pos.setPos(dx, y, dz), fillerState, false);
                        }
                    } else if (j > 0) {
                        --j;
                        primer.setBlockState(pos.setPos(dx, y, dz), fillerState, false);

                        if (j == 0 && fillerState.getBlock() == Blocks.SAND && k > 1) {
                            j = rand.nextInt(4) + Math.max(0, y - 63);
//                            fillerState = fillerState.get(SandBlock.VARIANT) == BlockSand.EnumType.RED_SAND ? RED_SANDSTONE : SANDSTONE;
                            fillerState = Blocks.SANDSTONE.getDefaultState();
                        }
                    }
                }
            }
        }
    }

    // @todo 1.15
//    protected void generateFlowers(World worldIn, Random random, int cnt) {
//        for (int i = 0; i < cnt; ++i) {
//            int x = random.nextInt(16) + 8;
//            int z = random.nextInt(16) + 8;
//            int y = worldIn.getHeight(decorator.chunkPos.add(x, 0, z)).getY() + 32;
//
//            if (y > 0) {
//                int y2 = random.nextInt(y);
//                BlockPos blockpos1 = decorator.chunkPos.add(x, y2, z);
//                flowers.generate(worldIn, random, blockpos1);
//            }
//        }
//    }
}
