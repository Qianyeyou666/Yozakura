package gq.yozakura.ui.engine.minecraft;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 阶段 4 切片 4.1：ViewportTransform 契约测试。
 *
 * <p>验证契约（AGENTS.md "Input and Coordinates"）：
 * <ul>
 *   <li>渲染/布局/命中测试共享一个逻辑坐标空间</li>
 *   <li>Minecraft scaled 坐标 ↔ 物理像素 只在 host/viewport 层转换</li>
 *   <li>支持整数 GUI Scale（1/2/3，MC 1.8.9 标准）</li>
 *   <li>支持非整数 GUI Scale（Forge mod 扩展）</li>
 *   <li>fbW/fbH 不能被 scale 整除时取 floor（MC ScaledResolution 行为）</li>
 *   <li>整数 scale 下，整数物理像素点 round-trip 严格一致</li>
 *   <li>非整数 scale 下，round-trip 误差 < 1 物理像素</li>
 *   <li>alignToPhysicalPixel 消除亚像素（文字/边线对齐到物理像素边界）</li>
 *   <li>非法 scale / fb 尺寸抛 IllegalArgumentException</li>
 * </ul>
 *
 * <p>本引擎约定：逻辑坐标 = MC scaled 坐标。
 * 因此 logicalToPhysical(x) = x * scale; physicalToLogical(p) = p / scale。
 */
public class ViewportTransformTest {

    private static final float EPS = 0.0001f;

    // ---- 整数 GUI Scale ----

    @Test
    public void scaleOneIsIdentity() {
        ViewportTransform t = new ViewportTransform(1f, 100, 100);
        assertEquals(100, t.logicalWidth());
        assertEquals(100, t.logicalHeight());
        assertEquals(50f, t.physicalToLogicalX(50f), EPS);
        assertEquals(50f, t.physicalToLogicalY(50f), EPS);
        assertEquals(50f, t.logicalToPhysicalX(50f), EPS);
        assertEquals(50f, t.logicalToPhysicalY(50f), EPS);
    }

    @Test
    public void scaleTwoHalvesCoordinates() {
        ViewportTransform t = new ViewportTransform(2f, 1920, 1080);
        assertEquals(960, t.logicalWidth());
        assertEquals(540, t.logicalHeight());
        // 物理 (100,200) -> 逻辑 (50,100)
        assertEquals(50f, t.physicalToLogicalX(100f), EPS);
        assertEquals(100f, t.physicalToLogicalY(200f), EPS);
        // 逻辑 (50,100) -> 物理 (100,200)
        assertEquals(100f, t.logicalToPhysicalX(50f), EPS);
        assertEquals(200f, t.logicalToPhysicalY(100f), EPS);
    }

    @Test
    public void scaleThreeDividesByThree() {
        ViewportTransform t = new ViewportTransform(3f, 150, 90);
        assertEquals(50, t.logicalWidth());
        assertEquals(30, t.logicalHeight());
        assertEquals(30f, t.physicalToLogicalX(90f), EPS);
        assertEquals(20f, t.physicalToLogicalY(60f), EPS);
    }

    // ---- 非整数 GUI Scale ----

    @Test
    public void nonIntegerScaleOnePointFiveSupported() {
        ViewportTransform t = new ViewportTransform(1.5f, 1920, 1080);
        // 1920/1.5 = 1280, 1080/1.5 = 720
        assertEquals(1280, t.logicalWidth());
        assertEquals(720, t.logicalHeight());
        // 物理 (90, 60) -> 逻辑 (60, 40)
        assertEquals(60f, t.physicalToLogicalX(90f), EPS);
        assertEquals(40f, t.physicalToLogicalY(60f), EPS);
        // 逻辑 (60, 40) -> 物理 (90, 60)
        assertEquals(90f, t.logicalToPhysicalX(60f), EPS);
        assertEquals(60f, t.logicalToPhysicalY(40f), EPS);
    }

    @Test
    public void nonIntegerScaleRoundTripsWithinOnePixel() {
        // 非整数 scale 下，整数物理像素 round-trip 误差应 < 1 物理像素
        ViewportTransform t = new ViewportTransform(1.5f, 1920, 1080);
        for (int px = 0; px <= 1920; px += 17) {
            for (int py = 0; py <= 1080; py += 23) {
                float lx = t.physicalToLogicalX(px);
                float ly = t.physicalToLogicalY(py);
                float rx = t.logicalToPhysicalX(lx);
                float ry = t.logicalToPhysicalY(ly);
                assertTrue("x round-trip drift at px=" + px + ": " + Math.abs(rx - px),
                        Math.abs(rx - px) < 1f);
                assertTrue("y round-trip drift at py=" + py + ": " + Math.abs(ry - py),
                        Math.abs(ry - py) < 1f);
            }
        }
    }

    // ---- floor 派生 ----

    @Test
    public void logicalWidthFlooredWhenNotDivisible() {
        // fbW=1921, scale=2: 1921/2 = 960.5 → floor = 960（MC ScaledResolution 行为）
        ViewportTransform t = new ViewportTransform(2f, 1921, 1081);
        assertEquals(960, t.logicalWidth());
        assertEquals(540, t.logicalHeight());
    }

    @Test
    public void nonIntegerScaleFloorsLogicalDimensions() {
        // fbW=1921, scale=1.5: 1921/1.5 = 1280.666 → floor = 1280
        ViewportTransform t = new ViewportTransform(1.5f, 1921, 1081);
        assertEquals(1280, t.logicalWidth());
        assertEquals(720, t.logicalHeight());
    }

    // ---- round-trip 严格性（整数 scale）----

    @Test
    public void integerScaleRoundTripsExactlyAtIntegerPhysicalPoints() {
        ViewportTransform t = new ViewportTransform(2f, 1920, 1080);
        for (int px = 0; px <= 1920; px += 31) {
            for (int py = 0; py <= 1080; py += 41) {
                float lx = t.physicalToLogicalX(px);
                float ly = t.physicalToLogicalY(py);
                assertEquals("px=" + px, (float) px, t.logicalToPhysicalX(lx), EPS);
                assertEquals("py=" + py, (float) py, t.logicalToPhysicalY(ly), EPS);
            }
        }
    }

    // ---- 亚像素对齐（文字/边线对齐到物理像素边界）----

    @Test
    public void alignToPhysicalPixelSnapsToHalfPixelAtScaleTwo() {
        // scale=2: 每物理像素 = 0.5 逻辑像素，对齐到 0.5 的倍数
        ViewportTransform t = new ViewportTransform(2f, 1920, 1080);
        // 30.3 * 2 = 60.6 → floor 60 → 60/2 = 30.0
        assertEquals(30.0f, t.alignToPhysicalPixel(30.3f), EPS);
        // 30.6 * 2 = 61.2 → floor 61 → 61/2 = 30.5
        assertEquals(30.5f, t.alignToPhysicalPixel(30.6f), EPS);
        // 30.0 * 2 = 60.0 → floor 60 → 60/2 = 30.0（已对齐不变）
        assertEquals(30.0f, t.alignToPhysicalPixel(30.0f), EPS);
    }

    @Test
    public void alignToPhysicalPixelSnapsToIntegerAtScaleOne() {
        ViewportTransform t = new ViewportTransform(1f, 100, 100);
        // scale=1: 物理像素 = 逻辑像素整数
        assertEquals(30.0f, t.alignToPhysicalPixel(30.7f), EPS);
        assertEquals(30.0f, t.alignToPhysicalPixel(30.0f), EPS);
    }

    @Test
    public void alignToPhysicalPixelHandlesNegativeCoordinates() {
        ViewportTransform t = new ViewportTransform(2f, 1920, 1080);
        // -0.3 * 2 = -0.6 → floor -1 → -1/2 = -0.5
        assertEquals(-0.5f, t.alignToPhysicalPixel(-0.3f), EPS);
    }

    // ---- getter ----

    @Test
    public void gettersReturnConstructorValues() {
        ViewportTransform t = new ViewportTransform(2.5f, 1920, 1080);
        assertEquals(2.5f, t.scale(), EPS);
        assertEquals(1920, t.framebufferWidth());
        assertEquals(1080, t.framebufferHeight());
    }

    // ---- 非法参数 ----

    @Test
    public void zeroScaleThrows() {
        try {
            new ViewportTransform(0f, 100, 100);
            fail("expected IllegalArgumentException for scale=0");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void negativeScaleThrows() {
        try {
            new ViewportTransform(-1f, 100, 100);
            fail("expected IllegalArgumentException for scale<0");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void zeroFramebufferWidthThrows() {
        try {
            new ViewportTransform(2f, 0, 100);
            fail("expected IllegalArgumentException for fbW=0");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void negativeFramebufferHeightThrows() {
        try {
            new ViewportTransform(2f, 100, -1);
            fail("expected IllegalArgumentException for fbH<0");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void nanScaleThrows() {
        try {
            new ViewportTransform(Float.NaN, 100, 100);
            fail("expected IllegalArgumentException for scale=NaN");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
