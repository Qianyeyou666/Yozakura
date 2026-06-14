package gq.yozakura;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class Yozakuraloader {
    public static void inject(Thread[] threads) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        for (Thread thread : threads) {
            if(thread != null) {
                if(thread.getName().equals("Client thread")) {
                    URLClassLoader classLoader = (URLClassLoader) thread.getContextClassLoader();
                    Thread.currentThread().setContextClassLoader(classLoader);
                    try {
                        Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                        if (method.isAccessible() == false) method.setAccessible(true);
                        method.invoke(classLoader, Yozakuraloader.class.getProtectionDomain().getCodeSource().getLocation());
                    } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    Class<?> clazz = classLoader.loadClass("gq.yozakura.core.Client");
                    clazz.newInstance();
                    break;
                }
            }
        }
    }
}
