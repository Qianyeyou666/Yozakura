package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.ClipPopCommand;
import gq.yozakura.ui.engine.paint.ClipPushCommand;
import gq.yozakura.ui.engine.paint.Color;
import gq.yozakura.ui.engine.paint.PaintCommandVisitor;
import gq.yozakura.ui.engine.paint.RectBorderCommand;
import gq.yozakura.ui.engine.paint.RectFillCommand;
import gq.yozakura.ui.engine.paint.TextPaintCommand;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Border 批处理器：遍历 {@link gq.yozakura.ui.engine.paint.PaintCommandList}，
 * 将连续的 {@link RectBorderCommand} 按 (color, clip) 上下文累积为 {@link BorderBatch}，
 * 提交给 {@link BorderBatchSink}。
 *
 * <p>批处理契约（AGENTS.md：按 shader/texture/clip 批处理，最小化 draw call）：
 * <ul>
 *   <li>相同 (color, clip) 下的连续 RectBorder 累积到 pending，{@link #finish()} 时刷出为一批</li>
 *   <li>不同 color → 立即刷出 pending，开始新批（color 是批的 uniform，必须一致）</li>
 *   <li>ClipPush/ClipPop 改变 clip 上下文 → 立即刷出 pending（保证批内 clip 一致）</li>
 *   <li>ClipPush 时与当前栈顶 clip 求交集，结果作为新的栈顶（嵌套裁剪）</li>
 *   <li>ClipPop 弹出栈顶；栈空时 currentClip 回到 null（无裁剪）</li>
 *   <li>RectFill 出现 → 立即刷出 pending（保证视觉顺序：border → fill → next border）</li>
 *   <li>几何（radius、各边宽度）不影响批次划分——同 color 同 clip 即可合并</li>
 *   <li>{@link #finish()} 幂等：重复调用不重复刷出</li>
 * </ul>
 *
 * <p>设计取舍：与 {@link BatchedRectangleRenderer} 一致选择"任何 clip 操作都打断批"的保守策略，
 * 保证视觉层级正确，避免透明叠加顺序问题。
 *
 * <p>不持有 GL 资源；sink 由调用方注入。生产实现 {@code LwjglBorderBatchSink}
 * 在 emit 时通过 {@link BorderGeometry} 分解为三角形并调用 glDrawElements。
 *
 * <p>零分配目标：visitRectBorder 路径仅在 pending 容量不足时分配；
 * flush 时 BorderBatch 构造做一次防御性拷贝（retained 后只读访问）。
 */
public final class BorderRenderer implements PaintCommandVisitor {

    private final BorderBatchSink sink;
    private final List<RectBorderCommand> pending = new ArrayList<RectBorderCommand>();
    private final Deque<ClipRect> clipStack = new ArrayDeque<ClipRect>();
    private Color pendingColor;     // 当前 pending 批的颜色；pending 空时为 null
    private ClipRect currentClip;   // 栈顶；栈空时为 null（无裁剪）
    private boolean finished;

    public BorderRenderer(BorderBatchSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        this.sink = sink;
    }

    /** 当前 clip 上下文（栈顶或 null）。测试与外部调试用。 */
    public ClipRect currentClip() {
        return currentClip;
    }

    /** 当前 pending 批中的 border 数量（未刷出）。测试用。 */
    public int pendingCount() {
        return pending.size();
    }

    @Override
    public void visitRectBorder(RectBorderCommand command) {
        if (finished) {
            // finish 后的命令静默丢弃：retained 模式下命令列表已固化
            return;
        }
        Color c = command.color();
        // 颜色变化 → 刷出当前 pending，开始新批
        if (!pending.isEmpty() && !pendingColor.equals(c)) {
            flush();
        }
        // pending 为空时初始化 pendingColor（标志新批开始）
        if (pending.isEmpty()) {
            pendingColor = c;
        }
        pending.add(command);
    }

    @Override
    public void visitRectFill(RectFillCommand command) {
        if (finished) {
            return;
        }
        // RectFill 走不同渲染路径（不同 shader/几何），打断 border 批
        // 保证视觉顺序：pending border 必须先于此 fill 绘制
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
        sink.emit(new BorderBatch(pending, pendingColor, currentClip));
        pending.clear();
        pendingColor = null;
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
            // 无重叠：返回 0×0 clip；批内所有 border 都会被裁掉
            return new ClipRect(x0, y0, 0f, 0f);
        }
        return new ClipRect(x0, y0, iw, ih);
    }
}
