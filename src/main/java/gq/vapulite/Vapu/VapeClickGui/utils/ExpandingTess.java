package gq.vapulite.Vapu.VapeClickGui.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 可自动扩容的细分曲面实现，继承自 {@link BasicTess}。
 * <p>
 * 当已使用的顶点容量达到总容量的 {@code ratio} 比例时，
 * 自动将内部缓冲区扩容至原大小的 {@code factor} 倍。
 * 适用于顶点数量不确定或需要动态增长的场景。
 */
public class ExpandingTess
        extends BasicTess {
    /** 触发扩容的容量比例阈值（0.0~1.0） */
    private final float ratio;
    /** 扩容因子，容量不足时乘以该值 */
    private final float factor;

    /**
     * 构造一个可自动扩容的细分曲面。
     *
     * @param initial 初始顶点容量
     * @param ratio   触发扩容的比例阈值（如 0.75 表示使用 75% 时扩容）
     * @param factor  扩容倍数（如 2.0 表示容量翻倍）
     */
    ExpandingTess(int initial, float ratio, float factor) {
        super(initial);
        this.ratio = ratio;
        this.factor = factor;
    }

    @Override
    public Tessellation addVertex(float x, float y, float z) {
        int capacity = this.raw.length;
        // 检查是否需要扩容：当前使用量达到容量比例阈值时触发
        if ((float) (this.index * 6) >= (float) capacity * this.ratio) {
            // 计算新容量并创建更大的缓冲区
            capacity = (int) ((float) capacity * this.factor);
            int[] newBuffer = new int[capacity];
            System.arraycopy(this.raw, 0, newBuffer, 0, this.raw.length);
            this.raw = newBuffer;
            // 重建直接缓冲区以匹配新容量
            this.buffer = ByteBuffer.allocateDirect(capacity * 4).order(ByteOrder.nativeOrder());
            this.iBuffer = this.buffer.asIntBuffer();
            this.fBuffer = this.buffer.asFloatBuffer();
        }
        return super.addVertex(x, y, z);
    }
}
