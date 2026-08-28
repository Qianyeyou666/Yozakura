package gq.yozakura.bridge.modern;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

final class ModernJumpResetBridge {
    private static final int JUMP_TICKS = 2;
    private static final int FORWARD_TICKS = 3;
    private static final int MAX_PENDING_PACKETS = 64;
    private static final Queue<Object> PENDING_PACKETS = new ConcurrentLinkedQueue<Object>();

    private static int ticksSinceVelocity = -1;
    private static int chanceAccumulator;
    private static boolean hurtConfirmed;
    private static Object forcedJumpKey;

    private ModernJumpResetBridge() {
    }

    static void onIncoming(Object packet, Object level, Object player) {
        if (packet == null || level == null || player == null
                || !ModernForgeEventBridge.enabled("JumpReset")) {
            return;
        }
        String name = packet.getClass().getName().toLowerCase(Locale.ROOT);
        if (!name.contains("clientboundsetentitymotionpacket")
                && !name.contains("s12packetentityvelocity")
                && !name.contains("clientboundentityeventpacket")
                && !name.contains("s19packetentitystatus")) {
            return;
        }
        if (PENDING_PACKETS.size() >= MAX_PENDING_PACKETS) {
            PENDING_PACKETS.poll();
        }
        PENDING_PACKETS.offer(packet);
    }

    static void onClientTick(Object event) {
        if (!isEndPhase(event)) {
            return;
        }
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object player = ModernMinecraftAccess.player(minecraft);
        Object options = ModernMinecraftAccess.options(minecraft);
        if (player == null || options == null || !ModernForgeEventBridge.enabled("JumpReset")) {
            reset(options);
            return;
        }
        if (resetBlocked(minecraft)) {
            reset(options);
            return;
        }

        drainPackets(ModernMinecraftAccess.level(minecraft), player);
        if (ticksSinceVelocity < 0) {
            return;
        }
        ticksSinceVelocity++;
        if (ticksSinceVelocity <= JUMP_TICKS && ModernRotationBridge.onGround(player)) {
            forceJump(options);
        }
        if (ticksSinceVelocity > FORWARD_TICKS) {
            closeWindow(options);
        }
    }

    static void onMovementInput(Object event) {
        if (ticksSinceVelocity <= 0 || ticksSinceVelocity > FORWARD_TICKS
                || !ModernForgeEventBridge.enabled("JumpReset")
                || !ModernForgeEventBridge.bool("JumpReset", "Force Forward", true)) {
            return;
        }
        Object minecraft = ModernMinecraftAccess.minecraft();
        if (resetBlocked(minecraft)) {
            reset(ModernMinecraftAccess.options(minecraft));
            return;
        }
        Object player = ModernMinecraftAccess.player(minecraft);
        Object entity = ModernForgeEventBridge.invoke(event, "getEntity");
        Object input = ModernForgeEventBridge.invoke(event, "getInput");
        if (input == null || (entity != null && entity != player)) {
            return;
        }
        setFloat(input, 1.0F, "forwardImpulse", "moveForward", "zza");
        setBoolean(input, true, "up", "forwardKeyDown");
    }

    static void shutdown() {
        Object minecraft = ModernMinecraftAccess.minecraft();
        reset(ModernMinecraftAccess.options(minecraft));
        chanceAccumulator = 0;
        hurtConfirmed = false;
    }

    private static void drainPackets(Object level, Object player) {
        Object packet;
        while ((packet = PENDING_PACKETS.poll()) != null) {
            String name = packet.getClass().getName().toLowerCase(Locale.ROOT);
            if (name.contains("clientboundentityeventpacket") || name.contains("s19packetentitystatus")) {
                Object entity = firstValue(packet,
                        new String[]{"getEntity", "m_132978_", "func_149161_a"},
                        new String[0], level);
                int eventId = intValue(firstValue(packet,
                        new String[]{"getEventId", "m_132977_", "getOpCode", "func_149160_c"},
                        new String[]{"eventId", "event", "f_132974_", "field_149164_b"}), -1);
                if (entity == player && eventId == 2) {
                    hurtConfirmed = true;
                }
                continue;
            }
            int entityId = intValue(firstValue(packet,
                    new String[]{"getId", "getEntityId", "m_132182_", "func_149412_c"},
                    new String[]{"id", "entityId", "f_132177_", "field_149417_a"}), -1);
            if (entityId != ModernRotationBridge.entityId(player)) {
                continue;
            }
            boolean requireHurt = ModernForgeEventBridge.bool("JumpReset", "Fake Check", false);
            if (requireHurt && !hurtConfirmed) {
                continue;
            }
            hurtConfirmed = false;
            if (acceptChance((int) Math.round(
                    ModernForgeEventBridge.number("JumpReset", "Chance", 100.0D)))) {
                ticksSinceVelocity = 0;
            }
        }
    }

    private static boolean acceptChance(int chance) {
        int safeChance = Math.max(0, Math.min(100, chance));
        if (safeChance == 0) {
            return false;
        }
        if (safeChance == 100) {
            return true;
        }
        chanceAccumulator += safeChance;
        if (chanceAccumulator < 100) {
            return false;
        }
        chanceAccumulator -= 100;
        return true;
    }

    private static void forceJump(Object options) {
        Object jumpKey = ModernInputBridge.key(options, "jump");
        if (jumpKey != null && ModernInputBridge.setKeyDown(jumpKey, true)) {
            forcedJumpKey = jumpKey;
        }
    }

    private static void closeWindow(Object options) {
        Object jumpKey = forcedJumpKey != null ? forcedJumpKey : ModernInputBridge.key(options, "jump");
        if (jumpKey != null && forcedJumpKey != null) {
            boolean physicalDown = ModernInputBridge.physicalDown(jumpKey);
            ModernInputBridge.setKeyDown(jumpKey, physicalDown);
        }
        forcedJumpKey = null;
        ticksSinceVelocity = -1;
    }

    private static void reset(Object options) {
        PENDING_PACKETS.clear();
        closeWindow(options);
        hurtConfirmed = false;
    }

    private static boolean resetBlocked(Object minecraft) {
        if (ModernForgeEventBridge.enabled("Scaffold")) {
            return true;
        }
        Object screen = ModernForgeEventBridge.field(minecraft, "screen");
        if (screen == null) {
            screen = ModernForgeEventBridge.field(minecraft, "f_91080_");
        }
        String name = screen == null ? "" : screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("inventory");
    }

    private static Object firstValue(Object target, String[] methods, String[] fields, Object... args) {
        if (target == null) {
            return null;
        }
        for (String method : methods) {
            Object value = ModernForgeEventBridge.invoke(target, method, args);
            if (value != null) {
                return value;
            }
        }
        for (String field : fields) {
            Object value = ModernForgeEventBridge.field(target, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static void setFloat(Object target, float value, String... names) {
        Field field = findField(target, names);
        if (field == null) {
            return;
        }
        try {
            if (field.getType() == float.class) {
                field.setFloat(target, value);
            } else {
                field.set(target, Float.valueOf(value));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setBoolean(Object target, boolean value, String... names) {
        Field field = findField(target, names);
        if (field == null) {
            return;
        }
        try {
            if (field.getType() == boolean.class) {
                field.setBoolean(target, value);
            } else {
                field.set(target, Boolean.valueOf(value));
            }
        } catch (Throwable ignored) {
        }
    }

    private static Field findField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            Field field = ModernRotationBridge.findField(target.getClass(), name);
            if (field != null) {
                try {
                    field.setAccessible(true);
                    return field;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
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

}
