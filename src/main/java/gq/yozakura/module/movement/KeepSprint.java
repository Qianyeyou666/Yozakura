package gq.vapulite.module.movement;

import gq.vapulite.module.runtime.Module;
import gq.vapulite.value.properties.BooleanProperty;
import gq.vapulite.value.properties.PercentProperty;

public class KeepSprint extends Module {
    public final BooleanProperty groundOnly = new BooleanProperty("Ground Only", false);
    public final BooleanProperty reachOnly = new BooleanProperty("Reach Only", false);
    public final PercentProperty slowdown = new PercentProperty("Slowdown", 0);

    public KeepSprint() {
        super("KeepSprint", false);
    }
}
