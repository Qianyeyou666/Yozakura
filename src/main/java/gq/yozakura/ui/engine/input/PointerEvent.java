package gq.yozakura.ui.engine.input;

/**
 * 指针事件值对象：携带类型、逻辑坐标、显式按钮、修饰键、点击计数、滚轮增量与时间戳。
 *
 * <p>不可变。坐标为逻辑像素（与 LayoutBox / 命中测试同一空间）。
 *
 * <p>AGENTS.md 强制契约："Pointer events carry explicit values for left, right
 * and middle buttons. Do not treat every click as a left click."
 *
 * <p>因此 DOWN/UP 事件必须显式携带非 NONE 的 button；MOVE/WHEEL 事件 button 强制为 NONE。
 * 这通过便捷工厂方法 {@link #down} / {@link #up} / {@link #move} / {@link #wheel}
 * 在构造时校验，避免下游误把右键当左键处理。
 *
 * <p>时间戳为单调时钟毫秒（与动画时钟一致，阶段 5）。
 */
public final class PointerEvent {

    private final PointerEventType type;
    private final float x;
    private final float y;
    private final PointerButton button;
    private final ModifierKeys modifiers;
    private final int clickCount;
    private final float wheelDelta;
    private final long timestamp;

    private PointerEvent(PointerEventType type, float x, float y,
                          PointerButton button, ModifierKeys modifiers,
                          int clickCount, float wheelDelta, long timestamp) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (button == null) {
            throw new IllegalArgumentException("button must not be null");
        }
        if (modifiers == null) {
            throw new IllegalArgumentException("modifiers must not be null");
        }
        if (Float.isNaN(x) || Float.isInfinite(x)) {
            throw new IllegalArgumentException("x must be finite: " + x);
        }
        if (Float.isNaN(y) || Float.isInfinite(y)) {
            throw new IllegalArgumentException("y must be finite: " + y);
        }
        if (Float.isNaN(wheelDelta) || Float.isInfinite(wheelDelta)) {
            throw new IllegalArgumentException("wheelDelta must be finite: " + wheelDelta);
        }
        if (timestamp < 0L) {
            throw new IllegalArgumentException("timestamp must not be negative: " + timestamp);
        }
        this.type = type;
        this.x = x;
        this.y = y;
        this.button = button;
        this.modifiers = modifiers;
        this.clickCount = clickCount;
        this.wheelDelta = wheelDelta;
        this.timestamp = timestamp;
    }

    /**
     * DOWN 事件：必须显式非 NONE button；clickCount >= 1；wheelDelta=0。
     * move/wheel 路径不应用此构造器，避免误传 NONE 当 LEFT。
     */
    public static PointerEvent down(float x, float y, PointerButton button,
                                     ModifierKeys modifiers, int clickCount, long timestamp) {
        if (button == null || button.isNone()) {
            throw new IllegalArgumentException("down event requires explicit non-NONE button: " + button);
        }
        if (clickCount <= 0) {
            throw new IllegalArgumentException("clickCount must be >= 1: " + clickCount);
        }
        return new PointerEvent(PointerEventType.DOWN, x, y, button, modifiers, clickCount, 0f, timestamp);
    }

    /** UP 事件：与 {@link #down} 同约束。 */
    public static PointerEvent up(float x, float y, PointerButton button,
                                   ModifierKeys modifiers, int clickCount, long timestamp) {
        if (button == null || button.isNone()) {
            throw new IllegalArgumentException("up event requires explicit non-NONE button: " + button);
        }
        if (clickCount <= 0) {
            throw new IllegalArgumentException("clickCount must be >= 1: " + clickCount);
        }
        return new PointerEvent(PointerEventType.UP, x, y, button, modifiers, clickCount, 0f, timestamp);
    }

    /** MOVE 事件：button 强制 NONE；clickCount=0；wheelDelta=0。 */
    public static PointerEvent move(float x, float y, ModifierKeys modifiers, long timestamp) {
        return new PointerEvent(PointerEventType.MOVE, x, y, PointerButton.NONE, modifiers, 0, 0f, timestamp);
    }

    /** WHEEL 事件：button 强制 NONE；delta 必须非 0（零增量无意义）；clickCount=0。 */
    public static PointerEvent wheel(float x, float y, float wheelDelta,
                                      ModifierKeys modifiers, long timestamp) {
        if (wheelDelta == 0f) {
            throw new IllegalArgumentException("wheel delta must not be zero");
        }
        return new PointerEvent(PointerEventType.WHEEL, x, y, PointerButton.NONE, modifiers, 0, wheelDelta, timestamp);
    }

    public PointerEventType type() { return type; }
    public float x() { return x; }
    public float y() { return y; }
    public PointerButton button() { return button; }
    public ModifierKeys modifiers() { return modifiers; }
    public int clickCount() { return clickCount; }
    public float wheelDelta() { return wheelDelta; }
    public long timestamp() { return timestamp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PointerEvent)) return false;
        PointerEvent e = (PointerEvent) o;
        return type == e.type
                && Float.floatToIntBits(x) == Float.floatToIntBits(e.x)
                && Float.floatToIntBits(y) == Float.floatToIntBits(e.y)
                && button == e.button
                && modifiers.equals(e.modifiers)
                && clickCount == e.clickCount
                && Float.floatToIntBits(wheelDelta) == Float.floatToIntBits(e.wheelDelta)
                && timestamp == e.timestamp;
    }

    @Override
    public int hashCode() {
        int r = type.hashCode();
        r = 31 * r + Float.floatToIntBits(x);
        r = 31 * r + Float.floatToIntBits(y);
        r = 31 * r + button.hashCode();
        r = 31 * r + modifiers.hashCode();
        r = 31 * r + clickCount;
        r = 31 * r + Float.floatToIntBits(wheelDelta);
        r = 31 * r + (int) (timestamp ^ (timestamp >>> 32));
        return r;
    }

    @Override
    public String toString() {
        return "PointerEvent{" + type + " (" + x + "," + y + ") btn=" + button
                + " mods=" + modifiers + " count=" + clickCount
                + " wheel=" + wheelDelta + " t=" + timestamp + "}";
    }
}
