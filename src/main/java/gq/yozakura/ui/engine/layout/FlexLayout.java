package gq.yozakura.ui.engine.layout;

import gq.yozakura.ui.engine.css.ComputedStyle;
import gq.yozakura.ui.engine.dom.DomNode;
import gq.yozakura.ui.engine.dom.ElementNode;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flex 布局：实现 display:flex 容器的子元素排布。
 *
 * <p>阶段 2 切片 4-6 实现：
 * <ul>
 *   <li>flex-direction: row（主轴=水平）/ column（主轴=垂直）</li>
 *   <li>justify-content: flex-start / center / flex-end / space-between / space-around / space-evenly</li>
 *   <li>align-items: stretch / flex-start / center / flex-end（交叉轴）</li>
 *   <li>align-self: 单个元素交叉轴对齐覆盖</li>
 *   <li>gap: 主轴与交叉轴的项间距</li>
 *   <li>flex-grow / flex-shrink / flex-basis：主轴剩余空间分配与溢出收缩</li>
 *   <li>min/max-width/height：最终主轴尺寸钳制</li>
 * </ul>
 *
 * <p>MVP 不实现 flex-wrap（单行/单列）。列向 flex 项的 auto height 使用
 * 已测量的内容高度；行向 auto width 仍以 0 为 base，依赖 flex-grow 撑开。
 *
 * <p>算法（三阶段）：
 * <ol>
 *   <li>度量阶段：对每个非 display:none 子元素调用 {@link LayoutEngine#measureChildForFlex}
 *       得到带尺寸的 LayoutBox（位置待定）。</li>
 *   <li>主轴分配阶段：计算 flex-basis 作为 base 主轴尺寸，按 grow/shrink 分配剩余空间，
 *       用 min/max 钳制，产出最终主轴尺寸并 resize 子元素。</li>
 *   <li>排布阶段：按 justify-content 分配主轴剩余空间，按 align-items 定位交叉轴，
 *       用 {@link LayoutBox#repositioned} 生成最终 LayoutBox。</li>
 * </ol>
 *
 * <p>坐标语义与 block 一致：子元素 borderBoxX/Y 相对 flex 容器 content 原点。
 */
final class FlexLayout {

    private final LayoutEngine engine;

    FlexLayout(LayoutEngine engine) {
        this.engine = engine;
    }

    /** 交叉轴对齐。 */
    private enum AlignItems {
        STRETCH, FLEX_START, CENTER, FLEX_END;

        static AlignItems parse(String raw) {
            if (raw == null) return STRETCH;
            String t = raw.trim();
            if (t.equals("center")) return CENTER;
            if (t.equals("flex-end")) return FLEX_END;
            if (t.equals("stretch")) return STRETCH;
            return FLEX_START; // 默认 flex-start
        }
    }

    /** 主轴对齐。 */
    private enum JustifyContent {
        FLEX_START, CENTER, FLEX_END, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY;

        static JustifyContent parse(String raw) {
            if (raw == null) return FLEX_START;
            String t = raw.trim();
            if (t.equals("center")) return CENTER;
            if (t.equals("flex-end")) return FLEX_END;
            if (t.equals("space-between")) return SPACE_BETWEEN;
            if (t.equals("space-around")) return SPACE_AROUND;
            if (t.equals("space-evenly")) return SPACE_EVENLY;
            return FLEX_START;
        }
    }

    /** flex-direction。 */
    private enum FlexDirection {
        ROW, COLUMN;

        static FlexDirection parse(String raw) {
            if (raw == null) return ROW;
            String t = raw.trim();
            if (t.equals("column")) return COLUMN;
            // row | row-reverse | column-reverse 暂不区分方向反转
            return ROW;
        }
    }

    /**
     * 排布 flex 容器的子元素，结果追加到 {@code outChildren}。
     *
     * @param containerContentWidth   flex 容器内容区宽（已减 padding/border）
     * @param containerContentHeightOrU  flex 容器内容区高；{@link LayoutEngine#UNDETERMINED} 表示未确定
     * @param cbAbsX / cbAbsY / cbWidth / cbHeightOrU  后代 containing block（absolute 子元素定位用）
     */
    void layout(ElementNode container,
                Map<ElementNode, ComputedStyle> styles,
                MeasureContext ctx,
                float elementFontPx, float remBasePx,
                float absContentX, float absContentY,
                float containerContentWidth, float containerContentHeightOrU,
                List<LayoutBox> outChildren,
                float cbAbsX, float cbAbsY,
                float cbWidth, float cbHeightOrU) {
        ComputedStyle containerStyle = styles.get(container);
        FlexDirection direction = FlexDirection.parse(containerStyle.get("flex-direction"));
        JustifyContent justify = JustifyContent.parse(containerStyle.get("justify-content"));
        AlignItems align = AlignItems.parse(containerStyle.get("align-items"));
        float gap = resolveGap(containerStyle, containerContentWidth,
                elementFontPx, remBasePx, ctx);

        // 1. 度量所有非 display:none 子元素
        List<LayoutBox> measured = new ArrayList<LayoutBox>();
        List<ElementNode> measuredElements = new ArrayList<ElementNode>();
        List<ComputedStyle> childStyleList = new ArrayList<ComputedStyle>();
        Map<ElementNode, LayoutBox> absoluteBoxes = new IdentityHashMap<ElementNode, LayoutBox>();
        for (int i = 0; i < container.childCount(); i++) {
            DomNode node = container.child(i);
            if (!(node instanceof ElementNode)) continue;
            ElementNode childEl = (ElementNode) node;
            ComputedStyle childStyle = styles.get(childEl);
            if (childStyle != null && "none".equals(trim(childStyle.get("display")))) continue;
            LayoutBox box = engine.measureChildForFlex(childEl, styles, ctx,
                    elementFontPx, remBasePx,
                    absContentX, absContentY,
                    containerContentWidth, containerContentHeightOrU,
                    cbAbsX, cbAbsY, cbWidth, cbHeightOrU);
            if (box != null) {
                if (box.position() == Position.ABSOLUTE) {
                    absoluteBoxes.put(childEl, box);
                } else {
                    measured.add(box);
                    measuredElements.add(childEl);
                    childStyleList.add(childStyle);
                }
            }
        }
        if (measured.isEmpty()) {
            appendInDomOrder(container, absoluteBoxes,
                    new IdentityHashMap<ElementNode, LayoutBox>(), outChildren);
            return;
        }

        boolean row = (direction == FlexDirection.ROW);
        int n = measured.size();

        // 2. 解析每项的 flex-grow / flex-shrink / flex-basis 与 min/max
        float[] grow = new float[n];
        float[] shrink = new float[n];
        float[] baseMain = new float[n];     // border-box 主轴 base（不含 margin）
        float[] minMain = new float[n];
        float[] maxMain = new float[n];
        float[] marginMain = new float[n];   // 主轴方向 margin 总和

        float mainPercentBase = containerContentWidth; // 主轴 % 基准（row 用容器宽）
        Map<ElementNode, LayoutBox> flowBoxes = new IdentityHashMap<ElementNode, LayoutBox>();
        for (int i = 0; i < n; i++) {
            ComputedStyle cs = childStyleList.get(i);
            LayoutBox b = measured.get(i);
            grow[i] = parseNumber(cs.get("flex-grow"), 0f);
            shrink[i] = parseNumber(cs.get("flex-shrink"), 1f); // CSS 默认 shrink=1

            // flex-basis：length → base；auto/缺失 → 回退到主轴尺寸
            float basis = resolveFlexBasis(cs.get("flex-basis"),
                    mainPercentBase, elementFontPx, remBasePx, ctx);
            if (basis >= 0f) {
                baseMain[i] = basis;
            } else {
                String mainProp = row ? cs.get("width") : cs.get("height");
                if (isAuto(mainProp)) {
                    // Column 的 block 度量能提供可靠的内容高度；若仍使用 0，
                    // auto-height 分组会全部定位到同一个 Y 并彼此覆盖。
                    // Row 的 auto width 当前按 block 可用宽度度量，并非固有宽度，
                    // 因此继续以 0 为 base，保留空 flex-grow 项的现有行为。
                    baseMain[i] = row ? 0f : b.borderBoxHeight();
                } else {
                    baseMain[i] = row ? b.borderBoxWidth() : b.borderBoxHeight();
                }
            }

            // min/max（主轴方向）
            String minProp = row ? cs.get("min-width") : cs.get("min-height");
            String maxProp = row ? cs.get("max-width") : cs.get("max-height");
            minMain[i] = LayoutEngine.resolveMinSize(minProp,
                    mainPercentBase, elementFontPx, remBasePx, ctx);
            maxMain[i] = LayoutEngine.resolveMaxSize(maxProp,
                    mainPercentBase, elementFontPx, remBasePx, ctx);
            // base 钳制到 [min, max]
            baseMain[i] = clamp(baseMain[i], minMain[i], maxMain[i]);

            marginMain[i] = row
                    ? b.margin().horizontalSum()
                    : b.margin().verticalSum();
        }

        // 3. 计算主轴总占用与剩余空间
        float mainSize = row ? containerContentWidth : containerContentHeightOrU;
        boolean mainUndetermined = (!row) && mainSize < 0;
        if (mainUndetermined) {
            mainSize = 0f; // column + height:auto：无主轴约束，按内容堆叠
        }
        float gapTotal = gap * (n - 1);
        if (gapTotal < 0) gapTotal = 0;
        float totalBase = gapTotal;
        for (int i = 0; i < n; i++) {
            totalBase += baseMain[i] + marginMain[i];
        }
        float freeSpace = mainUndetermined ? 0f : mainSize - totalBase;

        // 4. 分配剩余空间（grow 正空间 / shrink 负空间）
        float[] finalMain = new float[n];
        System.arraycopy(baseMain, 0, finalMain, 0, n);
        if (freeSpace > 0f) {
            float sumGrow = 0f;
            for (int i = 0; i < n; i++) sumGrow += grow[i];
            if (sumGrow > 0f) {
                for (int i = 0; i < n; i++) {
                    if (grow[i] > 0f) {
                        finalMain[i] = baseMain[i] + freeSpace * grow[i] / sumGrow;
                    }
                }
            }
        } else if (freeSpace < 0f) {
            // 收缩权重 = shrink * base（CSS 规范）
            float sumShrinkWeight = 0f;
            for (int i = 0; i < n; i++) {
                sumShrinkWeight += shrink[i] * baseMain[i];
            }
            if (sumShrinkWeight > 0f) {
                float overflow = -freeSpace;
                for (int i = 0; i < n; i++) {
                    if (shrink[i] > 0f && baseMain[i] > 0f) {
                        float shrinkAmount = overflow
                                * (shrink[i] * baseMain[i]) / sumShrinkWeight;
                        finalMain[i] = baseMain[i] - shrinkAmount;
                    }
                }
            }
        }

        // 5. min/max 钳制最终主轴尺寸
        for (int i = 0; i < n; i++) {
            finalMain[i] = clamp(finalMain[i], minMain[i], maxMain[i]);
        }

        // 6. 按最终主轴尺寸 resize 子元素（cross 尺寸不变，子树不重布局；MVP 简化）
        List<LayoutBox> resized = new ArrayList<LayoutBox>(n);
        for (int i = 0; i < n; i++) {
            LayoutBox b = measured.get(i);
            if (row) {
                resized.add(b.withBorderBoxSize(finalMain[i], b.borderBoxHeight()));
            } else {
                resized.add(b.withBorderBoxSize(b.borderBoxWidth(), finalMain[i]));
            }
        }

        // 7. 用最终尺寸重算 outerMain 与剩余空间（用于 justify-content）
        float[] outerMain = new float[n];
        float totalMainFinal = gapTotal;
        for (int i = 0; i < n; i++) {
            LayoutBox b = resized.get(i);
            outerMain[i] = (row ? b.margin().horizontalSum() + b.borderBoxWidth()
                                : b.margin().verticalSum() + b.borderBoxHeight());
            totalMainFinal += outerMain[i];
        }
        float remainingFree = mainUndetermined ? 0f : mainSize - totalMainFinal;
        if (remainingFree < 0f && !mainUndetermined) remainingFree = 0f;

        // 8. 主轴偏移
        float[] mainOffset = computeMainOffsets(justify, n, outerMain, gap, remainingFree);

        // 9. 交叉轴尺寸与排布
        float crossSize = row ? containerContentHeightOrU : containerContentWidth;
        float maxCross = 0f;
        for (int i = 0; i < n; i++) {
            LayoutBox b = resized.get(i);
            float oc = row
                    ? b.margin().verticalSum() + b.borderBoxHeight()
                    : b.margin().horizontalSum() + b.borderBoxWidth();
            if (oc > maxCross) maxCross = oc;
        }
        boolean crossUndetermined = row && crossSize < 0;
        if (crossUndetermined) {
            crossSize = maxCross; // height:auto → 交叉轴取最大子元素外尺寸
        }

        for (int i = 0; i < n; i++) {
            LayoutBox b = resized.get(i);
            AlignItems selfAlign = resolveAlignSelf(childStyleList.get(i), align);
            float mainStart = mainOffset[i];
            float crossStart = computeCrossOffset(selfAlign, b, crossSize, row);

            float newBorderBoxX;
            float newBorderBoxY;
            if (row) {
                newBorderBoxX = mainStart + b.margin().left();
                newBorderBoxY = crossStart + b.margin().top();
            } else {
                newBorderBoxX = crossStart + b.margin().left();
                newBorderBoxY = mainStart + b.margin().top();
            }
            // stretch：交叉轴拉伸到容器交叉尺寸（减自身 margin）
            LayoutBox finalBox;
            if (selfAlign == AlignItems.STRETCH && !crossUndetermined) {
                float targetCross = crossSize - crossMargin(b, row);
                finalBox = b.repositioned(newBorderBoxX, newBorderBoxY);
                if (row) {
                    finalBox = finalBox.withBorderBoxSize(
                            finalBox.borderBoxWidth(), Math.max(0, targetCross));
                } else {
                    finalBox = finalBox.withBorderBoxSize(
                            Math.max(0, targetCross), finalBox.borderBoxHeight());
                }
            } else {
                finalBox = b.repositioned(newBorderBoxX, newBorderBoxY);
            }
            flowBoxes.put(measuredElements.get(i), finalBox);
        }
        appendInDomOrder(container, absoluteBoxes, flowBoxes, outChildren);
    }

    private static void appendInDomOrder(ElementNode container,
                                         Map<ElementNode, LayoutBox> absoluteBoxes,
                                         Map<ElementNode, LayoutBox> flowBoxes,
                                         List<LayoutBox> outChildren) {
        for (int i = 0; i < container.childCount(); i++) {
            DomNode node = container.child(i);
            if (!(node instanceof ElementNode)) continue;
            ElementNode element = (ElementNode) node;
            LayoutBox box = absoluteBoxes.get(element);
            if (box == null) box = flowBoxes.get(element);
            if (box != null) outChildren.add(box);
        }
    }

    /** 计算每个子元素主轴起点偏移（相对容器 content 原点）。 */
    private static float[] computeMainOffsets(JustifyContent justify, int n,
                                              float[] outerMain, float gap,
                                              float freeSpace) {
        float[] offsets = new float[n];
        if (n == 0) return offsets;
        switch (justify) {
            case FLEX_START:
                fillPacked(offsets, outerMain, gap, 0f, n);
                break;
            case CENTER:
                fillPacked(offsets, outerMain, gap, freeSpace / 2f, n);
                break;
            case FLEX_END:
                fillPacked(offsets, outerMain, gap, freeSpace, n);
                break;
            case SPACE_BETWEEN: {
                float between = n > 1 ? freeSpace / (n - 1) : 0f;
                float cursor = 0f;
                for (int i = 0; i < n; i++) {
                    offsets[i] = cursor;
                    cursor += outerMain[i] + gap + between;
                }
                break;
            }
            case SPACE_AROUND: {
                float unit = n > 0 ? freeSpace / n : 0f;
                float cursor = unit / 2f;
                for (int i = 0; i < n; i++) {
                    offsets[i] = cursor;
                    cursor += outerMain[i] + gap + unit;
                }
                break;
            }
            case SPACE_EVENLY: {
                float unit = n > 0 ? freeSpace / (n + 1) : 0f;
                float cursor = unit;
                for (int i = 0; i < n; i++) {
                    offsets[i] = cursor;
                    cursor += outerMain[i] + gap + unit;
                }
                break;
            }
            default:
                fillPacked(offsets, outerMain, gap, 0f, n);
                break;
        }
        return offsets;
    }

    /** 填充打包式（flex-start/center/flex-end）偏移：项与项之间仅 gap。 */
    private static void fillPacked(float[] offsets, float[] outerMain,
                                   float gap, float startOffset, int n) {
        float cursor = startOffset;
        for (int i = 0; i < n; i++) {
            offsets[i] = cursor;
            cursor += outerMain[i] + gap;
        }
    }

    /** 计算单个子元素交叉轴起点偏移（不含 margin）。 */
    private static float computeCrossOffset(AlignItems selfAlign, LayoutBox b,
                                            float crossSize, boolean row) {
        float outerCross = row
                ? b.margin().verticalSum() + b.borderBoxHeight()
                : b.margin().horizontalSum() + b.borderBoxWidth();
        float available = crossSize - outerCross;
        if (available < 0) available = 0;
        switch (selfAlign) {
            case CENTER:
                return available / 2f;
            case FLEX_END:
                return available;
            case STRETCH:
            case FLEX_START:
            default:
                return 0f;
        }
    }

    /** 解析 align-self，缺省回退到容器 align-items。 */
    private static AlignItems resolveAlignSelf(ComputedStyle style, AlignItems fallback) {
        if (style == null) return fallback;
        String raw = style.get("align-self");
        if (raw == null || raw.trim().isEmpty() || raw.trim().equals("auto")) {
            return fallback;
        }
        AlignItems a = AlignItems.parse(raw);
        return a == AlignItems.STRETCH ? fallback : a; // auto/stretch → 容器值（MVP 简化）
    }

    /** 交叉轴 margin 总和。 */
    private static float crossMargin(LayoutBox b, boolean row) {
        return row ? b.margin().verticalSum() : b.margin().horizontalSum();
    }

    /** 解析 gap 到像素。仅支持单值 gap（行/列间距相同）。 */
    private float resolveGap(ComputedStyle style, float percentBase,
                             float emBase, float remBasePx, MeasureContext ctx) {
        String raw = style.get("gap");
        if (raw == null || raw.trim().isEmpty()) return 0f;
        // 多值 gap（"row col"）取第一个；MVP 仅用单值
        String first = raw.trim().split("\\s+")[0];
        Dimension d = Dimension.parse(first);
        if (d == null) return 0f;
        ResolveContext rc = ResolveContext.of(percentBase, 0,
                emBase, remBasePx, ctx.viewportWidth(), ctx.viewportHeight());
        float v = d.resolveToPx(rc, 0f);
        return v < 0 ? 0 : v;
    }

    /**
     * 解析 flex-basis 为 border-box 主轴像素值。
     * @return 解析后的像素值；auto/缺失/content 返回 -1 表示回退到 width/height
     */
    private float resolveFlexBasis(String raw, float percentBase,
                                   float emBase, float remBasePx, MeasureContext ctx) {
        if (raw == null || raw.trim().isEmpty()
                || raw.trim().equals("auto") || raw.trim().equals("content")) {
            return -1f;
        }
        Dimension d = Dimension.parse(raw);
        if (d == null || d.isAuto()) return -1f;
        ResolveContext rc = ResolveContext.of(percentBase, 0,
                emBase, remBasePx, ctx.viewportWidth(), ctx.viewportHeight());
        float v = d.resolveToPx(rc, 0f);
        return v < 0 ? 0 : v;
    }

    /** 解析无单位数值（flex-grow / flex-shrink）。 */
    private static float parseNumber(String raw, float fallback) {
        if (raw == null || raw.trim().isEmpty()) return fallback;
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static boolean isAuto(String raw) {
        return raw == null || raw.trim().equals("auto");
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
