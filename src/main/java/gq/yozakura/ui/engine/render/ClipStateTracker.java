package gq.yozakura.ui.engine.render;

/**
 * 增量 clip 状态跟踪：判断连续 applyClip 调用是否实际发生 clip 变化，
 * 让 renderer 跳过冗余的 glEnable/glScissor 调用。
 *
 * <p>AGENTS.md 性能目标：local hover/toggle updates limited to affected nodes。
 * 连续相同 clip 的 op 不应重复设置 scissor。LwjglUiRenderer 主循环每个 op 都调 applyClip，
 * 在 ClickGUI 中常见"同 clip 下连续多个 rect/text op"——增量跟踪可省去每 op 2 次 GL 调用。
 *
 * <p>线程模型：单线程（渲染线程）。非线程安全。
 *
 * <p>语义：
 * <ul>
 *   <li>初始状态：disabled，无 scissor box</li>
 *   <li>{@link #update(ClipRect)} 返回 true 表示状态实际变化，调用方应执行 GL 调用</li>
 *   <li>返回 false 表示状态未变，调用方可跳过 GL 调用</li>
 *   <li>{@link #reset()} 用于 render 入口清零到初始状态</li>
 * </ul>
 *
 * <p>坐标按 ClipRect 字段比对，使用 Float.floatToIntBits 避免浮点误差导致 false positive。
 */
public final class ClipStateTracker {
    private static final int DISABLED = 0;
    private static final int ENABLED = 1;
    private static final int UNKNOWN = 2;

    private int state = UNKNOWN;
    private int clipXBits;
    private int clipYBits;
    private int clipWidthBits;
    private int clipHeightBits;

    /** 重置到未知状态，强制下一次 update 写入。在 render 入口或 GlStateGuard 边界调用。 */
    public void reset() {
        state = UNKNOWN;
    }

    /**
     * 更新 clip 状态。
     *
     * @param clip 当前 op 的 clip 矩形，null 表示无裁剪
     * @return true 表示状态实际变化，调用方应执行 glEnable/glScissor；false 表示无变化可跳过
     */
    public boolean update(ClipRect clip) {
        if (clip == null) {
            if (state == DISABLED) {
                return false;
            }
            state = DISABLED;
            return true;
        }
        int xBits = Float.floatToIntBits(clip.x());
        int yBits = Float.floatToIntBits(clip.y());
        int widthBits = Float.floatToIntBits(clip.width());
        int heightBits = Float.floatToIntBits(clip.height());
        if (state == ENABLED
                && clipXBits == xBits
                && clipYBits == yBits
                && clipWidthBits == widthBits
                && clipHeightBits == heightBits) {
            return false;
        }
        state = ENABLED;
        clipXBits = xBits;
        clipYBits = yBits;
        clipWidthBits = widthBits;
        clipHeightBits = heightBits;
        return true;
    }

    /** 当前是否处于 enabled 状态。 */
    public boolean isEnabled() {
        return state == ENABLED;
    }
}
