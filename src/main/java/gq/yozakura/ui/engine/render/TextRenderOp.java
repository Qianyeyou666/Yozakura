package gq.yozakura.ui.engine.render;

import gq.yozakura.ui.engine.paint.TextPaintCommand;

/** Ordered text operation retaining the clip that was active at dispatch time. */
public final class TextRenderOp extends RenderOp {
    private final TextPaintCommand command;
    private final ClipRect clipRect;

    public TextRenderOp(TextPaintCommand command, ClipRect clipRect) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        this.command = command;
        this.clipRect = clipRect;
    }

    public TextPaintCommand command() { return command; }

    @Override
    public int kind() { return KIND_TEXT; }

    @Override
    public ClipRect clipRect() { return clipRect; }
}
