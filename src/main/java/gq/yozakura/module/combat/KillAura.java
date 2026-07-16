package gq.yozakura.module.combat;

import com.google.common.base.CaseFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import gq.yozakura.module.ModuleType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.manager.BlinkModules;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.event.bridge.*;
import gq.yozakura.manager.RotationState;
import gq.yozakura.manager.RotationDebug;
import gq.yozakura.manager.RotationCleanup;
import gq.yozakura.manager.RotationExitState;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.module.world.BedNuker;
import gq.yozakura.module.player.AutoBlockIn;
import gq.yozakura.module.player.AutoHeal;
import gq.yozakura.module.world.Scaffold;
import gq.yozakura.value.properties.*;
import gq.yozakura.util.module.*;

import java.util.ArrayList;
import java.util.Random;

public class KillAura extends Module {
    private static final float RANGE_INCREMENT = 0.1F;

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int AUTOBLOCK_NONE = 0;
    private static final int AUTOBLOCK_RELEASE = 1;
    private static final int AUTOBLOCK_INTERACT = 2;
    private static final int AUTOBLOCK_SWITCH = 3;
    private static final int AUTOBLOCK_BLINK = 4;
    public final ModeProperty mode;
    public final ModeProperty sort;
    public ModeProperty autoBlock;
    public final BooleanProperty autoBlockRequirePress;
    public final IntProperty autoBlockCPS;
    public final FloatProperty autoBlockRange;

    public final FloatProperty swingRange;
    public final FloatProperty attackRange;
    public final IntProperty fov;
    public final IntProperty minCPS;
    public final IntProperty maxCPS;
    public final IntProperty switchDelay;
    public final ModeProperty rotations;
    public final ModeProperty moveFix;
    public final PercentProperty smoothing;
    public final IntProperty angleStep;
    public final BooleanProperty throughWalls;
    public final BooleanProperty requirePress;
    public final BooleanProperty allowMining;
    public final BooleanProperty weaponsOnly;
    public final BooleanProperty allowTools;
    public final BooleanProperty inventoryCheck;
    public final BooleanProperty botCheck;
    public final BooleanProperty players;
    public final BooleanProperty bosses;
    public final BooleanProperty mobs;
    public final BooleanProperty animals;
    public final BooleanProperty golems;
    public final BooleanProperty silverfish;
    public final BooleanProperty teams;
    public final BooleanProperty rotationDebug;

    private final TimerUtil timer = new TimerUtil();
    public static EntityLivingBase target;
    private AttackData attackTarget = null;
    private int switchTick = 0;
    private boolean hitRegistered = false;
    private boolean blockingState = false;
    private boolean isBlocking = false;
    private boolean fakeBlockState = false;
    private long attackDelayMS = 0L;
    private long lastAttackAt = 0L;
    private long lastBlockAt = 0L;
    public int blockTick = 0;
    private boolean blockAnimationActive = false;
    private int blockAnimationSlot = -1;
    private final Random attackRandom = new Random();
    private boolean attackedThisTick = false;
    private int spoofSlotPackets = 0;
    private int spoofSlot = -1;
    private int autoBlockState = 0;
    private boolean manualAttackQueued = false;
    private boolean rotationSmoothActive = false;
    private float smoothYaw = 0.0F;
    private float smoothPitch = 0.0F;

    public KillAura() {
        super("KillAura", false);
        this.key = Keyboard.KEY_R;
        this.category = ModuleType.Combat;
        this.Chinese = "杀戮光环";
        this.Descript = "Attack targets with server-side rotations and autoblock";
        this.About = this.Descript;
        this.mode = new ModeProperty("Mode", 0, new String[]{"Single", "Switch"});
        this.sort = new ModeProperty("Sort", 0, new String[]{"Distance", "Health", "Hurt Time", "FOV"});

        this.autoBlock = new ModeProperty(
                "AutoBlock", AUTOBLOCK_NONE,
                new String[]{"None", "RELEASE", "INTERACT", "SWITCH", "BLINK"}
        );
        this.autoBlockRequirePress = new BooleanProperty("AutoBlock Require Press", false);
        this.autoBlockCPS = new IntProperty("AutoBlock Aps", 10, 1, 20);
        this.autoBlockRange = new FloatProperty("AutoBlock Range", 6.0F, 3.0F, 8.0F);
        this.swingRange = new FloatProperty("Swing Range", 3.5F, 3.0F, 6.0F);
        this.attackRange = new FloatProperty("Attack Range", 3.0F, 3.0F, 6.0F);
        this.autoBlockRange.setIncrement(RANGE_INCREMENT);
        this.swingRange.setIncrement(RANGE_INCREMENT);
        this.attackRange.setIncrement(RANGE_INCREMENT);
        this.fov = new IntProperty("Fov", 360, 30, 360);
        this.minCPS = new IntProperty("Min CPS", 14, 1, 20);
        this.maxCPS = new IntProperty("Max CPS", 14, 1, 20);
        this.switchDelay = new IntProperty("Switch Delay", 150, 0, 1000);
        this.rotations = new ModeProperty("Rotations", 2, new String[]{"None", "Legit", "Silent", "Lock View"});
        this.moveFix = new ModeProperty("Move Fix", 1, new String[]{"None", "Silent", "Strict"});
        this.smoothing = new PercentProperty("Smoothing", 25);
        this.angleStep = new IntProperty("Angle Step", 70, 30, 180);
        this.throughWalls = new BooleanProperty("Through Walls", true);
        this.requirePress = new BooleanProperty("Require Press", false);
        this.allowMining = new BooleanProperty("Allow Mining", false);
        this.weaponsOnly = new BooleanProperty("Weapons Only", false);
        this.allowTools = new BooleanProperty("Allow Tools", false, this.weaponsOnly::getValue);
        this.inventoryCheck = new BooleanProperty("Inventory Check", true);
        this.botCheck = new BooleanProperty("Bot Check", true);
        this.players = new BooleanProperty("Players", true);
        this.bosses = new BooleanProperty("Bosses", false);
        this.mobs = new BooleanProperty("Mobs", false);
        this.animals = new BooleanProperty("Animals", false);
        this.golems = new BooleanProperty("Golems", false);
        this.silverfish = new BooleanProperty("Silverfish", false);
        this.teams = new BooleanProperty("Teams", true);
        this.rotationDebug = new BooleanProperty("Rotation Debug", false);
    }

    private long getAttackDelay() {
        int minValue = Math.min(this.minCPS.getValue(), this.maxCPS.getValue());
        int maxValue = Math.max(this.minCPS.getValue(), this.maxCPS.getValue());
        double min = Math.max(1.0D, minValue);
        double max = Math.max(min, maxValue);
        double cps = min + this.attackRandom.nextDouble() * (max - min + 0.001D);
        int delay = Math.max(1, (int) Math.round(1000.0D / cps));
        delay += this.attackRandom.nextInt(11) - 5;
        if (this.attackRandom.nextInt(100) < 9) {
            delay += 15 + this.attackRandom.nextInt(35);
        }
        if (this.attackRandom.nextInt(100) < 5) {
            delay -= 6 + this.attackRandom.nextInt(14);
        }
        if (this.isBlocking) {
            delay += this.attackRandom.nextInt(9);
        }
        return Math.max(50L, Math.min(260L, delay));
    }

    private boolean performAttack(float yaw, float pitch) {
        if (YozakuraRuntime.playerStateManager.isDigging()
                || YozakuraRuntime.playerStateManager.isPlacing()) {
            return false;
        }
        if (this.isPlayerBlocking()) {
            return false;
        }
        this.prepareLocalAttackUseState();
        if ((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(this.attackTarget.getBox()))
                && RotationUtil.rayTrace(this.attackTarget.getBox(), yaw, pitch, this.attackRange.getValue()) == null) {
            return false;
        }
        AttackEvent event = new AttackEvent(this.attackTarget.getEntity());
        EventManager.call(event);
        if (event.isCancelled()) {
            return false;
        }
        this.attackDelayMS = this.getAttackDelay();
        this.lastAttackAt = System.currentTimeMillis();
        mc.thePlayer.swingItem();
        MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
        PacketUtil.sendPacket(new C02PacketUseEntity(this.attackTarget.getEntity(), Action.ATTACK));
        if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
            PlayerUtil.attackEntity(this.attackTarget.getEntity());
        }
        this.hitRegistered = true;
        return true;
    }

    private boolean sendUseItem() {
        MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
        return this.startBlock(mc.thePlayer.getHeldItem());
    }
    private boolean startBlock(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
        this.refreshBlockAnimation(itemStack, true);
        this.blockingState = true;
        this.lastBlockAt = System.currentTimeMillis();
        return true;
    }

    private boolean stopBlock() {
        if (mc.thePlayer == null || !this.isPlayerBlocking()) {
            return false;
        }
        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        this.blockingState = false;
        return true;
    }

    private void sendSpoofHeldItemChange(int slot) {
        this.spoofSlotPackets++;
        PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
    }

    private boolean spoofSlot(boolean allowUi) {
        if (mc.thePlayer == null || this.spoofSlot >= 0) {
            return this.spoofSlot >= 0;
        }
        if (mc.currentScreen instanceof GuiContainer && !allowUi) {
            return false;
        }
        this.spoofSlot = this.getNearbySpoofSlot();
        this.sendSpoofHeldItemChange(this.spoofSlot);
        return true;
    }

    private int getNearbySpoofSlot() {
        int current = mc.thePlayer.inventory.currentItem;
        int right = (current + 1) % 9;
        int left = (current + 8) % 9;
        return right != current ? right : left;
    }

    private void restoreSpoofSlot() {
        if (mc.thePlayer == null || this.spoofSlot < 0) {
            return;
        }
        this.sendSpoofHeldItemChange(mc.thePlayer.inventory.currentItem);
        this.spoofSlot = -1;
    }

    private boolean startExpoBlock() {
        return this.sendUseItem();
    }

    private void releaseAutoBlock(boolean sendPacket) {
        YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        if (sendPacket && mc.thePlayer != null && this.isPlayerBlocking()) {
            this.stopBlock();
        } else if (mc.thePlayer != null && this.blockAnimationActive) {
            this.clearUseAnimation();
        }
        this.blockingState = false;
        this.isBlocking = false;
        this.fakeBlockState = false;
        this.restoreSpoofSlot();
        this.blockTick = 0;
        this.autoBlockState = 0;
        this.clearBlockAnimationState();
    }

    private void refreshBlockAnimation(ItemStack itemStack, boolean resetRenderer) {
        if (mc.thePlayer == null || itemStack == null || !(itemStack.getItem() instanceof ItemSword)) {
            this.clearBlockAnimationState();
            return;
        }
        int slot = mc.thePlayer.inventory.currentItem;
        boolean resetSlot = !this.blockAnimationActive || this.blockAnimationSlot != slot;
        if (resetSlot || !mc.thePlayer.isUsingItem()) {
            mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
        }
        if (resetRenderer && (!this.blockAnimationActive || this.blockAnimationSlot != slot)) {
            this.resetBlockItemRenderer();
        }
        this.blockAnimationActive = true;
        this.blockAnimationSlot = slot;
    }

    private void maintainBlockAnimation() {
        if (mc.thePlayer == null) {
            this.clearBlockAnimationState();
            return;
        }
        if (this.shouldShowBlockAnimation()) {
            this.refreshBlockAnimation(mc.thePlayer.getHeldItem(), false);
        } else {
            this.clearBlockAnimationState();
        }
    }

    private boolean shouldShowBlockAnimation() {
        return ItemUtil.isHoldingSword()
                && (this.blockingState || this.isBlocking || this.fakeBlockState)
                && (this.attackTarget != null || target != null);
    }

    private boolean shouldKeepLocalBlockAnimation() {
        return mc.thePlayer != null
                && ItemUtil.isHoldingSword()
                && (this.isBlocking || this.fakeBlockState)
                && (this.attackTarget != null || target != null);
    }

    private boolean hasValidTarget() {
        return this.attackTarget != null
                && this.isValidTarget(this.attackTarget.getEntity())
                && (this.isBoxInSwingRange(this.attackTarget.getBox()) || this.isBoxInAttackRange(this.attackTarget.getBox()));
    }

    private void clearBlockAnimationState() {
        this.blockAnimationActive = false;
        this.blockAnimationSlot = -1;
    }

    private void clearUseAnimation() {
        if (mc.thePlayer != null) {
            mc.thePlayer.clearItemInUse();
        }
    }

    private void prepareLocalAttackUseState() {
        if (mc.thePlayer != null && mc.thePlayer.isUsingItem()) {
            mc.thePlayer.clearItemInUse();
        }
    }

    private void resetCombatState() {
        this.resetCombatState(true);
    }

    private void resetCombatState(boolean clearRotationState) {
        this.releaseAutoBlock(true);
        if (clearRotationState) {
            this.clearRotations();
        } else {
            this.resetRotationSmoothing();
            YozakuraRuntime.rotationManager.clear();
            RotationDebug.setSourceEnabled("KillAura", false);
        }
        this.attackDelayMS = 0L;
        this.lastAttackAt = 0L;
        this.lastBlockAt = 0L;
        this.manualAttackQueued = false;
        this.spoofSlot = -1;
        this.autoBlockState = 0;
        this.setAttackTarget(null);
    }

    private void setAttackTarget(AttackData nextTarget) {
        if (nextTarget == null) {
            this.resetRotationSmoothing();
        } else {
            RotationExitState.clearSource("KillAura");
        }
        this.attackTarget = nextTarget;
        target = nextTarget == null ? null : nextTarget.getEntity();
    }

    private void clearRotations() {
        this.resetRotationSmoothing();
        RotationCleanup.clearModuleRotations("KillAura", 1);
        RotationDebug.setSourceEnabled("KillAura", false);
    }

    private void resetRotationSmoothing() {
        this.rotationSmoothActive = false;
        this.smoothYaw = 0.0F;
        this.smoothPitch = 0.0F;
    }

    private boolean requestExitRotation() {
        if (mc.thePlayer == null
                || !this.rotationSmoothActive
                || (this.rotations.getValue() != 2 && this.rotations.getValue() != 3)) {
            return false;
        }
        float smoothFactor = Math.max(0.20F, (float) this.smoothing.getValue() / 100.0F);
        float maxStep = Math.max(15.0F, (float) this.angleStep.getValue());
        RotationExitState.request(
                "KillAura",
                this.smoothYaw,
                this.smoothPitch,
                1,
                maxStep,
                smoothFactor,
                2,
                this.moveFix.getValue() != 0 || this.rotations.getValue() == 3,
                false
        );
        return true;
    }

    private void resetBlockItemRenderer() {
        try {
            if (mc.entityRenderer != null && mc.entityRenderer.itemRenderer != null) {
                mc.entityRenderer.itemRenderer.resetEquippedProgress();
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean canAttack() {
        if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) {
            return false;
        } else if (!(Boolean) this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (MinecraftAccessor.isHittingBlock(mc.playerController)) {
                return false;
            } else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
                return false;
            } else {
                AutoHeal autoHeal = (AutoHeal) YozakuraRuntime.moduleManager.modules.get(AutoHeal.class);
                if (autoHeal.isEnabled() && autoHeal.isSwitching()) {
                    return false;
                } else {
                    BedNuker bedNuker = (BedNuker) YozakuraRuntime.moduleManager.modules.get(BedNuker.class);
                    AutoBlockIn autoBlockIn = (AutoBlockIn) YozakuraRuntime.moduleManager.modules.get(AutoBlockIn.class);
                    if (bedNuker.isEnabled() && bedNuker.isReady()) {
                        return false;
                    } else if (YozakuraRuntime.moduleManager.modules.get(Scaffold.class).isEnabled()) {
                        return false;
                    } else if (autoBlockIn.isEnabled()) {
                        return false;
                    } else if (this.requirePress.getValue()) {
                        return PlayerUtil.isAttacking();
                    } else {
                        MovingObjectPosition objectMouseOver = mc.objectMouseOver;
                        return !this.allowMining.getValue()
                                || objectMouseOver == null
                                || objectMouseOver.typeOfHit != MovingObjectType.BLOCK
                                || !PlayerUtil.isAttacking();
                    }
                }
            }
        } else {
            return false;
        }
    }

    private boolean canAutoBlock() {
        if (this.autoBlock.getValue() == AUTOBLOCK_NONE || !ItemUtil.isHoldingSword()) {
            return false;
        } else {
            return !this.autoBlockRequirePress.getValue() || PlayerUtil.isUsingItem();
        }
    }

    private long getBlockInterval() {
        return Math.max(45L, Math.round(1000.0D / Math.max(1.0D, this.autoBlockCPS.getValue())));
    }

    private void updateExpoTarget() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.setAttackTarget(null);
            return;
        }
        if (this.attackTarget != null
                && this.isValidTarget(this.attackTarget.getEntity())
                && this.isBoxInAttackRange(this.attackTarget.getBox())
                && this.isBoxInSwingRange(this.attackTarget.getBox())
                && !this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {
            this.setAttackTarget(new AttackData(this.attackTarget.getEntity()));
            return;
        }

        this.timer.reset();
        ArrayList<EntityLivingBase> targets = new ArrayList<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase
                    && this.isValidTarget((EntityLivingBase) entity)
                    && this.isInRange((EntityLivingBase) entity)) {
                targets.add((EntityLivingBase) entity);
            }
        }
        if (targets.isEmpty()) {
            this.releaseAutoBlock(true);
            this.clearRotations();
            this.setAttackTarget(null);
            return;
        }
        if (targets.stream().anyMatch(this::isInSwingRange)) {
            targets.removeIf(entityLivingBase -> !this.isInSwingRange(entityLivingBase));
        }
        if (targets.stream().anyMatch(this::isInAttackRange)) {
            targets.removeIf(entityLivingBase -> !this.isInAttackRange(entityLivingBase));
        }
        if (targets.stream().anyMatch(this::isPlayerTarget)) {
            targets.removeIf(entityLivingBase -> !this.isPlayerTarget(entityLivingBase));
        }
        targets.sort(
                (entityLivingBase1, entityLivingBase2) -> {
                    int sortBase = 0;
                    switch (this.sort.getValue()) {
                        case 1:
                            sortBase = Float.compare(TeamUtil.getHealthScore(entityLivingBase1), TeamUtil.getHealthScore(entityLivingBase2));
                            break;
                        case 2:
                            sortBase = Integer.compare(entityLivingBase1.hurtResistantTime, entityLivingBase2.hurtResistantTime);
                            break;
                        case 3:
                            sortBase = Float.compare(
                                    RotationUtil.angleToEntity(entityLivingBase1),
                                    RotationUtil.angleToEntity(entityLivingBase2)
                            );
                            break;
                        default:
                            break;
                    }
                    return sortBase != 0
                            ? sortBase
                            : Double.compare(RotationUtil.distanceToEntity(entityLivingBase1), RotationUtil.distanceToEntity(entityLivingBase2));
                }
        );
        if (this.mode.getValue() == 1 && this.hitRegistered) {
            this.hitRegistered = false;
            this.switchTick++;
        }
        if (this.mode.getValue() == 0 || this.switchTick >= targets.size()) {
            this.switchTick = 0;
        }
        this.setAttackTarget(new AttackData(targets.get(this.switchTick)));
    }

    private boolean isAttackReady(long now) {
        return now - this.lastAttackAt >= this.attackDelayMS || this.manualAttackQueued;
    }

    private boolean isBlockReady(long now) {
        return now - this.lastBlockAt > this.getBlockInterval();
    }

    private boolean shouldSpoofAutoBlockSlot(int mode, boolean attackReady) {
        return attackReady && (mode == AUTOBLOCK_SWITCH || mode == AUTOBLOCK_BLINK);
    }

    private void setExpoBlockVisualState(int mode) {
        this.isBlocking = true;
        this.fakeBlockState = mode != AUTOBLOCK_RELEASE;
        this.autoBlockState = this.isPlayerBlocking() ? 1 : 0;
    }

    private boolean tryExpoAutoBlockAttack(int mode, boolean attackReady, boolean blockReady, float yaw, float pitch) {
        if (!attackReady || !blockReady || !this.isBoxInSwingRange(this.attackTarget.getBox())) {
            return false;
        }

        boolean spoofed = false;
        if (this.shouldSpoofAutoBlockSlot(mode, true)) {
            spoofed = this.spoofSlot(false);
        }
        if (this.isPlayerBlocking()) {
            this.stopBlock();
        }

        boolean attacked = this.performAttack(yaw, pitch);
        if (attacked) {
            this.autoBlockState = 2;
        }
        if (attacked || !this.isPlayerBlocking()) {
            boolean started = this.startExpoBlock();
            if (started) {
                this.autoBlockState = attacked ? 3 : 1;
            }
        }
        if (spoofed || this.spoofSlot >= 0) {
            this.restoreSpoofSlot();
        }
        return attacked;
    }

    private void maintainExpoAutoBlock(int mode, boolean blockReady, float yaw, float pitch) {
        YozakuraRuntime.blinkManager.setBlinkState(mode == AUTOBLOCK_BLINK, BlinkModules.AUTO_BLOCK);
        this.setExpoBlockVisualState(mode);
        if (!this.isPlayerBlocking() && blockReady) {
            boolean spoofed = false;
            if (mode == AUTOBLOCK_SWITCH) {
                spoofed = this.spoofSlot(false);
            }
            if (this.startExpoBlock()) {
                this.autoBlockState = 1;
            }
            if (spoofed || this.spoofSlot >= 0) {
                this.restoreSpoofSlot();
            }
        }
    }

    private boolean isValidTarget(EntityLivingBase entityLivingBase) {
        if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) {
            return false;
        } else if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.thePlayer.ridingEntity) {
            if (entityLivingBase == mc.getRenderViewEntity() || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityLivingBase.deathTime > 0) {
                return false;
            } else if (RotationUtil.angleToEntity(entityLivingBase) > this.fov.getValue().floatValue()) {
                return false;
            } else if (!this.throughWalls.getValue() && !RotationUtil.hasVisiblePoint(entityLivingBase.getEntityBoundingBox().expand(entityLivingBase.getCollisionBorderSize(), entityLivingBase.getCollisionBorderSize(), entityLivingBase.getCollisionBorderSize()))) {
                return false;
            } else if (entityLivingBase instanceof EntityOtherPlayerMP) {
                if (!this.players.getValue()) {
                    return false;
                } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                    return false;
                } else {
                    return (!this.teams.getValue() || !TeamUtil.isSameTeam((EntityPlayer) entityLivingBase)) && (!this.botCheck.getValue() || !TeamUtil.isBot((EntityPlayer) entityLivingBase));
                }
            } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
                return this.bosses.getValue();
            } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
                if (entityLivingBase instanceof EntityAnimal
                        || entityLivingBase instanceof EntityBat
                        || entityLivingBase instanceof EntitySquid
                        || entityLivingBase instanceof EntityVillager) {
                    return this.animals.getValue();
                } else if (!(entityLivingBase instanceof EntityIronGolem)) {
                    return false;
                } else {
                    return this.golems.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
                }
            } else if (!(entityLivingBase instanceof EntitySilverfish)) {
                return this.mobs.getValue();
            } else {
                return this.silverfish.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
            }
        } else {
            return false;
        }
    }

    private boolean isInRange(EntityLivingBase entityLivingBase) {
        return this.isInBlockRange(entityLivingBase) || this.isInSwingRange(entityLivingBase) || this.isInAttackRange(entityLivingBase);
    }

    private boolean isInBlockRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.autoBlockRange.getValue();
    }

    private boolean isInSwingRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.swingRange.getValue();
    }

    private boolean isBoxInSwingRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.swingRange.getValue();
    }

    private boolean isInAttackRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.attackRange.getValue();
    }

    private boolean isBoxInAttackRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.attackRange.getValue();
    }

    private boolean isPlayerTarget(EntityLivingBase entityLivingBase) {
        return entityLivingBase instanceof EntityPlayer && TeamUtil.isTarget((EntityPlayer) entityLivingBase);
    }



    public EntityLivingBase getTarget() {
        return this.attackTarget != null ? this.attackTarget.getEntity() : null;
    }

    public boolean isAttackAllowed() {
        Scaffold scaffold = (Scaffold) YozakuraRuntime.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled()) {
            return false;
        } else if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            return !this.requirePress.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        } else {
            return false;
        }
    }

    public boolean shouldAutoBlock() {
        if (this.isPlayerBlocking() && this.isBlocking) {
            return !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava();
        } else {
            return false;
        }
    }

    public boolean canReduceVelocityAttack() {
        return true;
    }

    public boolean isBlocking() {
        return this.fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return this.blockingState && ItemUtil.isHoldingSword();
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.resetCombatState();
            return;
        }
        if (event.getType() == EventType.PRE) {
            RotationDebug.setSourceEnabled("KillAura", this.rotationDebug.getValue());
            this.attackedThisTick = false;
            this.updateExpoTarget();

            boolean attack = this.attackTarget != null && this.canAttack() && this.hasValidTarget();
            boolean block = attack && this.canAutoBlock();
            long now = System.currentTimeMillis();
            if (!block) {
                if (this.blockingState || this.isBlocking || this.fakeBlockState) {
                    this.releaseAutoBlock(this.blockingState && this.isPlayerBlocking());
                } else {
                    YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    this.blockTick = 0;
                }
            }
            if (!attack) {
                this.clearRotations();
                this.manualAttackQueued = false;
            }
            if (attack) {
                int autoBlockMode = this.autoBlock.getValue();
                boolean attackReady = this.isAttackReady(now) && this.isBoxInSwingRange(this.attackTarget.getBox());
                boolean blockReady = !block || this.isBlockReady(now);
                float yaw = event.getNewYaw();
                float pitch = event.getNewPitch();

                if (this.isBoxInSwingRange(this.attackTarget.getBox())) {
                    if (this.rotations.getValue() == 2 || this.rotations.getValue() == 3) {
                        float[] rotations = this.calculateTargetRotations(event);
                        if (event.trySetRotation(rotations[0], rotations[1], 1)) {
                            VisualRotationState.publish("KillAura", rotations[0], rotations[1], 1);
                            if (this.rotations.getValue() == 3) {
                                YozakuraRuntime.rotationManager.setRotation(rotations[0], rotations[1], 1, true);
                            }
                            if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) {
                                event.setPervRotation(rotations[0], 1);
                            }
                            yaw = rotations[0];
                            pitch = rotations[1];
                        }
                    }

                    if (block) {
                        if (attackReady && blockReady) {
                            this.attackedThisTick = this.tryExpoAutoBlockAttack(autoBlockMode, true, true, yaw, pitch);
                        } else {
                            this.maintainExpoAutoBlock(autoBlockMode, blockReady, yaw, pitch);
                        }
                    } else if (attackReady) {
                        this.attackedThisTick = this.performAttack(yaw, pitch);
                    }
                }
                if (attackReady) {
                    this.manualAttackQueued = false;
                }
            }
        }
        if (event.getType() == EventType.POST){
            this.maintainBlockAnimation();
            this.attackedThisTick = false;
        }
    }

    private float[] calculateTargetRotations(UpdateEvent event) {
        if (!this.rotationSmoothActive) {
            this.smoothYaw = event.getNewYaw();
            this.smoothPitch = event.getNewPitch();
            this.rotationSmoothActive = true;
        }
        float smoothFactor = Math.max(0.20F, (float) this.smoothing.getValue() / 100.0F);
        float[] rotations = RotationUtil.getRotationsToBox(
                this.attackTarget.getBox(),
                this.smoothYaw,
                this.smoothPitch,
                (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F),
                smoothFactor
        );
        this.smoothYaw = rotations[0];
        this.smoothPitch = MathHelper.clamp_float(rotations[1], -90.0F, 90.0F);
        return new float[]{this.smoothYaw, this.smoothPitch};
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            if (mc.thePlayer == null || mc.theWorld == null) {
                this.resetCombatState();
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled() && mc.thePlayer != null) {
            if (event.getPacket() instanceof C07PacketPlayerDigging) {
                C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
                if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                    this.blockingState = false;
                    if (!this.shouldKeepLocalBlockAnimation()) {
                        this.clearUseAnimation();
                        this.clearBlockAnimationState();
                    }
                }
            }
            if (event.getPacket() instanceof C09PacketHeldItemChange) {
                if (this.spoofSlotPackets > 0) {
                    this.spoofSlotPackets--;
                    return;
                }
                this.blockingState = false;
                if (this.isBlocking) {
                    this.clearUseAnimation();
                }
                this.clearBlockAnimationState();
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.attackTarget != null && this.canAttack()) {
                this.manualAttackQueued = true;
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.attackTarget != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.attackTarget != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        RotationExitState.clearSource("KillAura");
        this.setAttackTarget(null);
        this.resetRotationSmoothing();
        this.switchTick = 0;
        this.hitRegistered = false;
        this.attackDelayMS = 0L;
        this.lastAttackAt = 0L;
        this.lastBlockAt = 0L;
        this.blockTick = 0;
        this.manualAttackQueued = false;
        this.spoofSlot = -1;
        this.autoBlockState = 0;
        this.clearBlockAnimationState();
    }

    @Override
    public void onDisabled() {
        boolean exitRotation = this.requestExitRotation();
        this.resetCombatState(!exitRotation);
    }

    @Override
    public void verifyValue(String mode) {
        if (!this.autoBlock.getName().equals(mode) && !this.autoBlockCPS.getName().equals(mode)) {
            if (this.swingRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.attackRange.setValue(this.swingRange.getValue());
                }
            } else if (this.attackRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.swingRange.setValue(this.attackRange.getValue());
                }
            } else if (this.minCPS.getName().equals(mode)) {
                if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.maxCPS.setValue(this.minCPS.getValue());
                }
            } else {
                if (this.maxCPS.getName().equals(mode) && this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.minCPS.setValue(this.maxCPS.getValue());
                }
            }
        }
    }
    public static class RotationData{
        private final Vec3 eye;
        private final Vec3 hiteVec;
        private double distance;
        private final Vector2f roation;
        public Vector2f getRotation(){
            return roation;
        }

        public RotationData(Vec3 bestEye, Vec3 bestHitVec, double minDistance, Vector2f bestRotation) {
            eye  = bestEye;
            hiteVec = bestHitVec;
            distance = minDistance;
            roation = bestRotation;
        }
    }
    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }

    public static class AttackData {
        private final EntityLivingBase entity;
        private final AxisAlignedBB box;
        private final double x;
        private final double y;
        private final double z;

        public AttackData(EntityLivingBase entityLivingBase) {
            this.entity = entityLivingBase;
            double collisionBorderSize = entityLivingBase.getCollisionBorderSize();
            this.box = entityLivingBase.getEntityBoundingBox().expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);
            this.x = entityLivingBase.posX;
            this.y = entityLivingBase.posY;
            this.z = entityLivingBase.posZ;
        }

        public EntityLivingBase getEntity() {
            return this.entity;
        }

        public AxisAlignedBB getBox() {
            return this.box;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }
    }
}
