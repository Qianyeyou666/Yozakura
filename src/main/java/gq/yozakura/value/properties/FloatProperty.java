package gq.yozakura.value.properties;

import gq.yozakura.value.Numbers;

import java.util.function.BooleanSupplier;

public class FloatProperty extends Numbers<Float> {
    private final BooleanSupplier visible;

    public FloatProperty(String name, float value, float min, float max) {
        this(name, value, min, max, null);
    }

    public FloatProperty(String name, float value, float min, float max, BooleanSupplier visible) {
        super(name, name, value, min, max, 1.0F);
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible == null || visible.getAsBoolean();
    }
}
