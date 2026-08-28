package gq.yozakura.ui.engine.paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Paint 命令列表：有序、可累积、可快照。
 *
 * <p>构造阶段为可变 builder（{@link #append(PaintCommand)}）；
 * {@link #commands()} 返回不可变视图供 renderer 回放或测试断言。
 *
 * <p>命令按追加顺序保留：paint tree builder 先按 z-index/堆叠上下文排序，
 * 再按"先背景后内容"顺序追加，renderer 回放即得正确视觉结果。
 *
 * <p>静态帧场景：renderer 缓存一份 PaintCommandList，每帧调用 {@link #replay(PaintCommandVisitor)}
 * 重新执行命令而无需重建 DOM/CSS/Layout（retained-mode 核心收益）。
 *
 * <p>本类不持有 GL 资源，可在任意线程构造；renderer 在渲染线程回放。
 */
public final class PaintCommandList {

    private final List<PaintCommand> commands;
    private final List<PaintCommand> commandView;
    private int version;

    public PaintCommandList() {
        this.commands = new ArrayList<PaintCommand>();
        this.commandView = Collections.unmodifiableList(commands);
        this.version = 0;
    }

    /** 追加一条命令到列表末尾。 */
    public PaintCommandList append(PaintCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        commands.add(command);
        version++;
        return this;
    }

    public int size() {
        return commands.size();
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public PaintCommand command(int index) {
        return commands.get(index);
    }

    /** 不可变命令视图（防御性：调用方无法修改内部 list）。 */
    public List<PaintCommand> commands() {
        return commandView;
    }

    /** 按追加顺序回放所有命令到 visitor。 */
    public void replay(PaintCommandVisitor visitor) {
        if (visitor == null) {
            throw new IllegalArgumentException("visitor must not be null");
        }
        // 用索引遍历避免 iterator 分配（per-frame 路径，零分配目标）
        for (int i = 0; i < commands.size(); i++) {
            commands.get(i).accept(visitor);
        }
    }

    /** 清空命令列表（用于 dirty 重建场景）。 */
    public void clear() {
        commands.clear();
        version++;
    }

    /**
     * 修订号：每次 append/clear 自增，用于 renderer 缓存编译结果并按 (ref, version) 失效。
     *
     * <p>AGENTS.md 性能目标：no HTML/CSS parsing on steady frames；retained paint command list
     * 在静态帧应直接重放，不必重新编译。{@code LwjglUiRenderer.compile} 持有
     * {@code (ref, version) -> CompiledPaint} 缓存，仅当 ref 不同或 version 变化时重编译。
     */
    public int version() {
        return version;
    }
}
