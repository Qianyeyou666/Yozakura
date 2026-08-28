package gq.yozakura.ui.click.yozakura;

import org.lwjgl.input.Keyboard;

/** Encodes mouse buttons in the negative half of the module keybind space. */
public final class PanelModuleKeybind {
    private static final int MOUSE_OFFSET = -2;

    private PanelModuleKeybind() {
    }

    public static boolean isMouseButton(int keyBind) {
        return keyBind <= MOUSE_OFFSET;
    }

    public static int encodeMouseButton(int button) {
        if (button < 0) {
            return Keyboard.KEY_NONE;
        }
        return MOUSE_OFFSET - button;
    }

    public static int decodeMouseButton(int keyBind) {
        return MOUSE_OFFSET - keyBind;
    }

    public static String compactName(int keyBind) {
        return isMouseButton(keyBind) ? "M" + (decodeMouseButton(keyBind) + 1) : null;
    }
}
