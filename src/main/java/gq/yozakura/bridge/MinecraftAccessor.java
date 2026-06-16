package gq.yozakura.bridge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class MinecraftAccessor {
    private static Method syncCurrentPlayItem;
    private static Method clickMouse;
    private static Field leftClickCounter;
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

    public static boolean clickMouse(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        try {
            if (clickMouse == null) {
                clickMouse = findMethod(Minecraft.class, "clickMouse", "func_147116_af", "ay");
            }
            if (clickMouse == null) {
                return false;
            }
            clickMouse.invoke(minecraft);
            return true;
        } catch (Throwable ignored) {
            clickMouse = null;
            return false;
        }
    }

    public static void setLeftClickCounter(Minecraft minecraft, int value) {
        if (minecraft == null) {
            return;
        }
        try {
            if (leftClickCounter == null) {
                leftClickCounter = findField(Minecraft.class, "leftClickCounter", "field_71429_W", "ag");
            }
            if (leftClickCounter != null) {
                leftClickCounter.setInt(minecraft, value);
            }
        } catch (Throwable ignored) {
            leftClickCounter = null;
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
