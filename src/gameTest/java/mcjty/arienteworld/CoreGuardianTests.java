package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.items.modules.ModuleSupport;
import mcjty.ariente.setup.Registration;
import mcjty.arienteworld.ai.CityAI;
import mcjty.arienteworld.ai.CityAISystem;
import mcjty.arienteworld.cities.City;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.dimension.DimensionRegister;
import mcjty.arienteworld.entities.boss.CoreGuardianEntity;
import mcjty.arienteworld.entities.boss.CoreProtection;
import mcjty.arienteworld.entities.drone.DroneEntity;
import mcjty.arienteworld.entities.drone.DroneMoveControl;
import mcjty.arienteworld.entities.drone.SentinelDroneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

import static mcjty.arienteworld.setup.Registration.ENTITY_CORE_GUARDIAN;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class CoreGuardianTests {

    @GameTest(template = "empty")
    public static void lightningRewardsWorkingPowerArmorAndDefeatsEnchantedNetherite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        CoreGuardianEntity guardian = ENTITY_CORE_GUARDIAN.get().create(level);
        guardian.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 3, 1))));
        try {
            for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
                level.getServer().setDifficulty(difficulty, true);
                for (boolean enchanted : List.of(false, true)) {
                    for (int equipment = 0; equipment < 4; equipment++) {
                        Player player = target(level, equipment, enchanted);
                        player.setPos(guardian.position().add(4, 0, 0));
                        try {
                            boolean hit = guardian.strike(player);
                            helper.assertTrue(hit, "An unobstructed lightning strike must land");
                            if (equipment == 0 || equipment == 3) {
                                helper.assertTrue(player.isDeadOrDying(), "Netherite and unpowered suits must not ground the overload: " + difficulty);
                            } else {
                                float expected = equipment == 1 ? 2 : 8;
                                helper.assertTrue(Math.abs(player.getHealth() - expected) < 0.01,
                                        "Powered armor must survive, with six hearts lost when its field is active: " + difficulty + "/" + player.getHealth());
                            }
                        } finally {
                            player.discard();
                        }
                    }
                }
            }
            Player shielded = target(level, 0, true);
            try {
                shielded.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                shielded.setPos(guardian.position().add(4, 0, 0));
                shielded.setYRot(90);
                helper.assertTrue(shielded.isBlocking(), "The shield fixture must actually be blocking");
                helper.assertTrue(guardian.strike(shielded) && shielded.isDeadOrDying(),
                        "A vanilla shield must not let netherite trivialize the overload");
            } finally {
                shielded.discard();
            }
            Player player = target(level, 2, false);
            player.setPos(guardian.position().add(4, 0, 0));
            BlockPos wall = BlockPos.containing(guardian.getEyePosition().lerp(player.getEyePosition(), 0.5));
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            try {
                guardian.getSensing().tick();
                helper.assertTrue(!guardian.strike(player) && player.getHealth() == 20, "Lightning must never pass through cover");
            } finally {
                level.removeBlock(wall, false);
                player.discard();
            }
        } finally {
            level.getServer().setDifficulty(previous, true);
            guardian.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void cubeGuardianLivesOnceAndLocksCoreUntilKilled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City city = findCube(level, 18008);
        BlockPos core = new BlockPos(city.getCenter().getMiddleBlockX(), CityTools.getCityHeight(level, city) + 27,
                city.getCenter().getMiddleBlockZ());
        level.setChunkForced(city.getCenter().x, city.getCenter().z, true);
        level.getChunkAt(core);
        helper.assertTrue(level.getBlockState(core).is(Registration.AICORE.get()), "The fixture must use the generated central core");
        CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(city.getCenter());
        ai.tick(level.getBlockEntity(core));
        CompoundTag saved = new CompoundTag();
        ai.writeToNBT(saved);
        helper.assertTrue(saved.hasUUID("guardianId"), "A loaded core must spawn its guardian before the entire cube is loaded");
        UUID id = saved.getUUID("guardianId");
        helper.runAfterDelay(20, () -> {
            verifyEncounter(helper, level, core, core.above(8), ai, id);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void undergroundCoresShareGuardianInWesternHall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City city = findCity(level, 22008, "city3_1");
        ChunkPos center = city.getCenter();
        BlockPos origin = new BlockPos(center.getMinBlockX() - 32, 4, center.getMinBlockZ() - 32);
        BlockPos upperCore = origin.offset(56, 30, 7);
        BlockPos lowerCore = origin.offset(4, 18, 59);
        BlockPos home = origin.offset(8, 14, 40);
        level.setChunkForced(home.getX() >> 4, home.getZ() >> 4, true);
        for (BlockPos pos : List.of(upperCore, lowerCore, home)) {
            level.getChunkAt(pos);
        }
        helper.assertTrue(level.getBlockState(upperCore).is(Registration.AICORE.get())
                        && level.getBlockState(lowerCore).is(Registration.AICORE.get()),
                "Both authored underground cores must be present");
        CityAI ai = CityAISystem.getCityAISystem(level).getCityAI(center);
        ai.ensureGuardian(upperCore);
        ai.ensureGuardian(lowerCore);
        CompoundTag saved = new CompoundTag();
        ai.writeToNBT(saved);
        helper.assertTrue(saved.hasUUID("guardianId"), "Either loaded core must start the hall encounter");
        UUID id = saved.getUUID("guardianId");
        helper.runAfterDelay(20, () -> {
            try {
                verifyUndergroundEncounter(helper, level, upperCore, lowerCore, home, ai, id);
                helper.succeed();
            } finally {
                level.setChunkForced(home.getX() >> 4, home.getZ() >> 4, false);
            }
        });
    }

    private static void verifyUndergroundEncounter(GameTestHelper helper, ServerLevel level, BlockPos upperCore,
                                                  BlockPos lowerCore, BlockPos home, CityAI ai, UUID id) {
        CoreGuardianEntity guardian = (CoreGuardianEntity) level.getEntity(id);
        helper.assertTrue(guardian != null, "The spawned guardian must be registered when its chunk starts ticking");
        helper.assertTrue(home.equals(guardian.getHome()) && level.noCollision(guardian),
                "The guardian must fit in the long western room beside the lower core");
        helper.assertTrue(level.getEntitiesOfClass(CoreGuardianEntity.class, new AABB(home).inflate(64)).size() == 1,
                "Two cores must never create two guardians");
        helper.assertTrue(CoreProtection.isProtected(level, upperCore) && CoreProtection.isProtected(level, lowerCore),
                "Both cores must share the living guardian's protection");
        verifyEncounter(helper, level, lowerCore, home, ai, id);
        ai.ensureGuardian(upperCore);
        helper.assertTrue(!CoreProtection.isProtected(level, upperCore), "Defeat must also unlock the upper core permanently");
    }

    private static void verifyEncounter(GameTestHelper helper, ServerLevel level, BlockPos core, BlockPos home, CityAI ai, UUID id) {
        CompoundTag saved = new CompoundTag();
        ai.writeToNBT(saved);
        CoreGuardianEntity guardian = (CoreGuardianEntity) level.getEntity(id);
        helper.assertTrue(guardian != null && guardian.getHome().equals(home) && guardian.getMaxHealth() == 800,
                "Guardian must begin at its dungeon's home and be substantially tougher than a dragon: " + guardian);
        guardian.setHealth(643);
        CompoundTag entitySave = new CompoundTag();
        guardian.saveWithoutId(entitySave);
        CoreGuardianEntity restoredEntity = ENTITY_CORE_GUARDIAN.get().create(level);
        restoredEntity.load(entitySave);
        helper.assertTrue(restoredEntity.getHome().equals(guardian.getHome()) && restoredEntity.getHealth() == 643,
                "Entity reload must preserve its home and remaining health");
        restoredEntity.discard();
        CityAI restoredAI = new CityAI(level, ai.getCenter());
        restoredAI.readFromNBT(saved);
        restoredAI.ensureGuardian(core);
        helper.assertTrue(level.getEntitiesOfClass(CoreGuardianEntity.class, new AABB(home).inflate(32)).size() == 1,
                "Reload must never duplicate an already spawned guardian");
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "guardian-core-test"));
        BlockEvent.BreakEvent mining = new BlockEvent.BreakEvent(level, core, level.getBlockState(core), player);
        MinecraftForge.EVENT_BUS.post(mining);
        helper.assertTrue(mining.isCanceled() && CoreProtection.isProtected(level, core), "The living boss must prevent core mining");
        Explosion explosion = new Explosion(level, null, core.getX(), core.getY(), core.getZ(), 4, false, Explosion.BlockInteraction.DESTROY);
        explosion.getToBlow().add(core);
        MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, explosion, List.of()));
        helper.assertTrue(!explosion.getToBlow().contains(core), "Explosions must not bypass the boss gate");
        LivingDestroyBlockEvent griefing = new LivingDestroyBlockEvent(guardian, core, level.getBlockState(core));
        MinecraftForge.EVENT_BUS.post(griefing);
        helper.assertTrue(griefing.isCanceled(), "Mob block destruction must not bypass the boss gate");
        guardian.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(!CoreProtection.isProtected(level, core), "A real boss death must unlock the core");
        saved = new CompoundTag();
        ai.writeToNBT(saved);
        restoredAI.readFromNBT(saved);
        restoredAI.ensureGuardian(core);
        helper.assertTrue(!restoredAI.isCoreProtected() && saved.getBoolean("guardianDead"),
                "Death must remain permanent after city state reload");
        BlockEvent.BreakEvent unlocked = new BlockEvent.BreakEvent(level, core, level.getBlockState(core), player);
        MinecraftForge.EVENT_BUS.post(unlocked);
        helper.assertTrue(!unlocked.isCanceled(), "The core must be mineable after victory");
        level.setChunkForced(core.getX() >> 4, core.getZ() >> 4, false);
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void cubeDefendersPatrolAndGuardianReturnsHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(DimensionRegister.DIMENSION);
        City city = findCube(level, 20008);
        ChunkPos center = city.getCenter();
        int floor = CityTools.getCityHeight(level, city);
        for (int x = center.x - 3; x <= center.x + 3; x++) {
            for (int z = center.z - 3; z <= center.z + 3; z++) {
                level.getChunk(x, z);
            }
        }
        Vec3 start = new Vec3(center.getMiddleBlockX() + 0.5, floor + 38, center.getMiddleBlockZ() + 0.5);
        DroneEntity drone = DroneEntity.create(level, center);
        SentinelDroneEntity sentinel = SentinelDroneEntity.create(level, 0, center);
        drone.setPos(start);
        sentinel.setPos(start.add(0, 8, 0));
        SentinelDroneEntity straggler = SentinelDroneEntity.create(level, 1, center);
        straggler.setPos(start.add(16, -24, 16));
        Vec3 stranded = straggler.position();
        helper.assertTrue(level.noCollision(straggler), "The migration fixture must begin in a clear lower room");
        CoreGuardianEntity guardian = ENTITY_CORE_GUARDIAN.get().create(level);
        guardian.bindToCore(center, BlockPos.containing(start).below(8));
        Vec3 home = guardian.position();
        guardian.setPos(home.add(12, 0, 0));
        try {
            for (int tick = 0; tick < 240; tick++) {
                for (Mob mob : List.of(drone, sentinel, straggler, guardian)) {
                    mob.tick();
                    helper.assertTrue(Double.isFinite(mob.getX()) && Double.isFinite(mob.getY()), "Flight must remain finite at its destination");
                }
            }
            helper.assertTrue(drone.distanceToSqr(start) > 4 && sentinel.distanceToSqr(start.add(0, 8, 0)) > 4,
                    "Both defenders must fly patrols without requiring a visible player or initialized city settings");
            helper.assertTrue(straggler.distanceToSqr(stranded) > 4 && straggler.getY() > floor + 31,
                    "An old sentinel stranded in a lower room must rejoin the interior patrol: " + straggler.position());
            helper.assertTrue(guardian.distanceToSqr(home) < 4, "An idle guardian must return to its saved home above the core");
            BlockPos obstruction = BlockPos.containing(drone.position().add(4, 0, 0));
            level.setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
            try {
                helper.assertTrue(!DroneMoveControl.canFlyTo(drone, Vec3.atBottomCenterOf(obstruction)),
                        "Flight paths must reject solid obstacles");
            } finally {
                level.removeBlock(obstruction, false);
            }
        } finally {
            drone.discard();
            sentinel.discard();
            straggler.discard();
            guardian.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void guardianPursuesLastSightingAroundCover(GameTestHelper helper) {
        PursuitArena arena = createPursuitArena(helper, 0);
        CoreGuardianEntity guardian = arena.guardian();
        Player player = arena.player();
        Vec3 home = guardian.position();
        try {
            for (BlockPos pos : BlockPos.betweenClosed(arena.origin().offset(8, 0, -4), arena.origin().offset(8, 8, 4))) {
                arena.level().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
            guardian.getSensing().tick();
            helper.assertTrue(!guardian.getSensing().hasLineOfSight(player), "The wall must initially hide the player");
            guardian.tick();
            helper.assertTrue(guardian.getTarget() == player && guardian.getCharge() == 0,
                    "Breaking sight must interrupt lightning but retain the pursuit target");
            boolean moved = false;
            StringBuilder trace = new StringBuilder();
            for (int tick = 0; tick < 360 && player.getHealth() == 20; tick++) {
                guardian.tick();
                moved |= guardian.distanceToSqr(home) > 9;
                if (tick % 20 == 0) {
                    trace.append(tick).append(": ").append(guardian.position().subtract(home))
                            .append(" sight=").append(guardian.getSensing().hasLineOfSight(player))
                            .append(" charge=").append(guardian.getCharge())
                            .append(" wanted=").append(new Vec3(guardian.getMoveControl().getWantedX(),
                                    guardian.getMoveControl().getWantedY(), guardian.getMoveControl().getWantedZ()).subtract(home))
                            .append(" node=").append(guardian.getNavigation().isDone() ? "done"
                                    : guardian.getNavigation().getPath().getNextEntityPos(guardian).subtract(home))
                            .append("; ");
                }
            }
            helper.assertTrue(moved && player.getHealth() < 20,
                    "The guardian must fly around cover and regain a firing opportunity: " + trace);
        } finally {
            arena.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void guardianAbandonsUnreachablePlayersEvenAfterBriefPeeks(GameTestHelper helper) {
        PursuitArena arena = createPursuitArena(helper, 1);
        CoreGuardianEntity guardian = arena.guardian();
        Player player = arena.player();
        Vec3 home = guardian.position();
        try {
            setPursuitCover(arena, true);
            for (int tick = 0; tick < 350; tick++) {
                guardian.tick();
            }
            helper.assertTrue(guardian.getTarget() == player && guardian.getCharge() == 0 && player.getHealth() == 20,
                    "An unreachable player must remain remembered for most of the twenty-second pursuit");
            Vec3 coveredPosition = guardian.position();
            setPursuitCover(arena, false);
            guardian.tick();
            helper.assertTrue(guardian.getTarget() == player && guardian.getSensing().hasLineOfSight(player) && player.getHealth() == 20,
                    "A brief sighting must retain the target without instantly firing");
            // Keep the enclosing fixture from closing through the sphere after its one visible tick.
            guardian.setPos(coveredPosition);
            guardian.setDeltaMovement(Vec3.ZERO);
            guardian.getNavigation().stop();
            setPursuitCover(arena, true);
            helper.assertTrue(arena.level().noCollision(guardian), "Reclosing the cover must leave the guardian outside the enclosure");
            for (int tick = 0; tick < 180; tick++) {
                guardian.tick();
            }
            helper.assertTrue(guardian.getTarget() == null && player.getHealth() == 20,
                    "A brief peek without a shot must not restart the pursuit timer");
            for (int tick = 0; tick < 240; tick++) {
                guardian.tick();
            }
            helper.assertTrue(guardian.distanceToSqr(home) < 4,
                    "After abandoning pursuit, the guardian must navigate home around the cover: " + guardian.position());
        } finally {
            arena.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void firingRenewsGuardianPursuit(GameTestHelper helper) {
        PursuitArena arena = createPursuitArena(helper, 2);
        CoreGuardianEntity guardian = arena.guardian();
        Player player = arena.player();
        try {
            setPursuitCover(arena, true);
            for (int tick = 0; tick < 300; tick++) {
                guardian.tick();
            }
            setPursuitCover(arena, false);
            for (int tick = 0; tick < 110 && player.getHealth() == 20; tick++) {
                guardian.tick();
            }
            helper.assertTrue(player.getHealth() < 20, "Restoring sight must let the guardian finish charging and fire");
            setPursuitCover(arena, true);
            float healthAfterShot = player.getHealth();
            for (int tick = 0; tick < 300; tick++) {
                guardian.tick();
            }
            helper.assertTrue(guardian.getTarget() == player && player.getHealth() == healthAfterShot,
                    "Firing must grant a fresh pursuit window, while cover continues to block damage");
            player.discard();
            guardian.tick();
            helper.assertTrue(guardian.getTarget() == null, "Removed or dead players must be forgotten immediately");
        } finally {
            arena.close();
        }
        helper.succeed();
    }

    private static PursuitArena createPursuitArena(GameTestHelper helper, int index) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(1500000 + index * 128, 240, 1500000);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -1, -12), origin.offset(28, 12, 12))) {
            level.setBlockAndUpdate(pos, pos.getY() == origin.getY() - 1
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        CoreGuardianEntity guardian = ENTITY_CORE_GUARDIAN.get().create(level);
        guardian.bindToCore(new ChunkPos(origin), origin.below(8));
        Player player = target(level, 2, false);
        player.setPos(guardian.position().add(16, 0, 0));
        guardian.setTarget(player);
        guardian.tick();
        helper.assertTrue(guardian.getTarget() == player, "The fixture must begin with a visible player");
        return new PursuitArena(level, origin, guardian, player);
    }

    private static void setPursuitCover(PursuitArena arena, boolean covered) {
        BlockPos center = arena.origin().east(16);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, 0, -4), center.offset(4, 8, 4))) {
            if (pos.getX() == center.getX() - 4 || pos.getX() == center.getX() + 4
                    || pos.getZ() == center.getZ() - 4 || pos.getZ() == center.getZ() + 4 || pos.getY() == center.getY() + 8) {
                arena.level().setBlockAndUpdate(pos, covered ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
        }
    }

    private record PursuitArena(ServerLevel level, BlockPos origin, CoreGuardianEntity guardian, Player player) {

        public void close() {
            guardian.discard();
            player.discard();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -1, -12), origin.offset(28, 12, 12))) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static City findCube(ServerLevel level, int firstX) {
        return findCity(level, firstX, "city5_1");
    }

    private static City findCity(ServerLevel level, int firstX, String plan) {
        for (int i = 0; i < 1000; i++) {
            ChunkPos center = new ChunkPos(firstX + i * 16, 18008);
            City city = CityTools.getCity(level.getSeed(), center);
            if (CityTools.isCityCenter(level.getSeed(), center.x, center.z) && city.getPlan().getName().equals(plan)) {
                return city;
            }
        }
        throw new AssertionError("No dungeon found for " + plan);
    }

    private static Player target(ServerLevel level, int equipment, boolean enchanted) {
        Player player = new Player(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "guardian-target")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public boolean isBlocking() {
                // Supply raised-shield input without ticking this non-networked test player.
                return getOffhandItem().is(Items.SHIELD);
            }
        };
        ItemStack holder = new ItemStack(Registration.ENERGY_HOLDER.get());
        holder.getOrCreateTag().putInt("negarite", 1000);
        holder.getOrCreateTag().putInt("posirite", 1000);
        player.getInventory().setItem(9, holder);
        List<ItemStack> armor = equipment == 0
                ? List.of(new ItemStack(Items.NETHERITE_BOOTS), new ItemStack(Items.NETHERITE_LEGGINGS),
                        new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.NETHERITE_HELMET))
                : List.of(new ItemStack(Registration.POWERSUIT_FEET.get()), new ItemStack(Registration.POWERSUIT_LEGS.get()),
                        new ItemStack(Registration.POWERSUIT_CHEST.get()), new ItemStack(Registration.POWERSUIT_HEAD.get()));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                continue;
            }
            ItemStack stack = armor.get(slot.getIndex());
            if (equipment > 0) {
                stack.getOrCreateTag().putBoolean(ArmorUpgradeType.ARMOR.getModuleKey(), true);
                if (equipment == 2 && slot == EquipmentSlot.CHEST) {
                    stack.getTag().putBoolean(ArmorUpgradeType.FORCEFIELD.getModuleKey(), true);
                }
            }
            if (enchanted) {
                stack.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
            }
            player.setItemSlot(slot, stack);
            if (equipment > 0 && equipment < 3) {
                ModuleSupport.updateWorkingState(stack, player);
            }
            player.getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(slot));
        }
        return player;
    }
}
