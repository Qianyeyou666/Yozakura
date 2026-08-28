package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.ClipPopCommand;
import gq.yozakura.ui.engine.paint.ClipPushCommand;
import gq.yozakura.ui.engine.paint.Color;
import gq.yozakura.ui.engine.paint.PaintCommandList;
import gq.yozakura.ui.engine.paint.RectBorderCommand;
import gq.yozakura.ui.engine.paint.RectFillCommand;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 3 切片 5：BorderRenderer 批处理契约测试。
 *
 * <p>验证契约：
 * <ul>
 *   <li>相同 (clip, color) 下的连续 RectBorder 合并为单个 BorderBatch</li>
 *   <li>不同 color → 不同批</li>
 *   <li>ClipPush/ClipPop 改变 clip → 立即刷出 pending</li>
 *   <li>RectFill 出现 → 立即刷出 pending（保证视觉顺序：border1 → fill → border2）</li>
 *   <li>finish() 刷出最后一批；幂等</li>
 *   <li>空列表 → 0 批</li>
 *   <li>每个 BorderBatch 持有共享的 color 与 clipRect</li>
 * </ul>
 */
public class BorderRendererTest {

    private static final class CapturingSink implements BorderBatchSink {
        final List<BorderBatch> batches = new ArrayList<BorderBatch>();

        @Override
        public void emit(BorderBatch batch) {
            batches.add(batch);
        }
    }

    private static RectBorderCommand border(float x, float y, float w, float h, Color c) {
        return new RectBorderCommand(x, y, w, h, 1, 1, 1, 1, c, 0f);
    }

    private static RectBorderCommand roundedBorder(float x, float y, float w, float h,
                                                    Color c, float radius) {
        return new RectBorderCommand(x, y, w, h, 2, 2, 2, 2, c, radius);
    }

    private static RectFillCommand fill(float x, float y, float w, float h) {
        return new RectFillCommand(x, y, w, h, Color.fromRgba(1f, 0f, 0f, 1f));
    }

    private static final Color RED = Color.fromRgba(1f, 0f, 0f, 1f);
    private static final Color GREEN = Color.fromRgba(0f, 1f, 0f, 1f);

    private static CapturingSink run(PaintCommandList list) {
        CapturingSink sink = new CapturingSink();
        BorderRenderer r = new BorderRenderer(sink);
        list.replay(r);
        r.finish();
        return sink;
    }

    @Test
    public void emptyListProducesNoBatches() {
        assertTrue(run(new PaintCommandList()).batches.isEmpty());
    }

    @Test
    public void singleBorderProducesOneBatch() {
        PaintCommandList list = new PaintCommandList();
        list.append(border(0, 0, 10, 10, RED));
        CapturingSink sink = run(list);
        assertEquals(1, sink.batches.size());
        assertEquals(1, sink.batches.get(0).borderCount());
        assertEquals(RED, sink.batches.get(0).color());
    }

    @Test
    public void consecutiveBordersWithSameColorAndClipAreBatched() {
        PaintCommandList list = new PaintCommandList();
        list.append(border(0, 0, 10, 10, RED));
        list.append(border(10, 0, 10, 10, RED));
        list.append(border(20, 0, 10, 10, RED));
        CapturingSink sink = run(list);
        assertEquals(1, sink.batches.size());
        assertEquals(3, sink.batches.get(0).borderCount());
    }

    @Test
    public void differentColorsProduceDifferentBatches() {
        PaintCommandList list = new PaintCommandList();
        list.append(border(0, 0, 10, 10, RED));
        list.append(border(10, 0, 10, 10, GREEN));
        CapturingSink sink = run(list);
        assertEquals(2, sink.batches.size());
        assertEquals(RED, sink.batches.get(0).color());
        assertEquals(GREEN, sink.batches.get(1).color());
    }

    @Test
    public void clipPushBreaksBatch() {
        PaintCommandList list = new PaintCommandList();
        list.append(border(0, 0, 10, 10, RED));
        list.append(new ClipPushCommand(0, 0, 100, 100));
        list.append(border(10, 0, 10, 10, RED));
        CapturingSink sink = run(list);
        assertEquals(2, sink.batches.size());
        assertTrue("batch 0 should have no clip", sink.batches.get(0).clipRect() == null);
        assertTrue("batch 1 should have clip", sink.batches.get(1).clipRect() != null);
    }

    @Test
    public void clipPopBreaksBatch() {
        PaintCommandList list = new PaintCommandList();
        list.append(new ClipPushCommand(0, 0, 100, 100));
        list.append(border(0, 0, 10, 10, RED));
        list.append(new ClipPopCommand());
        list.append(border(10, 0, 10, 10, RED));
        CapturingSink sink = run(list);
        assertEquals(2, sink.batches.size());
        assertTrue("batch 0 in clip A", sink.batches.get(0).clipRect() != null);
        assertTrue("batch 1 no clip after pop", sink.batches.get(1).clipRect() == null);
    }

    @Test
    public void rectFillFlushesPendingBorders() {
        // border1 → fill → border2，同色同 clip
        // 视觉顺序：border1 必须先于 fill，fill 必须先于 border2
        // → border1 单独一批，border2 单独一批（被 fill 打断）
        PaintCommandList list = new PaintCommandList();
        list.append(border(0, 0, 10, 10, RED));
        list.append(fill(0, 0, 10, 10));
        list.append(border(20, 0, 10, 10, RED));
        CapturingSink sink = run(list);
        assertEquals(2, sink.batches.size());
        assertEquals(1, sink.batches.get(0).borderCount());
        assertEquals(1, sink.batches.get(1).borderCount());
    }

    @Test
    public void rectFillDoesNotFlushIfNoPendingBorders() {
        // 无 pending border 时 RectFill 不应产生空批
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10));
        list.append(border(0, 0, 10, 10, RED));
        CapturingSink sink = run(list);
        assertEquals(1, sink.batches.size());
    }

    @Test
    public void finishFlushesPendingBatch() {
        PaintCommandList list = new PaintCommandList();
        list.append(border(0, 0, 10, 10, RED));
        CapturingSink sink = new CapturingSink();
        BorderRenderer r = new BorderRenderer(sink);
        list.replay(r);
        assertEquals("pending should not emit before finish", 0, sink.batches.size());
        r.finish();
        assertEquals(1, sink.batches.size());
    }

    @Test
    public void finishIsIdempotent() {
        PaintCommandList list = new PaintCommandList();
        list.append(border(0, 0, 10, 10, RED));
        CapturingSink sink = new CapturingSink();
        BorderRenderer r = new BorderRenderer(sink);
        list.replay(r);
        r.finish();
        int n = sink.batches.size();
        r.finish();
        r.finish();
        assertEquals(n, sink.batches.size());
    }

    @Test
    public void nullSinkThrows() {
        try {
            new BorderRenderer(null);
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void batchPreservesBorderOrder() {
        PaintCommandList list = new PaintCommandList();
        list.append(border(0, 0, 10, 10, RED));
        list.append(border(10, 0, 10, 10, RED));
        list.append(border(20, 0, 10, 10, RED));
        CapturingSink sink = run(list);
        BorderBatch b = sink.batches.get(0);
        assertEquals(0f, b.border(0).x(), 0.0001f);
        assertEquals(10f, b.border(1).x(), 0.0001f);
        assertEquals(20f, b.border(2).x(), 0.0001f);
    }

    @Test
    public void roundedBorderBatchedWithNonRoundedSameColor() {
        // 圆角与非圆角 border 同色同 clip → 同批（color 与 clip 决定批，几何不影响批）
        PaintCommandList list = new PaintCommandList();
        list.append(border(0, 0, 10, 10, RED));
        list.append(roundedBorder(10, 0, 10, 10, RED, 4f));
        CapturingSink sink = run(list);
        assertEquals(1, sink.batches.size());
        assertEquals(2, sink.batches.get(0).borderCount());
    }
}
