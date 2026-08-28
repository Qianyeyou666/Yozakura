package gq.yozakura.ui.engine.layout;

import gq.yozakura.ui.engine.css.ComputedStyle;
import gq.yozakura.ui.engine.css.CssParser;
import gq.yozakura.ui.engine.css.StyleResolver;
import gq.yozakura.ui.engine.css.Stylesheet;
import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 2 切片 7：absolute 定位 + overflow + z-index 契约测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>position: absolute — 脱离文档流，相对 positioned 祖先定位</li>
 *   <li>top/left/right/bottom 偏移解析</li>
 *   <li>absolute 不影响后续 sibling 的堆叠位置</li>
 *   <li>position: relative — 保留在文档流，按 top/left 偏移</li>
 *   <li>overflow: hidden — 显式高度时不扩张到 children_sum</li>
 *   <li>overflow: visible — 显式高度时取 max(declared, children_sum)</li>
 *   <li>z-index 保留在 LayoutBox 中供 paint 使用</li>
 * </ul>
 */
public class AbsoluteLayoutTest {

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

    private static LayoutBox layout(ElementNode root, String css) {
        Stylesheet ss = new CssParser().parse(css);
        Map<ElementNode, ComputedStyle> styles = new StyleResolver().resolve(ss, root);
        MeasureContext ctx = new TestMeasureContext(VIEWPORT_W, VIEWPORT_H, ROOT_FONT);
        return new LayoutEngine().layout(root, styles, ctx);
    }

    // ---- position: absolute ----

    @Test
    public void absoluteElementPositionedByTopLeft() {
        // positioned 父容器 + absolute 子元素（top/left 相对父 content 原点）
        ElementNode root = el("div", null, "parent");
        ElementNode abs = el("div", null, "abs");
        root.appendChild(abs);
        LayoutBox box = layout(root,
                ".parent { position: relative; width: 300px; height: 200px; }" +
                ".abs { position: absolute; top: 30px; left: 40px; width: 50px; height: 60px; }");

        // absolute 子相对父 content 原点 (0,0) + (left, top) = (40, 30)
        LayoutBox absBox = box.child(0);
        assertEquals(40f, absBox.borderBoxX(), 0.0001f);
        assertEquals(30f, absBox.borderBoxY(), 0.0001f);
        assertEquals(50f, absBox.borderBoxWidth(), 0.0001f);
        assertEquals(60f, absBox.borderBoxHeight(), 0.0001f);
    }

    @Test
    public void positionedParentUsesPaddingBoxAsAbsoluteContainingBlock() {
        ElementNode root = el("div", null, "parent");
        ElementNode accent = el("span", null, "accent");
        root.appendChild(accent);
        LayoutBox box = layout(root,
                ".parent { position: relative; width: 200px; height: 56px; " +
                        "padding: 0 16px; border: 1px solid #fff; }" +
                ".accent { position: absolute; left: 0; top: 12px; width: 3px; height: 32px; }");

        LayoutBox accentBox = box.child(0);
        assertEquals(-16f, accentBox.borderBoxX(), 0.0001f);
        assertEquals(12f, accentBox.borderBoxY(), 0.0001f);
        assertEquals(3f, accentBox.borderBoxWidth(), 0.0001f);
        assertEquals(32f, accentBox.borderBoxHeight(), 0.0001f);
    }

    @Test
    public void absoluteElementDoesNotAffectSiblingFlow() {
        // absolute 脱离文档流：后续 sibling 位置不受影响（紧接前一个 in-flow sibling）
        ElementNode root = el("div", null, "parent");
        ElementNode a = el("div", null, "a");
        ElementNode abs = el("div", null, "abs");
        ElementNode b = el("div", null, "b");
        root.appendChild(a);
        root.appendChild(abs);
        root.appendChild(b);
        LayoutBox box = layout(root,
                ".parent { position: relative; width: 300px; height: 200px; }" +
                "div { width: 100px; height: 50px; }" +
                ".abs { position: absolute; top: 0; left: 0; }");

        // 子元素顺序保留：a, abs, b
        assertEquals(3, box.childCount());
        // a 在 0
        assertEquals(0f, box.child(0).borderBoxY(), 0.0001f);
        // b 紧接 a（abs 不占空间）→ b 在 50
        assertEquals(50f, box.child(2).borderBoxY(), 0.0001f);
    }

    @Test
    public void absoluteElementWithRightAndWidth() {
        // right + width → X = cbWidth - width - right
        ElementNode root = el("div", null, "parent");
        ElementNode abs = el("div", null, "abs");
        root.appendChild(abs);
        LayoutBox box = layout(root,
                ".parent { position: relative; width: 300px; height: 200px; }" +
                ".abs { position: absolute; right: 20px; width: 50px; height: 40px; }");

        // cbWidth = 300（父 content），X = 300 - 50 - 20 = 230
        LayoutBox absBox = box.child(0);
        assertEquals(230f, absBox.borderBoxX(), 0.0001f);
        assertEquals(50f, absBox.borderBoxWidth(), 0.0001f);
    }

    @Test
    public void absoluteElementWithBottomAndHeight() {
        // bottom + height → Y = cbHeight - height - bottom
        ElementNode root = el("div", null, "parent");
        ElementNode abs = el("div", null, "abs");
        root.appendChild(abs);
        LayoutBox box = layout(root,
                ".parent { position: relative; width: 300px; height: 200px; }" +
                ".abs { position: absolute; bottom: 30px; height: 40px; width: 50px; }");

        // cbHeight = 200，Y = 200 - 40 - 30 = 130
        LayoutBox absBox = box.child(0);
        assertEquals(130f, absBox.borderBoxY(), 0.0001f);
        assertEquals(40f, absBox.borderBoxHeight(), 0.0001f);
    }

    @Test
    public void absoluteElementWithLeftAndRightFillsWidth() {
        // left + right + auto width → width = cbWidth - left - right
        ElementNode root = el("div", null, "parent");
        ElementNode abs = el("div", null, "abs");
        root.appendChild(abs);
        LayoutBox box = layout(root,
                ".parent { position: relative; width: 300px; height: 200px; }" +
                ".abs { position: absolute; left: 10px; right: 20px; height: 40px; }");

        // width = 300 - 10 - 20 = 270；X = 10
        LayoutBox absBox = box.child(0);
        assertEquals(10f, absBox.borderBoxX(), 0.0001f);
        assertEquals(270f, absBox.borderBoxWidth(), 0.0001f);
    }

    @Test
    public void absoluteElementRelativeToViewportWithoutPositionedAncestor() {
        // 无 positioned 祖先 → 相对视口定位
        ElementNode root = el("div", null, "root");
        ElementNode abs = el("div", null, "abs");
        root.appendChild(abs);
        LayoutBox box = layout(root,
                ".root { width: 200px; height: 100px; margin: 50px; }" +
                ".abs { position: absolute; top: 10px; left: 20px; width: 30px; height: 40px; }");

        // root margin=50 → root content 绝对原点 (50,50)
        // absolute 相对视口 (0,0)：X_viewport=20, Y_viewport=10
        // 转为相对 DOM 父 root content 原点：X = 20 - 50 = -30, Y = 10 - 50 = -40
        LayoutBox absBox = box.child(0);
        assertEquals(-30f, absBox.borderBoxX(), 0.0001f);
        assertEquals(-40f, absBox.borderBoxY(), 0.0001f);
    }

    // ---- position: relative ----

    @Test
    public void relativeElementOffsetByTopLeft() {
        // relative 保留在文档流，但 borderBoxX/Y 偏移 top/left
        ElementNode root = el("div", null, "parent");
        ElementNode a = el("div", null, "a");
        ElementNode rel = el("div", null, "rel");
        ElementNode b = el("div", null, "b");
        root.appendChild(a);
        root.appendChild(rel);
        root.appendChild(b);
        LayoutBox box = layout(root,
                ".parent { width: 200px; height: 300px; }" +
                "div { width: 100px; height: 50px; }" +
                ".rel { position: relative; top: 15px; left: 25px; }");

        // a 在 0
        assertEquals(0f, box.child(0).borderBoxY(), 0.0001f);
        // rel 静态位置 Y=50，+top 15 → 65；静态 X=0，+left 25 → 25
        assertEquals(25f, box.child(1).borderBoxX(), 0.0001f);
        assertEquals(65f, box.child(1).borderBoxY(), 0.0001f);
        // b 静态位置 Y=100（rel 仍占 50px 空间，offset 不影响 sibling）
        assertEquals(100f, box.child(2).borderBoxY(), 0.0001f);
    }

    // ---- overflow ----

    @Test
    public void overflowHiddenClampsExplicitHeightToDeclared() {
        // overflow:hidden + 显式高度 + 子元素溢出 → 高度保持 declared，不扩张
        ElementNode root = el("div", null, "parent");
        ElementNode c1 = el("div", null, "c1");
        ElementNode c2 = el("div", null, "c2");
        root.appendChild(c1);
        root.appendChild(c2);
        LayoutBox box = layout(root,
                ".parent { width: 200px; height: 100px; overflow: hidden; }" +
                "div { width: 50px; height: 80px; }");

        // 子元素总高 160 > 100；overflow:hidden → 高度保持 100（不取 max）
        assertEquals(100f, box.contentHeight(), 0.0001f);
    }

    @Test
    public void overflowVisibleGrowsToMaxOfDeclaredAndChildren() {
        // overflow:visible（默认）+ 显式高度 + 子元素溢出 → 取 max(declared, children_sum)
        ElementNode root = el("div", null, "parent");
        ElementNode c1 = el("div", null, "c1");
        ElementNode c2 = el("div", null, "c2");
        root.appendChild(c1);
        root.appendChild(c2);
        LayoutBox box = layout(root,
                ".parent { width: 200px; height: 100px; overflow: visible; }" +
                "div { width: 50px; height: 80px; }");

        // 子元素总高 160 > 100；overflow:visible → 取 max(100, 160) = 160
        assertEquals(160f, box.contentHeight(), 0.0001f);
    }

    @Test
    public void overflowPropertyRecordedOnLayoutBox() {
        ElementNode root = el("div", null, "hidden");
        LayoutBox box = layout(root,
                ".hidden { width: 100px; height: 50px; overflow: hidden; }");
        assertEquals(Overflow.HIDDEN, box.overflow());

        ElementNode root2 = el("div", null, "vis");
        LayoutBox box2 = layout(root2,
                ".vis { width: 100px; height: 50px; }");
        assertEquals(Overflow.VISIBLE, box2.overflow());
    }

    // ---- z-index 保留 ----

    @Test
    public void zIndexPreservedOnLayoutBox() {
        ElementNode root = el("div", null, "parent");
        ElementNode a = el("div", null, "a");
        ElementNode b = el("div", null, "b");
        root.appendChild(a);
        root.appendChild(b);
        LayoutBox box = layout(root,
                ".parent { width: 200px; height: 100px; }" +
                ".a { width: 50px; height: 30px; z-index: 5; position: relative; }" +
                ".b { width: 50px; height: 30px; z-index: -1; position: relative; }");

        assertEquals(5, box.child(0).zIndex());
        assertEquals(-1, box.child(1).zIndex());
    }

    @Test
    public void zIndexAutoDefaultsToZero() {
        ElementNode root = el("div", null, "parent");
        ElementNode a = el("div", null, "a");
        root.appendChild(a);
        LayoutBox box = layout(root,
                ".parent { width: 200px; height: 100px; }" +
                ".a { width: 50px; height: 30px; }");

        // 无 z-index → 默认 0
        assertEquals(0, box.child(0).zIndex());
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
