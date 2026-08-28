package gq.yozakura.module.player;

import gq.yozakura.module.runtime.Module;
import gq.yozakura.value.properties.BooleanProperty;
import gq.yozakura.value.properties.IntProperty;
import gq.yozakura.value.properties.ModeProperty;

/**
 * Leader-Lite compatible global Blink release settings.
 */
public final class BlinkSettings extends Module {
    public final BooleanProperty slowRelease = new BooleanProperty("SlowRelease", false);
    public final ModeProperty slowReleaseTime = new ModeProperty(
            "SlowReleaseTime", 0, new String[]{"Start Blink", "Stop Blink"}, slowRelease::getValue);
    public final IntProperty slowReleaseDelay = new IntProperty(
            "DelayBetweenSlowRelease", 0, 0, 10, slowRelease::getValue);
    public final IntProperty maxPacketsPerTick = new IntProperty(
            "MaxPacketPerTick", 5, 1, 30, slowRelease::getValue);
    public final IntProperty maxC03PacketsPerTick = new IntProperty(
            "MaxC03PacketPerTick", 1, 1, 5, slowRelease::getValue);

    public BlinkSettings() {
        super("BlinkSettings", true);
    }
}
