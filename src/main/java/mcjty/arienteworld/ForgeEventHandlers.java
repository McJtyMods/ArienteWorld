package mcjty.arienteworld;

import mcjty.ariente.api.IAlarmMode;
import mcjty.ariente.api.IArienteMob;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityIndex;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.config.ConfigSetup;
import mcjty.arienteworld.config.WorldgenConfiguration;
import mcjty.arienteworld.dimension.ArienteChunkGenerator;
import mcjty.arienteworld.dimension.EditMode;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.tuple.Pair;

public class ForgeEventHandlers {

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (ConfigSetup.mainConfig.hasChanged()) {
            ConfigSetup.mainConfig.save();
        }
    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.isSpawner()) {
            return;
        }
        if (event.getWorld().provider.getDimension() == WorldgenConfiguration.DIMENSION_ID.get()) {
            if (event.getEntity() instanceof IArienteMob) {
                // These can always spawn
                return;
            }
            if (event.getEntity() instanceof EntityMob) {
                BlockPos pos = event.getEntity().getPosition();
                int chunkX = pos.getX()>>4;
                int chunkZ = pos.getZ()>>4;
                // Check if we are in a city dungeon
                CityIndex index = CityTools.getDungeonIndex(chunkX, chunkZ);
                if (index == null) {
                    // No so we can spawn
                    return;
                }
                // Check if this city is still alive
                ArienteChunkGenerator generator = (ArienteChunkGenerator) (((WorldServer) event.getWorld()).getChunkProvider().chunkGenerator);
                City city = CityTools.getNearestDungeon(generator, chunkX, chunkZ);
                if (city != null) {
                    CityAISystem cityAISystem = CityAISystem.getCityAISystem(event.getWorld());
                    CityAI cityAI = cityAISystem.getCityAI(city.getCenter());
                    if (cityAI != null) {
                        if (!cityAI.isDead(event.getWorld())) {
                            event.setResult(Event.Result.DENY);
                        }
                    }
                }
                return;
            }
            if (event.getEntity() instanceof IAnimals) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

//    @SubscribeEvent
//    public void onLivingEquipmentChange(LivingEquipmentChangeEvent event) {
//        PowerSuitFeatureCache.checkCacheClean(event.getEntity().getEntityId(), event.getSlot(), event.getFrom(), event.getTo());
//    }

    private void onBlockBreakNormal(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        TileEntity te = world.getTileEntity(pos);
        if (world.provider.getDimension() == WorldgenConfiguration.DIMENSION_ID.get()) {
            EntityPlayer player = event.getPlayer();
            if (te instanceof IAlarmMode) {
                boolean highAlert = ((IAlarmMode) te).isHighAlert();
                alertCity(world, pos, player, highAlert);
            } else if (world.getBlockState(pos).getBlock() == ArienteStuff.reinforcedMarble) {
                alertCity(world, pos, player, true);
            }
        }
    }

    private void alertCity(World world, BlockPos pos, EntityPlayer player, boolean highAlert) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        ArienteChunkGenerator generator = (ArienteChunkGenerator) (((WorldServer) world).getChunkProvider().chunkGenerator);
        if (CityTools.isDungeonChunk(cx, cz)) {
            City city = CityTools.getNearestDungeon(generator, cx, cz);
            CityAISystem cityAISystem = CityAISystem.getCityAISystem(world);
            CityAI cityAI = cityAISystem.getCityAI(city.getCenter());
            if (cityAI != null) {
                if (highAlert) {
                    cityAI.highAlertMode(player);
                } else {
                    cityAI.alertCity(player);
                }
                cityAISystem.save();
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!EditMode.editMode) {
            onBlockBreakNormal(event);
            return;
        }
        World world = event.getWorld();
        if (!world.isRemote) {
            if (world.provider.getDimension() == WorldgenConfiguration.DIMENSION_ID.get()) {
                BlockPos pos = event.getPos();
                City city = CityTools.getNearestDungeon(world, pos);
                if (city != null) {
                    Pair<BuildingPart, Integer> pair = EditMode.getCurrentPart(city, event.getWorld(), event.getPos());
                    if (pair != null) {
                        EditMode.breakBlock(city, event.getWorld(), pair.getKey(), pos.getX() & 0xf, pos.getY() - pair.getRight(), pos.getZ() & 0xf);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (!EditMode.editMode) {
            return;
        }
        World world = event.getWorld();
        if (!world.isRemote) {
            if (world.provider.getDimension() == WorldgenConfiguration.DIMENSION_ID.get()) {
                BlockPos pos = event.getPos();
                City city = CityTools.getNearestDungeon(world, pos);
                if (city != null) {
                    Pair<BuildingPart, Integer> pair = EditMode.getCurrentPart(city, event.getWorld(), event.getPos());
                    if (pair != null) {
                        EditMode.copyBlock(city, event.getWorld(), event.getPlacedBlock(), pair.getKey(), pos.getX() & 0xf, pos.getY() - pair.getRight(), pos.getZ() & 0xf);
                    }
                }
            }
        }
    }
}
