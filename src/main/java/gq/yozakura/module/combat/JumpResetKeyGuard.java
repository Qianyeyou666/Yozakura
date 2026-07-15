package gq.yozakura.module.combat;

import gq.yozakura.util.module.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/** Owns only the temporary jump-key state requested by JumpReset. */
final class JumpResetKeyGuard {
    private final Minecraft mc;
    private boolean holdingJump;

    JumpResetKeyGuard(Minecraft mc) {
        this.mc = mc;
    }

    void holdJump() {
        if (!hasJumpBinding()) {
            return;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
        holdingJump = true;
    }

    void releaseJump() {
        if (!holdingJump) {
            return;
        }
        holdingJump = false;
        restorePhysicalState();
    }

    void reset() {
        releaseJump();
    }

    private boolean hasJumpBinding() {
        return mc != null && mc.gameSettings != null && mc.gameSettings.keyBindJump != null;
    }

    private void restorePhysicalState() {
        if (!hasJumpBinding()) {
            return;
        }
        int keyCode = mc.gameSettings.keyBindJump.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, KeyBindUtil.isKeyDown(keyCode));
    }
}
