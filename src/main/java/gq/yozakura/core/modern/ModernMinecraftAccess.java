package gq.yozakura.core.modern;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

final class ModernMinecraftAccess {
    private static Object minecraft;
    private static Method minecraftGetter;

    private ModernMinecraftAccess() {
    }

    static Object minecraft() {
        if (minecraft != null) {
            return minecraft;
        }
        try {
            Class<?> minecraftClass = ModernForgeEventBridge.findClass("net.minecraft.client.Minecraft");
            minecraftGetter = findMethod(minecraftClass, "getInstance", 0);
            if (minecraftGetter == null) {
                minecraftGetter = findMethod(minecraftClass, "m_91087_", 0);
            }
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

    static Object player(Object minecraft) {
        Object player = ModernForgeEventBridge.field(minecraft, "player");
        if (player != null) {
            return player;
        }
        return ModernForgeEventBridge.field(minecraft, "f_91074_");
    }

    static Object level(Object minecraft) {
        Object level = ModernForgeEventBridge.field(minecraft, "level");
        if (level != null) {
            return level;
        }
        return ModernForgeEventBridge.field(minecraft, "f_91073_");
    }

    static Object font(Object minecraft) {
        Object font = ModernForgeEventBridge.field(minecraft, "font");
        if (font != null) {
            return font;
        }
        return ModernForgeEventBridge.field(minecraft, "f_91062_");
    }

    static List<Object> livingEntities(Object minecraft) {
        Object level = level(minecraft);
        if (level == null) {
            return new ArrayList<Object>();
        }
        Object iterable = ModernForgeEventBridge.invoke(level, "entitiesForRendering");
        if (iterable == null) {
            iterable = ModernForgeEventBridge.invoke(level, "m_104735_");
        }
        if (!(iterable instanceof Iterable)) {
            return new ArrayList<Object>();
        }
        ArrayList<Object> entities = new ArrayList<Object>();
        Class<?> living = livingEntityClass();
        for (Object entity : (Iterable) iterable) {
            if (entity != null && (living == null || living.isInstance(entity)) && isAlive(entity)) {
                entities.add(entity);
            }
        }
        return entities;
    }

    private static boolean isAlive(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "isAlive");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_6084_");
        }
        return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
    }

    private static Class<?> livingEntityClass() {
        try {
            return ModernForgeEventBridge.findClass("net.minecraft.world.entity.LivingEntity");
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String name, int count) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == count) {
                return method;
            }
        }
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == count) {
                return method;
            }
        }
        return null;
    }

    private static Field findStaticField(Class<?> owner, Class<?> type) {
        for (Field field : owner.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(modifiers) && type.isAssignableFrom(field.getType())) {
                return field;
            }
        }
        return null;
    }
}
