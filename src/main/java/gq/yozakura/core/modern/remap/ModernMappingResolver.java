package gq.yozakura.core.modern.remap;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class ModernMappingResolver {
    private static final String RESOURCE = "/yozakura/modern1201-mappings.properties";
    private static final ModernMappingResolver INSTANCE = new ModernMappingResolver();
    private final Properties mappings = new Properties();
    private final Map<String, Class<?>> classCache = new HashMap<String, Class<?>>();
    private final Map<String, Field> fieldCache = new HashMap<String, Field>();
    private final Map<String, Method> methodCache = new HashMap<String, Method>();

    private ModernMappingResolver() {
        loadMappings();
    }

    public static ModernMappingResolver get() {
        return INSTANCE;
    }

    public Class<?> classFor(String logicalName) throws ClassNotFoundException {
        String runtime = value("class." + logicalName, logicalName);
        synchronized (classCache) {
            if (classCache.containsKey(runtime)) {
                return classCache.get(runtime);
            }
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ModernMappingResolver.class.getClassLoader();
        }
        Class<?> type = Class.forName(runtime, false, loader);
        synchronized (classCache) {
            classCache.put(runtime, type);
        }
        return type;
    }

    public Field field(Class<?> owner, String logicalName, String... fallbackNames) {
        if (owner == null || logicalName == null) {
            return null;
        }
        String key = owner.getName() + "#field." + logicalName;
        synchronized (fieldCache) {
            if (fieldCache.containsKey(key)) {
                return fieldCache.get(key);
            }
        }
        String[] names = names("field." + owner.getName() + "." + logicalName, logicalName, fallbackNames);
        Field field = findField(owner, names);
        synchronized (fieldCache) {
            fieldCache.put(key, field);
        }
        return field;
    }

    public Method method(Class<?> owner, String logicalName, int parameterCount, String... fallbackNames) {
        if (owner == null || logicalName == null) {
            return null;
        }
        String key = owner.getName() + "#method." + logicalName + "/" + parameterCount;
        synchronized (methodCache) {
            if (methodCache.containsKey(key)) {
                return methodCache.get(key);
            }
        }
        String[] names = names("method." + owner.getName() + "." + logicalName, logicalName, fallbackNames);
        Method method = findMethod(owner, parameterCount, names);
        synchronized (methodCache) {
            methodCache.put(key, method);
        }
        return method;
    }

    public Object getField(Object target, String logicalName, String... fallbackNames) {
        if (target == null) {
            return null;
        }
        try {
            Field field = field(target.getClass(), logicalName, fallbackNames);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public Object invoke(Object target, String logicalName, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = method(target.getClass(), logicalName, args == null ? 0 : args.length);
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public String value(String key, String fallback) {
        String value = mappings.getProperty(key);
        return value == null || value.trim().length() == 0 ? fallback : value.trim();
    }

    private String[] names(String key, String logicalName, String... fallbackNames) {
        String value = mappings.getProperty(key);
        StringBuilder builder = new StringBuilder();
        if (value != null && value.trim().length() > 0) {
            builder.append(value.trim());
        }
        if (logicalName != null && logicalName.length() > 0) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(logicalName);
        }
        if (fallbackNames != null) {
            for (String fallback : fallbackNames) {
                if (fallback == null || fallback.length() == 0) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(fallback);
            }
        }
        return builder.toString().split(",");
    }

    private Field findField(Class<?> owner, String[] names) {
        Class<?> current = owner;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name.trim());
                    field.setAccessible(true);
                    return field;
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private Method findMethod(Class<?> owner, int parameterCount, String[] names) {
        Class<?> current = owner;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterTypes().length == parameterCount && matches(method.getName(), names)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        for (Method method : owner.getMethods()) {
            if (method.getParameterTypes().length == parameterCount && matches(method.getName(), names)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private boolean matches(String name, String[] names) {
        for (String candidate : names) {
            if (name.equals(candidate.trim())) {
                return true;
            }
        }
        return false;
    }

    private void loadMappings() {
        try {
            InputStream stream = ModernMappingResolver.class.getResourceAsStream(RESOURCE);
            if (stream == null) {
                return;
            }
            try {
                mappings.load(stream);
            } finally {
                stream.close();
            }
        } catch (Throwable ignored) {
        }
    }

    public static String normalize(String value) {
        return value == null ? "" : value.replace(" ", "").replace("_", "").replace("-", "")
                .toLowerCase(Locale.ROOT);
    }
}
