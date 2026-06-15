package gq.yozakura.module.movement;

import gq.yozakura.module.runtime.Module;
import gq.yozakura.value.properties.BooleanProperty;
import gq.yozakura.value.properties.PercentProperty;

public class KeepSprint extends Module {
    public final BooleanProperty groundOnly = new BooleanProperty("Ground Only", false);
    public final BooleanProperty reachOnly = new BooleanProperty("Reach Only", false);
    public final PercentProperty slowdown = new PercentProperty("Slowdown", 0);

    public KeepSprint() {
        super("KeepSprint", false);
    }
}
