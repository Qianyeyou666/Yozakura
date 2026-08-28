package gq.yozakura.ui.engine.animation;

/**
 * 运行中的过渡：不可变值对象，记录单次过渡的起始/目标值与时间窗口。
 *
 * <p>AGENTS.md 契约：
 * <ul>
 *   <li>"Transitions must support interruption and reversal."</li>
 *   <li>"Separate paint-only transitions from layout transitions."</li>
 * </ul>
 *
 * <p>时间窗口：
 * <pre>
 *   startTimeMs = launchTime + delayMs
 *   endTimeMs   = startTimeMs + durationMs
 * </pre>
 *
 * <p>progressAt(now) 返回 [0, 1] 的归一化进度（经 timing function 应用前的线性进度）；
 * delay 期间为 0，duration 完成后为 1。
 *
 * <p>valueAt(now) 返回当前插值结果 = from + (to - from) * timing.apply(progress)。
 *
 * <p>不可变；TransitionStore 在中断时替换实例而非修改字段。
 */
public final class RunningTransition {

    private final TransitionSpec spec;
    private final float fromValue;
    private final float toValue;
    private final long startTimeMs;  // 含 delay 的实际启动时间
    private final long endTimeMs;
    private final boolean layoutAffecting;

    public RunningTransition(TransitionSpec spec, float fromValue, float toValue,
                             long startTimeMs, long endTimeMs) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (endTimeMs < startTimeMs) {
            throw new IllegalArgumentException(
                    "endTime must not be before startTime: " + endTimeMs + " < " + startTimeMs);
        }
        this.spec = spec;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.layoutAffecting = spec.isLayoutAffecting();
    }

    public TransitionSpec spec() { return spec; }
    public String property() { return spec.property(); }
    public float fromValue() { return fromValue; }
    public float toValue() { return toValue; }
    public long startTimeMs() { return startTimeMs; }
    public long endTimeMs() { return endTimeMs; }

    /** 该过渡是否影响 layout（vs 仅 paint）。 */
    public boolean isLayoutAffecting() { return layoutAffecting; }

    /** 是否已完成（含 delay 阶段未启动时不算完成）。 */
    public boolean isCompletedAt(long nowMs) {
        return nowMs >= endTimeMs;
    }

    /** 是否仍在 delay 阶段（尚未真正进入插值）。 */
    public boolean isInDelayAt(long nowMs) {
        return nowMs < startTimeMs;
    }

    /**
     * 线性进度 [0, 1]，未经 timing function。
     *
     * <p>delay 期间返回 0；完成后返回 1；早于 startTimeMs 的查询返回 0（安全）。
     */
    public float progressAt(long nowMs) {
        if (nowMs <= startTimeMs) return 0f;
        if (nowMs >= endTimeMs) return 1f;
        long duration = endTimeMs - startTimeMs;
        if (duration <= 0L) return 1f;
        return (float) (nowMs - startTimeMs) / (float) duration;
    }

    /**
     * 当前插值结果 = from + (to - from) * timing.apply(progress)。
     *
     * <p>delay 期间返回 fromValue；完成后返回 toValue。
     */
    public float valueAt(long nowMs) {
        float p = spec.timingFunction().apply(progressAt(nowMs));
        return fromValue + (toValue - fromValue) * p;
    }

    @Override
    public String toString() {
        return "RunningTransition{" + property()
                + " " + fromValue + "->" + toValue
                + " [" + startTimeMs + ".." + endTimeMs + "]"
                + (layoutAffecting ? " layout" : " paint") + "}";
    }
}
