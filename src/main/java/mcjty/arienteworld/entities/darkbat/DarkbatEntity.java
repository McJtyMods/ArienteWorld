package mcjty.arienteworld.entities.darkbat;

import mcjty.arienteworld.ArienteWorld;
import mcjty.arienteworld.cities.CityTools;
import mcjty.arienteworld.config.SpawningConfiguration;
import mcjty.arienteworld.dimension.DimensionRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;

public class DarkbatEntity extends Phantom {

    public static final float SCALE = 1.6F;
    private static final int SPAWN_ATTEMPTS = 64;
    private static final double SPAWN_RADIUS = 128.0D;
    private static final TagKey<Biome> SPAWNS = TagKey.create(Registries.BIOME,
            new ResourceLocation(ArienteWorld.MODID, "darkbat_spawns"));

    public DarkbatEntity(EntityType<? extends DarkbatEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder registerAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    public static boolean checkDarkbatSpawnRules(EntityType<DarkbatEntity> type, ServerLevelAccessor level,
                                                 MobSpawnType reason, BlockPos pos, RandomSource random) {
        return level.getLevel().dimension().equals(DimensionRegister.DIMENSION)
                && level.getDifficulty() != Difficulty.PEACEFUL
                && level.getBiome(pos).is(SPAWNS)
                && level.canSeeSky(pos)
                && level.getFluidState(pos).isEmpty()
                // A low table weight alone has no effect when this is the only outdoor monster.
                && random.nextInt(SPAWN_ATTEMPTS) == 0
                && !CityTools.isDungeonChunk(level.getLevel().getSeed(), pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        return super.checkSpawnRules(level, reason)
                && (reason != MobSpawnType.NATURAL || hasNaturalSpawnCapacity());
    }

    private boolean hasNaturalSpawnCapacity() {
        int limit = SpawningConfiguration.DARKBAT_MAX_PER_PLAYER.get();
        if (limit == 0 || !(level() instanceof ServerLevel level)) {
            return false;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive() || horizontalDistanceToSqr(player) > SPAWN_RADIUS * SPAWN_RADIUS) {
                continue;
            }
            AABB area = new AABB(player.getX() - SPAWN_RADIUS, level.getMinBuildHeight(), player.getZ() - SPAWN_RADIUS,
                    player.getX() + SPAWN_RADIUS, level.getMaxBuildHeight(), player.getZ() + SPAWN_RADIUS);
            int count = level.getEntitiesOfClass(DarkbatEntity.class, area,
                    bat -> bat.isAlive() && bat.horizontalDistanceToSqr(player) <= SPAWN_RADIUS * SPAWN_RADIUS).size();
            if (count >= limit) {
                return false;
            }
        }
        return true;
    }

    private double horizontalDistanceToSqr(Player player) {
        double dx = getX() - player.getX();
        double dz = getZ() - player.getZ();
        return dx * dx + dz * dz;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Retain the phantom's circling and dive attacks, but acquire players without its scan delay.
        targetSelector.removeAllGoals(goal -> true);
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 0, true, false, null));
    }

    @Override
    public void setPhantomSize(int size) {
        // Fixed dimensions and damage; vanilla size changes also overwrite attack damage.
        super.setPhantomSize(0);
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }
}
