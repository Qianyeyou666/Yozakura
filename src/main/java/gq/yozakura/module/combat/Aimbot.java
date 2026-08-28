package gq.yozakura.module.combat;

import gq.yozakura.bridge.ForgeEnvironment;
import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bridge.RenderTickStartEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.combat.aim.AimAssistBodyAnchor;
import gq.yozakura.module.combat.aim.AimAssistController;
import gq.yozakura.module.combat.aim.AimAssistKnockbackWindow;
import gq.yozakura.module.combat.aim.AimAssistLockOnGeometry;
import gq.yozakura.module.combat.aim.AimAssistLockOnRetention;
import gq.yozakura.module.combat.aim.AimAssistLockOnState;
import gq.yozakura.module.combat.aim.AimAssistPitchHoldHysteresis;
import gq.yozakura.module.combat.aim.AimAssistTargetSelector;
import gq.yozakura.module.combat.aim.AimAssistVerticalPolicy;
import gq.yozakura.module.combat.aim.AimAssistVerticalStability;
import gq.yozakura.util.module.KeyBindUtil;
import gq.yozakura.util.module.RotationUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;

public class Aimbot extends Module {
    public enum AimMode {
        ADAPTIVE,
        LOCK_ON;

        public boolean isLockOn() {
            return this == LOCK_ON;
        }
    }

    public enum SortMode {
        HEALTH,
        ANGLE,
        HURT_TIME,
        DISTANCE
    }

    private static final double PITCH_HOLD_HORIZONTAL_INSET = 0.06D;
    private static final double PITCH_HOLD_VERTICAL_INSET = 0.08D;
    private static final double PITCH_HOLD_ENTER_HORIZONTAL_INSET = 0.10D;
    private static final double PITCH_HOLD_ENTER_VERTICAL_INSET = 0.14D;
    private static final double TARGET_VERTICAL_BLEND = 0.55D;
    private static final double ROTATION_EPSILON = 0.00001D;

    private final Mode<AimMode> mode = new Mode<AimMode>("Mode", "Mode", AimMode.values(), AimMode.ADAPTIVE);
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
            new Option<Boolean>("Teams", "IgnoreTeammates", true);
    private final Option<Boolean> botCheck = new Option<Boolean>("Bot Check", "BotCheck", true);
    private final Option<Boolean> targetPlayers = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> targetAnimals = new Option<Boolean>("Animals", "Animals", false);
    private final Option<Boolean> targetMobs = new Option<Boolean>("Mobs", "Mobs", false);
    private final Option<Boolean> stopWhenBreaking =
            new Option<Boolean>("Stop when breaking", "StopWhenBreaking", false);
    private final Numbers<Double> hoverDelay =
            new Numbers<Double>("Hover delay", "HoverDelay", 100.0, 0.0, 500.0, 10.0);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Weapon only", "WeaponOnly", false);

    public EntityLivingBase target;

    private final AimAssistTargetSelector targetSelector = new AimAssistTargetSelector();
    private final AimAssistController viewController = new AimAssistController();
    private final AimAssistVerticalStability verticalStability = new AimAssistVerticalStability();
    private final AimAssistPitchHoldHysteresis pitchHoldHysteresis = new AimAssistPitchHoldHysteresis();
    private final AimAssistLockOnState lockOnState = new AimAssistLockOnState();
    private final AimAssistLockOnRetention lockOnRetention = new AimAssistLockOnRetention();
    private final AimAssistKnockbackWindow knockbackWindow = new AimAssistKnockbackWindow();

    private AimMode lastMode;
    private int activeTargetId = -1;
    private long miningStartTime = -1L;
    private long nextTargetScanAt;
    private long lastRenderNanos;
    private AxisAlignedBB lastAnchorTargetBox;
    private AimAssistBodyAnchor bodyAnchor;
    private Object forgeCameraBridge;

    public Aimbot() {
        super("AimAssist", Keyboard.KEY_NONE, ModuleType.Combat, "Mouse-like first-person aim assistance");
        speed.visibleWhen(() -> !mode.getValue().isLockOn());
        verticalSpeed.visibleWhen(() -> !mode.getValue().isLockOn());
        reactionDelay.visibleWhen(() -> !mode.getValue().isLockOn());
        aimVertical.visibleWhen(() -> !mode.getValue().isLockOn());
        this.addValues(mode, speed, verticalSpeed, reactionDelay, updateRate, multipointHorizontal,
                multipointVertical, fov, range, sort, ignoreBehindWalls, ignoreBehindEntities,
                aimInvis, clickAim, requireHover, aimVertical, ignoreTeammates, botCheck, targetPlayers,
                targetAnimals, targetMobs, stopWhenBreaking, hoverDelay, weaponOnly);
        Chinese = "Aim Assist";
    }

    @Override
    public void enable() {
        resetAllState();
        lastMode = mode.getValue();
        registerForgeCameraBridge();
    }

    @Override
    public void disable() {
        unregisterForgeCameraBridge();
        resetAllState();
        super.disable();
    }

    @EventTarget(Priority.LOW)
    public void onBridgeTick(gq.yozakura.event.bridge.TickEvent event) {
        if (!getState() || event.getType() != EventType.PRE) {
            return;
        }
        handleModeChange();
        if (!refreshTargetForCurrentInput()) {
            verticalStability.reset();
            knockbackWindow.reset();
            return;
        }
        if (mode.getValue().isLockOn()) {
            knockbackWindow.update(mc.thePlayer.onGround, mc.thePlayer.hurtTime,
                    mc.thePlayer.posY - mc.thePlayer.lastTickPosY,
                    target.onGround, target.hurtTime, target.posY - target.lastTickPosY);
        } else {
            verticalStability.update(mc.thePlayer.onGround, mc.thePlayer.hurtTime);
        }
    }

    /** Forge Adaptive path. Lock-on waits until CameraSetup after mouse input. */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        applyAdaptiveFrame(event.renderTickTime);
    }

    private void applyAdaptiveFrame(float partialTicks) {
        if (!getState() || mode.getValue().isLockOn() || !hasActiveTarget()) {
            return;
        }
        if (!conditionsMet()) {
            clearTargetState();
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
        updateAdaptiveViewTarget(partialTicks, deltaSeconds);
        AimAssistController.Rotation rotation = viewController.step(
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                deltaSeconds, nowMillis, createControllerSettings());
        if (Math.abs(rotation.getYawDelta()) > ROTATION_EPSILON
                || Math.abs(rotation.getPitchDelta()) > ROTATION_EPSILON) {
            mc.thePlayer.setAngles(rotation.getYawDelta() / 0.15F, -rotation.getPitchDelta() / 0.15F);
        }
    }

    /** Lunar standalone path, dispatched immediately before renderWorld. */
    @EventTarget(Priority.LOWEST)
    public void onRenderTickStart(RenderTickStartEvent event) {
        if (!event.isCameraInputComplete()) {
            return;
        }
        float partialTicks = event.getPartialTicks();
        if (mode.getValue().isLockOn()) {
            applyLockOnBoundary(partialTicks);
        } else {
            applyAdaptiveFrame(partialTicks);
        }
    }

    CameraDelta applyLockOnBoundary(float partialTicks) {
        if (!getState() || !mode.getValue().isLockOn() || !hasActiveTarget()
                || !(target instanceof EntityPlayer)) {
            return CameraDelta.NONE;
        }
        if (!conditionsMet()) {
            return CameraDelta.NONE;
        }
        long now = System.currentTimeMillis();
        if (!isTargetStillEligible()) {
            if (lockOnRetention.shouldRetain(now)) {
                return CameraDelta.NONE;
            }
            clearTargetState();
            return CameraDelta.NONE;
        }
        lockOnRetention.confirmEligible(now);
        float partial = MathHelper.clamp_float(partialTicks, 0.0F, 1.0F);
        AxisAlignedBB box = interpolatedTargetBox(partial);
        float bodyYaw = interpolatedBodyYaw(partial);
        AimAssistLockOnGeometry.Frame frame = AimAssistLockOnGeometry.create(
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                bodyYaw, multipointHorizontal.getValue() / 100.0D,
                multipointVertical.getValue() / 100.0D);
        Vec3 eye = mc.thePlayer.getPositionEyes(partial);
        double reachDistance = range.getValue() + 0.15D;
        float currentYaw = mc.thePlayer.rotationYaw;
        float currentPitch = mc.thePlayer.rotationPitch;
        boolean hitsHead = rayHits(frame, AimAssistLockOnGeometry.Zone.HEAD,
                eye, currentYaw, currentPitch, reachDistance);
        boolean hitsAny = rayHitsAny(frame, eye, currentYaw, currentPitch, reachDistance);
        boolean disruptionActive = knockbackWindow.isActive();
        if (!lockOnState.isAiming()
                && ((disruptionActive && hitsAny) || (!disruptionActive && hitsHead))) {
            lockOnState.resolve(disruptionActive, hitsHead, hitsAny, false,
                    currentYaw, currentPitch, currentYaw, currentPitch);
            return CameraDelta.NONE;
        }
        Vec3 snapPoint;
        if (lockOnState.isAiming()) {
            snapPoint = resolveLockOnHeadPoint(frame, box, eye);
        } else if (disruptionActive && !hitsAny) {
            snapPoint = resolveLockOnUnionBoundaryPoint(frame, box, eye, currentYaw, currentPitch);
        } else if (!disruptionActive && !hitsHead) {
            snapPoint = resolveLockOnBoundaryPoint(frame, box, eye, currentYaw, currentPitch);
        } else {
            snapPoint = resolveLockOnHeadPoint(frame, box, eye);
        }
        if (snapPoint == null) {
            return CameraDelta.NONE;
        }
        ResolvedRotation snap = rotationFromEyeToPoint(eye, snapPoint);
        AimAssistLockOnState.Resolution resolution = lockOnState.resolve(
                disruptionActive, hitsHead, hitsAny, false,
                currentYaw, currentPitch, snap.yaw, snap.pitch);
        if (resolution.getAction() == AimAssistLockOnState.Action.KEEP) {
            return CameraDelta.NONE;
        }
        return applyCameraRotation(resolution.getYaw(), resolution.getPitch(), partial);
    }

    private Vec3 resolveLockOnBoundaryPoint(AimAssistLockOnGeometry.Frame frame,
                                            AxisAlignedBB box, Vec3 eye,
                                            float yaw, float pitch) {
        Vec3 look = RotationUtil.getLook(yaw, pitch);
        AimAssistLockOnGeometry.Point projected = frame.nearestHeadPointToRay(
                eye.xCoord, eye.yCoord, eye.zCoord,
                look.xCoord, look.yCoord, look.zCoord);
        Vec3 point = new Vec3(projected.getX(), projected.getY(), projected.getZ());
        if (targetSelector.isLineAllowed(mc, eye, point, target, createSelectorSettings(360.0D))) {
            return point;
        }
        return resolveLockOnHeadPoint(frame, box, eye);
    }

    private Vec3 resolveLockOnUnionBoundaryPoint(AimAssistLockOnGeometry.Frame frame,
                                                 AxisAlignedBB box, Vec3 eye,
                                                 float yaw, float pitch) {
        Vec3 look = RotationUtil.getLook(yaw, pitch);
        AimAssistTargetSelector.Settings settings = createSelectorSettings(360.0D);
        Vec3 best = null;
        double bestAngleDistance = Double.MAX_VALUE;
        for (AimAssistLockOnGeometry.Zone zone : AimAssistLockOnGeometry.Zone.values()) {
            AimAssistLockOnGeometry.Point projected = frame.nearestPointToRay(zone,
                    eye.xCoord, eye.yCoord, eye.zCoord,
                    look.xCoord, look.yCoord, look.zCoord);
            Vec3 candidate = new Vec3(projected.getX(), projected.getY(), projected.getZ());
            if (!targetSelector.isLineAllowed(mc, eye, candidate, target, settings)) {
                continue;
            }
            ResolvedRotation rotation = rotationFromEyeToPoint(eye, candidate);
            double yawDistance = MathHelper.wrapAngleTo180_float(rotation.yaw - yaw);
            double pitchDistance = rotation.pitch - pitch;
            double angleDistance = yawDistance * yawDistance + pitchDistance * pitchDistance;
            if (angleDistance < bestAngleDistance) {
                best = candidate;
                bestAngleDistance = angleDistance;
            }
        }
        return best != null ? best : resolveLockOnHeadPoint(frame, box, eye);
    }

    private Vec3 resolveLockOnHeadPoint(AimAssistLockOnGeometry.Frame frame,
                                        AxisAlignedBB box, Vec3 eye) {
        AimAssistLockOnGeometry.Point nearest = frame.nearestHeadPoint(
                eye.xCoord, eye.yCoord, eye.zCoord);
        Vec3 preferred = new Vec3(nearest.getX(), nearest.getY(), nearest.getZ());
        AimAssistTargetSelector.Settings settings = createSelectorSettings(360.0D);
        if (targetSelector.isLineAllowed(mc, eye, preferred, target, settings)) {
            return preferred;
        }
        AimAssistLockOnGeometry.Region head = frame.get(AimAssistLockOnGeometry.Zone.HEAD);
        Vec3 best = null;
        double bestAngleDistance = Double.MAX_VALUE;
        ResolvedRotation preferredRotation = rotationFromEyeToPoint(eye, preferred);
        for (int yIndex = 0; yIndex < 3; yIndex++) {
            double y = head.getMinimumY() + (head.getMaximumY() - head.getMinimumY())
                    * (0.2D + yIndex * 0.3D);
            for (int xIndex = 0; xIndex < 5; xIndex++) {
                double x = box.minX + (box.maxX - box.minX) * (0.1D + xIndex * 0.2D);
                for (int zIndex = 0; zIndex < 5; zIndex++) {
                    double z = box.minZ + (box.maxZ - box.minZ) * (0.1D + zIndex * 0.2D);
                    if (!head.contains(x, y, z)) {
                        continue;
                    }
                    Vec3 candidate = new Vec3(x, y, z);
                    if (!targetSelector.isLineAllowed(mc, eye, candidate, target, settings)) {
                        continue;
                    }
                    ResolvedRotation rotation = rotationFromEyeToPoint(eye, candidate);
                    double yawDistance = Math.abs(MathHelper.wrapAngleTo180_float(
                            rotation.yaw - preferredRotation.yaw));
                    double pitchDistance = Math.abs(rotation.pitch - preferredRotation.pitch);
                    double angleDistance = yawDistance * yawDistance + pitchDistance * pitchDistance;
                    if (angleDistance < bestAngleDistance) {
                        best = candidate;
                        bestAngleDistance = angleDistance;
                    }
                }
            }
        }
        return best;
    }

    private CameraDelta applyCameraRotation(float yaw, float pitch, float partialTicks) {
        float yawDelta = MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
        float pitchDelta = pitch - mc.thePlayer.rotationPitch;
        if (Math.abs(yawDelta) <= ROTATION_EPSILON && Math.abs(pitchDelta) <= ROTATION_EPSILON) {
            return CameraDelta.NONE;
        }
        mc.thePlayer.rotationYaw += yawDelta;
        mc.thePlayer.prevRotationYaw += yawDelta;
        mc.thePlayer.rotationPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch + pitchDelta,
                -90.0F, 90.0F);
        mc.thePlayer.prevRotationPitch = MathHelper.clamp_float(mc.thePlayer.prevRotationPitch + pitchDelta,
                -90.0F, 90.0F);
        mc.entityRenderer.getMouseOver(partialTicks);
        Reach.applyRuntimeMouseOverOverride(partialTicks);
        return new CameraDelta(yawDelta, pitchDelta);
    }

    private void registerForgeCameraBridge() {
        if (forgeCameraBridge != null || !ForgeEnvironment.isLegacyForgeAvailable()) {
            return;
        }
        forgeCameraBridge = AimAssistForgeCameraBridge.register(this);
    }

    private void unregisterForgeCameraBridge() {
        Object bridge = forgeCameraBridge;
        forgeCameraBridge = null;
        if (bridge != null && ForgeEnvironment.isLegacyForgeAvailable()) {
            AimAssistForgeCameraBridge.unregister(bridge);
        }
    }

    private void handleModeChange() {
        AimMode currentMode = mode.getValue();
        if (lastMode == null) {
            lastMode = currentMode;
            return;
        }
        if (lastMode != currentMode) {
            clearTargetState();
            nextTargetScanAt = 0L;
            lastMode = currentMode;
        }
    }

    private boolean refreshTargetForCurrentInput() {
        if (!conditionsMet()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (isTargetStillEligible()) {
            if (mode.getValue().isLockOn()) {
                lockOnRetention.confirmEligible(now);
            }
            return true;
        }
        refreshTarget(now);
        return hasActiveTarget();
    }

    private void refreshTarget(long now) {
        if (target != null && !isTargetStillEligible()) {
            if (mode.getValue().isLockOn() && lockOnRetention.shouldRetain(now)) {
                return;
            }
            clearTargetState();
            nextTargetScanAt = now;
        }
        if (now < nextTargetScanAt) {
            return;
        }
        nextTargetScanAt = now + sampleIntervalMillis();
        AimAssistTargetSelector.Selection selection = targetSelector.select(
                mc, mc.thePlayer.rotationYaw, createSelectorSettings(), now);
        if (selection == null) {
            if (mode.getValue().isLockOn() && lockOnRetention.shouldRetain(now)) {
                return;
            }
            clearTargetState();
            return;
        }
        EntityLivingBase nextTarget = selection.getTarget();
        boolean targetChanged = nextTarget.getEntityId() != activeTargetId;
        target = nextTarget;
        if (!targetChanged) {
            return;
        }
        activeTargetId = nextTarget.getEntityId();
        lastRenderNanos = 0L;
        if (mode.getValue().isLockOn()) {
            bodyAnchor = null;
            lastAnchorTargetBox = null;
            lockOnState.acquire(activeTargetId);
            lockOnRetention.confirmEligible(now);
            knockbackWindow.reset();
            viewController.releaseTarget();
            return;
        }
        bodyAnchor = AimAssistBodyAnchor.capture(
                selection.getPoint().xCoord, selection.getPoint().yCoord, selection.getPoint().zCoord);
        lastAnchorTargetBox = target.getEntityBoundingBox();
        viewController.acquireTarget(activeTargetId, now,
                Math.max(0L, reactionDelay.getValue().longValue()),
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        ResolvedRotation resolved = resolveAdaptiveTargetRotation(1.0F);
        if (resolved != null) {
            viewController.setTargetRotation(resolved.yaw, resolved.pitch, 1.0F);
        }
    }

    private void updateAdaptiveViewTarget(float partialTicks, float deltaSeconds) {
        ResolvedRotation resolved = resolveAdaptiveTargetRotation(partialTicks);
        if (resolved == null) {
            return;
        }
        viewController.setTargetRotation(resolved.yaw, resolved.pitch,
                temporalBlend(9.0F, deltaSeconds));
        float currentPitch = mc.thePlayer.rotationPitch;
        if (shouldHoldCurrentPitch(resolved.yaw, currentPitch, partialTicks)) {
            viewController.holdCurrentPitch(currentPitch);
        }
    }

    private ResolvedRotation resolveAdaptiveTargetRotation(float partialTicks) {
        if (!hasActiveTarget() || bodyAnchor == null) {
            return null;
        }
        Vec3 eye = mc.thePlayer.getPositionEyes(partialTicks);
        AxisAlignedBB targetBox = interpolatedTargetBox(partialTicks);
        AxisAlignedBB stateTargetBox = target.getEntityBoundingBox();
        float border = target.getCollisionBorderSize();
        AxisAlignedBB stateAttackBox = stateTargetBox.expand(border, border, border);
        if (lastAnchorTargetBox == null) {
            lastAnchorTargetBox = stateTargetBox;
        } else if (hasBoxMoved(lastAnchorTargetBox, stateTargetBox)) {
            bodyAnchor.followTargetTranslation(
                    lastAnchorTargetBox.minX, lastAnchorTargetBox.minY, lastAnchorTargetBox.minZ,
                    lastAnchorTargetBox.maxX, lastAnchorTargetBox.maxY, lastAnchorTargetBox.maxZ,
                    stateTargetBox.minX, stateTargetBox.minY, stateTargetBox.minZ,
                    stateTargetBox.maxX, stateTargetBox.maxY, stateTargetBox.maxZ,
                    TARGET_VERTICAL_BLEND);
            lastAnchorTargetBox = stateTargetBox;
        }
        bodyAnchor.clampToSafeBox(
                stateAttackBox.minX, stateAttackBox.minY, stateAttackBox.minZ,
                stateAttackBox.maxX, stateAttackBox.maxY, stateAttackBox.maxZ);
        double[] anchoredPoint = bodyAnchor.translatedPoint(
                stateTargetBox.minX, stateTargetBox.minY, stateTargetBox.minZ,
                stateTargetBox.maxX, stateTargetBox.maxY, stateTargetBox.maxZ,
                targetBox.minX, targetBox.minY, targetBox.minZ,
                targetBox.maxX, targetBox.maxY, targetBox.maxZ);
        Vec3 point = new Vec3(anchoredPoint[0], anchoredPoint[1], anchoredPoint[2]);
        if (!targetSelector.isLineAllowed(mc, eye, point, target, createSelectorSettings(360.0D))) {
            return null;
        }
        return rotationFromEyeToPoint(eye, point);
    }

    private boolean shouldHoldCurrentPitch(float targetYaw, float currentPitch, float partialTicks) {
        boolean outerBoxHit = currentRotationHitsTargetBox(targetYaw, currentPitch, partialTicks,
                PITCH_HOLD_HORIZONTAL_INSET, PITCH_HOLD_VERTICAL_INSET);
        boolean innerBoxHit = currentRotationHitsTargetBox(targetYaw, currentPitch, partialTicks,
                PITCH_HOLD_ENTER_HORIZONTAL_INSET, PITCH_HOLD_ENTER_VERTICAL_INSET);
        boolean eligible = AimAssistVerticalPolicy.isPitchHoldEligible(
                mc.thePlayer.onGround, targetIsMoving()) || verticalStability.isActive();
        return pitchHoldHysteresis.update(eligible, innerBoxHit, outerBoxHit);
    }

    private boolean targetIsMoving() {
        return hasActiveTarget() && AimAssistVerticalPolicy.isMoving(
                target.posX - target.lastTickPosX,
                target.posY - target.lastTickPosY,
                target.posZ - target.lastTickPosZ);
    }

    private boolean currentRotationHitsTargetBox(float yaw, float pitch, float partialTicks,
                                                  double horizontalInset, double verticalInset) {
        if (!hasActiveTarget()) {
            return false;
        }
        Vec3 eye = mc.thePlayer.getPositionEyes(partialTicks);
        AxisAlignedBB targetBox = interpolatedTargetBox(partialTicks).expand(
                target.getCollisionBorderSize(), target.getCollisionBorderSize(),
                target.getCollisionBorderSize());
        double safeHorizontalInset = Math.min(horizontalInset,
                Math.min(targetBox.maxX - targetBox.minX, targetBox.maxZ - targetBox.minZ) * 0.25D);
        double safeVerticalInset = Math.min(verticalInset,
                (targetBox.maxY - targetBox.minY) * 0.25D);
        targetBox = new AxisAlignedBB(
                targetBox.minX + safeHorizontalInset, targetBox.minY + safeVerticalInset,
                targetBox.minZ + safeHorizontalInset, targetBox.maxX - safeHorizontalInset,
                targetBox.maxY - safeVerticalInset, targetBox.maxZ - safeHorizontalInset);
        Vec3 look = RotationUtil.getLook(yaw, pitch);
        Vec3 end = eye.addVector(look.xCoord * (range.getValue() + 0.15D),
                look.yCoord * (range.getValue() + 0.15D),
                look.zCoord * (range.getValue() + 0.15D));
        return targetBox.isVecInside(eye) || targetBox.calculateIntercept(eye, end) != null;
    }

    private boolean rayHits(AimAssistLockOnGeometry.Frame frame, AimAssistLockOnGeometry.Zone zone,
                            Vec3 eye, float yaw, float pitch, double reachDistance) {
        Vec3 look = RotationUtil.getLook(yaw, pitch);
        return frame.rayHits(zone, eye.xCoord, eye.yCoord, eye.zCoord,
                look.xCoord, look.yCoord, look.zCoord, reachDistance);
    }

    private boolean rayHitsAny(AimAssistLockOnGeometry.Frame frame, Vec3 eye,
                               float yaw, float pitch, double reachDistance) {
        Vec3 look = RotationUtil.getLook(yaw, pitch);
        return frame.rayHitsAny(eye.xCoord, eye.yCoord, eye.zCoord,
                look.xCoord, look.yCoord, look.zCoord, reachDistance);
    }

    private ResolvedRotation rotationFromEyeToPoint(Vec3 eye, Vec3 point) {
        double diffX = point.xCoord - eye.xCoord;
        double diffY = point.yCoord - eye.yCoord;
        double diffZ = point.zCoord - eye.zCoord;
        double horizontalDistance = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, horizontalDistance)));
        return new ResolvedRotation(yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F));
    }

    private AxisAlignedBB interpolatedTargetBox(float partialTicks) {
        float partial = MathHelper.clamp_float(partialTicks, 0.0F, 1.0F);
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partial;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partial;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partial;
        return target.getEntityBoundingBox().offset(x - target.posX, y - target.posY, z - target.posZ);
    }

    private float interpolatedBodyYaw(float partialTicks) {
        float yawDelta = MathHelper.wrapAngleTo180_float(target.renderYawOffset - target.prevRenderYawOffset);
        return target.prevRenderYawOffset + yawDelta * partialTicks;
    }

    private boolean hasBoxMoved(AxisAlignedBB previous, AxisAlignedBB current) {
        return Double.compare(previous.minX, current.minX) != 0
                || Double.compare(previous.minY, current.minY) != 0
                || Double.compare(previous.minZ, current.minZ) != 0
                || Double.compare(previous.maxX, current.maxX) != 0
                || Double.compare(previous.maxY, current.maxY) != 0
                || Double.compare(previous.maxZ, current.maxZ) != 0;
    }

    private float temporalBlend(float response, float deltaSeconds) {
        float clampedDelta = MathHelper.clamp_float(deltaSeconds, 0.0001F, 0.25F);
        return 1.0F - (float) Math.exp(-response * clampedDelta);
    }

    private void clearTargetState() {
        target = null;
        activeTargetId = -1;
        bodyAnchor = null;
        lastAnchorTargetBox = null;
        targetSelector.clear();
        pitchHoldHysteresis.reset();
        viewController.releaseTarget();
        lockOnState.reset();
        lockOnRetention.reset();
        knockbackWindow.reset();
        lastRenderNanos = 0L;
        verticalStability.reset();
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
        return target != null && !target.isDead && target.deathTime == 0;
    }

    private boolean isTargetStillEligible() {
        return hasActiveTarget() && targetSelector.isStillEligible(
                mc, target, mc.thePlayer.rotationYaw, createSelectorSettings(360.0D));
    }

    private long sampleIntervalMillis() {
        double rate = Math.max(0.1D, updateRate.getValue());
        return Math.max(1L, Math.round(1000.0D / rate));
    }

    private AimAssistTargetSelector.Settings createSelectorSettings() {
        return createSelectorSettings(fov.getValue());
    }

    private AimAssistTargetSelector.Settings createSelectorSettings(double effectiveFov) {
        return new AimAssistTargetSelector.Settings(range.getValue(), effectiveFov,
                toSelectorSort(sort.getValue()), Boolean.TRUE.equals(ignoreBehindWalls.getValue()),
                Boolean.TRUE.equals(ignoreBehindEntities.getValue()), Boolean.TRUE.equals(aimInvis.getValue()),
                Boolean.TRUE.equals(ignoreTeammates.getValue()), Boolean.TRUE.equals(botCheck.getValue()),
                Boolean.TRUE.equals(targetPlayers.getValue()), Boolean.TRUE.equals(targetAnimals.getValue()),
                Boolean.TRUE.equals(targetMobs.getValue()), multipointHorizontal.getValue() / 100.0D,
                multipointVertical.getValue() / 100.0D, mode.getValue().isLockOn()
                ? AimAssistTargetSelector.AimPointMode.LOCK_ON_HEAD
                : AimAssistTargetSelector.AimPointMode.ADAPTIVE);
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
        float yawSpeed = MathHelper.clamp_float(
                22.0F + speed.getValue().floatValue() * 4.8F, 25.0F, 420.0F);
        float pitchSpeed = MathHelper.clamp_float(
                14.0F + verticalSpeed.getValue().floatValue() * 3.4F, 16.0F, 260.0F);
        float sensitivity = mc.gameSettings == null ? 0.5F : mc.gameSettings.mouseSensitivity;
        return new AimAssistController.Settings(yawSpeed, pitchSpeed, sensitivity,
                Boolean.TRUE.equals(aimVertical.getValue()), AimAssistController.Profile.REGULAR);
    }

    private void resetAllState() {
        clearTargetState();
        miningStartTime = -1L;
        nextTargetScanAt = 0L;
    }

    private static final class ResolvedRotation {
        private final float yaw;
        private final float pitch;

        private ResolvedRotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    static final class CameraDelta {
        private static final CameraDelta NONE = new CameraDelta(0.0F, 0.0F);

        private final float yaw;
        private final float pitch;

        private CameraDelta(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        float getYaw() {
            return yaw;
        }

        float getPitch() {
            return pitch;
        }
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
