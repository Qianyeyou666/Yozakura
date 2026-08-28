package gq.yozakura.ui.engine.css;

/**
 * CSS 选择器组合符。MVP 支持后代（空格）与直接子节点（大于号）。
 */
public enum Combinator {
    /** 后代：空格分隔，匹配任意层级的后代。 */
    DESCENDANT,
    /** 直接子节点：大于号分隔，仅匹配直接子元素。 */
    CHILD
}
