package gq.yozakura.ui.engine.input;

import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 阶段 4 切片 4.5：PointerCapture 状态对象测试。
 *
 * <p>验证契约（AGENTS.md）：
 * "Support pointer capture for sliders, dragging, resizing and scroll gestures.
 *  A captured pointer continues receiving move/up events outside the original element."
 *
 * <p>PointerCapture 是纯状态对象，由 InputDispatcher（4.5b）查询以决定事件路由。
 * 单指针模型：同时只允许一个捕获。
 */
public class PointerCaptureTest {

    private static ElementNode el(String tag) {
        return ElementNode.create(tag);
    }

    @Test
    public void initiallyNotCaptured() {
        PointerCapture c = new PointerCapture();
        assertFalse(c.isCaptured());
        assertNull(c.capturedElement());
        assertNull(c.capturedButton());
    }

    @Test
    public void captureStoresElementButtonAndOrigin() {
        PointerCapture c = new PointerCapture();
        ElementNode slider = el("div");
        c.capture(slider, PointerButton.LEFT, 50f, 60f, 1000L);
        assertTrue(c.isCaptured());
        assertSame(slider, c.capturedElement());
        assertSame(PointerButton.LEFT, c.capturedButton());
        assertEquals(50f, c.startX(), 0.0001f);
        assertEquals(60f, c.startY(), 0.0001f);
        assertEquals(1000L, c.startTime());
    }

    @Test
    public void releaseClearsCapture() {
        PointerCapture c = new PointerCapture();
        ElementNode slider = el("div");
        c.capture(slider, PointerButton.LEFT, 0, 0, 0);
        c.release();
        assertFalse(c.isCaptured());
        assertNull(c.capturedElement());
        assertNull(c.capturedButton());
    }

    @Test
    public void recaptureReplacesPrevious() {
        // 单指针模型：新 capture 覆盖旧 capture
        PointerCapture c = new PointerCapture();
        ElementNode a = el("div");
        ElementNode b = el("div");
        c.capture(a, PointerButton.LEFT, 0, 0, 0);
        c.capture(b, PointerButton.LEFT, 10, 20, 100);
        assertSame(b, c.capturedElement());
        assertEquals(10f, c.startX(), 0.0001f);
    }

    @Test
    public void rightButtonCaptureSupported() {
        // 右键拖动场景（如右键调整 slider）
        PointerCapture c = new PointerCapture();
        ElementNode slider = el("div");
        c.capture(slider, PointerButton.RIGHT, 0, 0, 0);
        assertSame(PointerButton.RIGHT, c.capturedButton());
    }

    @Test
    public void dragDeltaComputesDisplacementFromStart() {
        PointerCapture c = new PointerCapture();
        ElementNode slider = el("div");
        c.capture(slider, PointerButton.LEFT, 100f, 200f, 0);
        // 拖到 (130, 190)
        assertEquals(30f, c.dragDeltaX(130f), 0.0001f);
        assertEquals(-10f, c.dragDeltaY(190f), 0.0001f);
    }

    @Test
    public void dragDeltaWhenNotCapturedIsZero() {
        PointerCapture c = new PointerCapture();
        assertEquals(0f, c.dragDeltaX(100f), 0.0001f);
        assertEquals(0f, c.dragDeltaY(100f), 0.0001f);
    }

    @Test
    public void distanceFromStartEuclidean() {
        PointerCapture c = new PointerCapture();
        ElementNode slider = el("div");
        c.capture(slider, PointerButton.LEFT, 0, 0, 0);
        // 移动到 (3, 4) → distance = 5
        assertEquals(5f, c.distanceFromStart(3f, 4f), 0.0001f);
    }

    // ---- 非法参数 ----

    @Test
    public void captureRejectsNullElement() {
        try {
            new PointerCapture().capture(null, PointerButton.LEFT, 0, 0, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void captureRejectsNoneButton() {
        try {
            new PointerCapture().capture(el("div"), PointerButton.NONE, 0, 0, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void captureRejectsNegativeTimestamp() {
        try {
            new PointerCapture().capture(el("div"), PointerButton.LEFT, 0, 0, -1L);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
