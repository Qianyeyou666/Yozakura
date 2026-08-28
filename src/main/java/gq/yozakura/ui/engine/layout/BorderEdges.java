package gq.yozakura.ui.engine.layout;

/**
 * Border 宽度。不允许负值（钳为 0）。
 *
 * <p>提供两种解析入口：
 * <ul>
 *   <li>{@link #parseWidthShorthand(String)}：解析 border-width 简写（仅数值）</li>
 *   <li>{@link #parseBorderShorthand(String)}：解析 border 简写（如 "1px solid #ccc"），
 *       从中提取宽度部分</li>
 * </ul>
 *
 * <p>不可变值对象。
 */
public final class BorderEdges extends BoxEdges {
    public BorderEdges(float top, float right, float bottom, float left) {
        super(nonNeg(top), nonNeg(right), nonNeg(bottom), nonNeg(left));
    }

    private static float nonNeg(float v) {
        return v < 0 ? 0 : v;
    }

    /** 解析 border-width 简写（仅数值，1-4 个）。 */
    public static BorderEdges parseWidthShorthand(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new BorderEdges(0, 0, 0, 0);
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
        return new BorderEdges(values[0], values[1], values[2], values[3]);
    }

    /**
     * 解析 border 简写（如 "1px solid #ccc" 或 "solid 2px red"）。
     * 仅提取宽度部分（带 px/em 单位的数值）；找不到则四边为 0。
     */
    public static BorderEdges parseBorderShorthand(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new BorderEdges(0, 0, 0, 0);
        }
        String[] parts = raw.trim().split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            // 仅识别带长度单位的 token（避免与 "solid"/颜色冲突）
            if (p.endsWith("px") || p.endsWith("em") || p.endsWith("rem")
                    || p.endsWith("vw") || p.endsWith("vh") || p.equals("0")) {
                Dimension d = Dimension.parse(p);
                if (d != null) {
                    float v = d.value();
                    if (v < 0) v = 0;
                    return new BorderEdges(v, v, v, v);
                }
            }
        }
        return new BorderEdges(0, 0, 0, 0);
    }

    private static float parseLen(String s) {
        Dimension d = Dimension.parse(s);
        if (d == null) return 0f;
        return d.value();
    }
}
