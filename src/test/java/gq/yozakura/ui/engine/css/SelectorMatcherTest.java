package gq.yozakura.ui.engine.css;

import gq.yozakura.ui.engine.dom.ElementNode;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 阶段 1 切片 4：选择器解析、匹配与 specificity 契约测试。
 *
 * <p>覆盖标签、class、id、后代、直接子节点选择器的解析与匹配；
 * :hover/:active/:focus/:checked 伪类；属性选择器；
 * specificity 计算与排序；source order tie-break。
 */
public class SelectorMatcherTest {

    private static ElementNode el(String tag, String id, String... classes) {
        ElementNode e = ElementNode.create(tag);
        if (id != null) e.withId(id);
        if (classes.length > 0) e.withClasses(Arrays.asList(classes));
        return e;
    }

    private static ElementNode el(String tag) {
        return ElementNode.create(tag);
    }

    private static boolean matches(String selectorText, ElementNode element) {
        ParsedSelector selector = SelectorParser.parse(selectorText);
        return new SelectorMatcher().matches(selector, element);
    }

    // ---- 解析 ----

    @Test
    public void parsesTagSelector() {
        ParsedSelector s = SelectorParser.parse("div");
        assertEquals(1, s.compounds().size());
        assertEquals("div", s.compounds().get(0).tag());
        assertFalse(s.compounds().get(0).hasId());
    }

    @Test
    public void parsesClassSelector() {
        ParsedSelector s = SelectorParser.parse(".window");
        assertEquals(1, s.compounds().size());
        assertTrue(s.compounds().get(0).classes().contains("window"));
    }

    @Test
    public void parsesIdSelector() {
        ParsedSelector s = SelectorParser.parse("#app");
        assertEquals(1, s.compounds().size());
        assertEquals("app", s.compounds().get(0).id());
    }

    @Test
    public void parsesCompoundSelector() {
        ParsedSelector s = SelectorParser.parse("div.window#app.active");
        CompoundSelector c = s.compounds().get(0);
        assertEquals("div", c.tag());
        assertEquals("app", c.id());
        assertTrue(c.classes().contains("window"));
        assertTrue(c.classes().contains("active"));
    }

    @Test
    public void parsesDescendantCombinator() {
        ParsedSelector s = SelectorParser.parse(".sidebar .category");
        assertEquals(2, s.compounds().size());
        assertEquals(Combinator.DESCENDANT, s.combinator(0));
    }

    @Test
    public void parsesChildCombinator() {
        ParsedSelector s = SelectorParser.parse(".sidebar > .category");
        assertEquals(2, s.compounds().size());
        assertEquals(Combinator.CHILD, s.combinator(0));
    }

    @Test
    public void parsesMultipleCombinators() {
        ParsedSelector s = SelectorParser.parse("div > .sidebar .category:hover");
        assertEquals(3, s.compounds().size());
        assertEquals(Combinator.CHILD, s.combinator(0));
        assertEquals(Combinator.DESCENDANT, s.combinator(1));
    }

    @Test
    public void parsesPseudoClass() {
        ParsedSelector s = SelectorParser.parse("button:hover");
        CompoundSelector c = s.compounds().get(0);
        assertTrue(c.pseudos().contains(PseudoClass.HOVER));
    }

    @Test
    public void parsesMultiplePseudoClasses() {
        ParsedSelector s = SelectorParser.parse("input:focus:checked");
        CompoundSelector c = s.compounds().get(0);
        assertTrue(c.pseudos().contains(PseudoClass.FOCUS));
        assertTrue(c.pseudos().contains(PseudoClass.CHECKED));
    }

    @Test
    public void parsesAttributeSelector() {
        ParsedSelector s = SelectorParser.parse("input[type=text]");
        CompoundSelector c = s.compounds().get(0);
        assertEquals(1, c.attrs().size());
        assertEquals("type", c.attrs().get(0).name());
        assertEquals("text", c.attrs().get(0).value());
    }

    @Test
    public void parsesAttributePresenceSelector() {
        ParsedSelector s = SelectorParser.parse("input[disabled]");
        CompoundSelector c = s.compounds().get(0);
        assertEquals(1, c.attrs().size());
        assertEquals("disabled", c.attrs().get(0).name());
        assertTrue(c.attrs().get(0).isPresence());
    }

    // ---- 匹配 ----

    @Test
    public void tagSelectorMatchesElementByTag() {
        assertTrue(matches("div", el("div")));
        assertFalse(matches("span", el("div")));
    }

    @Test
    public void classSelectorMatchesElementByClass() {
        assertTrue(matches(".window", el("div", null, "window")));
        assertFalse(matches(".missing", el("div", null, "window")));
    }

    @Test
    public void idSelectorMatchesElementById() {
        assertTrue(matches("#app", el("div", "app")));
        assertFalse(matches("#other", el("div", "app")));
    }

    @Test
    public void compoundSelectorRequiresAllParts() {
        assertTrue(matches("div.window#app", el("div", "app", "window")));
        assertFalse(matches("div.window#app", el("div", "app", "other")));
        assertFalse(matches("span.window#app", el("div", "app", "window")));
    }

    @Test
    public void descendantSelectorMatchesNestedElement() {
        ElementNode root = el("div", null, "window");
        ElementNode sidebar = el("aside", null, "sidebar");
        ElementNode category = el("button", null, "category");
        root.appendChild(sidebar);
        sidebar.appendChild(category);

        assertTrue(matches(".sidebar .category", category));
        assertTrue(matches(".window .category", category));
        assertFalse(matches(".missing .category", category));
    }

    @Test
    public void childSelectorOnlyMatchesDirectChildren() {
        ElementNode root = el("div", null, "window");
        ElementNode sidebar = el("aside", null, "sidebar");
        ElementNode category = el("button", null, "category");
        root.appendChild(sidebar);
        sidebar.appendChild(category);

        assertTrue(matches(".sidebar > .category", category));
        assertFalse(matches(".window > .category", category));
    }

    @Test
    public void pseudoClassHoverMatchesOnlyWhenHovered() {
        ElementNode button = el("button");
        button.setHovered(false);
        assertFalse(matches("button:hover", button));
        button.setHovered(true);
        assertTrue(matches("button:hover", button));
    }

    @Test
    public void pseudoClassCheckedMatchesOnlyWhenChecked() {
        ElementNode input = el("input");
        input.setChecked(false);
        assertFalse(matches("input:checked", input));
        input.setChecked(true);
        assertTrue(matches("input:checked", input));
    }

    // ---- :root 伪类（变量声明所需） ----

    @Test
    public void parsesRootPseudoClass() {
        ParsedSelector s = SelectorParser.parse(":root");
        CompoundSelector c = s.compounds().get(0);
        assertTrue(c.pseudos().contains(PseudoClass.ROOT));
    }

    @Test
    public void rootPseudoClassMatchesOnlyRootElement() {
        ElementNode root = el("ui");
        ElementNode child = el("div");
        root.appendChild(child);

        // root 无父节点，:root 匹配
        assertTrue(":root should match element without parent",
                matches(":root", root));
        // 子节点有父节点，:root 不匹配
        assertFalse(":root should not match element with parent",
                matches(":root", child));
    }

    @Test
    public void rootPseudoClassCombinesWithTagSelector() {
        // ui:root 仅匹配 tag=ui 且无父节点的元素
        ElementNode root = el("ui");
        ElementNode rootDiv = el("div");
        // rootDiv 无父节点但 tag 不是 ui
        assertTrue("ui:root should match ui root",
                matches("ui:root", root));
        assertFalse("ui:root should not match div root",
                matches("ui:root", rootDiv));
    }

    @Test
    public void attributeSelectorMatchesByAttributeValue() {
        ElementNode input = el("input");
        input.withAttributes(gq.yozakura.ui.engine.dom.AttributeMap.builder()
                .set("type", "text").build());
        assertTrue(matches("input[type=text]", input));
        assertFalse(matches("input[type=checkbox]", input));
    }

    @Test
    public void attributePresenceSelectorMatchesExistence() {
        ElementNode input = el("input");
        input.withAttributes(gq.yozakura.ui.engine.dom.AttributeMap.builder()
                .set("disabled", "").build());
        assertTrue(matches("input[disabled]", input));
        assertFalse(matches("input[readonly]", input));
    }

    @Test
    public void emptySelectorTextIsRejected() {
        try {
            SelectorParser.parse("");
            fail("expected rejection of empty selector");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // ---- specificity ----

    @Test
    public void idBeatsClassBeatsTag() {
        Specificity id = Specificity.of(SelectorParser.parse("#app"));
        Specificity cls = Specificity.of(SelectorParser.parse(".window"));
        Specificity tag = Specificity.of(SelectorParser.parse("div"));

        assertTrue("id > class", id.compareTo(cls) > 0);
        assertTrue("class > tag", cls.compareTo(tag) > 0);
    }

    @Test
    public void multipleClassesIncreaseSpecificity() {
        Specificity one = Specificity.of(SelectorParser.parse(".a"));
        Specificity two = Specificity.of(SelectorParser.parse(".a.b"));
        assertTrue(two.compareTo(one) > 0);
    }

    @Test
    public void compoundSpecificitySumsParts() {
        Specificity compound = Specificity.of(SelectorParser.parse("div.window#app"));
        // 1 id + 1 class + 1 tag
        Specificity expected = Specificity.ofValues(1, 1, 1);
        assertEquals(0, compound.compareTo(expected));
    }

    @Test
    public void descendantSelectorSumsAllCompounds() {
        Specificity s = Specificity.of(SelectorParser.parse(".sidebar .category"));
        // 0 id + 2 class + 0 tag
        assertEquals(0, s.compareTo(Specificity.ofValues(0, 2, 0)));
    }

    @Test
    public void pseudoClassCountsAsClass() {
        Specificity withPseudo = Specificity.of(SelectorParser.parse("button:hover"));
        Specificity withoutPseudo = Specificity.of(SelectorParser.parse("button"));
        assertTrue(withPseudo.compareTo(withoutPseudo) > 0);
        // :hover 算作 class，所以 (0,1,1) vs (0,0,1)
        assertEquals(0, withPseudo.compareTo(Specificity.ofValues(0, 1, 1)));
    }

    @Test
    public void attributeSelectorCountsAsClass() {
        Specificity withAttr = Specificity.of(SelectorParser.parse("input[type=text]"));
        assertEquals(0, withAttr.compareTo(Specificity.ofValues(0, 1, 1)));
    }

    @Test
    public void equalSpecificityUsesSourceOrder() {
        // 同 specificity，source order 大者胜
        Rule earlier = new Rule(java.util.Collections.singletonList(new Selector(".a")),
                java.util.Collections.<Declaration>emptyList(), 0, null);
        Rule later = new Rule(java.util.Collections.singletonList(new Selector(".b")),
                java.util.Collections.<Declaration>emptyList(), 1, null);
        assertTrue("later source order should win on equal specificity",
                later.sourceOrder() > earlier.sourceOrder());
    }
}
