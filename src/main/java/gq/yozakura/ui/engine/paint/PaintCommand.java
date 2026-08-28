package gq.yozakura.ui.engine.paint;

/**
 * Paint 命令抽象基类。
 *
 * <p>每个命令是不可变值对象，描述一个绘制意图（填充、描边、裁剪等）。
 * 命令按追加顺序进入 {@link PaintCommandList}，由 renderer 通过 visitor 模式回放。
 *
 * <p>{@link #type()} 返回命令类别常量，用于 renderer 批处理分桶：相同 type 的相邻命令
 * 可在共享 shader/texture/clip 时合并为单次 draw call。
 *
 * <p>新增命令类型时：
 * <ol>
 *   <li>在此声明 TYPE_ 常量</li>
 *   <li>实现具体子类，重写 {@link #type()} 与 {@link #accept(PaintCommandVisitor)}</li>
 *   <li>扩展 {@link PaintCommandVisitor} 接口</li>
 * </ol>
 */
public abstract class PaintCommand {

    // ---- 命令类型常量 ----
    public static final int TYPE_RECT_FILL = 1;
    public static final int TYPE_RECT_BORDER = 2;
    public static final int TYPE_CLIP_PUSH = 3;
    public static final int TYPE_CLIP_POP = 4;
    public static final int TYPE_TEXT = 5;
    // 后续切片：TYPE_TEXT_GLYPHS、TYPE_IMAGE、TYPE_GRADIENT、TYPE_SHADOW

    /** 命令类别常量，用于批处理分桶。 */
    public abstract int type();

    /** 双分派：调用 visitor 对应本类型的方法。 */
    public abstract void accept(PaintCommandVisitor visitor);
}
