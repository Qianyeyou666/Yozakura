package gq.yozakura.module.combat;

import gq.yozakura.util.module.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;

/** Applies and restores only the use-key state owned by Helper mode. */
final class BlockHitHelperInput {
    private final Minecraft mc;
    private boolean useOwned;
    private boolean useSuppressed;

    BlockHitHelperInput(Minecraft mc) {
        this.mc = mc;
    }

    void holdUse() {
        KeyBinding binding = useBinding();
        if (binding == null) {
            return;
        }
        boolean activate = !useOwned || useSuppressed;
        useOwned = true;
        useSuppressed = false;
        KeyBindUtil.setKeyBindState(binding.getKeyCode(), true);
        if (activate) {
            startHeldItemUse();
        }
    }

    void suppressUse() {
        KeyBinding binding = useBinding();
        if (binding != null && useOwned) {
            useSuppressed = true;
            KeyBindUtil.setKeyBindState(binding.getKeyCode(), false);
        }
    }

    boolean isHoldingUse() {
        return useOwned && !useSuppressed;
    }

    void pressAttackOnce() {
        KeyBinding binding = attackBinding();
        if (binding != null) {
            KeyBindUtil.pressKeyOnce(binding.getKeyCode());
        }
    }

    void releaseOwnedUse() {
        if (!useOwned) {
            return;
        }
        useOwned = false;
        useSuppressed = false;
        KeyBinding binding = useBinding();
        if (binding != null) {
            KeyBindUtil.updateKeyState(binding.getKeyCode());
        }
    }

    private void startHeldItemUse() {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            return;
        }
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem != null) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, heldItem);
        }
    }

    private KeyBinding useBinding() {
        return mc == null || mc.gameSettings == null ? null : mc.gameSettings.keyBindUseItem;
    }

    private KeyBinding attackBinding() {
        return mc == null || mc.gameSettings == null ? null : mc.gameSettings.keyBindAttack;
    }
}
