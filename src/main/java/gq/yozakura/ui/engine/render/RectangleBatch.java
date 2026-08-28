package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.RectFillCommand;

import java.util.Collections;
import java.util.List;

/**
 * 矩形批：一组共享相同 clip 上下文（与 texture）的 RectFill 命令的不可变快照。
 *
 * <p>由 {@link BatchedRectangleRenderer} 在 clip 上下文改变、RectBorder 命令出现或
 * {@link BatchedRectangleRenderer#finish()} 时刷出，提交给 {@link RectangleBatchSink}。
 *
 * <p>renderer（生产实现 LwjglRectangleRenderer）一次 draw call 绘制一批：
 * <ol>
 *   <li>设置 GL_SCISSOR 到 {@link #clipRect()}（若非 null）</li>
 *   <li>上传 / 复用批内顶点缓冲（每矩形 4 顶点 6 索引）</li>
 *   <li>单次 glDrawElements 绘制全部矩形</li>
 * </ol>
 *
 * <p>本类不持有 GL 资源；可在任意线程构造。clipRect 为 null 表示无裁剪
 * （renderer 应禁用 GL_SCISSOR_TEST 或恢复为 framebuffer 全域）。
 *
 * <p>不可变值对象：构造后命令列表与 clipRect 不可变。
 */
public final class RectangleBatch {

    private final List<RectFillCommand> rects;
    private final ClipRect clipRect;

    public RectangleBatch(List<RectFillCommand> rects, ClipRect clipRect) {
        if (rects == null) {
            throw new IllegalArgumentException("rects must not be null");
        }
        if (rects.isEmpty()) {
            throw new IllegalArgumentException("rects must not be empty");
        }
        // 防御性拷贝：renderer 后续可能复用源 list，本批必须独立快照
        this.rects = Collections.unmodifiableList(
                new java.util.ArrayList<RectFillCommand>(rects));
        this.clipRect = clipRect;
    }

    /** 批内矩形数量。 */
    public int rectCount() {
        return rects.size();
    }

    /** 第 index 个矩形命令（按追加顺序）。 */
    public RectFillCommand rect(int index) {
        return rects.get(index);
    }

    /** 便捷取第 index 个矩形的 x。 */
    public float rectX(int index) {
        return rects.get(index).x();
    }

    /** 便捷取第 index 个矩形的 y。 */
    public float rectY(int index) {
        return rects.get(index).y();
    }

    /** 便捷取第 index 个矩形的 width。 */
    public float rectWidth(int index) {
        return rects.get(index).width();
    }

    /** 便捷取第 index 个矩形的 height。 */
    public float rectHeight(int index) {
        return rects.get(index).height();
    }

    /** 便捷取第 index 个矩形的颜色。 */
    public gq.yozakura.ui.engine.paint.Color rectColor(int index) {
        return rects.get(index).color();
    }

    /**
     * 本批的 clip 矩形（逻辑像素坐标）。
     * null 表示无裁剪；非 null 表示 renderer 应将 GL_SCISSOR 设为该矩形。
     */
    public ClipRect clipRect() {
        return clipRect;
    }

    /** 不可变命令视图。 */
    public List<RectFillCommand> rects() {
        return rects;
    }
}
