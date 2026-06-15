package gq.yozakura;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class YozakuraAttachPoint {
    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        ClassLoader classLoader = null;
        for (Class<?> classes : instrumentation.getAllLoadedClasses()) {
            if (isMinecraftClass(classes)) {
                classLoader = classes.getClassLoader();
                if (classLoader != null) {
                    break;
                }
            }
        }
        if (classLoader == null) {
            throw new IllegalStateException("Cannot find Minecraft classloader");
        }
        ClassLoader entryLoader = addCurrentJar(classLoader);
        Thread.currentThread().setContextClassLoader(entryLoader);
        Class<?> bootstrap = entryLoader.loadClass("gq.yozakura.YozakuraBootstrap");
        bootstrap.getMethod("start").invoke(null);
    }

    private static boolean isMinecraftClass(Class<?> type) {
        String name = type.getName();
        return "net.minecraft.client.Minecraft".equals(name)
                || "ave".equals(name)
                || name.endsWith(".client.Minecraft");
    }

    private static ClassLoader addCurrentJar(ClassLoader classLoader) throws Exception {
        URL url = YozakuraAttachPoint.class.getProtectionDomain().getCodeSource().getLocation();
        if (classLoader instanceof URLClassLoader) {
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            method.invoke(classLoader, url);
            return classLoader;
        }
        Class<?> type = classLoader.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod("addURL", URL.class);
                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }
                method.invoke(classLoader, url);
                return classLoader;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        return new URLClassLoader(new URL[]{url}, classLoader);
    }
}
