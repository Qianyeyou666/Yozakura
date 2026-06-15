package gq.vapulite.module.combat;

import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class KnockbackDelay extends Module {
    private static KnockbackDelay INSTANCE;

    private final Numbers<Double> delayMs = new Numbers<Double>("Delay MS", "DelayMS", 95.0, 0.0, 420.0, 5.0);
    private final Numbers<Double> jitterMs = new Numbers<Double>("Jitter MS", "JitterMS", 35.0, 0.0, 220.0, 5.0);
    private final Numbers<Double> chance = new Numbers<Double>("Chance", "Chance", 100.0, 0.0, 100.0, 1.0);
    private final Option<Boolean> onlyWeapon = new Option<Boolean>("Only Weapon", "OnlyWeapon", false);
    private final Option<Boolean> requireMoving = new Option<Boolean>("Require Moving", "RequireMoving", false);
    private final Option<Boolean> groundOnly = new Option<Boolean>("Ground Only", "GroundOnly", false);

    private int lastHurtTime;
    private long blockAttacksUntil;

    public KnockbackDelay() {
        super("KnockbackDelay", Keyboard.KEY_NONE, ModuleType.Combat,
                "Delay attacks briefly after taking knockback");
        this.addValues(delayMs, jitterMs, chance, onlyWeapon, requireMoving, groundOnly);
        Chinese = "受击延迟";
        INSTANCE = this;
    }

    @Override
    public void enable() {
        reset();
    }

    @Override
    public void disable() {
        reset();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame()) {
            reset();
            return;
        }

        int hurtTime = mc.thePlayer.hurtTime;
        if (hurtTime <= 0) {
            lastHurtTime = 0;
            return;
        }
        if (hurtTime > lastHurtTime) {
            lastHurtTime = hurtTime;
            if (canStartDelay() && ThreadLocalRandom.current().nextDouble(100.0D) <= chance.getValue()) {
                blockAttacksUntil = System.currentTimeMillis() + randomDelay();
            }
        } else {
            lastHurtTime = hurtTime;
        }
    }

    public static boolean shouldAttack(Entity target) {
        return INSTANCE == null || !INSTANCE.getState() || INSTANCE.canAttack(target);
    }

    private boolean canAttack(Entity target) {
        if (!isInGame() || target == null) {
            return true;
        }
        if (Boolean.TRUE.equals(onlyWeapon.getValue()) && !CombatUtil.isHoldingWeapon()) {
            return true;
        }
        if (Boolean.TRUE.equals(groundOnly.getValue()) && !mc.thePlayer.onGround) {
            return true;
        }
        if (Boolean.TRUE.equals(requireMoving.getValue()) && !isMoving()) {
            return true;
        }
        return System.currentTimeMillis() >= blockAttacksUntil;
    }

    private boolean canStartDelay() {
        if (Boolean.TRUE.equals(onlyWeapon.getValue()) && !CombatUtil.isHoldingWeapon()) {
            return false;
        }
        if (Boolean.TRUE.equals(groundOnly.getValue()) && !mc.thePlayer.onGround) {
            return false;
        }
        return !Boolean.TRUE.equals(requireMoving.getValue()) || isMoving();
    }

    private boolean isMoving() {
        return mc.thePlayer != null
                && (Math.abs(mc.thePlayer.motionX) + Math.abs(mc.thePlayer.motionZ) > 0.02D
                || mc.thePlayer.moveForward != 0.0F
                || mc.thePlayer.moveStrafing != 0.0F);
    }

    private long randomDelay() {
        long base = Math.max(0L, delayMs.getValue().longValue());
        long jitter = Math.max(0L, jitterMs.getValue().longValue());
        if (jitter == 0L) {
            return base;
        }
        long offset = ThreadLocalRandom.current().nextLong(jitter * 2L + 1L) - jitter;
        return Math.max(0L, base + offset);
    }

    private void reset() {
        lastHurtTime = 0;
        blockAttacksUntil = 0L;
    }
}
