package gq.vapulite.Vapu.modules.combat;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.RotationUtil;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Aimbot extends Module {
    public enum AimStyle {
        LEGIT,
        SMART,
        AGGRESSIVE
    }

    public enum AimPoint {
        CHEST,
        HEAD,
        DYNAMIC
    }

    private final Numbers<Double> range = new Numbers<Double>("Range", "Range", 4.4, 1.0, 6.0, 0.1);
    private final Numbers<Double> fov = new Numbers<Double>("FOV", "FOV", 130.0, 10.0, 180.0, 5.0);
    private final Numbers<Double> yawSpeed = new Numbers<Double>("Yaw Speed", "YawSpeed", 22.0, 1.0, 90.0, 1.0);
    private final Numbers<Double> pitchSpeed = new Numbers<Double>("Pitch Speed", "PitchSpeed", 15.0, 1.0, 90.0, 1.0);
    private final Numbers<Double> freeZone = new Numbers<Double>("Free Zone", "FreeZone", 0.45, 0.0, 6.0, 0.1);
    private final Numbers<Double> prediction = new Numbers<Double>("Prediction", "Prediction", 0.65, 0.0, 2.5, 0.05);
    private final Numbers<Double> reactionMs = new Numbers<Double>("Reaction MS", "ReactionMS", 70.0, 0.0, 260.0, 5.0);
    private final Numbers<Double> lockMs = new Numbers<Double>("Lock MS", "LockMS", 520.0, 0.0, 2200.0, 20.0);
    private final Numbers<Double> randomAmount = new Numbers<Double>("Randomize", "Randomize", 0.28, 0.0, 2.0, 0.05);
    private final Mode<AimStyle> style = new Mode<AimStyle>("Style", "Style", AimStyle.values(), AimStyle.SMART);
    private final Mode<AimPoint> aimPoint = new Mode<AimPoint>("Aim Point", "AimPoint", AimPoint.values(), AimPoint.DYNAMIC);
    private final Option<Boolean> requireMouse = new Option<Boolean>("Mouse Down", "MouseDown", false);
    private final Option<Boolean> onlyWeapon = new Option<Boolean>("Only Weapon", "OnlyWeapon", false);
    private final Option<Boolean> wallCheck = new Option<Boolean>("Wall Check", "WallCheck", true);
    private final Option<Boolean> onlyYaw = new Option<Boolean>("Only Yaw", "OnlyYaw", false);
    private final Option<Boolean> players = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> mobs = new Option<Boolean>("Mobs", "Mobs", true);
    private final Option<Boolean> animals = new Option<Boolean>("Animals", "Animals", true);
    private final Mode<CombatUtil.TargetPriority> priority =
            new Mode<CombatUtil.TargetPriority>("Priority", "Priority", CombatUtil.TargetPriority.values(), CombatUtil.TargetPriority.FOV);

    public EntityLivingBase target;
    private int targetId = -1;
    private long targetChangedAt;
    private long lastRandomAt;
    private float randomYaw;
    private float randomPitch;
    private final RotationUtil.State rotationState = new RotationUtil.State();

    public Aimbot() {
        super("Aimbot", Keyboard.KEY_NONE, ModuleType.Combat, "Smoothly aim at the best target");
        this.addValues(range, fov, yawSpeed, pitchSpeed, freeZone, prediction, reactionMs, lockMs, randomAmount,
                style, aimPoint, requireMouse, onlyWeapon, wallCheck, onlyYaw, players, mobs, animals, priority);
        Chinese = "自瞄";
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

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame() || CombatUtil.shouldPauseForScreen()) {
            resetTarget();
            return;
        }
        if (Boolean.TRUE.equals(requireMouse.getValue()) && !mc.gameSettings.keyBindAttack.isKeyDown()) {
            resetTarget();
            return;
        }
        if (Boolean.TRUE.equals(onlyWeapon.getValue()) && !CombatUtil.isHoldingWeapon()) {
            resetTarget();
            return;
        }

        EntityLivingBase nextTarget = selectTarget();
        if (nextTarget == null) {
            resetTarget();
            return;
        }
        if (target == null || nextTarget.getEntityId() != targetId) {
            targetChangedAt = System.currentTimeMillis();
            targetId = nextTarget.getEntityId();
            rotationState.reset();
        }
        target = nextTarget;
        if (System.currentTimeMillis() - targetChangedAt < reactionMs.getValue().longValue()) {
            return;
        }
        updateRandomOffsets();
        faceTarget(nextTarget);
    }

    private EntityLivingBase selectTarget() {
        if (target != null && isLockedTargetValid(target)) {
            return target;
        }

        List<EntityLivingBase> targets = CombatUtil.collectTargets(range.getValue(), fov.getValue(),
                players.getValue(), mobs.getValue(), animals.getValue(), !wallCheck.getValue());
        if (targets.isEmpty()) {
            return null;
        }
        CombatUtil.sortTargets(targets, priority.getValue());
        if (style.getValue() == AimStyle.SMART) {
            targets.sort((first, second) -> Double.compare(smartScore(first), smartScore(second)));
        }
        return targets.get(0);
    }

    private boolean isLockedTargetValid(EntityLivingBase entity) {
        if (System.currentTimeMillis() - targetChangedAt > lockMs.getValue().longValue()) {
            return false;
        }
        return CombatUtil.isValidTarget(entity, range.getValue() + 0.35D, Math.min(180.0D, fov.getValue() + 18.0D),
                players.getValue(), mobs.getValue(), animals.getValue(), !wallCheck.getValue());
    }

    private double smartScore(EntityLivingBase entity) {
        double distanceScore = mc.thePlayer.getDistanceToEntity(entity) * 0.55D;
        double fovScore = CombatUtil.getFovDifference(entity) * 0.045D;
        double healthScore = entity.getHealth() * 0.025D;
        double hurtScore = entity.hurtTime * 0.08D;
        return distanceScore + fovScore + healthScore + hurtScore;
    }

    private void faceTarget(EntityLivingBase entity) {
        float[] rotations = getSmartRotations(entity);
        float yawDiff = MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(rotations[1] - mc.thePlayer.rotationPitch);
        float free = freeZone.getValue().floatValue();
        float styleScale = style.getValue() == AimStyle.AGGRESSIVE ? 1.28f : style.getValue() == AimStyle.LEGIT ? 0.72f : 1.0f;
        float yawStep = yawSpeed.getValue().floatValue() * styleScale;
        float pitchStep = pitchSpeed.getValue().floatValue() * styleScale;
        float jitter = (ThreadLocalRandom.current().nextFloat() - 0.5f) * randomAmount.getValue().floatValue() * 0.10f;
        RotationUtil.applyToPlayer(mc, rotations[0] + jitter, rotations[1],
                yawStep, pitchStep, Boolean.TRUE.equals(onlyYaw.getValue()), free, rotationState,
                style.getValue() == AimStyle.LEGIT ? 0.30f : 0.42f, 0.22f, true);
    }

    private float[] getSmartRotations(EntityLivingBase entity) {
        double predict = prediction.getValue();
        double motionX = (entity.posX - entity.lastTickPosX) * predict;
        double motionY = (entity.posY - entity.lastTickPosY) * Math.min(0.8D, predict);
        double motionZ = (entity.posZ - entity.lastTickPosZ) * predict;
        double targetX = entity.posX + motionX + randomYaw * entity.width * 0.18D;
        double targetZ = entity.posZ + motionZ + randomPitch * entity.width * 0.12D;
        double targetY = entity.posY + motionY + getAimHeight(entity) + randomPitch * 0.04D;

        double diffX = targetX - mc.thePlayer.posX;
        double diffY = targetY - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double diffZ = targetZ - mc.thePlayer.posZ;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0D) + randomYaw;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, dist))) + randomPitch;
        return new float[]{yaw, MathHelper.clamp_float(pitch, -90.0f, 90.0f)};
    }

    private double getAimHeight(EntityLivingBase entity) {
        AimPoint point = aimPoint.getValue();
        if (point == AimPoint.HEAD) {
            return entity.getEyeHeight() * 0.94D;
        }
        if (point == AimPoint.CHEST) {
            return entity.getEyeHeight() * 0.62D;
        }
        double distance = mc.thePlayer.getDistanceToEntity(entity);
        double base = distance < 2.7D ? 0.58D : 0.76D;
        if (entity.hurtTime > 4) {
            base -= 0.08D;
        }
        if (!entity.onGround) {
            base += 0.06D;
        }
        return entity.getEyeHeight() * MathHelper.clamp_double(base, 0.48D, 0.92D);
    }

    private void updateRandomOffsets() {
        long now = System.currentTimeMillis();
        if (now - lastRandomAt < 120L) {
            return;
        }
        lastRandomAt = now;
        float amount = randomAmount.getValue().floatValue();
        if (amount <= 0.0f) {
            randomYaw = 0.0f;
            randomPitch = 0.0f;
            return;
        }
        randomYaw = (ThreadLocalRandom.current().nextFloat() - 0.5f) * amount;
        randomPitch = (ThreadLocalRandom.current().nextFloat() - 0.5f) * amount * 0.55f;
    }

    private void resetTarget() {
        this.target = null;
        this.targetId = -1;
        this.targetChangedAt = 0L;
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
