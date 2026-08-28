package gq.yozakura.bridge.modern;

import gq.yozakura.bridge.modern.adapter.ModernClientAdapter;
import gq.yozakura.bridge.modern.remap.ModernMappingResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ReflectionModernClientAdapter implements ModernClientAdapter {
    protected final ModernMappingResolver mappings = ModernMappingResolver.get();
    private Object minecraft;
    private Method minecraftGetter;

    @Override
    public Object minecraft() {
        if (minecraft != null) {
            return minecraft;
        }
        try {
            Class<?> minecraftClass = mappings.classFor("net.minecraft.client.Minecraft");
            minecraftGetter = findMethod(minecraftClass, "getInstance", 0, "m_91087_");
            if (minecraftGetter != null) {
                minecraftGetter.setAccessible(true);
                minecraft = minecraftGetter.invoke(null);
                return minecraft;
            }
            Field instance = findStaticField(minecraftClass, minecraftClass);
            if (instance != null) {
                instance.setAccessible(true);
                minecraft = instance.get(null);
            }
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Unable to resolve Minecraft instance", throwable);
        }
        return minecraft;
    }

    @Override
    public Object player(Object minecraft) {
        return firstField(minecraft, "player", "f_91074_");
    }

    @Override
    public Object level(Object minecraft) {
        return firstField(minecraft, "level", "f_91073_");
    }

    @Override
    public Object font(Object minecraft) {
        return firstField(minecraft, "font", "f_91062_");
    }

    @Override
    public Object gameMode(Object minecraft) {
        Object gameMode = firstField(minecraft, "gameMode", "f_91072_");
        return gameMode != null ? gameMode : fieldByClassName(minecraft, "MultiPlayerGameMode", "PlayerController");
    }

    @Override
    public Object options(Object minecraft) {
        Object options = firstField(minecraft, "options", "gameSettings", "f_91066_");
        return options != null ? options : fieldByClassName(minecraft, "Options", "GameSettings");
    }

    @Override
    public Object connection(Object minecraft, Object player) {
        Object connection = firstField(player, "connection", "f_108617_");
        if (connection != null) {
            return connection;
        }
        connection = invokeAny(minecraft, "getConnection", "m_91403_");
        return connection;
    }

    @Override
    public Object connectionNetworkManager(Object connection) {
        if (connection == null) {
            return null;
        }
        Object manager = invokeAny(connection, "getConnection", "m_104910_");
        if (manager == null) {
            manager = firstField(connection, "connection", "f_104888_");
        }
        return manager != null ? manager : fieldByClassName(connection, "Connection", "NetworkManager");
    }

    @Override
    public List<Object> entities(Object minecraft) {
        Object level = level(minecraft);
        if (level == null) {
            return new ArrayList<Object>();
        }
        LinkedHashSet<Object> collected = new LinkedHashSet<Object>();
        addIterable(collected, invokeAny(level, "entitiesForRendering", "m_104735_"));
        addIterable(collected, invokeAny(level, "players", "m_6907_"));
        if (collected.isEmpty()) {
            addIterableFields(collected, level);
        }
        return new ArrayList<Object>(collected);
    }

    @Override
    public List<Object> livingEntities(Object minecraft) {
        ArrayList<Object> entities = new ArrayList<Object>();
        Class<?> living = livingEntityClass();
        for (Object entity : entities(minecraft)) {
            if (entity != null && (living == null || living.isInstance(entity) || looksLiving(entity)) && isAlive(entity)) {
                entities.add(entity);
            }
        }
        return entities;
    }

    private void addIterable(Set<Object> entities, Object value) {
        if (!(value instanceof Iterable)) {
            return;
        }
        for (Object entity : (Iterable) value) {
            if (entity != null) {
                entities.add(entity);
            }
        }
    }

    private void addIterableFields(Set<Object> entities, Object owner) {
        if (owner == null) {
            return;
        }
        Class<?> current = owner.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (!Iterable.class.isAssignableFrom(field.getType()) && !Collection.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (value instanceof Iterable) {
                        addIterable(entities, value);
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
    }

    protected Object firstField(Object owner, String... names) {
        if (owner == null || names == null) {
            return null;
        }
        for (String name : names) {
            Object value = mappings.getField(owner, name);
            if (value == null) {
                value = ModernForgeEventBridge.field(owner, name);
            }
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected Object invokeAny(Object target, String... names) {
        if (target == null || names == null) {
            return null;
        }
        for (String name : names) {
            Object value = mappings.invoke(target, name);
            if (value == null) {
                value = ModernForgeEventBridge.invoke(target, name);
            }
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected Object fieldByClassName(Object owner, String... typeNames) {
        if (owner == null || typeNames == null) {
            return null;
        }
        Class<?> current = owner.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                String name = field.getType().getName();
                String simpleName = field.getType().getSimpleName();
                for (String typeName : typeNames) {
                    if ((name != null && name.endsWith(typeName)) || (simpleName != null && simpleName.equals(typeName))) {
                        try {
                            field.setAccessible(true);
                            Object value = field.get(owner);
                            if (value != null) {
                                return value;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private boolean isAlive(Object entity) {
        Object value = invokeAny(entity, "isAlive", "m_6084_");
        return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
    }

    private boolean looksLiving(Object entity) {
        if (entity == null) {
            return false;
        }
        Object health = invokeAny(entity, "getHealth", "m_21223_");
        if (health instanceof Number) {
            return true;
        }
        String name = entity.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("living") || name.contains("player") || name.contains("mob")
                || name.contains("animal") || name.contains("monster");
    }

    private Class<?> livingEntityClass() {
        try {
            return mappings.classFor("net.minecraft.world.entity.LivingEntity");
        } catch (Throwable throwable) {
            return null;
        }
    }

    private Method findMethod(Class<?> owner, String name, int count, String... fallbackNames) {
        Method method = mappings.method(owner, name, count, fallbackNames);
        if (method != null) {
            return method;
        }
        for (Method candidate : owner.getDeclaredMethods()) {
            if (candidate.getParameterTypes().length == count) {
                for (String fallback : fallbackNames) {
                    if (candidate.getName().equals(fallback)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private Field findStaticField(Class<?> owner, Class<?> type) {
        for (Field field : owner.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(modifiers) && type.isAssignableFrom(field.getType())) {
                return field;
            }
        }
        return null;
    }
}
