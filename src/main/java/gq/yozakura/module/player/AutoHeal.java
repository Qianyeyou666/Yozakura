package gq.yozakura.module.player;

import gq.yozakura.module.runtime.Module;

public class AutoHeal extends Module {
    public AutoHeal() {
        super("AutoHeal", false);
    }

    public boolean isSwitching() {
        return false;
    }
}
