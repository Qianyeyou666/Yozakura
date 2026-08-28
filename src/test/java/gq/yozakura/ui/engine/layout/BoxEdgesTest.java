package gq.yozakura.ui.engine.layout;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 阶段 2 切片 2：BoxEdges + box-sizing 契约测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>BoxEdges 存储 top/right/bottom/left 四向值</li>
 *   <li>从 ComputedStyle 解析 margin/padding/border-width 简写与单边</li>
 *   <li>box-sizing: content-box（默认）与 border-box 的尺寸换算</li>
 *   <li>auto margin 标记（用于 flex 居中）</li>
 *   <li>border-width 解析（px 数值；border 简写中提取宽度部分）</li>
 * </ul>
 */
public class BoxEdgesTest {

    // ---- BoxEdges 值对象 ----

    @Test
    public void boxEdgesStoresFourSides() {
        BoxEdges e = new BoxEdges(1, 2, 3, 4);
        assertEquals(1f, e.top(), 0.0001f);
        assertEquals(2f, e.right(), 0.0001f);
        assertEquals(3f, e.bottom(), 0.0001f);
        assertEquals(4f, e.left(), 0.0001f);
    }

    @Test
    public void boxEdgesHorizontalSum() {
        BoxEdges e = new BoxEdges(1, 10, 3, 20);
        assertEquals(30f, e.horizontalSum(), 0.0001f); // left+right
    }

    @Test
    public void boxEdgesVerticalSum() {
        BoxEdges e = new BoxEdges(10, 2, 30, 4);
        assertEquals(40f, e.verticalSum(), 0.0001f); // top+bottom
    }

    @Test
    public void boxEdgesZeroFactory() {
        BoxEdges e = BoxEdges.zero();
        assertEquals(0f, e.top(), 0.0001f);
        assertEquals(0f, e.right(), 0.0001f);
        assertEquals(0f, e.bottom(), 0.0001f);
        assertEquals(0f, e.left(), 0.0001f);
        assertTrue(e.isAllZero());
    }

    @Test
    public void boxEdgesAllZeroDetection() {
        assertTrue(BoxEdges.zero().isAllZero());
        assertFalse(new BoxEdges(0, 0, 0, 1).isAllZero());
        assertFalse(new BoxEdges(0, 1, 0, 0).isAllZero());
    }

    @Test
    public void boxEdgesUniformFactory() {
        BoxEdges e = BoxEdges.uniform(5);
        assertEquals(5f, e.top(), 0.0001f);
        assertEquals(5f, e.right(), 0.0001f);
        assertEquals(5f, e.bottom(), 0.0001f);
        assertEquals(5f, e.left(), 0.0001f);
    }

    // ---- margin 解析（含 auto 标记） ----

    @Test
    public void marginParsesSinglePx() {
        // margin: 10px → 四边均 10
        MarginEdges m = MarginEdges.parseSingle("10px");
        assertEquals(10f, m.top(), 0.0001f);
        assertEquals(10f, m.right(), 0.0001f);
        assertEquals(10f, m.bottom(), 0.0001f);
        assertEquals(10f, m.left(), 0.0001f);
        assertFalse(m.isTopAuto());
    }

    @Test
    public void marginParsesTwoValues() {
        // margin: 10px 20px → top/bottom=10, left/right=20
        MarginEdges m = MarginEdges.parseShorthand("10px 20px");
        assertEquals(10f, m.top(), 0.0001f);
        assertEquals(20f, m.right(), 0.0001f);
        assertEquals(10f, m.bottom(), 0.0001f);
        assertEquals(20f, m.left(), 0.0001f);
    }

    @Test
    public void marginParsesThreeValues() {
        // margin: 10px 20px 30px → top=10, left/right=20, bottom=30
        MarginEdges m = MarginEdges.parseShorthand("10px 20px 30px");
        assertEquals(10f, m.top(), 0.0001f);
        assertEquals(20f, m.right(), 0.0001f);
        assertEquals(30f, m.bottom(), 0.0001f);
        assertEquals(20f, m.left(), 0.0001f);
    }

    @Test
    public void marginParsesFourValues() {
        // margin: 1 2 3 4 → top right bottom left
        MarginEdges m = MarginEdges.parseShorthand("1px 2px 3px 4px");
        assertEquals(1f, m.top(), 0.0001f);
        assertEquals(2f, m.right(), 0.0001f);
        assertEquals(3f, m.bottom(), 0.0001f);
        assertEquals(4f, m.left(), 0.0001f);
    }

    @Test
    public void marginAutoSetsAutoFlag() {
        MarginEdges m = MarginEdges.parseShorthand("auto");
        assertTrue(m.isTopAuto());
        assertTrue(m.isRightAuto());
        assertTrue(m.isBottomAuto());
        assertTrue(m.isLeftAuto());
    }

    @Test
    public void marginAutoMixedWithPx() {
        // margin: 0 auto → top/bottom=0, left/right=auto
        MarginEdges m = MarginEdges.parseShorthand("0 auto");
        assertFalse(m.isTopAuto());
        assertTrue(m.isRightAuto());
        assertEquals(0f, m.top(), 0.0001f);
    }

    @Test
    public void marginEmptyReturnsZero() {
        MarginEdges m = MarginEdges.parseShorthand("");
        assertEquals(0f, m.top(), 0.0001f);
        assertFalse(m.isTopAuto());
    }

    @Test
    public void marginNullReturnsZero() {
        MarginEdges m = MarginEdges.parseShorthand(null);
        assertTrue(m.isAllZero());
    }

    // ---- padding 解析 ----

    @Test
    public void paddingParsesFourValues() {
        PaddingEdges p = PaddingEdges.parseShorthand("1px 2px 3px 4px");
        assertEquals(1f, p.top(), 0.0001f);
        assertEquals(2f, p.right(), 0.0001f);
        assertEquals(3f, p.bottom(), 0.0001f);
        assertEquals(4f, p.left(), 0.0001f);
    }

    @Test
    public void paddingCannotBeAuto() {
        // padding 不允许 auto；auto 视为 0
        PaddingEdges p = PaddingEdges.parseShorthand("auto");
        assertEquals(0f, p.top(), 0.0001f);
    }

    @Test
    public void paddingNegativeClampedToZero() {
        // padding 不允许负值；负值钳为 0
        PaddingEdges p = PaddingEdges.parseShorthand("-5px");
        assertEquals(0f, p.top(), 0.0001f);
    }

    // ---- border-width 解析 ----

    @Test
    public void borderWidthFromSinglePx() {
        BorderEdges b = BorderEdges.parseWidthShorthand("2px");
        assertEquals(2f, b.top(), 0.0001f);
        assertEquals(2f, b.right(), 0.0001f);
        assertEquals(2f, b.bottom(), 0.0001f);
        assertEquals(2f, b.left(), 0.0001f);
    }

    @Test
    public void borderWidthFromFourValues() {
        BorderEdges b = BorderEdges.parseWidthShorthand("1px 2px 3px 4px");
        assertEquals(1f, b.top(), 0.0001f);
        assertEquals(4f, b.left(), 0.0001f);
    }

    @Test
    public void borderWidthFromBorderShorthand() {
        // border: 1px solid #ccc → 提取宽度部分
        BorderEdges b = BorderEdges.parseBorderShorthand("1px solid #ccc");
        assertEquals(1f, b.top(), 0.0001f);
    }

    @Test
    public void borderWidthFromBorderShorthandWithStyleKeyword() {
        // border: solid 2px red → 宽度可能在中或后
        BorderEdges b = BorderEdges.parseBorderShorthand("solid 2px red");
        assertEquals(2f, b.top(), 0.0001f);
    }

    @Test
    public void borderWidthFromBorderShorthandNoWidth() {
        // border: solid red → 无宽度部分，默认 0（CSS 默认 medium=3px，但 MC UI 简化为 0）
        BorderEdges b = BorderEdges.parseBorderShorthand("solid red");
        assertEquals(0f, b.top(), 0.0001f);
    }

    @Test
    public void borderWidthNegativeClampedToZero() {
        BorderEdges b = BorderEdges.parseWidthShorthand("-5px");
        assertEquals(0f, b.top(), 0.0001f);
    }

    // ---- box-sizing ----

    @Test
    public void boxSizingContentBoxIsDefault() {
        assertEquals(BoxSizing.CONTENT_BOX, BoxSizing.parse(null));
        assertEquals(BoxSizing.CONTENT_BOX, BoxSizing.parse(""));
        assertEquals(BoxSizing.CONTENT_BOX, BoxSizing.parse("content-box"));
    }

    @Test
    public void boxSizingBorderBox() {
        assertEquals(BoxSizing.BORDER_BOX, BoxSizing.parse("border-box"));
    }

    @Test
    public void boxSizingInvalidReturnsContentBox() {
        // 未知值降级为 content-box（默认）
        assertEquals(BoxSizing.CONTENT_BOX, BoxSizing.parse("invalid"));
    }

    // ---- box-sizing 尺寸换算 ----

    @Test
    public void contentBoxReturnsGivenSizeAsContent() {
        // width=100, padding=10, border=2, sizing=content-box
        // 内容区 = 100，边框盒 = 100 + 2*10 + 2*2 = 124
        BoxSizing sizing = BoxSizing.CONTENT_BOX;
        PaddingEdges pad = PaddingEdges.parseShorthand("10px");
        BorderEdges border = BorderEdges.parseWidthShorthand("2px");

        assertEquals(100f, sizing.contentWidth(100, pad, border), 0.0001f);
        assertEquals(124f, sizing.borderBoxWidth(100, pad, border), 0.0001f);
    }

    @Test
    public void borderBoxSubtractsPaddingAndBorder() {
        // width=100, padding=10, border=2, sizing=border-box
        // 边框盒 = 100，内容区 = 100 - 2*10 - 2*2 = 76
        BoxSizing sizing = BoxSizing.BORDER_BOX;
        PaddingEdges pad = PaddingEdges.parseShorthand("10px");
        BorderEdges border = BorderEdges.parseWidthShorthand("2px");

        assertEquals(100f, sizing.borderBoxWidth(100, pad, border), 0.0001f);
        assertEquals(76f, sizing.contentWidth(100, pad, border), 0.0001f);
    }

    @Test
    public void contentBoxHeightSymmetricToWidth() {
        BoxSizing sizing = BoxSizing.CONTENT_BOX;
        PaddingEdges pad = PaddingEdges.parseShorthand("10px");
        BorderEdges border = BorderEdges.parseWidthShorthand("2px");

        assertEquals(50f, sizing.contentHeight(50, pad, border), 0.0001f);
        assertEquals(74f, sizing.borderBoxHeight(50, pad, border), 0.0001f);
    }

    @Test
    public void borderBoxHeightSubtractsPaddingAndBorder() {
        BoxSizing sizing = BoxSizing.BORDER_BOX;
        PaddingEdges pad = PaddingEdges.parseShorthand("10px");
        BorderEdges border = BorderEdges.parseWidthShorthand("2px");

        assertEquals(50f, sizing.borderBoxHeight(50, pad, border), 0.0001f);
        assertEquals(26f, sizing.contentHeight(50, pad, border), 0.0001f);
    }

    @Test
    public void borderBoxWithZeroPaddingBorder() {
        // 无 padding/border 时，两种 sizing 等价
        BoxSizing cb = BoxSizing.CONTENT_BOX;
        BoxSizing bb = BoxSizing.BORDER_BOX;
        PaddingEdges pad = BoxEdges.zero().asPadding();
        BorderEdges border = BoxEdges.zero().asBorder();

        assertEquals(100f, cb.contentWidth(100, pad, border), 0.0001f);
        assertEquals(100f, bb.contentWidth(100, pad, border), 0.0001f);
        assertEquals(100f, cb.borderBoxWidth(100, pad, border), 0.0001f);
        assertEquals(100f, bb.borderBoxWidth(100, pad, border), 0.0001f);
    }

    // ---- BoxEdges 通用 ----

    @Test
    public void boxEdgesEqualsAndHashCode() {
        BoxEdges a = new BoxEdges(1, 2, 3, 4);
        BoxEdges b = new BoxEdges(1, 2, 3, 4);
        BoxEdges c = new BoxEdges(1, 2, 3, 5);
        assertTrue(a.equals(b));
        assertFalse(a.equals(c));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void boxEdgesAsPaddingAndBorderConversion() {
        BoxEdges e = new BoxEdges(1, 2, 3, 4);
        PaddingEdges p = e.asPadding();
        BorderEdges b = e.asBorder();
        assertEquals(1f, p.top(), 0.0001f);
        assertEquals(4f, b.left(), 0.0001f);
    }

    @Test
    public void marginEdgesRetainsAutoFlagsAfterShorthand() {
        // 验证 4 值 auto 与 px 混合
        MarginEdges m = MarginEdges.parseShorthand("auto 10px auto 20px");
        assertTrue(m.isTopAuto());
        assertFalse(m.isRightAuto());
        assertTrue(m.isBottomAuto());
        assertFalse(m.isLeftAuto());
        assertEquals(10f, m.right(), 0.0001f);
        assertEquals(20f, m.left(), 0.0001f);
    }

    @Test
    public void marginEdgesZeroFactoryReturnsMarginEdgesInstance() {
        // MarginEdges.zero() 必须协变返回 MarginEdges，不能仅继承父类返回 BoxEdges 的 zero()。
        // 否则：调用方拿不到 isTopAuto() 等 MarginEdges 专属访问器；
        // 且若用 private static zero() 隐藏父类 public static zero() 会因降低可见性而无法编译。
        BoxEdges e = MarginEdges.zero();
        assertTrue("MarginEdges.zero() must return MarginEdges, got " + e.getClass(),
                e instanceof MarginEdges);
        MarginEdges m = (MarginEdges) e;
        assertEquals(0f, m.top(), 0.0001f);
        assertEquals(0f, m.right(), 0.0001f);
        assertEquals(0f, m.bottom(), 0.0001f);
        assertEquals(0f, m.left(), 0.0001f);
        assertFalse(m.isTopAuto());
        assertFalse(m.isRightAuto());
        assertFalse(m.isBottomAuto());
        assertFalse(m.isLeftAuto());
    }
}
