package mcjty.arienteworld.client;

import mcjty.ariente.client.textures.SurfaceQuad;
import mcjty.ariente.client.textures.SurfaceModel;
import mcjty.ariente.client.textures.SurfaceTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import mcjty.ariente.blocks.generators.NegariteTankTile;
import mcjty.ariente.blocks.generators.PosiriteTankTile;
import net.minecraft.world.item.ItemDisplayContext;
import mcjty.arienteworld.ArienteWorld;
import mcjty.ariente.api.EnumFacingUpDown;
import mcjty.ariente.setup.Registration;
import mcjty.ariente.cables.CableColor;
import mcjty.ariente.cables.ConnectorType;
import mcjty.ariente.cables.GenericCableBlock;
import mcjty.arienteworld.cities.LegacyBlockStates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

import static mcjty.ariente.blocks.BlockProperties.LOWER;
import static mcjty.ariente.blocks.BlockProperties.UPPER;

/** Runs only when the optional gameTest source set is explicitly included in a development client. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class TextureModelChecks {

    private static volatile boolean checked;
    private static int runs;

    private static void check(boolean success, String message) {
        if (!success) {
            throw new IllegalStateException("Ariente client regression: " + message);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = new BlockPos(90020, 240, 90020);
        if (checked || event.phase != TickEvent.Phase.END || mc.level == null || !mc.level.hasChunkAt(pos)
                || !mc.level.getBlockState(pos).is(LegacyBlockStates.read("ariente:marbletech@type=variation1").getBlock())) {
            return;
        }
        var model = mc.getBlockRenderer().getBlockModel(mc.level.getBlockState(pos));
        var data = model.getModelData(mc.level, pos, mc.level.getBlockState(pos), ModelData.EMPTY);
        var quads = model.getQuads(mc.level.getBlockState(pos), Direction.UP, RandomSource.create(1), data, RenderType.solid());
        check(quads.size() == 4, "An interior connected face must split into four quarters: " + quads.size() + " model=" + model.getClass().getSimpleName() + " sprite=" + quads.get(0).getSprite().contents().name() + " rule=" + SurfaceTextures.get(quads.get(0).getSprite().contents().name()));
        check(quads.stream().allMatch(quad -> quad.getSprite().contents().name().getPath().endsWith("-ctm")), "Interior faces must use the supplemental atlas");
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                check(SurfaceQuad.tile(x, y, false, false, false) == -1, "Disconnected quarters must retain the base image");
                check(SurfaceQuad.tile(x, y, true, true, true) == x + y * 4, "Connected corners must select the interior atlas tiles");
                check(SurfaceQuad.tile(x, y, true, true, false) == x + 2 + (y + 2) * 4, "Missing diagonals must retain inner corners");
            }
        }
        pos = new BlockPos(90020, 242, 90027);
        var state = mc.level.getBlockState(pos);
        model = mc.getBlockRenderer().getBlockModel(state);
        data = model.getModelData(mc.level, pos, state, ModelData.EMPTY);
        var glow = model.getQuads(state, Direction.NORTH, RandomSource.create(1), data, RenderType.cutout());
        check(glow.size() == 4, "Connected glow overlay must be present in cutout: " + glow.size());
        for (var quad : glow) {
            check(quad.getSprite().contents().name().getPath().endsWith("-ctm_glow"), "Glow overlays must use their own connected atlas");
            check(!quad.isShade() && !quad.hasAmbientOcclusion(), "Emissive pixels must not be darkened by shading/AO");
            int[] vertices = quad.getVertices();
            for (int i = 0; i < 4; i++) {
                check((vertices[i * 8 + 6] & 0xffff) >= 14 * 16, "Glow vertices must retain the configured minimum block light");
            }
        }
        check(glow.get(0).getSprite().contents().getUniqueFrames().count() > 1, "Connected glow atlas must preserve vanilla animation frames");
        check(model.applyTransform(ItemDisplayContext.GUI, new PoseStack(), false) instanceof SurfaceModel, "Item transforms must retain native glow rendering");
        var opaque = model.getQuads(state, Direction.NORTH, RandomSource.create(1), data, RenderType.solid());
        check(opaque.stream().noneMatch(quad -> quad.getSprite().contents().name().getPath().endsWith("glow")), "Glow must not be drawn twice in the solid pass");
        pos = new BlockPos(90025, 241, 90022);
        state = mc.level.getBlockState(pos);
        model = mc.getBlockRenderer().getBlockModel(state);
        var ramp = model.getQuads(state, null, RandomSource.create(1), ModelData.EMPTY, RenderType.solid());
        float top = 0;
        for (var quad : ramp) {
            check(!quad.getSprite().contents().name().getPath().equals("missingno"), "Ramp faces must resolve their texture variables");
            for (int i = 0; i < 4; i++) {
                top = Math.max(top, Float.intBitsToFloat(quad.getVertices()[i * 8 + 1]));
            }
        }
        check(top > .99f, "Ramp geometry must reach the full block height: " + top);
        checkSlopes(mc);
        checkCableModels(mc);
        checkTankModels(mc);
        SuitAndPanelRenderingChecks.check(mc);
        checked = true;
        runs++;
        if (runs == 1) {
            mc.reloadResourcePacks().thenRun(() -> checked = false);
        }
        ArienteWorld.setup.getLogger().info("CLIENT RENDERING CHECKS PASSED (run {}, {}): connected/glowing textures, ramps, slopes, cables/connectors, sorted tank glass, seamless billboard beams, mounted panel shapes, AI core, armor and lasers", runs, mc.options.graphicsMode().get());
    }

    private static void checkTankModels(Minecraft mc) {
        check(mc.level.getBlockEntity(new BlockPos(90023, 244, 90020)) instanceof NegariteTankTile negative && negative.isWorking(),
                "The client must receive the active generator state through the full Negarite tank stack");
        check(mc.level.getBlockEntity(new BlockPos(90022, 244, 90020)) instanceof PosiriteTankTile positive && positive.isWorking(),
                "The client must receive the active generator state through the full Posirite tank stack");
        for (var block : new BlockState[]{Registration.NEGARITE_TANK.get().defaultBlockState(), Registration.POSIRITE_TANK.get().defaultBlockState()}) {
            for (boolean upper : new boolean[]{false, true}) {
                for (boolean lower : new boolean[]{false, true}) {
                    var state = block.setValue(UPPER, upper).setValue(LOWER, lower);
                    var model = mc.getBlockRenderer().getBlockModel(state);
                    var layers = model.getRenderTypes(state, RandomSource.create(1), ModelData.EMPTY);
                    check(layers.asList().equals(List.of(RenderType.solid())), "Only opaque tank frames belong in the chunk renderer");
                    var frame = model.getQuads(state, null, RandomSource.create(1), ModelData.EMPTY, RenderType.solid());
                    check(!frame.isEmpty() && frame.stream().allMatch(quad -> quad.getSprite().contents().name().getPath().equals("block/marble/blackmarble")),
                            "Only the frame belongs in the solid tank pass");
                    int expectedFrameElements = 4 + (upper ? 0 : 4) + (lower ? 0 : 4);
                    check(frame.size() == expectedFrameElements * 6, "Stacked tanks must omit internal horizontal frame rims");
                }
            }
            ItemStack stack = new ItemStack(block.getBlock());
            var itemModel = mc.getItemRenderer().getModel(stack, null, null, 0);
            check(itemModel.getRenderPasses(stack, false).size() == 2 && itemModel.getRenderPasses(stack, true).size() == 2,
                    "Tank items must retain separate frame/glass passes in both graphics modes");
        }
        TankRenderingChecks.check(mc);
    }

    private static void checkCableModels(Minecraft mc) {
        for (CableColor color : CableColor.values()) {
            for (Direction direction : Direction.values()) {
                var property = switch (direction) {
                    case NORTH -> GenericCableBlock.NORTH;
                    case SOUTH -> GenericCableBlock.SOUTH;
                    case WEST -> GenericCableBlock.WEST;
                    case EAST -> GenericCableBlock.EAST;
                    case UP -> GenericCableBlock.UP;
                    case DOWN -> GenericCableBlock.DOWN;
                };
                var state = Registration.CONNECTOR.get().defaultBlockState().setValue(GenericCableBlock.COLOR, color)
                        .setValue(property, ConnectorType.BLOCK);
                var model = mc.getBlockRenderer().getBlockModel(state);
                check(model.getRenderTypes(state, RandomSource.create(1), ModelData.EMPTY).contains(RenderType.cutout()),
                        "Connector housing must participate in the cutout pass");
                var quads = model.getQuads(state, null, RandomSource.create(1), ModelData.EMPTY, RenderType.cutout());
                ResourceLocation plateTexture = new ResourceLocation("ariente", "block/cables/" + color.getSerializedName() + "/connector");
                check(quads.stream().anyMatch(quad -> quad.getSprite().contents().name().getPath().equals("block/cables/connector_side")),
                        "Connector must have an outer housing: " + color + "/" + direction);
                var plates = quads.stream().filter(quad -> quad.getSprite().contents().name().equals(plateTexture)).toList();
                check(!plates.isEmpty(), "Connector must render the plate touching its machine: " + color + "/" + direction);
                for (var quad : plates) {
                    for (int vertex = 0; vertex < 4; vertex++) {
                        int axis = direction.getAxis().ordinal();
                        float coordinate = Float.intBitsToFloat(quad.getVertices()[vertex * 8 + axis]);
                        float expected = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? .9f : .1f;
                        check(Math.abs(coordinate - expected) < .001, "Machine plate must lie on the connected face: " + direction);
                    }
                }
                for (var quad : quads) {
                    var sprite = quad.getSprite();
                    check(sprite == mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(sprite.contents().name()),
                            "Cable textures must belong to the current atlas after resource reload");
                }
                for (var facade : new BlockState[]{Blocks.STONE.defaultBlockState(), Blocks.GLASS.defaultBlockState()}) {
                    var data = ModelData.builder().with(GenericCableBlock.FACADEID, facade).build();
                    var facadeModel = mc.getBlockRenderer().getBlockModel(facade);
                    check(model.getRenderTypes(state, RandomSource.create(1), data).asList().equals(facadeModel.getRenderTypes(facade, RandomSource.create(1), ModelData.EMPTY).asList()),
                            "A covered connector must use its facade's render layers");
                    check(!model.getQuads(state, Direction.UP, RandomSource.create(1), data, null).isEmpty(), "Connector facade must retain its geometry");
                }
            }
            var cable = Registration.NETCABLE.get().defaultBlockState().setValue(GenericCableBlock.COLOR, color)
                    .setValue(GenericCableBlock.UP, ConnectorType.CABLE).setValue(GenericCableBlock.DOWN, ConnectorType.CABLE);
            var model = mc.getBlockRenderer().getBlockModel(cable);
            check(!model.getQuads(cable, null, RandomSource.create(1), ModelData.EMPTY, RenderType.cutout()).isEmpty(), "Straight cable shells must still render");
        }
    }

    private static void checkSlopes(Minecraft mc) {
        for (EnumFacingUpDown facing : EnumFacingUpDown.values()) {
            var state = Registration.SLOPE.get().defaultBlockState().setValue(EnumFacingUpDown.FACING, facing);
            var model = mc.getBlockRenderer().getBlockModel(state);
            boolean upsideDown = facing.getSerializedName().endsWith("_up");
            Direction direction = Direction.byName(facing.getSerializedName().replace("_up", ""));
            boolean foundSlope = false;
            // The diagonal must be uncullable even when a solid ceiling or wall touches it.
            for (var quad : model.getQuads(state, null, RandomSource.create(1), ModelData.EMPTY, RenderType.solid())) {
                check(!quad.getSprite().contents().name().getPath().equals("missingno"), "Slope texture must resolve: " + facing);
                boolean diagonal = true;
                float minY = 1;
                float maxY = 0;
                for (int i = 0; i < 4; i++) {
                    float x = Float.intBitsToFloat(quad.getVertices()[i * 8]);
                    float y = Float.intBitsToFloat(quad.getVertices()[i * 8 + 1]);
                    float z = Float.intBitsToFloat(quad.getVertices()[i * 8 + 2]);
                    float along = switch (direction) {
                        case NORTH -> z;
                        case SOUTH -> 1 - z;
                        case WEST -> x;
                        case EAST -> 1 - x;
                        default -> throw new IllegalArgumentException();
                    };
                    diagonal &= Math.abs(y - (upsideDown ? 1 - along : along)) < .001;
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
                foundSlope |= diagonal && minY < .001 && maxY > .999;
            }
            check(foundSlope, "Slope must expose the original diagonal in its expected orientation: " + facing);
        }
    }
}
