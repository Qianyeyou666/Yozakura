package gq.yozakura;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class Yozakuraloader {
    public static void inject(Thread[] threads) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        for (Thread thread : threads) {
            if(thread != null) {
                if(isClientThread(thread)) {
                    ClassLoader classLoader = thread.getContextClassLoader();
                    Thread.currentThread().setContextClassLoader(classLoader);
                    ClassLoader entryLoader = addCurrentJar(classLoader);
                    Thread.currentThread().setContextClassLoader(entryLoader);
                    Class<?> clazz = entryLoader.loadClass("gq.yozakura.YozakuraBootstrap");
                    clazz.newInstance();
                    break;
                }
            }
        }
    }

    private static boolean isClientThread(Thread thread) {
        String name = thread.getName();
        return "Client thread".equals(name) || "Render thread".equals(name) || "Minecraft main thread".equals(name);
    }

    private static ClassLoader addCurrentJar(ClassLoader classLoader) {
        try {
            URL url = Yozakuraloader.class.getProtectionDomain().getCodeSource().getLocation();
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
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return classLoader;
        }
    }
}
