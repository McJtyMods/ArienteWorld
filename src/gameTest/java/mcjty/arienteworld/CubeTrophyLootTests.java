package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.blocks.storagecube.StorageCubeTile;
import mcjty.ariente.blocks.storagecube.StorageCubeTransfer;
import mcjty.ariente.items.KeyCardItem;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.AssetRegistries;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.cities.StructureLoot;
import mcjty.arienteworld.dimension.CityFeature;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.entities.boss.CoreGuardianEntity;
import mcjty.arienteworld.entities.boss.CoreProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class CubeTrophyLootTests {

    private static final ResourceLocation TABLE = new ResourceLocation("arienteworld", "chests/conquered_overworld");

    @GameTest(template = "empty")
    public static void trophyTableGuaranteesDistinctOverworldRewards(GameTestHelper helper) {
        for (int seed = 0; seed < 32; seed++) {
            BlockPos pos = helper.absolutePos(new BlockPos(seed, 3, 0));
            StorageCubeTile first = cache(helper.getLevel(), pos);
            StorageCubeTile repeated = cache(helper.getLevel(), pos);
            StructureLoot.setLootTable(first, TABLE);
            StructureLoot.setLootTable(repeated, TABLE);
            helper.assertTrue(StructureLoot.fill(first) && StructureLoot.fill(repeated), "The configured vanilla loot table must be used");
            verifyRewards(helper, first);
            for (int slot = 0; slot < 30; slot++) {
                helper.assertTrue(ItemStack.matches(first.getItems().getStackInSlot(slot), repeated.getItems().getStackInSlot(slot)),
                        "The same world and cache position must reproduce its rewards");
            }
        }
        StorageCubeTile ordinary = cache(helper.getLevel(), helper.absolutePos(new BlockPos(0, 3, 1)));
        helper.assertTrue(!StructureLoot.fill(ordinary), "Ordinary Holo Storage Cubes must retain their usual city loot path");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void authoredCoreRoomPlacesOneLowestTierTrophyCache(GameTestHelper helper) {
        BlockPos origin = new BlockPos(190000, 160, 190000);
        var part = AssetRegistries.PARTS.get("c5_C1");
        var palette = AssetRegistries.PALETTES.get("city5_1");
        CityFeature.placePart(helper.getLevel(), origin, part, palette);
        BlockPos pos = origin.offset(7, 27, 8);
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(Registration.HOLO_STORAGE_1.get()), "The central cache must use the lowest holo tier");
        helper.assertTrue(helper.getLevel().getBlockState(pos.east()).is(Registration.AICORE.get())
                        && helper.getLevel().getBlockState(pos.north()).is(Registration.FORCEFIELD.get()),
                "The trophy cache must directly touch the authored core and forcefield generator");
        int caches = 0;
        for (int x = 0; x < part.getXSize(); x++) {
            for (int z = 0; z < part.getZSize(); z++) {
                for (int y = 0; y < part.getSliceCount(); y++) {
                    if (helper.getLevel().getBlockState(origin.offset(x, y, z)).is(Registration.HOLO_STORAGE_1.get())) {
                        caches++;
                    }
                }
            }
        }
        helper.assertTrue(caches == 1, "Each central room must contain exactly one trophy cache");
        StorageCubeTile storage = (StorageCubeTile) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(StructureLoot.fill(storage), "Structure placement must preserve the authored loot-table metadata");
        verifyRewards(helper, storage);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void generatedTrophiesKeepSecurityAndNeverRefill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City city = null;
        for (int x = 24808; x < 40808; x += 16) {
            City candidate = CityTools.getCity(level.getSeed(), new ChunkPos(x, -52008));
            if (candidate.getPlan().getName().equals("city5_1") && CityTools.isCityCenter(level.getSeed(), x, -52008)) {
                city = candidate;
                break;
            }
        }
        helper.assertTrue(city != null, "Find a generated cube for the real boss reward check");
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = city.getCenter().x - 4; x <= city.getCenter().x + 4; x++) {
            for (int z = city.getCenter().z - 4; z <= city.getCenter().z + 4; z++) {
                chunks.add(new ChunkPos(x, z));
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
            }
        }
        BlockPos core = new BlockPos(city.getCenter().getMiddleBlockX(), CityTools.getCityHeight(level, city) + 27,
                city.getCenter().getMiddleBlockZ());
        CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(city.getCenter());
        ai.tick(level.getBlockEntity(core));
        helper.runAfterDelay(2, () -> {
            try {
                StorageCubeTile storage = (StorageCubeTile) level.getBlockEntity(core.west());
                helper.assertTrue(storage != null && storage.isLocked() && ai.getStorageKeyId().equals(storage.getKeyId()),
                        "Generated trophies must use the cube's storage key");
                verifyRewards(helper, storage);
                helper.assertTrue(CoreProtection.isProtected(level, core), "The living guardian must still protect the storage-access reward");
                storage.acceptKeyCard(new ItemStack(Registration.KEY_CARD.get()));
                helper.assertTrue(storage.isLocked(), "An unrelated card must not unlock the trophies");
                for (Direction side : Direction.values()) {
                    var automation = storage.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(IllegalStateException::new);
                    helper.assertTrue(automation.extractItem(1, 1, true).isEmpty()
                                    && automation.extractItem(1, 1, false).isEmpty(),
                            "Hoppers must not bypass the trophy cache's storage key");
                }
                storage.getItems().setStackInSlot(0, ItemStack.EMPTY);
                CompoundTag saved = storage.saveWithoutMetadata();
                StorageCubeTile restored = cache(level, storage.getBlockPos());
                restored.load(saved);
                restored.setup(ai, level, false);
                ai.fillLoot(restored);
                helper.assertTrue(restored.isLocked() && restored.getItems().getStackInSlot(0).isEmpty()
                                && rewards(restored).size() == 23,
                        "Saving, reloading and loot setup must neither refill nor overwrite the trophy cache");
                CompoundTag state = new CompoundTag();
                ai.writeToNBT(state);
                CoreGuardianEntity guardian = (CoreGuardianEntity) level.getEntity(state.getUUID("guardianId"));
                helper.assertTrue(guardian != null, "The generated encounter must include its guardian");
                guardian.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                helper.assertTrue(!CoreProtection.isProtected(level, core), "Victory must allow access to the AI core");
                level.destroyBlock(core, false);
                ItemStack card = level.getEntitiesOfClass(ItemEntity.class, new AABB(core).inflate(4)).stream()
                        .map(ItemEntity::getItem).filter(stack -> KeyCardItem.hasSecurityTag(stack, ai.getStorageKeyId()))
                        .findFirst().orElseThrow(() -> new AssertionError("The defeated core must drop the trophy storage key"));
                storage.acceptKeyCard(card);
                helper.assertTrue(!storage.isLocked(), "The defeated core's key must unlock the conquered Overworld loot");
                helper.assertTrue(storage.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new)
                                .extractItem(12, 1, true).is(Items.TOTEM_OF_UNDYING),
                        "Unlocked trophy caches must allow ordinary automated extraction");
                FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "cube-trophy-test"));
                player.setPos(Vec3.atCenterOf(storage.getBlockPos()));
                helper.assertTrue(StorageCubeTransfer.apply(storage, player, 1, -1, 64, true,
                                storage.getItems().getStackInSlot(1).copy(), ItemStack.EMPTY),
                        "Unlocked trophy stacks must support the real Holo Storage transfer action");
                helper.assertTrue(player.getInventory().contains(new ItemStack(Items.NETHER_STAR)), "The player must be able to withdraw the actual rewards");
                player.discard();
                helper.succeed();
            } finally {
                for (ChunkPos chunk : chunks) {
                    level.setChunkForced(chunk.x, chunk.z, false);
                }
            }
        });
    }

    private static void verifyRewards(GameTestHelper helper, StorageCubeTile storage) {
        List<ItemStack> loot = rewards(storage);
        helper.assertTrue(storage.getItems().getSlots() == 30 && loot.size() == 24, "The lowest holo tier must hold 24 guaranteed reward stacks");
        int books = 0;
        int picks = 0;
        int regeneration = 0;
        for (int index = 0; index < loot.size(); index++) {
            ItemStack stack = loot.get(index);
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft"), "Trophies must contain Overworld spoils rather than ordinary Ariente loot");
            helper.assertTrue(stack.getCount() <= stack.getMaxStackSize(), "Every reward must fit its legal stack size");
            for (int previous = 0; previous < index; previous++) {
                helper.assertTrue(!ItemStack.isSameItemSameTags(stack, loot.get(previous)), "Rewards must include distinct items, enchantments or potion variants");
            }
            if (stack.is(Items.ENCHANTED_BOOK)) {
                books++;
                helper.assertTrue(!EnchantmentHelper.getEnchantments(stack).isEmpty(), "Books must have real stored enchantments");
            } else if (stack.is(Items.DIAMOND_PICKAXE)) {
                picks++;
                helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, stack) == 5
                                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, stack) == 3,
                        "Diamond pickaxes must carry powerful real enchantments");
            }
            if (PotionUtils.getPotion(stack) == Potions.STRONG_REGENERATION || PotionUtils.getPotion(stack) == Potions.LONG_REGENERATION) {
                regeneration++;
            }
        }
        helper.assertTrue(books == 4 && picks == 2 && regeneration == 3, "The cache must guarantee books, pickaxes and regeneration potions");
        helper.assertTrue(loot.stream().anyMatch(stack -> stack.is(Items.NETHERITE_INGOT))
                        && loot.stream().anyMatch(stack -> stack.is(Items.NETHER_STAR))
                        && loot.stream().anyMatch(stack -> stack.is(Items.GHAST_TEAR))
                        && loot.stream().anyMatch(stack -> stack.is(Items.TOTEM_OF_UNDYING)),
                "Each cache must include netherite, Nether Stars, Ghast Tears and a Totem of Undying");
    }

    private static List<ItemStack> rewards(StorageCubeTile storage) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < storage.getItems().getSlots(); slot++) {
            ItemStack stack = storage.getItems().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    private static StorageCubeTile cache(ServerLevel level, BlockPos pos) {
        StorageCubeTile storage = new StorageCubeTile(pos, Registration.HOLO_STORAGE_1.get().defaultBlockState());
        storage.setLevel(level);
        return storage;
    }
}
