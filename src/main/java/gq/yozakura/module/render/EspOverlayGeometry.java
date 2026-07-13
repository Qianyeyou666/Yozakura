package gq.yozakura.module.render;

final class EspOverlayGeometry {
    private EspOverlayGeometry() {
    }

    static Bounds bounds(float[][] points) {
        if (points == null || points.length == 0) {
            return null;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (float[] point : points) {
            if (point == null || point.length < 3 || point[2] < 0.0f || point[2] > 1.0f) {
                return null;
            }
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }
        return maxX > minX && maxY > minY ? new Bounds(minX, minY, maxX, maxY) : null;
    }

    static int applyOpacity(int color, float opacity) {
        int alpha = Math.round(((color >>> 24) & 255) * clamp(opacity, 0.0f, 1.0f));
        return color & 0x00FFFFFF | alpha << 24;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class Bounds {
        final float minX;
        final float minY;
        final float maxX;
        final float maxY;

        Bounds(float minX, float minY, float maxX, float maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }
}
