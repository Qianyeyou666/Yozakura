package gq.yozakura.module.combat;

import gq.yozakura.util.module.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/** Owns only the temporary use-item key state requested by BlockHit. */
final class BlockHitUseKeyGuard {
    private final Minecraft mc;
    private boolean holdingUse;

    BlockHitUseKeyGuard(Minecraft mc) {
        this.mc = mc;
    }

    void holdUse() {
        if (!hasUseBinding()) {
            return;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        holdingUse = true;
    }

    void releaseUse() {
        if (!holdingUse) {
            return;
        }
        holdingUse = false;
        restorePhysicalState();
    }

    void reset() {
        holdingUse = false;
        restorePhysicalState();
    }

    boolean isHoldingUse() {
        return holdingUse;
    }

    boolean isPhysicalUseDown() {
        return hasUseBinding() && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode());
    }

    private boolean hasUseBinding() {
        return mc != null && mc.gameSettings != null && mc.gameSettings.keyBindUseItem != null;
    }

    private void restorePhysicalState() {
        if (!hasUseBinding()) {
            return;
        }
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, KeyBindUtil.isKeyDown(keyCode));
    }
}
