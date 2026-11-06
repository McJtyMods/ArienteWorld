package mcjty.arienteworld.client;

import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class SwampFogRenderer {

    private static final ResourceKey<Biome> SWAMP = ResourceKey.create(Registries.BIOME,
            new ResourceLocation(ArienteWorld.MODID, "ariente_swamp"));
    private static final Vec3 SWAMP_WEIGHT = new Vec3(1, 0, 0);

    @SubscribeEvent
    public static void renderFog(ViewportEvent.RenderFog event) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(DimensionRegister.DIMENSION)
                || event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        // Use the same spatial blend as vanilla's biome fog colors, with no abrupt border change.
        Vec3 sample = event.getCamera().getPosition().subtract(2, 2, 2).scale(.25);
        float strength = (float) CubicSampler.gaussianSampleVec3(sample, (x, y, z) ->
                level.getBiomeManager().getNoiseBiomeAtQuart(x, y, z).is(SWAMP) ? SWAMP_WEIGHT : Vec3.ZERO).x;
        if (strength <= 0) {
            return;
        }
        // Keep water, lava, blindness and other already closer fog intact.
        event.setNearPlaneDistance(Mth.lerp(strength, event.getNearPlaneDistance(), Math.min(event.getNearPlaneDistance(), 12)));
        event.setFarPlaneDistance(Mth.lerp(strength, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), 96)));
        event.setCanceled(true);
    }
}
