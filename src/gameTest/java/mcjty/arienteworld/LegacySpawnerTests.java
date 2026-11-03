package mcjty.arienteworld;

import mcjty.arienteworld.entities.soldier.MasterSoldierEntity;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class LegacySpawnerTests {

    @GameTest(template = "empty")
    public static void oldSoldierSpawnersLoadWithWorkingDisplayEntities(GameTestHelper helper) {
        for (String id : new String[]{"soldier", "master_soldier"}) {
            BlockPos pos = helper.absolutePos(new BlockPos(1, 2, id.equals("soldier") ? 1 : 3));
            helper.getLevel().setBlockAndUpdate(pos, Blocks.SPAWNER.defaultBlockState());
            SpawnerBlockEntity spawner = (SpawnerBlockEntity) helper.getLevel().getBlockEntity(pos);
            CompoundTag saved = spawner.saveWithFullMetadata();
            CompoundTag entity = new CompoundTag();
            entity.putString("id", "ariente:" + id);
            entity.putString("CustomName", "\"Tutorial defender\"");
            CompoundTag spawn = new CompoundTag();
            spawn.put("entity", entity);
            saved.put("SpawnData", spawn);
            saved.putShort("Delay", (short) 127);
            CompoundTag potential = new CompoundTag();
            potential.putInt("weight", 7);
            potential.put("data", spawn.copy());
            ListTag potentials = new ListTag();
            potentials.add(potential);
            saved.put("SpawnPotentials", potentials);
            ListTag blockEntities = new ListTag();
            blockEntities.add(saved);
            CompoundTag chunkData = new CompoundTag();
            chunkData.put("block_entities", blockEntities);
            var chunk = helper.getLevel().getChunkAt(pos);
            MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, chunkData, ChunkStatus.ChunkType.LEVELCHUNK));
            helper.assertTrue(spawn.getCompound("entity").getString("id").equals("arienteworld:" + id)
                            && potential.getCompound("data").getCompound("entity").getString("id").equals("arienteworld:" + id),
                    "Chunk loading must repair both the active spawn and weighted alternatives");
            helper.assertTrue(saved.getShort("Delay") == 127 && potential.getInt("weight") == 7
                            && spawn.getCompound("entity").getString("CustomName").equals("\"Tutorial defender\""),
                    "Migration must retain spawn settings and custom entity data");
            spawner.load(saved);
            var display = spawner.getSpawner().getOrCreateDisplayEntity(helper.getLevel(), helper.getLevel().random, pos);
            helper.assertTrue(display instanceof SoldierEntity
                            && (id.equals("master_soldier") == (display instanceof MasterSoldierEntity)),
                    "The spawner must create and cache the correct display entity instead of retrying an invalid ID");
            helper.assertTrue(display == spawner.getSpawner().getOrCreateDisplayEntity(helper.getLevel(), helper.getLevel().random, pos),
                    "Repeated renders must reuse the valid display entity");
            CompoundTag migrated = chunkData.copy();
            MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, chunkData, ChunkStatus.ChunkType.LEVELCHUNK));
            helper.assertTrue(chunkData.equals(migrated), "Migrating a saved chunk again must leave it unchanged");
        }
        helper.succeed();
    }
}
