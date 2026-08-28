package gq.yozakura.ui.engine.paint;

/**
 * 裁剪出栈命令：弹出最近一次 {@link ClipPushCommand}，恢复上层裁剪区域。
 *
 * <p>无状态单例值对象。所有 ClipPopCommand 等价。
 *
 * <p>renderer 在 pop 时不应假定栈深度；若栈已空，pop 视为 no-op（防御性）。
 */
public final class ClipPopCommand extends PaintCommand {

    public ClipPopCommand() {
    }

    @Override
    public int type() {
        return TYPE_CLIP_POP;
    }

    @Override
    public void accept(PaintCommandVisitor visitor) {
        visitor.visitClipPop(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ClipPopCommand;
    }

    @Override
    public int hashCode() {
        return ClipPopCommand.class.hashCode();
    }

    @Override
    public String toString() {
        return "ClipPop";
    }
}
