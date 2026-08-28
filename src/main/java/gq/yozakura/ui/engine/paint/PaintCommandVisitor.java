package gq.yozakura.ui.engine.paint;

/**
 * Paint 命令 visitor：双分派接口，由 renderer 实现以回放命令列表。
 *
 * <p>visitor 按命令追加顺序接收 visit* 调用，可在此处维护状态（当前 clip 栈、
 * 当前 shader/texture 绑定、batch 累积顶点等）。
 */
public interface PaintCommandVisitor {
    void visitRectFill(RectFillCommand command);
    void visitRectBorder(RectBorderCommand command);
    void visitClipPush(ClipPushCommand command);
    void visitClipPop(ClipPopCommand command);
    void visitText(TextPaintCommand command);
}
