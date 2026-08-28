package gq.yozakura.ui.engine.paint;

/**
 * 裁剪入栈命令：将当前裁剪矩形与 (x, y, width, height) 的交集设为新裁剪区域。
 *
 * <p>不可变值对象。与 {@link ClipPopCommand} 成对出现，构成嵌套裁剪栈。
 * renderer 负责维护 scissor 栈并在 push 时计算与上层裁剪的交集。
 *
 * <p>坐标为逻辑像素。renderer 在 host/viewport 层转换为物理 scissor box。
 *
 * <p>width/height 为 0 表示裁剪区域为空（后续绘制全部被剔除）；为负非法。
 */
public final class ClipPushCommand extends PaintCommand {
    private final float x;
    private final float y;
    private final float width;
    private final float height;

    public ClipPushCommand(float x, float y, float width, float height) {
        if (width < 0) {
            throw new IllegalArgumentException("width must not be negative: " + width);
        }
        if (height < 0) {
            throw new IllegalArgumentException("height must not be negative: " + height);
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public float x() { return x; }
    public float y() { return y; }
    public float width() { return width; }
    public float height() { return height; }

    @Override
    public int type() {
        return TYPE_CLIP_PUSH;
    }

    @Override
    public void accept(PaintCommandVisitor visitor) {
        visitor.visitClipPush(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClipPushCommand)) return false;
        ClipPushCommand c = (ClipPushCommand) o;
        return Float.floatToIntBits(x) == Float.floatToIntBits(c.x)
                && Float.floatToIntBits(y) == Float.floatToIntBits(c.y)
                && Float.floatToIntBits(width) == Float.floatToIntBits(c.width)
                && Float.floatToIntBits(height) == Float.floatToIntBits(c.height);
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(x);
        result = 31 * result + Float.floatToIntBits(y);
        result = 31 * result + Float.floatToIntBits(width);
        result = 31 * result + Float.floatToIntBits(height);
        return result;
    }

    @Override
    public String toString() {
        return "ClipPush(" + x + "," + y + " " + width + "x" + height + ")";
    }
}
