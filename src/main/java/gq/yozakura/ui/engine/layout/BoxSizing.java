package gq.yozakura.ui.engine.layout;

/**
 * box-sizing 枚举：content-box（默认）或 border-box。
 *
 * <p>决定 width/height 解释为内容区尺寸还是边框盒尺寸。
 * 提供两种尺寸的相互换算：
 * <ul>
 *   <li>{@code contentWidth(width, pad, border)}：从指定 width 推导内容区宽</li>
 *   <li>{@code borderBoxWidth(width, pad, border)}：从指定 width 推导边框盒宽</li>
 * </ul>
 */
public enum BoxSizing {
    /** width/height 解释为内容区尺寸。边框盒 = content + padding + border。 */
    CONTENT_BOX {
        @Override
        public float contentWidth(float declaredWidth, PaddingEdges pad, BorderEdges border) {
            return declaredWidth;
        }

        @Override
        public float borderBoxWidth(float declaredWidth, PaddingEdges pad, BorderEdges border) {
            return declaredWidth + pad.horizontalSum() + border.horizontalSum();
        }

        @Override
        public float contentHeight(float declaredHeight, PaddingEdges pad, BorderEdges border) {
            return declaredHeight;
        }

        @Override
        public float borderBoxHeight(float declaredHeight, PaddingEdges pad, BorderEdges border) {
            return declaredHeight + pad.verticalSum() + border.verticalSum();
        }
    },

    /** width/height 解释为边框盒尺寸。内容区 = border-box - padding - border。 */
    BORDER_BOX {
        @Override
        public float contentWidth(float declaredWidth, PaddingEdges pad, BorderEdges border) {
            return declaredWidth - pad.horizontalSum() - border.horizontalSum();
        }

        @Override
        public float borderBoxWidth(float declaredWidth, PaddingEdges pad, BorderEdges border) {
            return declaredWidth;
        }

        @Override
        public float contentHeight(float declaredHeight, PaddingEdges pad, BorderEdges border) {
            return declaredHeight - pad.verticalSum() - border.verticalSum();
        }

        @Override
        public float borderBoxHeight(float declaredHeight, PaddingEdges pad, BorderEdges border) {
            return declaredHeight;
        }
    };

    /**
     * 从声明的 width 推导内容区宽。
     * 对 content-box 直接返回；对 border-box 减去 padding/border。
     */
    public abstract float contentWidth(float declaredWidth, PaddingEdges pad, BorderEdges border);

    /** 从声明的 width 推导边框盒宽。 */
    public abstract float borderBoxWidth(float declaredWidth, PaddingEdges pad, BorderEdges border);

    /** 从声明的 height 推导内容区高。 */
    public abstract float contentHeight(float declaredHeight, PaddingEdges pad, BorderEdges border);

    /** 从声明的 height 推导边框盒高。 */
    public abstract float borderBoxHeight(float declaredHeight, PaddingEdges pad, BorderEdges border);

    /** 解析字符串；null/空/未知返回 CONTENT_BOX（CSS 默认）。 */
    public static BoxSizing parse(String raw) {
        if (raw == null) return CONTENT_BOX;
        String s = raw.trim();
        if (s.equals("border-box")) return BORDER_BOX;
        // content-box 或未知值都返回默认
        return CONTENT_BOX;
    }
}
