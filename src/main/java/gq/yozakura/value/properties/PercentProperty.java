package gq.yozakura.value.properties;

import gq.yozakura.value.Numbers;

import java.util.function.BooleanSupplier;

public class PercentProperty extends Numbers<Integer> {
    private final BooleanSupplier visible;

    public PercentProperty(String name, int value) {
        this(name, value, null);
    }

    public PercentProperty(String name, int value, BooleanSupplier visible) {
        super(name, name, value, 0, 100, 1);
        this.visible = visible;
    }

    public PercentProperty(String name, int value, int min, int max, BooleanSupplier visible) {
        super(name, name, value, min, max, 1);
        this.visible = visible;
    }

    @Override
    public boolean isVisible() {
        return visible == null || visible.getAsBoolean();
    }
}
