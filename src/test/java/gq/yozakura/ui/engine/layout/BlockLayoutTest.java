package gq.yozakura.ui.engine.layout;

import gq.yozakura.ui.engine.css.ComputedStyle;
import gq.yozakura.ui.engine.css.CssParser;
import gq.yozakura.ui.engine.css.StyleResolver;
import gq.yozakura.ui.engine.css.Stylesheet;
import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 2 切片 3：LayoutEngine + BlockLayout 契约测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>LayoutBox 数据结构：content/border/padding/margin 矩形访问</li>
 *   <li>BlockLayout 竖向堆叠：子元素按文档顺序垂直排列</li>
 *   <li>完整 box model：margin 折叠、padding/border 包裹 content</li>
 *   <li>width/height 解析（px、百分比基于父内容区）</li>
 *   <li>auto width：块级元素默认占满父内容区</li>
 *   <li>auto height：根据子元素堆叠总高推导</li>
 *   <li>display:none 不产生 LayoutBox</li>
 *   <li>百分比相对父内容区宽解析</li>
 * </ul>
 */
public class BlockLayoutTest {

    private static final int VIEWPORT_W = 960;
    private static final int VIEWPORT_H = 640;
    private static final float ROOT_FONT = 14f;

    private static ElementNode el(String tag) {
        return ElementNode.create(tag);
    }

    private static ElementNode el(String tag, String id, String... classes) {
        ElementNode e = ElementNode.create(tag);
        if (id != null) e.withId(id);
        if (classes.length > 0) e.withClasses(java.util.Arrays.asList(classes));
        return e;
    }

    private static Map<ElementNode, ComputedStyle> resolveStyles(String css, ElementNode root) {
        Stylesheet ss = new CssParser().parse(css);
        return new StyleResolver().resolve(ss, root);
    }

    private static LayoutBox layout(ElementNode root, Map<ElementNode, ComputedStyle> styles) {
        MeasureContext ctx = new TestMeasureContext(VIEWPORT_W, VIEWPORT_H, ROOT_FONT);
        return new LayoutEngine().layout(root, styles, ctx);
    }

    // ---- LayoutBox 数据结构 ----

    @Test
    public void layoutBoxStoresContentRect() {
        Stylesheet ss = new CssParser().parse("div { width: 100px; height: 50px; }");
        ElementNode root = el("div");
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        assertEquals(100f, box.contentWidth(), 0.0001f);
        assertEquals(50f, box.contentHeight(), 0.0001f);
        assertEquals(0f, box.contentX(), 0.0001f);
        assertEquals(0f, box.contentY(), 0.0001f);
    }

    @Test
    public void layoutBoxStoresBorderBoxRect() {
        // padding=10, border=2, content-box sizing → 边框盒 = content + 2*10 + 2*2
        Stylesheet ss = new CssParser().parse(
                "div { width: 100px; height: 50px; padding: 10px; border: 2px solid #000; }");
        ElementNode root = el("div");
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        assertEquals(100f, box.contentWidth(), 0.0001f);
        assertEquals(50f, box.contentHeight(), 0.0001f);
        // 边框盒 = 100 + 2*10 + 2*2 = 124
        assertEquals(124f, box.borderBoxWidth(), 0.0001f);
        assertEquals(74f, box.borderBoxHeight(), 0.0001f);
    }

    @Test
    public void layoutBoxPaddingOffsetFromBorder() {
        Stylesheet ss = new CssParser().parse(
                "div { width: 100px; height: 50px; padding: 10px; border: 2px solid #000; }");
        ElementNode root = el("div");
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        // borderBox 原点 = (0,0)；padding 起点 = border 起点 = (0,0)；content 起点 = (border+padding, border+padding)
        // margin=0, padding=10, border=2 → content (x,y) = (12, 12)
        assertEquals(12f, box.contentX(), 0.0001f);
        assertEquals(12f, box.contentY(), 0.0001f);
    }

    @Test
    public void layoutBoxWithMarginOffsetsPosition() {
        // margin=5 → content 相对父内容区原点偏移 (margin+border+padding)
        Stylesheet ss = new CssParser().parse(
                "div { width: 100px; height: 50px; margin: 5px; padding: 10px; border: 2px solid #000; }");
        ElementNode root = el("div");
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        // margin=5, border=2, padding=10 → content (x,y) = (5+2+10, 5+2+10) = (17, 17)
        assertEquals(17f, box.contentX(), 0.0001f);
        assertEquals(17f, box.contentY(), 0.0001f);
    }

    // ---- BlockLayout 竖向堆叠 ----

    @Test
    public void blockChildrenStackVertically() {
        // 三个 div 子元素，每个高度 20px，应垂直堆叠
        Stylesheet ss = new CssParser().parse(
                "div { width: 100px; height: 20px; }");
        ElementNode root = el("div", null, "root");
        ElementNode c1 = el("div");
        ElementNode c2 = el("div");
        ElementNode c3 = el("div");
        root.appendChild(c1);
        root.appendChild(c2);
        root.appendChild(c3);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        assertEquals(3, box.childCount());
        LayoutBox b1 = box.child(0);
        LayoutBox b2 = box.child(1);
        LayoutBox b3 = box.child(2);

        // 子元素相对父 content 原点 (0,0) 排列
        assertEquals(0f, b1.borderBoxY(), 0.0001f);
        assertEquals(20f, b2.borderBoxY(), 0.0001f);
        assertEquals(40f, b3.borderBoxY(), 0.0001f);
    }

    @Test
    public void blockChildrenWithMarginDontCollapseByDefault() {
        // 简化模型：margin 不折叠，相邻 margin 累加
        Stylesheet ss = new CssParser().parse(
                "div { width: 100px; height: 20px; margin: 5px; }");
        ElementNode root = el("div");
        ElementNode c1 = el("div");
        ElementNode c2 = el("div");
        root.appendChild(c1);
        root.appendChild(c2);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        // 第一个子元素 margin=5，borderBoxY = 5
        assertEquals(5f, box.child(0).borderBoxY(), 0.0001f);
        // 第二个子元素 = 5 + 20(borderBox 高) + 5(margin) + 5(margin) = 35
        // 简化模型：margin 不折叠，直接累加
        assertEquals(35f, box.child(1).borderBoxY(), 0.0001f);
    }

    // ---- width/height 解析 ----

    @Test
    public void blockAutoWidthFillsParentContentArea() {
        // 子 div 无 width，自动占满父内容区宽
        Stylesheet ss = new CssParser().parse(
                ".parent { width: 200px; height: 100px; }" +
                ".child { height: 20px; }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        LayoutBox childBox = box.child(0);
        // 子占满父内容区宽（父无 padding/border，内容区宽=200）
        assertEquals(200f, childBox.contentWidth(), 0.0001f);
    }

    @Test
    public void blockAutoWidthFillsParentContentAreaMinusPadding() {
        // 父 padding=10 → 内容区 = 200-20 = 180；子占满 180
        Stylesheet ss = new CssParser().parse(
                ".parent { width: 200px; height: 100px; padding: 10px; box-sizing: border-box; }" +
                ".child { height: 20px; }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        LayoutBox childBox = box.child(0);
        assertEquals(180f, childBox.contentWidth(), 0.0001f);
    }

    @Test
    public void blockAutoHeightIsSumOfChildren() {
        // 父无 height，自动 = 子元素堆叠总高
        Stylesheet ss = new CssParser().parse(
                "div { width: 100px; height: 30px; }");
        ElementNode root = el("div", null, "root");
        ElementNode c1 = el("div");
        ElementNode c2 = el("div");
        root.appendChild(c1);
        root.appendChild(c2);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        // 父 height:auto，等于 2 个子元素高度 = 60
        assertEquals(60f, box.contentHeight(), 0.0001f);
    }

    @Test
    public void blockWidthPercentResolvesAgainstParentContentWidth() {
        // 父内容区宽 200；子 width:50% → 100
        Stylesheet ss = new CssParser().parse(
                ".parent { width: 200px; height: 100px; }" +
                ".child { width: 50%; height: 20px; }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        assertEquals(100f, box.child(0).contentWidth(), 0.0001f);
    }

    @Test
    public void blockHeightPercentResolvesAgainstParentContentHeight() {
        // 父内容区高 100；子 height:25% → 25
        Stylesheet ss = new CssParser().parse(
                ".parent { width: 200px; height: 100px; }" +
                ".child { width: 50px; height: 25%; }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        assertEquals(25f, box.child(0).contentHeight(), 0.0001f);
    }

    @Test
    public void blockPercentHeightWithAutoParentResolvesToZero() {
        // 父 height:auto 时，子 height:% 无基准，降级为 0（简化处理；CSS 规范为 auto）
        Stylesheet ss = new CssParser().parse(
                ".parent { width: 200px; }" +
                ".child { width: 50px; height: 50%; }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        // 父 height 未指定 → 内容区高未定 → 子 height:% 解析为 0
        assertEquals(0f, box.child(0).contentHeight(), 0.0001f);
    }

    // ---- display:none ----

    @Test
    public void displayNoneProducesNoLayoutBox() {
        Stylesheet ss = new CssParser().parse(
                ".parent { width: 200px; height: 100px; }" +
                ".hidden { display: none; }" +
                ".visible { height: 20px; }");
        ElementNode root = el("div", null, "parent");
        ElementNode hidden = el("div", null, "hidden");
        ElementNode visible = el("div", null, "visible");
        root.appendChild(hidden);
        root.appendChild(visible);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        // .hidden 不产生 LayoutBox，只看到 .visible
        assertEquals(1, box.childCount());
        // .visible 在堆叠顶部（hidden 不占空间）
        assertEquals(0f, box.child(0).borderBoxY(), 0.0001f);
    }

    @Test
    public void displayNoneOnRootReturnsNull() {
        Stylesheet ss = new CssParser().parse("div { display: none; }");
        ElementNode root = el("div");
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        assertNull("display:none should produce no LayoutBox", box);
    }

    // ---- 嵌套 ----

    @Test
    public void nestedLayoutOffsetsAreRelative() {
        // borderBoxX/Y 相对父 content 原点（非视口绝对坐标）。
        // 父 padding=10 把父 content 原点移到绝对 (10,10)，但子 borderBox 仍相对该原点。
        Stylesheet ss = new CssParser().parse(
                ".parent { width: 200px; height: 100px; padding: 10px; box-sizing: border-box; }" +
                ".child { width: 100px; height: 50px; margin: 5px; }" +
                ".grandchild { width: 30px; height: 30px; margin: 3px; }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div", null, "child");
        ElementNode grandchild = el("div", null, "grandchild");
        root.appendChild(child);
        child.appendChild(grandchild);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        LayoutBox childBox = box.child(0);
        LayoutBox grandchildBox = childBox.child(0);

        // child borderBox 相对父 content 原点 = (margin.left, margin.top) = (5, 5)
        // 若为绝对坐标则会是 (10+5, 10+5) = (15, 15)
        assertEquals(5f, childBox.borderBoxX(), 0.0001f);
        assertEquals(5f, childBox.borderBoxY(), 0.0001f);
        // grandchild borderBox 相对 child content 原点 = (margin.left, margin.top) = (3, 3)
        // child 无 padding/border → content 原点 = borderBox 原点
        // 若为绝对坐标则会是 (15+3, 15+3) = (18, 18)
        assertEquals(3f, grandchildBox.borderBoxX(), 0.0001f);
        assertEquals(3f, grandchildBox.borderBoxY(), 0.0001f);
    }

    @Test
    public void allDescendantElementsGetLayoutBox() {
        Stylesheet ss = new CssParser().parse("div { width: 50px; height: 10px; }");
        ElementNode root = el("div");
        ElementNode c1 = el("div");
        ElementNode c2 = el("div");
        ElementNode gc = el("div");
        root.appendChild(c1);
        root.appendChild(c2);
        c2.appendChild(gc);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        assertEquals(2, box.childCount());
        assertEquals(0, box.child(0).childCount());
        assertEquals(1, box.child(1).childCount());
    }

    @Test
    public void geometryOnlyCopiesReuseImmutableChildOrderViews() {
        Stylesheet ss = new CssParser().parse(
                ".root { width: 100px; height: 100px; }"
                        + ".first { width: 20px; height: 20px; z-index: 2; }"
                        + ".second { width: 20px; height: 20px; z-index: 1; }");
        ElementNode root = el("div", null, "root");
        root.appendChild(el("div", null, "first"));
        root.appendChild(el("div", null, "second"));
        LayoutBox box = layout(root, new StyleResolver().resolve(ss, root));

        LayoutBox moved = box.repositioned(4.0F, 7.0F);
        LayoutBox resized = box.withBorderBoxSize(120.0F, 80.0F);

        assertSame(box.children(), moved.children());
        assertSame(box.paintChildren(), moved.paintChildren());
        assertSame(box.hitChildren(), moved.hitChildren());
        assertSame(box.children(), resized.children());
        assertSame(box.paintChildren(), resized.paintChildren());
        assertSame(box.hitChildren(), resized.hitChildren());
    }

    // ---- z-index 保留 ----

    @Test
    public void zIndexFromComputedStylePreservedInLayoutBox() {
        Stylesheet ss = new CssParser().parse(
                "div { width: 100px; height: 50px; }" +
                ".high { z-index: 10; }" +
                ".low { z-index: -1; }");
        ElementNode root = el("div", null, "root");
        ElementNode high = el("div", null, "high");
        ElementNode low = el("div", null, "low");
        root.appendChild(high);
        root.appendChild(low);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        // z-index 默认为 0（auto）
        assertEquals(0, box.zIndex());
        assertEquals(10, box.child(0).zIndex());
        assertEquals(-1, box.child(1).zIndex());
    }

    @Test
    public void positionStaticIsDefault() {
        Stylesheet ss = new CssParser().parse("div { width: 100px; height: 50px; }");
        ElementNode root = el("div");
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        assertEquals(Position.STATIC, box.position());
    }

    // ---- MeasureContext ----

    @Test
    public void measureContextProvidesViewportAndRootFont() {
        TestMeasureContext ctx = new TestMeasureContext(960, 640, 14f);
        assertEquals(960, ctx.viewportWidth());
        assertEquals(640, ctx.viewportHeight());
        assertEquals(14f, ctx.rootFontSizePx(), 0.0001f);
    }

    @Test
    public void blockWidthVwResolvesAgainstViewport() {
        // width: 50vw → viewportW=960 → 480
        Stylesheet ss = new CssParser().parse("div { width: 50vw; height: 50px; }");
        ElementNode root = el("div");
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        assertEquals(480f, box.contentWidth(), 0.0001f);
    }

    @Test
    public void blockWidthEmResolvesAgainstElementOwnFontSize() {
        // 元素 font-size: 20px, width: 5em → 100
        Stylesheet ss = new CssParser().parse(
                "div { font-size: 20px; width: 5em; height: 50px; }");
        ElementNode root = el("div");
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        assertEquals(100f, box.contentWidth(), 0.0001f);
    }

    @Test
    public void blockWidthEmInheritsParentFontSize() {
        // 父 font-size: 20px, 子 width: 5em → 100（em 基于自身 font-size，自身继承父）
        Stylesheet ss = new CssParser().parse(
                ".parent { font-size: 20px; width: 100px; height: 100px; }" +
                ".child { width: 5em; height: 50px; }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        assertEquals(100f, box.child(0).contentWidth(), 0.0001f);
    }

    @Test
    public void blockWidthRemResolvesAgainstRootFont() {
        // root font-size: 14px, width: 10rem → 140
        Stylesheet ss = new CssParser().parse(
                ":root { font-size: 14px; } div { width: 10rem; height: 50px; }");
        ElementNode root = el("ui");
        ElementNode div = el("div");
        root.appendChild(div);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        LayoutBox box = layout(root, styles);

        assertEquals(140f, box.child(0).contentWidth(), 0.0001f);
    }

    // ---- 测试用 MeasureContext ----

    private static final class TestMeasureContext implements MeasureContext {
        private final int viewportWidth;
        private final int viewportHeight;
        private final float rootFont;

        TestMeasureContext(int vw, int vh, float rootFont) {
            this.viewportWidth = vw;
            this.viewportHeight = vh;
            this.rootFont = rootFont;
        }

        @Override
        public int viewportWidth() { return viewportWidth; }

        @Override
        public int viewportHeight() { return viewportHeight; }

        @Override
        public float rootFontSizePx() { return rootFont; }
    }
}
