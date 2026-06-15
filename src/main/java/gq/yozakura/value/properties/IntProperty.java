package gq.yozakura.value.properties;

import gq.yozakura.value.Numbers;

import java.util.function.BooleanSupplier;

public class IntProperty extends Numbers<Integer> {
    private final BooleanSupplier visible;

    public IntProperty(String name, int value, int min, int max) {
        this(name, value, min, max, null);
    }

    public IntProperty(String name, int value, int min, int max, BooleanSupplier visible) {
        super(name, name, value, min, max, 1);
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible == null || visible.getAsBoolean();
    }
}
