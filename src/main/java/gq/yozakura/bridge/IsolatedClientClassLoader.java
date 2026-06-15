package gq.vapulite.bridge;

import java.net.URL;
import java.net.URLClassLoader;

public final class IsolatedClientClassLoader extends URLClassLoader {
    public IsolatedClientClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null && shouldLoadChildFirst(name)) {
            try {
                loaded = findClass(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (loaded == null) {
            loaded = super.loadClass(name, false);
        }
        if (resolve) {
            resolveClass(loaded);
        }
        return loaded;
    }

    private static boolean shouldLoadChildFirst(String name) {
        return name.startsWith("gq.vapulite.")
                && !name.equals("gq.vapulite.bridge.IsolatedClientClassLoader");
    }
}
