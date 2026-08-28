package gq.yozakura.ui.engine.layout;

/**
 * Padding 边距。不允许 auto（解析为 0），不允许负值（钳为 0）。
 *
 * <p>不可变值对象。
 */
public final class PaddingEdges extends BoxEdges {
    public PaddingEdges(float top, float right, float bottom, float left) {
        super(nonNeg(top), nonNeg(right), nonNeg(bottom), nonNeg(left));
    }

    private static float nonNeg(float v) {
        return v < 0 ? 0 : v;
    }

    public static PaddingEdges parseShorthand(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new PaddingEdges(0, 0, 0, 0);
        }
        String[] parts = raw.trim().split("\\s+");
        float[] values = new float[4];
        if (parts.length == 1) {
            values[0] = values[1] = values[2] = values[3] = parseLen(parts[0]);
        } else if (parts.length == 2) {
            values[0] = values[2] = parseLen(parts[0]);
            values[1] = values[3] = parseLen(parts[1]);
        } else if (parts.length == 3) {
            values[0] = parseLen(parts[0]);
            values[1] = values[3] = parseLen(parts[1]);
            values[2] = parseLen(parts[2]);
        } else {
            for (int i = 0; i < 4; i++) {
                values[i] = parseLen(parts[i]);
            }
        }
        return new PaddingEdges(values[0], values[1], values[2], values[3]);
    }

    private static float parseLen(String s) {
        if (s.equals("auto")) {
            // padding 不允许 auto，视为 0
            return 0f;
        }
        Dimension d = Dimension.parse(s);
        return d == null ? 0f : d.value();
    }
}
