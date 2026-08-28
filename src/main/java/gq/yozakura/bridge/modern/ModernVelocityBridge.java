package gq.yozakura.bridge.modern;

import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

final class ModernVelocityBridge {
    private static final int MAX_PENDING_PACKETS = 32;
    private static final Queue<Object> PENDING_PACKETS = new ConcurrentLinkedQueue<Object>();

    private static int attackWindowTicks;
    private static int chanceAccumulator;

    private ModernVelocityBridge() {
    }

    static boolean needsPacketObservation() {
        return ModernForgeEventBridge.enabled("Velocity")
                && "Attack".equalsIgnoreCase(ModernForgeEventBridge.mode("Velocity", "Mode", "Reduce"));
    }

    static void onIncoming(Object packet) {
        if (packet == null || !needsPacketObservation()) {
            return;
        }
        String name = packet.getClass().getName().toLowerCase(Locale.ROOT);
        if (!name.contains("clientboundsetentitymotionpacket") && !name.contains("s12packetentityvelocity")) {
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
        if (player == null || !needsPacketObservation()) {
            resetWindow();
            return;
        }
        boolean armed = drainPackets(player);
        if (!armed && attackWindowTicks > 0) {
            attackWindowTicks--;
        }
    }

    static boolean applyAttackSlowdown(Object player, Object target,
                                       double motionX, double motionY, double motionZ,
                                       boolean motionAvailable) {
        if (attackWindowTicks <= 0 || player == null || target == null || !needsPacketObservation()) {
            return false;
        }
        if (ModernForgeEventBridge.bool("Velocity", "Only Sprinting", false) && !isSprinting(player)) {
            return false;
        }
        if (ModernForgeEventBridge.bool("Velocity", "Require KillAura", false)
                && !ModernForgeEventBridge.enabled("KillAura")) {
            return false;
        }
        if (ModernForgeEventBridge.bool("Velocity", "Players Only", true) && !isPlayer(target)) {
            return false;
        }
        double range = Math.max(1.0D, ModernForgeEventBridge.number("Velocity", "Attack Range", 3.0D));
        if (ModernRotationBridge.distanceSq(player, target) > range * range) {
            return false;
        }
        if (!acceptChance((int) Math.round(
                ModernForgeEventBridge.number("Velocity", "Chance", 100.0D)))) {
            return false;
        }

        if (!motionAvailable) {
            return false;
        }
        attackWindowTicks = 0;
        setDeltaMovement(player, motionX * 0.6D, motionY, motionZ * 0.6D);
        setSprinting(player, false);
        return true;
    }

    static void shutdown() {
        resetWindow();
        chanceAccumulator = 0;
    }

    private static boolean drainPackets(Object player) {
        Object packet;
        int playerId = ModernRotationBridge.entityId(player);
        boolean armed = false;
        while ((packet = PENDING_PACKETS.poll()) != null) {
            Object value = firstValue(packet,
                    new String[]{"getId", "getEntityId", "m_132182_", "func_149412_c"},
                    new String[]{"id", "entityId", "f_132177_", "field_149417_a"});
            if (intValue(value, -1) != playerId) {
                continue;
            }
            int timeout = (int) Math.round(
                    ModernForgeEventBridge.number("Velocity", "Attack Timeout", 2.0D));
            attackWindowTicks = Math.max(1, Math.min(6, timeout));
            armed = true;
        }
        return armed;
    }

    private static void resetWindow() {
        PENDING_PACKETS.clear();
        attackWindowTicks = 0;
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

    private static boolean isSprinting(Object player) {
        Object value = ModernForgeEventBridge.invoke(player, "isSprinting");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(player, "m_6067_");
        }
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static boolean isPlayer(Object entity) {
        try {
            Class<?> playerClass = ModernForgeEventBridge.findClass("net.minecraft.world.entity.player.Player");
            return playerClass.isInstance(entity);
        } catch (Throwable ignored) {
            String name = entity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            return name.contains("player");
        }
    }

    private static void setDeltaMovement(Object player, double x, double y, double z) {
        Object result = ModernForgeEventBridge.invoke(player, "setDeltaMovement",
                Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
        if (result == null) {
            ModernForgeEventBridge.invoke(player, "m_20256_",
                    Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
        }
    }

    private static void setSprinting(Object player, boolean sprinting) {
        ModernForgeEventBridge.invoke(player, "setSprinting", Boolean.valueOf(sprinting));
        ModernForgeEventBridge.invoke(player, "m_6858_", Boolean.valueOf(sprinting));
    }

    private static Object firstValue(Object target, String[] methods, String[] fields) {
        for (String method : methods) {
            Object value = ModernForgeEventBridge.invoke(target, method);
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
