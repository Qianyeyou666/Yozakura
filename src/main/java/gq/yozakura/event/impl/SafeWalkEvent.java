/*
 * Decompiled with CFR 0.152.
 */
package gq.yozakura.event.impl;

import gq.yozakura.event.Event;

public class SafeWalkEvent
extends Event {
    public boolean walkSafely;

    public SafeWalkEvent(boolean walkSafely) {
        this.walkSafely = walkSafely;
    }

    public boolean isWalkSafely() {
        return this.walkSafely;
    }

    public void setWalkSafely(boolean walkSafely) {
        this.walkSafely = walkSafely;
    }
}
