package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.ClipPopCommand;
import gq.yozakura.ui.engine.paint.ClipPushCommand;
import gq.yozakura.ui.engine.paint.PaintCommandVisitor;
import gq.yozakura.ui.engine.paint.RectBorderCommand;
import gq.yozakura.ui.engine.paint.RectFillCommand;
import gq.yozakura.ui.engine.paint.TextPaintCommand;

/**
 * 复合 Paint 命令分派器：单一 visitor 入口，将命令路由到内部
 * {@link BatchedRectangleRenderer}（处理 RectFill）与 {@link BorderRenderer}（处理 RectBorder），
 * 通过适配 sink 将两边刷出的批包装为 {@link RenderOp} 提交给唯一 {@link RenderOpSink}。
 *
 * <p>解决的问题：阶段 3 的两个 visitor 各自独立维护 pending 批与 clip 栈。
 * 若直接对同一命令列表分别 replay，会丢失 fill/border 的交错顺序
 * （所有 fill 先绘制 → 所有 border 后绘制 → 视觉层级错误）。
 *
 * <p>顺序契约（核心）：
 * <ul>
 *   <li>{@link #visitRectFill}：
 *     先调 {@code borderRenderer.visitRectFill}（刷出 pending border —
 *     属于更早元素，必须先于此 fill 绘制），再调 {@code rectRenderer.visitRectFill}</li>
 *   <li>{@link #visitRectBorder}：
 *     先调 {@code rectRenderer.visitRectBorder}（刷出 pending fill —
 *     属于同一元素或更早元素的 bg，必须先于此 border 绘制），再调 {@code borderRenderer.visitRectBorder}</li>
 *   <li>{@link #visitClipPush} / {@link #visitClipPop}：
 *     两端均调用（各自维护 clip 栈，输入相同 → 状态一致）</li>
 * </ul>
 *
 * <p>由于两端 visitor 在收到对方命令时都会立即 flush 自己的 pending，
 * 顺序契约保证：每当新命令到达，所有"应在其之前绘制"的 pending 批已被刷出到 sink。
 *
 * <p>finish 顺序：先 rectRenderer.finish（刷出剩余 fill），再 borderRenderer.finish
 * （刷出剩余 border）。这与 paint 树的"先背景后内容"顺序一致——
 * 最末元素的 bg 必须先于其 border 绘制。
 *
 * <p>线程模型：单线程（渲染线程）。非线程安全。
 *
 * <p>零分配目标：visit 路径仅在两端 pending 容量不足时分配；
 * 适配 sink 将 batch 包装为 op 时分配一次 op 对象（retained 后只读访问）。
 */
public final class PaintCommandDispatcher implements PaintCommandVisitor {

    private final BatchedRectangleRenderer rectRenderer;
    private final BorderRenderer borderRenderer;
    private final RenderOpSink sink;
    private boolean finished;

    public PaintCommandDispatcher(final RenderOpSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        // 适配 sink：两端刷出的批包装为对应 RenderOp 提交给唯一 sink。
        // 顺序由 visit/finish 中的调用顺序保证，与 sink 端无关。
        this.sink = sink;
        this.rectRenderer = new BatchedRectangleRenderer(new RectangleBatchSink() {
            @Override
            public void emit(RectangleBatch batch) {
                sink.emit(new RectangleRenderOp(batch));
            }
        });
        this.borderRenderer = new BorderRenderer(new BorderBatchSink() {
            @Override
            public void emit(BorderBatch batch) {
                sink.emit(new BorderRenderOp(batch));
            }
        });
    }

    @Override
    public void visitRectFill(RectFillCommand command) {
        if (finished) {
            throw finishedError();
        }
        // 先刷出 border pending：更早元素的 border 必须先于此 fill 绘制
        // （borderRenderer.visitRectFill 内部会 flush pending border，然后忽略此 fill）
        borderRenderer.visitRectFill(command);
        // 再将 fill 累积到 rect pending
        rectRenderer.visitRectFill(command);
    }

    @Override
    public void visitRectBorder(RectBorderCommand command) {
        if (finished) {
            throw finishedError();
        }
        // 先刷出 fill pending：同元素/更早元素的 bg 必须先于此 border 绘制
        // （rectRenderer.visitRectBorder 内部会 flush pending fill，然后忽略此 border）
        rectRenderer.visitRectBorder(command);
        // 再将 border 累积到 border pending
        borderRenderer.visitRectBorder(command);
    }

    @Override
    public void visitClipPush(ClipPushCommand command) {
        if (finished) {
            throw finishedError();
        }
        // 两端各自 flush pending 并更新 clip 栈
        // 顺序：先 rect 后 border — rect 的 pending fill 必须在 clip 改变前刷出
        // （border 的 pending 在 rect flush 后已无变化，仍按原 clip 刷出）
        rectRenderer.visitClipPush(command);
        borderRenderer.visitClipPush(command);
    }

    @Override
    public void visitClipPop(ClipPopCommand command) {
        if (finished) {
            throw finishedError();
        }
        // 两端各自 flush pending 并 pop clip 栈
        rectRenderer.visitClipPop(command);
        borderRenderer.visitClipPop(command);
    }

    @Override
    public void visitText(TextPaintCommand command) {
        if (finished) {
            throw finishedError();
        }
        rectRenderer.visitText(command);
        borderRenderer.visitText(command);
        sink.emit(new TextRenderOp(command, rectRenderer.currentClip()));
    }

    /**
     * 刷出两端剩余 pending 并标记完成。幂等。
     *
     * <p>顺序：先 rectRenderer.finish（剩余 fill），再 borderRenderer.finish（剩余 border）。
     * 这保证最末元素的 bg 先于其 border 绘制。
     */
    public void finish() {
        if (finished) {
            return;
        }
        finished = true;
        rectRenderer.finish();
        borderRenderer.finish();
    }

    private static IllegalStateException finishedError() {
        return new IllegalStateException("paint command dispatcher has already been finished");
    }
}
