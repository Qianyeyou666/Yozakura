package gq.yozakura.ui.engine.binding;

import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 5 切片 5.6：ListRepeater 数据驱动子节点渲染测试。
 *
 * <p>验证契约（AGENTS.md）：
 * <ul>
 *   <li>"controlled repeaters/templates for module and setting lists"</li>
 *   <li>数据列表变化时同步子节点；标 STYLE_DIRTY + LAYOUT_DIRTY（子树结构变化）</li>
 *   <li>支持 ObservableValue&lt;List&gt; 订阅 与 显式 setData 两种模式</li>
 *   <li>dispose 解除订阅</li>
 * </ul>
 *
 * <p>MVP 实现：每次 setData 全量重建子节点（清空 + 重新生成）；
 * diff 优化（保留稳定节点）记录为后续阶段。
 */
public class ListRepeaterTest {

    /** 简单模板：为每条数据生成 &lt;div class="item"&gt;{data}&lt;/div&gt;。 */
    private static final class StringItemTemplate implements ItemTemplate<String> {
        @Override
        public ElementNode create(String item, int index) {
            ElementNode e = ElementNode.create("div");
            e.addClass("item");
            e.setAttribute("data-idx", String.valueOf(index));
            e.setAttribute("data-value", item);
            return e;
        }
    }

    private static final class RecordingSink implements DirtyFlagSink {
        int styleDirty;
        int layoutDirty;
        int paintDirty;
        @Override public void markStyleDirty() { styleDirty++; }
        @Override public void markLayoutDirty() { layoutDirty++; }
        @Override public void markPaintDirty() { paintDirty++; }
        void reset() { styleDirty = layoutDirty = paintDirty = 0; }
    }

    // ---- ElementNode.clearChildren ----

    @Test
    public void element_clearChildren_removes_all() {
        ElementNode parent = ElementNode.create("div");
        ElementNode a = ElementNode.create("div");
        ElementNode b = ElementNode.create("div");
        parent.appendChild(a);
        parent.appendChild(b);
        assertEquals(2, parent.childCount());

        parent.clearChildren();

        assertEquals(0, parent.childCount());
        // 子节点的 parent 应为 null
        assertEquals(null, a.parent());
        assertEquals(null, b.parent());
    }

    @Test
    public void element_clearChildren_no_op_when_empty() {
        ElementNode parent = ElementNode.create("div");
        parent.clearChildren();
        assertEquals(0, parent.childCount());
    }

    // ---- setData 基本行为 ----

    @Test
    public void setData_generates_children_per_item() {
        ElementNode container = ElementNode.create("div");
        RecordingSink sink = new RecordingSink();
        ListRepeater<String> r = new ListRepeater<String>(
                container, new StringItemTemplate(), sink);

        r.setData(Arrays.asList("a", "b", "c"));

        assertEquals(3, container.childCount());
        ElementNode c0 = (ElementNode) container.child(0);
        assertEquals("div", c0.tag());
        assertTrue(c0.hasClass("item"));
        assertEquals("a", c0.attribute("data-value"));
        assertEquals("0", c0.attribute("data-idx"));
    }

    @Test
    public void setData_empty_clears_children() {
        ElementNode container = ElementNode.create("div");
        RecordingSink sink = new RecordingSink();
        ListRepeater<String> r = new ListRepeater<String>(
                container, new StringItemTemplate(), sink);

        r.setData(Arrays.asList("a", "b"));
        assertEquals(2, container.childCount());

        r.setData(Collections.<String>emptyList());
        assertEquals(0, container.childCount());
    }

    @Test
    public void setData_replaces_previous_children() {
        ElementNode container = ElementNode.create("div");
        RecordingSink sink = new RecordingSink();
        ListRepeater<String> r = new ListRepeater<String>(
                container, new StringItemTemplate(), sink);

        r.setData(Arrays.asList("a", "b"));
        ElementNode firstGen = (ElementNode) container.child(0);

        r.setData(Arrays.asList("x", "y", "z"));
        ElementNode secondGen = (ElementNode) container.child(0);

        assertEquals(3, container.childCount());
        // 重新生成 → 新实例（MVP 全量重建）
        assertNotSame(firstGen, secondGen);
        assertEquals("x", secondGen.attribute("data-value"));
        // 旧节点已被解挂
        assertEquals(null, firstGen.parent());
    }

    @Test
    public void setData_marks_style_and_layout_dirty() {
        ElementNode container = ElementNode.create("div");
        RecordingSink sink = new RecordingSink();
        ListRepeater<String> r = new ListRepeater<String>(
                container, new StringItemTemplate(), sink);

        sink.reset();
        r.setData(Arrays.asList("a", "b"));

        // 子节点结构变化 → STYLE + LAYOUT dirty
        assertTrue("style dirty marked", sink.styleDirty > 0);
        assertTrue("layout dirty marked", sink.layoutDirty > 0);
    }

    @Test
    public void setData_same_data_still_rebuilds_and_marks_dirty() {
        // MVP 全量重建：即使数据相同也重建（无 diff）
        // 这是简化策略；测试记录此行为以备后续优化时验证改变
        ElementNode container = ElementNode.create("div");
        RecordingSink sink = new RecordingSink();
        ListRepeater<String> r = new ListRepeater<String>(
                container, new StringItemTemplate(), sink);

        r.setData(Arrays.asList("a"));
        sink.reset();
        r.setData(Arrays.asList("a"));  // 相同数据

        assertTrue("rebuild marks dirty even for same data (MVP)", sink.styleDirty > 0);
    }

    // ---- ObservableValue<List> 订阅 ----

    @Test
    public void bindTo_subscribes_and_applies_initial() {
        ElementNode container = ElementNode.create("div");
        RecordingSink sink = new RecordingSink();
        ListRepeater<String> r = new ListRepeater<String>(
                container, new StringItemTemplate(), sink);

        ObservableValue<List<String>> src = new ObservableValue<List<String>>(
                new ArrayList<String>(Arrays.asList("a", "b")));
        r.bindTo(src);

        assertEquals(2, container.childCount());
        assertTrue(sink.styleDirty > 0);
    }

    @Test
    public void bindTo_propagates_subsequent_changes() {
        ElementNode container = ElementNode.create("div");
        RecordingSink sink = new RecordingSink();
        ListRepeater<String> r = new ListRepeater<String>(
                container, new StringItemTemplate(), sink);

        ObservableValue<List<String>> src = new ObservableValue<List<String>>(
                new ArrayList<String>(Arrays.asList("a")));
        r.bindTo(src);
        sink.reset();

        // 修改 list 内容并 set（ObservableValue 比较 list 引用 → 不同实例视为变化）
        src.set(new ArrayList<String>(Arrays.asList("a", "b", "c")));

        assertEquals(3, container.childCount());
        assertTrue(sink.styleDirty > 0);
    }

    @Test
    public void bindTo_same_reference_no_notification() {
        // 若调用方 set 同一 list 引用，ObservableValue 同值去抖 → 不通知
        ElementNode container = ElementNode.create("div");
        RecordingSink sink = new RecordingSink();
        ListRepeater<String> r = new ListRepeater<String>(
                container, new StringItemTemplate(), sink);

        List<String> data = new ArrayList<String>(Arrays.asList("a"));
        ObservableValue<List<String>> src = new ObservableValue<List<String>>(data);
        r.bindTo(src);
        sink.reset();

        src.set(data);  // 同引用
        assertEquals(0, sink.styleDirty);
    }

    @Test
    public void bindTo_dispose_unsubscribes() {
        ElementNode container = ElementNode.create("div");
        RecordingSink sink = new RecordingSink();
        ListRepeater<String> r = new ListRepeater<String>(
                container, new StringItemTemplate(), sink);

        ObservableValue<List<String>> src = new ObservableValue<List<String>>(
                new ArrayList<String>(Arrays.asList("a")));
        r.bindTo(src);

        r.dispose();
        sink.reset();

        src.set(new ArrayList<String>(Arrays.asList("a", "b")));
        assertEquals(1, container.childCount());  // 未变化
        assertEquals(0, sink.styleDirty);
    }

    // ---- dispose ----

    @Test
    public void setData_after_dispose_no_op() {
        ElementNode container = ElementNode.create("div");
        RecordingSink sink = new RecordingSink();
        ListRepeater<String> r = new ListRepeater<String>(
                container, new StringItemTemplate(), sink);

        r.setData(Arrays.asList("a"));
        r.dispose();
        sink.reset();

        r.setData(Arrays.asList("a", "b", "c"));
        assertEquals(1, container.childCount());  // 未变化
        assertEquals(0, sink.styleDirty);
    }

    // ---- index 与 template ----

    @Test
    public void template_receives_correct_index() {
        ElementNode container = ElementNode.create("div");
        RecordingSink sink = new RecordingSink();
        ListRepeater<String> r = new ListRepeater<String>(
                container, new StringItemTemplate(), sink);

        r.setData(Arrays.asList("a", "b", "c", "d"));

        for (int i = 0; i < 4; i++) {
            ElementNode child = (ElementNode) container.child(i);
            assertEquals(String.valueOf(i), child.attribute("data-idx"));
        }
    }

    @Test
    public void template_can_be_arbitrary_element() {
        ElementNode container = ElementNode.create("ul");
        RecordingSink sink = new RecordingSink();
        ItemTemplate<Integer> liTemplate = new ItemTemplate<Integer>() {
            @Override
            public ElementNode create(Integer item, int index) {
                ElementNode li = ElementNode.create("li");
                li.setAttribute("data-n", String.valueOf(item));
                return li;
            }
        };
        ListRepeater<Integer> r = new ListRepeater<Integer>(container, liTemplate, sink);

        r.setData(Arrays.asList(1, 2, 3));

        assertEquals(3, container.childCount());
        assertEquals("li", ((ElementNode) container.child(0)).tag());
        assertEquals("1", ((ElementNode) container.child(0)).attribute("data-n"));
    }

    // ---- null/data 校验 ----

    @Test(expected = IllegalArgumentException.class)
    public void constructor_null_container_rejected() {
        new ListRepeater<String>(null, new StringItemTemplate(), new RecordingSink());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_null_template_rejected() {
        new ListRepeater<String>(ElementNode.create("div"), null, new RecordingSink());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_null_sink_rejected() {
        new ListRepeater<String>(ElementNode.create("div"),
                new StringItemTemplate(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setData_null_rejected() {
        ListRepeater<String> r = new ListRepeater<String>(
                ElementNode.create("div"), new StringItemTemplate(), new RecordingSink());
        r.setData(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setData_null_item_in_list_rejected() {
        ListRepeater<String> r = new ListRepeater<String>(
                ElementNode.create("div"), new StringItemTemplate(), new RecordingSink());
        List<String> data = new ArrayList<String>();
        data.add("a");
        data.add(null);  // 不允许 null 元素
        r.setData(data);
    }

    // ---- 端到端：repeater + binding 配合 ----

    @Test
    public void end_to_end_repeater_with_class_binding_on_items() {
        // repeater 生成的每个 item 都被 binding 控制其 class
        ElementNode container = ElementNode.create("div");
        RecordingSink sink = new RecordingSink();
        ItemTemplate<String> templateWithBinding = new ItemTemplate<String>() {
            @Override
            public ElementNode create(String item, int index) {
                ElementNode e = ElementNode.create("div");
                e.addClass("item");
                e.setAttribute("data-value", item);
                return e;
            }
        };
        ListRepeater<String> r = new ListRepeater<String>(
                container, templateWithBinding, sink);

        r.setData(Arrays.asList("a", "b", "c"));

        // 验证子节点全部生成
        assertEquals(3, container.childCount());
        // 实际场景中 host 层会为每个 item 创建独立的 ObservableValue 与 ClassBinding
        // 本测试仅验证 repeater 生成结构正确
        for (int i = 0; i < 3; i++) {
            ElementNode child = (ElementNode) container.child(i);
            assertTrue(child.hasClass("item"));
        }
    }
}
