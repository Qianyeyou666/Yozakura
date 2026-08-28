package gq.yozakura.bridge.modern;

import java.util.List;
import java.util.Locale;

final class ModernBowAimBotBridge {
    private static final ModernVisibleAimState ROTATION_STATE = new ModernVisibleAimState();
    private static Object target;
    private static double rangeAimVelocity;

    private ModernBowAimBotBridge() {
    }

    static void onClientTick(Object event) {
        try {
            if (!isEndPhase(event)) {
                return;
            }
            Object minecraft = ModernMinecraftAccess.minecraft();
            Object player = ModernMinecraftAccess.player(minecraft);
            Object level = ModernMinecraftAccess.level(minecraft);
            if (!ModernForgeEventBridge.enabled("BowAimBot")
                    || minecraft == null || player == null || level == null
                    || !isUsingItem(player)) {
                reset();
                return;
            }

            Object usedStack = useItem(player);
            if (!isBow(usedStack)) {
                reset();
                return;
            }

            target = closestTarget(minecraft, player);
            if (target == null) {
                resetRotation();
                return;
            }

            int useTicks = useTicks(player, usedStack);
            rangeAimVelocity = ModernAimMath.bowVelocity(useTicks);
            if (rangeAimVelocity < 0.12D) {
                return;
            }

            double prediction = ModernForgeEventBridge.number(
                    "BowAimBot", "Prediction", 0.55D);
            ModernAimMath.PredictedPoint point = ModernAimMath.predict(
                    ModernRotationBridge.x(target),
                    ModernRotationBridge.y(target),
                    ModernRotationBridge.z(target),
                    previous(target, "xo", "f_19854_", ModernRotationBridge.x(target)),
                    previous(target, "yo", "f_19855_", ModernRotationBridge.y(target)),
                    previous(target, "zo", "f_19856_", ModernRotationBridge.z(target)),
                    ModernRotationBridge.eyeHeight(target), prediction);

            double playerX = ModernRotationBridge.x(player);
            double playerY = ModernRotationBridge.y(player) + ModernRotationBridge.eyeHeight(player);
            double playerZ = ModernRotationBridge.z(player);
            ModernAimMath.BallisticSolution solution = ModernAimMath.solveLowArc(
                    point.getX() - playerX,
                    point.getY() - playerY,
                    point.getZ() - playerZ,
                    rangeAimVelocity,
                    ModernAimMath.DEFAULT_BOW_GRAVITY);
            if (!solution.isReachable()) {
                return;
            }

            float yawSpeed = (float) ModernForgeEventBridge.number(
                    "BowAimBot", "Yaw Speed", 24.0D);
            float pitchSpeed = (float) ModernForgeEventBridge.number(
                    "BowAimBot", "Pitch Speed", 18.0D);
            float[] rotation = ROTATION_STATE.update(
                    ModernRotationBridge.yaw(player),
                    ModernRotationBridge.pitch(player),
                    solution.getYaw(), solution.getPitch(),
                    yawSpeed, pitchSpeed);
            ModernRotationBridge.applyVisibleRotation(player, rotation[0], rotation[1]);
        } catch (Throwable throwable) {
            reset();
            ModernForgeEventBridge.log("Modern BowAimBot tick failed", throwable);
        }
    }

    private static Object closestTarget(Object minecraft, Object player) {
        List<Object> entities = ModernMinecraftAccess.livingEntities(minecraft);
        Object closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Object entity : entities) {
            if (entity == null || entity == player || isArmorStand(entity)
                    || !isAlive(entity)
                    || ModernCombatBridge.isProbablyBotForBridge(entity)
                    || !hasLineOfSight(player, entity)) {
                continue;
            }
            double distance = ModernRotationBridge.distanceSq(player, entity);
            if (distance < closestDistance) {
                closest = entity;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private static boolean isEndPhase(Object event) {
        Object phase = ModernForgeEventBridge.field(event, "phase");
        if (phase == null) {
            phase = ModernForgeEventBridge.invoke(event, "phase");
        }
        if (phase == null) {
            phase = ModernForgeEventBridge.invoke(event, "getPhase");
        }
        return phase == null || "END".equals(String.valueOf(phase));
    }

    private static boolean isUsingItem(Object player) {
        Object value = ModernForgeEventBridge.invoke(player, "isUsingItem");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(player, "m_6117_");
        }
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static Object useItem(Object player) {
        Object stack = ModernForgeEventBridge.invoke(player, "getUseItem");
        if (stack == null) {
            stack = ModernForgeEventBridge.invoke(player, "m_21211_");
        }
        return stack;
    }

    private static boolean isBow(Object stack) {
        if (stack == null || isEmptyStack(stack)) {
            return false;
        }
        Object item = ModernForgeEventBridge.invoke(stack, "getItem");
        if (item == null) {
            item = ModernForgeEventBridge.invoke(stack, "m_41720_");
        }
        if (item == null) {
            return false;
        }
        try {
            Class<?> bow = ModernForgeEventBridge.findClass(
                    "net.minecraft.world.item.BowItem");
            if (bow.isInstance(item)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return item.getClass().getName().toLowerCase(Locale.ROOT).contains("bowitem");
    }

    private static boolean isEmptyStack(Object stack) {
        Object value = ModernForgeEventBridge.invoke(stack, "isEmpty");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(stack, "m_41619_");
        }
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static int useTicks(Object player, Object stack) {
        Object elapsed = ModernForgeEventBridge.invoke(player, "getTicksUsingItem");
        if (elapsed == null) {
            elapsed = ModernForgeEventBridge.invoke(player, "m_21252_");
        }
        if (elapsed instanceof Number) {
            return Math.max(0, ((Number) elapsed).intValue());
        }

        Object remaining = ModernForgeEventBridge.invoke(player, "getUseItemRemainingTicks");
        if (remaining == null) {
            remaining = ModernForgeEventBridge.invoke(player, "m_21212_");
        }
        Object duration = ModernForgeEventBridge.invoke(stack, "getUseDuration");
        if (duration == null) {
            duration = ModernForgeEventBridge.invoke(stack, "m_41779_");
        }
        if (!(duration instanceof Number)) {
            Object item = ModernForgeEventBridge.invoke(stack, "getItem");
            if (item == null) {
                item = ModernForgeEventBridge.invoke(stack, "m_41720_");
            }
            duration = ModernForgeEventBridge.invoke(item, "getUseDuration", stack);
            if (duration == null) {
                duration = ModernForgeEventBridge.invoke(item, "m_8105_", stack);
            }
        }
        if (remaining instanceof Number && duration instanceof Number) {
            return Math.max(0, ((Number) duration).intValue()
                    - ((Number) remaining).intValue());
        }
        return 0;
    }

    private static boolean isArmorStand(Object entity) {
        String name = entity.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("armorstand");
    }

    private static boolean isAlive(Object entity) {
        Object alive = ModernForgeEventBridge.invoke(entity, "isAlive");
        if (alive == null) {
            alive = ModernForgeEventBridge.invoke(entity, "m_6084_");
        }
        return !(alive instanceof Boolean) || ((Boolean) alive).booleanValue();
    }

    private static boolean hasLineOfSight(Object player, Object entity) {
        Object visible = ModernForgeEventBridge.invoke(player, "hasLineOfSight", entity);
        if (visible == null) {
            visible = ModernForgeEventBridge.invoke(player, "m_142582_", entity);
        }
        if (visible == null) {
            visible = ModernForgeEventBridge.invoke(player, "m_148306_", entity);
        }
        return !(visible instanceof Boolean) || ((Boolean) visible).booleanValue();
    }

    private static double previous(Object entity, String named, String obfuscated,
                                   double fallback) {
        Object value = ModernForgeEventBridge.field(entity, named);
        if (value == null) {
            value = ModernForgeEventBridge.field(entity, obfuscated);
        }
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    static void shutdown() {
        reset();
    }

    private static void resetRotation() {
        ROTATION_STATE.reset();
        target = null;
        rangeAimVelocity = 0.0D;
    }

    private static void reset() {
        resetRotation();
    }
}
