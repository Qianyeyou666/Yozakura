package gq.yozakura.ui.engine.css;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 阶段 1 切片 6：ComputedStyle 值对象契约测试。
 *
 * <p>ComputedStyle 保存元素经过 cascade + 继承 + var() 解析后的最终属性值。
 * 不可变值对象：构造完成后内部映射不可被外部修改。
 *
 * <p>此处只测试值对象语义；cascade/继承/var 解析由 {@link StyleResolverTest} 覆盖。
 */
public class ComputedStyleTest {

    @Test
    public void getReturnsPropertyValue() {
        ComputedStyle s = ComputedStyle.builder()
                .set("color", "#fff")
                .set("font-size", "14px")
                .build();
        assertEquals("#fff", s.get("color"));
        assertEquals("14px", s.get("font-size"));
    }

    @Test
    public void getReturnsNullForMissingProperty() {
        ComputedStyle s = ComputedStyle.builder()
                .set("color", "#fff")
                .build();
        assertNull(s.get("background-color"));
        assertFalse(s.has("background-color"));
        assertTrue(s.has("color"));
    }

    @Test
    public void propertyNamesReturnsAllKeys() {
        ComputedStyle s = ComputedStyle.builder()
                .set("color", "#fff")
                .set("font-size", "14px")
                .build();
        assertEquals(2, s.propertyNames().size());
        assertTrue(s.propertyNames().contains("color"));
        assertTrue(s.propertyNames().contains("font-size"));
    }

    @Test
    public void customPropertiesAccessibleSeparately() {
        // --accent 是自定义变量；color 是普通属性
        ComputedStyle s = ComputedStyle.builder()
                .set("color", "#fff")
                .setCustom("--accent", "#8b5cf6")
                .setCustom("--radius", "4px")
                .build();
        // 普通属性查询不包含 --vars
        assertEquals("#fff", s.get("color"));
        assertNull(s.get("--accent"));
        // 自定义变量通过 customProperty 查询
        assertEquals("#8b5cf6", s.customProperty("--accent"));
        assertEquals("4px", s.customProperty("--radius"));
        assertNull(s.customProperty("--missing"));
    }

    @Test
    public void customPropertyNamesReturnsAllVarKeys() {
        ComputedStyle s = ComputedStyle.builder()
                .setCustom("--accent", "#8b5cf6")
                .setCustom("--radius", "4px")
                .build();
        assertEquals(2, s.customPropertyNames().size());
        assertTrue(s.customPropertyNames().contains("--accent"));
    }

    @Test
    public void builderCanInheritFromParent() {
        // 父 ComputedStyle 已有 color 和 --accent
        ComputedStyle parent = ComputedStyle.builder()
                .set("color", "red")
                .set("font-size", "12px")
                .setCustom("--accent", "#parent")
                .build();
        // 子 builder 继承父的全部属性（深拷贝），可覆盖
        ComputedStyle child = ComputedStyle.builder()
                .inheritFrom(parent)
                .set("color", "blue")
                .setCustom("--radius", "4px")
                .build();
        // 子覆盖了 color
        assertEquals("blue", child.get("color"));
        // 子继承未覆盖的 font-size
        assertEquals("12px", child.get("font-size"));
        // 子继承父的 --accent
        assertEquals("#parent", child.customProperty("--accent"));
        // 子新增 --radius
        assertEquals("4px", child.customProperty("--radius"));
        // 父不应被修改
        assertEquals("red", parent.get("color"));
        assertFalse(parent.has("--radius"));
    }

    @Test
    public void emptyBuilderProducesEmptyStyle() {
        ComputedStyle s = ComputedStyle.builder().build();
        assertTrue(s.propertyNames().isEmpty());
        assertTrue(s.customPropertyNames().isEmpty());
        assertNull(s.get("color"));
    }

    @Test
    public void styleIsImmutable() {
        ComputedStyle s = ComputedStyle.builder()
                .set("color", "#fff")
                .build();
        // 返回的集合不可变
        try {
            s.propertyNames().add("background");
            fail("propertyNames should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        try {
            s.customPropertyNames().add("--x");
            fail("customPropertyNames should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void customPropertiesViewIsUnmodifiable() {
        ComputedStyle s = ComputedStyle.builder()
                .setCustom("--accent", "#fff")
                .build();
        Map<String, String> view = s.customProperties();
        try {
            view.put("--x", "y");
            fail("customProperties view should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}
