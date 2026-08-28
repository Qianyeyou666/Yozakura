package gq.yozakura.ui.engine.input;

import gq.yozakura.ui.engine.css.ComputedStyle;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.layout.LayoutBox;

import java.util.List;
import java.util.Map;

/**
 * 命中测试：在 LayoutBox 树中找最顶层可命中元素。
 *
 * <p>命中区域 = border-box（含 border，半开区间 [x, x+w) × [y, y+h)）。
 *
 * <p>命中优先级（CSS 堆叠上下文简化）：
 * <ol>
 *   <li>子元素先于父元素：点在子上返回子</li>
 *   <li>同级兄弟按 z-index 降序：高 z-index 先命中</li>
 *   <li>z-index 相同时按 DOM 倒序：later sibling paints on top → 先命中</li>
 * </ol>
 *
 * <p>pointer-events 契约：
 * <ul>
 *   <li>{@code pointer-events: none} 跳过元素自身（不命中），但子树仍递归</li>
 *   <li>子树无命中 + 自身 none → 返回 null（让上层有机会）</li>
 *   <li>缺失 ComputedStyle 视为默认（可命中），不崩溃</li>
 * </ul>
 *
 * <p>坐标契约：根 border-box 相对视口 (0,0)；子 border-box 相对父 content origin。
 * 递归时累加：absX = parentContentOriginX + box.borderBoxX()。
 *
 * <p>排序结果由 immutable {@link LayoutBox} 在布局构造时缓存；MOVE 热路径不复制列表。
 */
public final class HitTester {

    private HitTester() {
    }

    /**
     * 命中测试。
     *
     * @param root   布局树根（border-box 相对视口 (0,0)）
     * @param styles 元素 → ComputedStyle 映射；缺失视为默认可命中
     * @param x      逻辑坐标 X
     * @param y      逻辑坐标 Y
     * @return 命中的 LayoutBox，或 null
     */
    public static LayoutBox hit(LayoutBox root,
                                 Map<ElementNode, ComputedStyle> styles,
                                 float x, float y) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        if (styles == null) {
            throw new IllegalArgumentException("styles must not be null");
        }
        return hitInternal(root, styles, x, y, 0f, 0f);
    }

    private static LayoutBox hitInternal(LayoutBox box,
                                          Map<ElementNode, ComputedStyle> styles,
                                          float x, float y,
                                          float parentContentOriginX,
                                          float parentContentOriginY) {
        float absX = parentContentOriginX + box.borderBoxX();
        float absY = parentContentOriginY + box.borderBoxY();
        float w = box.borderBoxWidth();
        float h = box.borderBoxHeight();

        // 子元素先递归（高 z-index / later sibling 优先）
        float contentOriginX = absX + box.border().left() + box.padding().left();
        float contentOriginY = absY + box.border().top() + box.padding().top();

        if (box.childCount() > 0) {
            List<LayoutBox> ordered = box.hitChildren();
            for (int i = 0; i < ordered.size(); i++) {
                LayoutBox child = ordered.get(i);
                LayoutBox hit = hitInternal(child, styles, x, y, contentOriginX, contentOriginY);
                if (hit != null) {
                    return hit;
                }
            }
        }

        // 子树未命中，检查 box 自身
        if (isPointerEventsNone(box, styles)) {
            return null;  // 自身跳过，让上层有机会
        }
        if (inBorderBox(x, y, absX, absY, w, h)) {
            return box;
        }
        return null;
    }

    private static boolean isPointerEventsNone(LayoutBox box,
                                                Map<ElementNode, ComputedStyle> styles) {
        ComputedStyle style = styles.get(box.element());
        if (style == null) {
            return false;  // 缺失视为默认可命中
        }
        String pe = style.get("pointer-events");
        return pe != null && pe.equalsIgnoreCase("none");
    }

    /** 半开区间 [x, x+w) × [y, y+h)。 */
    private static boolean inBorderBox(float px, float py,
                                        float x, float y, float w, float h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
