package mcjty.arienteworld.dimension;

public class ArienteWorldProvider {} /* @todo 1.15 extends WorldProvider {

    @Override
    @Nonnull
    public DimensionType getDimensionType() {
        return DimensionRegister.dimensionType;
    }

    @Override
    @Nonnull
    public String getSaveFolder() {
        return "ARIENTE";
    }

    @Override
    @Nonnull
    public IChunkGenerator createChunkGenerator() {
        return new ArienteChunkGenerator(world);
    }

    @Override
    protected void init() {
        super.init();
        this.biomeProvider = new ArienteBiomeProvider(world);
    }



    @Nullable
    @Override
    public float[] calcSunriseSunsetColors(float celestialAngle, float partialTicks) {
        float[] floats = super.calcSunriseSunsetColors(celestialAngle, partialTicks);
//        if (floats != null) {
//            for (float f : floats) {
//                System.out.print(f + ",");
//            }
//            System.out.println("");
//
//        }
//        float[] floats = { 0.8f, 0.3f, 0.2f, 0.9f };
        return floats;
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks) {
        float angle = super.calculateCelestialAngle(worldTime, partialTicks);
//        System.out.println("angle = " + angle);
//        return 0.28f;
        return angle;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Vec3d getSkyColor(net.minecraft.entity.Entity cameraEntity, float partialTicks) {
        Vec3d v = world.getSkyColorBody(cameraEntity, partialTicks);
        return new Vec3d(Math.max(1.0f, v.x * 2.5f), v.y, Math.max(1.0f, v.z * 1.2f));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Vec3d getFogColor(float p_76562_1_, float p_76562_2_) {
        Vec3d v = super.getFogColor(p_76562_1_, p_76562_2_);
        return new Vec3d(v.x * .8f, v.y, v.z * .8f);
    }

    @Nullable
    @Override
    public MusicTicker.MusicType getMusicType() {
        return ClientProxy.arienteMusic;
    }

    @Override
    public void calculateInitialWeather() {
        world.thunderingStrength = 0.0F;
        world.rainingStrength = 0.0F;
        world.getWorldInfo().setThundering(false);
        world.getWorldInfo().setRaining(false);
    }

    @Override
    public void updateWeather() {
        WorldInfo worldInfo = world.getWorldInfo();
        if (!world.isRemote) {
            world.thunderingStrength = 0.0f;
            world.rainingStrength = 0.0F;
            worldInfo.setThundering(false);
            worldInfo.setRaining(false);
        }
        worldInfo.setCleanWeatherTime(0);
        worldInfo.setThunderTime(0);
        world.updateWeatherBody();
    }

    @Override
    public boolean canDoRainSnowIce(Chunk chunk) {
        return false;
    }

    @Nullable
    @Override
    public IRenderHandler getCloudRenderer() {
        return new IRenderHandler() {
            @Override
            public void render(float partialTicks, WorldClient world, Minecraft mc) {
                // Clouds are always disabled here
            }
        };
    }

    @Nullable
    @Override
    public IRenderHandler getSkyRenderer() {
        if (super.getSkyRenderer() == null) {
            setSkyRenderer(new ArienteSkyRenderer());
        }
        return super.getSkyRenderer();
    }
}
*/