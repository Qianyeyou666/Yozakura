package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.Color;
import gq.yozakura.ui.engine.paint.RectBorderCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Border 批：一组共享相同 color 与 clip 上下文的 RectBorder 命令的不可变快照。
 *
 * <p>由 {@link BorderRenderer} 在 color/clip 改变或 {@link BorderRenderer#finish()} 时刷出，
 * 提交给 {@link BorderBatchSink}。
 *
 * <p>renderer 一次 draw call 绘制一批：
 * <ol>
 *   <li>设置 GL_SCISSOR 到 {@link #clipRect()}（若非 null）</li>
 *   <li>通过 {@link BorderGeometry} 分解每个 border 为三角形，累积到顶点缓冲</li>
 *   <li>设置 uniform color 为 {@link #color()}，单次 glDrawElements 绘制全部 border</li>
 * </ol>
 *
 * <p>批内所有 border 共享同一 color（来自首个命令），不同 color 的 border 会被分到不同批。
 * 几何（radius、各边宽度）不影响批次划分——同 color 同 clip 即可合并。
 *
 * <p>本类不持有 GL 资源；可在任意线程构造。clipRect 为 null 表示无裁剪
 * （renderer 应禁用 GL_SCISSOR_TEST 或恢复为 framebuffer 全域）。
 *
 * <p>不可变值对象：构造后命令列表、color 与 clipRect 不可变。
 */
public final class BorderBatch {

    private final List<RectBorderCommand> borders;
    private final Color color;
    private final ClipRect clipRect;

    public BorderBatch(List<RectBorderCommand> borders, Color color, ClipRect clipRect) {
        if (borders == null) {
            throw new IllegalArgumentException("borders must not be null");
        }
        if (borders.isEmpty()) {
            throw new IllegalArgumentException("borders must not be empty");
        }
        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        // 防御性拷贝：renderer 后续可能复用源 list，本批必须独立快照
        this.borders = Collections.unmodifiableList(
                new ArrayList<RectBorderCommand>(borders));
        this.color = color;
        this.clipRect = clipRect;
    }

    /** 批内 border 数量。 */
    public int borderCount() {
        return borders.size();
    }

    /** 第 index 个 border 命令（按追加顺序）。 */
    public RectBorderCommand border(int index) {
        return borders.get(index);
    }

    /** 本批共享的颜色（来自首个 border）。 */
    public Color color() {
        return color;
    }

    /**
     * 本批的 clip 矩形（逻辑像素坐标）。
     * null 表示无裁剪；非 null 表示 renderer 应将 GL_SCISSOR 设为该矩形。
     */
    public ClipRect clipRect() {
        return clipRect;
    }

    /** 不可变命令视图。 */
    public List<RectBorderCommand> borders() {
        return borders;
    }
}
