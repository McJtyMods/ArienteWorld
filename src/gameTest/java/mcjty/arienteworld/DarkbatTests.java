package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.config.SpawningConfiguration;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.entities.darkbat.DarkbatEntity;
import mcjty.arienteworld.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class DarkbatTests {

    @GameTest(template = "empty")
    public static void darkbatRetainsSizeAndDamageAfterReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var type = Registration.ENTITY_DARKBAT.get();
        DarkbatEntity bat = type.create(level);
        bat.finalizeSpawn(level, level.getCurrentDifficultyAt(bat.blockPosition()), MobSpawnType.NATURAL, null, null);
        bat.setPhantomSize(10);
        CompoundTag saved = new CompoundTag();
        bat.saveWithoutId(saved);
        DarkbatEntity restored = type.create(level);
        restored.load(saved);
        helper.assertTrue(Math.abs(restored.getBbWidth() - EntityType.PHANTOM.getWidth() * 1.6F) < .0001F
                        && Math.abs(restored.getBbHeight() - EntityType.PHANTOM.getHeight() * 1.6F) < .0001F,
                "Darkbat collision dimensions must be 60% larger than a phantom after finalization and reload");
        helper.assertTrue(restored.getMaxHealth() == 40 && restored.getAttributeValue(Attributes.ATTACK_DAMAGE) == 12,
                "Vanilla phantom size handling must not overwrite darkbat combat attributes");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void darkbatTargetsAndSweepsWithoutProvocation(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        level.setDayTime(1000);
        BlockPos pos = helper.absolutePos(new BlockPos(8, 220, 8));
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "darkbat-target")) {

            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }
        };
        player.setGameMode(GameType.SURVIVAL);
        // FakePlayer skips ServerPlayer.tick(), so its initial login immunity never expires by itself.
        var loginImmunity = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        loginImmunity.setAccessible(true);
        loginImmunity.setInt(player, 0);
        player.setNoGravity(true);
        player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        level.addFreshEntity(player);
        DarkbatEntity bat = Registration.ENTITY_DARKBAT.get().create(level);
        bat.moveTo(pos.getX() + 5.5, pos.getY() + 12, pos.getZ() + .5);
        bat.setPersistenceRequired();
        level.addFreshEntity(bat);
        float health = player.getHealth();
        helper.runAfterDelay(10, () -> helper.assertTrue(bat.getTarget() == player,
                "Darkbats must acquire a visible survival player without provocation or insomnia"));
        helper.succeedWhen(() -> {
            helper.assertTrue(player.getHealth() <= health - 12,
                    "A darkbat must fly down and land its 12-damage sweep: health=" + player.getHealth()
                            + ", bat=" + bat.position() + ", player=" + player.position() + ", target=" + bat.getTarget());
            helper.assertTrue(!bat.isOnFire(), "Darkbats must survive daylight without burning");
            bat.discard();
            player.discard();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void darkbatSpawningIsRareAndRestrictedToTheWilderness(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        var type = Registration.ENTITY_DARKBAT.get();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        for (String biome : new String[]{"plains", "hills", "ocean", "forest", "rough", "city", "swamp"}) {
            var spawns = biomes.get(new ResourceLocation(ArienteWorld.MODID, "ariente_" + biome))
                    .getMobSettings().getMobs(MobCategory.MONSTER).unwrap();
            helper.assertTrue(spawns.stream().anyMatch(entry -> entry.type == type && entry.minCount == 1 && entry.maxCount == 1)
                            == !biome.equals("city"), "Only outdoor biomes must contain solo darkbat spawns: " + biome);
        }
        BlockPos outside = null;
        BlockPos city = null;
        BlockPos dungeon = null;
        var source = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        for (int x = 24000; x < 24256 && (outside == null || city == null || dungeon == null); x++) {
            for (int z = 24000; z < 24256 && (outside == null || city == null || dungeon == null); z++) {
                BlockPos pos = new BlockPos(x * 16 + 8, 300, z * 16 + 8);
                boolean cityBiome = source.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
                        QuartPos.fromBlock(pos.getZ()), sampler).unwrapKey().orElseThrow().location().getPath().equals("ariente_city");
                if (cityBiome && city == null) {
                    city = pos;
                } else if (!cityBiome && CityTools.isDungeonChunk(level.getSeed(), x, z) && dungeon == null) {
                    dungeon = pos;
                } else if (!cityBiome && !CityTools.isDungeonChunk(level.getSeed(), x, z) && outside == null) {
                    outside = pos;
                }
            }
        }
        helper.assertTrue(outside != null && city != null && dungeon != null, "Find outdoor, city and dungeon positions");
        RandomSource random = RandomSource.create(42);
        int accepted = 0;
        level.setBlock(outside.below(), Blocks.GLOWSTONE.defaultBlockState(), 3);
        for (int i = 0; i < 4096; i++) {
            if (SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, outside, random)) {
                accepted++;
            }
            helper.assertTrue(!SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, city, random)
                            && !SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, dungeon, random)
                            && !SpawnPlacements.checkSpawnRules(type, helper.getLevel(), MobSpawnType.NATURAL, outside, random),
                    "Darkbats must reject cities, dungeon footprints and other dimensions");
        }
        helper.assertTrue(accepted > 20 && accepted < 110, "Outdoor darkbats must pass about one in 64 spawn attempts: " + accepted);
        DarkbatEntity bat = type.create(level);
        bat.moveTo(outside.getX() + .5, outside.getY(), outside.getZ() + .5);
        helper.assertTrue(ForgeEventFactory.checkSpawnPosition(bat, level, MobSpawnType.NATURAL),
                "A bright outdoor position must pass the entity spawn check as well");
        level.setBlock(outside.above(3), Blocks.STONE.defaultBlockState(), 3);
        BlockPos roofed = outside;
        helper.succeedWhen(() -> {
            for (int i = 0; i < 256; i++) {
                helper.assertTrue(!SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, roofed, random),
                        "Darkbats must not spawn under a roof once skylight updates");
            }
        });
    }

    @GameTest(template = "empty", batch = "darkbat_cap")
    public static void naturalDarkbatsStopAtTheirOwnConfigurableCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 160, 8));
        FakePlayer player = spawnPlayer(level, origin);
        List<DarkbatEntity> bats = new ArrayList<>();
        DarkbatEntity candidate = Registration.ENTITY_DARKBAT.get().create(level);
        candidate.moveTo(origin.getX() + 40.5, origin.getY(), origin.getZ() + .5);
        int previousLimit = SpawningConfiguration.DARKBAT_MAX_PER_PLAYER.get();
        try {
            SpawningConfiguration.DARKBAT_MAX_PER_PLAYER.set(3);
            for (int i = 0; i < 3; i++) {
                helper.assertTrue(ForgeEventFactory.checkSpawnPosition(candidate, level, MobSpawnType.NATURAL),
                        "Natural Darkbats must spawn until their own cap is reached: " + i);
                // Include mobs far above the player; aerial circling must not evade the cap.
                bats.add(spawnBat(level, origin.offset(8 + i * 4, 80 + i * 4, 0)));
            }
            for (int attempt = 0; attempt < 1000; attempt++) {
                helper.assertTrue(!ForgeEventFactory.checkSpawnPosition(candidate, level, MobSpawnType.NATURAL),
                        "Repeated natural attempts must remain blocked at three, well below the hostile mob cap");
            }
            helper.assertTrue(ForgeEventFactory.checkSpawnPosition(candidate, level, MobSpawnType.SPAWNER)
                            && ForgeEventFactory.checkSpawnPosition(candidate, level, MobSpawnType.SPAWN_EGG),
                    "The natural limit must leave deliberate spawns available");
            bats.get(0).setHealth(0);
            helper.assertTrue(ForgeEventFactory.checkSpawnPosition(candidate, level, MobSpawnType.NATURAL),
                    "A dead Darkbat must immediately free space, even before its entity is removed");
            SpawningConfiguration.DARKBAT_MAX_PER_PLAYER.set(2);
            helper.assertTrue(!ForgeEventFactory.checkSpawnPosition(candidate, level, MobSpawnType.NATURAL),
                    "Lowering the configured cap must take effect immediately");
            SpawningConfiguration.DARKBAT_MAX_PER_PLAYER.set(4);
            helper.assertTrue(ForgeEventFactory.checkSpawnPosition(candidate, level, MobSpawnType.NATURAL),
                    "Increasing the configured cap must allow additional spawns");
            bats.forEach(DarkbatEntity::discard);
            SpawningConfiguration.DARKBAT_MAX_PER_PLAYER.set(0);
            helper.assertTrue(!ForgeEventFactory.checkSpawnPosition(candidate, level, MobSpawnType.NATURAL),
                    "A zero cap must disable natural spawns even in an empty area");
        } finally {
            SpawningConfiguration.DARKBAT_MAX_PER_PLAYER.set(previousLimit);
            bats.forEach(DarkbatEntity::discard);
            candidate.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "darkbat_cap")
    public static void overlappingPlayersShareTheDarkbatCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 160, 8));
        FakePlayer first = spawnPlayer(level, origin);
        FakePlayer second = spawnPlayer(level, origin.east(72));
        List<DarkbatEntity> bats = new ArrayList<>();
        DarkbatEntity candidate = Registration.ENTITY_DARKBAT.get().create(level);
        int previousLimit = SpawningConfiguration.DARKBAT_MAX_PER_PLAYER.get();
        try {
            SpawningConfiguration.DARKBAT_MAX_PER_PLAYER.set(3);
            for (int i = 0; i < 3; i++) {
                bats.add(spawnBat(level, origin.offset(-80 - i * 4, 0, 0)));
            }
            candidate.moveTo(origin.getX() + 36.5, origin.getY(), origin.getZ() + .5);
            helper.assertTrue(!ForgeEventFactory.checkSpawnPosition(candidate, level, MobSpawnType.NATURAL),
                    "A spawn near two players must be blocked if either player's area is full");
            candidate.moveTo(origin.getX() + 152.5, origin.getY(), origin.getZ() + .5);
            helper.assertTrue(ForgeEventFactory.checkSpawnPosition(candidate, level, MobSpawnType.NATURAL),
                    "Players away from a full area must still have their own Darkbat allowance");
            first.setGameMode(GameType.SPECTATOR);
            candidate.moveTo(origin.getX() + 36.5, origin.getY(), origin.getZ() + .5);
            helper.assertTrue(ForgeEventFactory.checkSpawnPosition(candidate, level, MobSpawnType.NATURAL),
                    "A spectator's full area must not block spawns for a nearby survival player");
        } finally {
            SpawningConfiguration.DARKBAT_MAX_PER_PLAYER.set(previousLimit);
            bats.forEach(DarkbatEntity::discard);
            candidate.discard();
            first.discard();
            second.discard();
        }
        helper.succeed();
    }

    private static FakePlayer spawnPlayer(ServerLevel level, BlockPos pos) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "darkbat-cap-player"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        level.getChunkAt(pos);
        level.addFreshEntity(player);
        return player;
    }

    private static DarkbatEntity spawnBat(ServerLevel level, BlockPos pos) {
        DarkbatEntity bat = Registration.ENTITY_DARKBAT.get().create(level);
        bat.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        level.getChunkAt(pos);
        level.addFreshEntity(bat);
        return bat;
    }
}
