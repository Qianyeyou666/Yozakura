package gq.yozakura.ui.engine.render;

import java.util.Collections;
import java.util.List;

/**
 * Immutable render-op stream compiled only when the paint command list changes.
 *
 * <p>构造时直接包装传入的 operations 列表为不可变视图；不再做防御性拷贝。
 * 唯一 caller {@code LwjglUiRenderer.compile()} 总是传入新构造的 ArrayList，
 * 构造后不会再修改——二次拷贝是冗余分配（per-frame 热路径，AGENTS.md 性能目标要求避免）。
 *
 * <p>不可变性约束：传入的 list 在构造后调用方不得修改；本类通过
 * {@link Collections#unmodifiableList(List)} 阻断外部修改。
 */
public final class CompiledPaint {
    private final List<RenderOp> operations;

    CompiledPaint(List<RenderOp> operations) {
        this.operations = Collections.unmodifiableList(operations);
    }

    public int size() { return operations.size(); }
    public RenderOp operation(int index) { return operations.get(index); }
}
