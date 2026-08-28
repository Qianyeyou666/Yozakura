package gq.yozakura.value.properties;

import gq.yozakura.value.Numbers;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

public class ModeProperty extends Numbers<Integer> {
    private final String[] modes;
    private final String[] selectableModes;
    private final Map<String, Integer> storedAliases = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
    private final BooleanSupplier visible;

    public ModeProperty(String name, int value, String[] modes) {
        this(name, value, modes, modes, null);
    }

    public ModeProperty(String name, int value, String[] modes, String[] selectableModes) {
        this(name, value, modes, selectableModes, null);
    }

    public ModeProperty(String name, int value, String[] modes, BooleanSupplier visible) {
        this(name, value, modes, modes, visible);
    }

    public ModeProperty(String name, int value, String[] modes, String[] selectableModes,
                        BooleanSupplier visible) {
        super(name, name, value, 0, Math.max(0, modes.length - 1), 1);
        this.modes = modes;
        this.selectableModes = selectableModes;
        this.visible = visible;
    }

    public String[] getModes() {
        return selectableModes;
    }

    public String getModeString() {
        int index = getValue();
        return index >= 0 && index < modes.length ? modes[index] : "";
    }

    public void setMode(String mode) {
        setModeFromOptions(mode, selectableModes);
    }

    public void setStoredMode(String mode) {
        Integer alias = mode == null ? null : storedAliases.get(mode);
        if (alias != null) {
            setValue(alias);
            return;
        }
        setModeFromOptions(mode, modes);
    }

    public ModeProperty addStoredAlias(String alias, int storedIndex) {
        if (alias != null && !alias.trim().isEmpty()
                && storedIndex >= 0 && storedIndex < modes.length) {
            storedAliases.put(alias, storedIndex);
        }
        return this;
    }

    public void nextMode() {
        String current = getModeString();
        int currentSelectable = indexOf(selectableModes, current);
        int next = currentSelectable < 0 || currentSelectable + 1 >= selectableModes.length
                ? 0
                : currentSelectable + 1;
        setMode(selectableModes[next]);
    }

    @Override
    public void setNumberValue(double value) {
        int index = (int) Math.round(value);
        if (index < 0 || index >= selectableModes.length) {
            return;
        }
        setMode(selectableModes[index]);
    }

    public void setStoredNumberValue(double value) {
        int index = (int) Math.round(value);
        setValue(Math.max(0, Math.min(modes.length - 1, index)));
    }

    private void setModeFromOptions(String mode, String[] options) {
        int selected = indexOf(options, mode);
        if (selected < 0) {
            return;
        }
        int stored = indexOf(modes, options[selected]);
        if (stored >= 0) {
            setValue(stored);
        }
    }

    private static int indexOf(String[] options, String mode) {
        if (mode == null) {
            return -1;
        }
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(mode)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean isVisible() {
        return visible == null || visible.getAsBoolean();
    }
}
