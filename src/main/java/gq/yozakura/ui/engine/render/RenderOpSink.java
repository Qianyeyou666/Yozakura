package gq.yozakura.ui.engine.render;

/**
 * 渲染操作接收端：由 host 实现以消费 {@link RenderOp} 流。
 *
 * <p>{@link PaintCommandDispatcher} 在内部两端 renderer 刷出批时，
 * 通过适配器包装为对应 {@link RenderOp} 提交给唯一 sink。
 *
 * <p>emit 调用顺序严格对应 paint 命令列表的回放顺序（背景→border→子元素），
 * 保证 host 按此顺序绘制即可得到正确视觉层级。
 *
 * <p>实现可选：
 * <ul>
 *   <li>立即绘制（生产 LwjglRenderHost：emit 时设 scissor + drawElements）</li>
 *   <li>缓存列表用于 retained 重放（{@code CapturingRenderOpSink}：append 到 List）</li>
 *   <li>丢弃（基准测试 sink：计数但不绘制）</li>
 * </ul>
 */
public interface RenderOpSink {

    /**
     * 接收一个渲染操作。
     *
     * @param op 非 null；按 dispatcher 刷出顺序调用
     */
    void emit(RenderOp op);
}
