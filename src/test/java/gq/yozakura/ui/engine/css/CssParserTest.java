package gq.yozakura.ui.engine.css;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 阶段 1 切片 3：CSS 解析器契约测试。
 *
 * <p>覆盖规则、声明、选择器文本、属性值、注释、important、自定义变量声明、
 * at-rule（@media 与 :root 规则）、语法错误带行列号。
 *
 * <p>选择器匹配与 specificity 在切片 4 单独测试；var() 解析在切片 5。
 */
public class CssParserTest {

    private static Stylesheet parse(String css) {
        return new CssParser().parse(css);
    }

    @Test
    public void parsesSingleRuleWithOneDeclaration() {
        Stylesheet ss = parse("div { color: red; }");

        assertEquals(1, ss.ruleCount());
        Rule rule = ss.rule(0);
        assertEquals(1, rule.declarations().size());
        assertEquals("color", rule.declarations().get(0).property().name());
        assertEquals("red", rule.declarations().get(0).value().raw());
    }

    @Test
    public void parsesMultipleDeclarations() {
        Stylesheet ss = parse("div { color: red; background: blue; padding: 8px; }");

        Rule rule = ss.rule(0);
        assertEquals(3, rule.declarations().size());
        assertEquals("color", rule.declarations().get(0).property().name());
        assertEquals("background", rule.declarations().get(1).property().name());
        assertEquals("padding", rule.declarations().get(2).property().name());
    }

    @Test
    public void parsesMultipleRules() {
        Stylesheet ss = parse("div { color: red; } .cls { color: blue; }");

        assertEquals(2, ss.ruleCount());
        assertEquals("div", ss.rule(0).selectorText());
        assertEquals(".cls", ss.rule(1).selectorText());
    }

    @Test
    public void preservesSourceOrder() {
        Stylesheet ss = parse("a { color: red; } b { color: blue; } c { color: green; }");

        assertEquals(0, ss.rule(0).sourceOrder());
        assertEquals(1, ss.rule(1).sourceOrder());
        assertEquals(2, ss.rule(2).sourceOrder());
    }

    @Test
    public void parsesImportantFlag() {
        Stylesheet ss = parse("div { color: red !important; }");

        Declaration decl = ss.rule(0).declarations().get(0);
        assertTrue("declaration should be important", decl.important());
    }

    @Test
    public void nonImportantDeclaration() {
        Stylesheet ss = parse("div { color: red; }");

        Declaration decl = ss.rule(0).declarations().get(0);
        assertFalse(decl.important());
    }

    @Test
    public void parsesCommentAndIgnoresIt() {
        Stylesheet ss = parse("/* comment */ div { color: red; /* inline */ }");

        assertEquals(1, ss.ruleCount());
        assertEquals("div", ss.rule(0).selectorText());
        assertEquals(1, ss.rule(0).declarations().size());
    }

    @Test
    public void parsesCustomPropertyDeclaration() {
        Stylesheet ss = parse(":root { --accent: #e98bc1; }");

        Rule rule = ss.rule(0);
        assertEquals(":root", rule.selectorText());
        Declaration decl = rule.declarations().get(0);
        assertEquals("--accent", decl.property().name());
        assertEquals("#e98bc1", decl.value().raw());
    }

    @Test
    public void parsesVarWithValueFallback() {
        Stylesheet ss = parse("div { color: var(--accent, #fff); }");

        Declaration decl = ss.rule(0).declarations().get(0);
        CssValue value = decl.value();
        assertEquals("var(--accent, #fff)", value.raw());
    }

    @Test
    public void parsesHexColor() {
        Stylesheet ss = parse("div { color: #e98bc1; }");

        CssValue value = ss.rule(0).declarations().get(0).value();
        assertEquals("#e98bc1", value.raw());
    }

    @Test
    public void parsesRgbaColor() {
        Stylesheet ss = parse("div { color: rgba(255, 0, 0, 0.5); }");

        CssValue value = ss.rule(0).declarations().get(0).value();
        assertEquals("rgba(255, 0, 0, 0.5)", value.raw());
    }

    @Test
    public void parsesLengthWithPx() {
        Stylesheet ss = parse("div { width: 100px; }");

        CssValue value = ss.rule(0).declarations().get(0).value();
        assertEquals("100px", value.raw());
    }

    @Test
    public void parsesPercentage() {
        Stylesheet ss = parse("div { width: 50%; }");

        CssValue value = ss.rule(0).declarations().get(0).value();
        assertEquals("50%", value.raw());
    }

    @Test
    public void parsesComplexSelectorText() {
        Stylesheet ss = parse("div.window > .sidebar .category:hover { color: red; }");

        assertEquals("div.window > .sidebar .category:hover", ss.rule(0).selectorText());
    }

    @Test
    public void parsesMultipleSelectorsInOneRule() {
        Stylesheet ss = parse("div, span, p { color: red; }");

        Rule rule = ss.rule(0);
        assertEquals(3, rule.selectors().size());
        assertEquals("div", rule.selectors().get(0).text());
        assertEquals("span", rule.selectors().get(1).text());
        assertEquals("p", rule.selectors().get(2).text());
    }

    @Test
    public void parsesAtMediaRuleAsOpaqueBlock() {
        Stylesheet ss = parse("@media (min-width: 500px) { div { color: red; } }");

        // MVP: @media 作为不透明块保留，内部规则可选解析
        // 这里验证不崩溃且至少保留为一条记录
        assertTrue(ss.ruleCount() >= 1 || ss.atRuleCount() >= 1);
    }

    @Test
    public void trailingSemicolonOptional() {
        Stylesheet ss = parse("div { color: red }");

        assertEquals(1, ss.rule(0).declarations().size());
    }

    @Test
    public void emptyRuleIsPreserved() {
        Stylesheet ss = parse("div { }");

        assertEquals(1, ss.ruleCount());
        assertEquals(0, ss.rule(0).declarations().size());
    }

    @Test
    public void emptyInputProducesEmptyStylesheet() {
        Stylesheet ss = parse("");

        assertEquals(0, ss.ruleCount());
    }

    @Test
    public void whitespaceOnlyInputProducesEmptyStylesheet() {
        Stylesheet ss = parse("   \n  ");

        assertEquals(0, ss.ruleCount());
    }

    @Test
    public void unterminatedRuleReportsError() {
        try {
            parse("div { color: red;");
            fail("expected CSS parse error for unterminated rule");
        } catch (CssParseException e) {
            assertTrue(e.line() >= 1);
            assertTrue(e.getMessage().length() > 0);
        }
    }

    @Test
    public void missingPropertyValueReportsError() {
        try {
            parse("div { color: }");
            fail("expected CSS parse error for missing property value");
        } catch (CssParseException e) {
            assertTrue(e.line() >= 1);
        }
    }

    @Test
    public void invalidPropertyNameReportsError() {
        try {
            parse("div { 123bad: red; }");
            fail("expected CSS parse error for invalid property name");
        } catch (CssParseException e) {
            assertTrue(e.line() >= 1);
        }
    }

    @Test
    public void declarationWithoutColonReportsError() {
        try {
            parse("div { color red; }");
            fail("expected CSS parse error for missing colon");
        } catch (CssParseException e) {
            assertTrue(e.line() >= 1);
        }
    }

    @Test
    public void sourcePositionRecordedForRule() {
        String css = "a { color: red; }\n\nb { color: blue; }";
        Stylesheet ss = parse(css);

        assertEquals(3, ss.rule(1).sourcePosition().line());
    }

    @Test
    public void parsesLinearGradientValue() {
        Stylesheet ss = parse("div { background: linear-gradient(to right, #000, #fff); }");

        CssValue value = ss.rule(0).declarations().get(0).value();
        assertEquals("linear-gradient(to right, #000, #fff)", value.raw());
    }

    @Test
    public void parsesUrlValue() {
        Stylesheet ss = parse("div { background: url(\"/icons/a.png\"); }");

        CssValue value = ss.rule(0).declarations().get(0).value();
        assertEquals("url(\"/icons/a.png\")", value.raw());
    }

    @Test
    public void parsesShorthandBorder() {
        Stylesheet ss = parse("div { border: 1px solid #ccc; }");

        CssValue value = ss.rule(0).declarations().get(0).value();
        assertEquals("1px solid #ccc", value.raw());
    }

    // ---- resourcePath 支持 ----

    @Test
    public void parseWithResourcePathReturnsSameStylesheet() {
        // parse(css, resourcePath) 在成功路径上应与 parse(css) 等价
        Stylesheet a = parse("div { color: red; }");
        Stylesheet b = new CssParser().parse("div { color: red; }", "ui/theme.css");
        assertEquals(a.ruleCount(), b.ruleCount());
        assertEquals(a.rule(0).selectorText(), b.rule(0).selectorText());
    }

    @Test
    public void parseWithResourcePathIncludesPathInException() {
        // 异常必须包含 resourcePath、line、column；resourcePath() 访问器可读
        try {
            new CssParser().parse("div { color: red;", "ui/theme.css");
            fail("expected CSS parse error with resourcePath");
        } catch (CssParseException e) {
            assertEquals("ui/theme.css", e.resourcePath());
            assertTrue("line should be >= 1: " + e.line(), e.line() >= 1);
            assertTrue("message should mention resource path: " + e.getMessage(),
                    e.getMessage().contains("ui/theme.css"));
        }
    }

    @Test
    public void parseWithResourcePathNullKeepsLegacyContract() {
        // resourcePath 可为 null（未知资源），异常仍带行列
        try {
            new CssParser().parse("div { color: red;", null);
            fail("expected CSS parse error");
        } catch (CssParseException e) {
            assertNull("resourcePath should be null", e.resourcePath());
            assertTrue(e.line() >= 1);
        }
    }

    @Test
    public void legacyParseOverloadResourcePathIsNull() {
        // 保留简化重载：parse(css) 抛出的异常 resourcePath() 必须为 null（向后兼容）
        try {
            new CssParser().parse("div { color: red;");
            fail("expected CSS parse error");
        } catch (CssParseException e) {
            assertNull(e.resourcePath());
        }
    }

    // ---- 不可变集合：防御性复制 ----

    @Test
    public void ruleDefensiveCopiesSelectorList() {
        // 构造 Rule 后修改原始 list 不应影响 Rule 内部状态
        java.util.List<Selector> selectors = new java.util.ArrayList<Selector>();
        selectors.add(new Selector("div"));
        java.util.List<Declaration> decls = new java.util.ArrayList<Declaration>();
        decls.add(new Declaration(new Property("color"), new CssValue("red"), false));
        Rule rule = new Rule(selectors, decls, 0, null);

        selectors.add(new Selector("span"));
        assertEquals("modifying original selector list must not affect Rule",
                1, rule.selectors().size());
    }

    @Test
    public void ruleDefensiveCopiesDeclarationList() {
        java.util.List<Selector> selectors = new java.util.ArrayList<Selector>();
        selectors.add(new Selector("div"));
        java.util.List<Declaration> decls = new java.util.ArrayList<Declaration>();
        decls.add(new Declaration(new Property("color"), new CssValue("red"), false));
        Rule rule = new Rule(selectors, decls, 0, null);

        decls.add(new Declaration(new Property("background"), new CssValue("blue"), false));
        assertEquals("modifying original declaration list must not affect Rule",
                1, rule.declarations().size());
    }

    @Test
    public void compoundSelectorDefensiveCopiesClassList() {
        java.util.List<String> classes = new java.util.ArrayList<String>();
        classes.add("a");
        CompoundSelector cs = new CompoundSelector("div", null, classes, null, null);

        classes.add("b");
        assertEquals("modifying original class list must not affect CompoundSelector",
                1, cs.classes().size());
    }

    @Test
    public void compoundSelectorDefensiveCopiesAttrList() {
        java.util.List<AttrSelector> attrs = new java.util.ArrayList<AttrSelector>();
        attrs.add(AttrSelector.equals("data-x", "1"));
        CompoundSelector cs = new CompoundSelector("div", null, null, null, attrs);

        attrs.add(AttrSelector.equals("data-y", "2"));
        assertEquals("modifying original attr list must not affect CompoundSelector",
                1, cs.attrs().size());
    }

    @Test
    public void parsedSelectorDefensiveCopiesCompoundList() {
        java.util.List<CompoundSelector> compounds = new java.util.ArrayList<CompoundSelector>();
        compounds.add(new CompoundSelector("div", null, null, null, null));
        java.util.List<Combinator> combs = new java.util.ArrayList<Combinator>();
        ParsedSelector ps = new ParsedSelector(compounds, combs, "div");

        compounds.add(new CompoundSelector("span", null, null, null, null));
        assertEquals("modifying original compound list must not affect ParsedSelector",
                1, ps.compounds().size());
    }

    @Test
    public void parsedSelectorDefensiveCopiesCombinatorList() {
        java.util.List<CompoundSelector> compounds = new java.util.ArrayList<CompoundSelector>();
        compounds.add(new CompoundSelector("div", null, null, null, null));
        compounds.add(new CompoundSelector("span", null, null, null, null));
        java.util.List<Combinator> combs = new java.util.ArrayList<Combinator>();
        combs.add(Combinator.DESCENDANT);
        ParsedSelector ps = new ParsedSelector(compounds, combs, "div span");

        combs.add(Combinator.CHILD);
        assertEquals("modifying original combinator list must not affect ParsedSelector",
                1, ps.combinatorCount());
    }
}
