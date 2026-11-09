package mcjty.arienteworld.client;

import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.GaussianSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = ArienteWorld.MODID, value = Dist.CLIENT)
public class SwampFogRenderer {

    private static final ResourceKey<Biome> SWAMP = ResourceKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(ArienteWorld.MODID, "ariente_swamp"));
    private static final Vec3 SWAMP_WEIGHT = new Vec3(1, 0, 0);

    @SubscribeEvent
    public static void renderFog(ViewportEvent.RenderFog event) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(DimensionRegister.DIMENSION)
                || event.getType() != FogType.NONE) {
            return;
        }
        // Use the same spatial blend as vanilla's biome fog colors, with no abrupt border change.
        double[] weights = new double[2];
        GaussianSampler.sample(event.getCamera().position().scale(.25), (x, y, z) ->
                level.getBiomeManager().getNoiseBiomeAtQuart(x, y, z).is(SWAMP), (weight, swamp) -> {
                    weights[0] += weight;
                    if (swamp) weights[1] += weight;
                });
        float strength = weights[0] == 0 ? 0 : (float) (weights[1] / weights[0]);
        if (strength <= 0) {
            return;
        }
        // Keep water, lava, blindness and other already closer fog intact.
        event.setNearPlaneDistance(Mth.lerp(strength, event.getNearPlaneDistance(), Math.min(event.getNearPlaneDistance(), 12)));
        event.setFarPlaneDistance(Mth.lerp(strength, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), 96)));
    }
}
