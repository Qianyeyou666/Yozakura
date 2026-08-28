package gq.yozakura.bridge.modern;

import java.util.Locale;
import java.util.Random;

final class ModernHitSelectBridge {
    private static final Random RANDOM = new Random();
    private static final ModernHitSelectState STATE = new ModernHitSelectState();

    private ModernHitSelectBridge() {
    }

    static void onClientTick(Object player) {
        if (player == null || !ModernForgeEventBridge.enabled("HitSelect")) {
            reset();
        }
    }

    static boolean shouldAttack(Object player, Object target, boolean multiAttack) {
        if (!ModernForgeEventBridge.enabled("HitSelect")) {
            return true;
        }
        if (player == null || target == null) {
            return false;
        }
        if (!isLivingEntity(target)) {
            return true;
        }
        if (!isAlive(target)) {
            return false;
        }
        if (multiAttack && ModernForgeEventBridge.bool("HitSelect", "Allow Multi", true)) {
            return true;
        }
        if (ModernForgeEventBridge.bool("HitSelect", "Only Weapon", false)
                && !isHoldingWeapon(player)) {
            return false;
        }

        long now = System.currentTimeMillis();
        long postDelay = Math.max(0L, Math.round(ModernForgeEventBridge.number(
                "HitSelect", "Post Delay", 80.0D)));
        double chance = clamp(ModernForgeEventBridge.number("HitSelect", "Chance", 100.0D),
                0.0D, 100.0D);
        int maxHurtTime = (int) Math.round(clamp(ModernForgeEventBridge.number(
                "HitSelect", "Max HurtTime", 2.0D), 0.0D, 10.0D));
        int tradeWindow = (int) Math.round(clamp(ModernForgeEventBridge.number(
                "HitSelect", "Trade Window", 7.0D), 1.0D, 10.0D));
        return STATE.shouldAttack(now, entityId(target), hurtTime(target), hurtTime(player),
                ModernForgeEventBridge.mode("HitSelect", "Mode", "Smart"),
                maxHurtTime, tradeWindow, postDelay,
                RANDOM.nextDouble() * 100.0D <= chance, randomDelay());
    }

    static void onAttack(Object target) {
        if (!ModernForgeEventBridge.enabled("HitSelect") || target == null) {
            return;
        }
        STATE.onAttack(System.currentTimeMillis(), entityId(target), hurtTime(target), randomDelay());
    }

    static void shutdown() {
        reset();
    }

    private static long randomDelay() {
        double configuredMin = ModernForgeEventBridge.number("HitSelect", "Min Delay", 25.0D);
        double configuredMax = ModernForgeEventBridge.number("HitSelect", "Max Delay", 85.0D);
        double min = clamp(Math.min(configuredMin, configuredMax), 0.0D, 260.0D);
        double max = clamp(Math.max(configuredMin, configuredMax), min, 260.0D);
        return Math.round(min + RANDOM.nextDouble() * (max - min + 0.001D));
    }

    private static boolean isLivingEntity(Object target) {
        try {
            Class<?> living = ModernForgeEventBridge.findClass("net.minecraft.world.entity.LivingEntity");
            return living == null || living.isInstance(target);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean isAlive(Object target) {
        Object alive = ModernForgeEventBridge.invoke(target, "isAlive");
        if (alive == null) {
            alive = ModernForgeEventBridge.invoke(target, "m_6084_");
        }
        if (alive instanceof Boolean && !((Boolean) alive).booleanValue()) {
            return false;
        }
        Object health = ModernForgeEventBridge.invoke(target, "getHealth");
        if (health == null) {
            health = ModernForgeEventBridge.invoke(target, "m_21223_");
        }
        return !(health instanceof Number) || ((Number) health).floatValue() > 0.0F;
    }

    private static boolean isHoldingWeapon(Object player) {
        Object stack = ModernForgeEventBridge.invoke(player, "getMainHandItem");
        if (stack == null) {
            stack = ModernForgeEventBridge.invoke(player, "m_21205_");
        }
        if (stack == null || Boolean.TRUE.equals(firstValue(stack,
                "isEmpty", "m_41619_"))) {
            return false;
        }
        Object item = ModernForgeEventBridge.invoke(stack, "getItem");
        if (item == null) {
            item = ModernForgeEventBridge.invoke(stack, "m_41720_");
        }
        String name = item == null ? "" : item.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (name.contains("sword") || name.contains("axe")
                || name.contains("trident") || name.contains("mace")) {
            return true;
        }
        Object damageable = firstValue(stack, "isDamageableItem", "m_41763_");
        return Boolean.TRUE.equals(damageable);
    }

    private static Object firstValue(Object target, String named, String obfuscated) {
        Object value = ModernForgeEventBridge.invoke(target, named);
        return value == null ? ModernForgeEventBridge.invoke(target, obfuscated) : value;
    }

    private static int entityId(Object target) {
        Object value = ModernForgeEventBridge.invoke(target, "getId");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(target, "m_19879_");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(target, "id");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(target, "f_19848_");
        }
        return value instanceof Number ? ((Number) value).intValue() : System.identityHashCode(target);
    }

    private static int hurtTime(Object target) {
        Object value = ModernForgeEventBridge.field(target, "hurtTime");
        if (value == null) {
            value = ModernForgeEventBridge.field(target, "f_20916_");
        }
        if (value == null) {
            value = ModernForgeEventBridge.invoke(target, "getHurtTime");
        }
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void reset() {
        STATE.reset();
    }
}
