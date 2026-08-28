package gq.yozakura.ui.engine.css;

import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 阶段 1 切片 6：StyleResolver cascade + 继承 + var() 解析契约测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>单规则应用</li>
 *   <li>source order tie-break（同 specificity 后写者胜）</li>
 *   <li>specificity 胜出（id &gt; class &gt; tag）</li>
 *   <li>!important 胜出（即使 specificity 较低）</li>
 *   <li>继承：color/font-family/font-size 等继承属性未设置时取父值</li>
 *   <li>非继承：background-color 等未设置时不取父值</li>
 *   <li>var() 解析：使用本元素 cascade 后的自定义变量映射</li>
 *   <li>var() 跨 :root 继承：根节点声明的 --var 对所有后代可见</li>
 *   <li>自定义变量 cascade：子元素覆盖父元素的 --var</li>
 * </ul>
 */
public class StyleResolverTest {

    private static ElementNode el(String tag, String id, String... classes) {
        ElementNode e = ElementNode.create(tag);
        if (id != null) e.withId(id);
        if (classes.length > 0) {
            e.withClasses(java.util.Arrays.asList(classes));
        }
        return e;
    }

    private static ElementNode el(String tag) {
        return el(tag, null);
    }

    private static Map<ElementNode, ComputedStyle> resolve(Stylesheet ss, ElementNode root) {
        return new StyleResolver().resolve(ss, root);
    }

    private static Stylesheet parse(String css) {
        return new CssParser().parse(css);
    }

    // ---- 基础 cascade ----

    @Test
    public void singleRuleAppliesToMatchingElement() {
        Stylesheet ss = parse("div { color: red; }");
        ElementNode root = el("div");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("red", styles.get(root).get("color"));
    }

    @Test
    public void sourceOrderTieBreakOnEqualSpecificity() {
        // 同 specificity (.a vs .b 都是 0,1,0)，后写者胜
        Stylesheet ss = parse(".a { color: red; } .b { color: blue; }");
        ElementNode root = el("div", null, "a", "b");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("blue", styles.get(root).get("color"));
    }

    @Test
    public void higherSpecificityWinsOverSourceOrder() {
        // #app (1,0,0) 应胜过 .a (0,1,0)，即使 .a 在后面
        Stylesheet ss = parse("#app { color: red; } .a { color: blue; }");
        ElementNode root = el("div", "app", "a");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("red", styles.get(root).get("color"));
    }

    @Test
    public void importantBeatsHigherSpecificity() {
        // .a !important 应胜过 #app（即使 #app specificity 更高）
        Stylesheet ss = parse("#app { color: red; } .a { color: blue !important; }");
        ElementNode root = el("div", "app", "a");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("blue", styles.get(root).get("color"));
    }

    @Test
    public void importantVsImportantUsesSpecificity() {
        // 两个 !important，按 specificity 决胜
        Stylesheet ss = parse("#app { color: red !important; } .a { color: blue !important; }");
        ElementNode root = el("div", "app", "a");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("red", styles.get(root).get("color"));
    }

    @Test
    public void multipleDeclarationsInOneRuleAllApply() {
        Stylesheet ss = parse("div { color: red; background-color: blue; font-size: 14px; }");
        ElementNode root = el("div");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        ComputedStyle s = styles.get(root);
        assertEquals("red", s.get("color"));
        assertEquals("blue", s.get("background-color"));
        assertEquals("14px", s.get("font-size"));
    }

    @Test
    public void nonMatchingRuleDoesNotApply() {
        Stylesheet ss = parse("span { color: red; } div { color: blue; }");
        ElementNode root = el("div");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("blue", styles.get(root).get("color"));
    }

    @Test
    public void multipleSelectorsInOneRuleApplyToEachMatch() {
        Stylesheet ss = parse("div, span, p { color: red; }");
        ElementNode root = el("div");
        ElementNode span = el("span");
        ElementNode p = el("p");
        root.appendChild(span);
        span.appendChild(p);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("red", styles.get(root).get("color"));
        assertEquals("red", styles.get(span).get("color"));
        assertEquals("red", styles.get(p).get("color"));
    }

    @Test
    public void multiSelectorRulePicksHighestSpecificityAcrossMatchingSelectors() {
        // 同一规则的 selector list 中，元素同时命中多个 selector 时，
        // 必须取所有命中 selector 中最高的 specificity，不能在首个匹配项 break。
        // .a (0,1,0) 与 #app (1,0,0) 同属规则 1；元素同时命中两者，
        // 规则 1 的 specificity 应取 (1,0,0)，胜过规则 2 .a 的 (0,1,0)，最终 red。
        Stylesheet ss = parse(
                ".a, #app { color: red; }" +
                ".a { color: blue; }");
        ElementNode root = el("div", "app", "a");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("red", styles.get(root).get("color"));
    }

    // ---- 继承 ----

    @Test
    public void inheritedPropertyFallsBackToParent() {
        // color 是继承属性；父设置 color:red，子未设置则继承 red
        Stylesheet ss = parse(".parent { color: red; }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("red", styles.get(child).get("color"));
    }

    @Test
    public void nonInheritedPropertyDoesNotFallBackToParent() {
        // background-color 非继承属性；父设置后子未设置应为 null
        Stylesheet ss = parse(".parent { background-color: red; }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertNull(styles.get(child).get("background-color"));
    }

    @Test
    public void childOverrideInheritedProperty() {
        // 子显式设置 color 应覆盖继承值
        Stylesheet ss = parse(".parent { color: red; } .child { color: blue; }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("blue", styles.get(child).get("color"));
    }

    @Test
    public void fontFamilyInherits() {
        Stylesheet ss = parse(".parent { font-family: Inter; }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("Inter", styles.get(child).get("font-family"));
    }

    @Test
    public void fontSizeInherits() {
        Stylesheet ss = parse(".parent { font-size: 14px; }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("14px", styles.get(child).get("font-size"));
    }

    // ---- var() 解析 ----

    @Test
    public void varResolvesUsingElementOwnCustomProperty() {
        // 元素自身声明的 --accent 在 var() 中被解析
        Stylesheet ss = parse(".a { --accent: #8b5cf6; color: var(--accent); }");
        ElementNode root = el("div", null, "a");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("#8b5cf6", styles.get(root).get("color"));
    }

    @Test
    public void varInheritsFromRootDeclaration() {
        // :root 声明 --accent，所有后代 var(--accent) 都能解析
        Stylesheet ss = parse(":root { --accent: #8b5cf6; } .child { color: var(--accent); }");
        ElementNode root = el("ui");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("#8b5cf6", styles.get(child).get("color"));
    }

    @Test
    public void varWithFallbackWhenUndefined() {
        Stylesheet ss = parse(".a { color: var(--missing, #fff); }");
        ElementNode root = el("div", null, "a");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("#fff", styles.get(root).get("color"));
    }

    @Test
    public void childOverridesInheritedCustomProperty() {
        // 父声明 --accent: red，子声明 --accent: blue，子的 var(--accent) 应为 blue
        Stylesheet ss = parse(
                ".parent { --accent: red; }" +
                ".child { --accent: blue; color: var(--accent); }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("blue", styles.get(child).get("color"));
    }

    @Test
    public void customPropertyInheritsToDescendants() {
        // 父声明 --accent，子未声明但 var(--accent) 仍可解析（自定义变量是继承属性）
        Stylesheet ss = parse(
                ".parent { --accent: green; }" +
                ".child { color: var(--accent); }");
        ElementNode root = el("div", null, "parent");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("green", styles.get(child).get("color"));
    }

    @Test
    public void unresolvedVarReturnsNullProperty() {
        // var(--missing) 无 fallback 且变量未定义，对应属性值为 null
        Stylesheet ss = parse(".a { color: var(--missing); }");
        ElementNode root = el("div", null, "a");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertNull(styles.get(root).get("color"));
    }

    @Test
    public void varEmbeddedInLargerValue() {
        Stylesheet ss = parse(
                ":root { --c: #ccc; }" +
                ".a { border: 1px solid var(--c); }");
        ElementNode root = el("ui");
        ElementNode a = el("div", null, "a");
        root.appendChild(a);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("1px solid #ccc", styles.get(a).get("border"));
    }

    // ---- 根节点 + 复合场景 ----

    @Test
    public void rootPseudoClassOnlyMatchesRootElement() {
        Stylesheet ss = parse(":root { --accent: #fff; } div { color: var(--accent); }");
        ElementNode root = el("ui");
        ElementNode child = el("div");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        // 子 div 能解析 :root 上声明的 --accent
        assertEquals("#fff", styles.get(child).get("color"));
    }

    @Test
    public void descendantSelectorApplies() {
        Stylesheet ss = parse(".window .category { color: red; }");
        ElementNode root = el("div", null, "window");
        ElementNode sidebar = el("aside");
        ElementNode category = el("div", null, "category");
        root.appendChild(sidebar);
        sidebar.appendChild(category);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("red", styles.get(category).get("color"));
    }

    @Test
    public void allElementsGetComputedStyle() {
        Stylesheet ss = parse("div { color: red; }");
        ElementNode root = el("div");
        ElementNode child = el("div");
        ElementNode grandchild = el("div");
        root.appendChild(child);
        child.appendChild(grandchild);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        // 三个元素都应在结果中
        assertEquals(3, styles.size());
        assertEquals("red", styles.get(root).get("color"));
        assertEquals("red", styles.get(child).get("color"));
        assertEquals("red", styles.get(grandchild).get("color"));
    }

    // ---- 内联 style 参与 cascade ----

    /**
     * 辅助：构造带 inline style 的元素。
     */
    private static ElementNode elInline(String tag, String inlineStyle) {
        ElementNode e = ElementNode.create(tag);
        e.withInlineStyle(inlineStyle);
        return e;
    }

    @Test
    public void inlineNormalBeatsStylesheetNormal() {
        // 内联普通声明优先级高于 stylesheet 普通声明（即使 stylesheet 用 id 选择器）
        Stylesheet ss = parse("#app { color: red; }");
        ElementNode root = elInline("div", "color: green").withId("app");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("green", styles.get(root).get("color"));
    }

    @Test
    public void stylesheetImportantBeatsInlineNormal() {
        // stylesheet !important 优先级高于内联普通声明
        Stylesheet ss = parse("div { color: red !important; }");
        ElementNode root = elInline("div", "color: green");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("red", styles.get(root).get("color"));
    }

    @Test
    public void inlineImportantBeatsStylesheetImportant() {
        // 内联 !important 优先级高于 stylesheet !important（即使 stylesheet 用 id 选择器）
        Stylesheet ss = parse("#app { color: red !important; }");
        ElementNode root = elInline("div", "color: green !important").withId("app");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("green", styles.get(root).get("color"));
    }

    @Test
    public void inlineCustomPropertyUsedByVarInSameElement() {
        // 内联声明的自定义变量可被同元素的 var() 引用
        Stylesheet ss = parse("div { color: var(--accent); }");
        ElementNode root = elInline("div", "--accent: #abc");
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("#abc", styles.get(root).get("color"));
    }

    @Test
    public void inlineStyleInheritsCustomPropertyToChildren() {
        // 父元素内联声明的 --accent 应被子元素 var() 继承使用
        Stylesheet ss = parse(".child { color: var(--accent); }");
        ElementNode root = elInline("div", "--accent: #def");
        ElementNode child = el("div", null, "child");
        root.appendChild(child);
        Map<ElementNode, ComputedStyle> styles = resolve(ss, root);
        assertEquals("#def", styles.get(child).get("color"));
    }
}
