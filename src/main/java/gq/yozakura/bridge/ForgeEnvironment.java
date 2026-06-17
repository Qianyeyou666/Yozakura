package gq.yozakura.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ForgeEnvironment {
    private static Boolean forgeAvailable;
    private static Boolean legacyForgeAvailable;
    private static Boolean modernForgeAvailable;

    private ForgeEnvironment() {
    }

    public static boolean isForgeAvailable() {
        if (forgeAvailable != null) {
            return forgeAvailable.booleanValue();
        }
        forgeAvailable = Boolean.valueOf(classExists("net.minecraftforge.common.MinecraftForge")
                && (isLegacyForgeAvailable() || isModernForgeAvailable()));
        return forgeAvailable.booleanValue();
    }

    public static boolean isLegacyForgeAvailable() {
        if (legacyForgeAvailable != null) {
            return legacyForgeAvailable.booleanValue();
        }
        legacyForgeAvailable = Boolean.valueOf(classExists("net.minecraftforge.fml.common.FMLCommonHandler"));
        return legacyForgeAvailable.booleanValue();
    }

    public static boolean isModernForgeAvailable() {
        if (modernForgeAvailable != null) {
            return modernForgeAvailable.booleanValue();
        }
        modernForgeAvailable = Boolean.valueOf(classExists("net.minecraftforge.fml.ModList")
                || classExists("net.minecraftforge.fml.loading.FMLLoader")
                || classExists("net.minecraftforge.eventbus.api.IEventBus"));
        return modernForgeAvailable.booleanValue();
    }

    public static boolean register(Object listener) {
        if (!isForgeAvailable()) {
            return false;
        }
        boolean registered = false;
        registered |= callEventBus("net.minecraftforge.common.MinecraftForge", "EVENT_BUS", null, "register", listener);
        if (isLegacyForgeAvailable()) {
            registered |= callFmlBus("register", listener);
        }
        return registered;
    }

    public static boolean unregister(Object listener) {
        if (!isForgeAvailable()) {
            return false;
        }
        boolean unregistered = false;
        unregistered |= callEventBus("net.minecraftforge.common.MinecraftForge", "EVENT_BUS", null, "unregister", listener);
        if (isLegacyForgeAvailable()) {
            unregistered |= callFmlBus("unregister", listener);
        }
        return unregistered;
    }

    public static boolean onPlayerAttackTarget(Object player, Object target) {
        if (!isForgeAvailable()) {
            return true;
        }
        try {
            Class<?> hooks = findClass("net.minecraftforge.common.ForgeHooks");
            Method method = findMethod(hooks, "onPlayerAttackTarget", 2);
            if (method == null) {
                return true;
            }
            Object result = method.invoke(null, player, target);
            return !(result instanceof Boolean) || ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean classExists(String name) {
        try {
            findClass(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Class<?> findClass(String name) throws ClassNotFoundException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ForgeEnvironment.class.getClassLoader();
        }
        return Class.forName(name, false, loader);
    }

    private static boolean callEventBus(String ownerName, String fieldName, Object owner, String methodName, Object listener) {
        try {
            Class<?> ownerClass = findClass(ownerName);
            Field field = ownerClass.getField(fieldName);
            Object bus = field.get(owner);
            callBusMethod(bus, methodName, listener);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean callFmlBus(String methodName, Object listener) {
        try {
            Class<?> commonHandler = findClass("net.minecraftforge.fml.common.FMLCommonHandler");
            Object handler = commonHandler.getMethod("instance").invoke(null);
            Object bus = handler.getClass().getMethod("bus").invoke(handler);
            callBusMethod(bus, methodName, listener);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void callBusMethod(Object bus, String methodName, Object listener) throws Exception {
        Method method = bus.getClass().getMethod(methodName, Object.class);
        method.invoke(bus, listener);
    }

    private static Method findMethod(Class<?> owner, String name, int parameterCount) {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
}
