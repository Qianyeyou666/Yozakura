package gq.yozakura.ui.engine.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ClipStateTracker 契约测试：增量 clip 状态跟踪的语义正确性。
 *
 * <p>对应优化点：applyClip 每个 op 都无条件调用 glEnable/glScissor，
 * 在 ClickGUI 同 clip 下连续多个 rect/text op 是冗余 GL 同步点。
 * tracker 应仅在状态实际变化时返回 true，让 renderer 跳过冗余 GL 调用。
 */
public class ClipStateTrackerTest {

    @Test
    public void firstUpdateFromUnknownAlwaysReturnsTrue() {
        ClipStateTracker tracker = new ClipStateTracker();
        // 初始状态为 UNKNOWN，首次 update 必然返回 true（强制写入）
        assertTrue(tracker.update(null));
        assertTrue(tracker.update(new ClipRect(0, 0, 100, 50)));
    }

    @Test
    public void sameClipReturnsFalseAfterFirstWrite() {
        ClipStateTracker tracker = new ClipStateTracker();
        ClipRect clip = new ClipRect(10, 20, 100, 50);

        assertTrue(tracker.update(clip));
        // 同一矩形（bit-equal）-> 未变化
        assertFalse(tracker.update(clip));
        assertFalse(tracker.update(new ClipRect(10, 20, 100, 50)));
    }

    @Test
    public void differentClipReturnsTrue() {
        ClipStateTracker tracker = new ClipStateTracker();
        ClipRect first = new ClipRect(0, 0, 100, 50);
        ClipRect second = new ClipRect(0, 0, 100, 60); // height 不同

        assertTrue(tracker.update(first));
        assertTrue(tracker.update(second));
        assertFalse(tracker.update(second));
    }

    @Test
    public void nullThenNonNullTransitionsReturnTrue() {
        ClipStateTracker tracker = new ClipStateTracker();

        assertTrue(tracker.update(null));       // UNKNOWN -> DISABLED
        assertFalse(tracker.update(null));      // DISABLED -> DISABLED，未变

        ClipRect clip = new ClipRect(0, 0, 10, 10);
        assertTrue(tracker.update(clip));        // DISABLED -> ENABLED
        assertFalse(tracker.update(clip));      // ENABLED 同值 -> 未变

        assertTrue(tracker.update(null));       // ENABLED -> DISABLED
        assertFalse(tracker.update(null));
    }

    @Test
    public void resetForcesNextUpdateToReturnTrue() {
        ClipStateTracker tracker = new ClipStateTracker();
        ClipRect clip = new ClipRect(0, 0, 10, 10);

        assertTrue(tracker.update(clip));
        assertFalse(tracker.update(clip));

        tracker.reset();
        // reset 后即使值相同，也强制写入（render 入口清零到未知状态）
        assertTrue(tracker.update(clip));
        assertFalse(tracker.update(clip));
    }

    @Test
    public void isEnabledReflectsCurrentState() {
        ClipStateTracker tracker = new ClipStateTracker();
        // UNKNOWN 状态不算 enabled
        assertFalse(tracker.isEnabled());

        tracker.update(new ClipRect(0, 0, 10, 10));
        assertTrue(tracker.isEnabled());

        tracker.update(null);
        assertFalse(tracker.isEnabled());
    }

    @Test
    public void floatBitsComparisonAvoidsFalsePositive() {
        // -0.0f 和 0.0f 在 == 比较中相等，但 Float.floatToIntBits 不同。
        // tracker 用 bits 比较应能区分（避免浮点误差积累导致的 false negative）。
        ClipStateTracker tracker = new ClipStateTracker();
        // ClipRect 不允许 width < 0；用 x 坐标区分
        ClipRect a = new ClipRect(0.0F, 0, 10, 10);
        ClipRect b = new ClipRect(-0.0F, 0, 10, 10);
        // 0.0f 和 -0.0f bits 不同，所以视为不同 clip
        assertTrue(tracker.update(a));
        assertTrue(tracker.update(b));
        assertFalse(tracker.update(b));
    }
}
