package gq.yozakura.module.combat;

import com.google.common.base.CaseFormat;
import gq.yozakura.event.bridge.LeftClickMouseEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.manager.BlinkModules;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.util.module.KeyBindUtil;
import gq.yozakura.util.module.RotationUtil;
import gq.yozakura.value.properties.ModeProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;

/**
 * Vape-style block hit driven exclusively through Minecraft's normal use-key
 * pipeline. The module never creates, reorders, or cancels combat/use packets.
 */
public class BlockHit extends Module {
    private static final int MODE_MANUAL = 0;
    private static final int MODE_PREDICT = 1;
    private static final int MODE_AUTO = 2;
    private static final int MODE_LAG = 3;

    private static final long MANUAL_USE_MS = 50L;
    private static final long PREDICT_USE_MS = 50L;
    private static final long AUTO_USE_MS = 50L;

    private static BlockHit instance;

    private final BlockHitSettings settings = new BlockHitSettings();
    public final ModeProperty mode = settings.mode;
    private final BlockHitController controller = new BlockHitController();
    private final BlockHitUseKeyGuard useKeyGuard = new BlockHitUseKeyGuard(mc);

    private int activeMode = -1;
    private volatile EntityLivingBase autoTarget;
    private volatile boolean lagBlinking;
    private volatile long lagReleaseAt;

    public BlockHit() {
        super("BlockHit", false);
        addValues(settings.values());
        setCategory(ModuleType.Combat);
        Chinese = "格挡攻击";
        Descript = "Vanilla-input sword block hit";
        About = Descript;
        instance = this;
    }

    @Override
    public void onEnabled() {
        activeMode = mode.getValue();
        resetState();
    }

    @Override
    public void onDisabled() {
        stopLagBlink();
        resetState();
    }

    /**
     * Manual matches Vape's input hook: a real left-button press briefly holds
     * use, letting vanilla choose the normal C08/C07 timing.
     */
    @EventTarget(Priority.LOWEST)
    public void onAttackInput(LeftClickMouseEvent event) {
        if (event == null || event.isCancelled() || !isEnabled() || mode.getValue() != MODE_MANUAL
                || !isGameplayReady() || isManualBlocking() || !passesManualChance()) {
            return;
        }
        beginUse(BlockHitController.UseOwner.MANUAL, System.currentTimeMillis(), MANUAL_USE_MS);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event == null || event.getType() != EventType.PRE) {
            return;
        }
        if (!isInGame() || mc.thePlayer.isDead) {
            clearDisconnectedState();
            return;
        }

        if (activeMode != mode.getValue()) {
            stopLagBlink();
            resetState();
            activeMode = mode.getValue();
        }

        long now = System.currentTimeMillis();
        if (!isGameplayReady()) {
            controller.reset();
            autoTarget = null;
            stopLagBlink();
            syncUseKey(now);
            return;
        }

        switch (mode.getValue()) {
            case MODE_PREDICT:
                updatePredict(now);
                break;
            case MODE_AUTO:
                updateAuto(now);
                break;
            case MODE_LAG:
                updateLag(now);
                break;
            case MODE_MANUAL:
            default:
                break;
        }
        syncUseKey(now);
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event == null || event.getType() != EventType.SEND || event.isCancelled()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof C03PacketPlayer) {
            controller.advanceMovementEpoch();
            return;
        }
        if (packet instanceof C02PacketUseEntity) {
            handleAttackPacket((C02PacketUseEntity) packet);
            return;
        }
        if (packet instanceof C07PacketPlayerDigging
                && ((C07PacketPlayerDigging) packet).getStatus()
                == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            handleVanillaRelease();
        }
    }

    public static boolean isBlockingActive() {
        return instance != null && instance.isEnabled() && instance.useKeyGuard.isHoldingUse()
                && instance.controller.isUseActive(System.currentTimeMillis());
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString())};
    }

    private void handleAttackPacket(C02PacketUseEntity packet) {
        if (packet.getAction() != C02PacketUseEntity.Action.ATTACK || mode.getValue() != MODE_AUTO
                || !isGameplayReady()) {
            return;
        }
        Entity entity = packet.getEntityFromWorld(mc.theWorld);
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase target = (EntityLivingBase) entity;
        if (!isValidTarget(target) || shouldIgnoreManualBlock()) {
            return;
        }

        autoTarget = target;
        controller.armAuto();
    }

    private void handleVanillaRelease() {
        if (mode.getValue() != MODE_LAG || !isGameplayReady() || lagBlinking) {
            return;
        }
        if (YozakuraRuntime.blinkManager.tryAcquire(BlinkModules.BLOCK_HIT)) {
            lagBlinking = true;
            lagReleaseAt = System.currentTimeMillis() + Math.max(50L, settings.lagDelay.getValue().longValue());
        }
    }

    private void updatePredict(long now) {
        if (Boolean.TRUE.equals(settings.requireMouseDown.getValue()) && !isPhysicalAttackDown()) {
            return;
        }
        if (shouldIgnoreManualBlock()) {
            return;
        }
        EntityLivingBase target = findPredictTarget();
        if (target != null && target.swingProgress > 0.0F) {
            beginUse(BlockHitController.UseOwner.PREDICT, now, PREDICT_USE_MS);
        }
    }

    private void updateAuto(long now) {
        if (!controller.isAutoReadyAfterMovement()) {
            return;
        }

        controller.consumeAutoArm();
        EntityLivingBase target = autoTarget;
        autoTarget = null;
        if (target == null || !isValidTarget(target) || shouldIgnoreManualBlock()) {
            return;
        }
        if (Boolean.TRUE.equals(settings.requireMouseDown.getValue()) && !isPhysicalAttackDown()) {
            return;
        }
        beginUse(BlockHitController.UseOwner.AUTO, now, AUTO_USE_MS);
    }

    private void updateLag(long now) {
        if (lagBlinking && now >= lagReleaseAt) {
            stopLagBlink();
        }
    }

    private void beginUse(BlockHitController.UseOwner owner, long now, long durationMs) {
        if (controller.beginUse(owner, now, durationMs)) {
            useKeyGuard.holdUse();
        }
    }

    private void syncUseKey(long now) {
        if (controller.isUseActive(now)) {
            useKeyGuard.holdUse();
        } else {
            useKeyGuard.releaseUse();
        }
    }

    private void resetState() {
        controller.reset();
        autoTarget = null;
        useKeyGuard.reset();
    }

    private void clearDisconnectedState() {
        if (YozakuraRuntime.blinkManager.owns(BlinkModules.BLOCK_HIT)) {
            YozakuraRuntime.blinkManager.discard(BlinkModules.BLOCK_HIT);
        }
        lagBlinking = false;
        lagReleaseAt = 0L;
        resetState();
    }

    private void stopLagBlink() {
        if (lagBlinking && YozakuraRuntime.blinkManager.owns(BlinkModules.BLOCK_HIT)) {
            YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.BLOCK_HIT);
        }
        lagBlinking = false;
        lagReleaseAt = 0L;
    }

    private boolean isGameplayReady() {
        return isInGame() && mc.currentScreen == null && mc.inGameHasFocus && isHoldingSword();
    }

    private boolean shouldIgnoreManualBlock() {
        return Boolean.TRUE.equals(settings.ignoreManualBlock.getValue()) && isManualBlocking();
    }

    private boolean isManualBlocking() {
        return mc.thePlayer != null && mc.thePlayer.isBlocking()
                && !controller.isUseActive(System.currentTimeMillis());
    }

    private boolean passesManualChance() {
        int chance = Math.max(0, Math.min(100, settings.chance.getValue()));
        return chance >= 100 || (chance > 0 && Math.random() * 100.0D < chance);
    }

    private boolean isValidTarget(EntityLivingBase target) {
        if (target == null || target == mc.thePlayer || target.isDead || target.getHealth() <= 0.0F) {
            return false;
        }
        return mc.thePlayer.getDistanceToEntity(target) <= settings.distance.getValue()
                && RotationUtil.angleToEntity(target) <= settings.angle.getValue();
    }

    private EntityLivingBase findPredictTarget() {
        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
            EntityLivingBase direct = (EntityLivingBase) mc.objectMouseOver.entityHit;
            if (isValidTarget(direct)) {
                return direct;
            }
        }
        if (mc.theWorld == null) {
            return null;
        }
        EntityLivingBase closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase candidate = (EntityLivingBase) object;
            if (!isValidTarget(candidate)) {
                continue;
            }
            double distance = mc.thePlayer.getDistanceSqToEntity(candidate);
            if (distance < closestDistance) {
                closest = candidate;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private boolean isPhysicalAttackDown() {
        return mc.gameSettings != null && mc.gameSettings.keyBindAttack != null
                && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
    }

    private boolean isHoldingSword() {
        if (!isInGame()) {
            return false;
        }
        ItemStack stack = mc.thePlayer.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemSword;
    }
}
