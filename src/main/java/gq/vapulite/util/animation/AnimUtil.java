package gq.vapulite.util.animation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * 基于真实时间的轻量级动画工具类，管理一组按键映射的动画进度。
 * <p>
 * 每个动画由 trigger 启动（进度=1），每帧调用 tick() 根据实际耗时衰减至 0。
 * 动画速度完全不受帧率影响。
 * <p>
 * 提供静态方法计算摇晃/弹跳的视觉偏移量。
 * <p>
 * 用法：
 * <pre>{@code
 *   AnimUtil shake = new AnimUtil(250);   // 250ms 衰减完毕
 *   AnimUtil bounce = new AnimUtil(280);  // 280ms 衰减完毕
 *
 *   // 每帧
 *   shake.tick();
 *   bounce.tick();
 *
 *   // 触发
 *   shake.trigger(tabIndex);
 *
 *   // 绘制时获取偏移
 *   float offsetX = AnimUtil.shakeX(shake.get(tabIndex));
 * }</pre>
 */
public final class AnimUtil {
    private final Map<Integer, Float> map = new HashMap<>();
    private final float durationMs;
    private long lastTickNs;

    /**
     * @param durationMs 动画从 1 衰减到 0 的总时长（毫秒）
     */
    public AnimUtil(float durationMs) {
        this.durationMs = durationMs;
        this.lastTickNs = System.nanoTime();
    }

    /**
     * 每帧调用，根据实际耗时将所有动画向 0 衰减，到 0 时自动移除。
     * 动画速度基于真实时间，不受帧率影响。
     */
    public void tick() {
        long now = System.nanoTime();
        float elapsedMs = (now - lastTickNs) / 1_000_000f;
        lastTickNs = now;
        // 避免卡顿后一帧内跳太多（最大 50ms 的上限防止大跳帧）
        float clampedElapsed = Math.min(elapsedMs, 50f);

        for (Integer key : new HashSet<>(map.keySet())) {
            float v = map.get(key);
            float decay = clampedElapsed / durationMs;
            v = Math.max(0f, v - decay);
            if (v <= 0f) {
                map.remove(key);
            } else {
                map.put(key, v);
            }
        }
    }

    /** 触发指定 key 的动画（进度重置为 1） */
    public void trigger(int key) {
        map.put(key, 1.0f);
    }

    /** 获取当前进度 0~1，无动画时返回 0 */
    public float get(int key) {
        return map.getOrDefault(key, 0f);
    }

    /** 是否有正在播放的动画 */
    public boolean has(int key) {
        return map.containsKey(key);
    }

    // ==================== 视觉偏移计算 ====================

    /** 摇晃水平偏移（左右快速抖动衰减） */
    public static float shakeX(float progress) {
        if (progress <= 0f) return 0f;
        return (float) (Math.sin(progress * Math.PI * 6) * progress * 6.0f);
    }

    /** 弹跳垂直偏移（向上弹起后弹性回落） */
    public static float bounceY(float progress) {
        if (progress <= 0f) return 0f;
        return -(float) (Math.sin(progress * Math.PI * 1.5) * progress * 2.5f);
    }

    /** 弹跳缩放系数（弹跳时略微放大） */
    public static float bounceScale(float progress) {
        if (progress <= 0f) return 1f;
        return 1f + (float) (Math.sin(progress * Math.PI) * progress * 0.03f);
    }
}
