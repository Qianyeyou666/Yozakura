package gq.yozakura.module.render;

/**
 * A single scrolling pink color field shared by every Night Bloom ArrayList glyph.
 */
final class NightBloomArrayListGradient {
    private static final int DEEP_PINK = 0xFFFF4FC7;
    private static final int LIGHT_PINK = 0xFFFFD4EC;
    private static final float HORIZONTAL_FREQUENCY = 0.065F;
    private static final float VERTICAL_DISTANCE = 72.0F;
    private static final float VERTICAL_WEIGHT = 0.72F;
    private static final float HORIZONTAL_WEIGHT = 1.0F - VERTICAL_WEIGHT;
    private static final long CYCLE_MILLIS = 2000L;

    private NightBloomArrayListGradient() {
    }

    static int colorAt(float x, float y, long tick) {
        long wrappedTick = tick % CYCLE_MILLIS;
        if (wrappedTick < 0L) {
            wrappedTick += CYCLE_MILLIS;
        }
        double cycle = wrappedTick / (double) CYCLE_MILLIS;
        float verticalPulse = triangle(cycle * 2.0D - y / VERTICAL_DISTANCE);
        float horizontalPulse = 0.5F + 0.5F * (float) Math.sin(
                x * HORIZONTAL_FREQUENCY - cycle * Math.PI * 2.0D);
        float amount = verticalPulse * VERTICAL_WEIGHT + horizontalPulse * HORIZONTAL_WEIGHT;
        return blend(DEEP_PINK, LIGHT_PINK, amount);
    }

    private static float triangle(double phase) {
        double wrapped = phase % 2.0D;
        if (wrapped < 0.0D) {
            wrapped += 2.0D;
        }
        return (float) Math.abs(wrapped - 1.0D);
    }

    private static int blend(int start, int end, float amount) {
        int red = Math.round((start >>> 16 & 255) + ((end >>> 16 & 255) - (start >>> 16 & 255)) * amount);
        int green = Math.round((start >>> 8 & 255) + ((end >>> 8 & 255) - (start >>> 8 & 255)) * amount);
        int blue = Math.round((start & 255) + ((end & 255) - (start & 255)) * amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
