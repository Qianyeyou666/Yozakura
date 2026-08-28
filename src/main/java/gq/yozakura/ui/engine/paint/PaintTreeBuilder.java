package gq.yozakura.ui.engine.paint;

import gq.yozakura.ui.engine.css.ComputedStyle;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.dom.DomNode;
import gq.yozakura.ui.engine.dom.TextNode;
import gq.yozakura.ui.engine.layout.BorderEdges;
import gq.yozakura.ui.engine.layout.Dimension;
import gq.yozakura.ui.engine.layout.LayoutBox;
import gq.yozakura.ui.engine.layout.Overflow;

import java.util.Map;
import java.util.List;
import java.util.Collections;

/**
 * Paint 树构建器：遍历 LayoutBox 树 + ComputedStyle 映射，产出 {@link PaintCommandList}。
 *
 * <p>渲染顺序（CSS 规范简化）：
 * <ol>
 *   <li>元素自身 background（border-box 几何）</li>
 *   <li>元素自身 border（含 radius）</li>
 *   <li>若 overflow 裁剪：ClipPush（padding 盒）</li>
 *   <li>递归子元素（按 DOM 顺序）</li>
 *   <li>若 overflow 裁剪：ClipPop</li>
 * </ol>
 *
 * <p>坐标转换：LayoutBox 使用相对父 content 原点的坐标；本构建器累加为绝对逻辑坐标。
 * 绝对坐标 = 父 content 原点 + 本节点 borderBoxX/Y。
 * 父 content 原点 = 父绝对 border-box 原点 + 父 border.left + 父 padding.left。
 *
 * <p>不持有 GL 资源；输出可任意线程构造，渲染线程回放。
 *
 * <p>Invalid visual values fail with element and property context. Silently omitting
 * a command would hide a broken CSS resource and violate the renderer fallback policy.
 */
public final class PaintTreeBuilder {

    public PaintCommandList build(LayoutBox root, Map<ElementNode, ComputedStyle> styles) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        if (styles == null) {
            throw new IllegalArgumentException("styles must not be null");
        }
        return build(root, styles, Collections.<ElementNode, PaintVisualState>emptyMap());
    }

    public PaintCommandList build(LayoutBox root, Map<ElementNode, ComputedStyle> styles,
                                  Map<ElementNode, PaintVisualState> visualStates) {
        if (root == null || styles == null || visualStates == null) {
            throw new IllegalArgumentException("root, styles and visualStates must not be null");
        }
        PaintCommandList list = new PaintCommandList();
        buildInternal(root, styles, visualStates, 0f, 0f, 0f, 0f, 1f, list);
        return list;
    }

    private void buildInternal(LayoutBox box, Map<ElementNode, ComputedStyle> styles,
                                Map<ElementNode, PaintVisualState> visualStates,
                                float parentContentOriginX, float parentContentOriginY,
                                float parentTranslateX, float parentTranslateY,
                                float parentOpacity, PaintCommandList out) {
        // 本节点 border-box 绝对原点 = 父 content 原点 + 本节点 borderBox 偏移
        float rawX = parentContentOriginX + box.borderBoxX();
        float rawY = parentContentOriginY + box.borderBoxY();
        PaintVisualState visual = visualStates.get(box.element());
        if (visual == null) visual = PaintVisualState.IDENTITY;
        float translateX = parentTranslateX + visual.translateX();
        float translateY = parentTranslateY + visual.translateY();
        float opacity = parentOpacity * visual.opacity();
        float absX = rawX + translateX;
        float absY = rawY + translateY;
        float bw = box.borderBoxWidth();
        float bh = box.borderBoxHeight();

        ComputedStyle style = styles.get(box.element());

        // 1. Bounded outer shadow, painted behind the element background.
        if (style != null) {
            appendBoxShadow(style.get("box-shadow"), absX, absY, bw, bh,
                    parseBorderRadii(style.get("border-radius")), box.element(), opacity, out);
        }

        // 2. Background（border-box）
        if (style != null) {
            float radius = parseBorderRadii(style.get("border-radius"))[0];
            RectFillCommand background;
            if (box.element().hasClass("color-hue")) {
                background = RectFillCommand.hue(absX, absY, bw, bh, radius);
            } else if (box.element().hasClass("color-sv")) {
                background = RectFillCommand.palette(absX, absY, bw, bh, radius,
                        parseUnitInterval(box.element().attribute("data-hue")));
            } else {
                background = parseBackground(style, absX, absY, bw, bh, box.element());
            }
            if (background != null) out.append(background.withOpacity(opacity));
        }

        // 3. Border
        if (style != null) {
            BorderEdges be = box.border();
            if (be != null && (be.top() > 0 || be.right() > 0 || be.bottom() > 0 || be.left() > 0)) {
                Color borderColor = tryParseBorderColor(style, box.element());
                if (borderColor != null) {
                    float radius = parseBorderRadii(style.get("border-radius"))[0];
                    out.append(new RectBorderCommand(absX, absY, bw, bh,
                            be.top(), be.right(), be.bottom(), be.left(),
                            multiplyAlpha(borderColor, opacity), radius));
                }
            }
        }

        // 4. Text content is painted above the element background and below child elements.
        if (style != null) {
            String text = directText(box.element());
            if (!text.isEmpty()) {
                float fontSize = parseFontSize(style.get("font-size"));
                String family = parseFontFamily(style.get("font-family"));
                boolean bold = parseBold(style.get("font-weight"));
                String rawColor = style.get("color");
                Color textColor = parseColor(rawColor == null ? "#000000" : rawColor,
                        box.element(), "color");
                float textX = absX + box.border().left() + box.padding().left();
                float verticalRoom = Math.max(0.0F, box.contentHeight() - fontSize);
                float baselineY = absY + box.border().top() + box.padding().top()
                        + verticalRoom * 0.5F + fontSize * 0.8F;
                out.append(new TextPaintCommand(text, textX, baselineY, fontSize,
                        family, bold, multiplyAlpha(textColor, opacity),
                        parseTextAlign(style.get("text-align")),
                        Math.max(0.0F, box.contentWidth())));
            }
        }

        // 5. overflow 裁剪（padding 盒）
        Overflow overflow = box.overflow();
        boolean clips = overflow != null && overflow.clips();
        if (clips) {
            // padding 盒 = border-box 内缩 border
            float padX = absX + box.border().left();
            float padY = absY + box.border().top();
            float padW = bw - box.border().horizontalSum();
            float padH = bh - box.border().verticalSum();
            // 钳制负尺寸为 0（border 比 border-box 还大时）
            out.append(new ClipPushCommand(padX, padY,
                    padW < 0 ? 0 : padW,
                    padH < 0 ? 0 : padH));
        }

        // 6. 递归子元素（content 原点），stable-sorted by z-index.
        float contentOriginX = rawX + box.border().left() + box.padding().left();
        float contentOriginY = rawY + box.border().top() + box.padding().top();
        List<LayoutBox> orderedChildren = box.paintChildren();
        for (int i = 0; i < orderedChildren.size(); i++) {
            buildInternal(orderedChildren.get(i), styles, visualStates,
                    contentOriginX, contentOriginY, translateX, translateY, opacity, out);
        }

        // 7. ClipPop
        if (clips) {
            out.append(new ClipPopCommand());
        }
    }

    private static float parseUnitInterval(String value) {
        if (value == null) return 0.0F;
        try {
            return Math.max(0.0F, Math.min(1.0F, Float.parseFloat(value)));
        } catch (NumberFormatException ignored) {
            return 0.0F;
        }
    }

    private static Color parseColor(String raw, ElementNode element, String property) {
        try {
            return Color.parse(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + property + " '" + raw
                    + "' on <" + element.tag() + "> at " + element.sourcePosition(), e);
        }
    }

    private static Color multiplyAlpha(Color color, float opacity) {
        return Color.fromRgba(color.r(), color.g(), color.b(), color.a() * opacity);
    }

    private static void appendBoxShadow(String raw, float x, float y, float width, float height,
                                        float[] radii, ElementNode element, float opacity,
                                        PaintCommandList out) {
        if (raw == null || raw.trim().isEmpty() || "none".equalsIgnoreCase(raw.trim())) return;
        String first = firstTopLevelValue(raw);
        if (first.toLowerCase().contains("inset")) return;
        List<String> tokens = splitWhitespaceOutsideParentheses(first);
        List<Float> lengths = new java.util.ArrayList<Float>();
        Color color = null;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            Dimension dimension = Dimension.parse(token);
            if (dimension != null && !dimension.isAuto()) {
                lengths.add(dimension.value());
                continue;
            }
            try {
                color = Color.parse(token);
            } catch (IllegalArgumentException ignored) {
                // The complete error below identifies the element and original property.
            }
        }
        if (lengths.size() < 2 || color == null) {
            throw new IllegalArgumentException("invalid box-shadow '" + raw + "' on <"
                    + element.tag() + "> at " + element.sourcePosition());
        }
        float offsetX = lengths.get(0);
        float offsetY = lengths.get(1);
        float blur = lengths.size() > 2 ? Math.max(0.0F, lengths.get(2)) : 0.0F;
        float spread = lengths.size() > 3 ? lengths.get(3) : 0.0F;
        float expandedWidth = width + spread * 2.0F;
        float expandedHeight = height + spread * 2.0F;
        if (blur <= 0.0F) {
            out.append(new RectFillCommand(x + offsetX - spread, y + offsetY - spread,
                    expandedWidth, expandedHeight, multiplyAlpha(color, opacity),
                    radii[0] + spread, radii[1] + spread,
                    radii[2] + spread, radii[3] + spread));
            return;
        }
        out.append(RectFillCommand.shadow(
                x + offsetX - spread, y + offsetY - spread,
                expandedWidth, expandedHeight, multiplyAlpha(color, opacity), blur,
                radii[0] + spread, radii[1] + spread,
                radii[2] + spread, radii[3] + spread));
    }

    private static RectFillCommand parseBackground(ComputedStyle style,
                                                   float x, float y, float width, float height,
                                                   ElementNode element) {
        float[] radii = parseBorderRadii(style.get("border-radius"));
        String background = style.get("background");
        if (background != null && background.trim().toLowerCase().startsWith("linear-gradient(")) {
            String value = background.trim();
            int open = value.indexOf('(');
            int close = value.lastIndexOf(')');
            if (open < 0 || close <= open) {
                throw new IllegalArgumentException("invalid background '" + background
                        + "' on <" + element.tag() + "> at " + element.sourcePosition());
            }
            List<String> arguments = splitTopLevelCommas(value.substring(open + 1, close));
            float angle = 180.0F;
            int colorIndex = 0;
            if (!arguments.isEmpty() && arguments.get(0).trim().toLowerCase().endsWith("deg")) {
                String angleText = arguments.get(0).trim();
                try {
                    angle = Float.parseFloat(angleText.substring(0, angleText.length() - 3).trim());
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("invalid linear-gradient angle '" + angleText
                            + "' on <" + element.tag() + "> at " + element.sourcePosition(), error);
                }
                colorIndex = 1;
            }
            if (arguments.size() < colorIndex + 2) {
                throw new IllegalArgumentException("linear-gradient requires two colors on <"
                        + element.tag() + "> at " + element.sourcePosition());
            }
            Color start = parseColor(arguments.get(colorIndex).trim(), element, "background");
            Color end = parseColor(arguments.get(colorIndex + 1).trim(), element, "background");
            if (start.a() <= 0.0F && end.a() <= 0.0F) return null;
            return new RectFillCommand(x, y, width, height, start, end, angle,
                    radii[0], radii[1], radii[2], radii[3]);
        }
        String rawColor = style.get("background-color");
        if (rawColor == null && background != null) rawColor = background;
        if (rawColor == null) return null;
        Color color = parseColor(rawColor, element, "background-color");
        return color.a() <= 0.0F ? null : new RectFillCommand(x, y, width, height, color,
                radii[0], radii[1], radii[2], radii[3]);
    }

    private static String firstTopLevelValue(String raw) {
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            else if (c == ',' && depth == 0) return raw.substring(0, i).trim();
        }
        return raw.trim();
    }

    private static List<String> splitWhitespaceOutsideParentheses(String raw) {
        List<String> tokens = new java.util.ArrayList<String>();
        StringBuilder token = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '(') depth++;
            if (Character.isWhitespace(c) && depth == 0) {
                if (token.length() > 0) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(c);
            }
            if (c == ')') depth = Math.max(0, depth - 1);
        }
        if (token.length() > 0) tokens.add(token.toString());
        return tokens;
    }

    private static List<String> splitTopLevelCommas(String raw) {
        List<String> values = new java.util.ArrayList<String>();
        StringBuilder value = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '(') depth++;
            if (c == ',' && depth == 0) {
                values.add(value.toString().trim());
                value.setLength(0);
            } else {
                value.append(c);
            }
            if (c == ')') depth = Math.max(0, depth - 1);
        }
        if (value.length() > 0) values.add(value.toString().trim());
        return values;
    }

    /**
     * 提取 border 颜色：优先 border-color，再从 border 简写中找可解析的颜色 token，
     * 最后尝试 border-top-color。找不到返回 null（不绘制 border）。
     */
    private static Color tryParseBorderColor(ComputedStyle style, ElementNode element) {
        String explicit = style.get("border-color");
        if (explicit != null) {
            return parseColor(explicit, element, "border-color");
        }
        String shorthand = style.get("border");
        if (shorthand != null) {
            // 在简写中找可解析为颜色的 token
            String[] parts = shorthand.trim().split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                // 跳过长度 token（含单位）和样式关键字
                if (isLengthToken(p) || isBorderStyleKeyword(p)) continue;
                return parseColor(p, element, "border");
            }
        }
        String topColor = style.get("border-top-color");
        if (topColor != null) {
            return parseColor(topColor, element, "border-top-color");
        }
        return null;
    }

    private static String directText(ElementNode element) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < element.childCount(); i++) {
            DomNode child = element.child(i);
            if (child instanceof TextNode) {
                value.append(((TextNode) child).text());
            }
        }
        return value.toString().trim().replaceAll("\\s+", " ");
    }

    private static float parseFontSize(String raw) {
        if (raw == null) return 14.0F;
        Dimension dimension = Dimension.parse(raw.trim());
        if (dimension == null || dimension.isAuto()) return 14.0F;
        return Math.max(1.0F, dimension.value());
    }

    private static String parseFontFamily(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Inter";
        String first = raw.split(",", 2)[0].trim();
        if (first.length() >= 2 && ((first.charAt(0) == '\'' && first.charAt(first.length() - 1) == '\'')
                || (first.charAt(0) == '"' && first.charAt(first.length() - 1) == '"'))) {
            first = first.substring(1, first.length() - 1);
        }
        return first;
    }

    private static boolean parseBold(String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        if ("bold".equalsIgnoreCase(value) || "bolder".equalsIgnoreCase(value)) return true;
        try {
            return Integer.parseInt(value) >= 600;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static int parseTextAlign(String raw) {
        if (raw == null) return TextPaintCommand.ALIGN_LEFT;
        String value = raw.trim();
        if ("center".equalsIgnoreCase(value)) return TextPaintCommand.ALIGN_CENTER;
        if ("right".equalsIgnoreCase(value) || "end".equalsIgnoreCase(value)) {
            return TextPaintCommand.ALIGN_RIGHT;
        }
        return TextPaintCommand.ALIGN_LEFT;
    }

    private static boolean isLengthToken(String s) {
        return Dimension.parse(s) != null;
    }

    private static boolean isBorderStyleKeyword(String s) {
        String l = s.toLowerCase();
        return l.equals("none") || l.equals("hidden") || l.equals("dotted")
                || l.equals("dashed") || l.equals("solid") || l.equals("double")
                || l.equals("groove") || l.equals("ridge") || l.equals("inset")
                || l.equals("outset");
    }

    /** Parses CSS 1/2/3/4-value corner radii (elliptical slash syntax remains out of MVP). */
    private static float[] parseBorderRadii(String raw) {
        if (raw == null) return new float[]{0f, 0f, 0f, 0f};
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return new float[]{0f, 0f, 0f, 0f};
        int slash = trimmed.indexOf('/');
        if (slash >= 0) trimmed = trimmed.substring(0, slash).trim();
        String[] tokens = trimmed.split("\\s+");
        int count = Math.min(4, tokens.length);
        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            Dimension dimension = Dimension.parse(tokens[i]);
            values[i] = dimension == null ? 0.0F : Math.max(0.0F, dimension.value());
        }
        if (count == 1) return new float[]{values[0], values[0], values[0], values[0]};
        if (count == 2) return new float[]{values[0], values[1], values[0], values[1]};
        if (count == 3) return new float[]{values[0], values[1], values[2], values[1]};
        return new float[]{values[0], values[1], values[2], values[3]};
    }
}
