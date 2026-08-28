package gq.yozakura.ui.engine.input;

/**
 * 鼠标按钮枚举：显式区分左/中/右键。
 *
 * <p>AGENTS.md 强制契约："Pointer events carry explicit values for left, right
 * and middle buttons. Do not treat every click as a left click."
 *
 * <p>{@link #NONE} 表示"无按钮按下"，用于 MOVE 与 WHEEL 事件——不能默认为 LEFT。
 *
 * <p>{@link #fromLwjgl(int)} 映射 LWJGL 2 Mouse / Minecraft 鼠标按钮：
 * 0=LEFT, 1=RIGHT, 2=MIDDLE；其它（含负数）→ NONE，不静默退化为 LEFT。
 */
public enum PointerButton {
    NONE,
    LEFT,
    RIGHT,
    MIDDLE;

    /** LWJGL Mouse button LEFT=0, RIGHT=1, MIDDLE=2；其它 → NONE。 */
    public static PointerButton fromLwjgl(int button) {
        switch (button) {
            case 0: return LEFT;
            case 1: return RIGHT;
            case 2: return MIDDLE;
            default: return NONE;
        }
    }

    /** Compatibility alias for older tests; numeric values match LWJGL 2 for these buttons. */
    public static PointerButton fromGlfw(int button) { return fromLwjgl(button); }

    public boolean isNone() { return this == NONE; }
    public boolean isLeft() { return this == LEFT; }
    public boolean isRight() { return this == RIGHT; }
    public boolean isMiddle() { return this == MIDDLE; }
}
