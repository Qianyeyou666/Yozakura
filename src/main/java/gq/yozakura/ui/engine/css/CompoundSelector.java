package gq.yozakura.ui.engine.css;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 复合选择器：同一 compound 内的 tag、id、class、伪类、属性选择器组合。
 * 不可变值对象。匹配时要求元素同时满足所有约束。
 */
public final class CompoundSelector {
    private final String tag;
    private final String id;
    private final List<String> classes;
    private final Set<PseudoClass> pseudos;
    private final List<AttrSelector> attrs;

    public CompoundSelector(String tag, String id, List<String> classes,
                            Set<PseudoClass> pseudos, List<AttrSelector> attrs) {
        this.tag = tag;
        this.id = id;
        // 防御性复制：调用方修改原始 list 不得影响已构造的 CompoundSelector 状态。
        // unmodifiableList 只是视图，原始 list 仍可被修改。
        this.classes = classes == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(classes));
        this.pseudos = pseudos == null || pseudos.isEmpty()
                ? Collections.<PseudoClass>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(pseudos));
        this.attrs = attrs == null
                ? Collections.<AttrSelector>emptyList()
                : Collections.unmodifiableList(new ArrayList<AttrSelector>(attrs));
    }

    public String tag() { return tag; }
    public String id() { return id; }
    public boolean hasId() { return id != null; }
    public List<String> classes() { return classes; }
    public Set<PseudoClass> pseudos() { return pseudos; }
    public List<AttrSelector> attrs() { return attrs; }
}
