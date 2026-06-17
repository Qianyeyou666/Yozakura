package gq.yozakura.core.modern;

import gq.yozakura.ui.click.web.ModernWebClickGuiState;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ModernForgeEventBridge {
    private static final ModernForgeEventBridge INSTANCE = new ModernForgeEventBridge();
    private static final String MODERN_BRIDGE_PREFIX = "gq.yozakura.core.modern.ModernForgeEventBridge$";
    private static final long MAX_LOG_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_LOGS_PER_MESSAGE = 8;
    private static volatile boolean initialized;
    private static final Map<String, Integer> LOG_COUNTS = new HashMap<String, Integer>();
    private static final Map<String, Method> METHOD_CACHE = new HashMap<String, Method>();
    private static final Map<String, Field> FIELD_CACHE = new HashMap<String, Field>();

    private ModernForgeEventBridge() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        INSTANCE.register();
    }

    private void register() {
        try {
            Object bus = eventBus();
            cleanupOldModernListeners(bus);
            registerListener(bus, "net.minecraftforge.client.event.RenderGuiEvent$Post", new Consumer<Object>() {
                @Override
                public void accept(Object event) {
                    ModernVisualRenderer.renderGui(event);
                }
            });
            registerListener(bus, "net.minecraftforge.client.event.RenderLevelStageEvent", new Consumer<Object>() {
                @Override
                public void accept(Object event) {
                    ModernVisualRenderer.renderLevel(event);
                }
            });
            registerOptionalListener(bus, "net.minecraftforge.client.event.InputEvent$MouseScrollingEvent", new Consumer<Object>() {
                @Override
                public void accept(Object event) {
                    ModernHudEditor.handleScroll(event);
                }
            });
            registerOptionalListener(bus, "net.minecraftforge.client.event.ScreenEvent$MouseScrolled$Pre", new Consumer<Object>() {
                @Override
                public void accept(Object event) {
                    ModernHudEditor.handleScroll(event);
                }
            });
            log("Modern visual event bridge registered");
        } catch (Throwable throwable) {
            log("Unable to register modern visual event bridge", throwable);
        }
    }

    private static void cleanupOldModernListeners(Object bus) {
        if (bus == null) {
            return;
        }
        try {
            Field listenersField = findField(bus.getClass(), "listeners");
            if (listenersField == null) {
                return;
            }
            listenersField.setAccessible(true);
            Object value = listenersField.get(bus);
            if (!(value instanceof Map)) {
                return;
            }
            Map<?, ?> listeners = (Map<?, ?>) value;
            List<Object> stale = new ArrayList<Object>();
            for (Object key : listeners.keySet()) {
                if (isModernBridgeConsumer(key)) {
                    stale.add(key);
                }
            }
            if (stale.isEmpty()) {
                return;
            }
            Method unregister = bus.getClass().getMethod("unregister", Object.class);
            int removed = 0;
            for (Object key : stale) {
                try {
                    unregister.invoke(bus, key);
                    removed++;
                } catch (Throwable ignored) {
                }
            }
            log("Removed stale modern visual listeners: " + removed);
        } catch (Throwable throwable) {
            log("Unable to clean stale modern visual listeners", throwable);
        }
    }

    private static boolean isModernBridgeConsumer(Object object) {
        if (object == null) {
            return false;
        }
        Class<?> type = object.getClass();
        return type.getName().startsWith(MODERN_BRIDGE_PREFIX);
    }

    private static Object eventBus() throws Exception {
        Class<?> minecraftForge = findClass("net.minecraftforge.common.MinecraftForge");
        Field field = minecraftForge.getField("EVENT_BUS");
        return field.get(null);
    }

    private static void registerListener(Object bus, String eventClassName, Consumer<Object> consumer) throws Exception {
        Class<?> eventClass = findClass(eventClassName);
        Class<?> priorityClass = findClass("net.minecraftforge.eventbus.api.EventPriority");
        Object priority = Enum.valueOf((Class<Enum>) priorityClass.asSubclass(Enum.class), "NORMAL");
        Method method = bus.getClass().getMethod("addListener", priorityClass, boolean.class, Class.class, Consumer.class);
        method.invoke(bus, priority, Boolean.FALSE, eventClass, consumer);
        log("Registered listener for " + eventClassName);
    }

    private static void registerOptionalListener(Object bus, String eventClassName, Consumer<Object> consumer) {
        try {
            registerListener(bus, eventClassName, consumer);
        } catch (Throwable throwable) {
            log("Optional modern listener unavailable: " + eventClassName, throwable);
        }
    }

    static Class<?> findClass(String name) throws ClassNotFoundException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ModernForgeEventBridge.class.getClassLoader();
        }
        return Class.forName(name, false, loader);
    }

    static Object invoke(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = findMethod(target.getClass(), methodName, args);
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Throwable throwable) {
            return null;
        }
    }

    static Object field(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field field = findField(target.getClass(), name);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String name, Object[] args) {
        String key = methodKey(owner, name, args);
        synchronized (METHOD_CACHE) {
            if (METHOD_CACHE.containsKey(key)) {
                return METHOD_CACHE.get(key);
            }
        }
        Class<?> current = owner;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && accepts(method.getParameterTypes(), args)) {
                    cacheMethod(key, method);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && accepts(method.getParameterTypes(), args)) {
                cacheMethod(key, method);
                return method;
            }
        }
        cacheMethod(key, null);
        return null;
    }

    private static void cacheMethod(String key, Method method) {
        synchronized (METHOD_CACHE) {
            METHOD_CACHE.put(key, method);
        }
    }

    private static String methodKey(Class<?> owner, String name, Object[] args) {
        StringBuilder builder = new StringBuilder(owner.getName()).append('#').append(name).append('(');
        if (args != null) {
            for (Object arg : args) {
                builder.append(arg == null ? "null" : arg.getClass().getName()).append(',');
            }
        }
        return builder.append(')').toString();
    }

    private static boolean accepts(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] == null) {
                continue;
            }
            Class<?> wanted = wrap(parameterTypes[i]);
            if (!wanted.isInstance(args[i])) {
                return false;
            }
        }
        return true;
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
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }

    private static Field findField(Class<?> owner, String name) {
        String key = owner.getName() + '#' + name;
        synchronized (FIELD_CACHE) {
            if (FIELD_CACHE.containsKey(key)) {
                return FIELD_CACHE.get(key);
            }
        }
        Class<?> current = owner;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                cacheField(key, field);
                return field;
            } catch (Throwable ignored) {
                current = current.getSuperclass();
            }
        }
        cacheField(key, null);
        return null;
    }

    private static void cacheField(String key, Field field) {
        synchronized (FIELD_CACHE) {
            FIELD_CACHE.put(key, field);
        }
    }

    static boolean enabled(String module) {
        return ModernWebClickGuiState.isEnabled(module);
    }

    static double number(String module, String value, double fallback) {
        return ModernWebClickGuiState.numberValue(module, value, fallback);
    }

    static boolean bool(String module, String value, boolean fallback) {
        return ModernWebClickGuiState.booleanValue(module, value, fallback);
    }

    static String mode(String module, String value, String fallback) {
        return ModernWebClickGuiState.modeValue(module, value, fallback);
    }

    static void log(String message) {
        log(message, null);
    }

    static void log(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraModernVisual.log");
            if (log.length() > MAX_LOG_BYTES) {
                if (!log.delete()) {
                    return;
                }
            }
            if (!shouldLog(message, throwable)) {
                return;
            }
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(message);
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static synchronized boolean shouldLog(String message, Throwable throwable) {
        String key = String.valueOf(message);
        if (throwable != null) {
            key += ":" + throwable.getClass().getName() + ":" + String.valueOf(throwable.getMessage());
        }
        Integer count = LOG_COUNTS.get(key);
        int next = count == null ? 1 : count.intValue() + 1;
        LOG_COUNTS.put(key, Integer.valueOf(next));
        return next <= MAX_LOGS_PER_MESSAGE;
    }
}
