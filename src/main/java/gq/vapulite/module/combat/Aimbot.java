package gq.vapulite.module.combat;

import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.util.minecraft.RotationUtil;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Aimbot extends Module {
    private static final long TARGET_REACTION_MS = 150L;
    private static final long TARGET_LOCK_MS = 650L;
    private static final double PREDICTION = 0.28D;

    private final Numbers<Double> range = new Numbers<Double>("Range", "Range", 4.0, 1.0, 5.0, 0.1);
    private final Numbers<Double> fov = new Numbers<Double>("FOV", "FOV", 55.0, 5.0, 120.0, 5.0);
    private final Numbers<Double> aimSpeed = new Numbers<Double>("Aim Speed", "AimSpeed", 3.0, 1.0, 10.0, 0.5);
    private final Numbers<Double> deadZone = new Numbers<Double>("Dead Zone", "DeadZone", 1.6, 0.0, 6.0, 0.1);
    private final Option<Boolean> onlyWeapon = new Option<Boolean>("Only Weapon", "OnlyWeapon", true);
    private final Option<Boolean> wallCheck = new Option<Boolean>("Wall Check", "WallCheck", true);

    public EntityLivingBase target;
    private int targetId = -1;
    private long targetChangedAt;
    private long nextOffsetAt;
    private float offsetYaw;
    private float offsetPitch;
    private float targetOffsetYaw;
    private float targetOffsetPitch;
    private final RotationUtil.State rotationState = new RotationUtil.State();

    public Aimbot() {
        super("AimAssist", Keyboard.KEY_NONE, ModuleType.Combat, "Light aim assist while attacking");
        this.addValues(range, fov, aimSpeed, deadZone, onlyWeapon, wallCheck);
        Chinese = "瞄准辅助";
    }

    @Override
    public void enable() {
        resetTarget();
    }

    @Override
    public void disable() {
        resetTarget();
        super.disable();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (!canAssistNow()) {
            resetTarget();
            return;
        }

        EntityLivingBase nextTarget = selectTarget();
        if (nextTarget == null) {
            resetTarget();
            return;
        }
        if (target == null || nextTarget.getEntityId() != targetId) {
            target = nextTarget;
            targetId = nextTarget.getEntityId();
            targetChangedAt = System.currentTimeMillis();
            nextOffsetAt = 0L;
            offsetYaw = 0.0f;
            offsetPitch = 0.0f;
            targetOffsetYaw = 0.0f;
            targetOffsetPitch = 0.0f;
            rotationState.reset();
            return;
        }

        target = nextTarget;
        if (System.currentTimeMillis() - targetChangedAt < TARGET_REACTION_MS) {
            return;
        }
        updateAimOffset();
        faceTarget(nextTarget);
    }

    private boolean canAssistNow() {
        if (!isInGame() || CombatUtil.shouldPauseForScreen()) {
            return false;
        }
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            return false;
        }
        if (KillAura.target != null) {
            return false;
        }
        if (Boolean.TRUE.equals(onlyWeapon.getValue()) && !CombatUtil.isHoldingWeapon()) {
            return false;
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        return hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private EntityLivingBase selectTarget() {
        EntityLivingBase hovered = getHoveredTarget();
        if (hovered != null) {
            return hovered;
        }
        if (target != null && isLockedTargetValid(target)) {
            return target;
        }

        List<EntityLivingBase> targets = CombatUtil.collectTargets(range.getValue(), fov.getValue(),
                true, false, false, !wallCheck.getValue());
        if (targets.isEmpty()) {
            return null;
        }
        targets.sort((first, second) -> Double.compare(assistScore(first), assistScore(second)));
        return targets.get(0);
    }

    private EntityLivingBase getHoveredTarget() {
        if (mc.objectMouseOver == null || !(mc.objectMouseOver.entityHit instanceof EntityLivingBase)) {
            return null;
        }
        EntityLivingBase hovered = (EntityLivingBase) mc.objectMouseOver.entityHit;
        return CombatUtil.isValidTarget(hovered, range.getValue() + 0.15D, 180.0D,
                true, false, false, !wallCheck.getValue()) ? hovered : null;
    }

    private boolean isLockedTargetValid(EntityLivingBase entity) {
        boolean relaxedLock = target != null && entity.getEntityId() == targetId
                && System.currentTimeMillis() - targetChangedAt <= TARGET_LOCK_MS * 3L;
        return CombatUtil.isValidTarget(entity, range.getValue() + 0.25D,
                relaxedLock ? Math.min(150.0D, fov.getValue() + 18.0D) : Math.min(130.0D, fov.getValue() + 5.0D),
                true, false, false, !wallCheck.getValue());
    }

    private double assistScore(EntityLivingBase entity) {
        double fovScore = CombatUtil.getFovDifference(entity);
        double distanceScore = mc.thePlayer.getDistanceToEntity(entity) * 8.0D;
        double hurtPenalty = entity.hurtTime * 1.2D;
        double lockBonus = target != null && entity.getEntityId() == targetId ? -18.0D : 0.0D;
        return fovScore + distanceScore + hurtPenalty + lockBonus;
    }

    private void faceTarget(EntityLivingBase entity) {
        float[] rotations = getAssistRotations(entity);
        float mouseStep = getMouseStep();
        float yawDiff = MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(rotations[1] - mc.thePlayer.rotationPitch);
        float free = deadZone.getValue().floatValue();
        if (Math.abs(yawDiff) <= free && Math.abs(pitchDiff) <= free) {
            return;
        }

        float power = aimSpeed.getValue().floatValue();
        float sensitivityScale = getSensitivityScale(mouseStep);
        float distance = mc.thePlayer.getDistanceToEntity(entity);
        float closeScale = distance < 2.8f ? 0.78f : 1.0f;
        float correctionScale = MathHelper.clamp_float(Math.abs(yawDiff) / 24.0f, 0.38f, 1.0f);
        float yawStep = MathHelper.clamp_float(1.0f + power * 0.82f, 1.4f, 9.2f)
                * closeScale * sensitivityScale;
        float pitchStep = MathHelper.clamp_float(0.55f + power * 0.48f, 0.75f, 5.2f)
                * closeScale * sensitivityScale;

        float edge = Math.abs(yawDiff) / Math.max(1.0f, fov.getValue().floatValue());
        float fovScale = MathHelper.clamp_float(1.0f - RotationUtil.smoothStep(edge) * 0.48f, 0.42f, 1.0f);
        boolean yawOnly = Math.abs(yawDiff) > Math.max(10.0f, fov.getValue().floatValue() * 0.24f);
        RotationUtil.applyToPlayer(mc, rotations[0], rotations[1], yawStep * fovScale * correctionScale,
                pitchStep * fovScale * MathHelper.clamp_float(correctionScale + 0.18f, 0.46f, 1.0f),
                yawOnly, free, rotationState, 0.42f, 0.006f, true);
    }

    private float getMouseStep() {
        float sensitivity = MathHelper.clamp_float(mc.gameSettings.mouseSensitivity, 0.0f, 1.0f);
        float base = sensitivity * 0.6f + 0.2f;
        return base * base * base * 1.2f;
    }

    private float getSensitivityScale(float mouseStep) {
        return MathHelper.clamp_float((float) Math.sqrt(mouseStep / 0.15f), 0.55f, 1.55f);
    }

    private float[] getAssistRotations(EntityLivingBase entity) {
        updateOffsetDrift();
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double width = Math.max(0.2D, Math.min(box.maxX - box.minX, box.maxZ - box.minZ));
        double targetX = entity.posX + (entity.posX - entity.lastTickPosX) * PREDICTION + offsetYaw * width * 0.08D;
        double targetZ = entity.posZ + (entity.posZ - entity.lastTickPosZ) * PREDICTION - offsetYaw * width * 0.05D;
        double height = Math.max(0.2D, box.maxY - box.minY);
        double targetY = box.minY + height * aimHeight(entity)
                + (entity.posY - entity.lastTickPosY) * Math.min(0.6D, PREDICTION)
                + offsetPitch * 0.025D;
        return RotationUtil.getRotationsTo(mc, targetX, targetY, targetZ);
    }

    private double aimHeight(EntityLivingBase entity) {
        double distance = mc.thePlayer.getDistanceToEntity(entity);
        double base = distance < 2.7D ? 0.58D : 0.67D;
        if (entity.hurtTime > 5) {
            base -= 0.05D;
        }
        if (!entity.onGround) {
            base += 0.04D;
        }
        return MathHelper.clamp_double(base, 0.48D, 0.76D);
    }

    private void updateAimOffset() {
        long now = System.currentTimeMillis();
        if (now < nextOffsetAt) {
            return;
        }
        nextOffsetAt = now + 850L + ThreadLocalRandom.current().nextLong(450L);
        float amount = MathHelper.clamp_float(aimSpeed.getValue().floatValue() / 20.0f, 0.03f, 0.34f);
        targetOffsetYaw = (ThreadLocalRandom.current().nextFloat() - 0.5f) * amount;
        targetOffsetPitch = (ThreadLocalRandom.current().nextFloat() - 0.5f) * amount * 0.42f;
    }

    private void updateOffsetDrift() {
        float yawDelta = targetOffsetYaw - offsetYaw;
        float pitchDelta = targetOffsetPitch - offsetPitch;
        if (Math.abs(yawDelta) > 0.012f) {
            offsetYaw += yawDelta * 0.024f;
        }
        if (Math.abs(pitchDelta) > 0.008f) {
            offsetPitch += pitchDelta * 0.020f;
        }
    }

    private void resetTarget() {
        this.target = null;
        this.targetId = -1;
        this.targetChangedAt = 0L;
        this.nextOffsetAt = 0L;
        this.offsetYaw = 0.0f;
        this.offsetPitch = 0.0f;
        this.targetOffsetYaw = 0.0f;
        this.targetOffsetPitch = 0.0f;
        this.rotationState.reset();
    }

    public static void assistFaceEntity(Entity entity, float yaw, float pitch) {
        CombatUtil.faceEntity(entity, yaw, pitch, pitch <= 0.0f, 0.0f);
    }

    public static float updateRotation(float current, float target, float maxTurn) {
        return CombatUtil.updateRotation(current, target, maxTurn);
    }

    public static List<Entity> getEntityList() {
        return mc.theWorld == null ? null : mc.theWorld.getLoadedEntityList();
    }
}
