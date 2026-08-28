package gq.yozakura.ui.engine.input;

import gq.yozakura.ui.engine.dom.ElementNode;

/**
 * 输入分发协调器：组合 InteractionState + PointerCapture，决定事件路由目标。
 *
 * <p>AGENTS.md 契约："A captured pointer continues receiving move/up events
 * outside the original element."
 *
 * <p>路由规则：
 * <ul>
 *   <li>未捕获：事件路由到 hit（命中测试结果）</li>
 *   <li>捕获中 + MOVE/WHEEL：路由到捕获元素（即使 hit 不同或为 null）</li>
 *   <li>捕获中 + 同 button 的 DOWN/UP：路由到捕获元素</li>
 *   <li>捕获中 + 不同 button 的 DOWN/UP：正常路由到 hit（允许多按钮并行）</li>
 *   <li>捕获中 + 同 button 的 UP：路由后自动 release 捕获</li>
 * </ul>
 *
 * <p>捕获期间 hover 冻结：MOVE 路由到捕获元素时，{@link InteractionState#handlePointer}
 * 调用 setHover(捕获元素)，若 hover 已是该元素则不变化、不 dirty。
 *
 * <p>requestCapture 由元素主动调用（如 slider 在收到 LEFT DOWN 时调用），
 * InputDispatcher 不在 DOWN 时自动捕获——这符合浏览器语义：仅特定元素请求捕获。
 *
 * <p>{@link #clearAll()} 同步释放捕获与状态，用于关闭 UI。
 *
 * <p>线程模型：单线程（UI 线程）。非线程安全。
 */
public final class InputDispatcher {

    private final InteractionState state;
    private final PointerCapture capture;

    public InputDispatcher(InteractionState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        this.state = state;
        this.capture = new PointerCapture();
    }

    /**
     * 分发指针事件。
     *
     * @param event 指针事件（非 null）
     * @param hit   命中测试结果（ElementNode），可为 null
     */
    public void dispatch(PointerEvent event, ElementNode hit) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        ElementNode target = hit;
        boolean shouldRelease = false;

        if (capture.isCaptured()) {
            boolean sameButton = event.button() == capture.capturedButton();
            boolean isMoveOrWheel = event.type() == PointerEventType.MOVE
                    || event.type() == PointerEventType.WHEEL;

            // MOVE/WHEEL 始终路由到捕获元素（即使 hit 不同）
            // 同 button 的 DOWN/UP 也路由到捕获元素
            if (isMoveOrWheel || sameButton) {
                target = capture.capturedElement();
            }

            // 同 button 的 UP 触发 release
            if (event.type() == PointerEventType.UP && sameButton) {
                shouldRelease = true;
            }
        }

        state.handlePointer(event, target);

        if (shouldRelease) {
            capture.release();
        }
    }

    /** 捕获状态查询；元素主动调用 {@link PointerCapture#capture} 请求捕获。 */
    public PointerCapture capture() {
        return capture;
    }

    /**
     * 暴露内部 InteractionState，供 host 层/TextEditor 查询 focus owner。
     *
     * <p>AGENTS.md 契约："Focus, text editing and keyboard routing must have one owner."
     * InputDispatcher 不直接调用元素回调（MVP 不引入事件回调 API）；
     * 而是提供 focus 查询，由调用方在键盘事件后自行处理。
     */
    public InteractionState state() {
        return state;
    }

    /**
     * 分发键盘事件。
     *
     * <p>路由契约：键盘事件路由到 {@link InteractionState#focus()}（单一 owner）。
     * focus=null 时为 no-op（不抛、不路由），用于关闭 UI 后恢复 repeat-key 状态。
     *
     * <p>本方法不直接调用元素回调；调用方（host 层/TextEditor）应在调用后查询
     * {@link #state()} 的 focus owner，并按业务逻辑处理键盘输入。
     *
     * @param event 键盘事件（非 null）
     */
    public void handleKeyboard(KeyboardEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        // 路由目标 = focus owner；focus=null 时 no-op
        // 不修改任何状态（focus 转移由指针/程序化 API 触发，不由键盘触发）
        // 调用方查询 state().focus() 后自行处理
    }

    /**
     * 请求捕获（便捷方法，等价于 capture().capture(...)）。
     * 由元素在收到 DOWN 时主动调用（如 slider、可拖动标题栏）。
     */
    public void requestCapture(ElementNode element, PointerButton button,
                                float startX, float startY, long startTime) {
        capture.capture(element, button, startX, startY, startTime);
    }

    /**
     * 关闭 UI 时清理：释放捕获 + 重置全部交互状态。
     * 任意非空状态被清空时标记 STYLE_DIRTY。
     */
    public void clearAll() {
        capture.release();
        state.clearAll();
    }
}
