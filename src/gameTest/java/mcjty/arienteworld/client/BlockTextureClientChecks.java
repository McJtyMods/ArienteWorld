package mcjty.arienteworld.client;

import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/** Opt-in atlas/model checks at the title screen, without opening a player world. */
@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class BlockTextureClientChecks {

    private static volatile boolean reloading;
    private static int runs;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("arienteworld.blockTextureCheck") || event.phase != TickEvent.Phase.END || reloading) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null
                || !(mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)) {
            return;
        }
        var atlas = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        var textures = mc.getResourceManager().listResources("textures/block", id ->
                id.getNamespace().equals(ArienteWorld.MODID) && id.getPath().endsWith(".png"));
        require(!textures.isEmpty(), "Block textures must be discoverable");
        for (var texture : textures.keySet()) {
            String path = texture.getPath();
            var sprite = new ResourceLocation(texture.getNamespace(), path.substring(9, path.length() - 4));
            require(atlas.apply(sprite).contents().name().equals(sprite), "Texture must be stitched into the block atlas: " + sprite);
        }
        for (var entry : Registration.BLOCKS.getEntries()) {
            for (var state : entry.get().getStateDefinition().getPossibleStates()) {
                var model = mc.getBlockRenderer().getBlockModel(state);
                require(model != mc.getModelManager().getMissingModel(), "Block model must load: " + entry.getId());
                for (int side = -1; side < Direction.values().length; side++) {
                    for (var quad : model.getQuads(state, side < 0 ? null : Direction.values()[side], RandomSource.create(1))) {
                        require(!quad.getSprite().contents().name().getPath().equals("missingno"),
                                "Model quads must have textures: " + entry.getId());
                    }
                }
            }
            var item = mc.getItemRenderer().getModel(new ItemStack(entry.get()), null, null, 0);
            require(item != mc.getModelManager().getMissingModel()
                            && !item.getParticleIcon().contents().name().getPath().equals("missingno"),
                    "Inventory model must have a texture: " + entry.getId());
        }
        var upper = Registration.TALLDARKGRASS.get().defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
        var model = mc.getBlockRenderer().getBlockModel(upper);
        Set<ResourceLocation> tops = new HashSet<>();
        for (int seed = 0; seed < 256; seed++) {
            for (var quad : model.getQuads(upper, null, RandomSource.create(seed))) {
                tops.add(quad.getSprite().contents().name());
            }
        }
        require(tops.equals(Set.of(texture("talldarkgrass_top1"), texture("talldarkgrass_top2"), texture("talldarkgrass_top3"))),
                "All three tall-grass tops must bake with their own sprites: " + tops);
        ArienteWorld.setup.getLogger().info("BLOCK TEXTURE CLIENT CHECK PASSED (run {}): {} sprites, all block/item models, three tall-grass tops",
                ++runs, textures.size());
        if (runs == 1) {
            reloading = true;
            mc.reloadResourcePacks().thenRun(() -> reloading = false);
        } else {
            mc.stop();
        }
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(ArienteWorld.MODID, "block/plants/" + name);
    }

    private static void require(boolean success, String message) {
        if (!success) {
            throw new IllegalStateException("ArienteWorld texture check: " + message);
        }
    }
}
