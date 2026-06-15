package gq.yozakura.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class YozakuraClientState {
    private static final String CLIENT_CLASS = "gq.yozakura.core.Client";
    private static volatile boolean standaloneDirty;

    private YozakuraClientState() {
    }

    public static String getName() {
        String name = readString("name");
        return name == null ? "Yozakura" : name;
    }

    public static String getConfig() {
        String config = readString("config");
        return config == null ? "module" : config;
    }

    public static boolean isMessageOn() {
        Boolean value = readBoolean("MessageON");
        return value == null || value;
    }

    public static boolean isChinese() {
        Boolean value = readBoolean("CHINESE");
        return value != null && value;
    }

    public static void markConfigDirty() {
        try {
            Class<?> clientClass = findClientClass(false);
            if (clientClass != null) {
                Method method = clientClass.getMethod("markConfigDirty");
                method.invoke(null);
                return;
            }
        } catch (Throwable ignored) {
        }
        standaloneDirty = true;
    }

    public static boolean consumeStandaloneDirty() {
        boolean dirty = standaloneDirty;
        standaloneDirty = false;
        return dirty;
    }

    private static String readString(String fieldName) {
        try {
            Field field = findClientClass(false).getField(fieldName);
            Object value = field.get(null);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Boolean readBoolean(String fieldName) {
        try {
            Field field = findClientClass(false).getField(fieldName);
            return field.getBoolean(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> findClientClass(boolean initialize) throws ClassNotFoundException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = YozakuraClientState.class.getClassLoader();
        }
        return Class.forName(CLIENT_CLASS, initialize, loader);
    }
}
