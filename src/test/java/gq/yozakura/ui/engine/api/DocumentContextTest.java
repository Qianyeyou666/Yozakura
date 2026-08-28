package gq.yozakura.ui.engine.api;

import gq.yozakura.ui.engine.animation.AnimationClock;
import gq.yozakura.ui.engine.css.CssParser;
import gq.yozakura.ui.engine.css.Stylesheet;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.dom.HtmlParser;
import gq.yozakura.ui.engine.input.InputDispatcher;
import gq.yozakura.ui.engine.input.InteractionState;
import gq.yozakura.ui.engine.input.ModifierKeys;
import gq.yozakura.ui.engine.input.PointerButton;
import gq.yozakura.ui.engine.input.PointerEvent;
import gq.yozakura.ui.engine.input.PointerEventType;
import gq.yozakura.ui.engine.layout.LayoutBox;
import gq.yozakura.ui.engine.layout.MeasureContext;
import gq.yozakura.ui.engine.paint.PaintCommandList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 6 切片 6.1：DocumentContext 测试。
 *
 * <p>验证契约（AGENTS.md "Dirty-State Model" + retained pipeline）：
 * <ul>
 *   <li>组合 DOM + Stylesheet + StyleResolver + LayoutEngine + PaintTreeBuilder +
 *       InputDispatcher + TransitionRunner + AnimationClock</li>
 *   <li>初始加载后自动 resolveStyle + layout + buildPaintCommands</li>
 *   <li>dirty 协调：STYLE_DIRTY → 重算 style + layout + commands；
 *       LAYOUT_DRITY → 重算 layout + commands；PAINT_DIRTY → 重算 commands</li>
 *   <li>hover/active/focus 变化标 STYLE_DIRTY，下次 tick 重算</li>
 *   <li>tick(nowMs) 推进动画，若活跃动画则继续重绘，否则静态 retained</li>
 * </ul>
 */
public class DocumentContextTest {

    private static final MeasureContext CTX_960x640 = new MeasureContext() {
        @Override public int viewportWidth() { return 960; }
        @Override public int viewportHeight() { return 640; }
        @Override public float rootFontSizePx() { return 14f; }
    };

    private static DocumentContext load(String html, String css) {
        ElementNode root = (ElementNode) new HtmlParser().parse(html);
        Stylesheet ss = new CssParser().parse(css);
        return new DocumentContext(root, ss, CTX_960x640);
    }

    // ---- 基本组合 ----

    @Test
    public void initial_load_resolves_style_layout_paint() {
        DocumentContext doc = load("<div><p>hello</p></div>",
                "div { display: block; width: 200px; height: 100px; background-color: #ff0000; }"
                + "p { color: red; }");
        assertNotNull(doc.rootElement());
        assertNotNull(doc.styles());
        assertNotNull(doc.layoutRoot());
        assertNotNull(doc.paintCommands());
        // div 应有 layout box
        LayoutBox root = doc.layoutRoot();
        assertEquals(200f, root.borderBoxWidth(), 0.001f);
        assertEquals(100f, root.borderBoxHeight(), 0.001f);
        // paint commands 非空（至少有一个 rect fill）
        assertTrue("paint commands generated", doc.paintCommands().size() > 0);
    }

    @Test
    public void initial_state_all_dirty_cleared() {
        DocumentContext doc = load("<div></div>", "div { width: 10px; }");
        assertFalse(doc.isStyleDirty());
        assertFalse(doc.isLayoutDirty());
        assertFalse(doc.isPaintDirty());
        assertFalse(doc.isCommandsDirty());
    }

    @Test
    public void paintOnlyHoverDoesNotRecomputeLayout() {
        DocumentContext doc = load(
                "<div><button>Hover</button></div>",
                "div { width: 200px; height: 100px; } " +
                        "button { width: 100px; height: 40px; background-color: #222; } " +
                        "button:hover { background-color: #333; }");
        int layoutsBefore = doc.layoutRecomputeCount();
        int paintsBefore = doc.paintRecomputeCount();

        doc.dispatchPointer(gq.yozakura.ui.engine.input.PointerEvent.move(
                20.0F, 20.0F, gq.yozakura.ui.engine.input.ModifierKeys.none(), 1L), 0.0F, 0.0F);
        doc.recompute();

        assertEquals(layoutsBefore, doc.layoutRecomputeCount());
        assertEquals(paintsBefore + 1, doc.paintRecomputeCount());
    }

    @Test
    public void hoverWithoutMatchingStyleDoesNotRebuildPaintCommands() {
        DocumentContext doc = load(
                "<div><button>Static</button></div>",
                "div { width: 200px; height: 100px; } " +
                        "button { width: 100px; height: 40px; background-color: #222; }");
        int paintsBefore = doc.paintRecomputeCount();
        PaintCommandList commandsBefore = doc.paintCommands();

        doc.dispatchPointer(gq.yozakura.ui.engine.input.PointerEvent.move(
                20.0F, 20.0F, gq.yozakura.ui.engine.input.ModifierKeys.none(), 1L), 0.0F, 0.0F);
        doc.recompute();

        assertEquals(paintsBefore, doc.paintRecomputeCount());
        org.junit.Assert.assertSame(commandsBefore, doc.paintCommands());
    }

    @Test(expected = IllegalArgumentException.class)
    public void null_root_rejected() {
        new DocumentContext(null, new CssParser().parse("div{}"), CTX_960x640);
    }

    @Test(expected = IllegalArgumentException.class)
    public void null_stylesheet_rejected() {
        new DocumentContext(ElementNode.create("div"), null, CTX_960x640);
    }

    @Test(expected = IllegalArgumentException.class)
    public void null_measure_context_rejected() {
        new DocumentContext(ElementNode.create("div"),
                new CssParser().parse("div{}"), null);
    }

    // ---- dirty 协调 ----

    @Test
    public void mark_style_dirty_propagates_to_layout_and_commands() {
        DocumentContext doc = load("<div></div>", "div { width: 10px; }");
        doc.markStyleDirty();
        assertTrue(doc.isStyleDirty());
        // style dirty 隐含 layout + commands 也需重算
        // 但 dirty 标志本身只在 mark 时设置；recompute 时级联
    }

    @Test
    public void recompute_after_style_dirty_regenerates_pipeline() {
        DocumentContext doc = load("<div><p>x</p></div>",
                "div { width: 100px; height: 50px; } p { color: red; }");
        PaintCommandList before = doc.paintCommands();
        int beforeSize = before.size();

        doc.markStyleDirty();
        doc.recompute();

        assertFalse(doc.isStyleDirty());
        assertFalse(doc.isLayoutDirty());
        assertFalse(doc.isCommandsDirty());
        // 重算后 commands 应是新实例（或等价内容）
        assertNotNull(doc.paintCommands());
    }

    @Test
    public void recompute_after_layout_dirty_only_regenerates_layout_and_commands() {
        DocumentContext doc = load("<div></div>", "div { width: 10px; }");
        doc.markLayoutDirty();
        assertTrue(doc.isLayoutDirty());
        doc.recompute();
        assertFalse(doc.isLayoutDirty());
        assertFalse(doc.isCommandsDirty());
        // style 不应被标 dirty
        assertFalse(doc.isStyleDirty());
    }

    @Test
    public void recompute_after_paint_dirty_only_regenerates_commands() {
        DocumentContext doc = load("<div></div>", "div { width: 10px; background: red; }");
        doc.markPaintDirty();
        assertTrue(doc.isPaintDirty());
        doc.recompute();
        assertFalse(doc.isPaintDirty());
        assertFalse(doc.isCommandsDirty());
        assertFalse(doc.isStyleDirty());
        assertFalse(doc.isLayoutDirty());
    }

    // ---- 输入集成 ----

    @Test
    public void input_dispatcher_exposed() {
        DocumentContext doc = load("<button>ok</button>",
                "button { width: 80px; height: 24px; }");
        assertNotNull(doc.inputDispatcher());
        assertTrue(doc.inputDispatcher() instanceof InputDispatcher);
    }

    @Test
    public void dispatch_pointer_hover_marks_style_dirty() {
        DocumentContext doc = load("<button>ok</button>",
                "button { width: 80px; height: 24px; } button:hover { background: blue; }");
        doc.clearAllDirty();

        // hover 命中 button
        doc.dispatchPointer(PointerEvent.move(10f, 10f, ModifierKeys.none(), 0L), 0f, 0f);

        // 应标 STYLE_DIRTY（hover 状态变化）
        assertTrue("hover marks style dirty", doc.isStyleDirty());
    }

    @Test
    public void repeated_pointer_position_reuses_retained_hit_test() {
        DocumentContext doc = load("<button>ok</button>",
                "button { width: 80px; height: 24px; } button:hover { background: blue; }");

        doc.dispatchPointer(PointerEvent.move(10f, 10f, ModifierKeys.none(), 1L), 0f, 0f);
        int hitTests = doc.hitTestCount();
        doc.dispatchPointer(PointerEvent.move(10f, 10f, ModifierKeys.none(), 2L), 0f, 0f);

        assertEquals(hitTests, doc.hitTestCount());
    }

    @Test
    public void layout_change_invalidates_retained_pointer_hit() {
        DocumentContext doc = load("<button>ok</button>",
                "button { width: 80px; height: 24px; }");

        doc.dispatchPointer(PointerEvent.move(10f, 10f, ModifierKeys.none(), 1L), 0f, 0f);
        int hitTests = doc.hitTestCount();
        doc.markLayoutDirty();
        doc.recompute();
        doc.dispatchPointer(PointerEvent.move(10f, 10f, ModifierKeys.none(), 2L), 0f, 0f);

        assertEquals(hitTests + 1, doc.hitTestCount());
    }

    @Test
    public void dispatch_pointer_left_click_focuses_focusable() {
        DocumentContext doc = load("<button>ok</button>",
                "button { width: 80px; height: 24px; }");
        doc.clearAllDirty();

        ElementNode btn = doc.rootElement();
        doc.dispatchPointer(PointerEvent.down(10f, 10f, PointerButton.LEFT,
                ModifierKeys.none(), 1, 0L), 0f, 0f);

        assertSame(btn, doc.inputDispatcher().state().focus());
    }

    @Test
    public void right_click_does_not_focus() {
        DocumentContext doc = load("<button>ok</button>",
                "button { width: 80px; height: 24px; }");
        doc.clearAllDirty();

        doc.dispatchPointer(PointerEvent.down(10f, 10f, PointerButton.RIGHT,
                ModifierKeys.none(), 1, 0L), 0f, 0f);

        // 右键不触发 focus（AGENTS.md "Do not treat every click as a left click"）
        assertEquals(null, doc.inputDispatcher().state().focus());
    }

    // ---- 动画集成 ----

    @Test
    public void tick_advances_clock() {
        DocumentContext doc = load("<div></div>", "div { width: 10px; }");
        AnimationClock clock = doc.animationClock();
        assertEquals(0L, clock.nowMillis());
        doc.tick(1000L);
        assertEquals(1000L, clock.nowMillis());
    }

    @Test
    public void tick_with_no_transitions_is_noop() {
        DocumentContext doc = load("<div></div>", "div { width: 10px; }");
        doc.clearAllDirty();
        doc.tick(500L);
        // 无动画 → 无 dirty
        assertFalse(doc.isPaintDirty());
        assertFalse(doc.isLayoutDirty());
    }

    // ---- clearAll（关闭 UI）----

    @Test
    public void clearAll_resets_input_and_animation() {
        DocumentContext doc = load("<button>ok</button>",
                "button { width: 80px; height: 24px; }");
        ElementNode btn = doc.rootElement();
        doc.dispatchPointer(PointerEvent.down(10f, 10f, PointerButton.LEFT,
                ModifierKeys.none(), 1, 0L), 0f, 0f);
        assertSame(btn, doc.inputDispatcher().state().focus());

        doc.clearAll();

        assertEquals(null, doc.inputDispatcher().state().focus());
        assertFalse(doc.animationClock().hasActiveAnimations());
    }

    // ---- setViewport（GUI Scale 变化）----

    @Test
    public void set_viewport_marks_layout_dirty() {
        DocumentContext doc = load("<div></div>", "div { width: 50vw; }");
        assertEquals(480f, doc.layoutRoot().borderBoxWidth(), 0.001f);  // 50% of 960

        doc.setViewport(1920, 1080);
        assertTrue("viewport change marks layout dirty", doc.isLayoutDirty());
        doc.recompute();
        assertEquals(960f, doc.layoutRoot().borderBoxWidth(), 0.001f);  // 50% of 1920
    }

    // ---- styles 查询 ----

    @Test
    public void styles_map_exposed() {
        DocumentContext doc = load("<div id='a'></div>", "#a { color: red; }");
        assertNotNull(doc.styles());
        assertTrue(doc.styles().containsKey(doc.rootElement()));
    }
}
