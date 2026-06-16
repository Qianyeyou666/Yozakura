package gq.yozakura.util.module;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class KeyBindUtil {
    public static String getKeyName(int keyCode) {
        return keyCode < 0 ? Mouse.getButtonName(keyCode + 100) : Keyboard.getKeyName(keyCode);
    }

    public static boolean isKeyDown(int keyCode) {
        try {
            return keyCode < 0 ? Mouse.isCreated() && Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isBindingDown(KeyBinding binding) {
        return binding != null && (binding.isKeyDown() || KeyBindUtil.isKeyDown(binding.getKeyCode()));
    }

    public static void updateKeyState(int keyCode) {
        KeyBindUtil.setKeyBindState(keyCode, KeyBindUtil.isKeyDown(keyCode));
    }

    public static void setKeyBindState(int keyCode, boolean pressed) {
        KeyBinding.setKeyBindState(keyCode, pressed);
    }

    public static void pressKeyOnce(int keyCode) {
        KeyBinding.onTick(keyCode);
    }
}
