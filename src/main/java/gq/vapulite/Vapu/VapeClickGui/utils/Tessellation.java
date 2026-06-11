package gq.vapulite.Vapu.VapeClickGui.utils;

import java.awt.*;

/**
 * 细分曲面（Tessellation）接口，用于 OpenGL 顶点数据的构建与渲染。
 * <p>
 * 提供顶点添加、颜色/纹理设置、缓冲区绑定以及绘制操作的方法。
 * 支持通过 {@link #createBasic} 和 {@link #createExpanding} 工厂方法创建实例。
 * <p>
 * 实现类：{@link BasicTess}（固定容量）、{@link ExpandingTess}（自动扩容）
 */
public interface Tessellation {
    /**
     * 创建一个固定容量的基础细分曲面实例。
     *
     * @param size 初始顶点数
     * @return 新的 BasicTess 实例
     */
    static Tessellation createBasic(int size) {
        return new BasicTess(size);
    }

    /**
     * 创建一个可自动扩容的细分曲面实例。
     *
     * @param size   初始顶点数
     * @param ratio  触发扩容的容量比例阈值
     * @param factor 扩容因子
     * @return 新的 ExpandingTess 实例
     */
    static Tessellation createExpanding(int size, float ratio, float factor) {
        return new ExpandingTess(size, ratio, factor);
    }

    /** 设置当前顶点的颜色 */
    Tessellation setColor(int var1);

    /** 设置当前顶点的颜色（Color 对象，默认转为白色） */
    default Tessellation setColor(Color color) {
        return this.setColor(new Color(255, 255, 255));
    }

    /** 设置当前顶点的纹理坐标 */
    Tessellation setTexture(float var1, float var2);

    /** 添加一个顶点到缓冲区 */
    Tessellation addVertex(float var1, float var2, float var3);

    /** 绑定顶点缓冲区到 OpenGL */
    Tessellation bind();

    /** 执行 OpenGL 绘制调用 */
    Tessellation pass(int var1);

    /** 重置缓冲区，清空所有顶点数据 */
    Tessellation reset();

    /** 解绑顶点缓冲区 */
    Tessellation unbind();

    /** 便捷方法：绑定 → 绘制 → 重置，一步完成 */
    default Tessellation draw(int mode) {
        return this.bind().pass(mode).reset();
    }
}
