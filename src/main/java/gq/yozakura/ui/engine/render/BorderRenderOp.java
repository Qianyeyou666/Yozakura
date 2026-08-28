package gq.yozakura.ui.engine.render;

/**
 * 矩形描边渲染操作：封装一个 {@link BorderBatch}。
 *
 * <p>由 {@link PaintCommandDispatcher} 的内部 {@link BorderRenderer}
 * 在刷出批时通过适配 sink 构造并提交给 {@link RenderOpSink}。
 *
 * <p>host 处理本 op：
 * <ol>
 *   <li>设置 GL_SCISSOR 到 {@link #clipRect()}（null 时禁用 scissor）</li>
 *   <li>通过 {@link BorderGeometry} 将每个 border 分解为三角形并累积顶点</li>
 *   <li>设置 uniform color 为 {@link BorderBatch#color()}，单次 glDrawElements</li>
 * </ol>
 *
 * <p>不可变值对象；{@link #batch()} 自身亦不可变（{@link BorderBatch} 防御性拷贝）。
 */
public final class BorderRenderOp extends RenderOp {

    private final BorderBatch batch;

    public BorderRenderOp(BorderBatch batch) {
        if (batch == null) {
            throw new IllegalArgumentException("batch must not be null");
        }
        this.batch = batch;
    }

    /** 本 op 封装的 border 批。 */
    public BorderBatch batch() {
        return batch;
    }

    @Override
    public int kind() {
        return KIND_BORDER;
    }

    @Override
    public ClipRect clipRect() {
        return batch.clipRect();
    }
}
