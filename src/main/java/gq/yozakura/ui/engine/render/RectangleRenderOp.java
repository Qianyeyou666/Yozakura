package gq.yozakura.ui.engine.render;

/**
 * 矩形填充渲染操作：封装一个 {@link RectangleBatch}。
 *
 * <p>由 {@link PaintCommandDispatcher} 的内部 {@link BatchedRectangleRenderer}
 * 在刷出批时通过适配 sink 构造并提交给 {@link RenderOpSink}。
 *
 * <p>host 处理本 op：
 * <ol>
 *   <li>设置 GL_SCISSOR 到 {@link #clipRect()}（null 时禁用 scissor）</li>
 *   <li>上传批内顶点（每矩形 4 顶点 6 索引）</li>
 *   <li>单次 glDrawElements 绘制全部矩形</li>
 * </ol>
 *
 * <p>不可变值对象；{@link #batch()} 自身亦不可变（{@link RectangleBatch} 防御性拷贝）。
 */
public final class RectangleRenderOp extends RenderOp {

    private final RectangleBatch batch;

    public RectangleRenderOp(RectangleBatch batch) {
        if (batch == null) {
            throw new IllegalArgumentException("batch must not be null");
        }
        this.batch = batch;
    }

    /** 本 op 封装的矩形批。 */
    public RectangleBatch batch() {
        return batch;
    }

    @Override
    public int kind() {
        return KIND_RECTANGLE;
    }

    @Override
    public ClipRect clipRect() {
        return batch.clipRect();
    }
}
