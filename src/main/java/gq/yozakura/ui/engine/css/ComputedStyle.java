package gq.yozakura.ui.engine.css;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 元素经过 cascade + 继承 + var() 解析后的最终计算样式。
 *
 * <p>不可变值对象：构造完成后内部映射不可被外部修改。
 *
 * <p>属性分两类存储：
 * <ul>
 *   <li>普通属性（{@code color}、{@code font-size} 等）：值已完全解析（var() 已展开）</li>
 *   <li>自定义变量（{@code --accent} 等）：保留原始值（可能含 var() 引用），
 *       供后代继承并按需由 {@link CssVariableResolver} 解析。
 *       自定义变量本身是继承属性。</li>
 * </ul>
 *
 * <p>使用 {@link #builder()} 构造；{@link Builder#inheritFrom(ComputedStyle)} 实现继承拷贝。
 */
public final class ComputedStyle {

    private final Map<String, String> properties;
    private final Map<String, String> customProperties;

    private ComputedStyle(Map<String, String> properties, Map<String, String> customProperties) {
        this.properties = properties;
        this.customProperties = customProperties;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 普通属性值；不存在返回 null。 */
    public String get(String name) {
        return properties.get(name);
    }

    public boolean has(String name) {
        return properties.containsKey(name);
    }

    /** 全部普通属性名视图（不可变）。 */
    public Set<String> propertyNames() {
        return properties.keySet();
    }

    /** 自定义变量值；不存在返回 null。 */
    public String customProperty(String name) {
        return customProperties.get(name);
    }

    /** 全部自定义变量名视图（不可变）。 */
    public Set<String> customPropertyNames() {
        return customProperties.keySet();
    }

    /** 自定义变量映射视图（不可变）。 */
    public Map<String, String> customProperties() {
        return customProperties;
    }

    /** 可变 builder；构造期间累积属性，{@link #build()} 产出不可变 ComputedStyle。 */
    public static final class Builder {
        private final Map<String, String> properties = new LinkedHashMap<String, String>();
        private final Map<String, String> customProperties = new LinkedHashMap<String, String>();

        /** 设置普通属性值；同名覆盖。 */
        public Builder set(String name, String value) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("property name must not be null or empty");
            }
            if (value == null) {
                throw new IllegalArgumentException("value must not be null for '" + name + "'");
            }
            properties.put(name, value);
            return this;
        }

        /** 设置自定义变量值；同名覆盖。 */
        public Builder setCustom(String name, String value) {
            if (name == null || !name.startsWith("--")) {
                throw new IllegalArgumentException(
                        "custom property name must start with '--': " + name);
            }
            if (value == null) {
                throw new IllegalArgumentException(
                        "custom property value must not be null for '" + name + "'");
            }
            customProperties.put(name, value);
            return this;
        }

        /**
         * 从父 ComputedStyle 继承全部属性（深拷贝）。
         * 后续 {@link #set}/{@link #setCustom} 可覆盖已继承的项。
         */
        public Builder inheritFrom(ComputedStyle parent) {
            if (parent == null) {
                return this;
            }
            properties.putAll(parent.properties);
            customProperties.putAll(parent.customProperties);
            return this;
        }

        public ComputedStyle build() {
            return new ComputedStyle(
                    Collections.unmodifiableMap(new LinkedHashMap<String, String>(properties)),
                    Collections.unmodifiableMap(new LinkedHashMap<String, String>(customProperties)));
        }
    }
}
