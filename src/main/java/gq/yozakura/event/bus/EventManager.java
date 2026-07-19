package gq.yozakura.event.bus;

import gq.yozakura.auth.YozakuraAuthGate;
import gq.yozakura.event.bus.events.EventStoppable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.annotation.Annotation;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventManager {
    private static final ConcurrentHashMap<Class<?>, List<MethodData>> REGISTRY =
            new ConcurrentHashMap<Class<?>, List<MethodData>>();
    private static final Set<String> CALL_FAILURES = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> REGISTRATION_LOGS = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> CALL_LOGS = Collections.synchronizedSet(new HashSet<String>());

    private EventManager() {
    }

    public static void register(Object object) {
        Set<String> seen = new HashSet<String>();
        Class<?> type = object.getClass();
        while (type != null && type != Object.class) {
            Method[] methods;
            try {
                methods = type.getDeclaredMethods();
            } catch (Throwable throwable) {
                log("Failed to inspect event methods for " + type.getName(), throwable);
                type = type.getSuperclass();
                continue;
            }
            for (Method method : methods) {
                String signature = method.getName() + methodSignature(method);
                if (!seen.add(signature) || isMethodBad(method)) {
                    continue;
                }
                register(method, object);
            }
            type = type.getSuperclass();
        }
    }

    public static void unregister(Object object) {
        for (Class<?> eventClass : REGISTRY.keySet()) {
            REGISTRY.computeIfPresent(eventClass, (key, current) -> {
                List<MethodData> updated = new ArrayList<MethodData>(current);
                if (!updated.removeIf(data -> data.source == object)) {
                    return current;
                }
                return updated.isEmpty()
                        ? null
                        : new CopyOnWriteArrayList<MethodData>(updated);
            });
        }
    }

    public static <T> T call(T event) {
        if (event == null) {
            return null;
        }
        if (!YozakuraAuthGate.permitEventDispatch()) {
            return event;
        }
        List<MethodData> list = REGISTRY.get(event.getClass());
        logWatchedCall(event, list);
        if (list == null) {
            return event;
        }
        for (MethodData data : list) {
            try {
                data.invoke(event);
                if (event instanceof EventStoppable && ((EventStoppable) event).isStopped()) {
                    break;
                }
            } catch (Throwable throwable) {
                logCallFailure(data, event, throwable);
            }
        }
        return event;
    }

    private static boolean isMethodBad(Method method) {
        return !hasListenerAnnotation(method) || method.getParameterTypes().length != 1;
    }

    private static void register(Method method, Object object) {
        Class<?> eventClass = method.getParameterTypes()[0];
        try {
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            MethodData data = new MethodData(object, method, getPriority(method));
            boolean[] registered = new boolean[1];
            REGISTRY.compute(eventClass, (key, current) -> {
                List<MethodData> updated = current == null
                        ? new ArrayList<MethodData>()
                        : new ArrayList<MethodData>(current);
                if (updated.contains(data)) {
                    return current;
                }
                updated.add(data);
                registered[0] = true;
                return sort(updated);
            });
            if (registered[0]) {
                logWatchedRegistration(object, method, eventClass);
            }
        } catch (Throwable throwable) {
            log("Failed to register event method " + object.getClass().getName() + "." + method.getName(), throwable);
        }
    }

    private static List<MethodData> sort(List<MethodData> listeners) {
        List<MethodData> sorted = new CopyOnWriteArrayList<MethodData>();
        for (byte priority : gq.yozakura.event.bus.types.Priority.VALUE_ARRAY) {
            for (MethodData data : listeners) {
                if (data.priority == priority) {
                    sorted.add(data);
                }
            }
        }
        return sorted;
    }

    private static boolean hasListenerAnnotation(Method method) {
        if (method.isAnnotationPresent(EventTarget.class)) {
            return true;
        }
        for (Annotation annotation : method.getAnnotations()) {
            String name = annotation.annotationType().getName();
            if ("gq.yozakura.bridge.forge.SubscribeEvent".equals(name)
                    || "net.minecraftforge.fml.common.eventhandler.SubscribeEvent".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static byte getPriority(Method method) {
        EventTarget target = method.getAnnotation(EventTarget.class);
        if (target != null) {
            return target.value();
        }
        for (Annotation annotation : method.getAnnotations()) {
            String name = annotation.annotationType().getName();
            if (!"gq.yozakura.bridge.forge.SubscribeEvent".equals(name)
                    && !"net.minecraftforge.fml.common.eventhandler.SubscribeEvent".equals(name)) {
                continue;
            }
            try {
                Object priority = annotation.annotationType().getMethod("priority").invoke(annotation);
                if (priority != null) {
                    return mapPriority(priority.toString());
                }
            } catch (Throwable ignored) {
            }
        }
        return gq.yozakura.event.bus.types.Priority.MEDIUM;
    }

    private static byte mapPriority(String priority) {
        if ("HIGHEST".equals(priority)) {
            return gq.yozakura.event.bus.types.Priority.HIGHEST;
        }
        if ("HIGH".equals(priority)) {
            return gq.yozakura.event.bus.types.Priority.HIGH;
        }
        if ("LOW".equals(priority)) {
            return gq.yozakura.event.bus.types.Priority.LOW;
        }
        if ("LOWEST".equals(priority)) {
            return gq.yozakura.event.bus.types.Priority.LOWEST;
        }
        return gq.yozakura.event.bus.types.Priority.MEDIUM;
    }

    private static String methodSignature(Method method) {
        StringBuilder builder = new StringBuilder("(");
        try {
            Class<?>[] types = method.getParameterTypes();
            for (Class<?> type : types) {
                builder.append(type.getName()).append(';');
            }
        } catch (Throwable ignored) {
        }
        return builder.append(')').toString();
    }

    private static void log(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraEventBus.log");
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

    private static void logWatchedRegistration(Object object, Method method, Class<?> eventClass) {
        String source = object.getClass().getName();
        if (!isWatchedSource(source) && !isWatchedEvent(eventClass)) {
            return;
        }
        String key = "register:" + source + "#" + method.getName() + ":" + eventClass.getName();
        if (REGISTRATION_LOGS.add(key)) {
            CALL_LOGS.remove("call:" + eventClass.getName());
            log("Registered listener: " + source + "." + method.getName()
                    + " -> " + eventClass.getName()
                    + " sourceLoader=" + loaderName(object.getClass().getClassLoader())
                    + " eventLoader=" + loaderName(eventClass.getClassLoader()), null);
        }
    }

    private static void logWatchedCall(Object event, List<MethodData> listeners) {
        Class<?> eventClass = event.getClass();
        if (!isWatchedEvent(eventClass)) {
            return;
        }
        String key = "call:" + eventClass.getName();
        if (CALL_LOGS.add(key)) {
            log("Event call: " + eventClass.getName()
                    + " listeners=" + (listeners == null ? 0 : listeners.size())
                    + " eventLoader=" + loaderName(eventClass.getClassLoader()), null);
        }
    }

    private static boolean isWatchedSource(String source) {
        return source.endsWith(".HUD")
                || source.endsWith(".Health")
                || source.endsWith(".TargetHUD")
                || source.endsWith(".KeyboardDisplay")
                || source.endsWith(".TargetESP")
                || source.endsWith(".KillEffect");
    }

    private static boolean isWatchedEvent(Class<?> eventClass) {
        String name = eventClass == null ? "" : eventClass.getName();
        return "gq.yozakura.event.bridge.Render2DEvent".equals(name)
                || "gq.yozakura.bridge.forge.RenderGameOverlayEvent$Text".equals(name)
                || "net.minecraftforge.client.event.RenderGameOverlayEvent$Text".equals(name)
                || "gq.yozakura.event.bridge.Render3DEvent".equals(name)
                || "gq.yozakura.bridge.forge.RenderWorldLastEvent".equals(name)
                || "net.minecraftforge.client.event.RenderWorldLastEvent".equals(name)
                || "gq.yozakura.event.bridge.AttackEvent".equals(name)
                || "gq.yozakura.bridge.forge.AttackEntityEvent".equals(name)
                || "net.minecraftforge.event.entity.player.AttackEntityEvent".equals(name)
                || "gq.yozakura.bridge.forge.TickEvent$ClientTickEvent".equals(name)
                || "net.minecraftforge.fml.common.gameevent.TickEvent$ClientTickEvent".equals(name);
    }

    private static String loaderName(ClassLoader loader) {
        return loader == null ? "bootstrap" : loader.getClass().getName();
    }

    private static void logCallFailure(MethodData data, Object event, Throwable throwable) {
        String key = data.source.getClass().getName() + "#" + data.target.getName()
                + ":" + event.getClass().getName() + ":" + throwable.getClass().getName();
        if (CALL_FAILURES.add(key)) {
            log("Event listener failed: " + data.source.getClass().getName() + "."
                    + data.target.getName() + "(" + event.getClass().getName() + ")", throwable);
        }
    }

    private static final class MethodData {
        private final Object source;
        private final Method target;
        private final MethodHandle invoker;
        private final byte priority;

        private MethodData(Object source, Method target, byte priority) throws IllegalAccessException {
            this.source = source;
            this.target = target;
            this.invoker = MethodHandles.lookup().unreflect(target).bindTo(source);
            this.priority = priority;
        }

        private void invoke(Object event) throws Throwable {
            invoker.invoke(event);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MethodData)) {
                return false;
            }
            MethodData data = (MethodData) other;
            return data.source == source && data.target.equals(target);
        }

        @Override
        public int hashCode() {
            return source.hashCode() * 31 + target.hashCode();
        }
    }
}
