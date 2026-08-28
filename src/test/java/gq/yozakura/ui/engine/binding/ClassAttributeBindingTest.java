package gq.yozakura.ui.engine.binding;

import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 5 切片 5.5：ClassBinding / AttributeBinding 测试。
 *
 * <p>验证契约（AGENTS.md）：
 * <ul>
 *   <li>"class/attribute 状态绑定" — ObservableValue 变化时同步到 DOM</li>
 *   <li>"STYLE_DIRTY: selector state, inherited property or CSS variable changed"
 *       — class/attribute 变化可能影响 selector 匹配，故标 STYLE_DIRTY</li>
 *   <li>同值去抖：ObservableValue 已自动去抖，但 binding 自身亦不应重复设 class</li>
 *   <li>dispose 解除订阅，防止内存泄漏与关闭 UI 后回调</li>
 * </ul>
 *
 * <p>测试同时验证 ElementNode 新增的 addClass/removeClass/setAttribute/removeAttribute 可变 API。
 */
public class ClassAttributeBindingTest {

    /** 记录 dirty 通知的 sink。 */
    private static final class RecordingSink implements DirtyFlagSink {
        int styleDirty;
        int layoutDirty;
        int paintDirty;

        @Override
        public void markStyleDirty() { styleDirty++; }
        @Override
        public void markLayoutDirty() { layoutDirty++; }
        @Override
        public void markPaintDirty() { paintDirty++; }

        void reset() { styleDirty = layoutDirty = paintDirty = 0; }
    }

    // ---- ElementNode 可变 API ----

    @Test
    public void element_addClass_adds_if_absent() {
        ElementNode e = ElementNode.create("div");
        e.addClass("foo");
        assertTrue(e.hasClass("foo"));
        assertEquals(1, e.classes().size());
    }

    @Test
    public void element_addClass_idempotent() {
        ElementNode e = ElementNode.create("div");
        e.addClass("foo");
        e.addClass("foo");
        assertEquals(1, e.classes().size());
    }

    @Test
    public void element_removeClass_removes_if_present() {
        ElementNode e = ElementNode.create("div");
        e.addClass("foo");
        e.addClass("bar");
        e.removeClass("foo");
        assertFalse(e.hasClass("foo"));
        assertTrue(e.hasClass("bar"));
    }

    @Test
    public void element_removeClass_no_op_if_absent() {
        ElementNode e = ElementNode.create("div");
        e.removeClass("foo");
        assertFalse(e.hasClass("foo"));
    }

    @Test
    public void element_setAttribute_sets_value() {
        ElementNode e = ElementNode.create("input");
        e.setAttribute("type", "text");
        assertEquals("text", e.attribute("type"));
    }

    @Test
    public void element_setAttribute_overwrites() {
        ElementNode e = ElementNode.create("input");
        e.setAttribute("type", "text");
        e.setAttribute("type", "password");
        assertEquals("password", e.attribute("type"));
    }

    @Test
    public void element_removeAttribute_removes() {
        ElementNode e = ElementNode.create("input");
        e.setAttribute("type", "text");
        e.removeAttribute("type");
        assertNull(e.attribute("type"));
    }

    @Test
    public void element_removeAttribute_no_op_if_absent() {
        ElementNode e = ElementNode.create("input");
        e.removeAttribute("type");
        assertNull(e.attribute("type"));
    }

    // ---- ClassBinding ----

    @Test
    public void classBinding_true_adds_class_and_marks_style_dirty() {
        ElementNode e = ElementNode.create("div");
        ObservableValue<Boolean> v = new ObservableValue<Boolean>(false);
        RecordingSink sink = new RecordingSink();
        ClassBinding.bind(e, "active", v, sink);

        sink.reset();
        v.set(true);

        assertTrue(e.hasClass("active"));
        assertEquals(1, sink.styleDirty);
        assertEquals(0, sink.layoutDirty);
        assertEquals(0, sink.paintDirty);
    }

    @Test
    public void classBinding_false_removes_class() {
        ElementNode e = ElementNode.create("div");
        e.addClass("active");
        ObservableValue<Boolean> v = new ObservableValue<Boolean>(true);
        RecordingSink sink = new RecordingSink();
        ClassBinding.bind(e, "active", v, sink);

        sink.reset();
        v.set(false);

        assertFalse(e.hasClass("active"));
        assertEquals(1, sink.styleDirty);
    }

    @Test
    public void classBinding_initial_value_applied_on_bind() {
        // bind 时立即同步当前值
        ElementNode e = ElementNode.create("div");
        ObservableValue<Boolean> v = new ObservableValue<Boolean>(true);
        RecordingSink sink = new RecordingSink();
        ClassBinding.bind(e, "active", v, sink);

        assertTrue(e.hasClass("active"));
        assertEquals(1, sink.styleDirty);  // 初始应用也标 dirty
    }

    @Test
    public void classBinding_same_value_no_dirty() {
        ElementNode e = ElementNode.create("div");
        e.addClass("active");
        ObservableValue<Boolean> v = new ObservableValue<Boolean>(true);
        RecordingSink sink = new RecordingSink();
        ClassBinding.bind(e, "active", v, sink);
        sink.reset();

        // ObservableValue 同值不通知（已在 5.1 验证）
        v.set(true);
        // 由于 ObservableValue 同值去抖，binding 不被调用
        assertEquals(0, sink.styleDirty);
        assertTrue(e.hasClass("active"));
    }

    @Test
    public void classBinding_toggle_multiple_times() {
        ElementNode e = ElementNode.create("div");
        ObservableValue<Boolean> v = new ObservableValue<Boolean>(false);
        RecordingSink sink = new RecordingSink();
        ClassBinding.bind(e, "active", v, sink);
        sink.reset();

        v.set(true);   // add
        assertTrue(e.hasClass("active"));
        v.set(false);  // remove
        assertFalse(e.hasClass("active"));
        v.set(true);   // add again
        assertTrue(e.hasClass("active"));

        assertEquals(3, sink.styleDirty);
    }

    @Test
    public void classBinding_dispose_unsubscribes() {
        ElementNode e = ElementNode.create("div");
        ObservableValue<Boolean> v = new ObservableValue<Boolean>(false);
        RecordingSink sink = new RecordingSink();
        ClassBinding binding = ClassBinding.bind(e, "active", v, sink);

        binding.dispose();
        sink.reset();

        v.set(true);
        assertFalse(e.hasClass("active"));
        assertEquals(0, sink.styleDirty);
    }

    @Test
    public void classBinding_multiple_classes_independent() {
        ElementNode e = ElementNode.create("div");
        ObservableValue<Boolean> v1 = new ObservableValue<Boolean>(false);
        ObservableValue<Boolean> v2 = new ObservableValue<Boolean>(false);
        RecordingSink sink = new RecordingSink();
        ClassBinding.bind(e, "a", v1, sink);
        ClassBinding.bind(e, "b", v2, sink);
        sink.reset();

        v1.set(true);
        assertTrue(e.hasClass("a"));
        assertFalse(e.hasClass("b"));
        v2.set(true);
        assertTrue(e.hasClass("b"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void classBinding_null_element_rejected() {
        ClassBinding.bind(null, "x", new ObservableValue<Boolean>(false), new RecordingSink());
    }

    @Test(expected = IllegalArgumentException.class)
    public void classBinding_null_observable_rejected() {
        ClassBinding.bind(ElementNode.create("div"), "x", null, new RecordingSink());
    }

    @Test(expected = IllegalArgumentException.class)
    public void classBinding_null_className_rejected() {
        ClassBinding.bind(ElementNode.create("div"), null,
                new ObservableValue<Boolean>(false), new RecordingSink());
    }

    @Test(expected = IllegalArgumentException.class)
    public void classBinding_empty_className_rejected() {
        ClassBinding.bind(ElementNode.create("div"), "",
                new ObservableValue<Boolean>(false), new RecordingSink());
    }

    // ---- AttributeBinding ----

    @Test
    public void attributeBinding_sets_attribute_and_marks_style_dirty() {
        ElementNode e = ElementNode.create("input");
        ObservableValue<String> v = new ObservableValue<String>("text");
        RecordingSink sink = new RecordingSink();
        AttributeBinding.bind(e, "type", v, sink);

        sink.reset();
        v.set("password");

        assertEquals("password", e.attribute("type"));
        assertEquals(1, sink.styleDirty);
        assertEquals(0, sink.layoutDirty);
        assertEquals(0, sink.paintDirty);
    }

    @Test
    public void attributeBinding_initial_value_applied_on_bind() {
        ElementNode e = ElementNode.create("input");
        ObservableValue<String> v = new ObservableValue<String>("text");
        RecordingSink sink = new RecordingSink();
        AttributeBinding.bind(e, "type", v, sink);

        assertEquals("text", e.attribute("type"));
        assertEquals(1, sink.styleDirty);
    }

    @Test
    public void attributeBinding_dispose_unsubscribes() {
        ElementNode e = ElementNode.create("input");
        ObservableValue<String> v = new ObservableValue<String>("text");
        RecordingSink sink = new RecordingSink();
        AttributeBinding b = AttributeBinding.bind(e, "type", v, sink);

        b.dispose();
        sink.reset();
        v.set("password");
        assertEquals("text", e.attribute("type"));  // 未变化
        assertEquals(0, sink.styleDirty);
    }

    @Test
    public void attributeBinding_null_value_removes_attribute() {
        // 字符串属性 null → removeAttribute
        ElementNode e = ElementNode.create("input");
        e.setAttribute("type", "text");
        ObservableValue<String> v = new ObservableValue<String>("text");
        RecordingSink sink = new RecordingSink();
        AttributeBinding.bind(e, "type", v, sink);
        sink.reset();

        v.set(null);
        assertNull(e.attribute("type"));
        assertEquals(1, sink.styleDirty);
    }

    @Test(expected = IllegalArgumentException.class)
    public void attributeBinding_null_element_rejected() {
        AttributeBinding.bind(null, "x", new ObservableValue<String>("v"), new RecordingSink());
    }

    @Test(expected = IllegalArgumentException.class)
    public void attributeBinding_null_attrName_rejected() {
        AttributeBinding.bind(ElementNode.create("div"), null,
                new ObservableValue<String>("v"), new RecordingSink());
    }

    // ---- 端到端：binding + selector 上下文 ----

    @Test
    public void end_to_end_binding_changes_class_then_selector_would_match() {
        // 验证 binding 改变 class 后，STYLE_DIRTY 标记让 StyleResolver 重算
        // 本测试只验证 class 已被加 + dirty 已标；实际 selector 匹配在 StyleResolver 测试中
        ElementNode e = ElementNode.create("div");
        ObservableValue<Boolean> v = new ObservableValue<Boolean>(false);
        RecordingSink sink = new RecordingSink();
        ClassBinding.bind(e, "selected", v, sink);
        sink.reset();

        v.set(true);

        assertTrue(e.hasClass("selected"));
        assertEquals(1, sink.styleDirty);
        // 后续 StyleResolver 检测 STYLE_DIRTY → 重新匹配 ".selected" 规则
    }
}
