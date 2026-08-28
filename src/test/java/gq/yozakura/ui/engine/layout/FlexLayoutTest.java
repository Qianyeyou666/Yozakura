package gq.yozakura.ui.engine.layout;

import gq.yozakura.ui.engine.css.ComputedStyle;
import gq.yozakura.ui.engine.css.CssParser;
import gq.yozakura.ui.engine.css.StyleResolver;
import gq.yozakura.ui.engine.css.Stylesheet;
import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * 阶段 2 切片 4-5：FlexLayout 契约测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>flex-direction: row — 子元素水平排列</li>
 *   <li>justify-content: flex-start / center / flex-end / space-between / space-around / space-evenly</li>
 *   <li>gap — 主轴项间距</li>
 *   <li>flex-direction: column — 子元素垂直排列</li>
 *   <li>align-items: flex-start / center / flex-end / stretch（交叉轴）</li>
 *   <li>align-self — 单元素交叉轴覆盖</li>
 *   <li>flex 容器 auto height = 最大子元素高（row）/ auto 主轴（column）</li>
 *   <li>display:none 子元素不参与 flex 排布</li>
 * </ul>
 */
public class FlexLayoutTest {

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

    // ---- flex-direction: row ----

    @Test
    public void flexRowItemsStackHorizontally() {
        ElementNode root = el("div", null, "container");
        ElementNode c1 = el("div");
        ElementNode c2 = el("div");
        ElementNode c3 = el("div");
        root.appendChild(c1);
        root.appendChild(c2);
        root.appendChild(c3);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 300px; height: 50px; align-items: flex-start; }" +
                "div { width: 100px; height: 30px; }");

        assertEquals(3, box.childCount());
        // 三个 100px 宽子元素在 300px 容器中，flex-start，无 gap
        assertEquals(0f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(100f, box.child(1).borderBoxX(), 0.0001f);
        assertEquals(200f, box.child(2).borderBoxX(), 0.0001f);
        // 交叉轴 flex-start → borderBoxY = 0
        assertEquals(0f, box.child(0).borderBoxY(), 0.0001f);
    }

    @Test
    public void flexRowJustifyCenter() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; justify-content: center; width: 300px; height: 50px; align-items: flex-start; }" +
                "div { width: 100px; height: 30px; }");

        // 两个 100px 项，总 200，剩余 100，居中 → 起点偏移 50
        assertEquals(50f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(150f, box.child(1).borderBoxX(), 0.0001f);
    }

    @Test
    public void flexRowJustifyFlexEnd() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; justify-content: flex-end; width: 300px; height: 50px; align-items: flex-start; }" +
                "div { width: 100px; height: 30px; }");

        // 两个 100px 项，总 200，剩余 100 在前 → 起点 100, 200
        assertEquals(100f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(200f, box.child(1).borderBoxX(), 0.0001f);
    }

    @Test
    public void flexRowJustifySpaceBetween() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; justify-content: space-between; width: 300px; height: 50px; align-items: flex-start; }" +
                "div { width: 50px; height: 30px; }");

        // 三个 50px 项，总 150，剩余 150，2 个间隔 → 每间隔 75
        // 位置：0, 50+75=125, 125+50+75=250
        assertEquals(0f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(125f, box.child(1).borderBoxX(), 0.0001f);
        assertEquals(250f, box.child(2).borderBoxX(), 0.0001f);
    }

    @Test
    public void flexRowJustifySpaceAround() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; justify-content: space-around; width: 300px; height: 50px; align-items: flex-start; }" +
                "div { width: 50px; height: 30px; }");

        // 三个 50px 项，总 150，剩余 150，3 个单位 → 每单位 50
        // 两端各半个单位（25），项间一个单位（50）
        // 位置：25, 25+50+50=125, 125+50+50=225
        assertEquals(25f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(125f, box.child(1).borderBoxX(), 0.0001f);
        assertEquals(225f, box.child(2).borderBoxX(), 0.0001f);
    }

    @Test
    public void flexRowJustifySpaceEvenly() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; justify-content: space-evenly; width: 300px; height: 50px; align-items: flex-start; }" +
                "div { width: 50px; height: 30px; }");

        // 三个 50px 项，总 150，剩余 150，4 个单位（两端+2间隔）→ 每单位 37.5
        // 位置：37.5, 37.5+50+37.5=125, 125+50+37.5=212.5
        assertEquals(37.5f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(125f, box.child(1).borderBoxX(), 0.0001f);
        assertEquals(212.5f, box.child(2).borderBoxX(), 0.0001f);
    }

    // ---- gap ----

    @Test
    public void flexRowGapBetweenItems() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; gap: 10px; width: 320px; height: 50px; align-items: flex-start; }" +
                "div { width: 100px; height: 30px; }");

        // 三个 100px 项，gap=10，总 = 300 + 20 = 320，剩余 0
        // 位置：0, 110, 220
        assertEquals(0f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(110f, box.child(1).borderBoxX(), 0.0001f);
        assertEquals(220f, box.child(2).borderBoxX(), 0.0001f);
    }

    @Test
    public void flexRowGapWithCenterJustify() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; gap: 10px; justify-content: center; width: 320px; height: 50px; align-items: flex-start; }" +
                "div { width: 100px; height: 30px; }");

        // 两个 100px 项 + gap 10 = 210，剩余 110，居中 → 起点偏移 55
        assertEquals(55f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(165f, box.child(1).borderBoxX(), 0.0001f);
    }

    // ---- flex 容器 auto height ----

    @Test
    public void flexRowAutoHeightIsMaxChildHeight() {
        ElementNode root = el("div", null, "container");
        ElementNode c1 = el("div", null, "small");
        ElementNode c2 = el("div", null, "tall");
        root.appendChild(c1);
        root.appendChild(c2);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 300px; align-items: flex-start; }" +
                "div { width: 100px; height: 30px; }" +
                ".tall { height: 50px; }");

        // 容器 height:auto → 内容高 = max(30, 50) = 50
        assertEquals(50f, box.contentHeight(), 0.0001f);
    }

    // ---- flex-direction: column ----

    @Test
    public void flexColumnItemsStackVertically() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; flex-direction: column; width: 100px; height: 300px; align-items: flex-start; }" +
                "div { width: 50px; height: 100px; }");

        assertEquals(3, box.childCount());
        // 三个 100px 高子元素垂直堆叠
        assertEquals(0f, box.child(0).borderBoxY(), 0.0001f);
        assertEquals(100f, box.child(1).borderBoxY(), 0.0001f);
        assertEquals(200f, box.child(2).borderBoxY(), 0.0001f);
    }

    @Test
    public void flexColumnJustifyCenter() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; flex-direction: column; justify-content: center; width: 100px; height: 300px; align-items: flex-start; }" +
                "div { width: 50px; height: 100px; }");

        // 两个 100px 项，总 200，剩余 100，居中 → 起点偏移 50
        assertEquals(50f, box.child(0).borderBoxY(), 0.0001f);
        assertEquals(150f, box.child(1).borderBoxY(), 0.0001f);
    }

    @Test
    public void flexColumnJustifySpaceBetween() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; flex-direction: column; justify-content: space-between; width: 100px; height: 300px; align-items: flex-start; }" +
                "div { width: 50px; height: 50px; }");

        // 三个 50px 项，总 150，剩余 150，2 间隔 → 每间隔 75
        assertEquals(0f, box.child(0).borderBoxY(), 0.0001f);
        assertEquals(125f, box.child(1).borderBoxY(), 0.0001f);
        assertEquals(250f, box.child(2).borderBoxY(), 0.0001f);
    }

    @Test
    public void flexColumnGapBetweenItems() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; flex-direction: column; gap: 10px; width: 100px; height: 320px; align-items: flex-start; }" +
                "div { width: 50px; height: 100px; }");

        // 三个 100px 项 + gap 10*2 = 320，无剩余
        assertEquals(0f, box.child(0).borderBoxY(), 0.0001f);
        assertEquals(110f, box.child(1).borderBoxY(), 0.0001f);
        assertEquals(220f, box.child(2).borderBoxY(), 0.0001f);
    }

    // ---- align-items（交叉轴） ----

    @Test
    public void flexRowAlignItemsCenter() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; align-items: center; width: 300px; height: 50px; }" +
                "div { width: 100px; height: 30px; }");

        // 容器高 50，子元素高 30，居中 → borderBoxY = (50-30)/2 = 10
        assertEquals(10f, box.child(0).borderBoxY(), 0.0001f);
        assertEquals(10f, box.child(1).borderBoxY(), 0.0001f);
    }

    @Test
    public void flexRowAlignItemsFlexEnd() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; align-items: flex-end; width: 300px; height: 50px; }" +
                "div { width: 100px; height: 30px; }");

        // 容器高 50，子元素高 30，底部对齐 → borderBoxY = 50-30 = 20
        assertEquals(20f, box.child(0).borderBoxY(), 0.0001f);
        assertEquals(20f, box.child(1).borderBoxY(), 0.0001f);
    }

    @Test
    public void flexRowAlignItemsStretch() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; align-items: stretch; width: 300px; height: 50px; }" +
                "div { width: 100px; height: 30px; }");

        // stretch → 子元素 height 拉伸到容器高 50
        assertEquals(50f, box.child(0).borderBoxHeight(), 0.0001f);
        assertEquals(50f, box.child(1).borderBoxHeight(), 0.0001f);
    }

    @Test
    public void flexColumnAlignItemsCenter() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; flex-direction: column; align-items: center; width: 100px; height: 300px; }" +
                "div { width: 50px; height: 100px; }");

        // 容器宽 100，子元素宽 50，居中 → borderBoxX = (100-50)/2 = 25
        assertEquals(25f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(25f, box.child(1).borderBoxX(), 0.0001f);
    }

    @Test
    public void flexColumnAlignItemsFlexEnd() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; flex-direction: column; align-items: flex-end; width: 100px; height: 300px; }" +
                "div { width: 50px; height: 100px; }");

        // 容器宽 100，子元素宽 50，右对齐 → borderBoxX = 100-50 = 50
        assertEquals(50f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(50f, box.child(1).borderBoxX(), 0.0001f);
    }

    @Test
    public void flexColumnAlignItemsStretch() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; flex-direction: column; align-items: stretch; width: 100px; height: 300px; }" +
                "div { width: 50px; height: 100px; }");

        // stretch → 子元素 width 拉伸到容器宽 100
        assertEquals(100f, box.child(0).borderBoxWidth(), 0.0001f);
        assertEquals(100f, box.child(1).borderBoxWidth(), 0.0001f);
    }

    // ---- align-self ----

    @Test
    public void alignSelfOverridesAlignItems() {
        ElementNode root = el("div", null, "container");
        ElementNode c1 = el("div", null, "top");
        ElementNode c2 = el("div", null, "center");
        ElementNode c3 = el("div", null, "bottom");
        root.appendChild(c1);
        root.appendChild(c2);
        root.appendChild(c3);
        LayoutBox box = layout(root,
                ".container { display: flex; align-items: flex-start; width: 300px; height: 50px; }" +
                "div { width: 100px; height: 30px; }" +
                ".center { align-self: center; }" +
                ".bottom { align-self: flex-end; }");

        // c1: flex-start → borderBoxY = 0
        assertEquals(0f, box.child(0).borderBoxY(), 0.0001f);
        // c2: center → borderBoxY = (50-30)/2 = 10
        assertEquals(10f, box.child(1).borderBoxY(), 0.0001f);
        // c3: flex-end → borderBoxY = 50-30 = 20
        assertEquals(20f, box.child(2).borderBoxY(), 0.0001f);
    }

    // ---- display:none 子元素 ----

    @Test
    public void flexSkipsDisplayNoneChildren() {
        ElementNode root = el("div", null, "container");
        ElementNode c1 = el("div", null, "a");
        ElementNode c2 = el("div", null, "hidden");
        ElementNode c3 = el("div", null, "b");
        root.appendChild(c1);
        root.appendChild(c2);
        root.appendChild(c3);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 300px; height: 50px; align-items: flex-start; }" +
                "div { width: 100px; height: 30px; }" +
                ".hidden { display: none; }");

        // .hidden 不参与 flex 排布
        assertEquals(2, box.childCount());
        // c1 在 0，c3 在 100（hidden 不占空间）
        assertEquals(0f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(100f, box.child(1).borderBoxX(), 0.0001f);
    }

    @Test
    public void absoluteChildrenDoNotConsumeFlexSpace() {
        ElementNode root = el("div", null, "container");
        ElementNode accent = el("span", null, "accent");
        ElementNode first = el("div", null, "item");
        ElementNode second = el("div", null, "item");
        root.appendChild(accent);
        root.appendChild(first);
        root.appendChild(second);

        LayoutBox box = layout(root,
                ".container { display: flex; position: relative; width: 200px; height: 50px; align-items: flex-start; }" +
                ".accent { position: absolute; left: 10px; top: 5px; width: 3px; height: 20px; }" +
                ".item { width: 80px; height: 30px; flex-shrink: 0; }");

        assertEquals(3, box.childCount());
        assertEquals(10f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(5f, box.child(0).borderBoxY(), 0.0001f);
        assertEquals(0f, box.child(1).borderBoxX(), 0.0001f);
        assertEquals(80f, box.child(2).borderBoxX(), 0.0001f);
    }

    // ---- margin 在 flex 中的行为 ----

    @Test
    public void flexRowChildMarginOffsetsPosition() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; width: 300px; height: 50px; align-items: flex-start; }" +
                "div { width: 80px; height: 30px; margin: 5px; }");

        // 子元素 margin=5，外尺寸 = 5+80+5 = 90
        // 两个项总 180，剩余 120，flex-start
        // c1 borderBoxX = 0 + 5(margin) = 5
        // c2 borderBoxX = 90 + 5(margin) = 95
        assertEquals(5f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(95f, box.child(1).borderBoxX(), 0.0001f);
        // 交叉轴 margin.top = 5
        assertEquals(5f, box.child(0).borderBoxY(), 0.0001f);
    }

    @Test
    public void flexRowAutoHeightAccountsForChildMargin() {
        ElementNode root = el("div", null, "container");
        root.appendChild(el("div"));
        root.appendChild(el("div"));
        LayoutBox box = layout(root,
                ".container { display: flex; width: 300px; align-items: flex-start; }" +
                "div { width: 100px; height: 30px; margin: 5px; }");

        // 子元素外高 = 5+30+5 = 40，容器 auto height = 40
        assertEquals(40f, box.contentHeight(), 0.0001f);
    }

    // ---- flex-grow（切片 6） ----

    @Test
    public void flexGrowSingleItemFillsRemainingSpace() {
        ElementNode root = el("div", null, "container");
        ElementNode fixed = el("div", null, "fixed");
        ElementNode grow = el("div", null, "grow");
        root.appendChild(fixed);
        root.appendChild(grow);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 300px; height: 30px; align-items: flex-start; }" +
                ".fixed { width: 100px; height: 30px; }" +
                ".grow { flex-grow: 1; height: 30px; }");

        // fixed 占 100，剩余 200 全部分给 grow（无显式 width → base=0 + grow=1）
        assertEquals(100f, box.child(0).borderBoxWidth(), 0.0001f);
        assertEquals(200f, box.child(1).borderBoxWidth(), 0.0001f);
        // 位置：fixed 在 0，grow 在 100
        assertEquals(0f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(100f, box.child(1).borderBoxX(), 0.0001f);
    }

    @Test
    public void flexGrowMultipleItemsDistributeProportionally() {
        ElementNode root = el("div", null, "container");
        ElementNode a = el("div", null, "a");
        ElementNode b = el("div", null, "b");
        ElementNode c = el("div", null, "c");
        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 300px; height: 30px; align-items: flex-start; }" +
                "div { height: 30px; }" +
                ".a { flex-grow: 1; }" +
                ".b { flex-grow: 2; }" +
                ".c { flex-grow: 1; }");

        // 三个 item base=0，剩余 300 按 1:2:1 分配 → 75, 150, 75
        assertEquals(75f, box.child(0).borderBoxWidth(), 0.0001f);
        assertEquals(150f, box.child(1).borderBoxWidth(), 0.0001f);
        assertEquals(75f, box.child(2).borderBoxWidth(), 0.0001f);
    }

    @Test
    public void flexGrowUsesExplicitWidthAsBase() {
        ElementNode root = el("div", null, "container");
        ElementNode a = el("div", null, "a");
        ElementNode b = el("div", null, "b");
        root.appendChild(a);
        root.appendChild(b);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 300px; height: 30px; align-items: flex-start; }" +
                ".a { width: 100px; height: 30px; flex-grow: 1; }" +
                ".b { width: 100px; height: 30px; flex-grow: 1; }");

        // 两项 base 各 100，总 200，剩余 100，grow 1:1 → 各加 50
        // 最终：a=150, b=150
        assertEquals(150f, box.child(0).borderBoxWidth(), 0.0001f);
        assertEquals(150f, box.child(1).borderBoxWidth(), 0.0001f);
    }

    @Test
    public void flexGrowWithGap() {
        ElementNode root = el("div", null, "container");
        ElementNode fixed = el("div", null, "fixed");
        ElementNode grow = el("div", null, "grow");
        root.appendChild(fixed);
        root.appendChild(grow);
        LayoutBox box = layout(root,
                ".container { display: flex; gap: 10px; width: 300px; height: 30px; align-items: flex-start; }" +
                ".fixed { width: 100px; height: 30px; }" +
                ".grow { flex-grow: 1; height: 30px; }");

        // fixed=100, gap=10, 剩余 = 300 - 100 - 10 = 190 → grow
        assertEquals(100f, box.child(0).borderBoxWidth(), 0.0001f);
        assertEquals(190f, box.child(1).borderBoxWidth(), 0.0001f);
        assertEquals(0f, box.child(0).borderBoxX(), 0.0001f);
        assertEquals(110f, box.child(1).borderBoxX(), 0.0001f);
    }

    @Test
    public void flexGrowColumnDirection() {
        ElementNode root = el("div", null, "container");
        ElementNode fixed = el("div", null, "fixed");
        ElementNode grow = el("div", null, "grow");
        root.appendChild(fixed);
        root.appendChild(grow);
        LayoutBox box = layout(root,
                ".container { display: flex; flex-direction: column; width: 100px; height: 300px; align-items: flex-start; }" +
                ".fixed { width: 50px; height: 100px; }" +
                ".grow { flex-grow: 1; width: 50px; }");

        // fixed 高 100，剩余 200 给 grow
        assertEquals(100f, box.child(0).borderBoxHeight(), 0.0001f);
        assertEquals(200f, box.child(1).borderBoxHeight(), 0.0001f);
        assertEquals(0f, box.child(0).borderBoxY(), 0.0001f);
        assertEquals(100f, box.child(1).borderBoxY(), 0.0001f);
    }

    @Test
    public void flexColumnAutoHeightItemsUseMeasuredContentAsBasis() {
        ElementNode root = el("div", null, "container");
        ElementNode firstGroup = el("div", null, "group");
        ElementNode secondGroup = el("div", null, "group");
        firstGroup.appendChild(el("div", null, "card"));
        secondGroup.appendChild(el("div", null, "card"));
        root.appendChild(firstGroup);
        root.appendChild(secondGroup);

        LayoutBox box = layout(root,
                ".container { display: flex; flex-direction: column; width: 100px; height: 60px; gap: 8px; align-items: flex-start; }" +
                ".group { width: 100px; flex-shrink: 0; }" +
                ".card { width: 100px; height: 40px; }");

        assertEquals(40f, box.child(0).borderBoxHeight(), 0.0001f);
        assertEquals(40f, box.child(1).borderBoxHeight(), 0.0001f);
        assertEquals(0f, box.child(0).borderBoxY(), 0.0001f);
        assertEquals(48f, box.child(1).borderBoxY(), 0.0001f);
    }

    // ---- flex-basis（切片 6） ----

    @Test
    public void flexBasisLengthOverridesWidth() {
        ElementNode root = el("div", null, "container");
        ElementNode a = el("div", null, "a");
        ElementNode b = el("div", null, "b");
        root.appendChild(a);
        root.appendChild(b);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 300px; height: 30px; align-items: flex-start; }" +
                ".a { width: 100px; flex-basis: 50px; height: 30px; }" +
                ".b { flex-grow: 1; height: 30px; }");

        // a 的 base = flex-basis 50（忽略 width:100）；剩余 250 给 b
        assertEquals(50f, box.child(0).borderBoxWidth(), 0.0001f);
        assertEquals(250f, box.child(1).borderBoxWidth(), 0.0001f);
    }

    @Test
    public void flexBasisAutoUsesWidth() {
        ElementNode root = el("div", null, "container");
        ElementNode a = el("div", null, "a");
        ElementNode b = el("div", null, "b");
        root.appendChild(a);
        root.appendChild(b);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 300px; height: 30px; align-items: flex-start; }" +
                ".a { width: 80px; flex-basis: auto; height: 30px; }" +
                ".b { flex-grow: 1; height: 30px; }");

        // a base = width 80；剩余 220 给 b
        assertEquals(80f, box.child(0).borderBoxWidth(), 0.0001f);
        assertEquals(220f, box.child(1).borderBoxWidth(), 0.0001f);
    }

    // ---- flex-shrink（切片 6） ----

    @Test
    public void flexShrinkReducesOverflowingItems() {
        ElementNode root = el("div", null, "container");
        ElementNode a = el("div", null, "a");
        ElementNode b = el("div", null, "b");
        root.appendChild(a);
        root.appendChild(b);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 200px; height: 30px; align-items: flex-start; }" +
                ".a { width: 150px; height: 30px; flex-shrink: 1; }" +
                ".b { width: 150px; height: 30px; flex-shrink: 1; }");

        // 两项总 300，溢出 100；shrink 1:1，各减 50 → 各 100
        assertEquals(100f, box.child(0).borderBoxWidth(), 0.0001f);
        assertEquals(100f, box.child(1).borderBoxWidth(), 0.0001f);
    }

    @Test
    public void flexShrinkProportionalByBasisTimesShrink() {
        ElementNode root = el("div", null, "container");
        ElementNode a = el("div", null, "a");
        ElementNode b = el("div", null, "b");
        root.appendChild(a);
        root.appendChild(b);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 200px; height: 30px; align-items: flex-start; }" +
                // a: basis=100, shrink=1 → weight 100
                // b: basis=200, shrink=1 → weight 200
                // 总 300，溢出 100；a 减 100*(100/300)=33.33, b 减 100*(200/300)=66.67
                ".a { width: 100px; height: 30px; flex-shrink: 1; }" +
                ".b { width: 200px; height: 30px; flex-shrink: 1; }");

        assertEquals(66.66667f, box.child(0).borderBoxWidth(), 0.01f);
        assertEquals(133.33333f, box.child(1).borderBoxWidth(), 0.01f);
    }

    @Test
    public void flexShrinkZeroPreventsShrinking() {
        ElementNode root = el("div", null, "container");
        ElementNode a = el("div", null, "a");
        ElementNode b = el("div", null, "b");
        root.appendChild(a);
        root.appendChild(b);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 200px; height: 30px; align-items: flex-start; }" +
                ".a { width: 100px; height: 30px; flex-shrink: 0; }" +
                ".b { width: 200px; height: 30px; flex-shrink: 1; }");

        // a 不收缩，保持 100；b 收缩到 100（剩余空间）
        assertEquals(100f, box.child(0).borderBoxWidth(), 0.0001f);
        assertEquals(100f, box.child(1).borderBoxWidth(), 0.0001f);
    }

    // ---- min/max 约束（切片 6） ----

    @Test
    public void maxWidthClampsFlexGrowResult() {
        ElementNode root = el("div", null, "container");
        ElementNode a = el("div", null, "a");
        ElementNode b = el("div", null, "b");
        root.appendChild(a);
        root.appendChild(b);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 300px; height: 30px; align-items: flex-start; }" +
                ".a { width: 100px; height: 30px; }" +
                // b base=0, grow=1，本应得 200，但 max-width=150 钳制
                ".b { flex-grow: 1; max-width: 150px; height: 30px; }");

        assertEquals(100f, box.child(0).borderBoxWidth(), 0.0001f);
        assertEquals(150f, box.child(1).borderBoxWidth(), 0.0001f);
    }

    @Test
    public void minWidthClampsFlexShrinkResult() {
        ElementNode root = el("div", null, "container");
        ElementNode a = el("div", null, "a");
        ElementNode b = el("div", null, "b");
        root.appendChild(a);
        root.appendChild(b);
        LayoutBox box = layout(root,
                ".container { display: flex; width: 200px; height: 30px; align-items: flex-start; }" +
                // 两项各 150，溢出 100；shrink 1:1 各减 50 → 100，但 min-width=120 钳制
                ".a { width: 150px; min-width: 120px; height: 30px; flex-shrink: 1; }" +
                ".b { width: 150px; min-width: 120px; height: 30px; flex-shrink: 1; }");

        assertEquals(120f, box.child(0).borderBoxWidth(), 0.0001f);
        assertEquals(120f, box.child(1).borderBoxWidth(), 0.0001f);
    }

    @Test
    public void maxHeightClampsFlexGrowColumn() {
        ElementNode root = el("div", null, "container");
        ElementNode a = el("div", null, "a");
        ElementNode b = el("div", null, "b");
        root.appendChild(a);
        root.appendChild(b);
        LayoutBox box = layout(root,
                ".container { display: flex; flex-direction: column; width: 100px; height: 300px; align-items: flex-start; }" +
                ".a { width: 50px; height: 100px; }" +
                // b base=0, grow=1，本应得 200，但 max-height=150 钳制
                ".b { flex-grow: 1; max-height: 150px; width: 50px; }");

        assertEquals(100f, box.child(0).borderBoxHeight(), 0.0001f);
        assertEquals(150f, box.child(1).borderBoxHeight(), 0.0001f);
    }

    @Test
    public void minMaxWidthOnBlockElementClampsWidth() {
        // block 元素的 width 也应受 min/max-width 钳制
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        LayoutBox box = layout(root,
                ".parent { width: 300px; height: 100px; }" +
                ".child { width: 500px; max-width: 200px; height: 30px; }");

        assertEquals(200f, box.child(0).borderBoxWidth(), 0.0001f);
    }

    @Test
    public void minWidthRaisesBlockWidthAboveDeclared() {
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        LayoutBox box = layout(root,
                ".parent { width: 300px; height: 100px; }" +
                ".child { width: 50px; min-width: 120px; height: 30px; }");

        assertEquals(120f, box.child(0).borderBoxWidth(), 0.0001f);
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
