package gq.yozakura.ui.engine.css;

import gq.yozakura.ui.engine.dom.DomNode;
import gq.yozakura.ui.engine.dom.ElementNode;

import java.util.List;

/**
 * 将 {@link ParsedSelector} 与 {@link ElementNode} 进行匹配。
 *
 * <p>采用标准 CSS 右到左匹配：最后一个 compound 必须匹配目标元素，
 * 其余 compound 按 combinator 约束沿祖先链向上匹配。
 *
 * <p>匹配只读取元素的静态属性（tag/id/class/属性）与交互状态
 * （hover/active/focus/checked），不修改任何状态。
 */
public final class SelectorMatcher {

    /**
     * 判断 {@code selector} 是否匹配 {@code element}。
     *
     * @param selector 已解析的选择器，不得为 null
     * @param element  目标元素，不得为 null
     * @return 匹配返回 true
     */
    public boolean matches(ParsedSelector selector, ElementNode element) {
        if (selector == null || element == null) {
            throw new IllegalArgumentException("selector and element must not be null");
        }
        int last = selector.compounds().size() - 1;
        if (!matchesCompound(selector.compounds().get(last), element)) {
            return false;
        }
        return matchesRest(selector, last, element.parent());
    }

    /**
     * 已匹配 {@code matchedIndex} 对应的 compound，现在向上匹配剩余 compound。
     *
     * @param matchedIndex 刚匹配成功的 compound 下标
     * @param ancestor     下一个候选祖先（matchedIndex 对应元素的父节点）
     */
    private boolean matchesRest(ParsedSelector selector, int matchedIndex, DomNode ancestor) {
        int nextIndex = matchedIndex - 1;
        if (nextIndex < 0) {
            return true;
        }
        CompoundSelector next = selector.compounds().get(nextIndex);
        // combinators[nextIndex] 连接 compounds[nextIndex] 与 compounds[nextIndex+1]
        Combinator comb = selector.combinator(nextIndex);
        if (comb == Combinator.CHILD) {
            if (!(ancestor instanceof ElementNode)) {
                return false;
            }
            if (!matchesCompound(next, (ElementNode) ancestor)) {
                return false;
            }
            return matchesRest(selector, nextIndex, ancestor.parent());
        }
        // DESCENDANT：沿祖先链寻找匹配
        DomNode cur = ancestor;
        while (cur != null) {
            if (cur instanceof ElementNode && matchesCompound(next, (ElementNode) cur)) {
                if (matchesRest(selector, nextIndex, cur.parent())) {
                    return true;
                }
            }
            cur = cur.parent();
        }
        return false;
    }

    private boolean matchesCompound(CompoundSelector c, ElementNode e) {
        if (c.tag() != null && !c.tag().equals(e.tag())) {
            return false;
        }
        if (c.hasId()) {
            if (e.id() == null || !c.id().equals(e.id())) {
                return false;
            }
        }
        List<String> classes = c.classes();
        for (int i = 0; i < classes.size(); i++) {
            if (!e.hasClass(classes.get(i))) {
                return false;
            }
        }
        for (PseudoClass pc : c.pseudos()) {
            if (!matchesPseudo(pc, e)) {
                return false;
            }
        }
        List<AttrSelector> attrs = c.attrs();
        for (int i = 0; i < attrs.size(); i++) {
            if (!matchesAttr(attrs.get(i), e)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesPseudo(PseudoClass pc, ElementNode e) {
        switch (pc) {
            case HOVER:
                return e.isHovered();
            case ACTIVE:
                return e.isActive();
            case FOCUS:
                return e.isFocused();
            case CHECKED:
                return e.isChecked();
            case ROOT:
                // :root 匹配文档根元素（无父节点的元素）
                return e.parent() == null;
            default:
                return false;
        }
    }

    private boolean matchesAttr(AttrSelector a, ElementNode e) {
        String val = e.attribute(a.name());
        if (a.isPresence()) {
            return val != null;
        }
        return val != null && val.equals(a.value());
    }
}
