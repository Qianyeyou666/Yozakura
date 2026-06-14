/*
 * Decompiled with CFR 0_132.
 */
package gq.yozakura.value;

import gq.yozakura.core.Client;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public abstract class Value<V> {
    private String displayName;
    private String name;
    private V value;
    public float optionAnim = 0;//present
    public float optionAnimNow = 0;//present
    public float animX1;
    public float animX;
    private BooleanSupplier visibleWhen;

    public Value(String displayName, String name) {
        this.displayName = displayName;
        this.name = name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getName() {
        return this.name;
    }

    public V getValue() {
        return this.value;
    }

    public void setValue(V value) {
        if (Objects.equals(this.value, value)) {
            return;
        }
        this.value = value;
        Client.markConfigDirty();
    }

    public Value<V> visibleWhen(BooleanSupplier visibleWhen) {
        this.visibleWhen = visibleWhen;
        return this;
    }

    public boolean isVisible() {
        if (visibleWhen == null) {
            return true;
        }
        try {
            return visibleWhen.getAsBoolean();
        } catch (Throwable ignored) {
            return true;
        }
    }
}
