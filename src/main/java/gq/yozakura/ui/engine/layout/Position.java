package gq.yozakura.ui.engine.layout;

/**
 * position 属性枚举。
 *
 * <p>MVP 子集支持：
 * <ul>
 *   <li>{@link #STATIC}：默认，按文档流定位</li>
 *   <li>{@link #RELATIVE}：相对自身静态位置偏移（阶段 7 实现）</li>
 *   <li>{@link #ABSOLUTE}：脱离文档流，相对最近 positioned 祖先定位（阶段 7 实现）</li>
 * </ul>
 *
 * <p>{@code fixed} 不在 MVP 子集内。
 */
public enum Position {
    STATIC,
    RELATIVE,
    ABSOLUTE;

    /** 解析字符串；null/空/未知值返回 {@link #STATIC}（CSS 默认）。 */
    public static Position parse(String raw) {
        if (raw == null) return STATIC;
        String s = raw.trim();
        if (s.equals("relative")) return RELATIVE;
        if (s.equals("absolute")) return ABSOLUTE;
        return STATIC;
    }
}
