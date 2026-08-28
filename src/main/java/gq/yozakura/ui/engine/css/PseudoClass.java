package gq.yozakura.ui.engine.css;

/**
 * CSS 伪类。MVP 支持 hover、active、focus、checked、root。
 *
 * <p>{@code :root} 匹配文档根元素（无父节点的元素），用于在根节点声明
 * CSS 自定义变量（如 {@code :root { --accent: #fff; }}）。
 */
public enum PseudoClass {
    HOVER,
    ACTIVE,
    FOCUS,
    CHECKED,
    ROOT;

    /** 按名称解析伪类；未知名称返回 null（由调用方决定是否报错）。 */
    public static PseudoClass byName(String name) {
        if (name.equals("hover")) return HOVER;
        if (name.equals("active")) return ACTIVE;
        if (name.equals("focus")) return FOCUS;
        if (name.equals("checked")) return CHECKED;
        if (name.equals("root")) return ROOT;
        return null;
    }
}
