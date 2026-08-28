package gq.yozakura.ui.engine.css;

import gq.yozakura.ui.engine.dom.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CSS 规则：选择器列表 + 声明列表 + sourceOrder + 源位置。不可变值对象。
 *
 * <p>多个选择器共享同一声明块时（如 "div, span, p { ... }"），
 * selectors 列表包含 3 个元素。sourceOrder 用于同优先级 tie-break。
 */
public final class Rule {
    private final List<Selector> selectors;
    private final List<Declaration> declarations;
    private final int sourceOrder;
    private final SourcePosition sourcePosition;

    public Rule(List<Selector> selectors, List<Declaration> declarations,
                int sourceOrder, SourcePosition sourcePosition) {
        if (selectors == null || selectors.isEmpty()) {
            throw new IllegalArgumentException("selectors must not be null or empty");
        }
        if (declarations == null) {
            throw new IllegalArgumentException("declarations must not be null");
        }
        // 防御性复制：调用方修改原始 list 不得影响已构造的 Rule 状态。
        // 仅用 unmodifiableList 包裹原始 list 是不安全的——它只是视图，原始 list 仍可被修改。
        this.selectors = Collections.unmodifiableList(new ArrayList<Selector>(selectors));
        this.declarations = Collections.unmodifiableList(new ArrayList<Declaration>(declarations));
        this.sourceOrder = sourceOrder;
        this.sourcePosition = sourcePosition == null ? SourcePosition.unknown() : sourcePosition;
    }

    public List<Selector> selectors() {
        return selectors;
    }

    /** 便捷方法：返回第一个选择器的文本，用于单选择器规则。 */
    public String selectorText() {
        return selectors.get(0).text();
    }

    public List<Declaration> declarations() {
        return declarations;
    }

    public int sourceOrder() {
        return sourceOrder;
    }

    public SourcePosition sourcePosition() {
        return sourcePosition;
    }

    @Override
    public String toString() {
        return selectors + " { " + declarations.size() + " declarations }";
    }
}
