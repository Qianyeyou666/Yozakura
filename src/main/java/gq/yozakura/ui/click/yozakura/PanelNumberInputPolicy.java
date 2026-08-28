package gq.yozakura.ui.click.yozakura;

import gq.yozakura.value.Numbers;

public final class PanelNumberInputPolicy {
    private PanelNumberInputPolicy() {
    }

    public static double normalizeTypedValue(Numbers<?> number, double value) {
        double minimum = number.getMinimum().doubleValue();
        double maximum = number.getMaximum().doubleValue();
        double clamped = Math.max(minimum, Math.min(maximum, value));
        if (usesWholeNumbers(number)) {
            clamped = Math.round(clamped);
        }
        return Math.max(minimum, Math.min(maximum, clamped));
    }

    private static boolean usesWholeNumbers(Numbers<?> number) {
        Number increment = number.getIncrement();
        return increment != null && increment.doubleValue() >= 1.0D;
    }
}
