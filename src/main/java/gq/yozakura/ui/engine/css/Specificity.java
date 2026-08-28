package gq.yozakura.ui.engine.css;

import java.util.List;

/**
 * CSS 选择器优先级（specificity）。
 *
 * <p>三元组 (a, b, c)：
 * <ul>
 *   <li>a = id 选择器个数</li>
 *   <li>b = class、属性选择器、伪类个数</li>
 *   <li>c = tag 选择器个数</li>
 * </ul>
 *
 * <p>比较顺序：a → b → c。同 specificity 时由 source order 决定胜者（见 {@link Rule#sourceOrder()}）。
 *
 * <p>不可变值对象，实现 {@link Comparable}。
 */
public final class Specificity implements Comparable<Specificity> {
    private final int ids;
    private final int classes;
    private final int tags;

    private Specificity(int ids, int classes, int tags) {
        this.ids = ids;
        this.classes = classes;
        this.tags = tags;
    }

    /** 直接构造 (a, b, c) 三元组，主要用于测试与显式比较。 */
    public static Specificity ofValues(int ids, int classes, int tags) {
        return new Specificity(ids, classes, tags);
    }

    /** 计算已解析选择器的 specificity：累加所有 compound 的各部分计数。 */
    public static Specificity of(ParsedSelector selector) {
        if (selector == null) {
            throw new IllegalArgumentException("selector must not be null");
        }
        int ids = 0;
        int classes = 0;
        int tags = 0;
        List<CompoundSelector> compounds = selector.compounds();
        for (int i = 0; i < compounds.size(); i++) {
            CompoundSelector c = compounds.get(i);
            if (c.hasId()) {
                ids++;
            }
            classes += c.classes().size();
            classes += c.pseudos().size();
            classes += c.attrs().size();
            if (c.tag() != null) {
                tags++;
            }
        }
        return new Specificity(ids, classes, tags);
    }

    public int ids() {
        return ids;
    }

    public int classes() {
        return classes;
    }

    public int tags() {
        return tags;
    }

    @Override
    public int compareTo(Specificity o) {
        if (ids != o.ids) {
            return ids - o.ids;
        }
        if (classes != o.classes) {
            return classes - o.classes;
        }
        return tags - o.tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Specificity)) return false;
        Specificity that = (Specificity) o;
        return ids == that.ids && classes == that.classes && tags == that.tags;
    }

    @Override
    public int hashCode() {
        return (ids * 31 + classes) * 31 + tags;
    }

    @Override
    public String toString() {
        return "(" + ids + "," + classes + "," + tags + ")";
    }
}
