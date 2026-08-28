package gq.yozakura.bridge;

import gq.yozakura.bridge.util.ReflectionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class MinecraftAccessor {
    private static Method syncCurrentPlayItem;
    private static Method clickMouse;
    private static Method rightClickMouse;
    private static Field leftClickCounter;
    private static Field isHittingBlock;
    private static Field currentBlockDamage;
    private static Field currentBlock;
    private static Field blockHitDelay;
    private static Field rightClickDelayTimer;
    private static IllegalStateException rightClickDelayTimerFailure;

    private MinecraftAccessor() {
    }

    public static void syncCurrentPlayItem(PlayerControllerMP controller) {
        if (controller == null) {
            return;
        }
        try {
            if (syncCurrentPlayItem == null) {
                syncCurrentPlayItem = ReflectionUtils.findMethod(PlayerControllerMP.class, "syncCurrentPlayItem", "func_78750_j", "n");
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
                isHittingBlock = ReflectionUtils.findField(PlayerControllerMP.class, "isHittingBlock", "field_78778_j", "h");
            }
            return isHittingBlock != null && isHittingBlock.getBoolean(controller);
        } catch (Throwable ignored) {
            isHittingBlock = null;
            return false;
        }
    }

    public static float getCurrentBlockDamage(PlayerControllerMP controller) {
        if (controller == null) {
            return 0.0F;
        }
        try {
            if (currentBlockDamage == null) {
                currentBlockDamage = ReflectionUtils.findField(PlayerControllerMP.class,
                        "curBlockDamageMP", "field_78770_f", "e");
            }
            if (currentBlockDamage == null) {
                return 0.0F;
            }
            return Math.max(0.0F, Math.min(1.0F, currentBlockDamage.getFloat(controller)));
        } catch (Throwable ignored) {
            currentBlockDamage = null;
            return 0.0F;
        }
    }

    public static void setCurrentBlockDamage(PlayerControllerMP controller, float damage) {
        if (controller == null) {
            return;
        }
        try {
            if (currentBlockDamage == null) {
                currentBlockDamage = ReflectionUtils.findField(PlayerControllerMP.class,
                        "curBlockDamageMP", "field_78770_f", "e");
            }
            if (currentBlockDamage != null) {
                currentBlockDamage.setFloat(controller, Math.max(0.0F, Math.min(1.0F, damage)));
            }
        } catch (Throwable ignored) {
            currentBlockDamage = null;
        }
    }

    public static void setBlockHitDelay(PlayerControllerMP controller, int delay) {
        if (controller == null) {
            return;
        }
        try {
            if (blockHitDelay == null) {
                blockHitDelay = ReflectionUtils.findField(PlayerControllerMP.class,
                        "blockHitDelay", "field_78781_i", "g");
            }
            if (blockHitDelay != null) {
                blockHitDelay.setInt(controller, Math.max(0, delay));
            }
        } catch (Throwable ignored) {
            blockHitDelay = null;
        }
    }

    public static BlockPos getCurrentBlock(PlayerControllerMP controller) {
        if (controller == null) {
            return null;
        }
        try {
            if (currentBlock == null) {
                currentBlock = ReflectionUtils.findField(PlayerControllerMP.class,
                        "currentBlock", "field_178895_c", "c");
            }
            if (currentBlock == null) {
                return null;
            }
            Object value = currentBlock.get(controller);
            return value instanceof BlockPos ? (BlockPos) value : null;
        } catch (Throwable ignored) {
            currentBlock = null;
            return null;
        }
    }

    public static boolean clickMouse(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        try {
            if (clickMouse == null) {
                clickMouse = ReflectionUtils.findMethod(Minecraft.class, "clickMouse", "func_147116_af", "ay");
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

    public static boolean rightClickMouse(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        try {
            if (rightClickMouse == null) {
                rightClickMouse = ReflectionUtils.findMethod(Minecraft.class,
                        "rightClickMouse", "func_147121_ag", "az");
            }
            if (rightClickMouse == null) {
                return false;
            }
            rightClickMouse.invoke(minecraft);
            return true;
        } catch (Throwable ignored) {
            rightClickMouse = null;
            return false;
        }
    }

    public static void setLeftClickCounter(Minecraft minecraft, int value) {
        if (minecraft == null) {
            return;
        }
        try {
            if (leftClickCounter == null) {
                leftClickCounter = ReflectionUtils.findField(Minecraft.class, "leftClickCounter", "field_71429_W", "ag");
            }
            if (leftClickCounter != null) {
                leftClickCounter.setInt(minecraft, value);
            }
        } catch (Throwable ignored) {
            leftClickCounter = null;
        }
    }

    public static void capRightClickDelayTimer(Minecraft minecraft, int maximumDelay) {
        if (minecraft == null) {
            throw new IllegalArgumentException("minecraft");
        }
        if (maximumDelay < 0) {
            throw new IllegalArgumentException("maximumDelay");
        }
        try {
            Field field = getRightClickDelayTimer();
            if (field.getInt(minecraft) > maximumDelay) {
                field.setInt(minecraft, maximumDelay);
            }
        } catch (IllegalAccessException exception) {
            throw rememberRightClickDelayTimerFailure(exception);
        }
    }

    private static Field getRightClickDelayTimer() {
        if (rightClickDelayTimer != null) {
            return rightClickDelayTimer;
        }
        if (rightClickDelayTimerFailure != null) {
            throw rightClickDelayTimerFailure;
        }

        Field field = ReflectionUtils.findField(Minecraft.class, "rightClickDelayTimer", "field_71467_ac", "ap");
        if (field == null || field.getType() != Integer.TYPE) {
            rightClickDelayTimerFailure = new IllegalStateException(
                    "Unable to resolve Minecraft right-click delay on " + Minecraft.class.getName());
            throw rightClickDelayTimerFailure;
        }
        rightClickDelayTimer = field;
        return field;
    }

    private static IllegalStateException rememberRightClickDelayTimerFailure(IllegalAccessException exception) {
        if (rightClickDelayTimerFailure == null) {
            rightClickDelayTimerFailure = new IllegalStateException(
                    "Unable to update Minecraft right-click delay", exception);
        }
        return rightClickDelayTimerFailure;
    }
}
