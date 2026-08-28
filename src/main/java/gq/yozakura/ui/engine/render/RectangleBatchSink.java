package gq.yozakura.ui.engine.render;

/**
 * 矩形批接收端：由 renderer 实现以消费 {@link RectangleBatch}。
 *
 * <p>每次 {@link BatchedRectangleRenderer} 累积的批被刷出时调用 {@link #emit(RectangleBatch)}。
 * 实现可在 emit 时立即绘制，也可缓存批列表用于 retained-mode 重放。
 *
 * <p>emit 调用顺序严格对应命令列表的回放顺序，保证视觉层级正确。
 */
public interface RectangleBatchSink {
    void emit(RectangleBatch batch);
}
