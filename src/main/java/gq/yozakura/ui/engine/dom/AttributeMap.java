package gq.yozakura.ui.engine.dom;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>不可变属性集合，存储元素的 HTML 属性（id、class、style、data-*、type、src 等）。
 *
 * <p>不可变值对象：构建后不可修改。{@link AttributeMapBuilder} 每次构建产出独立快照，
 * 后续 builder 修改不影响已构建实例。
 *
 * <p>属性名大小写敏感，保留插入顺序（LinkedHashMap）以便确定性遍历与调试。
 */
public final class AttributeMap {
    private static final AttributeMap EMPTY = new AttributeMap(Collections.<String, String>emptyMap());

    private final Map<String, String> attributes;

    private AttributeMap(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public static AttributeMap empty() {
        return EMPTY;
    }

    public static AttributeMapBuilder builder() {
        return new AttributeMapBuilder();
    }

    /** 返回属性值，缺失返回 null。 */
    public String get(String name) {
        return attributes.get(name);
    }

    /** 是否包含指定属性。 */
    public boolean has(String name) {
        return attributes.containsKey(name);
    }

    /** 属性数量。 */
    public int size() {
        return attributes.size();
    }

    /** 是否为空。 */
    public boolean isEmpty() {
        return attributes.isEmpty();
    }

    /** 不可变属性条目视图，保留插入顺序。 */
    public Iterable<Map.Entry<String, String>> entries() {
        return attributes.entrySet();
    }

    /**
     * 可变构建器，用于解析期累积属性。每次 {@link #build()} 产出独立不可变快照，
     * 之后 builder 继续修改不会影响已构建实例。
     */
    public static final class AttributeMapBuilder {
        private final Map<String, String> map = new LinkedHashMap<String, String>();

        public AttributeMapBuilder set(String name, String value) {
            if (name == null) {
                throw new IllegalArgumentException("attribute name must not be null");
            }
            map.put(name, value);
            return this;
        }

        public AttributeMapBuilder setAll(AttributeMap source) {
            for (Map.Entry<String, String> e : source.entries()) {
                map.put(e.getKey(), e.getValue());
            }
            return this;
        }

        public AttributeMap build() {
            return new AttributeMap(new LinkedHashMap<String, String>(map));
        }
    }
}
