package gq.yozakura.ui.engine.css;

/**
 * CSS 声明：属性 + 值 + important 标记。不可变值对象。
 */
public final class Declaration {
    private final Property property;
    private final CssValue value;
    private final boolean important;

    public Declaration(Property property, CssValue value, boolean important) {
        if (property == null) throw new IllegalArgumentException("property must not be null");
        if (value == null) throw new IllegalArgumentException("value must not be null");
        this.property = property;
        this.value = value;
        this.important = important;
    }

    public Property property() {
        return property;
    }

    public CssValue value() {
        return value;
    }

    public boolean important() {
        return important;
    }

    @Override
    public String toString() {
        return property.name() + ": " + value.raw() + (important ? " !important" : "");
    }
}
