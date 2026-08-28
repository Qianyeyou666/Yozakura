package gq.yozakura.ui.engine.input;

import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 4 切片 4.5b：InputDispatcher 协调器测试。
 *
 * <p>验证契约（AGENTS.md）：
 * "A captured pointer continues receiving move/up events outside the original element."
 *
 * <p>InputDispatcher 协调 InteractionState + PointerCapture + HitTester：
 * <ul>
 *   <li>捕获期间 MOVE/UP 路由到捕获元素（即使 hit 不同或为 null）</li>
 *   <li>同 button 的 UP 自动 release 捕获</li>
 *   <li>不同 button 的 UP 不 release</li>
 *   <li>捕获期间 hover 冻结（MOVE 不更新 hover）</li>
 *   <li>requestCapture 由元素主动调用（如 slider 收到 LEFT DOWN 时）</li>
 *   <li>右键拖动：requestCapture(RIGHT) → 右键 MOVE 路由到捕获元素</li>
 * </ul>
 */
public class InputDispatcherTest {

    private static ElementNode el(String tag) {
        return ElementNode.create(tag);
    }

    private static PointerEvent leftDown(float x, float y) {
        return PointerEvent.down(x, y, PointerButton.LEFT, ModifierKeys.none(), 1, 100L);
    }

    private static PointerEvent leftMove(float x, float y) {
        return PointerEvent.move(x, y, ModifierKeys.none(), 200L);
    }

    private static PointerEvent leftUp(float x, float y) {
        return PointerEvent.up(x, y, PointerButton.LEFT, ModifierKeys.none(), 1, 300L);
    }

    private static PointerEvent rightMove(float x, float y) {
        return PointerEvent.move(x, y, ModifierKeys.none(), 200L);
    }

    private static PointerEvent rightUp(float x, float y) {
        return PointerEvent.up(x, y, PointerButton.RIGHT, ModifierKeys.none(), 1, 300L);
    }

    // ---- 未捕获：正常路由 ----

    @Test
    public void withoutCaptureMoveRoutesToHit() {
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode a = el("div");
        d.dispatch(leftMove(10, 10), a);
        assertSame(a, state.hover());
    }

    @Test
    public void withoutCaptureUpDoesNotAffectCapture() {
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        d.dispatch(leftUp(10, 10), el("div"));
        assertFalse(d.capture().isCaptured());
    }

    // ---- 捕获后路由 ----

    @Test
    public void capturedMoveRoutesToCapturedElementEvenIfHitDiffers() {
        // 关键测试：捕获后 MOVE 路由到捕获元素，而非 hit
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode slider = el("div");
        ElementNode other = el("div");
        // 先 hover slider
        d.dispatch(leftMove(10, 10), slider);
        // 捕获 slider（模拟 slider 收到 LEFT DOWN 时主动请求）
        d.requestCapture(slider, PointerButton.LEFT, 10, 10, 100L);
        // MOVE 到 other 元素上 → 应仍路由到 slider
        d.dispatch(leftMove(50, 50), other);
        // hover 应冻结在 slider（不更新为 other）
        assertSame(slider, state.hover());
        assertTrue(d.capture().isCaptured());
    }

    @Test
    public void capturedMoveRoutesToCapturedElementEvenIfHitIsNull() {
        // 关键测试：捕获后 MOVE 到元素外（hit=null）仍路由到捕获元素
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode slider = el("div");
        d.dispatch(leftMove(10, 10), slider);
        d.requestCapture(slider, PointerButton.LEFT, 10, 10, 100L);
        // MOVE 到 hit=null（元素外）→ hover 不应被清空（仍为 slider）
        d.dispatch(leftMove(500, 500), null);
        assertSame(slider, state.hover());
        assertTrue(d.capture().isCaptured());
    }

    @Test
    public void sameButtonUpReleasesCapture() {
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode slider = el("div");
        d.dispatch(leftMove(10, 10), slider);
        d.requestCapture(slider, PointerButton.LEFT, 10, 10, 100L);
        assertTrue(d.capture().isCaptured());

        d.dispatch(leftUp(50, 50), null);  // UP 在元素外
        assertFalse(d.capture().isCaptured());
        // UP 后 active 应被清除
        assertNull(state.active());
    }

    @Test
    public void differentButtonUpDoesNotReleaseCapture() {
        // 捕获 LEFT，RIGHT UP 不应 release
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode slider = el("div");
        d.dispatch(leftMove(10, 10), slider);
        d.requestCapture(slider, PointerButton.LEFT, 10, 10, 100L);

        d.dispatch(rightUp(50, 50), null);
        assertTrue(d.capture().isCaptured());
    }

    // ---- 捕获期间 hover 冻结 ----

    @Test
    public void captureFreezesHoverDuringMove() {
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode slider = el("div");
        ElementNode other = el("div");
        d.dispatch(leftMove(10, 10), slider);
        d.requestCapture(slider, PointerButton.LEFT, 10, 10, 100L);

        // MOVE 到 other → hover 应保持 slider（不更新）
        d.dispatch(leftMove(50, 50), other);
        assertSame(slider, state.hover());
    }

    // ---- 释放后恢复正常路由 ----

    @Test
    public void afterReleaseMoveRoutesToHitAgain() {
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode slider = el("div");
        ElementNode other = el("div");
        d.dispatch(leftMove(10, 10), slider);
        d.requestCapture(slider, PointerButton.LEFT, 10, 10, 100L);
        d.dispatch(leftUp(50, 50), null);  // release
        d.dispatch(leftMove(50, 50), other);  // 恢复正常路由
        assertSame(other, state.hover());
    }

    // ---- 右键拖动 ----

    @Test
    public void rightButtonCaptureRoutesRightMoveToCaptured() {
        // 右键拖动场景（如右键调整 slider）
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode slider = el("div");
        d.dispatch(rightMove(10, 10), slider);  // 右键前先 MOVE 建立.hover
        // 右键捕获
        d.requestCapture(slider, PointerButton.RIGHT, 10, 10, 100L);
        // 右键 MOVE（实际上 MOVE 事件 button=NONE，但捕获中应路由到 slider）
        d.dispatch(rightMove(50, 50), null);
        assertSame(slider, state.hover());
        assertTrue(d.capture().isCaptured());
        assertSame(PointerButton.RIGHT, d.capture().capturedButton());
    }

    @Test
    public void rightButtonUpReleasesRightCapture() {
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode slider = el("div");
        d.requestCapture(slider, PointerButton.RIGHT, 10, 10, 100L);
        d.dispatch(rightUp(50, 50), null);
        assertFalse(d.capture().isCaptured());
    }

    // ---- DOWN 时不自动捕获 ----

    @Test
    public void downDoesNotAutomaticallyCapture() {
        // 捕获由元素主动 requestCapture（如 slider 在 onMouseDown 中调用）
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode slider = el("div");
        d.dispatch(leftDown(10, 10), slider);
        assertFalse(d.capture().isCaptured());  // 未自动捕获
    }

    // ---- clearAll 同步释放捕获 ----

    @Test
    public void clearAllReleasesCapture() {
        // 关闭 UI 时清理捕获（避免悬空捕获）
        InteractionState state = new InteractionState();
        InputDispatcher d = new InputDispatcher(state);
        ElementNode slider = el("div");
        d.requestCapture(slider, PointerButton.LEFT, 0, 0, 0);
        d.clearAll();
        assertFalse(d.capture().isCaptured());
        assertNull(state.hover());
        assertNull(state.active());
        assertNull(state.focus());
    }

    // ---- 非法参数 ----

    @Test(expected = IllegalArgumentException.class)
    public void dispatchRejectsNullEvent() {
        new InputDispatcher(new InteractionState()).dispatch(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNullState() {
        new InputDispatcher(null);
    }
}
