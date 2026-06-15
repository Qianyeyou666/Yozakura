package gq.yozakura.value.properties;

import gq.yozakura.value.Option;

import java.util.function.BooleanSupplier;

public class BooleanProperty extends Option<Boolean> {
    private final BooleanSupplier visible;

    public BooleanProperty(String name, boolean value) {
        this(name, value, null);
    }

    public BooleanProperty(String name, boolean value, BooleanSupplier visible) {
        super(name, name, value);
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible == null || visible.getAsBoolean();
    }
}
