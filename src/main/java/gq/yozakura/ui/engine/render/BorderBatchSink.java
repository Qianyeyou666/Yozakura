package gq.yozakura.ui.engine.render;

/**
 * Border 批接收端：由 renderer 实现以消费 {@link BorderBatch}。
 *
 * <p>每次 {@link BorderRenderer} 累积的批被刷出时调用 {@link #emit(BorderBatch)}。
 * 实现可在 emit 时立即绘制，也可缓存批列表用于 retained-mode 重放。
 *
 * <p>emit 调用顺序严格对应命令列表的回放顺序，保证视觉层级正确。
 */
public interface BorderBatchSink {
    void emit(BorderBatch batch);
}
