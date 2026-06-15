package gq.yozakura.module.movement;

import gq.yozakura.module.runtime.Module;

public class LongJump extends Module {
    public LongJump() {
        super("LongJump", false);
    }

    public boolean isAutoMode() {
        return false;
    }

    public boolean isJumping() {
        return true;
    }
}
