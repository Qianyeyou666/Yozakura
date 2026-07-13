package gq.yozakura.module.combat;

import gq.yozakura.value.Value;
import gq.yozakura.value.properties.BooleanProperty;
import gq.yozakura.value.properties.FloatProperty;
import gq.yozakura.value.properties.IntProperty;
import gq.yozakura.value.properties.ModeProperty;

/** Vape-style BlockHit configuration surface. */
final class BlockHitSettings {
    final ModeProperty mode = new ModeProperty(
            "Mode", 0, new String[]{"Manual", "Predict", "Auto", "Lag"});
    final IntProperty chance = new IntProperty(
            "Chance", 90, 70, 100, () -> mode.getValue() == 0);
    final BooleanProperty requireMouseDown = new BooleanProperty(
            "Require Mouse Down", true, () -> mode.getValue() == 1 || mode.getValue() == 2);
    final BooleanProperty ignoreManualBlock = new BooleanProperty(
            "Ignore Manual Block", true, () -> mode.getValue() == 1 || mode.getValue() == 2);
    final IntProperty angle = new IntProperty(
            "Angle", 90, 0, 360, () -> mode.getValue() == 1 || mode.getValue() == 2);
    final FloatProperty distance = new FloatProperty(
            "Distance", 5.0F, 0.0F, 6.0F, () -> mode.getValue() == 1 || mode.getValue() == 2);
    final IntProperty lagDelay = new IntProperty(
            "Lag Delay", 100, 50, 500, () -> mode.getValue() == 3);

    Value[] values() {
        return new Value[]{
                mode,
                chance,
                requireMouseDown,
                ignoreManualBlock,
                angle,
                distance,
                lagDelay
        };
    }
}
