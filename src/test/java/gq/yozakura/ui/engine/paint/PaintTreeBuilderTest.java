package gq.yozakura.ui.engine.paint;

import gq.yozakura.ui.engine.css.ComputedStyle;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.dom.TextNode;
import gq.yozakura.ui.engine.layout.BorderEdges;
import gq.yozakura.ui.engine.layout.LayoutBox;
import gq.yozakura.ui.engine.layout.MarginEdges;
import gq.yozakura.ui.engine.layout.Overflow;
import gq.yozakura.ui.engine.layout.PaddingEdges;
import gq.yozakura.ui.engine.layout.Position;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 3 切片 2：PaintTreeBuilder 契约测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>background-color → RectFillCommand（border-box 几何）</li>
 *   <li>border → RectBorderCommand（含 radius）</li>
 *   <li>overflow: hidden → ClipPush/ClipPop 包裹子树（裁剪到 padding 盒）</li>
 *   <li>递归遍历：父 → 子，坐标累加为绝对逻辑坐标</li>
 *   <li>无 background/无 border 不产生命令</li>
 *   <li>渲染顺序：背景 → border → 裁剪 → 子内容 → 出栈</li>
 * </ul>
 */
public class PaintTreeBuilderTest {

    private static ElementNode el(String tag) {
        return ElementNode.create(tag);
    }

    private static ComputedStyle.Builder style() {
        return ComputedStyle.builder();
    }

    private static LayoutBox box(ElementNode element,
                                 float x, float y, float w, float h,
                                 BorderEdges border, PaddingEdges padding,
                                 Overflow overflow) {
        return new LayoutBox(element, x, y, w, h,
                MarginEdges.zero(), border, padding,
                0, Position.STATIC, overflow, java.util.Collections.<LayoutBox>emptyList());
    }

    private static LayoutBox boxWithChildren(ElementNode element,
                                             float x, float y, float w, float h,
                                             BorderEdges border, PaddingEdges padding,
                                             Overflow overflow,
                                             LayoutBox... children) {
        java.util.List<LayoutBox> kids = new java.util.ArrayList<LayoutBox>();
        java.util.Collections.addAll(kids, children);
        return new LayoutBox(element, x, y, w, h,
                MarginEdges.zero(), border, padding,
                0, Position.STATIC, overflow, kids);
    }

    private static Map<ElementNode, ComputedStyle> styles(ElementNode root, ComputedStyle rootStyle) {
        Map<ElementNode, ComputedStyle> map = new HashMap<ElementNode, ComputedStyle>();
        map.put(root, rootStyle);
        return map;
    }

    private static PaintCommandList build(LayoutBox root, Map<ElementNode, ComputedStyle> styles) {
        return new PaintTreeBuilder().build(root, styles);
    }

    // ---- 单元素：background + border ----

    @Test
    public void backgroundProducesRectFillAtBorderBox() {
        ElementNode e = el("div");
        ComputedStyle s = style().set("background-color", "#ff0000").build();
        BorderEdges border = new BorderEdges(0, 0, 0, 0);
        PaddingEdges padding = PaddingEdges.parseShorthand("0");
        LayoutBox root = box(e, 10, 20, 100, 50, border, padding, Overflow.VISIBLE);

        PaintCommandList list = build(root, styles(e, s));

        assertEquals(1, list.size());
        PaintCommand cmd = list.command(0);
        assertEquals(PaintCommand.TYPE_RECT_FILL, cmd.type());
        RectFillCommand fill = (RectFillCommand) cmd;
        assertEquals(10f, fill.x(), 0.0001f);
        assertEquals(20f, fill.y(), 0.0001f);
        assertEquals(100f, fill.width(), 0.0001f);
        assertEquals(50f, fill.height(), 0.0001f);
        assertEquals(1f, fill.color().r(), 0.0001f);
        assertEquals(0f, fill.color().g(), 0.0001f);
        assertEquals(0f, fill.color().b(), 0.0001f);
    }

    @Test
    public void retainedVisualStateTranslatesAndFadesWholeSubtree() {
        ElementNode parent = el("div");
        ElementNode child = el("span");
        parent.appendChild(child);
        Map<ElementNode, ComputedStyle> styleMap = new HashMap<ElementNode, ComputedStyle>();
        styleMap.put(parent, style().set("background-color", "#ffffff").build());
        styleMap.put(child, style().set("background-color", "#ff0000").build());
        LayoutBox childBox = box(child, 5, 6, 10, 10, new BorderEdges(0, 0, 0, 0),
                PaddingEdges.parseShorthand("0"), Overflow.VISIBLE);
        LayoutBox parentBox = boxWithChildren(parent, 10, 20, 50, 40, new BorderEdges(0, 0, 0, 0),
                PaddingEdges.parseShorthand("0"), Overflow.VISIBLE, childBox);
        Map<ElementNode, PaintVisualState> visuals =
                new java.util.IdentityHashMap<ElementNode, PaintVisualState>();
        visuals.put(parent, new PaintVisualState(3.0F, -2.0F, 0.5F));

        PaintCommandList list = new PaintTreeBuilder().build(parentBox, styleMap, visuals);

        RectFillCommand parentFill = (RectFillCommand) list.command(0);
        RectFillCommand childFill = (RectFillCommand) list.command(1);
        assertEquals(13.0F, parentFill.x(), 0.001F);
        assertEquals(18.0F, parentFill.y(), 0.001F);
        assertEquals(18.0F, childFill.x(), 0.001F);
        assertEquals(24.0F, childFill.y(), 0.001F);
        assertEquals(0.5F, childFill.color().a(), 0.001F);
    }

    @Test
    public void borderProducesRectBorderCommand() {
        ElementNode e = el("div");
        ComputedStyle s = style()
                .set("border", "2px solid #000")
                .set("border-radius", "4px")
                .build();
        BorderEdges border = new BorderEdges(2, 2, 2, 2);
        PaddingEdges padding = PaddingEdges.parseShorthand("0");
        LayoutBox root = box(e, 0, 0, 50, 50, border, padding, Overflow.VISIBLE);

        PaintCommandList list = build(root, styles(e, s));

        // 应有：背景（无）+ border
        assertEquals(1, list.size());
        assertEquals(PaintCommand.TYPE_RECT_BORDER, list.command(0).type());
        RectBorderCommand b = (RectBorderCommand) list.command(0);
        assertEquals(2f, b.borderTop(), 0.0001f);
        assertEquals(4f, b.radius(), 0.0001f);
    }

    @Test
    public void backgroundAndBorderProduceFillThenBorder() {
        ElementNode e = el("div");
        ComputedStyle s = style()
                .set("background-color", "#fff")
                .set("border", "1px solid #000")
                .build();
        BorderEdges border = new BorderEdges(1, 1, 1, 1);
        PaddingEdges padding = PaddingEdges.parseShorthand("0");
        LayoutBox root = box(e, 0, 0, 50, 50, border, padding, Overflow.VISIBLE);

        PaintCommandList list = build(root, styles(e, s));

        // 顺序：背景 → border
        assertEquals(2, list.size());
        assertEquals(PaintCommand.TYPE_RECT_FILL, list.command(0).type());
        assertEquals(PaintCommand.TYPE_RECT_BORDER, list.command(1).type());
    }

    @Test
    public void outerBoxShadowProducesSingleBoundedShaderCommandBeforeBackground() {
        ElementNode e = el("div");
        ComputedStyle s = style()
                .set("background-color", "#222222")
                .set("border-radius", "8px")
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.30)")
                .build();
        LayoutBox root = box(e, 10, 20, 100, 40,
                new BorderEdges(0, 0, 0, 0), PaddingEdges.parseShorthand("0"),
                Overflow.VISIBLE);

        PaintCommandList list = build(root, styles(e, s));

        assertEquals(2, list.size());
        RectFillCommand shadow = (RectFillCommand) list.command(0);
        RectFillCommand background = (RectFillCommand) list.command(1);
        assertTrue(shadow.isShadow());
        assertEquals(10.0F, shadow.x(), 0.001F);
        assertEquals(24.0F, shadow.y(), 0.001F);
        assertEquals(100.0F, shadow.width(), 0.001F);
        assertEquals(12.0F, shadow.shadowBlur(), 0.001F);
        assertEquals(0.30F, shadow.color().a(), 0.001F);
        assertEquals(10.0F, background.x(), 0.001F);
        assertEquals(20.0F, background.y(), 0.001F);
    }

    @Test
    public void linearGradientBackgroundRetainsColorsAndAngle() {
        ElementNode e = el("div");
        ComputedStyle s = style()
                .set("background", "linear-gradient(135deg, #f08bb0, #f5a6c7)")
                .set("border-radius", "8px")
                .build();
        LayoutBox root = box(e, 0, 0, 40, 30,
                new BorderEdges(0, 0, 0, 0), PaddingEdges.parseShorthand("0"),
                Overflow.VISIBLE);

        PaintCommandList list = build(root, styles(e, s));

        assertEquals(1, list.size());
        RectFillCommand gradient = (RectFillCommand) list.command(0);
        assertTrue(gradient.isGradient());
        assertEquals(Color.parse("#f08bb0"), gradient.color());
        assertEquals(Color.parse("#f5a6c7"), gradient.endColor());
        assertEquals(135.0F, gradient.gradientAngleDegrees(), 0.001F);
    }

    @Test
    public void borderRadiusShorthandRetainsIndependentCorners() {
        ElementNode e = el("div");
        ComputedStyle s = style()
                .set("background-color", "#222222")
                .set("border-radius", "12px 12px 0 0")
                .build();
        LayoutBox root = box(e, 0, 0, 100, 40,
                new BorderEdges(0, 0, 0, 0), PaddingEdges.parseShorthand("0"),
                Overflow.VISIBLE);

        RectFillCommand fill = (RectFillCommand) build(root, styles(e, s)).command(0);

        assertEquals(12.0F, fill.topLeftRadius(), 0.001F);
        assertEquals(12.0F, fill.topRightRadius(), 0.001F);
        assertEquals(0.0F, fill.bottomRightRadius(), 0.001F);
        assertEquals(0.0F, fill.bottomLeftRadius(), 0.001F);
    }

    @Test
    public void noBackgroundNoBorderProducesNoCommands() {
        ElementNode e = el("div");
        ComputedStyle s = style().build();
        BorderEdges border = new BorderEdges(0, 0, 0, 0);
        PaddingEdges padding = PaddingEdges.parseShorthand("0");
        LayoutBox root = box(e, 0, 0, 50, 50, border, padding, Overflow.VISIBLE);

        PaintCommandList list = build(root, styles(e, s));

        assertEquals(0, list.size());
    }

    @Test
    public void transparentBackgroundProducesNoFill() {
        ElementNode e = el("div");
        ComputedStyle s = style().set("background-color", "transparent").build();
        BorderEdges border = new BorderEdges(0, 0, 0, 0);
        PaddingEdges padding = PaddingEdges.parseShorthand("0");
        LayoutBox root = box(e, 0, 0, 50, 50, border, padding, Overflow.VISIBLE);

        PaintCommandList list = build(root, styles(e, s));

        assertEquals(0, list.size());
    }

    @Test
    public void textAlignCenterIsRetainedWithContentWidth() {
        ElementNode e = el("span");
        e.appendChild(TextNode.of("Y"));
        ComputedStyle s = style()
                .set("color", "#ffffff")
                .set("font-size", "14px")
                .set("text-align", "center")
                .build();
        BorderEdges border = new BorderEdges(1, 1, 1, 1);
        PaddingEdges padding = PaddingEdges.parseShorthand("4px");
        LayoutBox root = box(e, 10, 20, 40, 30, border, padding, Overflow.VISIBLE);

        PaintCommandList list = build(root, styles(e, s));

        TextPaintCommand text = (TextPaintCommand) list.command(0);
        assertEquals(TextPaintCommand.ALIGN_CENTER, text.alignment());
        assertEquals(30.0F, text.availableWidth(), 0.001F);
        assertEquals(15.0F, text.x(), 0.001F);
    }

    // ---- 递归 ----

    @Test
    public void childrenArePaintedAfterParentBackgroundAndBorder() {
        ElementNode parent = el("div");
        ElementNode child = el("span");
        ComputedStyle parentStyle = style().set("background-color", "#ff0000").build();
        ComputedStyle childStyle = style().set("background-color", "#00ff00").build();
        Map<ElementNode, ComputedStyle> styles = new HashMap<ElementNode, ComputedStyle>();
        styles.put(parent, parentStyle);
        styles.put(child, childStyle);

        // parent at (0,0) 100x100；child at content origin (10,10) 50x50（相对 parent content）
        BorderEdges noBorder = new BorderEdges(0, 0, 0, 0);
        PaddingEdges padding = PaddingEdges.parseShorthand("10px");
        LayoutBox childBox = box(child, 0, 0, 50, 50, noBorder, PaddingEdges.parseShorthand("0"), Overflow.VISIBLE);
        LayoutBox parentBox = boxWithChildren(parent, 0, 0, 100, 100, noBorder, padding, Overflow.VISIBLE, childBox);

        PaintCommandList list = build(parentBox, styles);

        // 顺序：parent 背景 → child 背景
        assertEquals(2, list.size());
        RectFillCommand parentFill = (RectFillCommand) list.command(0);
        RectFillCommand childFill = (RectFillCommand) list.command(1);
        assertEquals(1f, parentFill.color().r(), 0.0001f);

        // child 绝对坐标 = parent content origin (10,10) + child (0,0) = (10,10)
        assertEquals(10f, childFill.x(), 0.0001f);
        assertEquals(10f, childFill.y(), 0.0001f);
        assertEquals(50f, childFill.width(), 0.0001f);
        assertEquals(1f, childFill.color().g(), 0.0001f);
    }

    // ---- overflow hidden 产生裁剪 ----

    @Test
    public void overflowHiddenProducesClipPushPopAroundChildren() {
        ElementNode parent = el("div");
        ElementNode child = el("span");
        ComputedStyle parentStyle = style().set("background-color", "#ff0000").build();
        ComputedStyle childStyle = style().set("background-color", "#00ff00").build();
        Map<ElementNode, ComputedStyle> styles = new HashMap<ElementNode, ComputedStyle>();
        styles.put(parent, parentStyle);
        styles.put(child, childStyle);

        BorderEdges noBorder = new BorderEdges(0, 0, 0, 0);
        PaddingEdges padding = PaddingEdges.parseShorthand("0");
        LayoutBox childBox = box(child, 0, 0, 200, 200, noBorder, padding, Overflow.VISIBLE);
        LayoutBox parentBox = boxWithChildren(parent, 10, 10, 100, 100, noBorder, padding,
                Overflow.HIDDEN, childBox);

        PaintCommandList list = build(parentBox, styles);

        // 顺序：parent 背景 → ClipPush → child 背景 → ClipPop
        assertEquals(4, list.size());
        assertEquals(PaintCommand.TYPE_RECT_FILL, list.command(0).type());      // parent bg
        assertEquals(PaintCommand.TYPE_CLIP_PUSH, list.command(1).type());
        assertEquals(PaintCommand.TYPE_RECT_FILL, list.command(2).type());      // child bg
        assertEquals(PaintCommand.TYPE_CLIP_POP, list.command(3).type());

        // ClipPush 矩形 = parent padding 盒绝对坐标 (10,10,100,100)
        ClipPushCommand clip = (ClipPushCommand) list.command(1);
        assertEquals(10f, clip.x(), 0.0001f);
        assertEquals(10f, clip.y(), 0.0001f);
        assertEquals(100f, clip.width(), 0.0001f);
        assertEquals(100f, clip.height(), 0.0001f);
    }

    @Test
    public void overflowVisibleDoesNotProduceClip() {
        ElementNode parent = el("div");
        ElementNode child = el("span");
        ComputedStyle parentStyle = style().set("background-color", "#ff0000").build();
        ComputedStyle childStyle = style().set("background-color", "#00ff00").build();
        Map<ElementNode, ComputedStyle> styles = new HashMap<ElementNode, ComputedStyle>();
        styles.put(parent, parentStyle);
        styles.put(child, childStyle);

        BorderEdges noBorder = new BorderEdges(0, 0, 0, 0);
        PaddingEdges padding = PaddingEdges.parseShorthand("0");
        LayoutBox childBox = box(child, 0, 0, 50, 50, noBorder, padding, Overflow.VISIBLE);
        LayoutBox parentBox = boxWithChildren(parent, 0, 0, 100, 100, noBorder, padding,
                Overflow.VISIBLE, childBox);

        PaintCommandList list = build(parentBox, styles);

        // 无裁剪命令
        for (int i = 0; i < list.size(); i++) {
            assertTrue("overflow:visible 不应产生 clip 命令",
                    list.command(i).type() != PaintCommand.TYPE_CLIP_PUSH
                            && list.command(i).type() != PaintCommand.TYPE_CLIP_POP);
        }
    }

    @Test
    public void overflowAutoAlsoClips() {
        // AUTO 等同 HIDDEN（MVP 不实现滚动条，但仍裁剪）
        ElementNode parent = el("div");
        ElementNode child = el("span");
        ComputedStyle parentStyle = style().build();
        ComputedStyle childStyle = style().set("background-color", "#00ff00").build();
        Map<ElementNode, ComputedStyle> styles = new HashMap<ElementNode, ComputedStyle>();
        styles.put(parent, parentStyle);
        styles.put(child, childStyle);

        BorderEdges noBorder = new BorderEdges(0, 0, 0, 0);
        PaddingEdges padding = PaddingEdges.parseShorthand("0");
        LayoutBox childBox = box(child, 0, 0, 50, 50, noBorder, padding, Overflow.VISIBLE);
        LayoutBox parentBox = boxWithChildren(parent, 0, 0, 100, 100, noBorder, padding,
                Overflow.AUTO, childBox);

        PaintCommandList list = build(parentBox, styles);

        // 应有 ClipPush 与 ClipPop（即使 parent 无背景）
        assertEquals(3, list.size());
        assertEquals(PaintCommand.TYPE_CLIP_PUSH, list.command(0).type());
        assertEquals(PaintCommand.TYPE_RECT_FILL, list.command(1).type());
        assertEquals(PaintCommand.TYPE_CLIP_POP, list.command(2).type());
    }

    // ---- 几何：border-box + content origin 累加 ----

    @Test
    public void childAbsolutePositionAccountsForParentBorderAndPadding() {
        ElementNode parent = el("div");
        ElementNode child = el("span");
        ComputedStyle parentStyle = style().set("background-color", "#ff0000").build();
        ComputedStyle childStyle = style().set("background-color", "#00ff00").build();
        Map<ElementNode, ComputedStyle> styles = new HashMap<ElementNode, ComputedStyle>();
        styles.put(parent, parentStyle);
        styles.put(child, childStyle);

        // parent at (10,10) borderBox 100x100，border=2，padding=8
        // parent content origin = (10+2+8, 10+2+8) = (20, 20)
        BorderEdges border = new BorderEdges(2, 2, 2, 2);
        PaddingEdges padding = PaddingEdges.parseShorthand("8px");
        LayoutBox childBox = box(child, 5, 5, 30, 30,
                new BorderEdges(0, 0, 0, 0), PaddingEdges.parseShorthand("0"), Overflow.VISIBLE);
        LayoutBox parentBox = boxWithChildren(parent, 10, 10, 100, 100, border, padding,
                Overflow.VISIBLE, childBox);

        PaintCommandList list = build(parentBox, styles);

        // child 绝对 = parent content origin (20,20) + child (5,5) = (25,25)
        RectFillCommand childFill = (RectFillCommand) list.command(1);
        assertEquals(25f, childFill.x(), 0.0001f);
        assertEquals(25f, childFill.y(), 0.0001f);
        assertEquals(30f, childFill.width(), 0.0001f);
    }

    // ---- 缺失样式容错 ----

    @Test
    public void missingComputedStyleSkipsElement() {
        // 元素不在 styles map 中：不应崩溃，子树仍递归
        ElementNode parent = el("div");
        ElementNode child = el("span");
        // 只给 child 样式，不给 parent
        ComputedStyle childStyle = style().set("background-color", "#00ff00").build();
        Map<ElementNode, ComputedStyle> styles = new HashMap<ElementNode, ComputedStyle>();
        styles.put(child, childStyle);

        BorderEdges noBorder = new BorderEdges(0, 0, 0, 0);
        PaddingEdges padding = PaddingEdges.parseShorthand("0");
        LayoutBox childBox = box(child, 0, 0, 50, 50, noBorder, padding, Overflow.VISIBLE);
        LayoutBox parentBox = boxWithChildren(parent, 0, 0, 100, 100, noBorder, padding,
                Overflow.VISIBLE, childBox);

        PaintCommandList list = build(parentBox, styles);

        // parent 无样式 → 仅 child 背景
        assertEquals(1, list.size());
        assertEquals(PaintCommand.TYPE_RECT_FILL, list.command(0).type());
    }

    @Test
    public void invalidBackgroundColorFailsWithContext() {
        ElementNode e = el("div");
        ComputedStyle s = style()
                .set("background-color", "not-a-color")
                .set("border", "1px solid #000")
                .build();
        BorderEdges border = new BorderEdges(1, 1, 1, 1);
        PaddingEdges padding = PaddingEdges.parseShorthand("0");
        LayoutBox root = box(e, 0, 0, 50, 50, border, padding, Overflow.VISIBLE);

        try {
            build(root, styles(e, s));
            org.junit.Assert.fail("invalid CSS color must not be silently skipped");
        } catch (IllegalArgumentException error) {
            org.junit.Assert.assertTrue(error.getMessage().contains("background-color"));
            org.junit.Assert.assertTrue(error.getMessage().contains("not-a-color"));
        }
    }

    // ---- 深度递归 ----

    @Test
    public void deepNestingPreservesOrder() {
        // div > div > div，三层各带背景（红/绿/蓝各一种）
        ElementNode a = el("div");
        ElementNode b = el("div");
        ElementNode c = el("div");
        ComputedStyle sa = style().set("background-color", "#ff0000").build();
        ComputedStyle sb = style().set("background-color", "#00ff00").build();
        ComputedStyle sc = style().set("background-color", "#0000ff").build();
        Map<ElementNode, ComputedStyle> styles = new HashMap<ElementNode, ComputedStyle>();
        styles.put(a, sa);
        styles.put(b, sb);
        styles.put(c, sc);

        BorderEdges noBorder = new BorderEdges(0, 0, 0, 0);
        PaddingEdges noPadding = PaddingEdges.parseShorthand("0");
        LayoutBox cBox = box(c, 0, 0, 10, 10, noBorder, noPadding, Overflow.VISIBLE);
        LayoutBox bBox = boxWithChildren(b, 0, 0, 20, 20, noBorder, noPadding, Overflow.VISIBLE, cBox);
        LayoutBox aBox = boxWithChildren(a, 0, 0, 30, 30, noBorder, noPadding, Overflow.VISIBLE, bBox);

        PaintCommandList list = build(aBox, styles);

        assertEquals(3, list.size());
        // 顺序：a（红）→ b（绿）→ c（蓝）
        assertEquals(1f, ((RectFillCommand) list.command(0)).color().r(), 0.0001f); // a
        assertEquals(1f, ((RectFillCommand) list.command(1)).color().g(), 0.0001f); // b
        assertEquals(1f, ((RectFillCommand) list.command(2)).color().b(), 0.0001f); // c
    }
}
