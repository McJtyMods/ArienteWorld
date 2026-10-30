package mcjty.arienteworld;

import mcjty.ariente.api.IAlarmMode;
import mcjty.ariente.api.IArienteMob;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.BuildingPart;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityIndex;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.commands.ModCommands;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.dimension.EditMode;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import org.apache.commons.lang3.tuple.Pair;

public class ForgeEventHandlers {

    @SubscribeEvent
    public void serverLoad(FMLServerStartingEvent event) {
        ModCommands.register(event.getCommandDispatcher());
    }

    // @todo 1.15
//    @SubscribeEvent
//    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
//        if (ConfigSetup.mainConfig.hasChanged()) {
//            ConfigSetup.mainConfig.save();
//        }
//    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.isSpawner()) {
            return;
        }
        if (event.getWorld().getDimension().getType() == DimensionRegister.dimensionType) {
            if (event.getEntity() instanceof IArienteMob) {
                // These can always spawn
                return;
            }
            if (event.getEntity() instanceof MobEntity) {
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
                City city = CityTools.getNearestDungeon(chunkX, chunkZ);
                if (city != null) {
                    CityAISystem cityAISystem = CityAISystem.getCityAISystem(event.getWorld().getWorld());
                    CityAI cityAI = cityAISystem.getCityAI(city.getCenter());
                    if (cityAI != null) {
                        if (!cityAI.isDead(event.getWorld().getWorld())) {
                            event.setResult(Event.Result.DENY);
                        }
                    }
                }
                return;
            }
            if (event.getEntity() instanceof AnimalEntity) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

//    @SubscribeEvent
//    public void onLivingEquipmentChange(LivingEquipmentChangeEvent event) {
//        PowerSuitFeatureCache.checkCacheClean(event.getEntity().getEntityId(), event.getSlot(), event.getFrom(), event.getTo());
//    }

    private void onBlockBreakNormal(BlockEvent.BreakEvent event) {
        World world = event.getWorld().getWorld();
        BlockPos pos = event.getPos();
        TileEntity te = world.getTileEntity(pos);
        if (world.getDimension().getType() == DimensionRegister.dimensionType) {
            PlayerEntity player = event.getPlayer();
            if (te instanceof IAlarmMode) {
                boolean highAlert = ((IAlarmMode) te).isHighAlert();
                alertCity(world, pos, player, highAlert);
            } else if (world.getBlockState(pos).getBlock() == ArienteStuff.reinforcedMarble) {
                alertCity(world, pos, player, true);
            }
        }
    }

    private void alertCity(World world, BlockPos pos, PlayerEntity player, boolean highAlert) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        if (CityTools.isDungeonChunk(cx, cz)) {
            City city = CityTools.getNearestDungeon(cx, cz);
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
        World world = event.getWorld().getWorld();
        if (!world.isRemote) {
            if (world.getDimension().getType() == DimensionRegister.dimensionType) {
                BlockPos pos = event.getPos();
                City city = CityTools.getNearestDungeon(world, pos);
                if (city != null) {
                    Pair<BuildingPart, Integer> pair = EditMode.getCurrentPart(city, event.getWorld().getWorld(), event.getPos());
                    if (pair != null) {
                        EditMode.breakBlock(city, event.getWorld().getWorld(), pair.getKey(), pos.getX() & 0xf, pos.getY() - pair.getRight(), pos.getZ() & 0xf);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!EditMode.editMode) {
            return;
        }
        World world = event.getWorld().getWorld();
        if (!world.isRemote) {
            if (world.getDimension().getType() == DimensionRegister.dimensionType) {
                BlockPos pos = event.getPos();
                City city = CityTools.getNearestDungeon(world, pos);
                if (city != null) {
                    Pair<BuildingPart, Integer> pair = EditMode.getCurrentPart(city, event.getWorld().getWorld(), event.getPos());
                    if (pair != null) {
                        EditMode.copyBlock(city, event.getWorld().getWorld(), event.getPlacedBlock(), pair.getKey(), pos.getX() & 0xf, pos.getY() - pair.getRight(), pos.getZ() & 0xf);
                    }
                }
            }
        }
    }
}
