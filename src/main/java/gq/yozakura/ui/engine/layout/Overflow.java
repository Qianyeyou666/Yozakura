package gq.yozakura.ui.engine.layout;

/**
 * overflow 属性枚举。
 *
 * <p>MVP 子集支持：
 * <ul>
 *   <li>{@link #VISIBLE}：默认，内容可溢出盒边界，不裁剪</li>
 *   <li>{@link #HIDDEN}：内容裁剪到 padding 盒；布局阶段显式高度不扩张到 children_sum</li>
 *   <li>{@link #AUTO}：MVP 等同 HIDDEN（不实现滚动条）</li>
 * </ul>
 *
 * <p>{@code scroll} 不在 MVP 子集内（按 AUTO 处理）。
 */
public enum Overflow {
    VISIBLE,
    HIDDEN,
    AUTO;

    /** 解析字符串；null/空/未知值返回 {@link #VISIBLE}（CSS 默认）。 */
    public static Overflow parse(String raw) {
        if (raw == null) return VISIBLE;
        String s = raw.trim();
        if (s.equals("hidden")) return HIDDEN;
        if (s.equals("auto")) return AUTO;
        if (s.equals("scroll")) return AUTO; // MVP：scroll 当作 auto（无滚动条）
        return VISIBLE;
    }

    /** 是否裁剪内容（hidden/auto/scroll 都裁剪）。 */
    public boolean clips() {
        return this != VISIBLE;
    }
}
