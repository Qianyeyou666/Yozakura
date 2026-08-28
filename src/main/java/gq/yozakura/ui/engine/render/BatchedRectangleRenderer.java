package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.ClipPopCommand;
import gq.yozakura.ui.engine.paint.ClipPushCommand;
import gq.yozakura.ui.engine.paint.PaintCommandVisitor;
import gq.yozakura.ui.engine.paint.RectBorderCommand;
import gq.yozakura.ui.engine.paint.RectFillCommand;
import gq.yozakura.ui.engine.paint.TextPaintCommand;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 矩形批处理器：遍历 {@link gq.yozakura.ui.engine.paint.PaintCommandList}，
 * 将连续的 {@link RectFillCommand} 按 clip 上下文累积为 {@link RectangleBatch}，
 * 提交给 {@link RectangleBatchSink}。
 *
 * <p>批处理契约（AGENTS.md：按 shader/texture/clip 批处理，最小化 draw call）：
 * <ul>
 *   <li>相同 clip 上下文下的连续 RectFill 累积到 pending，{@link #finish()} 时刷出为一批</li>
 *   <li>ClipPush/ClipPop 改变 clip 上下文 → 立即刷出 pending（保证批内 clip 一致）</li>
 *   <li>ClipPush 时与当前栈顶 clip 求交集，结果作为新的栈顶（嵌套裁剪）</li>
 *   <li>ClipPop 弹出栈顶；栈空时 currentClip 回到 null（无裁剪）</li>
 *   <li>RectBorder 走不同渲染路径（不同 shader/几何）→ 立即刷出 pending</li>
 *   <li>{@link #finish()} 幂等：重复调用不重复刷出</li>
 * </ul>
 *
 * <p>设计取舍：MVP 选择"任何 clip 操作都打断批"的保守策略，
 * 而非"clip 值未变则继续批"的优化策略。
 * 理由：clip push/pop 在视觉上代表子树层级边界，保守打断可避免透明叠加顺序问题；
 * 后续可通过比较 ClipRect 值实现更激进的合并。
 *
 * <p>不持有 GL 资源；sink 由调用方注入。生产实现 {@code LwjglRectangleBatchSink}
 * 在 emit 时上传顶点并调用 glDrawElements。单测用 CapturingSink 验证编排。
 *
 * <p>零分配目标：visitRectFill 路径仅在 pending 容量不足时分配；
 * flush 时 RectangleBatch 构造做一次防御性拷贝（retained 后只读访问，无后续修改）。
 */
public final class BatchedRectangleRenderer implements PaintCommandVisitor {

    private final RectangleBatchSink sink;
    private final List<RectFillCommand> pending = new ArrayList<RectFillCommand>();
    private final Deque<ClipRect> clipStack = new ArrayDeque<ClipRect>();
    private ClipRect currentClip;   // 栈顶；栈空时为 null（无裁剪）
    private boolean finished;

    public BatchedRectangleRenderer(RectangleBatchSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        this.sink = sink;
    }

    /** 当前 clip 上下文（栈顶或 null）。测试与外部调试用。 */
    public ClipRect currentClip() {
        return currentClip;
    }

    /** 当前 pending 批中的矩形数量（未刷出）。测试用。 */
    public int pendingCount() {
        return pending.size();
    }

    @Override
    public void visitRectFill(RectFillCommand command) {
        if (finished) {
            // finish 后的命令静默丢弃：retained 模式下命令列表已固化
            return;
        }
        pending.add(command);
    }

    @Override
    public void visitRectBorder(RectBorderCommand command) {
        // Border 走不同渲染路径（不同 shader/几何），打断 RectFill 批
        // Border 自身由 BorderRenderer 处理（切片 3.5）
        flush();
    }

    @Override
    public void visitText(TextPaintCommand command) {
        flush();
    }

    @Override
    public void visitClipPush(ClipPushCommand command) {
        if (finished) return;
        // ClipPush 改变 clip 上下文 → 立即刷出 pending（保证批内 clip 一致）
        flush();
        // 与当前栈顶求交集，结果作为新栈顶
        ClipRect newClip = intersectWithCurrent(
                command.x(), command.y(), command.width(), command.height());
        clipStack.push(newClip);
        currentClip = newClip;
    }

    @Override
    public void visitClipPop(ClipPopCommand command) {
        if (finished) return;
        // ClipPop 改变 clip 上下文 → 立即刷出 pending
        flush();
        // 防御性：栈空时 pop 为 no-op（与 ClipPopCommand 文档一致）
        if (!clipStack.isEmpty()) {
            clipStack.pop();
            currentClip = clipStack.isEmpty() ? null : clipStack.peek();
        }
    }

    /** 刷出 pending 批到 sink。空 pending 为 no-op。 */
    private void flush() {
        if (pending.isEmpty()) {
            return;
        }
        sink.emit(new RectangleBatch(pending, currentClip));
        pending.clear();
    }

    /** 刷出最后一批并标记完成。幂等。 */
    public void finish() {
        if (finished) {
            return;
        }
        finished = true;
        flush();
    }

    /**
     * 计算给定矩形与当前 clip 的交集。
     * 当前 clip 为 null 时直接返回给定矩形（无上层裁剪）。
     * 交集为空（无重叠）时返回 0×0 矩形（renderer 应跳过该批）。
     */
    private ClipRect intersectWithCurrent(float x, float y, float w, float h) {
        if (currentClip == null) {
            return new ClipRect(x, y, w, h);
        }
        float x0 = Math.max(x, currentClip.x());
        float y0 = Math.max(y, currentClip.y());
        float x1 = Math.min(x + w, currentClip.x() + currentClip.width());
        float y1 = Math.min(y + h, currentClip.y() + currentClip.height());
        float iw = x1 - x0;
        float ih = y1 - y0;
        if (iw < 0f || ih < 0f) {
            // 无重叠：返回 0×0 clip；批内所有 rect 都会被裁掉
            return new ClipRect(x0, y0, 0f, 0f);
        }
        return new ClipRect(x0, y0, iw, ih);
    }
}
