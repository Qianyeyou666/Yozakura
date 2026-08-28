package gq.yozakura.ui.engine.binding;

import gq.yozakura.ui.engine.dom.ElementNode;

/**
 * 列表项模板：由 {@link ListRepeater} 在为每个数据项生成 DOM 节点时调用。
 *
 * <p>AGENTS.md 契约："controlled repeaters/templates for module and setting lists"。
 *
 * <p>实现示例：
 * <pre>
 *   ItemTemplate&lt;Module&gt; template = new ItemTemplate&lt;Module&gt;() {
 *       public ElementNode create(Module m, int index) {
 *           ElementNode div = ElementNode.create("div")
 *               .withId("module-" + m.id());
 *           div.addClass("module");
 *           div.setAttribute("data-module-id", m.id());
 *           // ... 子节点（label, toggle, ...）
 *           return div;
 *       }
 *   };
 * </pre>
 *
 * @param <T> 数据项类型
 */
public interface ItemTemplate<T> {
    /**
     * 为单个数据项生成 ElementNode。
     *
     * @param item  数据项（非 null，ListRepeater 已校验）
     * @param index 在列表中的索引（0-based）
     * @return 新创建的 ElementNode（ListRepeater 会调用 appendChild 挂载）
     */
    ElementNode create(T item, int index);
}
