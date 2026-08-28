package gq.yozakura.ui.click.engine;

/** Time-based motion curves shared by the Minecraft host and deterministic tests. */
final class ClickGuiMotion {
    private ClickGuiMotion() { }

    static float openingEase(float progress) {
        return cubicBezier(clamp(progress), 0.34F, 1.56F, 0.64F, 1.0F);
    }

    static float controlSpring(float progress) {
        return cubicBezier(clamp(progress), 0.22F, 1.2F, 0.36F, 1.0F);
    }

    static float reverseControlSpring(float progress) {
        return 1.0F - controlSpring(progress);
    }

    static float layoutCompensation(float previousPosition, float currentPosition) {
        return previousPosition - currentPosition;
    }

    static float toggleKnobCompensation(boolean enabled) {
        return enabled ? -16.0F : 16.0F;
    }

    private static float cubicBezier(float progress, float x1, float y1, float x2, float y2) {
        if (progress <= 0.0F || progress >= 1.0F) return progress;
        float lower = 0.0F;
        float upper = 1.0F;
        float parameter = progress;
        for (int i = 0; i < 14; i++) {
            float x = bezierCoordinate(parameter, x1, x2);
            if (Math.abs(x - progress) < 0.00001F) break;
            if (x < progress) lower = parameter;
            else upper = parameter;
            parameter = (lower + upper) * 0.5F;
        }
        return bezierCoordinate(parameter, y1, y2);
    }

    private static float bezierCoordinate(float parameter, float first, float second) {
        float inverse = 1.0F - parameter;
        return 3.0F * inverse * inverse * parameter * first
                + 3.0F * inverse * parameter * parameter * second
                + parameter * parameter * parameter;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
