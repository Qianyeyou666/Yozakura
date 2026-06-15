package gq.vapulite.module.render.runtime;

import gq.vapulite.module.runtime.Module;
import gq.vapulite.value.properties.BooleanProperty;
import gq.vapulite.value.properties.FloatProperty;

import java.awt.Color;

public class HUD extends Module {
    public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 2.0F);
    public final BooleanProperty shadow = new BooleanProperty("Shadow", true);
    public final BooleanProperty toggleSound = new BooleanProperty("Toggle Sound", false);

    public HUD() {
        super("HUD", false);
    }

    public Color getColor(long offset) {
        float hue = (System.currentTimeMillis() + offset) % 5000L / 5000.0F;
        return Color.getHSBColor(hue, 0.65F, 1.0F);
    }
}
