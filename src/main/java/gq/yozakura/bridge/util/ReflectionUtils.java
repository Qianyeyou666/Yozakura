package gq.yozakura.bridge.util;

import io.netty.channel.Channel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class ReflectionUtils {
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<String, Field>();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<String, Method>();
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<String, Class<?>>();
    private static volatile Field networkManagerChannelField;

    private ReflectionUtils() {
    }

    public static Channel getChannel(Object manager) {
        if (manager == null) {
            return null;
        }
        try {
            Field field = networkManagerChannelField;
            if (field == null || !field.getDeclaringClass().isInstance(manager)) {
                field = null;
                Class<?> current = manager.getClass();
                while (current != null && field == null) {
                    for (String name : new String[]{"channel", "f_129468_", "field_150746_k", "k"}) {
                        try {
                            field = current.getDeclaredField(name);
                            field.setAccessible(true);
                            networkManagerChannelField = field;
                            break;
                        } catch (Throwable ignored) {
                        }
                    }
                    current = current.getSuperclass();
                }
            }
            Object value = field == null ? null : field.get(manager);
            return value instanceof Channel ? (Channel) value : null;
        } catch (Throwable ignored) {
            networkManagerChannelField = null;
            return null;
        }
    }

    public static Field findField(Class<?> owner, String... names) {
        if (owner == null || names == null || names.length == 0) {
            return null;
        }
        for (String name : names) {
            Field cached = FIELD_CACHE.get(owner.getName() + '#' + name);
            if (cached != null) {
                return cached;
            }
        }
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                FIELD_CACHE.put(owner.getName() + '#' + name, field);
                return field;
            } catch (Throwable ignored) {
            }
        }
        for (String name : names) {
            FIELD_CACHE.put(owner.getName() + '#' + name, null);
        }
        return null;
    }

    public static Field findFieldDeep(Class<?> owner, String name) {
        if (owner == null || name == null) {
            return null;
        }
        String key = owner.getName() + '#' + name;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null || FIELD_CACHE.containsKey(key)) {
            return cached;
        }
        Class<?> current = owner;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                FIELD_CACHE.put(key, field);
                return field;
            } catch (Throwable ignored) {
                current = current.getSuperclass();
            }
        }
        FIELD_CACHE.put(key, null);
        return null;
    }

    public static Object getFieldValue(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        try {
            Field field = findFieldDeep(target.getClass(), name);
            if (field == null) {
                return null;
            }
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object getFieldValue(Object target, Class<?> owner, String name) {
        if (target == null || owner == null || name == null) {
            return null;
        }
        try {
            Field field = findField(owner, name);
            if (field == null) {
                return null;
            }
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Method findMethod(Class<?> owner, String... names) {
        if (owner == null || names == null || names.length == 0) {
            return null;
        }
        for (String name : names) {
            String key = methodKey(owner, name, new Class<?>[0]);
            Method cached = METHOD_CACHE.get(key);
            if (cached != null || METHOD_CACHE.containsKey(key)) {
                return cached;
            }
        }
        for (String name : names) {
            try {
                Method method = owner.getDeclaredMethod(name);
                method.setAccessible(true);
                String key = methodKey(owner, name, new Class<?>[0]);
                METHOD_CACHE.put(key, method);
                return method;
            } catch (Throwable ignored) {
            }
        }
        for (String name : names) {
            String key = methodKey(owner, name, new Class<?>[0]);
            METHOD_CACHE.put(key, null);
        }
        return null;
    }

    public static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        if (owner == null || name == null) {
            return null;
        }
        String key = methodKey(owner, name, parameterTypes);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null || METHOD_CACHE.containsKey(key)) {
            return cached;
        }
        Class<?> current = owner;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                METHOD_CACHE.put(key, method);
                return method;
            } catch (Throwable ignored) {
                current = current.getSuperclass();
            }
        }
        METHOD_CACHE.put(key, null);
        return null;
    }

    public static Method findMethodByCount(Class<?> owner, String name, int parameterCount) {
        if (owner == null || name == null) {
            return null;
        }
        String key = owner.getName() + '#' + name + '(' + parameterCount + ')';
        Method cached = METHOD_CACHE.get(key);
        if (cached != null || METHOD_CACHE.containsKey(key)) {
            return cached;
        }
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                METHOD_CACHE.put(key, method);
                return method;
            }
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                method.setAccessible(true);
                METHOD_CACHE.put(key, method);
                return method;
            }
        }
        METHOD_CACHE.put(key, null);
        return null;
    }

    public static Object invokeMethod(Object target, String name, Object... args) {
        if (target == null || name == null) {
            return null;
        }
        try {
            Class<?>[] paramTypes = new Class<?>[args == null ? 0 : args.length];
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    paramTypes[i] = args[i] == null ? Object.class : args[i].getClass();
                }
            }
            Method method = findMethodByArgTypes(target.getClass(), name, args);
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethodByArgTypes(Class<?> owner, String name, Object[] args) {
        Class<?> current = owner;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && accepts(method.getParameterTypes(), args)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && accepts(method.getParameterTypes(), args)) {
                return method;
            }
        }
        return null;
    }

    private static boolean accepts(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != (args == null ? 0 : args.length)) {
            return false;
        }
        if (args == null) {
            return true;
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

    public static Class<?> classForName(String name) throws ClassNotFoundException {
        if (name == null) {
            throw new ClassNotFoundException("null");
        }
        Class<?> cached = CLASS_CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        ClassNotFoundException failure = null;
        ClassLoader[] loaders = new ClassLoader[]{
                Thread.currentThread().getContextClassLoader(),
                ReflectionUtils.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(name, false, loader);
                CLASS_CACHE.put(name, clazz);
                return clazz;
            } catch (ClassNotFoundException exception) {
                if (failure == null) {
                    failure = exception;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        return Class.forName(name);
    }

    public static boolean classExists(String name) {
        try {
            classForName(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String methodKey(Class<?> owner, String name, Class<?>[] parameterTypes) {
        StringBuilder builder = new StringBuilder(owner.getName()).append('#').append(name).append('(');
        if (parameterTypes != null) {
            for (Class<?> paramType : parameterTypes) {
                builder.append(paramType.getName()).append(',');
            }
        }
        return builder.append(')').toString();
    }
}
