package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.Color;
import gq.yozakura.ui.engine.paint.ClipPopCommand;
import gq.yozakura.ui.engine.paint.ClipPushCommand;
import gq.yozakura.ui.engine.paint.PaintCommandList;
import gq.yozakura.ui.engine.paint.RectBorderCommand;
import gq.yozakura.ui.engine.paint.RectFillCommand;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 3 切片 4：BatchedRectangleRenderer 批处理契约测试。
 *
 * <p>验证契约（AGENTS.md：按 shader/texture/clip 批处理，最小化 draw call）：
 * <ul>
 *   <li>相同 clip 上下文下的连续 RectFill 合并为单个 RectangleBatch</li>
 *   <li>ClipPush/ClipPop 改变 clip 上下文 → 打断当前批</li>
 *   <li>ClipPop 后恢复上层 clip 上下文 → 与之前的同 clip rect 仍可继续批</li>
 *   <li>RectBorder 走不同渲染路径 → 打断当前 RectFill 批</li>
 *   <li>空命令列表 → 0 批</li>
 *   <li>每个 RectangleBatch 持有当前 clip rect（用于 GL scissor 设置）</li>
 *   <li>finish() 刷出最后一批</li>
 * </ul>
 *
 * <p>本测试只验证批处理编排契约，不触及真实 OpenGL。
 * 实际 draw call 行为需在 Minecraft 实机环境验证。
 */
public class BatchedRectangleRendererTest {

    private static final class CapturingSink implements RectangleBatchSink {
        final List<RectangleBatch> batches = new ArrayList<RectangleBatch>();

        @Override
        public void emit(RectangleBatch batch) {
            batches.add(batch);
        }
    }

    private static RectFillCommand fill(float x, float y, float w, float h) {
        return new RectFillCommand(x, y, w, h, Color.fromRgba(1f, 0f, 0f, 1f));
    }

    private static RectBorderCommand border(float x, float y, float w, float h) {
        return new RectBorderCommand(x, y, w, h, 1, 1, 1, 1, Color.fromRgba(0f, 0f, 0f, 1f), 0f);
    }

    private static CapturingSink run(PaintCommandList list) {
        CapturingSink sink = new CapturingSink();
        BatchedRectangleRenderer renderer = new BatchedRectangleRenderer(sink);
        list.replay(renderer);
        renderer.finish();
        return sink;
    }

    @Test
    public void emptyListProducesNoBatches() {
        CapturingSink sink = run(new PaintCommandList());
        assertTrue("empty command list must produce 0 batches",
                sink.batches.isEmpty());
    }

    @Test
    public void singleRectFillProducesOneBatch() {
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10));
        CapturingSink sink = run(list);
        assertEquals(1, sink.batches.size());
        assertEquals(1, sink.batches.get(0).rectCount());
    }

    @Test
    public void consecutiveRectFillsWithSameClipAreBatched() {
        // 3 个连续 RectFill，无 clip 改变 → 1 个批含 3 个矩形
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10));
        list.append(fill(10, 0, 10, 10));
        list.append(fill(20, 0, 10, 10));
        CapturingSink sink = run(list);
        assertEquals(1, sink.batches.size());
        assertEquals(3, sink.batches.get(0).rectCount());
    }

    @Test
    public void clipPushBreaksBatch() {
        // rect, clip push, rect → 2 批（clip 上下文不同）
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10));
        list.append(new ClipPushCommand(0, 0, 100, 100));
        list.append(fill(10, 0, 10, 10));
        CapturingSink sink = run(list);
        assertEquals(2, sink.batches.size());
        assertEquals(1, sink.batches.get(0).rectCount());
        assertEquals(1, sink.batches.get(1).rectCount());
    }

    @Test
    public void clipPushWithSameRectAsCurrentStillBreaksBatch() {
        // 即使新 clip 与旧 clip 数值相同，clip push 仍代表状态切换，必须打断
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10));
        list.append(new ClipPushCommand(0, 0, 100, 100));
        list.append(fill(10, 0, 10, 10));
        list.append(new ClipPushCommand(0, 0, 100, 100)); // 同 rect，但仍是 push
        list.append(fill(20, 0, 10, 10));
        CapturingSink sink = run(list);
        assertEquals(3, sink.batches.size());
    }

    @Test
    public void clipPopRestoresPreviousClipContext() {
        // rect（clip A）→ push（clip B）→ rect → pop → rect（回到 clip A）
        // 第 1 与第 3 个 rect 都在 clip A（null/no-clip），但中间隔了不同 clip
        // → 必须分 3 批（批上下文不能跨 clip 改变）
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10));                       // clip 上下文 = none
        list.append(new ClipPushCommand(0, 0, 100, 100));      // → clip 上下文 = A
        list.append(fill(10, 0, 10, 10));                      // 在 A 内
        list.append(new ClipPopCommand());                     // → 回到 none
        list.append(fill(20, 0, 10, 10));                      // 在 none 内
        CapturingSink sink = run(list);
        assertEquals(3, sink.batches.size());
        // 第 1 与第 3 个批的 clip rect 都为 null（无裁剪）
        assertTrue("batch 0 should have no clip", sink.batches.get(0).clipRect() == null);
        assertTrue("batch 2 should have no clip after pop", sink.batches.get(2).clipRect() == null);
        // 第 2 个批的 clip rect 为 (0,0,100,100)
        RectangleBatch mid = sink.batches.get(1);
        assertEquals(0f, mid.clipRect().x(), 0.0001f);
        assertEquals(0f, mid.clipRect().y(), 0.0001f);
        assertEquals(100f, mid.clipRect().width(), 0.0001f);
        assertEquals(100f, mid.clipRect().height(), 0.0001f);
    }

    @Test
    public void nestedClipPushesTrackDepth() {
        // rect → push1 → rect → push2 → rect → pop → rect → pop → rect
        // 5 个 rect，每个都处于不同 clip 上下文 → 5 批
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10));                              // none
        list.append(new ClipPushCommand(0, 0, 100, 100));            // clip A
        list.append(fill(10, 0, 10, 10));                            // in A
        list.append(new ClipPushCommand(10, 10, 50, 50));            // clip A∩B
        list.append(fill(20, 0, 10, 10));                            // in A∩B
        list.append(new ClipPopCommand());                           // back to A
        list.append(fill(30, 0, 10, 10));                            // in A
        list.append(new ClipPopCommand());                           // back to none
        list.append(fill(40, 0, 10, 10));                            // in none
        CapturingSink sink = run(list);
        assertEquals(5, sink.batches.size());
        // 每批 1 矩形
        for (int i = 0; i < 5; i++) {
            assertEquals("batch " + i + " should have 1 rect", 1, sink.batches.get(i).rectCount());
        }
        // batch 0 = none, batch 1 = A(0,0,100,100), batch 2 = A∩B(10,10,50,50),
        // batch 3 = A(0,0,100,100), batch 4 = none
        assertTrue(sink.batches.get(0).clipRect() == null);
        assertEquals(0f, sink.batches.get(1).clipRect().x(), 0.0001f);
        assertEquals(100f, sink.batches.get(1).clipRect().width(), 0.0001f);
        assertEquals(10f, sink.batches.get(2).clipRect().x(), 0.0001f);
        assertEquals(50f, sink.batches.get(2).clipRect().width(), 0.0001f);
        assertEquals(0f, sink.batches.get(3).clipRect().x(), 0.0001f);
        assertEquals(100f, sink.batches.get(3).clipRect().width(), 0.0001f);
        assertTrue(sink.batches.get(4).clipRect() == null);
    }

    @Test
    public void rectBorderBreaksBatch() {
        // rect, border, rect → 2 批（border 走不同渲染路径）
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10));
        list.append(border(0, 0, 10, 10));
        list.append(fill(20, 0, 10, 10));
        CapturingSink sink = run(list);
        // RectBorder 不进入 RectangleBatch（由 BorderRenderer 处理）
        // → 仅 2 个 RectFill 批（中间被 border 打断）
        assertEquals(2, sink.batches.size());
        assertEquals(1, sink.batches.get(0).rectCount());
        assertEquals(1, sink.batches.get(1).rectCount());
    }

    @Test
    public void finishFlushesPendingBatch() {
        // 不调用 finish 会丢失最后一批
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10));
        list.append(fill(10, 0, 10, 10));

        CapturingSink sink = new CapturingSink();
        BatchedRectangleRenderer renderer = new BatchedRectangleRenderer(sink);
        list.replay(renderer);
        // finish 前已有 1 批（2 矩形累积中？取决于实现：可在 visitRectFill 时累积，finish 时刷出）
        // 我们采用懒刷：visit 时不立即 emit，finish 时才 emit
        // → finish 前应为 0 批
        assertEquals("pending batch should not be emitted before finish",
                0, sink.batches.size());
        renderer.finish();
        assertEquals(1, sink.batches.size());
        assertEquals(2, sink.batches.get(0).rectCount());
    }

    @Test
    public void clipChangeFlushesPendingBatchEagerly() {
        // 懒刷实现下：clip 变化时必须立即刷出累积的批，保证批内 clip 一致
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10));
        list.append(fill(10, 0, 10, 10));
        list.append(new ClipPushCommand(0, 0, 100, 100));
        list.append(fill(20, 0, 10, 10));

        CapturingSink sink = new CapturingSink();
        BatchedRectangleRenderer renderer = new BatchedRectangleRenderer(sink);
        list.replay(renderer);
        // 此时应在 clip push 时已刷出前 2 个 rect（同 none clip）
        assertEquals("clip push must flush pending batch",
                1, sink.batches.size());
        assertEquals(2, sink.batches.get(0).rectCount());
        renderer.finish();
        // finish 刷出第 2 批（在 clip A 内的 1 个 rect）
        assertEquals(2, sink.batches.size());
        assertEquals(1, sink.batches.get(1).rectCount());
    }

    @Test
    public void batchPreservesRectOrder() {
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10));
        list.append(fill(10, 0, 10, 10));
        list.append(fill(20, 0, 10, 10));
        CapturingSink sink = run(list);
        RectangleBatch batch = sink.batches.get(0);
        assertEquals(0f, batch.rectX(0), 0.0001f);
        assertEquals(10f, batch.rectX(1), 0.0001f);
        assertEquals(20f, batch.rectX(2), 0.0001f);
    }

    @Test
    public void nullSinkThrows() {
        try {
            new BatchedRectangleRenderer(null);
            org.junit.Assert.fail("expected IllegalArgumentException for null sink");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void finishIsIdempotent() {
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10));
        CapturingSink sink = new CapturingSink();
        BatchedRectangleRenderer renderer = new BatchedRectangleRenderer(sink);
        list.replay(renderer);
        renderer.finish();
        int countAfterFirstFinish = sink.batches.size();
        renderer.finish();
        renderer.finish();
        assertEquals("repeated finish must not produce duplicate batches",
                countAfterFirstFinish, sink.batches.size());
    }

    @Test
    public void zeroSizeRectIsIncludedInBatch() {
        // 0 尺寸 rect 在命令层合法（renderer 可选择跳过）；本测试断言 renderer 不过滤
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 0, 0));
        list.append(fill(10, 0, 10, 10));
        CapturingSink sink = run(list);
        assertEquals(1, sink.batches.size());
        assertEquals(2, sink.batches.get(0).rectCount());
    }
}
