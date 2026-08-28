package gq.yozakura.ui.engine.render;

/**
 * GL 状态守卫：try-with-resources 模式保存/恢复 GL 状态。
 *
 * <p>用法：
 * <pre>
 * try (GlStateGuard guard = new GlStateGuard(access)) {
 *     // 修改 GL 状态、渲染 UI
 *     // ...
 * }  // 自动 close：恢复 capture 时的状态
 * </pre>
 *
 * <p>构造时立即调用 {@link GlStateAccess#capture()} 保存当前状态。
 * {@link #close()} 调用 {@link GlStateAccess#restore(GlStateSnapshot)} 恢复。
 *
 * <p>资源释放幂等（AGENTS.md 要求）：重复 close 不重复 restore。
 * 不依赖 finalizer；若忘记 close，状态不会恢复（开发期应通过 -ea 或 lint 检测）。
 *
 * <p>嵌套守卫按 LIFO 恢复：内层先 close，外层后 close；每层独立持有自己的 snapshot。
 */
public final class GlStateGuard implements AutoCloseable {

    private final GlStateAccess access;
    private GlStateSnapshot snapshot;
    private boolean closed;

    public GlStateGuard(GlStateAccess access) {
        if (access == null) {
            throw new IllegalArgumentException("access must not be null");
        }
        this.access = access;
        // 立即 capture；若 capture 抛异常，构造失败，无需 restore
        this.snapshot = access.capture();
    }

    /**
     * 返回构造时捕获的快照引用（只读视角）。
     * 调用方不应修改返回对象；如需快照副本使用 {@link GlStateSnapshot#copy()}。
     */
    public GlStateSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public void close() {
        if (closed) {
            // 幂等：重复 close 不重复 restore
            return;
        }
        closed = true;
        if (snapshot != null) {
            access.restore(snapshot);
            // 释放引用；snapshot 本身由 access 实现决定是否需要进一步清理
            snapshot = null;
        }
    }
}
