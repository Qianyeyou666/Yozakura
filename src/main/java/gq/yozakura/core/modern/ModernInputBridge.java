package gq.yozakura.core.modern;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class ModernInputBridge {
    private static final Map<String, String[]> OPTION_KEY_NAMES = new HashMap<String, String[]>();
    private static final Map<String, Object> KEY_CACHE = new HashMap<String, Object>();
    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<String, Class<?>>();

    static {
        OPTION_KEY_NAMES.put("attack", new String[]{"keyAttack", "f_92096_", "keyBindAttack"});
        OPTION_KEY_NAMES.put("use", new String[]{"keyUse", "f_92095_", "keyBindUseItem", "keyBindUse"});
        OPTION_KEY_NAMES.put("forward", new String[]{"keyUp", "keyForward", "f_92085_", "keyBindForward"});
        OPTION_KEY_NAMES.put("back", new String[]{"keyDown", "keyBack", "f_92087_", "keyBindBack"});
        OPTION_KEY_NAMES.put("left", new String[]{"keyLeft", "f_92086_", "keyBindLeft"});
        OPTION_KEY_NAMES.put("right", new String[]{"keyRight", "f_92088_", "keyBindRight"});
        OPTION_KEY_NAMES.put("jump", new String[]{"keyJump", "f_92089_", "keyBindJump"});
        OPTION_KEY_NAMES.put("shift", new String[]{"keyShift", "keySneak", "f_92090_", "keyBindSneak"});
        OPTION_KEY_NAMES.put("sprint", new String[]{"keySprint", "f_92091_", "keyBindSprint"});
    }

    private ModernInputBridge() {
    }

    static Object key(Object options, String logicalName) {
        if (options == null || logicalName == null) {
            return null;
        }
        String normalized = normalize(logicalName);
        String cacheKey = System.identityHashCode(options) + ":" + normalized;
        synchronized (KEY_CACHE) {
            if (KEY_CACHE.containsKey(cacheKey)) {
                return KEY_CACHE.get(cacheKey);
            }
        }
        Object key = null;
        String[] names = OPTION_KEY_NAMES.get(normalized);
        if (names == null) {
            names = new String[]{logicalName};
        }
        for (String name : names) {
            key = ModernForgeEventBridge.field(options, name);
            if (key != null) {
                break;
            }
        }
        synchronized (KEY_CACHE) {
            KEY_CACHE.put(cacheKey, key);
        }
        return key;
    }

    static boolean down(Object options, String logicalName) {
        return isDown(key(options, logicalName));
    }

    static boolean setDown(Object options, String logicalName, boolean down) {
        return setKeyDown(key(options, logicalName), down);
    }

    static boolean isDown(Object keyMapping) {
        if (keyMapping == null) {
            return false;
        }
        Object value = ModernForgeEventBridge.invoke(keyMapping, "isDown");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(keyMapping, "m_90857_");
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        value = ModernForgeEventBridge.field(keyMapping, "isDown");
        if (value == null) {
            value = ModernForgeEventBridge.field(keyMapping, "f_90817_");
        }
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    static boolean setKeyDown(Object keyMapping, boolean down) {
        if (keyMapping == null) {
            return false;
        }
        try {
            Object result = ModernForgeEventBridge.invoke(keyMapping, "setDown", Boolean.valueOf(down));
            if (result == null) {
                ModernForgeEventBridge.invoke(keyMapping, "m_7249_", Boolean.valueOf(down));
            }
            return true;
        } catch (Throwable e1) {
            Field field = findField(keyMapping.getClass(), "isDown", "f_90817_");
            if (field == null) {
                return false;
            }
            try {
                field.setAccessible(true);
                field.setBoolean(keyMapping, down);
                return true;
            } catch (Throwable e2) {
                return false;
            }
        }
    }

    static boolean physicalDown(Object keyMapping) {
        if (keyMapping == null) {
            return false;
        }
        Object key = ModernForgeEventBridge.invoke(keyMapping, "getKey");
        if (key == null) {
            key = ModernForgeEventBridge.invoke(keyMapping, "m_90859_");
        }
        if (key == null) {
            key = ModernForgeEventBridge.field(keyMapping, "key");
        }
        if (key == null) {
            key = ModernForgeEventBridge.field(keyMapping, "f_90816_");
        }
        int code = inputCode(key);
        if (code == Integer.MIN_VALUE) {
            return isDown(keyMapping);
        }
        long window = windowHandle();
        if (window == 0L) {
            return isDown(keyMapping);
        }
        return glfwDown(window, code);
    }

    static double mouseSensitivity(Object minecraft) {
        Object options = ModernMinecraftAccess.options(minecraft);
        Object instance = optionInstance(options, "sensitivity", "m_231964_");
        Object value = optionValue(instance);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        Object raw = firstField(options, "mouseSensitivity", "sensitivity", "f_92068_");
        return raw instanceof Number ? ((Number) raw).doubleValue() : 0.5D;
    }

    static Object gammaOption(Object options) {
        return optionInstance(options, "gamma", "m_231927_");
    }

    static Object optionValue(Object optionInstance) {
        if (optionInstance == null) {
            return null;
        }
        Object value = ModernForgeEventBridge.invoke(optionInstance, "get");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(optionInstance, "m_231551_");
        }
        if (value != null) {
            return value;
        }
        Method getter = findValueGetter(optionInstance.getClass());
        if (getter == null) {
            return null;
        }
        try {
            getter.setAccessible(true);
            return getter.invoke(optionInstance);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean setOptionValue(Object optionInstance, Object value) {
        if (optionInstance == null || value == null) {
            return false;
        }
        if (invokeSetter(optionInstance, "set", value) || invokeSetter(optionInstance, "m_231514_", value)) {
            return true;
        }
        Method setter = findValueSetter(optionInstance.getClass(), value);
        if (setter == null) {
            return false;
        }
        try {
            setter.setAccessible(true);
            setter.invoke(optionInstance, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean invokeSetter(Object target, String name, Object value) {
        Method method = findNamedMethod(target.getClass(), name, 1);
        if (method == null || !wrap(method.getParameterTypes()[0]).isInstance(value)) {
            return false;
        }
        try {
            method.setAccessible(true);
            method.invoke(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findNamedMethod(Class<?> owner, String name, int parameterCount) {
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

    private static Object optionInstance(Object options, String named, String obfuscated) {
        if (options == null) {
            return null;
        }
        Object instance = ModernForgeEventBridge.invoke(options, named);
        if (instance == null) {
            instance = ModernForgeEventBridge.invoke(options, obfuscated);
        }
        if (instance == null) {
            instance = firstField(options, named, obfuscated);
        }
        return instance;
    }

    private static Object firstField(Object owner, String... names) {
        if (owner == null || names == null) {
            return null;
        }
        for (String name : names) {
            Object value = ModernForgeEventBridge.field(owner, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Method findValueGetter(Class<?> owner) {
        for (Method method : owner.getMethods()) {
            if (method.getParameterTypes().length == 0
                    && method.getReturnType() != Void.TYPE
                    && ("get".equals(method.getName()) || "m_231551_".equals(method.getName()))) {
                return method;
            }
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getParameterTypes().length == 0
                    && method.getReturnType() != Void.TYPE
                    && !method.getReturnType().getName().contains("Tooltip")
                    && !method.getReturnType().getName().contains("ValueSet")) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                if ("get".equals(name) || name.startsWith("m_")) {
                    return method;
                }
            }
        }
        return null;
    }

    private static Method findValueSetter(Class<?> owner, Object value) {
        for (Method method : owner.getMethods()) {
            if (acceptsSetter(method, value)
                    && ("set".equals(method.getName()) || "m_231514_".equals(method.getName()))) {
                return method;
            }
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (acceptsSetter(method, value)
                    && (method.getName().equals("set") || method.getName().startsWith("m_"))) {
                return method;
            }
        }
        return null;
    }

    private static boolean acceptsSetter(Method method, Object value) {
        Class<?>[] types = method.getParameterTypes();
        return types.length == 1 && (value == null || wrap(types[0]).isInstance(value));
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        return type;
    }

    private static int inputCode(Object key) {
        Object value = ModernForgeEventBridge.invoke(key, "getValue");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(key, "m_84873_");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(key, "value");
        }
        return value instanceof Number ? ((Number) value).intValue() : Integer.MIN_VALUE;
    }

    private static long windowHandle() {
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object window = ModernForgeEventBridge.invoke(minecraft, "getWindow");
        if (window == null) {
            window = ModernForgeEventBridge.invoke(minecraft, "m_91268_");
        }
        if (window == null) {
            window = ModernForgeEventBridge.field(minecraft, "window");
        }
        if (window == null) {
            window = ModernForgeEventBridge.field(minecraft, "f_90990_");
        }
        Object handle = ModernForgeEventBridge.invoke(window, "getWindow");
        if (handle == null) {
            handle = ModernForgeEventBridge.invoke(window, "m_85439_");
        }
        return handle instanceof Number ? ((Number) handle).longValue() : 0L;
    }

    private static boolean glfwDown(long window, int code) {
        try {
            Class<?> glfw = classForName("org.lwjgl.glfw.GLFW");
            if (glfw == null) {
                return false;
            }
            Method keyMethod = glfw.getMethod("glfwGetKey", long.class, int.class);
            Object keyState = keyMethod.invoke(null, Long.valueOf(window), Integer.valueOf(code));
            if (keyState instanceof Number && ((Number) keyState).intValue() == 1) {
                return true;
            }
            int mouseButton = code < 0 ? code + 100 : code;
            Method mouseMethod = glfw.getMethod("glfwGetMouseButton", long.class, int.class);
            Object mouseState = mouseMethod.invoke(null, Long.valueOf(window), Integer.valueOf(mouseButton));
            return mouseState instanceof Number && ((Number) mouseState).intValue() == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Field findField(Class<?> owner, String... names) {
        Class<?> current = owner;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Class<?> classForName(String name) {
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

    private static String normalize(String text) {
        return text == null ? "" : text.replace(" ", "").replace("_", "").replace("-", "")
                .toLowerCase(Locale.ROOT);
    }
}
