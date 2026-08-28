package gq.yozakura.ui.engine.input;

import gq.yozakura.ui.engine.css.ComputedStyle;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.layout.BorderEdges;
import gq.yozakura.ui.engine.layout.LayoutBox;
import gq.yozakura.ui.engine.layout.MarginEdges;
import gq.yozakura.ui.engine.layout.Overflow;
import gq.yozakura.ui.engine.layout.PaddingEdges;
import gq.yozakura.ui.engine.layout.Position;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * 阶段 4 切片 4.3：HitTester 契约测试。
 *
 * <p>验证契约（AGENTS.md "Input and Coordinates"）：
 * <ul>
 *   <li>渲染/布局/命中测试共享一个逻辑坐标空间</li>
 *   <li>命中区域 = border-box（含 border）</li>
 *   <li>子元素先于父元素命中（点在子上返回子）</li>
 *   <li>z-index 高的兄弟优先（即使 DOM 顺序在前）</li>
 *   <li>z-index 相同时 DOM 顺序靠后者优先（later sibling paints on top）</li>
 *   <li>pointer-events: none 跳过自身，但子树仍可命中</li>
 *   <li>坐标累加：父 content origin + 子 border-box 偏移 = 绝对坐标</li>
 *   <li>命中边界：左上角命中、右下角外不命中</li>
 *   <li>display:none 元素不入 LayoutBox 树（无需特殊处理）</li>
 * </ul>
 *
 * <p>坐标契约：根 border-box 相对视口 (0,0)；子 border-box 相对父 content origin。
 */
public class HitTesterTest {

    private static ElementNode el(String tag) {
        return ElementNode.create(tag);
    }

    private static ComputedStyle.Builder style() {
        return ComputedStyle.builder();
    }

    private static LayoutBox box(ElementNode element,
                                 float x, float y, float w, float h) {
        return new LayoutBox(element, x, y, w, h,
                MarginEdges.zero(), new BorderEdges(0, 0, 0, 0),
                PaddingEdges.parseShorthand("0"),
                0, Position.STATIC, Overflow.VISIBLE,
                Collections.<LayoutBox>emptyList());
    }

    private static LayoutBox boxWithChildren(ElementNode element,
                                             float x, float y, float w, float h,
                                             LayoutBox... children) {
        List<LayoutBox> kids = new ArrayList<LayoutBox>();
        Collections.addAll(kids, children);
        return new LayoutBox(element, x, y, w, h,
                MarginEdges.zero(), new BorderEdges(0, 0, 0, 0),
                PaddingEdges.parseShorthand("0"),
                0, Position.STATIC, Overflow.VISIBLE, kids);
    }

    private static LayoutBox boxWithZ(ElementNode element,
                                       float x, float y, float w, float h, int z) {
        return new LayoutBox(element, x, y, w, h,
                MarginEdges.zero(), new BorderEdges(0, 0, 0, 0),
                PaddingEdges.parseShorthand("0"),
                z, Position.STATIC, Overflow.VISIBLE,
                Collections.<LayoutBox>emptyList());
    }

    private static Map<ElementNode, ComputedStyle> styles(Object... kv) {
        Map<ElementNode, ComputedStyle> m = new HashMap<ElementNode, ComputedStyle>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((ElementNode) kv[i], (ComputedStyle) kv[i + 1]);
        }
        return m;
    }

    // ---- 单元素 ----

    @Test
    public void hitInsideBorderBoxReturnsElement() {
        ElementNode e = el("div");
        LayoutBox root = box(e, 0, 0, 100, 100);
        LayoutBox hit = HitTester.hit(root, styles(e, style().build()), 50, 50);
        assertSame(e, hit.element());
    }

    @Test
    public void hitOutsideBorderBoxReturnsNull() {
        ElementNode e = el("div");
        LayoutBox root = box(e, 0, 0, 100, 100);
        assertNull(HitTester.hit(root, styles(e, style().build()), 150, 50));
        assertNull(HitTester.hit(root, styles(e, style().build()), 50, 150));
    }

    @Test
    public void topLeftCornerIsInclusive() {
        // 左上角 (0,0) 命中
        ElementNode e = el("div");
        LayoutBox root = box(e, 0, 0, 100, 100);
        assertSame(e, HitTester.hit(root, styles(e, style().build()), 0, 0).element());
    }

    @Test
    public void bottomRightCornerIsExclusive() {
        // 右下角 (100,100) 不命中（半开区间 [x, x+w)）
        ElementNode e = el("div");
        LayoutBox root = box(e, 0, 0, 100, 100);
        assertNull(HitTester.hit(root, styles(e, style().build()), 100, 100));
        assertNull(HitTester.hit(root, styles(e, style().build()), 99.99f, 100));
    }

    @Test
    public void offsetRootIsHitAtItsOrigin() {
        // 根偏移 (10,20)，命中点需在 (10,20)-(110,120) 内
        ElementNode e = el("div");
        LayoutBox root = box(e, 10, 20, 100, 100);
        assertSame(e, HitTester.hit(root, styles(e, style().build()), 10, 20).element());
        assertSame(e, HitTester.hit(root, styles(e, style().build()), 50, 50).element());
        assertNull(HitTester.hit(root, styles(e, style().build()), 9, 20));
    }

    // ---- 父子嵌套 ----

    @Test
    public void childIsHitOverParent() {
        // parent (0,0,100,100), child (10,10,50,50) → 点 (20,20) 命中 child
        ElementNode parent = el("div");
        ElementNode child = el("span");
        LayoutBox childBox = box(child, 10, 10, 50, 50);
        LayoutBox parentBox = boxWithChildren(parent, 0, 0, 100, 100, childBox);
        LayoutBox hit = HitTester.hit(parentBox,
                styles(parent, style().build(), child, style().build()), 20, 20);
        assertSame(child, hit.element());
    }

    @Test
    public void parentIsHitWhenPointNotOverChild() {
        // 点 (60,60) 在 parent 内但 child 外 → 命中 parent
        ElementNode parent = el("div");
        ElementNode child = el("span");
        LayoutBox childBox = box(child, 10, 10, 50, 50);
        LayoutBox parentBox = boxWithChildren(parent, 0, 0, 100, 100, childBox);
        LayoutBox hit = HitTester.hit(parentBox,
                styles(parent, style().build(), child, style().build()), 60, 60);
        assertSame(parent, hit.element());
    }

    @Test
    public void coordinateAccumulationThroughBorderAndPadding() {
        // parent (10,10,100,100), border=2, padding=8
        // parent content origin = (10+2+8, 10+2+8) = (20,20)
        // child (5,5,30,30) 相对父 content origin → 绝对 (25,25)-(55,55)
        ElementNode parent = el("div");
        ElementNode child = el("span");
        LayoutBox childBox = box(child, 5, 5, 30, 30);
        LayoutBox parentBox = new LayoutBox(parent, 10, 10, 100, 100,
                MarginEdges.zero(), new BorderEdges(2, 2, 2, 2),
                PaddingEdges.parseShorthand("8px"),
                0, Position.STATIC, Overflow.VISIBLE,
                Collections.singletonList(childBox));
        // 点 (30,30) 应在子内
        LayoutBox hit = HitTester.hit(parentBox,
                styles(parent, style().build(), child, style().build()), 30, 30);
        assertSame(child, hit.element());
        // 点 (22,22) 在父 content 但子在 (25,25) 外 → 命中 parent
        LayoutBox hitParent = HitTester.hit(parentBox,
                styles(parent, style().build(), child, style().build()), 22, 22);
        assertSame(parent, hitParent.element());
    }

    // ---- z-index 排序 ----

    @Test
    public void higherZIndexSiblingWinsEvenIfEarlierInDom() {
        // sibling A (z=1) at (0,0,100,100), sibling B (z=10) at (0,0,100,100)
        // 点 (50,50) 命中 B（高 z-index 优先）
        ElementNode a = el("div");
        ElementNode b = el("div");
        LayoutBox aBox = boxWithZ(a, 0, 0, 100, 100, 1);
        LayoutBox bBox = boxWithZ(b, 0, 0, 100, 100, 10);
        LayoutBox parent = boxWithChildren(el("container"), 0, 0, 100, 100, aBox, bBox);
        LayoutBox hit = HitTester.hit(parent,
                styles(a, style().build(), b, style().build()), 50, 50);
        assertSame(b, hit.element());
    }

    @Test
    public void sameZIndexLaterSiblingWins() {
        // A 与 B 同 z-index，B 在 DOM 中靠后 → 命中 B
        ElementNode a = el("div");
        ElementNode b = el("div");
        LayoutBox aBox = boxWithZ(a, 0, 0, 100, 100, 0);
        LayoutBox bBox = boxWithZ(b, 0, 0, 100, 100, 0);
        LayoutBox parent = boxWithChildren(el("container"), 0, 0, 100, 100, aBox, bBox);
        LayoutBox hit = HitTester.hit(parent,
                styles(a, style().build(), b, style().build()), 50, 50);
        assertSame(b, hit.element());
    }

    @Test
    public void negativeZIndexLosesToZero() {
        ElementNode a = el("div");
        ElementNode b = el("div");
        LayoutBox aBox = boxWithZ(a, 0, 0, 100, 100, -1);
        LayoutBox bBox = boxWithZ(b, 0, 0, 100, 100, 0);
        LayoutBox parent = boxWithChildren(el("container"), 0, 0, 100, 100, aBox, bBox);
        LayoutBox hit = HitTester.hit(parent,
                styles(a, style().build(), b, style().build()), 50, 50);
        assertSame(b, hit.element());
    }

    // ---- pointer-events: none ----

    @Test
    public void pointerEventsNoneSkipsElementButChildrenStillHit() {
        // parent pointer-events:none, child pointer-events:auto（默认）
        // 点在 child 上 → 命中 child（parent 被跳过不影响 child）
        ElementNode parent = el("div");
        ElementNode child = el("span");
        LayoutBox childBox = box(child, 10, 10, 50, 50);
        LayoutBox parentBox = boxWithChildren(parent, 0, 0, 100, 100, childBox);
        ComputedStyle parentStyle = style().set("pointer-events", "none").build();
        ComputedStyle childStyle = style().build();  // 默认 auto
        LayoutBox hit = HitTester.hit(parentBox,
                styles(parent, parentStyle, child, childStyle), 20, 20);
        assertSame(child, hit.element());
    }

    @Test
    public void pointerEventsNoneOnParentAndChildSkipsBoth() {
        // parent 与 child 都 pointer-events:none → 无命中
        ElementNode parent = el("div");
        ElementNode child = el("span");
        LayoutBox childBox = box(child, 10, 10, 50, 50);
        LayoutBox parentBox = boxWithChildren(parent, 0, 0, 100, 100, childBox);
        ComputedStyle parentStyle = style().set("pointer-events", "none").build();
        ComputedStyle childStyle = style().set("pointer-events", "none").build();
        LayoutBox hit = HitTester.hit(parentBox,
                styles(parent, parentStyle, child, childStyle), 20, 20);
        assertNull(hit);
    }

    @Test
    public void pointerEventsNoneChildFallsBackToParent() {
        // child pointer-events:none，parent auto
        // 点在 child 上但 child 被跳过 → 命中 parent（如果点也在 parent 内）
        ElementNode parent = el("div");
        ElementNode child = el("span");
        LayoutBox childBox = box(child, 10, 10, 50, 50);
        LayoutBox parentBox = boxWithChildren(parent, 0, 0, 100, 100, childBox);
        ComputedStyle parentStyle = style().build();
        ComputedStyle childStyle = style().set("pointer-events", "none").build();
        LayoutBox hit = HitTester.hit(parentBox,
                styles(parent, parentStyle, child, childStyle), 20, 20);
        assertSame(parent, hit.element());
    }

    @Test
    public void missingComputedStyleTreatedAsHitTestable() {
        // 元素不在 styles map 中 → 视为默认（可命中），不崩溃
        ElementNode e = el("div");
        LayoutBox root = box(e, 0, 0, 100, 100);
        LayoutBox hit = HitTester.hit(root, new HashMap<ElementNode, ComputedStyle>(), 50, 50);
        assertSame(e, hit.element());
    }

    // ---- 深度 ----

    @Test
    public void deepNestingHitsDeepestMatch() {
        // a > b > c，三层各 (0,0,30,30)（嵌套，content origin 与父 border-box 重合因无 border/padding）
        ElementNode a = el("div");
        ElementNode b = el("div");
        ElementNode c = el("div");
        LayoutBox cBox = box(c, 0, 0, 10, 10);
        LayoutBox bBox = boxWithChildren(b, 0, 0, 20, 20, cBox);
        LayoutBox aBox = boxWithChildren(a, 0, 0, 30, 30, bBox);
        LayoutBox hit = HitTester.hit(aBox,
                styles(a, style().build(), b, style().build(), c, style().build()), 5, 5);
        assertSame(c, hit.element());
    }

    // ---- 非法参数 ----

    @Test(expected = IllegalArgumentException.class)
    public void nullRootThrows() {
        HitTester.hit(null, new HashMap<ElementNode, ComputedStyle>(), 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullStylesThrows() {
        HitTester.hit(box(el("div"), 0, 0, 10, 10), null, 0, 0);
    }
}
