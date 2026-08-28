package gq.yozakura.ui.engine.css;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * CSS 继承属性集合。
 *
 * <p>仅包含 MVP 子集中需要从父元素继承的普通属性名。
 * 自定义变量（{@code --*}）始终继承，由 {@link ComputedStyle} 单独存储，不在此集合中。
 *
 * <p>非此集合中的属性（如 {@code background-color}、{@code border}、{@code width}）
 * 不继承，未显式声明时使用初始值（阶段 1 表现为 absent）。
 */
final class InheritedProperties {

    private static final Set<String> INHERITED;

    static {
        Set<String> s = new HashSet<String>();
        // 排版相关
        s.add("color");
        s.add("font-family");
        s.add("font-size");
        s.add("font-weight");
        s.add("font-style");
        s.add("line-height");
        s.add("letter-spacing");
        s.add("word-spacing");
        s.add("text-align");
        s.add("text-indent");
        s.add("text-transform");
        s.add("white-space");
        // 其他常见继承属性
        s.add("visibility");
        s.add("cursor");
        s.add("direction");
        INHERITED = Collections.unmodifiableSet(s);
    }

    static boolean contains(String name) {
        return name != null && INHERITED.contains(name);
    }

    private InheritedProperties() {
        // 工具类，不实例化
    }
}
