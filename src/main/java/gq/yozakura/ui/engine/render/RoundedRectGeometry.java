package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.RectFillCommand;

/** Emits triangle vertices for plain and rounded rectangle fills without overdraw outside corners. */
public final class RoundedRectGeometry {
    private RoundedRectGeometry() { }

    public interface TriangleSink {
        void triangle(float x0, float y0, float x1, float y1, float x2, float y2);
    }

    public static void emit(RectFillCommand command, TriangleSink sink) {
        if (command == null || sink == null) {
            throw new IllegalArgumentException("command and sink must not be null");
        }
        float x = command.x();
        float y = command.y();
        float width = command.width();
        float height = command.height();
        if (width <= 0.0F || height <= 0.0F) return;
        float topLeft = command.topLeftRadius();
        float topRight = command.topRightRadius();
        float bottomRight = command.bottomRightRadius();
        float bottomLeft = command.bottomLeftRadius();
        if (topLeft <= 0.0F && topRight <= 0.0F
                && bottomRight <= 0.0F && bottomLeft <= 0.0F) {
            sink.triangle(x, y, x + width, y, x + width, y + height);
            sink.triangle(x, y, x + width, y + height, x, y + height);
            return;
        }

        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;
        float firstX = x;
        float firstY = y + topLeft;
        float previousX = firstX;
        float previousY = firstY;
        long previous = emitCorner(sink, centerX, centerY, previousX, previousY,
                x + topLeft, y + topLeft, topLeft,
                Math.PI, Math.PI * 1.5, segmentsFor(topLeft));
        previous = emitCorner(sink, centerX, centerY, unpackX(previous), unpackY(previous),
                x + width - topRight, y + topRight, topRight,
                Math.PI * 1.5, Math.PI * 2.0, segmentsFor(topRight));
        previous = emitCorner(sink, centerX, centerY, unpackX(previous), unpackY(previous),
                x + width - bottomRight, y + height - bottomRight, bottomRight,
                0.0, Math.PI * 0.5, segmentsFor(bottomRight));
        previous = emitCorner(sink, centerX, centerY, unpackX(previous), unpackY(previous),
                x + bottomLeft, y + height - bottomLeft, bottomLeft,
                Math.PI * 0.5, Math.PI, segmentsFor(bottomLeft));
        sink.triangle(centerX, centerY, unpackX(previous), unpackY(previous), firstX, firstY);
    }

    private static int segmentsFor(float radius) {
        return radius <= 0.0F ? 1 : Math.max(3, (int) Math.ceil(radius * 0.75F));
    }

    private static long emitCorner(TriangleSink sink, float centerX, float centerY,
                                      float previousX, float previousY,
                                      float cx, float cy, float radius,
                                      double start, double end, int segments) {
        for (int i = 1; i <= segments; i++) {
            double angle = start + (end - start) * i / segments;
            float nextX = cx + radius * (float) Math.cos(angle);
            float nextY = cy + radius * (float) Math.sin(angle);
            sink.triangle(centerX, centerY, previousX, previousY, nextX, nextY);
            previousX = nextX;
            previousY = nextY;
        }
        return ((long) Float.floatToRawIntBits(previousX) << 32)
                | (Float.floatToRawIntBits(previousY) & 0xFFFFFFFFL);
    }

    private static float unpackX(long packed) {
        return Float.intBitsToFloat((int) (packed >>> 32));
    }

    private static float unpackY(long packed) {
        return Float.intBitsToFloat((int) packed);
    }
}
