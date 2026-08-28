package gq.yozakura.bridge.modern;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class ModernRotationBridge {
    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<String, Class<?>>();
    private static final long SILENT_ROTATION_TIMEOUT_MS = 260L;
    private static volatile boolean silentRotationActive;
    private static volatile boolean silentMoveFix;
    private static volatile float silentYaw;
    private static volatile float silentPitch;
    private static volatile long silentRotationAt;

    private ModernRotationBridge() {
    }

    static float[] rotationsToEntity(Object player, Object target) {
        double eyeX = x(player);
        double eyeY = y(player) + eyeHeight(player);
        double eyeZ = z(player);
        double targetX = x(target);
        double targetY = y(target) + height(target) * 0.62D;
        double targetZ = z(target);
        return rotationsTo(eyeX, eyeY, eyeZ, targetX, targetY, targetZ);
    }

    static float[] rotationsTo(double eyeX, double eyeY, double eyeZ, double targetX, double targetY, double targetZ) {
        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-6D && Math.abs(dy) < 1.0E-6D) {
            return null;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, distance)));
        return new float[]{yaw, clampPitch(pitch)};
    }

    static void applyVisibleRotation(Object player, float yaw, float pitch) {
        if (player == null) {
            return;
        }
        float safePitch = clampPitch(pitch);
        ModernForgeEventBridge.invoke(player, "setYRot", Float.valueOf(yaw));
        ModernForgeEventBridge.invoke(player, "m_146922_", Float.valueOf(yaw));
        ModernForgeEventBridge.invoke(player, "setXRot", Float.valueOf(safePitch));
        ModernForgeEventBridge.invoke(player, "m_146926_", Float.valueOf(safePitch));
        ModernForgeEventBridge.invoke(player, "setYHeadRot", Float.valueOf(yaw));
        ModernForgeEventBridge.invoke(player, "m_5616_", Float.valueOf(yaw));
        setFloat(player, yaw, "yRot", "rotationYaw", "f_19857_");
        setFloat(player, safePitch, "xRot", "rotationPitch", "f_19858_");
        setFloat(player, yaw, "yHeadRot", "rotationYawHead", "f_20885_");
        setFloat(player, yaw, "yBodyRot", "renderYawOffset", "f_20883_");
        setFloat(player, yaw, "yHeadRotO", "prevRotationYawHead", "f_20886_");
        setFloat(player, safePitch, "xRotO", "prevRotationPitch", "f_19860_");
        setFloat(player, yaw, "yBodyRotO", "prevRenderYawOffset", "f_20884_");
    }

    static boolean sendRotationPacket(Object player, float yaw, float pitch) {
        Object packet = createRotationPacket(player, yaw, pitch);
        return packet != null && sendPacket(player, packet);
    }

    static Object createRotationPacket(Object player, float yaw, float pitch) {
        try {
            Class<?> packetClass = classForName("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Rot");
            Constructor<?> constructor = packetClass.getDeclaredConstructor(float.class, float.class, boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(Float.valueOf(yaw), Float.valueOf(clampPitch(pitch)),
                    Boolean.valueOf(onGround(player)));
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean sendPositionPacket(Object player, double px, double py, double pz, boolean onGround) {
        try {
            Class<?> packetClass = classForName("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Pos");
            Constructor<?> constructor = packetClass.getDeclaredConstructor(double.class, double.class, double.class, boolean.class);
            constructor.setAccessible(true);
            Object packet = constructor.newInstance(Double.valueOf(px), Double.valueOf(py), Double.valueOf(pz),
                    Boolean.valueOf(onGround));
            ModernPacketBridge.markBypass(packet);
            return sendPacket(player, packet);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean sendPacket(Object player, Object packet) {
        if (player == null || packet == null) {
            return false;
        }
        Object connection = ModernForgeEventBridge.field(player, "connection");
        if (connection == null) {
            connection = ModernForgeEventBridge.field(player, "f_108617_");
        }
        if (connection == null) {
            Object minecraft = ModernMinecraftAccess.minecraft();
            connection = ModernForgeEventBridge.invoke(minecraft, "getConnection");
            if (connection == null) {
                connection = ModernForgeEventBridge.invoke(minecraft, "m_91403_");
            }
        }
        if (connection == null) {
            return false;
        }
        ModernPacketBridge.markBypass(packet);
        ModernForgeEventBridge.invoke(connection, "send", packet);
        ModernForgeEventBridge.invoke(connection, "m_104955_", packet);
        return true;
    }

    static void requestSilentRotation(Object player, float yaw, float pitch, boolean moveFix) {
        if (player == null) {
            clearSilentRotation();
            return;
        }
        silentYaw = yaw;
        silentPitch = clampPitch(pitch);
        silentMoveFix = moveFix;
        silentRotationAt = System.currentTimeMillis();
        silentRotationActive = true;
        setFloat(player, yaw, "yHeadRot", "rotationYawHead", "f_20885_");
        setFloat(player, yaw, "yBodyRot", "renderYawOffset", "f_20883_");
    }

    static void clearSilentRotation() {
        silentRotationActive = false;
        silentMoveFix = false;
        silentRotationAt = 0L;
    }

    static boolean hasSilentRotation() {
        return silentRotationActive && System.currentTimeMillis() - silentRotationAt <= SILENT_ROTATION_TIMEOUT_MS;
    }

    static float silentYaw(Object player) {
        return hasSilentRotation() ? silentYaw : yaw(player);
    }

    static float silentPitch(Object player) {
        return hasSilentRotation() ? silentPitch : pitch(player);
    }

    static boolean isMovePlayerPacket(Object packet) {
        return className(packet).contains("serverboundmoveplayerpacket");
    }

    static Object rewriteMovePlayerPacket(Object packet, Object player, boolean forceRotation) {
        if (packet == null || player == null || (!forceRotation && !hasSilentRotation())) {
            return packet;
        }
        if (!isMovePlayerPacket(packet)) {
            return packet;
        }
        boolean hasPos = packetHasPosition(packet);
        boolean hasRot = forceRotation || packetHasRotation(packet) || hasSilentRotation();
        if (!hasRot) {
            return packet;
        }
        double px = packetDouble(packet, x(player), "getX", "m_134129_", "x", "f_134117_");
        double py = packetDouble(packet, y(player), "getY", "m_134133_", "y", "f_134118_");
        double pz = packetDouble(packet, z(player), "getZ", "m_134140_", "z", "f_134119_");
        boolean ground = packetBoolean(packet, onGround(player), "isOnGround", "m_134139_", "onGround", "f_134123_");
        Object rewritten = createMovePacket(player, hasPos, px, py, pz, silentYaw(player), silentPitch(player), ground);
        return rewritten == null ? packet : rewritten;
    }

    static Object createMovePacket(Object player, boolean hasPos, double px, double py, double pz,
                                   float yaw, float pitch, boolean ground) {
        try {
            Class<?> packetClass = classForName(hasPos
                    ? "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$PosRot"
                    : "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Rot");
            Constructor<?> constructor = hasPos
                    ? packetClass.getDeclaredConstructor(double.class, double.class, double.class, float.class, float.class, boolean.class)
                    : packetClass.getDeclaredConstructor(float.class, float.class, boolean.class);
            constructor.setAccessible(true);
            Object packet = hasPos
                    ? constructor.newInstance(Double.valueOf(px), Double.valueOf(py), Double.valueOf(pz),
                    Float.valueOf(yaw), Float.valueOf(clampPitch(pitch)), Boolean.valueOf(ground))
                    : constructor.newInstance(Float.valueOf(yaw), Float.valueOf(clampPitch(pitch)), Boolean.valueOf(ground));
            return packet;
        } catch (Throwable ignored) {
            return createRotationPacket(player, yaw, pitch);
        }
    }

    static void applyMoveFix(Object input, Object player) {
        if (input == null || player == null || !silentMoveFix || !hasSilentRotation()) {
            return;
        }
        float forward = inputFloat(input, "forwardImpulse", "moveForward", "zza");
        float strafe = inputFloat(input, "leftImpulse", "moveStrafe", "xxa");
        if (Math.abs(forward) < 0.01F && Math.abs(strafe) < 0.01F) {
            return;
        }
        float visualYaw = yaw(player);
        float serverYaw = silentYaw(player);
        float wanted = movementYaw(visualYaw, forward, strafe);
        float bestForward = sign(forward);
        float bestStrafe = sign(strafe);
        float bestDelta = 999.0F;
        for (int f = -1; f <= 1; f++) {
            for (int s = -1; s <= 1; s++) {
                if (f == 0 && s == 0) {
                    continue;
                }
                float candidate = movementYaw(serverYaw, f, s);
                float delta = angleDelta(wanted, candidate);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    bestForward = f;
                    bestStrafe = s;
                }
            }
        }
        setInputFloat(input, bestForward, "forwardImpulse", "moveForward", "zza");
        setInputFloat(input, bestStrafe, "leftImpulse", "moveStrafe", "xxa");
    }

    static float smoothAngle(float current, float target, float step) {
        float delta = wrapDegrees(target - current);
        if (delta > step) {
            delta = step;
        } else if (delta < -step) {
            delta = -step;
        }
        return current + delta;
    }

    static float angleDelta(float first, float second) {
        return Math.abs(wrapDegrees(first - second));
    }

    static float wrapDegrees(float value) {
        value = value % 360.0F;
        if (value >= 180.0F) {
            value -= 360.0F;
        }
        if (value < -180.0F) {
            value += 360.0F;
        }
        return value;
    }

    static float clampPitch(float pitch) {
        return Math.max(-90.0F, Math.min(90.0F, pitch));
    }

    static double distanceSq(Object first, Object second) {
        double dx = x(first) - x(second);
        double dy = y(first) - y(second);
        double dz = z(first) - z(second);
        return dx * dx + dy * dy + dz * dz;
    }

    static double x(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getX");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_20185_");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(entity, "x");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(entity, "f_19853_");
        }
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    static double y(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getY");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_20186_");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(entity, "y");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(entity, "f_19854_");
        }
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    static double z(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getZ");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_20189_");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(entity, "z");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(entity, "f_19855_");
        }
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    static double height(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getBbHeight");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_20205_");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(entity, "height");
        }
        return value instanceof Number ? ((Number) value).doubleValue() : 1.8D;
    }

    static double eyeHeight(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getEyeHeight");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_20192_");
        }
        return value instanceof Number ? ((Number) value).doubleValue() : 1.62D;
    }

    static float yaw(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getYRot");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_146908_");
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return getFloat(entity, "yRot", "rotationYaw", "f_19857_");
    }

    static float pitch(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getXRot");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_146909_");
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return getFloat(entity, "xRot", "rotationPitch", "f_19858_");
    }

    static boolean onGround(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "onGround");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_20096_");
        }
        return booleanValue(value, true);
    }

    static int entityId(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getId");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_19879_");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(entity, "id");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(entity, "f_19848_");
        }
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    private static float getFloat(Object entity, String... names) {
        for (String name : names) {
            Object value = ModernForgeEventBridge.field(entity, name);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
        }
        return 0.0F;
    }

    static void setFloat(Object entity, float value, String... names) {
        if (entity == null || names == null) {
            return;
        }
        for (String name : names) {
            try {
                Field field = findField(entity.getClass(), name);
                if (field != null) {
                    field.setAccessible(true);
                    field.setFloat(entity, value);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    static Field findField(Class<?> owner, String name) {
        Class<?> current = owner;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (Throwable ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    static Method findMethod(Class<?> owner, String name, int parameterCount) {
        Class<?> current = owner;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static boolean packetHasPosition(Object packet) {
        String name = className(packet);
        if (name.contains("$pos") || name.contains("posrot")) {
            return true;
        }
        if (name.contains("statusonly") || name.endsWith("$rot")) {
            return false;
        }
        return packetBoolean(packet, false, "hasPosition", "m_134138_", "hasPos", "f_134124_");
    }

    private static boolean packetHasRotation(Object packet) {
        String name = className(packet);
        if (name.contains("$rot") || name.contains("posrot")) {
            return true;
        }
        if (name.contains("statusonly") || name.endsWith("$pos")) {
            return false;
        }
        return packetBoolean(packet, false, "hasRotation", "m_134137_", "hasRot", "f_134125_");
    }

    private static double packetDouble(Object packet, double fallback, String method, String obfuscatedMethod,
                                       String field, String obfuscatedField) {
        Object value = ModernForgeEventBridge.invoke(packet, method, Double.valueOf(fallback));
        if (value == null) {
            value = ModernForgeEventBridge.invoke(packet, obfuscatedMethod, Double.valueOf(fallback));
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(packet, field);
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(packet, obfuscatedField);
        }
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static boolean packetBoolean(Object packet, boolean fallback, String method, String obfuscatedMethod,
                                         String field, String obfuscatedField) {
        Object value = ModernForgeEventBridge.invoke(packet, method);
        if (value == null) {
            value = ModernForgeEventBridge.invoke(packet, obfuscatedMethod);
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(packet, field);
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(packet, obfuscatedField);
        }
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    private static String className(Object object) {
        return object == null ? "" : object.getClass().getName().toLowerCase(Locale.ROOT);
    }

    private static float inputFloat(Object input, String... names) {
        for (String name : names) {
            Object value = ModernForgeEventBridge.field(input, name);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
        }
        return 0.0F;
    }

    private static void setInputFloat(Object input, float value, String... names) {
        for (String name : names) {
            try {
                Field field = findField(input.getClass(), name);
                if (field != null) {
                    field.setAccessible(true);
                    field.setFloat(input, value);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static float movementYaw(float yaw, float forward, float strafe) {
        float result = yaw;
        if (forward < 0.0F) {
            result += 180.0F;
        }
        float strafeFactor = forward < 0.0F ? -0.5F : forward > 0.0F ? 0.5F : 1.0F;
        if (strafe > 0.0F) {
            result -= 90.0F * strafeFactor;
        }
        if (strafe < 0.0F) {
            result += 90.0F * strafeFactor;
        }
        return result;
    }

    private static float sign(float value) {
        return value > 0.01F ? 1.0F : value < -0.01F ? -1.0F : 0.0F;
    }

    static Class<?> classForName(String name) {
        synchronized (CLASS_CACHE) {
            if (CLASS_CACHE.containsKey(name)) {
                return CLASS_CACHE.get(name);
            }
        }
        Class<?> type = null;
        try {
            type = ModernForgeEventBridge.findClass(name);
        } catch (Throwable ignored) {
        }
        synchronized (CLASS_CACHE) {
            CLASS_CACHE.put(name, type);
        }
        return type;
    }
}
