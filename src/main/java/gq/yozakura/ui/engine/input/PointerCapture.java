package gq.yozakura.ui.engine.input;

import gq.yozakura.ui.engine.dom.ElementNode;

/**
 * 指针捕获状态：记录当前被捕获的元素、按钮与起始坐标。
 *
 * <p>AGENTS.md 契约："Support pointer capture for sliders, dragging, resizing
 * and scroll gestures. A captured pointer continues receiving move/up events
 * outside the original element."
 *
 * <p>单指针模型：同时只允许一个捕获。新 capture 覆盖旧 capture。
 * 由 {@link InputDispatcher} 查询本对象以决定事件路由目标。
 *
 * <p>提供 {@link #dragDeltaX(float)} / {@link #dragDeltaY(float)} / {@link #distanceFromStart(float, float)}
 * 供 slider / 拖动 / 缩放手势计算位移。
 *
 * <p>不可变快照语义：capture() 后状态固定，release() 后清零。
 * 线程模型：单线程（UI 线程）。非线程安全。
 */
public final class PointerCapture {

    private ElementNode element;
    private PointerButton button;
    private float startX;
    private float startY;
    private long startTime;

    public PointerCapture() {
    }

    /**
     * 捕获指针。覆盖前一次捕获（单指针模型）。
     *
     * @param element  被捕获元素（非 null）
     * @param button   触发捕获的按钮（非 NONE）
     * @param startX   捕获起始逻辑 X
     * @param startY   捕获起始逻辑 Y
     * @param startTime 捕获时刻（单调时钟毫秒，非负）
     */
    public void capture(ElementNode element, PointerButton button,
                         float startX, float startY, long startTime) {
        if (element == null) {
            throw new IllegalArgumentException("element must not be null");
        }
        if (button == null || button.isNone()) {
            throw new IllegalArgumentException("button must not be NONE: " + button);
        }
        if (startTime < 0L) {
            throw new IllegalArgumentException("startTime must not be negative: " + startTime);
        }
        this.element = element;
        this.button = button;
        this.startX = startX;
        this.startY = startY;
        this.startTime = startTime;
    }

    /** 释放捕获；幂等。 */
    public void release() {
        element = null;
        button = null;
        startX = 0f;
        startY = 0f;
        startTime = 0L;
    }

    public boolean isCaptured() {
        return element != null;
    }

    public ElementNode capturedElement() {
        return element;
    }

    public PointerButton capturedButton() {
        return button;
    }

    public float startX() {
        return startX;
    }

    public float startY() {
        return startY;
    }

    public long startTime() {
        return startTime;
    }

    /** 当前 X 相对捕获起始的位移；未捕获返回 0。 */
    public float dragDeltaX(float currentX) {
        return isCaptured() ? currentX - startX : 0f;
    }

    /** 当前 Y 相对捕获起始的位移；未捕获返回 0。 */
    public float dragDeltaY(float currentY) {
        return isCaptured() ? currentY - startY : 0f;
    }

    /** 当前点相对捕获起始的欧氏距离；未捕获返回 0。 */
    public float distanceFromStart(float currentX, float currentY) {
        if (!isCaptured()) {
            return 0f;
        }
        float dx = currentX - startX;
        float dy = currentY - startY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
