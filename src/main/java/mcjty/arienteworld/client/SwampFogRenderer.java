package mcjty.arienteworld.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.client.Camera;
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

public class SwampFogRenderer {

    private static final ResourceKey<Biome> SWAMP = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ArienteWorld.MODID, "ariente_swamp"));
    private static final Vec3 SWAMP_WEIGHT = new Vec3(1, 0, 0);

    public static void renderFog(Camera camera, FogRenderer.FogMode mode) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(DimensionRegister.DIMENSION)
                || camera.getFluidInCamera() != FogType.NONE || mode != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        // Use the same spatial blend as vanilla's biome fog colors, with no abrupt border change.
        Vec3 sample = camera.getPosition().subtract(2, 2, 2).scale(.25);
        float strength = (float) CubicSampler.gaussianSampleVec3(sample, (x, y, z) ->
                level.getBiomeManager().getNoiseBiomeAtQuart(x, y, z).is(SWAMP) ? SWAMP_WEIGHT : Vec3.ZERO).x;
        if (strength <= 0) {
            return;
        }
        // Keep water, lava, blindness and other already closer fog intact.
        RenderSystem.setShaderFogStart(Mth.lerp(strength, RenderSystem.getShaderFogStart(), Math.min(RenderSystem.getShaderFogStart(), 12)));
        RenderSystem.setShaderFogEnd(Mth.lerp(strength, RenderSystem.getShaderFogEnd(), Math.min(RenderSystem.getShaderFogEnd(), 96)));

    }
}
