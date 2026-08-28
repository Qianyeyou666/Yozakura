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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 阶段 6 切片 3：PaintCommandDispatcher（复合 visitor）桥测试。
 *
 * <p>验证契约（AGENTS.md：retained pipeline 最后一步
 * "PaintCommandList -> batched OpenGL rendering"，按 shader/texture/clip 批处理，
 * 最小化 draw call，同时保持正确视觉层级）。
 *
 * <p>背景：阶段 3 已有 {@link BatchedRectangleRenderer} 与 {@link BorderRenderer}
 * 两个独立 visitor，各自维护 pending 批与 clip 栈。但若分别 replay 同一命令列表，
 * 会丢失 fill/border 的交错顺序（所有 fill 先于所有 border 绘制 → 视觉层级错误）。
 *
 * <p>{@link PaintCommandDispatcher} 是复合 visitor：
 * <ul>
 *   <li>单一 {@link gq.yozakura.ui.engine.paint.PaintCommandVisitor} 入口</li>
 *   <li>内部委托给 BatchedRectangleRenderer + BorderRenderer</li>
 *   <li>通过适配 sink 将两边刷出的批包装为 {@link RenderOp} 提交给单一 {@link RenderOpSink}</li>
 *   <li>关键顺序契约：
 *     <ul>
 *       <li>visitRectFill：先调 borderRenderer.visitRectFill（刷出 pending border，
 *           属于更早元素，必须先绘制），再调 rectRenderer.visitRectFill</li>
 *       <li>visitRectBorder：先调 rectRenderer.visitRectBorder（刷出 pending fill，
 *           属于同一元素或更早元素的 bg，必须先于 border 绘制），再调 borderRenderer.visitRectBorder</li>
 *       <li>visitClipPush/Pop：两端都调用（各自维护 clip 栈，结果一致）</li>
 *     </ul>
 *   </li>
 *   <li>finish：依次调用两端 finish，刷出剩余 pending</li>
 * </ul>
 *
 * <p>本测试只验证编排契约（emit 顺序、kind 标签、clip 携带），不触及真实 OpenGL。
 */
public class PaintCommandDispatcherTest {

    /** 捕获 sink：按追加顺序记录所有 RenderOp。 */
    private static final class CapturingSink implements RenderOpSink {
        final List<RenderOp> ops = new ArrayList<RenderOp>();

        @Override
        public void emit(RenderOp op) {
            ops.add(op);
        }
    }

    private static RectFillCommand fill(float x, float y, float w, float h, Color c) {
        return new RectFillCommand(x, y, w, h, c);
    }

    private static RectBorderCommand border(float x, float y, float w, float h, Color c) {
        return new RectBorderCommand(x, y, w, h, 1, 1, 1, 1, c, 0f);
    }

    private static CapturingSink run(PaintCommandList list) {
        CapturingSink sink = new CapturingSink();
        PaintCommandDispatcher dispatcher = new PaintCommandDispatcher(sink);
        list.replay(dispatcher);
        dispatcher.finish();
        return sink;
    }

    // ---- 基础契约 ----

    @Test
    public void emptyListProducesNoOps() {
        CapturingSink sink = run(new PaintCommandList());
        assertTrue("empty command list must produce 0 ops",
                sink.ops.isEmpty());
    }

    @Test
    public void nullSinkThrows() {
        try {
            new PaintCommandDispatcher(null);
            fail("expected IllegalArgumentException for null sink");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void singleRectFillProducesOneRectangleOp() {
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10, Color.fromRgba(1f, 0f, 0f, 1f)));
        CapturingSink sink = run(list);
        assertEquals(1, sink.ops.size());
        assertEquals(RenderOp.KIND_RECTANGLE, sink.ops.get(0).kind());
        RectangleRenderOp rectOp = (RectangleRenderOp) sink.ops.get(0);
        assertEquals(1, rectOp.batch().rectCount());
        assertNull("no clip push → clipRect must be null", rectOp.clipRect());
    }

    @Test
    public void singleRectBorderProducesOneBorderOp() {
        PaintCommandList list = new PaintCommandList();
        list.append(border(0, 0, 10, 10, Color.fromRgba(0f, 0f, 0f, 1f)));
        CapturingSink sink = run(list);
        assertEquals(1, sink.ops.size());
        assertEquals(RenderOp.KIND_BORDER, sink.ops.get(0).kind());
        BorderRenderOp borderOp = (BorderRenderOp) sink.ops.get(0);
        assertEquals(1, borderOp.batch().borderCount());
        assertNull("no clip push → clipRect must be null", borderOp.clipRect());
    }

    // ---- 顺序契约（核心）----

    /**
     * 单元素的 background → border 顺序：
     * 必须先 emit RectangleOp(bg)，再 emit BorderOp(border)。
     *
     * <p>若直接分别 replay 两端，所有 fill 会先于所有 border → 顺序错。
     */
    @Test
    public void backgroundBeforeBorderEmitOrder() {
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10, Color.fromRgba(1f, 0f, 0f, 1f)));   // bg
        list.append(border(0, 0, 10, 10, Color.fromRgba(0f, 0f, 0f, 1f))); // border
        CapturingSink sink = run(list);
        assertEquals(2, sink.ops.size());
        assertEquals("op 0 must be rectangle (bg first)",
                RenderOp.KIND_RECTANGLE, sink.ops.get(0).kind());
        assertEquals("op 1 must be border (border after bg)",
                RenderOp.KIND_BORDER, sink.ops.get(1).kind());
    }

    /**
     * 交错场景：bg1 → border1 → bg2 → border2
     * 必须 emit：RectOp(bg1) → BorderOp(border1) → RectOp(bg2) → BorderOp(border2)。
     */
    @Test
    public void interleavedFillBorderPreservesVisualOrder() {
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10, Color.fromRgba(1f, 0f, 0f, 1f)));    // bg1
        list.append(border(0, 0, 10, 10, Color.fromRgba(0f, 0f, 0f, 1f)));  // border1
        list.append(fill(20, 0, 10, 10, Color.fromRgba(0f, 1f, 0f, 1f)));   // bg2
        list.append(border(20, 0, 10, 10, Color.fromRgba(0f, 0f, 0f, 1f))); // border2
        CapturingSink sink = run(list);
        assertEquals(4, sink.ops.size());
        int[] expectedKinds = {
                RenderOp.KIND_RECTANGLE, RenderOp.KIND_BORDER,
                RenderOp.KIND_RECTANGLE, RenderOp.KIND_BORDER,
        };
        for (int i = 0; i < expectedKinds.length; i++) {
            assertEquals("op " + i + " kind mismatch", expectedKinds[i], sink.ops.get(i).kind());
        }
        // 验证内容：第 1 个 rect 是 bg1（红色），第 2 个 rect 是 bg2（绿色）
        RectangleRenderOp r0 = (RectangleRenderOp) sink.ops.get(0);
        RectangleRenderOp r2 = (RectangleRenderOp) sink.ops.get(2);
        assertEquals(0f, r0.batch().rectX(0), 0.0001f);
        assertEquals(20f, r2.batch().rectX(0), 0.0001f);
    }

    /**
     * 完整元素嵌套场景（paint tree builder 实际产出顺序）：
     *   outer bg → outer border → clipPush → inner bg → inner border → clipPop
     *
     * <p>期望 emit 顺序：
     *   1. RectOp(outer bg) — clip=null
     *   2. BorderOp(outer border) — clip=null（border 在 clipPush 前）
     *   3. RectOp(inner bg) — clip=A
     *   4. BorderOp(inner border) — clip=A
     */
    @Test
    public void nestedElementPaintTreeEmitsInCorrectOrderWithClips() {
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 100, 100, Color.fromRgba(1f, 0f, 0f, 1f)));    // outer bg
        list.append(border(0, 0, 100, 100, Color.fromRgba(0f, 0f, 0f, 1f)));  // outer border
        list.append(new ClipPushCommand(0, 0, 100, 100));                     // clip A
        list.append(fill(10, 10, 50, 50, Color.fromRgba(0f, 1f, 0f, 1f)));    // inner bg
        list.append(border(10, 10, 50, 50, Color.fromRgba(0f, 0f, 0f, 1f)));  // inner border
        list.append(new ClipPopCommand());

        CapturingSink sink = run(list);
        assertEquals(4, sink.ops.size());

        // 1. outer bg — no clip
        assertEquals(RenderOp.KIND_RECTANGLE, sink.ops.get(0).kind());
        assertNull("outer bg should have no clip", sink.ops.get(0).clipRect());

        // 2. outer border — no clip
        assertEquals(RenderOp.KIND_BORDER, sink.ops.get(1).kind());
        assertNull("outer border should have no clip", sink.ops.get(1).clipRect());

        // 3. inner bg — clip A
        assertEquals(RenderOp.KIND_RECTANGLE, sink.ops.get(2).kind());
        ClipRect innerClip = sink.ops.get(2).clipRect();
        assertNotNull("inner bg should carry clip A", innerClip);
        assertEquals(0f, innerClip.x(), 0.0001f);
        assertEquals(0f, innerClip.y(), 0.0001f);
        assertEquals(100f, innerClip.width(), 0.0001f);
        assertEquals(100f, innerClip.height(), 0.0001f);

        // 4. inner border — clip A
        assertEquals(RenderOp.KIND_BORDER, sink.ops.get(3).kind());
        ClipRect borderClip = sink.ops.get(3).clipRect();
        assertNotNull("inner border should carry clip A", borderClip);
        assertEquals(0f, borderClip.x(), 0.0001f);
        assertEquals(100f, borderClip.width(), 0.0001f);
    }

    // ---- clip 栈契约 ----

    /**
     * ClipPop 后回到无裁剪：bg1(none) → push(A) → bg2(A) → pop → bg3(none)
     * 期望 3 个 RectOp，clip 分别为 null / A / null。
     */
    @Test
    public void clipPushPopMaintainsClipStackAcrossFills() {
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10, Color.fromRgba(1f, 0f, 0f, 1f)));
        list.append(new ClipPushCommand(0, 0, 100, 100));
        list.append(fill(10, 0, 10, 10, Color.fromRgba(0f, 1f, 0f, 1f)));
        list.append(new ClipPopCommand());
        list.append(fill(20, 0, 10, 10, Color.fromRgba(0f, 0f, 1f, 1f)));

        CapturingSink sink = run(list);
        assertEquals(3, sink.ops.size());
        assertNull("op 0 clip must be null", sink.ops.get(0).clipRect());
        assertNotNull("op 1 clip must be non-null", sink.ops.get(1).clipRect());
        assertEquals(100f, sink.ops.get(1).clipRect().width(), 0.0001f);
        assertNull("op 2 clip must be null after pop", sink.ops.get(2).clipRect());
    }

    /**
     * 嵌套裁剪：push(A) → push(B) → fill → pop → pop
     * 中间 fill 的 clip 应为 A∩B。
     */
    @Test
    public void nestedClipPushIntersectsClipRects() {
        PaintCommandList list = new PaintCommandList();
        list.append(new ClipPushCommand(0, 0, 100, 100));    // A
        list.append(new ClipPushCommand(10, 10, 50, 50));    // B → A∩B = (10,10,50,50)
        list.append(fill(20, 20, 10, 10, Color.fromRgba(1f, 0f, 0f, 1f)));
        list.append(new ClipPopCommand());                   // back to A
        list.append(new ClipPopCommand());                   // back to none

        CapturingSink sink = run(list);
        assertEquals(1, sink.ops.size());
        ClipRect clip = sink.ops.get(0).clipRect();
        assertNotNull(clip);
        assertEquals(10f, clip.x(), 0.0001f);
        assertEquals(10f, clip.y(), 0.0001f);
        assertEquals(50f, clip.width(), 0.0001f);
        assertEquals(50f, clip.height(), 0.0001f);
    }

    // ---- finish 契约 ----

    /**
     * finish 刷出两端 pending：
     * bg → border（两端各 pending 1）→ finish → 2 ops。
     */
    @Test
    public void finishFlushesPendingFromBothRenderers() {
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10, Color.fromRgba(1f, 0f, 0f, 1f)));
        list.append(border(0, 0, 10, 10, Color.fromRgba(0f, 0f, 0f, 1f)));

        CapturingSink sink = new CapturingSink();
        PaintCommandDispatcher dispatcher = new PaintCommandDispatcher(sink);
        list.replay(dispatcher);
        // finish 前：border 已在 visitRectBorder 时触发 rect flush（emit RectOp），
        // 但 border 自身仍在 borderRenderer.pending 中，未 emit
        assertEquals("RectOp must be emitted before finish (triggered by border visit)",
                1, sink.ops.size());
        assertEquals(RenderOp.KIND_RECTANGLE, sink.ops.get(0).kind());
        dispatcher.finish();
        assertEquals("finish must flush pending BorderOp",
                2, sink.ops.size());
        assertEquals(RenderOp.KIND_BORDER, sink.ops.get(1).kind());
    }

    @Test
    public void finishIsIdempotent() {
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10, Color.fromRgba(1f, 0f, 0f, 1f)));
        CapturingSink sink = new CapturingSink();
        PaintCommandDispatcher dispatcher = new PaintCommandDispatcher(sink);
        list.replay(dispatcher);
        dispatcher.finish();
        int countAfterFirst = sink.ops.size();
        dispatcher.finish();
        dispatcher.finish();
        assertEquals("repeated finish must not produce duplicate ops",
                countAfterFirst, sink.ops.size());
    }

    // ---- 连续同色 border 批合并 ----

    /**
     * 两个连续同色 border（中间无 fill/clip 变化）应合并为单个 BorderOp。
     * 这是 BorderRenderer 的固有行为，dispatcher 不应破坏。
     */
    @Test
    public void consecutiveSameColorBordersMergeIntoSingleBorderOp() {
        Color c = Color.fromRgba(0f, 0f, 0f, 1f);
        PaintCommandList list = new PaintCommandList();
        list.append(border(0, 0, 10, 10, c));
        list.append(border(20, 0, 10, 10, c));
        CapturingSink sink = run(list);
        assertEquals(1, sink.ops.size());
        assertEquals(RenderOp.KIND_BORDER, sink.ops.get(0).kind());
        assertEquals(2, ((BorderRenderOp) sink.ops.get(0)).batch().borderCount());
    }

    /**
     * 不同色 border 必须分批（color 是 border batch 的 uniform）。
     */
    @Test
    public void differentColorBordersSplitIntoSeparateOps() {
        PaintCommandList list = new PaintCommandList();
        list.append(border(0, 0, 10, 10, Color.fromRgba(0f, 0f, 0f, 1f)));
        list.append(border(20, 0, 10, 10, Color.fromRgba(1f, 0f, 0f, 1f)));
        CapturingSink sink = run(list);
        assertEquals(2, sink.ops.size());
        assertEquals(RenderOp.KIND_BORDER, sink.ops.get(0).kind());
        assertEquals(RenderOp.KIND_BORDER, sink.ops.get(1).kind());
    }

    // ---- 边界：finish 后再 visit 明确报生命周期错误 ----

    @Test(expected = IllegalStateException.class)
    public void visitAfterFinishReportsLifecycleError() {
        PaintCommandList list = new PaintCommandList();
        list.append(fill(0, 0, 10, 10, Color.fromRgba(1f, 0f, 0f, 1f)));

        CapturingSink sink = new CapturingSink();
        PaintCommandDispatcher dispatcher = new PaintCommandDispatcher(sink);
        list.replay(dispatcher);
        dispatcher.finish();
        dispatcher.visitRectFill(fill(100, 100, 5, 5, Color.fromRgba(0f, 1f, 0f, 1f)));
    }
}
