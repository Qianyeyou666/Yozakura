package gq.yozakura.ui.engine.layout;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 2 切片 1：{@link Dimension} 解析与解析为像素的契约测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>parse：px / % / em / rem / vw / vh / auto / 无单位数值（含 0 与 1.5）/ 负数 / 非法值</li>
 *   <li>resolveToPx：每种单位正确使用 ResolveContext 提供的基数</li>
 *   <li>auto resolveToPx 返回 autoFallback</li>
 *   <li>equals / hashCode / toString</li>
 * </ul>
 */
public class DimensionTest {

    private static ResolveContext ctx(float percentBase, float emBase, float remBase,
                                      int vw, int vh) {
        return ResolveContext.of(percentBase, 0, emBase, remBase, vw, vh);
    }

    // ---- parse ----

    @Test
    public void parsePx() {
        Dimension d = Dimension.parse("12px");
        assertNotNull(d);
        assertEquals(12f, d.value(), 0.0001f);
        assertEquals(Dimension.Unit.PX, d.unit());
    }

    @Test
    public void parsePercent() {
        Dimension d = Dimension.parse("50%");
        assertNotNull(d);
        assertEquals(50f, d.value(), 0.0001f);
        assertEquals(Dimension.Unit.PERCENT, d.unit());
    }

    @Test
    public void parseEm() {
        Dimension d = Dimension.parse("1.5em");
        assertNotNull(d);
        assertEquals(1.5f, d.value(), 0.0001f);
        assertEquals(Dimension.Unit.EM, d.unit());
    }

    @Test
    public void parseRem() {
        Dimension d = Dimension.parse("1.2rem");
        assertNotNull(d);
        assertEquals(1.2f, d.value(), 0.0001f);
        assertEquals(Dimension.Unit.REM, d.unit());
    }

    @Test
    public void parseVw() {
        Dimension d = Dimension.parse("100vw");
        assertNotNull(d);
        assertEquals(100f, d.value(), 0.0001f);
        assertEquals(Dimension.Unit.VW, d.unit());
    }

    @Test
    public void parseVh() {
        Dimension d = Dimension.parse("50vh");
        assertNotNull(d);
        assertEquals(50f, d.value(), 0.0001f);
        assertEquals(Dimension.Unit.VH, d.unit());
    }

    @Test
    public void parseAuto() {
        Dimension d = Dimension.parse("auto");
        assertNotNull(d);
        assertEquals(Dimension.Unit.AUTO, d.unit());
        assertTrue(d.isAuto());
    }

    @Test
    public void parseZeroWithoutUnitIsPx() {
        // CSS 规范：无单位 0 视为 0px
        Dimension d = Dimension.parse("0");
        assertNotNull(d);
        assertEquals(0f, d.value(), 0.0001f);
        assertEquals(Dimension.Unit.PX, d.unit());
    }

    @Test
    public void parseNumberIsNumberUnit() {
        // line-height / flex-grow 等无单位数值
        Dimension d = Dimension.parse("1.5");
        assertNotNull(d);
        assertEquals(1.5f, d.value(), 0.0001f);
        assertEquals(Dimension.Unit.NUMBER, d.unit());
    }

    @Test
    public void parseNegativePx() {
        Dimension d = Dimension.parse("-10px");
        assertNotNull(d);
        assertEquals(-10f, d.value(), 0.0001f);
        assertEquals(Dimension.Unit.PX, d.unit());
    }

    @Test
    public void parseAllowsSurroundingWhitespace() {
        Dimension d = Dimension.parse("  12px  ");
        assertNotNull(d);
        assertEquals(12f, d.value(), 0.0001f);
        assertEquals(Dimension.Unit.PX, d.unit());
    }

    @Test
    public void parseNullReturnsNull() {
        assertNull(Dimension.parse(null));
    }

    @Test
    public void parseEmptyReturnsNull() {
        assertNull(Dimension.parse(""));
        assertNull(Dimension.parse("   "));
    }

    @Test
    public void parseInvalidUnitReturnsNull() {
        assertNull(Dimension.parse("12pt"));
        assertNull(Dimension.parse("12cm"));
        assertNull(Dimension.parse("abc"));
    }

    @Test
    public void parseLoneSignReturnsNull() {
        assertNull(Dimension.parse("+"));
        assertNull(Dimension.parse("-"));
        assertNull(Dimension.parse("."));
    }

    // ---- resolveToPx ----

    @Test
    public void resolvePxReturnsValueAsIs() {
        Dimension d = Dimension.parse("12px");
        ResolveContext c = ctx(100f, 14f, 16f, 960, 640);
        assertEquals(12f, d.resolveToPx(c, 0f), 0.0001f);
    }

    @Test
    public void resolvePercentUsesPercentBase() {
        Dimension d = Dimension.parse("50%");
        ResolveContext c = ctx(200f, 14f, 16f, 960, 640);
        // 50% of 200 = 100
        assertEquals(100f, d.resolveToPx(c, 0f), 0.0001f);
    }

    @Test
    public void resolveEmUsesEmBase() {
        Dimension d = Dimension.parse("1.5em");
        ResolveContext c = ctx(100f, 14f, 16f, 960, 640);
        // 1.5 * emBase(14) = 21
        assertEquals(21f, d.resolveToPx(c, 0f), 0.0001f);
    }

    @Test
    public void resolveRemUsesRemBase() {
        Dimension d = Dimension.parse("2rem");
        ResolveContext c = ctx(100f, 14f, 16f, 960, 640);
        // 2 * remBase(16) = 32
        assertEquals(32f, d.resolveToPx(c, 0f), 0.0001f);
    }

    @Test
    public void resolveVwUsesViewportWidth() {
        Dimension d = Dimension.parse("50vw");
        ResolveContext c = ctx(100f, 14f, 16f, 960, 640);
        // 50% of 960 = 480
        assertEquals(480f, d.resolveToPx(c, 0f), 0.0001f);
    }

    @Test
    public void resolveVhUsesViewportHeight() {
        Dimension d = Dimension.parse("25vh");
        ResolveContext c = ctx(100f, 14f, 16f, 960, 640);
        // 25% of 640 = 160
        assertEquals(160f, d.resolveToPx(c, 0f), 0.0001f);
    }

    @Test
    public void resolveAutoReturnsAutoFallback() {
        Dimension d = Dimension.parse("auto");
        ResolveContext c = ctx(100f, 14f, 16f, 960, 640);
        assertEquals(42f, d.resolveToPx(c, 42f), 0.0001f);
        assertEquals(0f, d.resolveToPx(c, 0f), 0.0001f);
    }

    @Test
    public void resolveNumberReturnsValueAsIs() {
        Dimension d = Dimension.parse("1.5");
        ResolveContext c = ctx(100f, 14f, 16f, 960, 640);
        assertEquals(1.5f, d.resolveToPx(c, 0f), 0.0001f);
    }

    @Test
    public void resolveZeroPxIsZero() {
        Dimension d = Dimension.parse("0");
        ResolveContext c = ctx(100f, 14f, 16f, 960, 640);
        assertEquals(0f, d.resolveToPx(c, 0f), 0.0001f);
    }

    // ---- factory ----

    @Test
    public void factoryPxAndZeroAndAuto() {
        assertEquals(Dimension.Unit.PX, Dimension.px(5f).unit());
        assertEquals(5f, Dimension.px(5f).value(), 0.0001f);
        assertEquals(Dimension.Unit.PX, Dimension.zero().unit());
        assertEquals(0f, Dimension.zero().value(), 0.0001f);
        assertEquals(Dimension.Unit.AUTO, Dimension.auto().unit());
        assertTrue(Dimension.auto().isAuto());
    }

    // ---- equals / hashCode / toString ----

    @Test
    public void equalsByValueAndUnit() {
        Dimension a = Dimension.parse("12px");
        Dimension b = Dimension.parse("12px");
        Dimension c = Dimension.parse("13px");
        Dimension d = Dimension.parse("12em");
        assertTrue(a.equals(b));
        assertFalse(a.equals(c));
        assertFalse(a.equals(d));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void toStringRoundtrips() {
        assertEquals("12.0px", Dimension.parse("12px").toString());
        assertEquals("50.0%", Dimension.parse("50%").toString());
        assertEquals("1.5em", Dimension.parse("1.5em").toString());
        assertEquals("1.2rem", Dimension.parse("1.2rem").toString());
        assertEquals("100.0vw", Dimension.parse("100vw").toString());
        assertEquals("50.0vh", Dimension.parse("50vh").toString());
        assertEquals("auto", Dimension.parse("auto").toString());
        assertEquals("1.5", Dimension.parse("1.5").toString());
    }
}
