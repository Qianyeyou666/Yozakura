package gq.yozakura.ui.engine.input;

/**
 * 单行文本编辑器：由 {@link KeyboardEvent} 驱动的纯逻辑状态机。
 *
 * <p>AGENTS.md 契约："Focus, text editing and keyboard routing must have one owner."
 * TextEditor 不持有 focus 概念——host 层在键盘事件路由到 focus owner 后调用
 * {@link #handleKey(KeyboardEvent)}；本类只管 text 与 cursor。
 *
 * <p>支持操作（单行 MVP）：
 * <ul>
 *   <li>可打印字符插入：在 cursor 处插入，cursor 后移</li>
 *   <li>BACKSPACE：删除 cursor 前一字符，cursor 前移</li>
 *   <li>DELETE：删除 cursor 后一字符，cursor 不动</li>
 *   <li>LEFT / RIGHT：cursor 移动，边界 [0, length]</li>
 *   <li>HOME / END：cursor 移到首/尾</li>
 *   <li>ENTER / 换行：忽略（单行编辑器）</li>
 *   <li>其它控制键（无 character）：忽略</li>
 * </ul>
 *
 * <p>dirty 语义：仅 text 内容变化时标 dirty；cursor 移动不标 dirty。
 * 配合阶段 5 dirty-state 模型——host 层据 {@link #isDirty()} 决定是否重算
 * layout/paint。clearDirty 由外部在重建后调用。
 *
 * <p>maxLength 默认 {@link Integer#MAX_VALUE}（无限制）；超长插入被忽略。
 *
 * <p>线程模型：单线程（UI 线程）。非线程安全。
 */
public final class TextEditor {

    /** 默认无长度限制。 */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    private String text;
    private int cursor;
    private final int maxLength;
    private boolean dirty;

    /** 无长度限制的编辑器。 */
    public TextEditor() {
        this(UNLIMITED);
    }

    /**
     * 指定最大长度的编辑器。
     *
     * @param maxLength 最大字符数（&gt;= 0）；{@link #UNLIMITED} 表示无限制
     */
    public TextEditor(int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength must not be negative: " + maxLength);
        }
        this.text = "";
        this.cursor = 0;
        this.maxLength = maxLength;
        this.dirty = false;
    }

    /**
     * 处理键盘事件，更新 text/cursor。
     *
     * <p>不可打印控制键（无 character 且非已知控制键名）被忽略。
     *
     * @param event 键盘事件（非 null）
     */
    public void handleKey(KeyboardEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        // 优先处理控制键（按 key 名）
        String key = event.key();
        if (key != null) {
            switch (key) {
                case "BACKSPACE":
                    backspace();
                    return;
                case "DELETE":
                    deleteForward();
                    return;
                case "LEFT":
                    moveCursor(cursor - 1);
                    return;
                case "RIGHT":
                    moveCursor(cursor + 1);
                    return;
                case "HOME":
                    moveCursor(0);
                    return;
                case "END":
                    moveCursor(text.length());
                    return;
                case "ENTER":
                case "RETURN":
                    // 单行编辑器忽略 Enter
                    return;
                default:
                    // 其它控制键名（如 TAB、UP、DOWN）在 MVP 中忽略
                    // 若为可打印字符事件则继续走字符路径
                    if (!event.isCharacterInput()) {
                        return;
                    }
                    break;
            }
        }

        // 字符输入路径
        if (event.isCharacterInput()) {
            char c = event.character();
            // 拒绝换行（单行编辑器）
            if (c == '\n' || c == '\r') {
                return;
            }
            // 拒绝其它控制字符（ASCII < 0x20），保留可打印与扩展字符
            if (c < 0x20) {
                return;
            }
            insertChar(c);
        }
    }

    // ---- 显式状态控制 ----

    /** 设置 text；cursor 钳制到 [0, length]。 */
    public void setText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        // 拒绝包含换行的文本（单行编辑器）
        String normalized = text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
                ? text.replaceAll("[\\r\\n]", "")
                : text;
        this.text = normalized;
        clampCursor();
        this.dirty = true;
    }

    /** 设置 cursor；钳制到 [0, length]。 */
    public void setCursor(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("cursor must not be negative: " + position);
        }
        this.cursor = position;
        clampCursor();
    }

    /** 清空 text 与 cursor；标 dirty。 */
    public void clear() {
        this.text = "";
        this.cursor = 0;
        this.dirty = true;
    }

    // ---- 查询 ----

    public String text() { return text; }
    public int cursor() { return cursor; }
    public boolean isEmpty() { return text.isEmpty(); }
    public int maxLength() { return maxLength; }

    /** 自上次 clearDirty 后 text 是否变化（cursor 移动不算）。 */
    public boolean isDirty() { return dirty; }

    /** 外部在重建后清除 dirty 标记。 */
    public void clearDirty() { dirty = false; }

    // ---- 内部操作 ----

    private void insertChar(char c) {
        if (text.length() >= maxLength) {
            return;  // 超长忽略
        }
        text = text.substring(0, cursor) + c + text.substring(cursor);
        cursor++;
        dirty = true;
    }

    private void backspace() {
        if (cursor <= 0) {
            return;
        }
        text = text.substring(0, cursor - 1) + text.substring(cursor);
        cursor--;
        dirty = true;
    }

    private void deleteForward() {
        if (cursor >= text.length()) {
            return;
        }
        text = text.substring(0, cursor) + text.substring(cursor + 1);
        dirty = true;
    }

    private void moveCursor(int target) {
        cursor = target;
        clampCursor();
    }

    private void clampCursor() {
        int len = text.length();
        if (cursor < 0) cursor = 0;
        if (cursor > len) cursor = len;
    }

    @Override
    public String toString() {
        return "TextEditor{text='" + text + "', cursor=" + cursor
                + ", max=" + (maxLength == UNLIMITED ? "∞" : maxLength)
                + ", dirty=" + dirty + "}";
    }
}
