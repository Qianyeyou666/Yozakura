package gq.vapulite.Vapu.VapeClickGui.utils;

import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * 固定容量的细分曲面实现，使用直接缓冲区存储 OpenGL 顶点数据。
 * <p>
 * 每个顶点包含 6 个 int 分量：x, y, z（坐标）、color（颜色）、u, v（纹理坐标）。
 * 初始容量在构造时确定，超出容量不会自动扩容。
 * <p>
 * 需要动态扩容请使用 {@link ExpandingTess}。
 */
public class BasicTess implements Tessellation {
    /** 当前已添加的顶点索引 */
    int index;

    /** 原始 int 数组缓冲区，用于暂存顶点数据 */
    int[] raw;

    /** 直接字节缓冲区，用于 OpenGL 传输 */
    ByteBuffer buffer;

    /** 字节缓冲区的 FloatBuffer 视图 */
    FloatBuffer fBuffer;

    /** 字节缓冲区的 IntBuffer 视图 */
    IntBuffer iBuffer;

    /** 当前顶点颜色值 */
    private int colors;

    /** 当前顶点纹理 U 坐标 */
    private float texU;

    /** 当前顶点纹理 V 坐标 */
    private float texV;

    /** 是否已设置颜色 */
    private boolean color;

    /** 是否已设置纹理 */
    private boolean texture;

    /**
     * 构造一个固定容量的细分曲面。
     *
     * @param capacity 初始顶点容量（内部会乘以 6 以容纳所有分量）
     */
    BasicTess(int capacity) {
        capacity *= 6;
        this.raw = new int[capacity];
        this.buffer = ByteBuffer.allocateDirect(capacity * 4).order(ByteOrder.nativeOrder());
        this.fBuffer = this.buffer.asFloatBuffer();
        this.iBuffer = this.buffer.asIntBuffer();
    }

    @Override
    public Tessellation setColor(int color) {
        this.color = true;
        this.colors = color;
        return this;
    }

    @Override
    public Tessellation setTexture(float u, float v) {
        this.texture = true;
        this.texU = u;
        this.texV = v;
        return this;
    }

    @Override
    public Tessellation addVertex(float x, float y, float z) {
        int dex = this.index * 6;
        // 将顶点数据写入 raw 数组（以 int 形式存储浮点数的位表示）
        this.raw[dex] = Float.floatToRawIntBits(x);
        this.raw[dex + 1] = Float.floatToRawIntBits(y);
        this.raw[dex + 2] = Float.floatToRawIntBits(z);
        this.raw[dex + 3] = this.colors;
        this.raw[dex + 4] = Float.floatToRawIntBits(this.texU);
        this.raw[dex + 5] = Float.floatToRawIntBits(this.texV);
        this.index++;
        return this;
    }

    @Override
    public Tessellation bind() {
        int dex = this.index * 6;
        // 将 raw 数组复制到直接缓冲区
        this.iBuffer.put(this.raw, 0, dex);
        this.buffer.position(0);
        this.buffer.limit(dex * 4);
        // 如果设置了颜色，配置颜色指针（从偏移 12 开始，步长 24 字节）
        if (this.color) {
            this.buffer.position(12);
            GL11.glColorPointer(4, true, 24, this.buffer);
        }
        // 如果设置了纹理，配置纹理指针（从偏移 16 开始，步长 24 字节）
        if (this.texture) {
            this.fBuffer.position(4);
            GL11.glTexCoordPointer(2, 24, this.fBuffer);
        }
        // 配置顶点指针（从偏移 0 开始，步长 24 字节）
        this.fBuffer.position(0);
        GL11.glVertexPointer(3, 24, this.fBuffer);
        return this;
    }

    @Override
    public Tessellation pass(int mode) {
        GL11.glDrawArrays(mode, 0, this.index);
        return this;
    }

    @Override
    public Tessellation unbind() {
        this.iBuffer.position(0);
        return this;
    }

    @Override
    public Tessellation reset() {
        this.iBuffer.clear();
        this.index = 0;
        this.color = false;
        this.texture = false;
        return this;
    }
}
