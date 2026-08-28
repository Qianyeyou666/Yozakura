package gq.yozakura.ui.engine.animation;

/**
 * 单调动画时钟：阶段 5 唯一时间源。
 *
 * <p>AGENTS.md 契约：
 * <ul>
 *   <li>"Use one monotonic clock. Animation progress is time-based, not frame-count-based."</li>
 *   <li>"Keep rendering active while an animation is running, then return to the static retained path."</li>
 * </ul>
 *
 * <p>设计：
 * <ul>
 *   <li>时间单位毫秒（与 transition duration 单位一致）</li>
 *   <li>时间单调不可回退（{@link #advanceTo} 拒绝回退值）</li>
 *   <li>活跃动画计数：动画启动时 {@link #registerActive}，结束时 {@link #unregisterActive}；
 *       {@link #hasActiveAnimations()} 由 host 层查询决定是否继续渲染</li>
 *   <li>测试可控：{@link #advanceTo} / {@link #advanceBy} 注入时间，不依赖 System.nanoTime</li>
 * </ul>
 *
 * <p>线程模型：单线程（UI 线程）。非线程安全。
 *
 * <p>注意：本类不持有动画实例引用；仅维护时间与活跃计数。
 * TransitionRunner 在动画启动/结束时调用 register/unregister。
 */
public final class AnimationClock {

    private long nowMillis;
    private int activeCount;

    public AnimationClock() {
        this.nowMillis = 0L;
        this.activeCount = 0;
    }

    /** 当前时间（毫秒）。 */
    public long nowMillis() {
        return nowMillis;
    }

    /**
     * 推进到绝对时间。时间单调不可回退。
     *
     * @param timeMs 绝对时间（&gt;= 当前时间，&gt;= 0）
     */
    public void advanceTo(long timeMs) {
        if (timeMs < 0L) {
            throw new IllegalArgumentException("time must not be negative: " + timeMs);
        }
        if (timeMs < nowMillis) {
            throw new IllegalArgumentException(
                    "time must not go backward: now=" + nowMillis + " requested=" + timeMs);
        }
        nowMillis = timeMs;
    }

    /**
     * 推进相对时间（&gt;= 0）。
     */
    public void advanceBy(long deltaMs) {
        if (deltaMs < 0L) {
            throw new IllegalArgumentException("delta must not be negative: " + deltaMs);
        }
        nowMillis += deltaMs;
    }

    /**
     * 注册一个活跃动画。启动动画时调用。
     * 计数器递增；{@link #hasActiveAnimations()} 返回 true 直到所有动画结束。
     */
    public void registerActive() {
        activeCount++;
    }

    /**
     * 注销一个活跃动画。动画结束时调用。
     *
     * @throws IllegalStateException 若计数器已为 0
     */
    public void unregisterActive() {
        if (activeCount <= 0) {
            throw new IllegalStateException("no active animations to unregister");
        }
        activeCount--;
    }

    /**
     * 是否有活跃动画。host 层据此决定是否继续渲染（动态路径 vs 静态 retained 路径）。
     */
    public boolean hasActiveAnimations() {
        return activeCount > 0;
    }

    /**
     * 重置时钟：时间归零，活跃计数清零。
     *
     * <p>用于关闭/重新打开 UI 时恢复初始状态。
     */
    public void reset() {
        nowMillis = 0L;
        activeCount = 0;
    }

    /** 活跃动画数（用于测试与调试）。 */
    public int activeCount() {
        return activeCount;
    }
}
