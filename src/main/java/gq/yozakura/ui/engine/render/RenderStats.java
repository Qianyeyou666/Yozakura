package gq.yozakura.ui.engine.render;

/**
 * 渲染性能计数器：单帧累积的 draw call、op、clip 变化、compile cache 命中统计。
 *
 * <p>AGENTS.md 要求"Optimization must be evidence-based. Add counters or benchmarks
 * before introducing complex caches."。本类提供 baseline 与回归验证的量化依据。
 *
 * <p>使用方式：
 * <ol>
 *   <li>host 每帧入口调用 {@link #reset()} 清零</li>
 *   <li>LwjglUiRenderer 在 render 路径递增各字段</li>
 *   <li>帧末读取字段值；可经日志或 on-screen counter 暴露</li>
 * </ol>
 *
 * <p>非线程安全：仅渲染线程访问（OpenGL 上下文线程）。
 * 字段为 public 可变：renderer 直接写入避免方法调用开销（per-frame 热路径）。
 */
public final class RenderStats {
    /** 一次 render() 调用至今处理的 RenderOp 总数。 */
    public int opCount;
    /** KIND_RECTANGLE op 数量。 */
    public int rectOps;
    /** KIND_BORDER op 数量。 */
    public int borderOps;
    /** KIND_TEXT op 数量。 */
    public int textOps;
    /** 实际 draw call 等价路径调用次数（drawRectangles + drawBorders + drawText）。 */
    public int drawCalls;
    /** applyClip 检测到 clip 实际变化的次数（剔除无变化的冗余 GL 调用后）。 */
    public int clipChanges;
    /** compile() 命中缓存的次数。 */
    public int compileHits;
    /** compile() 未命中缓存的次数（发生实际重编译）。 */
    public int compileMisses;
    /** 文本布局缓存命中次数（layoutText 命中 textLayouts）。 */
    public int textLayoutHits;
    /** 文本布局缓存未命中次数（触发 layoutText 计算）。 */
    public int textLayoutMisses;
    /** 文本布局缓存淘汰条目数（LRU 收缩时累加）。 */
    public int textLayoutEvictions;
    /** GlStateAccess.capture/restore 调用次数（每次 GlStateGuard 构造 +1，close +1）。 */
    public int stateSnapshots;

    /** 全部字段清零。每帧入口调用一次。 */
    public void reset() {
        opCount = 0;
        rectOps = 0;
        borderOps = 0;
        textOps = 0;
        drawCalls = 0;
        clipChanges = 0;
        compileHits = 0;
        compileMisses = 0;
        textLayoutHits = 0;
        textLayoutMisses = 0;
        textLayoutEvictions = 0;
        stateSnapshots = 0;
    }

    /** 便捷视图：compile cache 命中率（0~1，无数据时为 0）。 */
    public float compileHitRate() {
        int total = compileHits + compileMisses;
        if (total == 0) return 0.0F;
        return (float) compileHits / total;
    }

    /** 便捷视图：文本布局缓存命中率。 */
    public float textLayoutHitRate() {
        int total = textLayoutHits + textLayoutMisses;
        if (total == 0) return 0.0F;
        return (float) textLayoutHits / total;
    }
}
