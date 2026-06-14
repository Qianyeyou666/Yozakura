/*
 * Decompiled with CFR 0.152.
 */
package gq.yozakura.event.impl;

import gq.yozakura.event.Event;

public class SlowdownEvent
extends Event {
    public Type type;

    public SlowdownEvent(Type type) {
        this.type = type;
    }

    public static enum Type {
        Sprinting;

    }
}
