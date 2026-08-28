package gq.yozakura.ui.engine.layout;

import gq.yozakura.ui.engine.dom.ElementNode;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * 布局树节点：记录一个元素在布局后得到的几何信息。
 *
 * <p>不可变值对象。所有坐标均为逻辑像素，相对父 LayoutBox 的内容区原点；
 * 根节点相对视口左上角 (0, 0)。
 *
 * <p>Box model 几何关系：
 * <pre>
 *   marginBox = margin + borderBox
 *   borderBox = border + paddingBox
 *   paddingBox = padding + contentBox
 *   contentBox = (contentX, contentY, contentWidth, contentHeight)
 * </pre>
 *
 * <p>{@code borderBoxX/Y} 是 border 盒左上角相对父 content 原点的偏移；
 * {@code contentX/Y} 由 {@code borderBox + border + padding} 推导；
 * {@code contentWidth/Height} 由 {@code borderBox - border - padding} 推导。
 *
 * <p>{@code zIndex} 与 {@code position} 来自 ComputedStyle，由 paint 阶段决定堆叠与裁剪行为。
 *
 * <p>子节点列表按 DOM 顺序保留；display:none 元素不产生 LayoutBox，不入子列表。
 */
public final class LayoutBox {

    private final ElementNode element;
    private final float borderBoxX;
    private final float borderBoxY;
    private final float borderBoxWidth;
    private final float borderBoxHeight;
    private final MarginEdges margin;
    private final BorderEdges border;
    private final PaddingEdges padding;
    private final int zIndex;
    private final Position position;
    private final Overflow overflow;
    private final List<LayoutBox> children;
    private final List<LayoutBox> paintChildren;
    private final List<LayoutBox> hitChildren;

    public LayoutBox(ElementNode element,
                     float borderBoxX, float borderBoxY,
                     float borderBoxWidth, float borderBoxHeight,
                     MarginEdges margin, BorderEdges border, PaddingEdges padding,
                     int zIndex, Position position,
                     List<LayoutBox> children) {
        this(element, borderBoxX, borderBoxY,
                borderBoxWidth, borderBoxHeight,
                margin, border, padding, zIndex, position, Overflow.VISIBLE, children);
    }

    public LayoutBox(ElementNode element,
                     float borderBoxX, float borderBoxY,
                     float borderBoxWidth, float borderBoxHeight,
                     MarginEdges margin, BorderEdges border, PaddingEdges padding,
                     int zIndex, Position position, Overflow overflow,
                     List<LayoutBox> children) {
        this(element, borderBoxX, borderBoxY, borderBoxWidth, borderBoxHeight,
                margin, border, padding, zIndex, position, overflow,
                immutableChildren(children), null, null);
    }

    private LayoutBox(ElementNode element,
                      float borderBoxX, float borderBoxY,
                      float borderBoxWidth, float borderBoxHeight,
                      MarginEdges margin, BorderEdges border, PaddingEdges padding,
                      int zIndex, Position position, Overflow overflow,
                      List<LayoutBox> children,
                      List<LayoutBox> paintChildren,
                      List<LayoutBox> hitChildren) {
        this.element = element;
        this.borderBoxX = borderBoxX;
        this.borderBoxY = borderBoxY;
        this.borderBoxWidth = borderBoxWidth;
        this.borderBoxHeight = borderBoxHeight;
        this.margin = margin;
        this.border = border;
        this.padding = padding;
        this.zIndex = zIndex;
        this.position = position;
        this.overflow = overflow == null ? Overflow.VISIBLE : overflow;
        this.children = children;
        this.paintChildren = paintChildren == null ? orderedForPaint(children) : paintChildren;
        this.hitChildren = hitChildren == null ? orderedForHit(children) : hitChildren;
    }

    private static List<LayoutBox> immutableChildren(List<LayoutBox> source) {
        return source == null || source.isEmpty()
                ? Collections.<LayoutBox>emptyList()
                : Collections.unmodifiableList(new ArrayList<LayoutBox>(source));
    }

    public ElementNode element() { return element; }

    /** Border 盒左上角 X（相对父 content 原点）。 */
    public float borderBoxX() { return borderBoxX; }
    /** Border 盒左上角 Y（相对父 content 原点）。 */
    public float borderBoxY() { return borderBoxY; }
    public float borderBoxWidth() { return borderBoxWidth; }
    public float borderBoxHeight() { return borderBoxHeight; }

    /** Content 盒左上角 X = borderBox + border.left + padding.left。 */
    public float contentX() { return borderBoxX + border.left() + padding.left(); }
    /** Content 盒左上角 Y = borderBox + border.top + padding.top。 */
    public float contentY() { return borderBoxY + border.top() + padding.top(); }
    public float contentWidth() {
        return borderBoxWidth - border.horizontalSum() - padding.horizontalSum();
    }
    public float contentHeight() {
        return borderBoxHeight - border.verticalSum() - padding.verticalSum();
    }

    public MarginEdges margin() { return margin; }
    public BorderEdges border() { return border; }
    public PaddingEdges padding() { return padding; }

    public int zIndex() { return zIndex; }
    public Position position() { return position; }
    public Overflow overflow() { return overflow; }

    public int childCount() { return children.size(); }
    public LayoutBox child(int index) { return children.get(index); }
    public List<LayoutBox> children() { return children; }
    public List<LayoutBox> paintChildren() { return paintChildren; }
    public List<LayoutBox> hitChildren() { return hitChildren; }

    private static List<LayoutBox> orderedForPaint(List<LayoutBox> source) {
        if (source.size() < 2) return source;
        List<LayoutBox> ordered = new ArrayList<LayoutBox>(source);
        Collections.sort(ordered, new Comparator<LayoutBox>() {
            @Override public int compare(LayoutBox left, LayoutBox right) {
                return left.zIndex < right.zIndex ? -1 : left.zIndex == right.zIndex ? 0 : 1;
            }
        });
        return Collections.unmodifiableList(ordered);
    }

    private static List<LayoutBox> orderedForHit(List<LayoutBox> source) {
        if (source.size() < 2) return source;
        List<LayoutBox> ordered = new ArrayList<LayoutBox>(source.size());
        for (int i = source.size() - 1; i >= 0; i--) ordered.add(source.get(i));
        Collections.sort(ordered, new Comparator<LayoutBox>() {
            @Override public int compare(LayoutBox left, LayoutBox right) {
                return left.zIndex > right.zIndex ? -1 : left.zIndex == right.zIndex ? 0 : 1;
            }
        });
        return Collections.unmodifiableList(ordered);
    }

    /**
     * 创建几何尺寸不变、仅改变 border 盒相对偏移的副本。
     *
     * <p>用于 flex 布局：先按 block 语义度量子元素得到尺寸，再按 flex 规则重新定位。
     * 子树引用共享（子元素相对 content 原点的偏移不受父元素位移影响）。
     */
    public LayoutBox repositioned(float newBorderBoxX, float newBorderBoxY) {
        return new LayoutBox(element, newBorderBoxX, newBorderBoxY,
                borderBoxWidth, borderBoxHeight,
                margin, border, padding, zIndex, position, overflow,
                children, paintChildren, hitChildren);
    }

    /**
     * 创建位置不变、仅改变 border 盒尺寸的副本（用于 align-items:stretch）。
     *
     * <p>注意：拉伸仅改变 borderBox 尺寸；内部 content 尺寸随之按 box model 推导。
     * 子树引用共享（拉伸后的元素不再重新布局子元素；MVP 简化）。
     */
    public LayoutBox withBorderBoxSize(float newWidth, float newHeight) {
        return new LayoutBox(element, borderBoxX, borderBoxY,
                newWidth, newHeight,
                margin, border, padding, zIndex, position, overflow,
                children, paintChildren, hitChildren);
    }

    @Override
    public String toString() {
        return "LayoutBox{" + (element == null ? "?" : element.tag())
                + " border=(" + borderBoxX + "," + borderBoxY
                + " " + borderBoxWidth + "x" + borderBoxHeight + ")"
                + " content=(" + contentX() + "," + contentY()
                + " " + contentWidth() + "x" + contentHeight() + ")"
                + " z=" + zIndex + " pos=" + position
                + " children=" + children.size() + "}";
    }
}
