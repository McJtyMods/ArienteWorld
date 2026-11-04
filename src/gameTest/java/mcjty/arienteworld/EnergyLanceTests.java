package mcjty.arienteworld;

import static mcjty.arienteworld.setup.Registration.ENTITY_SOLDIER;
import static mcjty.arienteworld.setup.Registration.ENTITY_MASTER_SOLDIER;

import mcjty.ariente.Ariente;
import mcjty.arienteworld.entities.soldier.SoldierBehaviourType;
import mcjty.ariente.api.ArmorUpgradeType;
import mcjty.ariente.items.EnergyHolderItem;
import mcjty.ariente.blocks.utility.BlueprintStorageTile;
import mcjty.ariente.blocks.utility.ConstructorTile;
import mcjty.ariente.entities.LaserEntity;
import mcjty.arienteworld.entities.soldier.EntityAISoldierAttack;
import mcjty.arienteworld.entities.soldier.SoldierEntity;
import mcjty.arienteworld.entities.soldier.SoldierLanceAttackGoal;
import mcjty.ariente.items.BlueprintItem;
import mcjty.ariente.items.EnergyLanceItem;
import mcjty.ariente.items.LanceAim;
import mcjty.ariente.items.modules.ModuleSupport;
import mcjty.ariente.gui.ModGuis;
import mcjty.hologui.gui.HoloGuiEntity;
import mcjty.ariente.recipes.BlueprintRecipeRegistry;
import mcjty.ariente.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LootingLevelEvent;

import java.util.List;

@GameTestHolder(ArienteWorld.MODID)
@PrefixGameTestTemplate(false)
public class EnergyLanceTests {

    @GameTest(template = "empty")
    public static void lanceRequiresBothHandsAndEnforcesCooldown(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        player.setPos(helper.absoluteVec(new Vec3(1, 3, 1)));
        ItemStack lance = new ItemStack(Registration.ENERGY_LANCE.get());
        helper.assertTrue(!lance.isDamageableItem() && lance.getMaxDamage() == 0,
                "The lance must have no durability");
        player.setItemInHand(InteractionHand.MAIN_HAND, lance);
        for (var item : List.of(Items.SHIELD, Items.STONE, Registration.ENERGY_LANCE.get())) {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item));
            helper.assertTrue(lance.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL,
                    "Any occupied off hand must block the lance");
            helper.assertTrue(lance.getDamageValue() == 0 && !player.getCooldowns().isOnCooldown(lance.getItem()),
                    "A blocked shot must not consume durability or start a cooldown");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(lance.use(helper.getLevel(), player, InteractionHand.OFF_HAND).getResult() == InteractionResult.FAIL,
                "The lance cannot fire from the off hand");
        player.setItemInHand(InteractionHand.MAIN_HAND, lance);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.assertTrue(lance.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "An empty off hand must allow firing");
        helper.assertTrue(lance.getDamageValue() == 0 && player.getCooldowns().isOnCooldown(lance.getItem()),
                "A successful survival shot starts a cooldown without damaging the lance");
        helper.assertTrue(lance.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL
                        && lance.getDamageValue() == 0,
                "Repeated use during cooldown must not fire or damage the lance");
        player.getCooldowns().removeCooldown(lance.getItem());
        helper.assertTrue(lance.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction()
                        && lance.getCount() == 1 && lance.getDamageValue() == 0,
                "Firing again after cooldown must leave the lance intact");
        helper.getLevel().getEntitiesOfClass(LaserEntity.class, player.getBoundingBox().inflate(3),
                laser -> laser.getOwner() == player).forEach(LaserEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lanceStartsAtTheWeaponAndAimsAtTheCrosshair(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        Vec3 origin = clearFiringLane(helper);
        player.moveTo(origin.x, origin.y, origin.z, -90, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.ENERGY_LANCE.get()));
        Cow target = EntityType.COW.create(helper.getLevel());
        target.setPos(origin.add(4, player.getEyeHeight() - target.getEyeHeight(), 0));
        target.setNoAi(true);
        helper.getLevel().addFreshEntity(target);
        try {
            for (HumanoidArm arm : HumanoidArm.values()) {
                player.setMainArm(arm);
                LaserEntity shot = LaserEntity.createLanceShot(helper.getLevel(), player, new Vec3(1, 0, 0), 0);
                Vec3 muzzle = shot.position();
                Vec3 offset = muzzle.subtract(player.getEyePosition());
                helper.assertTrue(offset.x > .5 && offset.y < 0 && Math.abs(offset.z) > .1,
                        "The muzzle must be forward, below the eyes and beside the crosshair");
                helper.assertTrue((offset.z > 0) == (arm == HumanoidArm.RIGHT), "The muzzle must follow the main arm");
                helper.getLevel().addFreshEntity(shot);
                LanceAim held = LanceAim.calculate(player, new Vec3(1, 0, 0));
                helper.assertTrue(held.muzzle().distanceTo(muzzle) < .0001 && held.direction().dot(shot.getBeamDirection()) > .999999,
                        "A fired laser must not pull the held lance away from its target");
                helper.assertTrue(shot.getBeamLength(muzzle) == 0 && shot.getBeamLength(muzzle.add(1, 0, 0)) == 1,
                        "A growing laser trail must start at the muzzle instead of extending behind it");
                Vec3 aim = shot.getBeamDirection();
                double distance = (target.getX() - muzzle.x) / aim.x;
                Vec3 atTarget = muzzle.add(aim.scale(distance));
                helper.assertTrue(target.getBoundingBox().inflate(.01).contains(atTarget),
                        "The offset muzzle must still aim at the entity under the crosshair");
                Vec3 atSight = muzzle.add(aim.scale((target.getBoundingBox().minX - muzzle.x) / aim.x));
                helper.assertTrue(Math.abs(atSight.y - player.getEyeY()) < .0001 && Math.abs(atSight.z - player.getZ()) < .0001,
                        "Entity aiming must use the sightline intersection rather than the target's feet");
                shot.discard();
            }
            target.setPos(origin.add(1, player.getEyeHeight() - target.getEyeHeight(), 0));
            LaserEntity close = LaserEntity.createLanceShot(helper.getLevel(), player, new Vec3(1, 0, 0), 0);
            helper.assertTrue(close.getX() < target.getBoundingBox().minX && close.getBeamDirection().x > 0,
                    "The long muzzle offset must not spawn beyond a close target and shoot back at the wielder: muzzle="
                            + close.position() + ", target=" + target.getBoundingBox() + ", direction=" + close.getBeamDirection());
            close.discard();
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stationarySoldierLanceStaysBelowItsHead(GameTestHelper helper) {
        Vec3 origin = clearFiringLane(helper);
        SoldierEntity soldier = ENTITY_SOLDIER.get().create(helper.getLevel());
        soldier.setNoAi(true);
        soldier.moveTo(origin.x, origin.y, origin.z, 0, 0);
        soldier.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.ENERGY_LANCE.get()));
        for (HumanoidArm arm : HumanoidArm.values()) {
            soldier.setLeftHanded(arm == HumanoidArm.LEFT);
            for (float bodyYaw : new float[]{0, 90, 180, -90}) {
                soldier.setYRot(bodyYaw);
                for (float headYaw : new float[]{0, 90, 180, -90}) {
                    soldier.setYHeadRot(headYaw);
                    soldier.setOldPosAndRot();
                    soldier.yHeadRotO = headYaw;
                    for (float partialTick : new float[]{0, .5f, 1}) {
                        Vec3 eye = soldier.getEyePosition(partialTick);
                        Vec3 forward = soldier.getViewVector(partialTick);
                        LanceAim aim = LanceAim.calculate(soldier, eye, forward, bodyYaw);
                        Vec3 offset = aim.muzzle().subtract(eye);
                        helper.assertTrue(offset.y < -.2 && offset.dot(forward) > 1,
                                "A stationary soldier's lance must stay below its eyes and forward when head and body face different ways");
                        Vec3 right = new Vec3(-forward.z, 0, forward.x).normalize();
                        helper.assertTrue((offset.dot(right) > 0) == (arm == HumanoidArm.RIGHT),
                                "The muzzle must stay on the main-hand side of the soldier's aim");
                    }
                }
            }
        }
        for (Vec3 vertical : new Vec3[]{new Vec3(0, 1, 0), new Vec3(0, -1, 0)}) {
            Vec3 right = LanceAim.rightVector(vertical, 90);
            helper.assertTrue(Math.abs(right.length() - 1) < .0001 && Math.abs(right.dot(vertical)) < .0001,
                    "Vertical aim needs a finite perpendicular weapon frame");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lanceHitsForThirtyPercentMoreAndStopsAtWalls(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        Vec3 origin = clearFiringLane(helper);
        player.setPos(origin);
        ItemStack sabre = new ItemStack(Registration.ENERGY_SABRE.get());
        var modifiers = sabre.getAttributeModifiers(EquipmentSlot.MAINHAND);
        player.getAttributes().addTransientAttributeModifiers(modifiers);
        float sabreHit = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        player.getAttributes().removeAttributeModifiers(modifiers);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.ENERGY_LANCE.get()));
        Cow target = EntityType.COW.create(helper.getLevel());
        target.setPos(origin.add(4, player.getEyeHeight() - target.getEyeHeight(), 0));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        target.setHealth(100);
        target.setNoAi(true);
        helper.getLevel().addFreshEntity(target);
        LaserEntity shot = LaserEntity.createLanceShot(helper.getLevel(), player, new Vec3(1, 0, 0), EnergyLanceItem.getShotDamage(player));
        try {
            helper.assertTrue(Math.abs(shot.getDeltaMovement().length() - 1.8) < .0001,
                    "A lance shot must launch 20% faster, at 1.8 blocks per tick");
            for (int tick = 0; tick < 8 && !shot.isRemoved(); tick++) {
                shot.tick();
            }
            helper.assertTrue(shot.isRemoved() && Math.abs((100 - target.getHealth()) - sabreHit * 1.3F) < .001F,
                    "The actual laser impact must deal 30% more damage than a fully charged ordinary sabre hit");
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "The shot must ignore its wielder");
        } finally {
            target.discard();
            shot.discard();
        }
        BlockPos wall = BlockPos.containing(player.getEyePosition().add(2, 0, 0));
        helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        LaserEntity blocked = LaserEntity.createLanceShot(helper.getLevel(), player, new Vec3(1, 0, 0), 16.9F);
        Vec3 blockedMuzzle = blocked.position();
        for (int tick = 0; tick < 5 && !blocked.isRemoved(); tick++) {
            blocked.tick();
        }
        helper.assertTrue(blocked.isRemoved() && blocked.getX() < wall.getX() + 2,
                "A lance laser must be consumed by a solid wall: muzzle=" + blockedMuzzle + ", final=" + blocked.position()
                        + ", direction=" + blocked.getBeamDirection() + ", wall=" + wall + ", state=" + helper.getLevel().getBlockState(wall));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void soldiersAndGuardsSwitchToRangedCombatAfterReload(GameTestHelper helper) {
        for (SoldierBehaviourType behaviour : SoldierBehaviourType.values()) {
            SoldierEntity soldier = SoldierEntity.create(helper.getLevel(), null, behaviour);
            Player target = helper.makeMockSurvivalPlayer();
            Vec3 origin = clearFiringLane(helper);
            soldier.setPos(origin);
            target.setPos(origin.add(4, 0, 0));
            soldier.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.ENERGY_LANCE.get()));
            CompoundTag saved = new CompoundTag();
            soldier.saveWithoutId(saved);
            SoldierEntity restored = ENTITY_SOLDIER.get().create(helper.getLevel());
            restored.load(saved);
            restored.setTarget(target);
            SoldierLanceAttackGoal ranged = new SoldierLanceAttackGoal(restored);
            EntityAISoldierAttack melee = new EntityAISoldierAttack(restored, restored, 1, false);
            helper.assertTrue(ranged.canUse() && !melee.canUse(), "Reloaded lance wielders must select ranged combat: " + behaviour);
            ranged.start();
            for (int tick = 0; tick < EnergyLanceItem.SHOT_INTERVAL + 2; tick++) {
                ranged.tick();
            }
            var shots = helper.getLevel().getEntitiesOfClass(LaserEntity.class, restored.getBoundingBox().inflate(8),
                    laser -> laser.getOwner() == restored && laser.isLanceShot());
            helper.assertTrue(!shots.isEmpty(), "The ranged goal must fire a real laser: " + behaviour
                    + ", visible=" + restored.getSensing().hasLineOfSight(target));
            CompoundTag projectile = new CompoundTag();
            shots.get(0).saveWithoutId(projectile);
            helper.assertTrue(Math.abs(projectile.getFloat("damage") - restored.getMinimumSabreDamage() * 1.3F) < .001F,
                    "Soldier lance damage must include the sabre damage floor");
            shots.forEach(LaserEntity::discard);
            ranged.stop();
            restored.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.ENERGY_SABRE.get()));
            helper.assertTrue(!ranged.canUse(), "Changing to a sabre must stop ranged combat");
            restored.discard();
            soldier.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lanceBlueprintCraftsInTheConstructor(GameTestHelper helper) {
        BlockPos bench = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(bench, Registration.CONSTRUCTOR.get().defaultBlockState());
        helper.getLevel().setBlockAndUpdate(bench.above(), Registration.BLUEPRINT_STORAGE.get().defaultBlockState());
        var library = (BlueprintStorageTile) helper.getLevel().getBlockEntity(bench.above());
        ItemStack blueprint = BlueprintItem.makeBluePrint(new ItemStack(Registration.ENERGY_LANCE.get()));
        library.getItems().setStackInSlot(0, blueprint);
        var recipe = BlueprintRecipeRegistry.findRecipe(BlueprintItem.getDestination(blueprint));
        helper.assertTrue(recipe != null, "A populated lance blueprint must resolve its Constructor recipe");
        Player player = helper.makeMockSurvivalPlayer();
        for (ItemStack ingredient : recipe.getIngredientList()) {
            player.getInventory().add(ingredient.copy());
        }
        var constructor = (ConstructorTile) helper.getLevel().getBlockEntity(bench);
        var output = constructor.createGui("", Ariente.guiHandler.getComponentRegistry()).findChild("outputslots").orElseThrow();
        output.hit(player, null, 2, 1.5);
        output.hit(player, null, 2, 1.5);
        helper.assertTrue(player.getInventory().countItem(Registration.ENERGY_LANCE.get()) == 1,
                "The actual Constructor control must craft a lance from its blueprint");
        helper.assertTrue(ItemStack.matches(blueprint, library.getItems().getStackInSlot(0)), "Crafting must retain the reusable blueprint");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lanceGuiInstallsAllWeaponModulesUsingHolderFuel(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        player.setPos(helper.absoluteVec(new Vec3(1, 3, 1)));
        ItemStack lance = new ItemStack(Registration.ENERGY_LANCE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, lance);
        var modules = List.of(Registration.MODULE_ENERGY.get(),
                Registration.MODULE_POWER.get(), Registration.MODULE_INHIBIT.get(), Registration.MODULE_LOOTING.get(), Registration.MODULE_FIRE.get());
        for (int i = 0; i < modules.size(); i++) {
            player.getInventory().setItem(9 + i, new ItemStack(modules.get(i)));
        }
        player.getInventory().setItem(17, new ItemStack(Registration.ENERGY_HOLDER.get()));
        player.getInventory().setItem(15, new ItemStack(Registration.DUST_NEGARITE.get(), 2));
        player.getInventory().setItem(16, new ItemStack(Registration.DUST_POSIRITE.get(), 2));
        HoloGuiEntity holo = (HoloGuiEntity) Ariente.guiHandler.openHoloGui(player, ModGuis.GUI_ARMOR, 1);
        holo.setCloseStrategy(0);
        try {
            click(player, holo, 3, 3);
            helper.assertTrue(holo.getGuiId().equals(ModGuis.GUI_ARMOR_SABRE), "The equipment menu must open the lance's weapon configuration");
            for (int i = 0; i < modules.size(); i++) {
                double x = i % 2 == 0 ? 1.5 : 5.5;
                double y = 2.7 + 1.2 * (i / 2);
                click(player, holo, x, y);
                click(player, holo, x + 1, y);
                click(player, holo, x + 2, y);
                helper.assertTrue(lance.getTag().getBoolean(modules.get(i).getType().getModuleKey())
                                && lance.getTag().getInt(modules.get(i).getType().getHotkeyKey()) == 1
                                && player.getInventory().countItem(modules.get(i)) == 0,
                        "Each sabre module must install, enable and accept a hotkey on the lance: " + modules.get(i).getType() + ", tag=" + lance.getTag());
            }
            var holderGui = EnergyHolderItem.createGui(player);
            holderGui.hit(player, null, 5.5, 3.8);
            holderGui.hit(player, null, 5.5, 6.2);
            helper.assertTrue(EnergyHolderItem.count(player, "negarite") == 1 && EnergyHolderItem.count(player, "posirite") == 1,
                    "The holder GUI must supply the weapon’s shared fuel");
            lance.inventoryTick(helper.getLevel(), player, 0, true);
            helper.assertTrue(ModuleSupport.getPowerUsage(lance).getRight() == 130
                            && modules.stream().allMatch(module -> ModuleSupport.hasWorkingUpgrade(lance, module.getType())),
                    "Energy Efficiency must allow all 110 usage of the weapon modules to work");
            helper.assertTrue(Math.abs(EnergyLanceItem.getShotDamage(player) - 32.5F) < .001F,
                    "Power must double the lance's weapon damage bonus, preserving its 30% advantage");
            EnergyHolderItem.findActiveHolder(player).getTag().putLong("charge", 0);
            holderGui.hit(player, null, 5.5, 3.8);
            holderGui.hit(player, null, 5.5, 6.2);
            lance.inventoryTick(helper.getLevel(), player, 0, true);
            helper.assertTrue(player.getInventory().countItem(Registration.DUST_NEGARITE.get()) == 0
                            && player.getInventory().countItem(Registration.DUST_POSIRITE.get()) == 0,
                    "The holder GUI must refill its reserves using both inventory dusts");
            ModuleSupport.receivedHotkey(player, 1);
            helper.assertTrue(modules.stream().noneMatch(module -> ModuleSupport.hasWorkingUpgrade(lance, module.getType())),
                    "The configured module hotkey must toggle lance modules off");
            for (int i = 0; i < modules.size(); i++) {
                click(player, holo, i % 2 == 0 ? 1.5 : 5.5, 2.7 + 1.2 * (i / 2));
            }
            helper.assertTrue(modules.stream().allMatch(module -> player.getInventory().countItem(module) == 1),
                    "Removing modules from the lance must return their inventory items");
        } finally {
            holo.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lanceLaserRetainsFireInhibitAndLootingWhenSavedAndStowed(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        Vec3 origin = clearFiringLane(helper);
        player.setPos(origin);
        ItemStack lance = new ItemStack(Registration.ENERGY_LANCE.get());
        for (ArmorUpgradeType type : List.of(ArmorUpgradeType.ENERGY, ArmorUpgradeType.FIRE, ArmorUpgradeType.INHIBIT, ArmorUpgradeType.LOOTING)) {
            lance.getOrCreateTag().putBoolean(type.getModuleKey(), true);
        }
        ItemStack holder = new ItemStack(Registration.ENERGY_HOLDER.get());
        holder.getOrCreateTag().putInt("negarite", 1);
        holder.getOrCreateTag().putInt("posirite", 1);
        player.getInventory().setItem(9, holder);
        player.setItemInHand(InteractionHand.MAIN_HAND, lance);
        lance.inventoryTick(helper.getLevel(), player, 0, true);
        LaserEntity shot = LaserEntity.createLanceShot(helper.getLevel(), player, new Vec3(1, 0, 0), EnergyLanceItem.getShotDamage(player));
        CompoundTag saved = new CompoundTag();
        shot.saveWithoutId(saved);
        shot.discard();
        LaserEntity restored = Registration.ENTITY_LASER.get().create(helper.getLevel());
        restored.load(saved);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        lance.inventoryTick(helper.getLevel(), player, 0, false);
        Cow target = EntityType.COW.create(helper.getLevel());
        target.setPos(origin.add(4, player.getEyeHeight() - target.getEyeHeight(), 0));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        target.setHealth(100);
        target.setNoAi(true);
        target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200));
        target.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        helper.getLevel().addFreshEntity(target);
        try {
            for (int tick = 0; tick < 8 && !restored.isRemoved(); tick++) {
                restored.tick();
            }
            helper.assertTrue(target.getHealth() < 100 && target.isOnFire(), "A saved laser must still apply its Fire module after stowing the lance"
                    + ": health=" + target.getHealth() + ", fire=" + target.getRemainingFireTicks() + ", savedFire=" + saved.getInt("lanceFire"));
            helper.assertTrue(!target.hasEffect(MobEffects.REGENERATION) && target.hasEffect(MobEffects.POISON),
                    "The laser's Inhibit module must strip beneficial effects while preserving harmful effects");
            LootingLevelEvent looting = new LootingLevelEvent(target, helper.getLevel().damageSources().indirectMagic(restored, player), 0);
            MinecraftForge.EVENT_BUS.post(looting);
            helper.assertTrue(looting.getLootingLevel() == 3, "The actual loot hook must retain Looting III after saving and stowing the weapon");
        } finally {
            target.discard();
            restored.discard();
        }
        var master = ENTITY_MASTER_SOLDIER.get().create(helper.getLevel());
        master.setPos(origin.add(4, player.getEyeHeight() - master.getEyeHeight(), 0));
        helper.getLevel().addFreshEntity(master);
        LaserEntity inhibitor = Registration.ENTITY_LASER.get().create(helper.getLevel());
        inhibitor.load(saved);
        try {
            for (int tick = 0; tick < 8 && !inhibitor.isRemoved(); tick++) {
                inhibitor.tick();
            }
            CompoundTag masterData = new CompoundTag();
            master.saveWithoutId(masterData);
            helper.assertTrue(masterData.getInt("noregen") == 200, "Inhibit must suppress Master Soldier regeneration despite their magic resistance");
        } finally {
            master.discard();
            inhibitor.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void occupiedOffhandStopsLanceModulesWithoutConsumingFuel(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        ItemStack lance = new ItemStack(Registration.ENERGY_LANCE.get());
        lance.getOrCreateTag().putBoolean(ArmorUpgradeType.FIRE.getModuleKey(), true);
        lance.getTag().putBoolean(ArmorUpgradeType.POWER.getModuleKey(), true);
        ItemStack holder = new ItemStack(Registration.ENERGY_HOLDER.get());
        holder.getOrCreateTag().putInt("negarite", 1);
        holder.getOrCreateTag().putInt("posirite", 1);
        player.getInventory().setItem(9, holder);
        lance.enchant(Enchantments.MOB_LOOTING, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, lance);
        lance.inventoryTick(helper.getLevel(), player, 0, true);
        helper.assertTrue(ModuleSupport.hasWorkingUpgrade(lance, ArmorUpgradeType.FIRE), "A usable held lance must activate fueled modules");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
        long power = holder.getTag().getLong("charge");
        lance.inventoryTick(helper.getLevel(), player, 0, true);
        helper.assertTrue(holder.getTag().getLong("charge") == power && !ModuleSupport.hasWorkingUpgrade(lance, ArmorUpgradeType.POWER)
                        && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, lance) == 0
                        && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MOB_LOOTING, lance) == 1,
                "An occupied off hand must stop modules, preserve fuel and remove only module-supplied enchantments");
        helper.succeed();
    }

    private static Vec3 clearFiringLane(GameTestHelper helper) {
        Vec3 origin = helper.absoluteVec(new Vec3(1, 160, 1));
        BlockPos pos = BlockPos.containing(origin);
        for (BlockPos block : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(6, 4, 1))) {
            helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        }
        return origin;
    }

    private static void click(Player player, HoloGuiEntity holo, double x, double y) {
        // Drive panel actions deterministically here; LanceModuleClientChecks covers ray targeting.
        holo.getGui(player).hit(player, holo, x, y);
    }
}
