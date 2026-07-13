package gq.yozakura.module.combat;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.combat.aim.AimAssistAimPoint;
import gq.yozakura.module.combat.aim.AimAssistController;
import gq.yozakura.module.combat.aim.AimAssistTargetSelector;
import gq.yozakura.manager.RotationExitState;
import gq.yozakura.util.minecraft.RotationUtil;
import gq.yozakura.util.module.KeyBindUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;
import java.util.Random;

public class Aimbot extends Module {
    public enum AimMode {
        NORMAL(false, AimAssistController.Profile.REGULAR),
        SILENT(true, AimAssistController.Profile.REGULAR),
        BLATANT(false, AimAssistController.Profile.BLATANT),
        SILENT_BLATANT(true, AimAssistController.Profile.BLATANT);

        private final boolean silent;
        private final AimAssistController.Profile profile;

        AimMode(boolean silent, AimAssistController.Profile profile) {
            this.silent = silent;
            this.profile = profile;
        }

        public boolean isSilent() {
            return silent;
        }

        public boolean isBlatant() {
            return profile == AimAssistController.Profile.BLATANT;
        }

        public AimAssistController.Profile getProfile() {
            return profile;
        }
    }

    public enum SortMode {
        HEALTH,
        ANGLE,
        HURT_TIME,
        DISTANCE
    }

    private static final int SILENT_ROTATION_PRIORITY = 1;
    private static final int SILENT_RETURN_TICKS = 3;

    private final Mode<AimMode> mode = new Mode<AimMode>("Mode", "Mode", AimMode.values(), AimMode.NORMAL);
    private final Numbers<Double> speed = new Numbers<Double>("Horizontal Speed", "Speed", 10.0, 1.0, 180.0, 1.0);
    private final Numbers<Double> verticalSpeed =
            new Numbers<Double>("Vertical Speed", "VerticalSpeed", 5.0, 1.0, 90.0, 1.0);
    private final Numbers<Double> reactionDelay =
            new Numbers<Double>("Reaction Delay", "ReactionDelay", 30.0, 10.0, 200.0, 1.0);
    private final Numbers<Double> updateRate =
            new Numbers<Double>("Update Rate", "UpdateRate", 20.0, 0.1, 20.0, 0.1);
    private final Numbers<Double> multipointHorizontal =
            new Numbers<Double>("Multipoint horizontal", "MultipointHorizontal", 18.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> multipointVertical =
            new Numbers<Double>("Multipoint vertical", "MultipointVertical", 35.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> randomization =
            new Numbers<Double>("Randomization", "Randomization", 20.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> fov = new Numbers<Double>("FOV", "FOV", 90.0, 15.0, 360.0, 1.0);
    private final Numbers<Double> range = new Numbers<Double>("Range", "Range", 4.5, 0.0, 6.0, 0.1);
    private final Mode<SortMode> sort = new Mode<SortMode>("Sort", "Sort", SortMode.values(), SortMode.ANGLE);
    private final Option<Boolean> ignoreBehindWalls =
            new Option<Boolean>("Ignore behind walls", "IgnoreBehindWalls", false);
    private final Option<Boolean> ignoreBehindEntities =
            new Option<Boolean>("Ignore behind entities", "IgnoreBehindEntities", false);
    private final Option<Boolean> aimInvis = new Option<Boolean>("Aim invis", "AimInvis", false);
    private final Option<Boolean> clickAim = new Option<Boolean>("Require mouse", "RequireMouse", true);
    private final Option<Boolean> requireHover =
            new Option<Boolean>("Require target", "RequireTarget", false);
    private final Option<Boolean> aimVertical =
            new Option<Boolean>("Vertical aim", "VerticalAim", true);
    private final Option<Boolean> ignoreTeammates =
            new Option<Boolean>("Ignore teammates", "IgnoreTeammates", true);
    private final Option<Boolean> botCheck = new Option<Boolean>("Bot Check", "BotCheck", true);
    private final Option<Boolean> targetPlayers = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> targetAnimals = new Option<Boolean>("Animals", "Animals", false);
    private final Option<Boolean> targetMobs = new Option<Boolean>("Mobs", "Mobs", false);
    private final Option<Boolean> stopWhenBreaking =
            new Option<Boolean>("Stop when breaking", "StopWhenBreaking", false);
    private final Option<Boolean> keepMoveDirection =
            new Option<Boolean>("Keep move direction", "KeepMoveDirection", true);
    private final Numbers<Double> hoverDelay =
            new Numbers<Double>("Hover delay", "HoverDelay", 100.0, 0.0, 500.0, 10.0);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Weapon only", "WeaponOnly", false);

    public EntityLivingBase target;

    private final Random random = new Random();
    private final AimAssistTargetSelector targetSelector = new AimAssistTargetSelector();
    private final AimAssistController viewController = new AimAssistController();
    private final AimAssistController silentController = new AimAssistController();

    private AimAssistAimPoint aimPoint;
    private AimMode lastMode;
    private int activeTargetId = -1;
    private long miningStartTime = -1L;
    private long nextTargetScanAt;
    private long nextNoiseAt;
    private long lastRenderNanos;
    private long lastTargetUpdateMillis;
    private float noiseYaw;
    private float noisePitch;
    private float desiredNoiseYaw;
    private float desiredNoisePitch;
    private boolean silentRotationPublished;
    private boolean silentReturning;
    private int silentReturnTicks;
    private float lastServerYaw;
    private float lastServerPitch;

    public Aimbot() {
        super("AimAssist", Keyboard.KEY_NONE, ModuleType.Combat, "Mouse-like first-person aim assistance");
        this.addValues(mode, speed, verticalSpeed, reactionDelay, updateRate, multipointHorizontal,
                multipointVertical, randomization, fov, range, sort, ignoreBehindWalls, ignoreBehindEntities,
                aimInvis, clickAim, requireHover, aimVertical, ignoreTeammates, botCheck, targetPlayers,
                targetAnimals, targetMobs, stopWhenBreaking, keepMoveDirection, hoverDelay, weaponOnly);
        Chinese = "瞄准辅助";
    }

    @Override
    public void enable() {
        RotationExitState.clearSource("AimAssist");
        resetAllState();
        lastMode = mode.getValue();
    }

    @Override
    public void disable() {
        scheduleSilentRotationReset();
        resetAllState();
        super.disable();
    }

    @EventTarget(Priority.LOW)
    public void onBridgeTick(gq.yozakura.event.bridge.TickEvent event) {
        if (!getState() || event.getType() != EventType.PRE) {
            return;
        }

        handleModeChange();
        if (!conditionsMet()) {
            clearTargetState(mode.getValue().isSilent());
            return;
        }

        refreshTarget(System.currentTimeMillis());
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!getState() || event.phase != TickEvent.Phase.START || mode.getValue().isSilent() || !hasActiveTarget()) {
            return;
        }
        if (!conditionsMet()) {
            clearTargetState(false);
            return;
        }

        long nowMillis = System.currentTimeMillis();
        if (!viewController.isReady(nowMillis)) {
            return;
        }
        long nowNanos = System.nanoTime();
        float deltaSeconds = lastRenderNanos == 0L
                ? 1.0F / 60.0F
                : (nowNanos - lastRenderNanos) / 1000000000.0F;
        lastRenderNanos = nowNanos;
        updateViewTarget(nowMillis, event.renderTickTime, deltaSeconds);

        AimAssistController.Rotation rotation = viewController.step(mc.thePlayer.rotationYaw,
                mc.thePlayer.rotationPitch, deltaSeconds, nowMillis, createControllerSettings());
        if (Math.abs(rotation.getYawDelta()) > 0.00001F || Math.abs(rotation.getPitchDelta()) > 0.00001F) {
            mc.thePlayer.setAngles(rotation.getYawDelta() / 0.15F, -rotation.getPitchDelta() / 0.15F);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!getState() || event.getType() != EventType.PRE) {
            return;
        }
        if (silentReturning) {
            publishSilentReturn(event);
            return;
        }
        if (!mode.getValue().isSilent() || !hasActiveTarget()) {
            return;
        }
        if (!conditionsMet()) {
            clearTargetState(true);
            publishSilentReturn(event);
            return;
        }
        if (event.isRotated()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!silentController.isReady(now)) {
            return;
        }
        AimAssistController.Rotation rotation = silentController.step(
                silentRotationPublished ? lastServerYaw : event.getYaw(),
                silentRotationPublished ? lastServerPitch : event.getPitch(),
                1.0F / 20.0F, now, createControllerSettings());
        event.setRotation(rotation.getYaw(), rotation.getPitch(), SILENT_ROTATION_PRIORITY);
        if (Boolean.TRUE.equals(keepMoveDirection.getValue())) {
            event.setPervRotation(rotation.getYaw(), SILENT_ROTATION_PRIORITY);
        }
        lastServerYaw = rotation.getYaw();
        lastServerPitch = rotation.getPitch();
        silentRotationPublished = true;
    }

    private void handleModeChange() {
        AimMode currentMode = mode.getValue();
        if (lastMode == null) {
            lastMode = currentMode;
            return;
        }
        if (lastMode == currentMode) {
            return;
        }
        if (lastMode.isSilent() && !currentMode.isSilent()) {
            finishSilentReturn();
        }
        clearTargetState(false);
        nextTargetScanAt = 0L;
        lastMode = currentMode;
    }

    private void refreshTarget(long now) {
        if (target != null && !isTargetStillEligible()) {
            clearTargetState(mode.getValue().isSilent());
            nextTargetScanAt = now;
        }
        if (now < nextTargetScanAt) {
            return;
        }
        nextTargetScanAt = now + sampleIntervalMillis();
        AimAssistTargetSelector.Selection selection = targetSelector.select(mc, mc.thePlayer.rotationYaw,
                createSelectorSettings(), now);
        if (selection == null) {
            clearTargetState(mode.getValue().isSilent());
            return;
        }

        EntityLivingBase nextTarget = selection.getTarget();
        if (nextTarget.getEntityId() != activeTargetId) {
            activeTargetId = nextTarget.getEntityId();
            target = nextTarget;
            aimPoint = selection.getAimPoint();
            resetNoise();
            long delay = Math.max(0L, reactionDelay.getValue().longValue());
            viewController.acquireTarget(activeTargetId, now, delay, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            silentController.acquireTarget(activeTargetId, now, delay, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            lastRenderNanos = 0L;
        } else {
            target = nextTarget;
            aimPoint = selection.getAimPoint();
        }

        updateTickTarget(now);
    }

    private void updateTickTarget(long now) {
        float[] rotations = resolveTargetRotation(1.0F);
        if (rotations == null) {
            return;
        }
        float deltaSeconds = lastTargetUpdateMillis == 0L
                ? 1.0F / 20.0F
                : (now - lastTargetUpdateMillis) / 1000.0F;
        lastTargetUpdateMillis = now;
        advanceNoise(now, deltaSeconds);
        float targetYaw = rotations[0] + noiseYaw;
        float targetPitch = MathHelper.clamp_float(rotations[1] + noisePitch, -90.0F, 90.0F);
        float blend = temporalBlend(mode.getValue().isBlatant() ? 15.0F : 9.0F, deltaSeconds);
        viewController.setTargetRotation(targetYaw, targetPitch, blend);
        silentController.setTargetRotation(targetYaw, targetPitch, blend);
    }

    private void updateViewTarget(long now, float partialTicks, float deltaSeconds) {
        float[] rotations = resolveTargetRotation(partialTicks);
        if (rotations == null) {
            return;
        }
        advanceNoise(now, deltaSeconds);
        float targetYaw = rotations[0] + noiseYaw;
        float targetPitch = MathHelper.clamp_float(rotations[1] + noisePitch, -90.0F, 90.0F);
        float blend = temporalBlend(mode.getValue().isBlatant() ? 15.0F : 9.0F, deltaSeconds);
        viewController.setTargetRotation(targetYaw, targetPitch, blend);
    }

    private float[] resolveTargetRotation(float partialTicks) {
        if (!hasActiveTarget()) {
            return null;
        }
        Vec3 point = aimPoint.resolve(target, partialTicks);
        return RotationUtil.getRotationsTo(mc, point.xCoord, point.yCoord, point.zCoord);
    }

    private void advanceNoise(long now, float deltaSeconds) {
        if (now >= nextNoiseAt) {
            float strength = MathHelper.clamp_float(randomization.getValue().floatValue() / 100.0F, 0.0F, 1.0F);
            desiredNoiseYaw = randomOffset(0.80F * strength);
            desiredNoisePitch = randomOffset(0.45F * strength);
            nextNoiseAt = now + 140L + random.nextInt(121);
        }
        float blend = temporalBlend(mode.getValue().isBlatant() ? 10.0F : 6.0F, deltaSeconds);
        noiseYaw += (desiredNoiseYaw - noiseYaw) * blend;
        noisePitch += (desiredNoisePitch - noisePitch) * blend;
    }

    private float temporalBlend(float response, float deltaSeconds) {
        float clampedDelta = MathHelper.clamp_float(deltaSeconds, 0.0001F, 0.25F);
        return 1.0F - (float) Math.exp(-response * clampedDelta);
    }

    private float randomOffset(float maximum) {
        return (random.nextFloat() * 2.0F - 1.0F) * maximum;
    }

    private void clearTargetState(boolean returnSilentRotation) {
        if (returnSilentRotation) {
            beginSilentReturn();
        }
        target = null;
        aimPoint = null;
        activeTargetId = -1;
        targetSelector.clear();
        viewController.releaseTarget();
        silentController.releaseTarget();
        lastRenderNanos = 0L;
        lastTargetUpdateMillis = 0L;
        resetNoise();
    }

    private void beginSilentReturn() {
        if (!silentRotationPublished || silentReturning) {
            return;
        }
        silentReturning = true;
        silentReturnTicks = SILENT_RETURN_TICKS;
    }

    private void publishSilentReturn(UpdateEvent event) {
        if (!silentReturning) {
            return;
        }
        if (!isInGame()) {
            finishSilentReturn();
            return;
        }
        if (event.isRotated()) {
            return;
        }

        float cameraYaw = mc.thePlayer.rotationYaw;
        float cameraPitch = mc.thePlayer.rotationPitch;
        float progress = silentReturnTicks <= 1 ? 1.0F : 0.62F;
        lastServerYaw += MathHelper.wrapAngleTo180_float(cameraYaw - lastServerYaw) * progress;
        lastServerPitch = MathHelper.clamp_float(lastServerPitch + (cameraPitch - lastServerPitch) * progress,
                -90.0F, 90.0F);
        event.setRotation(lastServerYaw, lastServerPitch, SILENT_ROTATION_PRIORITY);
        if (Boolean.TRUE.equals(keepMoveDirection.getValue())) {
            event.setPervRotation(lastServerYaw, SILENT_ROTATION_PRIORITY);
        }
        silentReturnTicks--;
        if (silentReturnTicks <= 0) {
            finishSilentReturn();
        }
    }

    private void finishSilentReturn() {
        silentReturning = false;
        silentReturnTicks = 0;
        silentRotationPublished = false;
        lastServerYaw = 0.0F;
        lastServerPitch = 0.0F;
    }

    private void scheduleSilentRotationReset() {
        if (!silentRotationPublished || !isInGame()) {
            return;
        }
        RotationExitState.request("AimAssist", lastServerYaw, lastServerPitch, SILENT_ROTATION_PRIORITY,
                180.0F, 0.0F, SILENT_RETURN_TICKS, Boolean.TRUE.equals(keepMoveDirection.getValue()), false);
    }

    private boolean conditionsMet() {
        if (!isInGame() || mc.currentScreen != null || (!mc.inGameHasFocus && !isAttackHeld())) {
            return false;
        }
        gq.yozakura.module.runtime.Module killAuraMod =
                gq.yozakura.runtime.YozakuraRuntime.moduleManager.modules.get(KillAura.class);
        if (killAuraMod != null && killAuraMod.isEnabled() && KillAura.target != null) {
            return false;
        }
        if (Boolean.TRUE.equals(weaponOnly.getValue()) && !CombatUtil.isHoldingWeapon()) {
            return false;
        }
        if (Boolean.TRUE.equals(clickAim.getValue()) && !isAttackHeld()) {
            return false;
        }
        if (Boolean.TRUE.equals(requireHover.getValue()) && !isHoveringLivingTarget()) {
            return false;
        }
        if (Boolean.TRUE.equals(stopWhenBreaking.getValue()) && isMining()) {
            if (miningStartTime == -1L) {
                miningStartTime = System.currentTimeMillis();
            }
            return System.currentTimeMillis() - miningStartTime < hoverDelay.getValue().longValue();
        }
        miningStartTime = -1L;
        return true;
    }

    private boolean isMining() {
        if (MinecraftAccessor.isHittingBlock(mc.playerController)) {
            return true;
        }
        return isAttackHeld() && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private boolean isHoveringLivingTarget() {
        return mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityLivingBase
                && !(mc.objectMouseOver.entityHit instanceof EntityArmorStand);
    }

    private boolean isAttackHeld() {
        if (mc.gameSettings == null) {
            return isMouseButtonDown(0);
        }
        return KeyBindUtil.isBindingDown(mc.gameSettings.keyBindAttack) || isMouseButtonDown(0);
    }

    private boolean isMouseButtonDown(int button) {
        try {
            return Mouse.isCreated() && Mouse.isButtonDown(button);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasActiveTarget() {
        return target != null && aimPoint != null && !target.isDead && target.deathTime == 0;
    }

    private boolean isTargetStillEligible() {
        return hasActiveTarget() && targetSelector.isStillEligible(mc, target, mc.thePlayer.rotationYaw,
                createSelectorSettings());
    }

    private long sampleIntervalMillis() {
        double rate = Math.max(0.1D, updateRate.getValue());
        return Math.max(1L, Math.round(1000.0D / rate));
    }

    private AimAssistTargetSelector.Settings createSelectorSettings() {
        return new AimAssistTargetSelector.Settings(range.getValue(), fov.getValue(), toSelectorSort(sort.getValue()),
                Boolean.TRUE.equals(ignoreBehindWalls.getValue()),
                Boolean.TRUE.equals(ignoreBehindEntities.getValue()), Boolean.TRUE.equals(aimInvis.getValue()),
                Boolean.TRUE.equals(ignoreTeammates.getValue()), Boolean.TRUE.equals(botCheck.getValue()),
                Boolean.TRUE.equals(targetPlayers.getValue()), Boolean.TRUE.equals(targetAnimals.getValue()),
                Boolean.TRUE.equals(targetMobs.getValue()), multipointHorizontal.getValue() / 100.0D,
                multipointVertical.getValue() / 100.0D);
    }

    private AimAssistTargetSelector.Sort toSelectorSort(SortMode value) {
        switch (value) {
            case HEALTH:
                return AimAssistTargetSelector.Sort.HEALTH;
            case HURT_TIME:
                return AimAssistTargetSelector.Sort.HURT_TIME;
            case DISTANCE:
                return AimAssistTargetSelector.Sort.DISTANCE;
            case ANGLE:
            default:
                return AimAssistTargetSelector.Sort.ANGLE;
        }
    }

    private AimAssistController.Settings createControllerSettings() {
        float yawSpeed = MathHelper.clamp_float(22.0F + speed.getValue().floatValue() * 4.8F, 25.0F, 420.0F);
        float pitchSpeed = MathHelper.clamp_float(14.0F + verticalSpeed.getValue().floatValue() * 3.4F,
                16.0F, 260.0F);
        AimAssistController.Profile profile = mode.getValue().getProfile();
        if (profile == AimAssistController.Profile.BLATANT) {
            yawSpeed = Math.min(480.0F, yawSpeed * 1.15F);
            pitchSpeed = Math.min(300.0F, pitchSpeed * 1.15F);
        }
        float sensitivity = mc.gameSettings == null ? 0.5F : mc.gameSettings.mouseSensitivity;
        return new AimAssistController.Settings(yawSpeed, pitchSpeed, sensitivity,
                Boolean.TRUE.equals(aimVertical.getValue()), profile);
    }

    private void resetNoise() {
        noiseYaw = 0.0F;
        noisePitch = 0.0F;
        desiredNoiseYaw = 0.0F;
        desiredNoisePitch = 0.0F;
        nextNoiseAt = 0L;
    }

    private void resetAllState() {
        target = null;
        aimPoint = null;
        activeTargetId = -1;
        miningStartTime = -1L;
        nextTargetScanAt = 0L;
        lastRenderNanos = 0L;
        lastTargetUpdateMillis = 0L;
        targetSelector.clear();
        viewController.releaseTarget();
        silentController.releaseTarget();
        resetNoise();
        silentRotationPublished = false;
        silentReturning = false;
        silentReturnTicks = 0;
        lastServerYaw = 0.0F;
        lastServerPitch = 0.0F;
    }

    public static void assistFaceEntity(Entity entity, float yaw, float pitch) {
        CombatUtil.faceEntity(entity, yaw, pitch, pitch <= 0.0F, 0.0F);
    }

    public static float updateRotation(float current, float target, float maxTurn) {
        return CombatUtil.updateRotation(current, target, maxTurn);
    }

    public static List<Entity> getEntityList() {
        return mc.theWorld == null ? null : mc.theWorld.getLoadedEntityList();
    }
}
