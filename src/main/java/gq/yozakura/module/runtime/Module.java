package gq.vapulite.module.runtime;

import gq.vapulite.module.ModuleType;
import gq.vapulite.value.Value;
import org.lwjgl.input.Keyboard;
import gq.vapulite.event.bus.EventManager;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public abstract class Module extends gq.vapulite.module.Module {
    private boolean valuesBound;

    protected Module(String name, boolean enabled) {
        super(name, Keyboard.KEY_NONE, ModuleType.Other);
        if (enabled) {
            this.state = true;
        }
    }

    public boolean isEnabled() {
        return getState();
    }

    public void setEnabled(boolean enabled) {
        setState(enabled);
    }

    @Override
    public void enable() {
        bindValuesFromFields();
        EventManager.register(this);
        onEnabled();
    }

    @Override
    public void disable() {
        try {
            onDisabled();
        } finally {
            EventManager.unregister(this);
        }
    }

    @Override
    public java.util.List<Value> getValues() {
        bindValuesFromFields();
        return super.getValues();
    }

    protected final void bindValuesFromFields() {
        if (valuesBound) {
            return;
        }
        valuesBound = true;
        Set<Value> known = new HashSet<Value>(this.values);
        Class<?> type = getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!Value.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(this);
                    if (value instanceof Value && known.add((Value) value)) {
                        this.values.add((Value) value);
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    public String[] getSuffix() {
        return new String[0];
    }

    public void verifyValue(String mode) {
    }

    public void onEnabled() {
    }

    public void onDisabled() {
    }
}
