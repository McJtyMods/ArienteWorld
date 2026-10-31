package mcjty.arienteworld;

import com.mojang.authlib.GameProfile;
import mcjty.ariente.entities.LaserEntity;
import mcjty.ariente.entities.drone.DroneEntity;
import mcjty.ariente.entities.soldier.EntityAISoldierAttack;
import mcjty.ariente.entities.soldier.SoldierEntity;
import mcjty.ariente.setup.Registration;
import net.minecraft.world.Difficulty;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class CombatRegressionTests {

    @GameTest(template = "empty")
    public static void soldierMeleeGoalWorksAfterSpawnAndReload(GameTestHelper helper) {
        var level = helper.getLevel();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        BlockPos pos = helper.absolutePos(new BlockPos(4, 160, 4));
        for (BlockPos block : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(3, 3, 2))) {
            level.setBlock(block, (block.getY() < pos.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
        }
        for (var type : List.of(Registration.ENTITY_SOLDIER.get(), Registration.ENTITY_MASTER_SOLDIER.get())) {
            SoldierEntity soldier = type.spawn(level, pos, MobSpawnType.NATURAL);
            helper.assertTrue(soldier != null, "Soldier must spawn for the melee check");
            try {
                verifySoldierMelee(helper, soldier, pos);
                CompoundTag saved = new CompoundTag();
                soldier.saveWithoutId(saved);
                SoldierEntity restored = type.create(level);
                try {
                    restored.load(saved);
                    verifySoldierMelee(helper, restored, pos);
                } finally {
                    restored.discard();
                }
            } finally {
                soldier.discard();
            }
        }
        helper.succeed();
    }

    private static void verifySoldierMelee(GameTestHelper helper, SoldierEntity soldier, BlockPos pos) {
        var player = helper.makeMockSurvivalPlayer();
        player.moveTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + .5);
        soldier.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        soldier.setTarget(player);
        EntityAISoldierAttack attack = new EntityAISoldierAttack(soldier, soldier, 1.0D, false);
        try {
            float health = player.getHealth();
            attack.start();
            attack.tick();
            helper.assertTrue(player.getHealth() < health,
                    "The soldier's melee goal must damage a player without missing combat attributes: " + soldier.getType());
        } finally {
            attack.stop();
            soldier.setTarget(null);
            player.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void combatDroneShootsVisiblePlayers(GameTestHelper helper) {
        var level = helper.getLevel();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        BlockPos origin = helper.absolutePos(new BlockPos(0, 250, 0));
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3))) {
            boolean wall = Math.abs(pos.getX() - origin.getX()) == 3 || Math.abs(pos.getY() - origin.getY()) == 3 || Math.abs(pos.getZ() - origin.getZ()) == 3;
            level.setBlock(pos, (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
        }
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "drone-target")) {

            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }
        };
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(origin.getX() + 8.5, origin.getY(), origin.getZ() + .5);
        level.addFreshEntity(player);
        DroneEntity drone = new DroneEntity(Registration.ENTITY_DRONE.get(), level) {
            {
                goalSelector.removeAllGoals(goal -> goal.getClass().getSimpleName().equals("AIDroneFly"));
            }
        };
        drone.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        drone.setPersistenceRequired();
        level.addFreshEntity(drone);
        drone.setTarget(player);
        AABB arena = new AABB(origin).inflate(18);
        boolean[] opened = {false};
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(level.getEntitiesOfClass(LaserEntity.class, arena).isEmpty(), "Combat drones must not shoot through their enclosing wall");
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            drone.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            drone.setDeltaMovement(0, 0, 0);
            drone.setTarget(player);
            opened[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(opened[0] && !level.getEntitiesOfClass(LaserEntity.class, arena, laser -> laser.getOwner() == drone).isEmpty(),
                    "A combat drone must fire its actual ranged goal at a visible player: removed=" + drone.isRemoved() + ", target=" + drone.getTarget() + ", visible=" + drone.getSensing().hasLineOfSight(player));
            level.getEntitiesOfClass(LaserEntity.class, arena).forEach(LaserEntity::discard);
            drone.discard();
            player.discard();
        });
    }

    @GameTest(template = "empty")
    public static void laserCollisionTracksMovement(GameTestHelper helper) {
        var level = helper.getLevel();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        BlockPos pos = helper.absolutePos(new BlockPos(0, 280, 0));
        Zombie target = EntityType.ZOMBIE.create(level);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.moveTo(pos.getX() + 6.5, pos.getY(), pos.getZ() + .5);
        level.getChunkAt(pos);
        level.getChunkAt(target.blockPosition());
        helper.assertTrue(level.addFreshEntity(target), "Laser target must be inserted in a loaded test chunk");
        LaserEntity laser = LaserEntity.create(level, pos.getX() + .5, pos.getY() + .9, pos.getZ() + .5, 1, 0, 0);
        level.addFreshEntity(laser);
        try {
            for (int ticks = 0; ticks < 40 && !laser.isRemoved(); ticks++) {
                laser.tick();
                helper.assertTrue(Math.abs(laser.getBoundingBox().minY - laser.getY()) < .00001
                                && Math.abs(laser.getBoundingBox().getCenter().x - laser.getX()) < .00001,
                        "Laser collision bounds must move with the projectile");
            }
            helper.assertTrue(target.getHealth() < target.getMaxHealth() && laser.isRemoved(), "A moving laser must hit and damage the target: health=" + target.getHealth() + ", laser=" + laser.position() + ", removed=" + laser.isRemoved());
        } finally {
            target.discard();
            laser.discard();
        }
        helper.succeed();
    }
}
