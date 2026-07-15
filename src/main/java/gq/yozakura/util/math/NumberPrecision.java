package gq.yozakura.util.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NumberPrecision {
    private static final double DEFAULT_INCREMENT = 1.0D;

    private NumberPrecision() {
    }

    public static double uiIncrement(Number configuredIncrement) {
        if (configuredIncrement == null) {
            return DEFAULT_INCREMENT;
        }
        double increment = Math.abs(configuredIncrement.doubleValue());
        if (Double.isNaN(increment) || Double.isInfinite(increment) || increment <= 0.0D) {
            return DEFAULT_INCREMENT;
        }
        if (increment >= 1.0D) {
            return increment;
        }
        if (increment >= 0.5D) {
            return 0.1D;
        }
        if (increment >= 0.01D) {
            return 0.01D;
        }
        return increment;
    }

    public static int decimalPlaces(Number configuredIncrement) {
        BigDecimal increment = BigDecimal.valueOf(uiIncrement(configuredIncrement)).stripTrailingZeros();
        return Math.max(0, increment.scale());
    }

    public static String format(Number value, Number configuredIncrement) {
        int places = decimalPlaces(configuredIncrement);
        if (value == null || !isFinite(value.doubleValue())) {
            return BigDecimal.ZERO.setScale(places, RoundingMode.HALF_UP).toPlainString();
        }
        return BigDecimal.valueOf(value.doubleValue()).setScale(places, RoundingMode.HALF_UP).toPlainString();
    }

    public static double snap(double value, double minimum, double maximum, Number configuredIncrement) {
        if (!isFinite(minimum) || !isFinite(maximum)) {
            return value;
        }
        double min = Math.min(minimum, maximum);
        double max = Math.max(minimum, maximum);
        double clamped = isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
        BigDecimal step = BigDecimal.valueOf(uiIncrement(configuredIncrement));
        BigDecimal offset = BigDecimal.valueOf(clamped).subtract(BigDecimal.valueOf(min));
        BigDecimal steps = offset.divide(step, 0, RoundingMode.HALF_UP);
        BigDecimal snapped = BigDecimal.valueOf(min).add(step.multiply(steps));
        double result = Math.max(min, Math.min(max, snapped.doubleValue()));
        return BigDecimal.valueOf(result).setScale(decimalPlaces(configuredIncrement), RoundingMode.HALF_UP).doubleValue();
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
