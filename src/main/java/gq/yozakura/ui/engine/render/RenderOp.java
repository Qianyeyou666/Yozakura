package gq.yozakura.ui.engine.render;

/**
 * 渲染操作：{@link PaintCommandDispatcher} 输出的有序渲染指令。
 *
 * <p>一个 RenderOp 代表一次潜在的 GL draw call（或一组可合并的 draw call），
 * 由 dispatcher 在 visit/flush 时构造，提交给 {@link RenderOpSink}。
 *
 * <p>种类：
 * <ul>
 *   <li>{@link #KIND_RECTANGLE}：由 {@link BatchedRectangleRenderer} 刷出的
 *       {@link RectangleRenderOp}，封装一组共享 clip 的 RectFill</li>
 *   <li>{@link #KIND_BORDER}：由 {@link BorderRenderer} 刷出的
 *       {@link BorderRenderOp}，封装一组共享 color+clip 的 RectBorder</li>
 * </ul>
 *
 * <p>host（如 LwjglRenderHost，阶段 6.4+）按 sink 接收顺序遍历 ops，
 * 对每个 op：
 * <ol>
 *   <li>设置 GL_SCISSOR 到 {@link #clipRect()}（null 则禁用 scissor）</li>
 *   <li>根据 kind 上传顶点并 glDrawElements</li>
 * </ol>
 *
 * <p>不可变值对象。子类封装各自的 batch，构造后状态不可变。
 */
public abstract class RenderOp {

    /** 矩形填充批 op（{@link RectangleRenderOp}）。 */
    public static final int KIND_RECTANGLE = 1;
    /** 矩形描边批 op（{@link BorderRenderOp}）。 */
    public static final int KIND_BORDER = 2;
    /** Text glyph operation ({@link TextRenderOp}). */
    public static final int KIND_TEXT = 3;

    /** 本 op 的种类常量，用于 host 分派绘制路径。 */
    public abstract int kind();

    /**
     * 本 op 的 clip 矩形（逻辑像素）。
     * null 表示无裁剪；非 null 表示 host 应将 GL_SCISSOR 设为该矩形。
     */
    public abstract ClipRect clipRect();
}
