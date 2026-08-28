package gq.yozakura.ui.engine.layout;

import gq.yozakura.ui.engine.css.ComputedStyle;
import gq.yozakura.ui.engine.dom.DomNode;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.dom.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 布局引擎入口：对 DOM + ComputedStyle 执行布局，产出 {@link LayoutBox} 树。
 *
 * <p>阶段 2 实现：
 * <ul>
 *   <li>block 布局：块级子元素按 DOM 顺序垂直堆叠</li>
 *   <li>flex 布局：row/column、justify-content、align-items、gap、flex-grow/shrink/basis（见 {@link FlexLayout}）</li>
 *   <li>完整 box model：margin/padding/border/content 四层嵌套</li>
 *   <li>width/height 解析：px/%/em/rem/vw/vh/auto，百分比相对父内容区</li>
 *   <li>min/max-width/height 钳制</li>
 *   <li>position: relative — 保留在文档流，按 top/left/right/bottom 偏移</li>
 *   <li>position: absolute — 脱离文档流，相对最近 positioned 祖先 content 盒定位；
 *       支持 top/left/right/bottom 与 auto width(left+right)/auto height(top+bottom)</li>
 *   <li>overflow: hidden/auto — 显式高度时不扩张到 children_sum；visible 默认取 max</li>
 *   <li>z-index 与 position 保留供 paint 阶段使用</li>
 *   <li>display:none 不产生 LayoutBox</li>
 * </ul>
 *
 * <p>所有坐标为逻辑像素，相对父 LayoutBox 的内容区原点；根节点相对视口 (0,0)。
 */
public final class LayoutEngine {

    /** 父内容区高度未确定的哨兵值：用于 height:auto 父级，子级 height:% 据此降级为 0。 */
    static final float UNDETERMINED = -1f;

    /** display 关键字。 */
    private static final String BLOCK = "block";
    private static final String FLEX = "flex";
    private static final String INLINE_BLOCK = "inline-block";
    private static final String NONE = "none";

    private static String displayKeyword(ComputedStyle style) {
        if (style == null) return BLOCK;
        String d = style.get("display");
        if (d == null) return BLOCK;
        String t = d.trim();
        return t.isEmpty() ? BLOCK : t;
    }

    /**
     * 布局入口。
     *
     * @param root    根元素
     * @param styles  StyleResolver 产出的元素→ComputedStyle 映射
     * @param ctx     度量上下文（视口、根字号）
     * @return 根 LayoutBox；若根 display:none 返回 null
     */
    public LayoutBox layout(ElementNode root,
                            Map<ElementNode, ComputedStyle> styles,
                            MeasureContext ctx) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        if (styles == null) {
            throw new IllegalArgumentException("styles must not be null");
        }
        if (ctx == null) {
            throw new IllegalArgumentException("ctx must not be null");
        }
        ComputedStyle rootStyle = styles.get(root);
        if (isDisplayNone(rootStyle)) {
            return null;
        }
        // 根节点：父内容原点 (0,0)（视口），堆叠光标 0；父内容宽 = 视口宽，高 = 视口高（视为确定）
        // 初始 containing block = 视口（原点 0,0；尺寸 vw×vh）
        float rootFontPx = ctx.rootFontSizePx();
        float vw = (float) ctx.viewportWidth();
        float vh = (float) ctx.viewportHeight();
        return layoutElement(root, styles, ctx,
                rootFontPx, rootFontPx,
                0f, 0f, 0f,
                vw, vh,
                0f, 0f, vw, vh);
    }

    /**
     * 递归布局单个元素。
     *
     * <p>坐标语义：{@code LayoutBox.borderBoxX/Y} 存储相对父 content 原点的偏移；
     * 内部用 {@code parentAbsContentX/Y} 跟踪绝对坐标，用于子元素递归。
     *
     * @param element                 当前元素
     * @param styles                  样式映射
     * @param ctx                     度量上下文
     * @param parentFontSizePx        父元素计算后的字号（用于 em 与 font-size 百分比解析）
     * @param remBasePx               根元素字号（用于 rem）
     * @param parentAbsContentX       父内容区原点 X（绝对坐标）
     * @param parentAbsContentY       父内容区原点 Y（绝对坐标）
     * @param cursorYRelToParent      当前子元素堆叠光标（相对父 content 原点；block 用，flex 由 dispatch 计算）
     * @param parentContentWidth      父内容区宽（已确定；非负）
     * @param parentContentHeightOrU  父内容区高；{@link #UNDETERMINED} 表示未确定
     * @param cbAbsX                  containing block 原点 X（绝对坐标）— 最近 positioned 祖先 content 原点，无则视口 (0,0)
     * @param cbAbsY                  containing block 原点 Y（绝对坐标）
     * @param cbWidth                 containing block 宽
     * @param cbHeightOrU             containing block 高；{@link #UNDETERMINED} 表示未确定（auto 高度的 positioned 祖先）
     */
    private LayoutBox layoutElement(ElementNode element,
                                    Map<ElementNode, ComputedStyle> styles,
                                    MeasureContext ctx,
                                    float parentFontSizePx, float remBasePx,
                                    float parentAbsContentX, float parentAbsContentY,
                                    float cursorYRelToParent,
                                    float parentContentWidth, float parentContentHeightOrU,
                                    float cbAbsX, float cbAbsY,
                                    float cbWidth, float cbHeightOrU) {
        ComputedStyle style = styles.get(element);
        if (isDisplayNone(style)) {
            return null;
        }

        Position position = Position.parse(style.get("position"));
        boolean isAbsolute = (position == Position.ABSOLUTE);
        boolean isPositioned = (position != Position.STATIC);

        // 1. 解析本元素字号（em 基准与子级继承基准）
        float elementFontPx = resolveFontSize(style, parentFontSizePx, remBasePx, ctx);

        // 2. 解析 margin/padding/border 到像素
        //    absolute 元素的 % 相对 CB 解析；static/relative 相对父内容区
        float widthPercentBase = isAbsolute ? cbWidth : parentContentWidth;
        MarginEdges margin = resolveMargin(style, widthPercentBase, elementFontPx, remBasePx, ctx);
        PaddingEdges padding = resolvePadding(style, widthPercentBase, elementFontPx, remBasePx, ctx);
        BorderEdges border = resolveBorder(style, widthPercentBase, elementFontPx, remBasePx, ctx);
        BoxSizing boxSizing = BoxSizing.parse(style.get("box-sizing"));

        // 3. 解析 top/left/right/bottom 偏移（相对 CB；未设置视为 auto）
        float cbH = cbHeightOrU < 0 ? 0f : cbHeightOrU;
        float offsetTop = resolveOffset(style.get("top"), cbH, elementFontPx, remBasePx, ctx);
        float offsetBottom = resolveOffset(style.get("bottom"), cbH, elementFontPx, remBasePx, ctx);
        float offsetLeft = resolveOffset(style.get("left"), cbWidth, elementFontPx, remBasePx, ctx);
        float offsetRight = resolveOffset(style.get("right"), cbWidth, elementFontPx, remBasePx, ctx);
        boolean topAuto = isAuto(style.get("top"));
        boolean bottomAuto = isAuto(style.get("bottom"));
        boolean leftAuto = isAuto(style.get("left"));
        boolean rightAuto = isAuto(style.get("right"));

        // 4. 计算宽度
        float declaredWidth = resolveLength(style.get("width"),
                widthPercentBase, elementFontPx, remBasePx, ctx, 0f);
        boolean widthAuto = isAuto(style.get("width"));

        float borderBoxWidth;
        if (widthAuto) {
            if (isAbsolute && !leftAuto && !rightAuto) {
                // left + right + auto width → width = cbWidth - left - right - margin
                borderBoxWidth = cbWidth - offsetLeft - offsetRight - margin.horizontalSum();
            } else {
                // auto width：占满父内容区宽（减去 margin）
                borderBoxWidth = parentContentWidth - margin.horizontalSum();
            }
        } else {
            borderBoxWidth = boxSizing.borderBoxWidth(declaredWidth, padding, border);
        }
        // min/max-width 钳制（作用于 border-box；MVP 简化，不区分 box-sizing）
        float maxWidth = resolveMaxSize(style.get("max-width"),
                widthPercentBase, elementFontPx, remBasePx, ctx);
        float minWidth = resolveMinSize(style.get("min-width"),
                widthPercentBase, elementFontPx, remBasePx, ctx);
        if (borderBoxWidth > maxWidth) borderBoxWidth = maxWidth;
        if (borderBoxWidth < minWidth) borderBoxWidth = minWidth;
        if (borderBoxWidth < 0) borderBoxWidth = 0;
        float contentWidth = borderBoxWidth - border.horizontalSum() - padding.horizontalSum();
        if (contentWidth < 0) contentWidth = 0;

        // 5. 计算 border 盒位置
        //    absolute：相对 CB 解析；static/relative：用 cursor + margin（relative 偏移稍后应用）
        float relBorderBoxX;
        float relBorderBoxY;
        float absBorderBoxX;
        float absBorderBoxY;

        if (isAbsolute) {
            // X 方向
            if (!leftAuto) {
                absBorderBoxX = cbAbsX + offsetLeft + margin.left();
            } else if (!rightAuto) {
                absBorderBoxX = cbAbsX + cbWidth - offsetRight - borderBoxWidth - margin.right();
            } else {
                // 静态位置（沿用文档流位置）
                absBorderBoxX = parentAbsContentX + margin.left();
            }
            // Y 方向 — top 已知则确定；bottom 或 static 推迟到高度计算后
            if (!topAuto) {
                absBorderBoxY = cbAbsY + offsetTop + margin.top();
            } else {
                // 暂用静态位置；若 bottom 设置则在第 9 步重算
                absBorderBoxY = parentAbsContentY + cursorYRelToParent + margin.top();
            }
            relBorderBoxX = absBorderBoxX - parentAbsContentX;
            relBorderBoxY = absBorderBoxY - parentAbsContentY;
        } else {
            relBorderBoxX = margin.left();
            relBorderBoxY = cursorYRelToParent + margin.top();
            // relative 偏移在此应用（不影响文档流光标）
            if (position == Position.RELATIVE) {
                if (!leftAuto) {
                    relBorderBoxX += offsetLeft;
                } else if (!rightAuto) {
                    relBorderBoxX -= offsetRight;
                }
                if (!topAuto) {
                    relBorderBoxY += offsetTop;
                } else if (!bottomAuto) {
                    relBorderBoxY -= offsetBottom;
                }
            }
            absBorderBoxX = parentAbsContentX + relBorderBoxX;
            absBorderBoxY = parentAbsContentY + relBorderBoxY;
        }

        float absContentX = absBorderBoxX + border.left() + padding.left();
        float absContentY = absBorderBoxY + border.top() + padding.top();

        // 6. 判断高度模式
        boolean heightAuto = isAuto(style.get("height"));
        // % 基准：absolute 用 CB 高；其他用父内容高；未确定时降级为 0
        float heightPercentBase;
        if (isAbsolute) {
            heightPercentBase = cbHeightOrU < 0 ? 0f : cbHeightOrU;
        } else {
            heightPercentBase = parentContentHeightOrU < 0 ? 0f : parentContentHeightOrU;
        }
        float declaredHeight = resolveLength(style.get("height"),
                heightPercentBase, elementFontPx, remBasePx, ctx, 0f);

        // auto height 且 absolute 同时设了 top+bottom → 高度 = cbHeight - top - bottom
        if (heightAuto && isAbsolute && !topAuto && !bottomAuto) {
            declaredHeight = cbH - offsetTop - offsetBottom
                    - border.verticalSum() - padding.verticalSum() - margin.verticalSum();
            if (declaredHeight < 0) declaredHeight = 0;
            heightAuto = false;
        }

        float initialContentHeight;
        if (heightAuto) {
            initialContentHeight = UNDETERMINED;
        } else {
            initialContentHeight = boxSizing.contentHeight(declaredHeight, padding, border);
            if (initialContentHeight < 0) initialContentHeight = 0;
        }

        // 7. 布局子元素
        //    后代 CB：本元素 positioned → 用本元素 content 盒；否则继承父级 CB
        float childParentHeightOrU = heightAuto ? UNDETERMINED : initialContentHeight;
        List<LayoutBox> children = new ArrayList<LayoutBox>();

        // CSS absolute containing blocks use the positioned ancestor's padding box.
        // LayoutBox child coordinates remain relative to the parent's content box, so
        // left:0 intentionally becomes -padding.left() in the retained child box.
        float descCbAbsX = isPositioned ? absBorderBoxX + border.left() : cbAbsX;
        float descCbAbsY = isPositioned ? absBorderBoxY + border.top() : cbAbsY;
        float descCbWidth = isPositioned ? contentWidth + padding.horizontalSum() : cbWidth;
        float descCbHeightOrU = isPositioned
                ? (childParentHeightOrU < 0 ? UNDETERMINED
                : childParentHeightOrU + padding.verticalSum())
                : cbHeightOrU;

        String display = displayKeyword(style);
        if (FLEX.equals(display)) {
            FlexLayout flex = new FlexLayout(this);
            flex.layout(element, styles, ctx, elementFontPx, remBasePx,
                    absContentX, absContentY, contentWidth, childParentHeightOrU,
                    children,
                    descCbAbsX, descCbAbsY, descCbWidth, descCbHeightOrU);
        } else {
            layoutBlockChildren(element, styles, ctx, elementFontPx, remBasePx,
                    absContentX, absContentY, contentWidth, childParentHeightOrU, children,
                    descCbAbsX, descCbAbsY, descCbWidth, descCbHeightOrU);
        }

        // 子元素总高（absolute 子元素不参与文档流高度）
        float childrenSumHeight = 0f;
        for (int i = 0; i < children.size(); i++) {
            LayoutBox c = children.get(i);
            if (c.position() == Position.ABSOLUTE) continue;
            float childBottom = c.borderBoxY() + c.borderBoxHeight() + c.margin().bottom();
            if (childBottom > childrenSumHeight) {
                childrenSumHeight = childBottom;
            }
        }

        float intrinsicTextHeight = hasDirectText(element)
                ? resolveLineHeight(style, elementFontPx, remBasePx, ctx) : 0.0F;

        // 8. 计算最终 contentHeight
        Overflow overflow = Overflow.parse(style.get("overflow"));
        float contentHeight;
        if (heightAuto) {
            contentHeight = Math.max(childrenSumHeight, intrinsicTextHeight);
        } else if (overflow.clips()) {
            // overflow:hidden/auto + 显式高度 → 保持 declared，不扩张到 children_sum
            contentHeight = initialContentHeight;
        } else {
            // overflow:visible → 取 max(declared, children_sum)
            contentHeight = Math.max(initialContentHeight, childrenSumHeight);
        }
        if (contentHeight < 0) contentHeight = 0;

        float borderBoxHeight = contentHeight + border.verticalSum() + padding.verticalSum();
        // min/max-height 钳制（作用于 border-box；MVP 简化，不区分 box-sizing）
        float maxHeight = resolveMaxSize(style.get("max-height"),
                heightPercentBase, elementFontPx, remBasePx, ctx);
        float minHeight = resolveMinSize(style.get("min-height"),
                heightPercentBase, elementFontPx, remBasePx, ctx);
        if (borderBoxHeight > maxHeight) borderBoxHeight = maxHeight;
        if (borderBoxHeight < minHeight) borderBoxHeight = minHeight;
        if (borderBoxHeight < 0) borderBoxHeight = 0;

        // 9. absolute + bottom（无 top）：高度已知后重算 Y
        if (isAbsolute && topAuto && !bottomAuto) {
            absBorderBoxY = cbAbsY + cbH - offsetBottom - borderBoxHeight - margin.bottom();
            relBorderBoxY = absBorderBoxY - parentAbsContentY;
            // 注：后代 CB Y 已用 tentative 值布局，对单层 absolute 不影响
        }

        // 10. 解析 z-index
        int zIndex = parseZIndex(style.get("z-index"));

        return new LayoutBox(element, relBorderBoxX, relBorderBoxY,
                borderBoxWidth, borderBoxHeight,
                margin, border, padding, zIndex, position, overflow, children);
    }

    /**
     * Block 布局子元素：竖向堆叠，子元素 borderBoxY 相对本元素 content 原点。
     * absolute 子元素仍产生 LayoutBox（保留 DOM 顺序）但不推进光标。
     */
    private void layoutBlockChildren(ElementNode element,
                                     Map<ElementNode, ComputedStyle> styles,
                                     MeasureContext ctx,
                                     float elementFontPx, float remBasePx,
                                     float absContentX, float absContentY,
                                     float contentWidth, float parentContentHeightOrU,
                                     List<LayoutBox> outChildren,
                                     float cbAbsX, float cbAbsY,
                                     float cbWidth, float cbHeightOrU) {
        float cursorY = 0f;  // 相对本元素 content 原点
        for (int i = 0; i < element.childCount(); i++) {
            DomNode node = element.child(i);
            if (!(node instanceof ElementNode)) {
                continue;
            }
            ElementNode childEl = (ElementNode) node;
            ComputedStyle childStyle = styles.get(childEl);
            if (isDisplayNone(childStyle)) {
                continue;
            }
            boolean childAbsolute = (Position.parse(childStyle.get("position")) == Position.ABSOLUTE);
            // absolute 子元素光标传入 0（不参与流），且不推进 cursorY
            float childCursor = childAbsolute ? 0f : cursorY;
            LayoutBox childBox = layoutElement(childEl, styles, ctx,
                    elementFontPx, remBasePx,
                    absContentX, absContentY, childCursor,
                    contentWidth, parentContentHeightOrU,
                    cbAbsX, cbAbsY, cbWidth, cbHeightOrU);
            if (childBox != null) {
                outChildren.add(childBox);
                if (!childAbsolute) {
                    cursorY += childBox.borderBoxHeight() + childBox.margin().verticalSum();
                }
            }
        }
    }

    /**
     * 供 FlexLayout 度量子元素：调用 {@link #layoutElement} 得到带尺寸的 LayoutBox，
     * 位置（borderBoxX/Y）由 FlexLayout 后续按 flex 规则重写。
     *
     * @param availableWidth  flex 容器内容区宽（用于子级 width:% 与 auto 解析）
     * @param availableHeightOrU  flex 容器内容区高；{@link #UNDETERMINED} 表示未确定
     */
    LayoutBox measureChildForFlex(ElementNode child,
                                  Map<ElementNode, ComputedStyle> styles,
                                  MeasureContext ctx,
                                  float parentFontSizePx, float remBasePx,
                                  float absContentX, float absContentY,
                                  float availableWidth, float availableHeightOrU,
                                  float cbAbsX, float cbAbsY,
                                  float cbWidth, float cbHeightOrU) {
        return layoutElement(child, styles, ctx,
                parentFontSizePx, remBasePx,
                absContentX, absContentY, 0f,
                availableWidth, availableHeightOrU,
                cbAbsX, cbAbsY, cbWidth, cbHeightOrU);
    }

    // ---- 解析辅助 ----

    private static boolean isDisplayNone(ComputedStyle style) {
        return NONE.equals(displayKeyword(style));
    }

    private static boolean isAuto(String raw) {
        return raw == null || raw.trim().equals("auto");
    }

    /**
     * 解析 top/right/bottom/left 偏移到像素。
     * auto/缺失 → 0（调用方通过 isAuto 单独判断是否设置）。
     */
    private static float resolveOffset(String raw, float percentBase, float emBase,
                                       float remBasePx, MeasureContext ctx) {
        if (raw == null || raw.trim().isEmpty() || raw.trim().equals("auto")) {
            return 0f;
        }
        Dimension d = Dimension.parse(raw);
        if (d == null) return 0f;
        ResolveContext rc = ResolveContext.of(percentBase, 0,
                emBase, remBasePx,
                ctx.viewportWidth(), ctx.viewportHeight());
        return d.resolveToPx(rc, 0f);
    }

    /**
     * 解析元素 font-size 到像素。
     * 缺失/auto/未知 → 继承父字号；em 基准 = 父字号；rem 基准 = 根字号；百分比基准 = 父字号。
     */
    private float resolveFontSize(ComputedStyle style, float parentFontSizePx,
                                   float remBasePx, MeasureContext ctx) {
        String raw = style.get("font-size");
        if (raw == null || raw.trim().isEmpty() || raw.trim().equals("auto")) {
            return parentFontSizePx;
        }
        Dimension d = Dimension.parse(raw);
        if (d == null) {
            return parentFontSizePx;
        }
        ResolveContext rc = ResolveContext.of(parentFontSizePx, 0,
                parentFontSizePx, remBasePx,
                ctx.viewportWidth(), ctx.viewportHeight());
        float resolved = d.resolveToPx(rc, parentFontSizePx);
        return resolved < 0 ? 0 : resolved;
    }

    /**
     * 解析通用长度属性到像素。
     */
    private float resolveLength(String raw, float percentBase, float emBase,
                                 float remBasePx, MeasureContext ctx, float autoFallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return autoFallback;
        }
        Dimension d = Dimension.parse(raw);
        if (d == null) {
            return autoFallback;
        }
        ResolveContext rc = ResolveContext.of(percentBase, 0,
                emBase, remBasePx,
                ctx.viewportWidth(), ctx.viewportHeight());
        return d.resolveToPx(rc, autoFallback);
    }

    /**
     * 解析 max-width/max-height：none/缺失 → 无上界（{@link Float#MAX_VALUE}）。
     * 包级可见以供 FlexLayout 复用同款语义。
     */
    static float resolveMaxSize(String raw, float percentBase, float emBase,
                                float remBasePx, MeasureContext ctx) {
        if (raw == null || raw.trim().isEmpty() || raw.trim().equals("none")) {
            return Float.MAX_VALUE;
        }
        return resolveLengthStatic(raw, percentBase, emBase, remBasePx, ctx, Float.MAX_VALUE);
    }

    /**
     * 解析 min-width/min-height：缺失 → 0。
     * 包级可见以供 FlexLayout 复用同款语义。
     */
    static float resolveMinSize(String raw, float percentBase, float emBase,
                                float remBasePx, MeasureContext ctx) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0f;
        }
        return resolveLengthStatic(raw, percentBase, emBase, remBasePx, ctx, 0f);
    }

    private static float resolveLengthStatic(String raw, float percentBase, float emBase,
                                             float remBasePx, MeasureContext ctx, float autoFallback) {
        Dimension d = Dimension.parse(raw);
        if (d == null) {
            return autoFallback;
        }
        ResolveContext rc = ResolveContext.of(percentBase, 0,
                emBase, remBasePx,
                ctx.viewportWidth(), ctx.viewportHeight());
        return d.resolveToPx(rc, autoFallback);
    }

    private MarginEdges resolveMargin(ComputedStyle style, float percentBase,
                                       float emBase, float remBasePx, MeasureContext ctx) {
        float[] sides = resolveBoxSides(style, "margin",
                "margin-top", "margin-right", "margin-bottom", "margin-left",
                percentBase, emBase, remBasePx, ctx);
        // auto 标记：仅在 flex 居中场景使用，block 布局阶段视为 0
        // 阶段 2 切片 3 不解析 auto margin（保留为 false）
        return new MarginEdges(sides[0], sides[1], sides[2], sides[3],
                false, false, false, false);
    }

    private PaddingEdges resolvePadding(ComputedStyle style, float percentBase,
                                         float emBase, float remBasePx, MeasureContext ctx) {
        float[] sides = resolveBoxSides(style, "padding",
                "padding-top", "padding-right", "padding-bottom", "padding-left",
                percentBase, emBase, remBasePx, ctx);
        return new PaddingEdges(sides[0], sides[1], sides[2], sides[3]);
    }

    private BorderEdges resolveBorder(ComputedStyle style, float percentBase,
                                       float emBase, float remBasePx, MeasureContext ctx) {
        // 1. border 简写（如 "1px solid #000"）→ 提取宽度部分
        Dimension[] sides = new Dimension[4];
        String borderSh = style.get("border");
        if (borderSh != null && !borderSh.trim().isEmpty()) {
            BorderEdges sh = BorderEdges.parseBorderShorthand(borderSh);
            sides[0] = Dimension.px(sh.top());
            sides[1] = Dimension.px(sh.right());
            sides[2] = Dimension.px(sh.bottom());
            sides[3] = Dimension.px(sh.left());
        }
        // 2. border-width 简写覆盖
        String bwSh = style.get("border-width");
        if (bwSh != null && !bwSh.trim().isEmpty()) {
            BorderEdges bw = BorderEdges.parseWidthShorthand(bwSh);
            sides[0] = Dimension.px(bw.top());
            sides[1] = Dimension.px(bw.right());
            sides[2] = Dimension.px(bw.bottom());
            sides[3] = Dimension.px(bw.left());
        }
        // 3. 单边 longhand 覆盖
        String[] longhands = {"border-top-width", "border-right-width",
                "border-bottom-width", "border-left-width"};
        for (int i = 0; i < 4; i++) {
            String lh = style.get(longhands[i]);
            if (lh != null && !lh.trim().isEmpty()) {
                Dimension d = Dimension.parse(lh.trim());
                if (d != null) {
                    sides[i] = d;
                }
            }
        }
        // 4. 解析到像素
        float[] result = new float[4];
        ResolveContext rc = ResolveContext.of(percentBase, 0,
                emBase, remBasePx,
                ctx.viewportWidth(), ctx.viewportHeight());
        for (int i = 0; i < 4; i++) {
            if (sides[i] == null) {
                result[i] = 0f;
            } else {
                float v = sides[i].resolveToPx(rc, 0f);
                result[i] = v < 0 ? 0 : v;
            }
        }
        return new BorderEdges(result[0], result[1], result[2], result[3]);
    }

    /**
     * 解析 box 边距（margin/padding）四向到像素。
     * 合并 shorthand 与 longhand；longhand 覆盖 shorthand 对应方向。
     * auto 视为 0（padding 不允许 auto；margin auto 仅在 flex 居中时单独处理）。
     */
    private float[] resolveBoxSides(ComputedStyle style, String shorthandName,
                                     String topName, String rightName,
                                     String bottomName, String leftName,
                                     float percentBase, float emBase, float remBasePx,
                                     MeasureContext ctx) {
        // 1. 解析 shorthand 为 4 个 Dimension（null 表示缺失）
        Dimension[] sides = new Dimension[4];
        String sh = style.get(shorthandName);
        if (sh != null && !sh.trim().isEmpty()) {
            String[] parts = sh.trim().split("\\s+");
            if (parts.length == 1) {
                sides[0] = sides[1] = sides[2] = sides[3] = parseDimOrAuto(parts[0]);
            } else if (parts.length == 2) {
                sides[0] = sides[2] = parseDimOrAuto(parts[0]);
                sides[1] = sides[3] = parseDimOrAuto(parts[1]);
            } else if (parts.length == 3) {
                sides[0] = parseDimOrAuto(parts[0]);
                sides[1] = sides[3] = parseDimOrAuto(parts[1]);
                sides[2] = parseDimOrAuto(parts[2]);
            } else {
                for (int i = 0; i < 4; i++) {
                    sides[i] = parseDimOrAuto(parts[i]);
                }
            }
        }
        // 2. longhand 覆盖
        String[] longhands = {topName, rightName, bottomName, leftName};
        for (int i = 0; i < 4; i++) {
            String lh = style.get(longhands[i]);
            if (lh != null && !lh.trim().isEmpty()) {
                sides[i] = parseDimOrAuto(lh.trim());
            }
        }
        // 3. 解析到像素
        float[] result = new float[4];
        ResolveContext rc = ResolveContext.of(percentBase, 0,
                emBase, remBasePx,
                ctx.viewportWidth(), ctx.viewportHeight());
        for (int i = 0; i < 4; i++) {
            if (sides[i] == null || sides[i].isAuto()) {
                result[i] = 0f;
            } else {
                result[i] = sides[i].resolveToPx(rc, 0f);
            }
        }
        return result;
    }

    private static Dimension parseDimOrAuto(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.equals("auto")) {
            return Dimension.auto();
        }
        return Dimension.parse(trimmed);
    }

    private static int parseZIndex(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        String t = raw.trim();
        if (t.equals("auto")) {
            return 0;
        }
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean hasDirectText(ElementNode element) {
        for (int i = 0; i < element.childCount(); i++) {
            DomNode child = element.child(i);
            if (child instanceof TextNode && !((TextNode) child).text().trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static float resolveLineHeight(ComputedStyle style, float fontSize,
                                           float remBasePx, MeasureContext ctx) {
        String raw = style == null ? null : style.get("line-height");
        if (raw == null || raw.trim().isEmpty() || "normal".equalsIgnoreCase(raw.trim())) {
            return fontSize * 1.2F;
        }
        Dimension dimension = Dimension.parse(raw.trim());
        if (dimension == null || dimension.isAuto()) return fontSize * 1.2F;
        ResolveContext resolve = ResolveContext.of(fontSize, fontSize,
                fontSize, remBasePx, ctx.viewportWidth(), ctx.viewportHeight());
        return Math.max(0.0F, dimension.resolveToPx(resolve, fontSize * 1.2F));
    }
}
