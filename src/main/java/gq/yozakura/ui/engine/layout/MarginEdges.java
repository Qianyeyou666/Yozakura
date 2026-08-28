package gq.yozakura.ui.engine.layout;

/**
 * Margin 边距：在 BoxEdges 基础上增加 auto 标记（每边独立）。
 *
 * <p>auto margin 用于 flex 居中或块级元素水平居中；解析时若值为 "auto" 则置标记，
 * 数值字段保持 0（实际值由布局算法在分配剩余空间时填入）。
 *
 * <p>不可变值对象。
 */
public final class MarginEdges extends BoxEdges {
    private final boolean topAuto;
    private final boolean rightAuto;
    private final boolean bottomAuto;
    private final boolean leftAuto;

    public MarginEdges(float top, float right, float bottom, float left,
                       boolean topAuto, boolean rightAuto,
                       boolean bottomAuto, boolean leftAuto) {
        super(top, right, bottom, left);
        this.topAuto = topAuto;
        this.rightAuto = rightAuto;
        this.bottomAuto = bottomAuto;
        this.leftAuto = leftAuto;
    }

    /**
     * 全零 MarginEdges 工厂，所有 auto 标记为 false。
     *
     * <p>协变返回 MarginEdges（而非父类 BoxEdges），避免调用方强制转换。
     * 注意：不得改为 private/static 降低可见性——会隐藏父类 {@link BoxEdges#zero()}
     * 的 public static 方法，导致编译失败。
     */
    public static MarginEdges zero() {
        return new MarginEdges(0, 0, 0, 0, false, false, false, false);
    }

    public boolean isTopAuto() { return topAuto; }
    public boolean isRightAuto() { return rightAuto; }
    public boolean isBottomAuto() { return bottomAuto; }
    public boolean isLeftAuto() { return leftAuto; }

    /** 解析单值 margin（如来自 margin-top: 10px）。 */
    public static MarginEdges parseSingle(String raw) {
        return parseShorthand(raw);
    }

    /**
     * 解析 margin 简写：1-4 个值，支持 "auto" 与长度。
     * <ul>
     *   <li>1 值：四边</li>
     *   <li>2 值：top/bottom, left/right</li>
     *   <li>3 值：top, left/right, bottom</li>
     *   <li>4 值：top, right, bottom, left</li>
     * </ul>
     */
    public static MarginEdges parseShorthand(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return zero();
        }
        String[] parts = raw.trim().split("\\s+");
        float[] values = new float[4];
        boolean[] autos = new boolean[4];
        if (parts.length == 1) {
            applyOne(parts[0], values, autos);
        } else if (parts.length == 2) {
            applyTwo(parts, values, autos);
        } else if (parts.length == 3) {
            applyThree(parts, values, autos);
        } else {
            applyFour(parts, values, autos);
        }
        return new MarginEdges(values[0], values[1], values[2], values[3],
                autos[0], autos[1], autos[2], autos[3]);
    }

    private static void applyOne(String p, float[] v, boolean[] a) {
        if (p.equals("auto")) {
            a[0] = a[1] = a[2] = a[3] = true;
        } else {
            float f = parseLen(p);
            v[0] = v[1] = v[2] = v[3] = f;
        }
    }

    private static void applyTwo(String[] parts, float[] v, boolean[] a) {
        // top/bottom = parts[0], left/right = parts[1]
        if (parts[0].equals("auto")) {
            a[0] = a[2] = true;
        } else {
            v[0] = v[2] = parseLen(parts[0]);
        }
        if (parts[1].equals("auto")) {
            a[1] = a[3] = true;
        } else {
            v[1] = v[3] = parseLen(parts[1]);
        }
    }

    private static void applyThree(String[] parts, float[] v, boolean[] a) {
        // top=0, left/right=1, bottom=2
        if (parts[0].equals("auto")) a[0] = true; else v[0] = parseLen(parts[0]);
        if (parts[1].equals("auto")) a[1] = a[3] = true; else v[1] = v[3] = parseLen(parts[1]);
        if (parts[2].equals("auto")) a[2] = true; else v[2] = parseLen(parts[2]);
    }

    private static void applyFour(String[] parts, float[] v, boolean[] a) {
        for (int i = 0; i < 4; i++) {
            if (parts[i].equals("auto")) {
                a[i] = true;
            } else {
                v[i] = parseLen(parts[i]);
            }
        }
    }

    private static float parseLen(String s) {
        Dimension d = Dimension.parse(s);
        return d == null ? 0f : d.value();
    }
}
