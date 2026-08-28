package gq.yozakura.ui.engine.css;

/**
 * CSS 属性名。值对象，包装字符串名称。
 *
 * <p>阶段 1 用字符串包装；后续阶段可扩展为枚举或带类型的属性描述符。
 * 自定义变量声明使用前缀 "--"（如 --accent），同样由 Property 包装。
 */
public final class Property {
    private final String name;

    public Property(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("property name must not be null or empty");
        }
        this.name = name;
    }

    public String name() {
        return name;
    }

    public boolean isCustomProperty() {
        return name.startsWith("--");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Property)) return false;
        return name.equals(((Property) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
