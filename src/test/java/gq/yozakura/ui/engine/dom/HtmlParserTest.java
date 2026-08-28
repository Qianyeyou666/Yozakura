package gq.yozakura.ui.engine.dom;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 阶段 1 切片 2：HTML 解析器契约测试。
 *
 * <p>覆盖支持的标签子集（ui、div、span、p、button、input、img、label、template），
 * 属性解析（id、class、style、data-星号、type、src）、嵌套、自闭合标签、
 * 文本节点、实体解码，以及带行列号的语法错误。
 */
public class HtmlParserTest {

    private static ElementNode parseRoot(String html) {
        DomNode root = new HtmlParser().parse(html);
        assertTrue("root should be ElementNode, got " + (root == null ? "null" : root.getClass()),
                root instanceof ElementNode);
        return (ElementNode) root;
    }

    private static ElementNode firstElementChild(ElementNode parent) {
        for (int i = 0; i < parent.childCount(); i++) {
            if (parent.child(i) instanceof ElementNode) {
                return (ElementNode) parent.child(i);
            }
        }
        throw new AssertionError("no element child found in " + parent);
    }

    @Test
    public void parsesSingleDivWithText() {
        ElementNode root = parseRoot("<div>hello</div>");

        assertEquals("div", root.tag());
        assertEquals(1, root.childCount());
        assertTrue(root.child(0) instanceof TextNode);
        assertEquals("hello", ((TextNode) root.child(0)).text());
    }

    @Test
    public void parsesNestedElements() {
        ElementNode root = parseRoot("<div><span>ab</span><p>cd</p></div>");

        assertEquals("div", root.tag());
        assertEquals(2, root.childCount());
        assertEquals("span", ((ElementNode) root.child(0)).tag());
        assertEquals("p", ((ElementNode) root.child(1)).tag());
        assertEquals("ab", ((TextNode) ((ElementNode) root.child(0)).child(0)).text());
        assertEquals("cd", ((TextNode) ((ElementNode) root.child(1)).child(0)).text());
    }

    @Test
    public void parsesIdClassAndDataAttributes() {
        ElementNode root = parseRoot("<button id=\"save\" class=\"btn primary\" data-module=\"KillAura\">Save</button>");

        assertEquals("save", root.id());
        assertTrue(root.hasClass("btn"));
        assertTrue(root.hasClass("primary"));
        assertEquals("KillAura", root.attribute("data-module"));
        assertEquals("Save", ((TextNode) root.child(0)).text());
    }

    @Test
    public void parsesInlineStyleAttribute() {
        ElementNode root = parseRoot("<div style=\"color: red; padding: 8px;\">x</div>");

        assertEquals("color: red; padding: 8px;", root.inlineStyle());
    }

    @Test
    public void selfClosingImgTagHasNoChildren() {
        ElementNode root = parseRoot("<img src=\"/icons/a.png\" />");

        assertEquals("img", root.tag());
        assertEquals("/icons/a.png", root.attribute("src"));
        assertEquals(0, root.childCount());
    }

    @Test
    public void selfClosingInputCheckbox() {
        ElementNode root = parseRoot("<input type=\"checkbox\" />");

        assertEquals("input", root.tag());
        assertEquals("checkbox", root.attribute("type"));
        assertEquals(0, root.childCount());
    }

    @Test
    public void voidTagsDoNotRequireClosingTag() {
        ElementNode root = parseRoot("<div><img src=\"a.png\"><br></div>");

        assertEquals(2, root.childCount());
        assertEquals("img", ((ElementNode) root.child(0)).tag());
    }

    @Test
    public void multipleClassesInOneAttribute() {
        ElementNode root = parseRoot("<div class=\"a b c\">x</div>");

        assertTrue(root.hasClass("a"));
        assertTrue(root.hasClass("b"));
        assertTrue(root.hasClass("c"));
    }

    @Test
    public void textNodesArePreservedBetweenElements() {
        ElementNode root = parseRoot("<div>before<span>mid</span>after</div>");

        assertEquals(3, root.childCount());
        assertEquals("before", ((TextNode) root.child(0)).text());
        assertEquals("after", ((TextNode) root.child(2)).text());
    }

    @Test
    public void entitiesAreDecodedInText() {
        ElementNode root = parseRoot("<p>a &amp; b &lt; c &gt; d &quot; e &apos; f</p>");

        assertEquals("a & b < c > d \" e ' f", ((TextNode) root.child(0)).text());
    }

    @Test
    public void nbspEntityDecodedToNonBreakingSpace() {
        ElementNode root = parseRoot("<p>x&nbsp;y</p>");

        assertEquals("x\u00a0y", ((TextNode) root.child(0)).text());
    }

    @Test
    public void numericEntityDecoded() {
        ElementNode root = parseRoot("<p>&#65;&#x42;</p>");

        assertEquals("AB", ((TextNode) root.child(0)).text());
    }

    @Test
    public void commentsAreIgnored() {
        ElementNode root = parseRoot("<div><!-- comment -->x</div>");

        assertEquals(1, root.childCount());
        assertEquals("x", ((TextNode) root.child(0)).text());
    }

    @Test
    public void whitespaceOnlyTextBetweenTagsIsPreservedAsIs() {
        ElementNode root = parseRoot("<div> <span>x</span> </div>");

        assertEquals(3, root.childCount());
        assertEquals(" ", ((TextNode) root.child(0)).text());
        assertEquals(" ", ((TextNode) root.child(2)).text());
    }

    @Test
    public void templateTagIsParsable() {
        ElementNode root = parseRoot("<template data-repeat=\"modules\"><span>card</span></template>");

        assertEquals("template", root.tag());
        assertEquals("modules", root.attribute("data-repeat"));
        assertEquals(1, root.childCount());
    }

    @Test
    public void uiTagIsParsableAsRoot() {
        ElementNode root = parseRoot("<ui><div>x</div></ui>");

        assertEquals("ui", root.tag());
        assertEquals(1, root.childCount());
    }

    @Test
    public void labelTagIsParsable() {
        ElementNode root = parseRoot("<label>Name</label>");

        assertEquals("label", root.tag());
        assertEquals("Name", ((TextNode) root.child(0)).text());
    }

    @Test
    public void attributesWithoutQuotes() {
        ElementNode root = parseRoot("<input type=text checked />");

        assertEquals("text", root.attribute("type"));
        assertEquals("", root.attribute("checked"));
    }

    @Test
    public void booleanAttributeHasEmptyValue() {
        ElementNode root = parseRoot("<input disabled />");

        assertEquals("", root.attribute("disabled"));
    }

    @Test
    public void sourcePositionRecordedForElements() {
        String html = "<div>\n  <span>x</span>\n</div>";
        ElementNode root = parseRoot(html);

        ElementNode span = firstElementChild(root);
        assertEquals(2, span.sourcePosition().line());
    }

    @Test
    public void unclosedTagReportsErrorWithLineAndColumn() {
        try {
            new HtmlParser().parse("<div><span>x</div>");
            fail("expected parse error for unclosed <span>");
        } catch (HtmlParseException e) {
            assertTrue("error message should mention span: " + e.getMessage(),
                    e.getMessage().contains("span"));
            assertTrue("line should be >= 1: " + e.line(), e.line() >= 1);
        }
    }

    @Test
    public void unmatchedClosingTagReportsError() {
        try {
            new HtmlParser().parse("<div></span></div>");
            fail("expected parse error for unmatched </span>");
        } catch (HtmlParseException e) {
            assertTrue(e.getMessage().contains("span"));
            assertTrue(e.line() >= 1);
        }
    }

    @Test
    public void mismatchedClosingTagReportsError() {
        try {
            new HtmlParser().parse("<div><span>x</span></p>");
            fail("expected parse error for mismatched </p>");
        } catch (HtmlParseException e) {
            assertTrue(e.getMessage().contains("p"));
        }
    }

    @Test
    public void invalidTagNameReportsError() {
        try {
            new HtmlParser().parse("<123>bad</123>");
            fail("expected parse error for invalid tag name");
        } catch (HtmlParseException e) {
            assertTrue(e.line() >= 1);
        }
    }

    @Test
    public void emptyInputProducesError() {
        try {
            new HtmlParser().parse("");
            fail("expected parse error for empty input");
        } catch (HtmlParseException e) {
            assertTrue(e.getMessage().isEmpty() == false);
        }
    }

    @Test
    public void whitespaceOnlyInputProducesError() {
        try {
            new HtmlParser().parse("   \n  ");
            fail("expected parse error for whitespace-only input");
        } catch (HtmlParseException e) {
            // expected
        }
    }

    @Test
    public void multipleRootElementsProduceError() {
        try {
            new HtmlParser().parse("<div>a</div><div>b</div>");
            fail("expected parse error for multiple root elements");
        } catch (HtmlParseException e) {
            assertTrue(e.getMessage().contains("root") || e.getMessage().contains("multiple"));
        }
    }

    @Test
    public void deeplyNestedStructure() {
        ElementNode root = parseRoot(
                "<div class=\"window\">" +
                "  <aside class=\"sidebar\">" +
                "    <button class=\"category\">Combat</button>" +
                "  </aside>" +
                "</div>");

        assertEquals("div", root.tag());
        assertTrue(root.hasClass("window"));
        ElementNode aside = firstElementChild(root);
        assertEquals("aside", aside.tag());
        ElementNode button = firstElementChild(aside);
        assertEquals("button", button.tag());
        assertEquals("Combat", ((TextNode) button.child(0)).text());
    }

    @Test
    public void textBeforeRootProducesError() {
        try {
            new HtmlParser().parse("text<div>x</div>");
            fail("expected parse error for text before root");
        } catch (HtmlParseException e) {
            // expected
        }
    }

    // ---- resourcePath 支持 ----

    @Test
    public void parseWithResourcePathReturnsSameTree() {
        // parse(source, resourcePath) 在成功路径上应与 parse(source) 等价
        ElementNode a = parseRoot("<div>x</div>");
        ElementNode b = (ElementNode) new HtmlParser().parse("<div>x</div>", "ui/main.html");
        assertEquals(a.tag(), b.tag());
        assertEquals(((TextNode) a.child(0)).text(), ((TextNode) b.child(0)).text());
    }

    @Test
    public void parseWithResourcePathIncludesPathInException() {
        // 异常必须包含 resourcePath、line、column；resourcePath() 访问器可读
        try {
            new HtmlParser().parse("<div><span>x</div>", "ui/broken.html");
            fail("expected parse error with resourcePath");
        } catch (HtmlParseException e) {
            assertEquals("ui/broken.html", e.resourcePath());
            assertTrue("line should be >= 1: " + e.line(), e.line() >= 1);
            assertTrue("message should mention resource path: " + e.getMessage(),
                    e.getMessage().contains("ui/broken.html"));
        }
    }

    @Test
    public void parseWithResourcePathNullKeepsLegacyContract() {
        // resourcePath 可为 null（未知资源），异常仍带行列
        try {
            new HtmlParser().parse("<div><span>x</div>", null);
            fail("expected parse error");
        } catch (HtmlParseException e) {
            assertNull("resourcePath should be null", e.resourcePath());
            assertTrue(e.line() >= 1);
        }
    }

    @Test
    public void legacyParseOverloadResourcePathIsNull() {
        // 保留简化重载：parse(source) 抛出的异常 resourcePath() 必须为 null（向后兼容）
        try {
            new HtmlParser().parse("<div><span>x</div>");
            fail("expected parse error");
        } catch (HtmlParseException e) {
            assertNull(e.resourcePath());
        }
    }
}
