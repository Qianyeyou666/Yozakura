package gq.yozakura.module.combat;

import gq.yozakura.value.Value;
import gq.yozakura.value.properties.BooleanProperty;
import gq.yozakura.value.properties.FloatProperty;
import gq.yozakura.value.properties.IntProperty;
import gq.yozakura.value.properties.ModeProperty;

/** Vape-style BlockHit configuration surface. */
final class BlockHitSettings {
    final ModeProperty mode = new ModeProperty(
            "Mode", 0, new String[]{"Manual", "Predict", "Auto", "Lag", "Hypixel", "noprehyp"});
    final IntProperty chance = new IntProperty(
            "Chance", 90, 70, 100, () -> mode.getValue() == 0);
    final BooleanProperty ignoreManualBlock = new BooleanProperty(
            "Ignore Manual Block", true, () -> mode.getValue() == 1 || mode.getValue() == 2);
    final IntProperty angle = new IntProperty(
            "Angle", 90, 0, 360, () -> mode.getValue() == 1 || mode.getValue() == 2);
    final FloatProperty distance = new FloatProperty(
            "Distance", 5.0F, 0.0F, 6.0F, () -> mode.getValue() == 1 || mode.getValue() == 2);
    final IntProperty lagDelay = new IntProperty(
            "Lag Delay", 100, 50, 500, () -> mode.getValue() == 3);
    final IntProperty stopTicks = new IntProperty(
            "Stop Ticks", 2, 1, 5, () -> mode.getValue() == 4 || mode.getValue() == 5);
    final FloatProperty helperThreatRange = new FloatProperty(
            "Threat Range", 3.6F, 2.0F, 6.0F, () -> mode.getValue() == 4);
    final IntProperty helperThreatAngle = new IntProperty(
            "Threat Angle", 65, 15, 180, () -> mode.getValue() == 4);

    Value[] values() {
        return new Value[]{
                mode,
                chance,
                ignoreManualBlock,
                angle,
                distance,
                lagDelay,
                stopTicks,
                helperThreatRange,
                helperThreatAngle
        };
    }
}
