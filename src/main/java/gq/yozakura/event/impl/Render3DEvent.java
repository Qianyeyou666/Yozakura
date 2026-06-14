/*
 * Decompiled with CFR 0.152.
 */
package gq.yozakura.event.impl;

import gq.yozakura.event.Event;

public class Render3DEvent
extends Event {
    private final float partialTicks;

    public Render3DEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return this.partialTicks;
    }
}
