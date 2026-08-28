package gq.yozakura.ui.engine.minecraft;

/**
 * 视口坐标转换：在逻辑坐标、Minecraft scaled 坐标与物理像素之间转换。
 *
 * <p>本引擎约定：逻辑坐标 = Minecraft scaled 坐标（与 MC 1.8.9 ScaledResolution 一致）。
 * 因此：
 * <ul>
 *   <li>{@code logicalToPhysical(x) = x * scale}</li>
 *   <li>{@code physicalToLogical(p) = p / scale}</li>
 * </ul>
 *
 * <p>渲染、布局、命中测试共享一个逻辑坐标空间；物理 ↔ 逻辑 转换只在 host/viewport 层进行
 * （AGENTS.md "Input and Coordinates"）。本类是该层的纯函数式抽象，不依赖 GL 或 Minecraft 类，
 * 便于单测与多后端复用。
 *
 * <p>支持整数 GUI Scale（1/2/3，MC 1.8.9 标准）与非整数 GUI Scale（Forge mod 扩展）。
 * 逻辑尺寸 = floor(fbDim / scale)，与 MC ScaledResolution 行为一致
 * （fbW 不能被 scale 整除时丢弃余数）。
 *
 * <p>亚像素对齐：{@link #alignToPhysicalPixel(float)} 将逻辑坐标对齐到最近的物理像素边界
 * （向下取整），用于消除文字与 1px 边线的亚像素模糊。
 * 公式：{@code floor(x * scale) / scale}。
 *
 * <p>不可变值对象。线程安全。
 */
public final class ViewportTransform {

    private final float scale;
    private final int framebufferWidth;
    private final int framebufferHeight;
    private final int logicalWidth;
    private final int logicalHeight;

    /**
     * @param scale             GUI Scale，必须为有限正数（支持非整数）
     * @param framebufferWidth  物理帧缓冲宽度，必须 > 0
     * @param framebufferHeight 物理帧缓冲高度，必须 > 0
     */
    public ViewportTransform(float scale, int framebufferWidth, int framebufferHeight) {
        if (Float.isNaN(scale) || Float.isInfinite(scale) || scale <= 0f) {
            throw new IllegalArgumentException("scale must be a positive finite number: " + scale);
        }
        if (framebufferWidth <= 0) {
            throw new IllegalArgumentException("framebufferWidth must be > 0: " + framebufferWidth);
        }
        if (framebufferHeight <= 0) {
            throw new IllegalArgumentException("framebufferHeight must be > 0: " + framebufferHeight);
        }
        this.scale = scale;
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
        // MC ScaledResolution 行为：丢弃不能整除的余数
        this.logicalWidth = (int) Math.floor(framebufferWidth / scale);
        this.logicalHeight = (int) Math.floor(framebufferHeight / scale);
    }

    /** GUI Scale 因子（可能为非整数）。 */
    public float scale() {
        return scale;
    }

    /** 物理帧缓冲宽度（像素）。 */
    public int framebufferWidth() {
        return framebufferWidth;
    }

    /** 物理帧缓冲高度（像素）。 */
    public int framebufferHeight() {
        return framebufferHeight;
    }

    /** 逻辑视口宽度 = floor(fbW / scale)。 */
    public int logicalWidth() {
        return logicalWidth;
    }

    /** 逻辑视口高度 = floor(fbH / scale)。 */
    public int logicalHeight() {
        return logicalHeight;
    }

    /** 物理 X → 逻辑 X：p / scale。 */
    public float physicalToLogicalX(float physicalX) {
        return physicalX / scale;
    }

    /** 物理 Y → 逻辑 Y：p / scale。 */
    public float physicalToLogicalY(float physicalY) {
        return physicalY / scale;
    }

    /** 逻辑 X → 物理 X：x * scale。 */
    public float logicalToPhysicalX(float logicalX) {
        return logicalX * scale;
    }

    /** 逻辑 Y → 物理 Y：y * scale。 */
    public float logicalToPhysicalY(float logicalY) {
        return logicalY * scale;
    }

    /**
     * 将逻辑坐标对齐到最近的物理像素边界（向下取整）。
     *
     * <p>公式：{@code floor(x * scale) / scale}。
     * 用于消除文字与 1px 边线在非整数 scale 下的亚像素模糊。
     *
     * <p>负数行为：{@code Math.floor} 对负数向下取整（更负），
     * 保证物理像素索引单调对应逻辑坐标区间。
     */
    public float alignToPhysicalPixel(float logicalCoordinate) {
        return (float) Math.floor(logicalCoordinate * scale) / scale;
    }
}
