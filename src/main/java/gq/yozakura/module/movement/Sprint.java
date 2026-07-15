package gq.yozakura.module.movement;

import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.event.bridge.MoveInputEvent;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.util.minecraft.SprintUtil;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint", Keyboard.KEY_R, ModuleType.Movement,"Force sprint when you moving");
        Chinese="疾跑";
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!isInGame()) {
            return;
        }
        if (!mc.thePlayer.isCollidedHorizontally && mc.thePlayer.movementInput.moveForward >= 0.8F) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
    }

    @Override
    public void disable() {
        restoreSprintKey();
        SprintUtil.setSprinting(false);
    }

    private void restoreSprintKey() {
        int key = mc.gameSettings.keyBindSprint.getKeyCode();
        boolean physicallyDown = key < 0 ? Mouse.isButtonDown(key + 100) : Keyboard.isKeyDown(key);
        KeyBinding.setKeyBindState(key, physicallyDown);
    }
}
