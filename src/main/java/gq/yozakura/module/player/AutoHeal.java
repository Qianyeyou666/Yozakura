package gq.vapulite.module.player;

import gq.vapulite.module.runtime.Module;

public class AutoHeal extends Module {
    public AutoHeal() {
        super("AutoHeal", false);
    }

    public boolean isSwitching() {
        return false;
    }
}
