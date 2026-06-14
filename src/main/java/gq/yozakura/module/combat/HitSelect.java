package gq.yozakura.module.combat;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class HitSelect extends Module {
    public enum SelectMode {
        VULNERABLE,
        TRADE,
        SMART
    }

    private static HitSelect INSTANCE;

    private final Mode<SelectMode> mode = new Mode<SelectMode>("Mode", "Mode", SelectMode.values(), SelectMode.SMART);
    private final Numbers<Double> maxHurtTime = new Numbers<Double>("Max HurtTime", "MaxHurtTime", 2.0, 0.0, 10.0, 1.0);
    private final Numbers<Double> minDelay = new Numbers<Double>("Min Delay", "MinDelay", 25.0, 0.0, 180.0, 5.0);
    private final Numbers<Double> maxDelay = new Numbers<Double>("Max Delay", "MaxDelay", 85.0, 0.0, 260.0, 5.0);
    private final Numbers<Double> chance = new Numbers<Double>("Chance", "Chance", 100.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> tradeWindow = new Numbers<Double>("Trade Window", "TradeWindow", 7.0, 1.0, 10.0, 1.0);
    private final Numbers<Double> postAttackDelay = new Numbers<Double>("Post Delay", "PostDelay", 80.0, 0.0, 220.0, 5.0);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Only Weapon", "OnlyWeapon", false);
    private final Option<Boolean> ignoreAuraMulti = new Option<Boolean>("Allow Multi", "AllowMulti", true);

    private int selectedEntityId = -1;
    private int lastHurtTime = -1;
    private long nextAllowedAt;
    private long lastAttackAt;

    public HitSelect() {
        super("HitSelect", Keyboard.KEY_NONE, ModuleType.Combat, "Select smarter attack timing for combat modules");
        this.addValues(mode, maxHurtTime, minDelay, maxDelay, chance, tradeWindow, postAttackDelay, weaponOnly, ignoreAuraMulti);
        Chinese = "智能选刀";
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

    public static boolean shouldAttack(Entity entity) {
        if (INSTANCE == null || !INSTANCE.getState() || !(entity instanceof EntityLivingBase)) {
            return true;
        }
        return INSTANCE.canAttack((EntityLivingBase) entity);
    }

    public static boolean shouldAttack(Entity entity, boolean multiAttack) {
        if (INSTANCE == null || !INSTANCE.getState()) {
            return true;
        }
        if (multiAttack && Boolean.TRUE.equals(INSTANCE.ignoreAuraMulti.getValue())) {
            return true;
        }
        return shouldAttack(entity);
    }

    public static void onAttack(Entity entity) {
        if (INSTANCE != null && INSTANCE.getState() && entity instanceof EntityLivingBase) {
            INSTANCE.afterAttack((EntityLivingBase) entity);
        }
    }

    private boolean canAttack(EntityLivingBase target) {
        if (!isInGame() || target == null || target.isDead || target.getHealth() <= 0.0f) {
            return false;
        }
        if (Boolean.TRUE.equals(weaponOnly.getValue()) && !CombatUtil.isHoldingWeapon()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (target.getEntityId() != selectedEntityId) {
            selectedEntityId = target.getEntityId();
            lastHurtTime = target.hurtTime;
            nextAllowedAt = now + randomDelay();
        } else if (target.hurtTime > lastHurtTime) {
            nextAllowedAt = now + randomDelay();
            lastHurtTime = target.hurtTime;
        } else {
            lastHurtTime = target.hurtTime;
        }

        if (now - lastAttackAt < postAttackDelay.getValue().longValue()) {
            return false;
        }
        if (ThreadLocalRandom.current().nextDouble(100.0D) > chance.getValue()) {
            return false;
        }

        SelectMode currentMode = mode.getValue();
        if (currentMode == SelectMode.TRADE && isTradeWindow()) {
            return now >= nextAllowedAt;
        }
        if (currentMode == SelectMode.SMART && isTradeWindow() && target.hurtTime <= maxHurtTime.getValue() + 2) {
            return now >= nextAllowedAt;
        }
        return target.hurtTime <= maxHurtTime.getValue().intValue() && now >= nextAllowedAt;
    }

    private boolean isTradeWindow() {
        return mc.thePlayer != null && mc.thePlayer.hurtTime > 0 && mc.thePlayer.hurtTime <= tradeWindow.getValue().intValue();
    }

    private void afterAttack(EntityLivingBase target) {
        selectedEntityId = target.getEntityId();
        lastHurtTime = target.hurtTime;
        lastAttackAt = System.currentTimeMillis();
        nextAllowedAt = lastAttackAt + randomDelay();
    }

    private long randomDelay() {
        double min = Math.min(minDelay.getValue(), maxDelay.getValue());
        double max = Math.max(minDelay.getValue(), maxDelay.getValue());
        return Math.round(min + ThreadLocalRandom.current().nextDouble() * (max - min + 0.001D));
    }

    private void reset() {
        selectedEntityId = -1;
        lastHurtTime = -1;
        nextAllowedAt = 0L;
        lastAttackAt = 0L;
    }
}
