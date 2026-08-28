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
    private static final int AUTOBLOCK_LEGIT = 5;
    private static final int AUTOBLOCK_FULL_AB = 6;
    private static final int AUTOBLOCK_BYPASS_ALL = 7;
    private static final int AUTOBLOCK_HYPIXEL = 8;
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
    private long attackDelayMS = 0L;
    private long lastAttackAt = 0L;
    private final Random attackRandom = new Random();
    private boolean attackedThisTick = false;
    private boolean submittingOwnedAttackPackets = false;
    private boolean manualAttackQueued = false;
    private final KillAuraConfirmedRotationTracker attackRotationTracker =
            new KillAuraConfirmedRotationTracker();
    private final KillAuraRotationController rotationController =
            new KillAuraRotationController();
    private boolean rotationSmoothActive = false;
    private float smoothYaw = 0.0F;
    private float smoothPitch = 0.0F;
    private boolean hypixelAnimationBlockPose = false;
    private int activeAutoBlockMode = AUTOBLOCK_NONE;
    private static final long OWNED_PACKET_TIMEOUT_MILLIS = 350L;
    private volatile boolean awaitingOwnedBlockPacket;
    private volatile boolean awaitingOwnedReleasePacket;
    private volatile long ownedBlockAwaitStartedAt;
    private volatile long ownedReleaseAwaitStartedAt;
    private volatile long ownedBlockWriteId = PacketAcceptedEvent.NO_WRITE_ID;
    private volatile long ownedReleaseWriteId = PacketAcceptedEvent.NO_WRITE_ID;
    private final KillAuraAutoBlockController autoBlockController =
            new KillAuraAutoBlockController();
    private final KillAuraLeaderAutoBlockCycle leaderAutoBlockCycle =
            new KillAuraLeaderAutoBlockCycle();
    private final BlockHitRenderPose hypixelAnimationRenderPose =
            new BlockHitRenderPose();

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
                new String[]{"None", "RELEASE", "INTERACT", "SWITCH", "BLINK", "LEGIT", "FullAB", "BypassAll", "Hypixel"},
                new String[]{"None", "RELEASE", "INTERACT", "SWITCH", "BLINK", "FullAB", "BypassAll", "Hypixel"}
        ).addStoredAlias("Hypixel(Without NoSlow)", AUTOBLOCK_FULL_AB)
                .addStoredAlias("HypixelLag", AUTOBLOCK_HYPIXEL);
        this.autoBlockRequirePress = new BooleanProperty("AutoBlock Require Press", false);
        this.autoBlockCPS = new IntProperty("AutoBlock Aps", 10, 1, 20,
                () -> this.autoBlock.getValue() != AUTOBLOCK_NONE);
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
        if (this.autoBlockController.shouldRenderBlockPose()) {
            delay += this.attackRandom.nextInt(9);
        }
        return Math.max(50L, Math.min(260L, delay));
    }

    private boolean performAttack(float yaw, float pitch) {
        return this.performAttack(yaw, pitch, false);
    }

    private boolean performAttack(float yaw, float pitch, boolean allowReferenceRelease) {
        if (this.attackTarget == null
                || YozakuraRuntime.playerStateManager.isDigging()
                || YozakuraRuntime.playerStateManager.isPlacing()) {
            return false;
        }
        this.releaseStandaloneAutoBlockForAttack();
        if (this.autoBlockController.isBlocking()
                || this.autoBlockController.isReleasePending() && !allowReferenceRelease) {
            return false;
        }
        if (allowReferenceRelease && this.autoBlockController.isReleasePending()) {
            this.autoBlockController.prepareReferenceAttack();
        }
        AxisAlignedBB liveTargetBox = this.getLiveTargetBox();
        if (liveTargetBox == null || !this.isBoxInAttackRange(liveTargetBox)) {
            return false;
        }
        if ((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(liveTargetBox))
                && RotationUtil.rayTrace(liveTargetBox, yaw, pitch, this.attackRange.getValue()) == null) {
            return false;
        }
        AttackEvent event = new AttackEvent(this.attackTarget.getEntity());
        EventManager.call(event);
        if (event.isCancelled()) {
            return false;
        }
        this.attackDelayMS = this.getAttackDelay();
        this.lastAttackAt = monotonicTimeMillis();
        this.submittingOwnedAttackPackets = true;
        try {
            if (this.shouldKeepReferenceBlockPose()) {
                PacketUtil.sendPacket(new C0APacketAnimation());
            } else {
                mc.thePlayer.swingItem();
            }
            PacketUtil.sendPacket(new C02PacketUseEntity(this.attackTarget.getEntity(), Action.ATTACK));
        } finally {
            this.submittingOwnedAttackPackets = false;
        }
        if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
            PlayerUtil.attackEntity(this.attackTarget.getEntity());
        }
        this.hitRegistered = true;
        return true;
    }

    private boolean startBlock() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            this.autoBlockController.onBlockStartFailed();
            return false;
        }
        ItemStack itemStack = mc.thePlayer.getHeldItem();
        if (itemStack == null || !(itemStack.getItem() instanceof ItemSword)) {
            this.autoBlockController.onBlockStartFailed();
            return false;
        }
        this.awaitingOwnedBlockPacket = true;
        this.ownedBlockAwaitStartedAt = monotonicTimeMillis();
        this.ownedBlockWriteId = PacketAcceptedEvent.NO_WRITE_ID;
        boolean started = mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, itemStack);
        if (started) {
            this.resetBlockItemRenderer();
        } else {
            this.awaitingOwnedBlockPacket = false;
            this.ownedBlockAwaitStartedAt = 0L;
            this.autoBlockController.onBlockStartFailed();
        }
        return started;
    }

    private boolean stopBlock() {
        if (mc.thePlayer == null || mc.playerController == null) {
            this.autoBlockController.onBlockStopFailed();
            return false;
        }
        this.awaitingOwnedReleasePacket = true;
        this.ownedReleaseAwaitStartedAt = monotonicTimeMillis();
        this.ownedReleaseWriteId = PacketAcceptedEvent.NO_WRITE_ID;
        mc.playerController.onStoppedUsingItem(mc.thePlayer);
        return true;
    }

    private void releaseAutoBlock(boolean releaseOwnedUse) {
        YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        if (releaseOwnedUse && (this.autoBlockController.isBlocking()
                || this.autoBlockController.isBlockPending()
                || this.autoBlockController.isReleasePending())) {
            this.stopBlock();
        }
        this.awaitingOwnedBlockPacket = false;
        this.awaitingOwnedReleasePacket = false;
        this.ownedBlockAwaitStartedAt = 0L;
        this.ownedReleaseAwaitStartedAt = 0L;
        this.ownedBlockWriteId = PacketAcceptedEvent.NO_WRITE_ID;
        this.ownedReleaseWriteId = PacketAcceptedEvent.NO_WRITE_ID;
        this.autoBlockController.reset();
        this.leaderAutoBlockCycle.reset();
        this.hypixelAnimationRenderPose.end();
        this.hypixelAnimationBlockPose = false;
        this.activeAutoBlockMode = AUTOBLOCK_NONE;
    }

    private boolean shouldKeepReferenceBlockPose() {
        int mode = this.autoBlock.getValue();
        return (mode == AUTOBLOCK_LEGIT || mode == AUTOBLOCK_FULL_AB)
                && this.autoBlockController.shouldRenderBlockPose();
    }

    private boolean hasValidTarget() {
        return this.attackTarget != null
                && this.isValidTarget(this.attackTarget.getEntity())
                && (this.isBoxInBlockRange(this.attackTarget.getBox())
                || this.isBoxInSwingRange(this.attackTarget.getBox())
                || this.isBoxInAttackRange(this.attackTarget.getBox()));
    }

    private void releaseStandaloneAutoBlockForAttack() {
        gq.yozakura.module.Module module = gq.yozakura.manager.ModuleManager.getModule("AutoBlock");
        if (module instanceof AutoBlock && module.getState()) {
            ((AutoBlock) module).releaseForAttack();
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
        this.submittingOwnedAttackPackets = false;
        this.attackRotationTracker.clear();
        this.manualAttackQueued = false;
        this.setAttackTarget(null);
    }

    private void setAttackTarget(AttackData nextTarget) {
        EntityLivingBase previousEntity = this.attackTarget == null
                ? null : this.attackTarget.getEntity();
        EntityLivingBase nextEntity = nextTarget == null ? null : nextTarget.getEntity();
        if (previousEntity != nextEntity) {
            this.attackRotationTracker.clear();
            this.resetRotationSmoothing();
        }
        if (nextTarget != null) {
            RotationExitState.clearSource("KillAura");
        }
        this.attackTarget = nextTarget;
        target = nextEntity;
    }

    private AxisAlignedBB getLiveTargetBox() {
        if (this.attackTarget == null || this.attackTarget.getEntity() == null) {
            return null;
        }
        EntityLivingBase entity = this.attackTarget.getEntity();
        float border = entity.getCollisionBorderSize();
        return entity.getEntityBoundingBox().expand(border, border, border);
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
        this.rotationController.reset();
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
                this.moveFix.getValue() == 1 || this.rotations.getValue() == 3,
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
        }
        return !this.autoBlockRequirePress.getValue() || PlayerUtil.isUsingItem();
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

    private int getAutoBlockCadence() {
        return this.autoBlockCPS.getValue();
    }

    private void updateAutoBlockBlink(int mode, boolean shouldBlock) {
        if (mode != AUTOBLOCK_FULL_AB && mode != AUTOBLOCK_HYPIXEL) {
            YozakuraRuntime.blinkManager.setBlinkState(
                    shouldBlock && mode == AUTOBLOCK_BLINK,
                    BlinkModules.AUTO_BLOCK);
        }
    }

    private void startReferenceBlink() {
        YozakuraRuntime.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
    }

    private void stopReferenceBlink() {
        YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
    }

    private long remainingAttackDelay(long now) {
        if (this.manualAttackQueued) {
            return 0L;
        }
        return Math.max(0L, this.attackDelayMS - Math.max(0L, now - this.lastAttackAt));
    }

    private boolean interactBlockAfterAttack(float yaw, float pitch) {
        if (this.attackTarget == null || mc.thePlayer == null || mc.playerController == null) {
            return false;
        }
        MovingObjectPosition hit = RotationUtil.rayTrace(
                this.attackTarget.getBox(), yaw, pitch, 8.0D);
        if (hit == null || hit.hitVec == null) {
            return false;
        }
        EntityLivingBase entity = this.attackTarget.getEntity();
        PacketUtil.sendPacket(new C02PacketUseEntity(entity,
                new Vec3(hit.hitVec.xCoord - this.attackTarget.getX(),
                        hit.hitVec.yCoord - this.attackTarget.getY(),
                        hit.hitVec.zCoord - this.attackTarget.getZ())));
        PacketUtil.sendPacket(new C02PacketUseEntity(entity, Action.INTERACT));
        return this.startBlock();
    }

    private boolean updateLeaderLegitAutoBlockCycle(boolean shouldBlock, boolean canAttackTarget,
                                                    boolean attackReady, long now,
                                                    float yaw, float pitch) {
        if (this.awaitingOwnedBlockPacket || this.awaitingOwnedReleasePacket) {
            return false;
        }
        KillAuraLeaderAutoBlockCycle.LegitStep step = this.leaderAutoBlockCycle.nextLegit(
                shouldBlock && canAttackTarget,
                this.autoBlockController.isBlocking(),
                this.remainingAttackDelay(now),
                attackReady);
        switch (step) {
            case ATTACK_AND_BLOCK:
                if (!attackReady) {
                    return false;
                }
                boolean attacked = this.performAttack(yaw, pitch);
                this.leaderAutoBlockCycle.onLegitInitialAttackResult(attacked);
                if (attacked && !this.autoBlockController.isBlocking()
                        && !this.autoBlockController.isBlockPending()) {
                    this.autoBlockController.requestReferenceBlockStart(now);
                    if (!this.interactBlockAfterAttack(yaw, pitch)) {
                        this.autoBlockController.onBlockStartFailed();
                    }
                }
                return attacked;
            case RELEASE_AND_WAIT:
                this.autoBlockController.requestReferenceRelease(now);
                this.stopBlock();
                return false;
            case RESET:
                this.stopReferenceBlink();
                if (this.autoBlockController.isBlocking()
                        || this.autoBlockController.isBlockPending()
                        || this.autoBlockController.isReleasePending()) {
                    this.stopBlock();
                }
                this.autoBlockController.reset();
                return false;
            case WAIT:
            default:
                return false;
        }
    }

    private boolean updateHypixelWithoutNoSlowCycle(boolean shouldBlock, boolean canAttackTarget,
                                                     boolean attackReady, long now,
                                                     float yaw, float pitch) {
        this.hypixelAnimationBlockPose = shouldBlock;
        if (this.awaitingOwnedBlockPacket || this.awaitingOwnedReleasePacket) {
            return false;
        }
        KillAuraLeaderAutoBlockCycle.HypixelStep step = this.leaderAutoBlockCycle.nextHypixel(
                shouldBlock && canAttackTarget, attackReady);
        switch (step) {
            case FLUSH_ATTACK_AND_BLOCK:
                this.stopReferenceBlink();
                if (!attackReady) {
                    return false;
                }
                boolean attacked = this.performAttack(yaw, pitch);
                this.leaderAutoBlockCycle.onHypixelInitialAttackResult(attacked);
                if (attacked && !this.autoBlockController.isBlocking()
                        && !this.autoBlockController.isBlockPending()) {
                    this.autoBlockController.requestReferenceBlockStart(now);
                    if (!this.interactBlockAfterAttack(yaw, pitch)) {
                        this.autoBlockController.onBlockStartFailed();
                    }
                }
                return attacked;
            case WAIT:
                return false;
            case BLINK_RELEASE_AND_ATTACK:
                this.startReferenceBlink();
                if (this.autoBlockController.isBlocking()) {
                    this.autoBlockController.requestReferenceRelease(now);
                    this.stopBlock();
                }
                return attackReady && this.performAttack(yaw, pitch, true);
            case RESET:
                this.stopReferenceBlink();
                if (this.autoBlockController.isBlocking()
                        || this.autoBlockController.isBlockPending()
                        || this.autoBlockController.isReleasePending()) {
                    this.stopBlock();
                }
                this.autoBlockController.reset();
                return false;
            default:
                return false;
        }
    }

    private boolean updateHypixelLagCycle(boolean shouldBlock, boolean canAttackTarget,
                                         boolean attackReady, long now,
                                         float yaw, float pitch) {
        this.hypixelAnimationBlockPose = shouldBlock;
        if (this.awaitingOwnedBlockPacket || this.awaitingOwnedReleasePacket) {
            return false;
        }
        KillAuraLeaderAutoBlockCycle.HypixelLagStep step = this.leaderAutoBlockCycle.nextHypixelLag(
                shouldBlock && canAttackTarget,
                this.autoBlockController.isBlocking(),
                this.remainingAttackDelay(now),
                attackReady);
        switch (step) {
            case ATTACK_AND_BLOCK:
                if (!attackReady) {
                    return false;
                }
                boolean attacked = this.performAttack(yaw, pitch);
                this.leaderAutoBlockCycle.onHypixelLagInitialAttackResult(attacked);
                if (attacked && !this.autoBlockController.isBlocking()
                        && !this.autoBlockController.isBlockPending()) {
                    this.autoBlockController.requestReferenceBlockStart(now);
                    if (!this.interactBlockAfterAttack(yaw, pitch)) {
                        this.autoBlockController.onBlockStartFailed();
                    }
                }
                this.stopReferenceBlink();
                this.startReferenceBlink();
                return attacked;
            case RELEASE_AND_SUPPRESS_ATTACK:
                if (this.autoBlockController.isBlocking()) {
                    this.autoBlockController.requestReferenceRelease(now);
                    this.stopBlock();
                }
                return false;
            case FLUSH_AND_WAIT:
                this.stopReferenceBlink();
                this.remainingAttackDelay(now);
                return false;
            case RESET:
                this.stopReferenceBlink();
                if (this.autoBlockController.isBlocking()
                        || this.autoBlockController.isBlockPending()
                        || this.autoBlockController.isReleasePending()) {
                    this.stopBlock();
                }
                this.autoBlockController.reset();
                return false;
            case WAIT:
            default:
                return false;
        }
    }

    private boolean updateHypixelAnimationMode(boolean shouldBlock, boolean attackReady,
                                               float yaw, float pitch) {
        this.hypixelAnimationBlockPose = shouldBlock;
        return attackReady && this.performAttack(yaw, pitch);
    }

    private boolean applyAutoBlockAction(KillAuraAutoBlockController.Action action,
                                         int mode, long now, float yaw, float pitch) {
        boolean attacked = false;
        if (action == KillAuraAutoBlockController.Action.START_BLOCK) {
            this.startBlock();
        } else if (action == KillAuraAutoBlockController.Action.RELEASE_FOR_ATTACK
                || action == KillAuraAutoBlockController.Action.RELEASE) {
            this.stopBlock();
        } else if (action == KillAuraAutoBlockController.Action.ATTACK) {
            attacked = this.performAttack(yaw, pitch);
            this.autoBlockController.onAttackResult(attacked, now, this.attackDelayMS);
        }
        return attacked;
    }

    private static long monotonicTimeMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private void recoverOwnedPacketTimeouts(long now) {
        if (this.awaitingOwnedBlockPacket
                && now - this.ownedBlockAwaitStartedAt >= OWNED_PACKET_TIMEOUT_MILLIS) {
            this.awaitingOwnedBlockPacket = false;
            this.ownedBlockAwaitStartedAt = 0L;
            this.ownedBlockWriteId = PacketAcceptedEvent.NO_WRITE_ID;
            this.autoBlockController.onBlockStartFailed();
        }
        if (this.awaitingOwnedReleasePacket
                && now - this.ownedReleaseAwaitStartedAt >= OWNED_PACKET_TIMEOUT_MILLIS) {
            this.awaitingOwnedReleasePacket = false;
            this.ownedReleaseAwaitStartedAt = 0L;
            this.ownedReleaseWriteId = PacketAcceptedEvent.NO_WRITE_ID;
            this.autoBlockController.onBlockStopFailed();
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
                    return (!this.teams.getValue() || !TeamUtil.isSameTeam((EntityPlayer) entityLivingBase)) && (!this.botCheck.getValue() || !AntiBot.isServerBot(entityLivingBase));
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

    private boolean isBoxInBlockRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.autoBlockRange.getValue();
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
        return mc.thePlayer != null
                && this.autoBlockController.isBlocking()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isInLava();
    }

    public boolean canReduceVelocityAttack() {
        return true;
    }

    public boolean isBlocking() {
        return ItemUtil.isHoldingSword()
                && (this.hypixelAnimationBlockPose || this.autoBlockController.shouldRenderBlockPose());
    }

    public boolean isPlayerBlocking() {
        return ItemUtil.isHoldingSword() && this.autoBlockController.isBlocking();
    }

    private boolean shouldCancelBlockInput() {
        return ItemUtil.isHoldingSword() && this.autoBlockController.shouldRenderBlockPose();
    }

    @EventTarget(Priority.HIGHEST)
    public void onRenderTickStart(RenderTickStartEvent event) {
        if (event != null && this.isEnabled() && this.hypixelAnimationBlockPose) {
            this.hypixelAnimationRenderPose.begin(mc.thePlayer);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onRenderTickEnd(RenderTickEndEvent event) {
        this.hypixelAnimationRenderPose.end();
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event == null) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.resetCombatState();
            return;
        }
        if (event.getType() == EventType.POST) {
            this.attackedThisTick = false;
            return;
        }
        if (event.getType() != EventType.PRE) {
            return;
        }

        RotationDebug.setSourceEnabled("KillAura", this.rotationDebug.getValue());
        this.attackedThisTick = false;
        this.updateExpoTarget();

        boolean targetPresent = this.attackTarget != null && this.hasValidTarget();
        boolean shouldBlock = targetPresent && this.canAutoBlock()
                && this.isBoxInBlockRange(this.attackTarget.getBox());
        boolean canAttackTarget = targetPresent && this.canAttack()
                && this.isBoxInSwingRange(this.attackTarget.getBox())
                && this.isBoxInAttackRange(this.attackTarget.getBox());
        long now = monotonicTimeMillis();
        this.recoverOwnedPacketTimeouts(now);
        int autoBlockMode = this.autoBlock.getValue();

        if (this.activeAutoBlockMode != autoBlockMode) {
            this.releaseAutoBlock(true);
            this.activeAutoBlockMode = autoBlockMode;
        }
        this.updateAutoBlockBlink(autoBlockMode, shouldBlock);

        if (!targetPresent) {
            this.clearRotations();
            this.attackRotationTracker.clear();
            this.manualAttackQueued = false;
        }

        boolean silentRotation = this.rotations.getValue() == 2 || this.rotations.getValue() == 3;
        this.attackRotationTracker.drain(this.isEnabled(), silentRotation);
        KillAuraConfirmedRotationTracker.Rotation confirmedRotation =
                this.attackRotationTracker.getConfirmed();
        float yaw = event.getNewYaw();
        float pitch = event.getNewPitch();
        boolean rotationClaimedThisTick = false;
        if (targetPresent && this.isBoxInSwingRange(this.attackTarget.getBox())
                && silentRotation) {
            float[] rotations = this.calculateTargetRotations(event);
            if (event.trySetRotation(rotations[0], rotations[1], 1)) {
                VisualRotationState.publish("KillAura", rotations[0], rotations[1], 1);
                if (this.rotations.getValue() == 3) {
                    YozakuraRuntime.rotationManager.setRotation(rotations[0], rotations[1], 1, true);
                }
                if (this.moveFix.getValue() == 1) {
                    event.setPervRotation(rotations[0], 1);
                } else if (this.moveFix.getValue() == 2 || this.rotations.getValue() == 3) {
                    event.setPervRotation(rotations[0], 1, false);
                }
                this.commitTargetRotations(rotations);
                yaw = rotations[0];
                pitch = rotations[1];
                rotationClaimedThisTick = true;
                this.attackRotationTracker.claim(rotations[0], rotations[1]);
            }
        }
        if (silentRotation && confirmedRotation != null) {
            yaw = confirmedRotation.yaw;
            pitch = confirmedRotation.pitch;
        }
        boolean attackReady = canAttackTarget && this.isAttackReady(now)
                && (!silentRotation || rotationClaimedThisTick && confirmedRotation != null);
        if (autoBlockMode == AUTOBLOCK_LEGIT) {
            this.attackedThisTick = this.updateLeaderLegitAutoBlockCycle(
                    shouldBlock, canAttackTarget, attackReady, now, yaw, pitch);
        } else if (autoBlockMode == AUTOBLOCK_FULL_AB) {
            this.attackedThisTick = this.updateHypixelWithoutNoSlowCycle(
                    shouldBlock, canAttackTarget, attackReady, now, yaw, pitch);
        } else if (autoBlockMode == AUTOBLOCK_BYPASS_ALL) {
            this.attackedThisTick = this.updateHypixelAnimationMode(shouldBlock, attackReady, yaw, pitch);
        } else if (autoBlockMode == AUTOBLOCK_HYPIXEL) {
            this.attackedThisTick = this.updateHypixelLagCycle(
                    shouldBlock, canAttackTarget, attackReady, now, yaw, pitch);
        } else if (shouldBlock) {
            KillAuraAutoBlockController.Action action = this.autoBlockController.update(
                    now, true, canAttackTarget, attackReady,
                    this.getAutoBlockCadence());
            this.attackedThisTick = this.applyAutoBlockAction(
                    action, autoBlockMode, now, yaw, pitch);
        } else {
            KillAuraAutoBlockController.Action action = this.autoBlockController.update(
                    now, false, false, false, this.getAutoBlockCadence());
            this.applyAutoBlockAction(action, autoBlockMode, now, yaw, pitch);
            if (canAttackTarget && attackReady) {
                this.attackedThisTick = this.performAttack(yaw, pitch);
            }
        }

        if (this.attackedThisTick) {
            this.manualAttackQueued = false;
        }
    }

    private float[] calculateTargetRotations(UpdateEvent event) {
        float sourceYaw = this.rotationSmoothActive ? this.smoothYaw : event.getYaw();
        float sourcePitch = this.rotationSmoothActive ? this.smoothPitch : event.getPitch();
        AxisAlignedBB box = this.attackTarget.getBox();
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        KillAuraAimPoint.Point point = KillAuraAimPoint.closest(
                eye.xCoord, eye.yCoord, eye.zCoord,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        double deltaX = point.getX() - eye.xCoord;
        double deltaY = point.getY() - eye.yCoord;
        double deltaZ = point.getZ() - eye.zCoord;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float targetYaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float targetPitch = MathHelper.clamp_float(
                (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance)), -90.0F, 90.0F);
        KillAuraRotationController.Rotation controlled = rotationController.step(
                sourceYaw, sourcePitch, targetYaw, targetPitch,
                this.angleStep.getValue(), this.smoothing.getValue());
        float sensitivity = mc.gameSettings.mouseSensitivity;
        return new float[]{
                KillAuraRotationQuantizer.quantizeYaw(sourceYaw, controlled.getYaw(), sensitivity),
                KillAuraRotationQuantizer.quantizePitch(sourcePitch, controlled.getPitch(), sensitivity)
        };
    }

    private void commitTargetRotations(float[] rotations) {
        this.smoothYaw = rotations[0];
        this.smoothPitch = rotations[1];
        this.rotationSmoothActive = true;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if ((mc.thePlayer == null || mc.theWorld == null) && this.isEnabled()) {
            this.resetCombatState();
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event == null || mc.thePlayer == null) {
            return;
        }
        if (event.isCancelled()) {
            if (this.awaitingOwnedBlockPacket && isUseItemPacket(event.getPacket())) {
                this.awaitingOwnedBlockPacket = false;
                this.ownedBlockAwaitStartedAt = 0L;
                this.autoBlockController.onBlockStartFailed();
            } else if (this.awaitingOwnedReleasePacket && isReleaseUsePacket(event.getPacket())) {
                this.awaitingOwnedReleasePacket = false;
                this.ownedReleaseAwaitStartedAt = 0L;
                this.autoBlockController.onBlockStopFailed();
            }
            return;
        }
        if (event.getPacket() instanceof C09PacketHeldItemChange) {
            this.releaseAutoBlock(true);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPlayerPacketBoundary(PlayerPacketBoundaryEvent event) {
        if (event == null) {
            return;
        }
        this.attackRotationTracker.acceptBoundary(
                event.isPacketAccepted(), event.isRotated(), event.getYaw(), event.getPitch());
    }

    @EventTarget(Priority.LOWEST)
    public void onPacketAccepted(PacketAcceptedEvent event) {
        if (event == null) {
            return;
        }
        if (this.submittingOwnedAttackPackets && isOwnedAttackActionPacket(event.getPacket())) {
            event.requestStrictOriginalPacketOrder();
        }
        if (this.awaitingOwnedBlockPacket && isUseItemPacket(event.getPacket())) {
            this.awaitingOwnedBlockPacket = false;
            this.ownedBlockAwaitStartedAt = 0L;
            this.ownedBlockWriteId = event.getWriteId();
            this.autoBlockController.onBlockStarted();
            return;
        }
        if (this.awaitingOwnedReleasePacket && isReleaseUsePacket(event.getPacket())) {
            this.awaitingOwnedReleasePacket = false;
            this.ownedReleaseAwaitStartedAt = 0L;
            this.ownedReleaseWriteId = event.getWriteId();
            this.autoBlockController.onBlockStopped();
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacketWritten(PacketWriteEvent event) {
        if (event == null || !event.isPacketAccepted()) {
            return;
        }
        if (event.getWriteId() == this.ownedBlockWriteId) {
            this.ownedBlockWriteId = PacketAcceptedEvent.NO_WRITE_ID;
            if (!event.isSuccess()) {
                this.autoBlockController.onBlockWriteFailed();
            }
            return;
        }
        if (event.getWriteId() == this.ownedReleaseWriteId) {
            this.ownedReleaseWriteId = PacketAcceptedEvent.NO_WRITE_ID;
            if (!event.isSuccess()) {
                this.autoBlockController.onReleaseWriteFailed();
            }
        }
    }

    private static boolean isOwnedAttackActionPacket(net.minecraft.network.Packet<?> packet) {
        return packet instanceof C0APacketAnimation
                || packet instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) packet).getAction() == Action.ATTACK;
    }

    private static boolean isUseItemPacket(net.minecraft.network.Packet<?> packet) {
        return packet instanceof C08PacketPlayerBlockPlacement
                && ((C08PacketPlayerBlockPlacement) packet).getPlacedBlockDirection() == 255;
    }

    private static boolean isReleaseUsePacket(net.minecraft.network.Packet<?> packet) {
        return packet instanceof C07PacketPlayerDigging
                && ((C07PacketPlayerDigging) packet).getStatus()
                == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM;
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.shouldCancelBlockInput()) {
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
        if (this.shouldCancelBlockInput()) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.attackTarget != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.shouldCancelBlockInput()) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.attackTarget != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (this.shouldCancelBlockInput()) {
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
        this.attackedThisTick = false;
        this.manualAttackQueued = false;
        this.attackRotationTracker.clear();
        this.hypixelAnimationRenderPose.end();
        this.hypixelAnimationBlockPose = false;
        this.activeAutoBlockMode = AUTOBLOCK_NONE;
        this.awaitingOwnedBlockPacket = false;
        this.awaitingOwnedReleasePacket = false;
        this.ownedBlockAwaitStartedAt = 0L;
        this.ownedReleaseAwaitStartedAt = 0L;
        this.ownedBlockWriteId = PacketAcceptedEvent.NO_WRITE_ID;
        this.ownedReleaseWriteId = PacketAcceptedEvent.NO_WRITE_ID;
        this.autoBlockController.reset();
        this.leaderAutoBlockCycle.reset();
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
