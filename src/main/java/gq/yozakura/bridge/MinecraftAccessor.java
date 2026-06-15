package gq.vapulite.bridge;

import net.minecraft.client.multiplayer.PlayerControllerMP;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class MinecraftAccessor {
    private static Method syncCurrentPlayItem;
    private static Field isHittingBlock;

    private MinecraftAccessor() {
    }

    public static void syncCurrentPlayItem(PlayerControllerMP controller) {
        if (controller == null) {
            return;
        }
        try {
            if (syncCurrentPlayItem == null) {
                syncCurrentPlayItem = findMethod(PlayerControllerMP.class, "syncCurrentPlayItem", "func_78750_j", "n");
            }
            if (syncCurrentPlayItem != null) {
                syncCurrentPlayItem.invoke(controller);
            }
        } catch (Throwable ignored) {
            syncCurrentPlayItem = null;
        }
    }

    public static boolean isHittingBlock(PlayerControllerMP controller) {
        if (controller == null) {
            return false;
        }
        try {
            if (isHittingBlock == null) {
                isHittingBlock = findField(PlayerControllerMP.class, "isHittingBlock", "field_78778_j", "h");
            }
            return isHittingBlock != null && isHittingBlock.getBoolean(controller);
        } catch (Throwable ignored) {
            isHittingBlock = null;
            return false;
        }
    }

    private static Method findMethod(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Method method = owner.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
