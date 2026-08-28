package gq.yozakura.ui.engine.dom;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 阶段 1 切片 1：DOM 节点模型契约测试。
 *
 * <p>定义 DOM 树的内存形状：{@link DomNode} 抽象基类、{@link ElementNode} 元素节点、
 * {@link TextNode} 文本节点、{@link AttributeMap} 不可变属性集合。
 *
 * <p>节点本身可变（树结构、交互状态），但 {@link AttributeMap} 必须不可变，
 * 以便解析阶段产出的属性可安全共享与比较。
 */
public class DomNodeModelTest {

    @Test
    public void elementNodeHoldsTagIdClassesAndChildren() {
        ElementNode node = ElementNode.create("div")
                .withId("app")
                .withClasses(Arrays.asList("window", "active"))
                .appendChild(TextNode.of("hello"));

        assertEquals("div", node.tag());
        assertEquals("app", node.id());
        assertEquals(Arrays.asList("window", "active"), node.classes());
        assertEquals(1, node.childCount());
        assertTrue(node.child(0) instanceof TextNode);
        assertEquals("hello", ((TextNode) node.child(0)).text());
    }

    @Test
    public void elementNodeParentLinksAreMaintainedOnAppend() {
        ElementNode parent = ElementNode.create("div");
        ElementNode child = ElementNode.create("span");

        parent.appendChild(child);

        assertSame(parent, child.parent());
        assertSame(child, parent.child(0));
    }

    @Test
    public void appendingAlreadyAttachedNodeReparentsItFromPreviousTree() {
        ElementNode first = ElementNode.create("div");
        ElementNode second = ElementNode.create("main");
        ElementNode child = ElementNode.create("span");

        first.appendChild(child);
        second.appendChild(child);

        assertEquals(0, first.childCount());
        assertEquals(1, second.childCount());
        assertSame(second, child.parent());
    }

    @Test
    public void textNodeHasNoChildrenAndIsLeaf() {
        TextNode text = TextNode.of("abc");
        assertFalse(text.hasChildren());
        assertEquals(0, text.childCount());
    }

    @Test
    public void attributeMapIsImmutableAndGetReturnsNullForMissing() {
        AttributeMap attrs = AttributeMap.builder()
                .set("type", "text")
                .set("data-bind", "module.name")
                .build();

        assertEquals("text", attrs.get("type"));
        assertEquals("module.name", attrs.get("data-bind"));
        assertNull(attrs.get("missing"));
    }

    @Test
    public void attributeMapBuilderDoesNotMutateBuiltInstance() {
        AttributeMap.AttributeMapBuilder builder = AttributeMap.builder()
                .set("a", "1");
        AttributeMap first = builder.build();
        AttributeMap second = builder.set("b", "2").build();

        assertNull(first.get("b"));
        assertEquals("2", second.get("b"));
    }

    @Test
    public void elementNodeAttributesAreRetrievedViaAttributeMap() {
        ElementNode node = ElementNode.create("input")
                .withAttributes(AttributeMap.builder()
                        .set("type", "checkbox")
                        .set("data-module", "KillAura")
                        .build());

        assertEquals("checkbox", node.attribute("type"));
        assertEquals("KillAura", node.attribute("data-module"));
        assertNull(node.attribute("missing"));
    }

    @Test
    public void classesAreDeduplicatedAndOrderPreserved() {
        ElementNode node = ElementNode.create("div")
                .withClasses(Arrays.asList("a", "b", "a", "c", "b"));

        assertEquals(Arrays.asList("a", "b", "c"), node.classes());
    }

    @Test
    public void hasClassChecksMembershipCaseSensitively() {
        ElementNode node = ElementNode.create("div")
                .withClasses(Arrays.asList("Window", "active"));

        assertTrue(node.hasClass("Window"));
        assertFalse(node.hasClass("window"));
        assertFalse(node.hasClass("missing"));
    }

    @Test
    public void emptyElementHasNoIdEmptyClassesAndNoChildren() {
        ElementNode node = ElementNode.create("div");

        assertNull(node.id());
        assertTrue(node.classes().isEmpty());
        assertEquals(0, node.childCount());
        assertFalse(node.hasChildren());
    }

    @Test
    public void removingChildBreaksParentLink() {
        ElementNode parent = ElementNode.create("div");
        ElementNode child = ElementNode.create("span");
        parent.appendChild(child);

        parent.removeChild(child);

        assertEquals(0, parent.childCount());
        assertNull(child.parent());
    }

    @Test
    public void removingUnattachedChildIsNoOp() {
        ElementNode parent = ElementNode.create("div");
        ElementNode orphan = ElementNode.create("span");

        parent.removeChild(orphan);

        assertEquals(0, parent.childCount());
    }

    @Test
    public void childrenViewIsUnmodifiable() {
        ElementNode parent = ElementNode.create("div");
        parent.appendChild(ElementNode.create("span"));

        List<DomNode> children = parent.children();
        try {
            children.add(TextNode.of("x"));
            fail("children view must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void inlineStyleIsStoredAsRawText() {
        ElementNode node = ElementNode.create("div")
                .withInlineStyle("color: red; padding: 8px;");

        assertEquals("color: red; padding: 8px;", node.inlineStyle());
    }

    @Test
    public void sourcePositionRecordsOriginForErrorReporting() {
        ElementNode node = ElementNode.create("div", SourcePosition.of(12, 5));

        assertEquals(12, node.sourcePosition().line());
        assertEquals(5, node.sourcePosition().column());
    }

    @Test
    public void sourcePositionDefaultsToUnknownWhenNotProvided() {
        ElementNode node = ElementNode.create("div");
        assertTrue(node.sourcePosition().isUnknown());
    }

    @Test
    public void textNodeCarriesSourcePosition() {
        TextNode text = TextNode.of("abc", SourcePosition.of(3, 1));
        assertEquals(3, text.sourcePosition().line());
        assertEquals(1, text.sourcePosition().column());
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            fail("expected same instance " + expected + " but got " + actual);
        }
    }
}
