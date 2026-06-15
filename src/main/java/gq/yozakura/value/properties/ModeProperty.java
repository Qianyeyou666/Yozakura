package gq.yozakura.value.properties;

import gq.yozakura.value.Numbers;

import java.util.function.BooleanSupplier;

public class ModeProperty extends Numbers<Integer> {
    private final String[] modes;
    private final BooleanSupplier visible;

    public ModeProperty(String name, int value, String[] modes) {
        this(name, value, modes, null);
    }

    public ModeProperty(String name, int value, String[] modes, BooleanSupplier visible) {
        super(name, name, value, 0, Math.max(0, modes.length - 1), 1);
        this.modes = modes;
        this.visible = visible;
    }

    public String[] getModes() {
        return modes;
    }

    public String getModeString() {
        int index = getValue();
        return index >= 0 && index < modes.length ? modes[index] : "";
    }

    public void setMode(String mode) {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equalsIgnoreCase(mode)) {
                setValue(i);
                return;
            }
        }
    }

    public void nextMode() {
        int next = getValue() + 1;
        if (next >= modes.length) {
            next = 0;
        }
        setValue(next);
    }

    @Override
    public void setNumberValue(double value) {
        int index = (int) Math.round(value);
        setValue(Math.max(0, Math.min(modes.length - 1, index)));
    }

    @Override
    public boolean isVisible() {
        return visible == null || visible.getAsBoolean();
    }
}
