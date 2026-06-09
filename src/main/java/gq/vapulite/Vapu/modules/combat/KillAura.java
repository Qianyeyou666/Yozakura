package gq.vapulite.Vapu.modules.combat;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.TimerUtil;
import gq.vapulite.Vapu.utils.RotationUtil;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class KillAura extends Module {
    public enum AttackMode {
        SINGLE,
        SWITCH,
        MULTI
    }

    private final TimerUtil timer = new TimerUtil();
    public static EntityLivingBase target;

    private final Numbers<Double> rangeValue = new Numbers<Double>("Range", "Range", 4.2, 1.0, 6.0, 0.1);
    private final Numbers<Double> minCps = new Numbers<Double>("Min CPS", "MinCPS", 8.0, 1.0, 20.0, 1.0);
    private final Numbers<Double> cps = new Numbers<Double>("Max CPS", "Cps", 12.0, 1.0, 20.0, 1.0);
    private final Numbers<Double> fov = new Numbers<Double>("FOV", "FOV", 180.0, 10.0, 180.0, 5.0);
    private final Numbers<Double> yawSpeed = new Numbers<Double>("Yaw Speed", "YawSpeed", 32.0, 1.0, 90.0, 1.0);
    private final Numbers<Double> pitchSpeed = new Numbers<Double>("Pitch Speed", "PitchSpeed", 24.0, 1.0, 90.0, 1.0);
    private final Numbers<Double> hurtTime = new Numbers<Double>("Hurt Time", "HurtTime", 10.0, 0.0, 10.0, 1.0);
    private final Mode<AttackMode> mode = new Mode<AttackMode>("Mode", "Mode", AttackMode.values(), AttackMode.SINGLE);
    private final Mode<CombatUtil.TargetPriority> priority =
            new Mode<CombatUtil.TargetPriority>("Priority", "Priority", CombatUtil.TargetPriority.values(), CombatUtil.TargetPriority.DISTANCE);
    private final Option<Boolean> autoblock = new Option<Boolean>("AutoBlock", "AutoBlock", true);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Only Weapon", "OnlyWeapon", false);
    private final Option<Boolean> players = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> mobs = new Option<Boolean>("Mobs", "Mobs", true);
    private final Option<Boolean> animals = new Option<Boolean>("Animals", "Animals", true);
    private final Option<Boolean> throughWalls = new Option<Boolean>("Through Walls", "ThroughWalls", false);
    private final Option<Boolean> rotate = new Option<Boolean>("Rotate", "Rotate", true);
    private final Option<Boolean> onlyYaw = new Option<Boolean>("Only Yaw", "OnlyYaw", false);

    private int switchIndex;
    private int delayMs;
    private int targetId = -1;
    private final RotationUtil.State rotationState = new RotationUtil.State();

    public KillAura() {
        super("KillAura", Keyboard.KEY_NONE, ModuleType.Combat, "Auto attack nearby targets");
        this.addValues(rangeValue, minCps, cps, fov, yawSpeed, pitchSpeed, hurtTime, mode, priority, autoblock,
                weaponOnly, players, mobs, animals, throughWalls, rotate, onlyYaw);
        Chinese = "杀戮光环";
    }

    @Override
    public void enable() {
        target = null;
        targetId = -1;
        rotationState.reset();
        switchIndex = 0;
        delayMs = CombatUtil.nextDelay(minCps.getValue(), cps.getValue());
    }

    @Override
    public void disable() {
        target = null;
        targetId = -1;
        rotationState.reset();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame() || CombatUtil.shouldPauseForScreen()) {
            target = null;
            targetId = -1;
            rotationState.reset();
            return;
        }
        if (Boolean.TRUE.equals(weaponOnly.getValue()) && !CombatUtil.isHoldingWeapon()) {
            target = null;
            targetId = -1;
            rotationState.reset();
            return;
        }

        List<EntityLivingBase> targets = CombatUtil.collectTargets(rangeValue.getValue(), fov.getValue(),
                players.getValue(), mobs.getValue(), animals.getValue(), throughWalls.getValue());
        CombatUtil.sortTargets(targets, priority.getValue());
        if (targets.isEmpty()) {
            target = null;
            targetId = -1;
            rotationState.reset();
            return;
        }

        if (mode.getValue() == AttackMode.SWITCH) {
            if (switchIndex >= targets.size()) {
                switchIndex = 0;
            }
            target = targets.get(switchIndex);
        } else {
            target = targets.get(0);
        }

        if (target != null && target.getEntityId() != targetId) {
            targetId = target.getEntityId();
            rotationState.reset();
        }

        if (Boolean.TRUE.equals(rotate.getValue()) && target != null) {
            CombatUtil.faceEntity(target, yawSpeed.getValue().floatValue(), pitchSpeed.getValue().floatValue(),
                    Boolean.TRUE.equals(onlyYaw.getValue()), 0.18f, rotationState);
        }

        if (!timer.delay(delayMs)) {
            return;
        }

        if (mode.getValue() == AttackMode.MULTI) {
            for (EntityLivingBase entity : targets) {
                attack(entity, true);
            }
        } else {
            attack(target, false);
            if (mode.getValue() == AttackMode.SWITCH) {
                switchIndex++;
            }
        }
        delayMs = CombatUtil.nextDelay(minCps.getValue(), cps.getValue());
        timer.reset();
    }

    private void attack(EntityLivingBase entity, boolean multiAttack) {
        if (entity == null || entity.isDead || entity.getHealth() <= 0.0f) {
            return;
        }
        if (entity.hurtTime > hurtTime.getValue().intValue()) {
            return;
        }
        if (!HitSelect.shouldAttack(entity, multiAttack)) {
            return;
        }
        Criticals.tryCritical();
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, entity);
        HitSelect.onAttack(entity);
        BlockHit.onAttack(entity);
        WTap.onAttack(entity);
        if (Boolean.TRUE.equals(autoblock.getValue())) {
            blockWithSword();
        }
    }

    private void blockWithSword() {
        ItemStack stack = mc.thePlayer.getCurrentEquippedItem();
        if (stack == null || !(stack.getItem() instanceof ItemSword)) {
            return;
        }
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);
    }

    public static void assistFaceEntity(Entity entity, float yaw, float pitch) {
        CombatUtil.faceEntity(entity, yaw, pitch, pitch <= 0.0f, 0.0f);
    }

    public static float updateRotation(float current, float targetYaw, float maxTurn) {
        return CombatUtil.updateRotation(current, targetYaw, maxTurn);
    }
}
