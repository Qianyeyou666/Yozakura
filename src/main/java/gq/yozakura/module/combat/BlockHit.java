package gq.yozakura.module.combat;

import com.google.common.base.CaseFormat;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.AttackEvent;
import gq.yozakura.event.bridge.LeftClickMouseEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.manager.BlinkModules;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.util.module.KeyBindUtil;
import gq.yozakura.value.properties.BooleanProperty;
import gq.yozakura.value.properties.FloatProperty;
import gq.yozakura.value.properties.IntProperty;
import gq.yozakura.value.properties.ModeProperty;
import gq.yozakura.value.properties.PercentProperty;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

public class BlockHit extends Module {
    private static final int MODE_HELPER = 0;
    private static final int MODE_AUTO = 1;
    private static final int MODE_LAG = 2;

    private static final int TIMING_DELAY = 0;
    private static final int TIMING_HURT_TIME = 1;
    private static final int TIMING_SMART = 2;

    private static final int AUTO_SPAM = 0;
    private static final int AUTO_HOLD = 1;

    private static BlockHit instance;

    public final ModeProperty mode =
            new ModeProperty("Mode", MODE_HELPER, new String[]{"Helper", "Auto", "Lag"});

    private final IntProperty helperAttackTick =
            new IntProperty("Attack Tick", 2, 1, 4, () -> mode.getValue() == MODE_HELPER);
    private final IntProperty helperStopTicks =
            new IntProperty("Stop Ticks", 3, 2, 6, () -> mode.getValue() == MODE_HELPER);

    private final ModeProperty autoTiming = new ModeProperty("Auto Timing", TIMING_DELAY,
            new String[]{"Delay", "HurtTime", "Smart"}, () -> mode.getValue() == MODE_AUTO);
    private final ModeProperty autoMode = new ModeProperty("Auto Mode", AUTO_SPAM,
            new String[]{"Spam", "Hold"},
            () -> mode.getValue() == MODE_AUTO && autoTiming.getValue() == TIMING_DELAY);
    private final IntProperty blockDelay = new IntProperty("Block Delay", 100, 0, 1000,
            () -> mode.getValue() == MODE_AUTO && autoTiming.getValue() == TIMING_DELAY);
    private final IntProperty holdTicks = new IntProperty("Hold Ticks", 2, 1, 8,
            () -> mode.getValue() == MODE_AUTO
                    && autoTiming.getValue() == TIMING_DELAY && autoMode.getValue() == AUTO_HOLD);
    private final IntProperty minHurtTime = new IntProperty("Min HurtTime", 8, 1, 10,
            () -> mode.getValue() == MODE_AUTO && autoTiming.getValue() == TIMING_HURT_TIME);
    private final IntProperty maxHurtTime = new IntProperty("Max HurtTime", 10, 1, 10,
            () -> mode.getValue() == MODE_AUTO && autoTiming.getValue() == TIMING_HURT_TIME);
    private final IntProperty smartBlockTicks = new IntProperty("Smart Block Ticks", 2, 1, 6,
            () -> mode.getValue() == MODE_AUTO && autoTiming.getValue() == TIMING_SMART);
    private final PercentProperty chance =
            new PercentProperty("Block Hit Chance", 100, () -> mode.getValue() == MODE_AUTO);
    private final BooleanProperty rangeCheck =
            new BooleanProperty("Range Check", true, () -> mode.getValue() == MODE_AUTO);
    private final FloatProperty range = new FloatProperty("Range", 3.0F, 1.0F, 6.0F,
            () -> mode.getValue() == MODE_AUTO && rangeCheck.getValue());

    private final IntProperty lagStartHurtTime =
            new IntProperty("Start HurtTime", 6, 1, 10, () -> mode.getValue() == MODE_LAG);
    private final IntProperty lagDelayTicks =
            new IntProperty("Delay Packet Ticks", 2, 1, 10, () -> mode.getValue() == MODE_LAG);
    private final IntProperty lagBlockTicks =
            new IntProperty("Block Ticks", 3, 1, 8, () -> mode.getValue() == MODE_LAG);

    private int activeMode = -1;

    private boolean helperActive;
    private int helperTicks;

    private EntityLivingBase autoTarget;
    private boolean autoArmed;
    private boolean autoBlocking;
    private int autoTicks;
    private int autoHoldTicks;
    private long autoBlockAt;

    private boolean lagBlocking;
    private boolean lagBlinking;
    private int lagBlockCounter;
    private int lagBlinkCounter;

    private int lastRecordedAttackTick = Integer.MIN_VALUE;
    private int lastRecordedTargetId = Integer.MIN_VALUE;

    public BlockHit() {
        super("BlockHit", false);
        setCategory(ModuleType.Combat);
        Chinese = "格挡攻击";
        Descript = "Helper, automatic, and lag-assisted block hit";
        About = Descript;
        instance = this;
    }

    @Override
    public void onEnabled() {
        activeMode = mode.getValue();
        resetRuntime(true);
    }

    @Override
    public void onDisabled() {
        resetRuntime(true);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (!isInGame() || mc.thePlayer.isDead || mc.currentScreen != null || !isHoldingSword()) {
            resetRuntime(true);
            return;
        }
        if (activeMode != mode.getValue()) {
            resetRuntime(true);
            activeMode = mode.getValue();
        }

        switch (mode.getValue()) {
            case MODE_HELPER:
                updateHelper();
                break;
            case MODE_AUTO:
                updateAuto();
                break;
            case MODE_LAG:
                updateLag();
                break;
            default:
                resetRuntime(true);
                break;
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (!isEnabled() || !isInGame() || mc.objectMouseOver == null) {
            return;
        }
        recordAttack(mc.objectMouseOver.entityHit);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (isEnabled()) {
            recordAttack(event.getTarget());
        }
    }

    public static void onAttack(Entity entity) {
        if (instance != null && instance.isEnabled()) {
            instance.recordAttack(entity);
        }
    }

    public static boolean isBlockingActive() {
        return instance != null && instance.isEnabled()
                && (instance.helperActive || instance.autoBlocking
                || instance.lagBlocking || instance.lagBlinking);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString())};
    }

    private void updateHelper() {
        if (!helperActive && isAttackBindingDown() && mc.thePlayer.isBlocking()) {
            helperActive = true;
            helperTicks = 0;
            setUsePressed(false);
        }
        if (!helperActive) {
            return;
        }

        helperTicks++;
        if (helperTicks == helperAttackTick.getValue()) {
            KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        if (helperTicks > helperStopTicks.getValue()) {
            restoreUseBinding();
            helperActive = false;
            helperTicks = 0;
        }
    }

    private void updateAuto() {
        if (!autoArmed || autoTarget == null) {
            return;
        }
        if (++autoTicks > 8 || autoTarget.isDead || autoTarget.getHealth() <= 0.0F) {
            resetAuto(true);
            return;
        }
        if (Boolean.TRUE.equals(rangeCheck.getValue())
                && mc.thePlayer.getDistanceToEntity(autoTarget) > range.getValue()) {
            resetAuto(true);
            return;
        }

        switch (autoTiming.getValue()) {
            case TIMING_DELAY:
                updateDelayAuto();
                break;
            case TIMING_HURT_TIME:
                updateHurtTimeAuto();
                break;
            case TIMING_SMART:
                updateSmartAuto();
                break;
            default:
                resetAuto(true);
                break;
        }
    }

    private void updateDelayAuto() {
        if (System.currentTimeMillis() < autoBlockAt) {
            return;
        }
        if (autoMode.getValue() == AUTO_SPAM) {
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
            resetAuto(false);
            return;
        }

        if (!autoBlocking) {
            autoBlocking = true;
            autoHoldTicks = 0;
            setUsePressed(true);
        }
        if (++autoHoldTicks >= holdTicks.getValue()) {
            resetAuto(true);
        }
    }

    private void updateHurtTimeAuto() {
        int min = Math.min(minHurtTime.getValue(), maxHurtTime.getValue());
        int max = Math.max(minHurtTime.getValue(), maxHurtTime.getValue());
        boolean shouldBlock = mc.thePlayer.hurtTime >= min && mc.thePlayer.hurtTime <= max;
        if (shouldBlock) {
            autoBlocking = true;
            setUsePressed(true);
        } else if (autoBlocking) {
            resetAuto(true);
        }
    }

    private void updateSmartAuto() {
        if (autoTarget.hurtTime <= 0) {
            return;
        }
        if (!autoBlocking) {
            autoBlocking = true;
            autoHoldTicks = 0;
            setUsePressed(true);
        }
        if (++autoHoldTicks >= smartBlockTicks.getValue()) {
            resetAuto(true);
        }
    }

    private void updateLag() {
        if (!lagBlocking && !lagBlinking && mc.thePlayer.hurtTime == lagStartHurtTime.getValue()) {
            lagBlocking = true;
            lagBlinking = true;
            lagBlockCounter = 0;
            lagBlinkCounter = 0;
            setUsePressed(true);
            YozakuraRuntime.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        }
        if (lagBlinking && ++lagBlinkCounter > lagDelayTicks.getValue()) {
            stopLagBlink();
        }
        if (lagBlocking && ++lagBlockCounter > lagBlockTicks.getValue()) {
            lagBlocking = false;
            lagBlockCounter = 0;
            restoreUseBinding();
        }
    }

    private void recordAttack(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || !isInGame() || !isHoldingSword()) {
            return;
        }
        EntityLivingBase target = (EntityLivingBase) entity;
        int tick = mc.thePlayer.ticksExisted;
        if (lastRecordedAttackTick == tick && lastRecordedTargetId == target.getEntityId()) {
            return;
        }
        lastRecordedAttackTick = tick;
        lastRecordedTargetId = target.getEntityId();

        if (mode.getValue() != MODE_AUTO || Math.random() * 100.0D >= chance.getValue()) {
            return;
        }
        resetAuto(true);
        autoTarget = target;
        autoArmed = true;
        autoBlockAt = System.currentTimeMillis() + blockDelay.getValue();
    }

    private void resetRuntime(boolean restoreUse) {
        helperActive = false;
        helperTicks = 0;
        resetAuto(restoreUse);
        lagBlocking = false;
        lagBlockCounter = 0;
        stopLagBlink();
        if (restoreUse) {
            restoreUseBinding();
        }
        lastRecordedAttackTick = Integer.MIN_VALUE;
        lastRecordedTargetId = Integer.MIN_VALUE;
    }

    private void resetAuto(boolean restoreUse) {
        if (restoreUse && autoBlocking) {
            restoreUseBinding();
        }
        autoTarget = null;
        autoArmed = false;
        autoBlocking = false;
        autoTicks = 0;
        autoHoldTicks = 0;
        autoBlockAt = 0L;
    }

    private void stopLagBlink() {
        if (lagBlinking) {
            YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        }
        lagBlinking = false;
        lagBlinkCounter = 0;
    }

    private boolean isAttackBindingDown() {
        return KeyBindUtil.isBindingDown(mc.gameSettings.keyBindAttack);
    }

    private void setUsePressed(boolean pressed) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), pressed);
    }

    private void restoreUseBinding() {
        if (mc.gameSettings != null && mc.gameSettings.keyBindUseItem != null) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
    }

    private boolean isHoldingSword() {
        if (!isInGame()) {
            return false;
        }
        ItemStack stack = mc.thePlayer.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemSword;
    }
}
