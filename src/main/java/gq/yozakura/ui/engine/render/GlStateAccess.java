package gq.yozakura.ui.engine.render;

/**
 * GL 状态访问抽象：捕获与恢复 OpenGL 状态。
 *
 * <p>抽象出 {@link #capture()} 与 {@link #restore(GlStateSnapshot)} 两个操作，
 * 让 {@link GlStateGuard} 与具体 GL 实现（LWJGL 2/OpenGL 2.1）解耦。
 *
 * <p>生产实现 {@code LwjglGlStateAccess} 放在 render 包，通过 GL11/GL14/ARB 函数
 * 实际查询与设置 GL 状态。单测用 Fake 实现验证 GlStateGuard 的编排契约。
 *
 * <p>所有方法必须在渲染线程调用（OpenGL 上下文线程）。
 */
public interface GlStateAccess {
    /**
     * 捕获当前全部相关 GL 状态到新快照。
     *
     * @return 填充完毕的 {@link GlStateSnapshot}；调用方持有所有权
     */
    GlStateSnapshot capture();

    /**
     * 从快照恢复 GL 状态。应恢复 capture 时记录的全部字段；
     * 若快照字段为 null/默认值，恢复为对应 GL 默认。
     *
     * @param snapshot 之前 capture 返回的快照；不为 null
     */
    void restore(GlStateSnapshot snapshot);
}
