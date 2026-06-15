/*
 * Decompiled with CFR 0.152.
 */
package gq.vapulite.event.impl;

import gq.vapulite.event.Event;

public class KeycodeEvent
extends Event {
    public int key;

    public KeycodeEvent(int key) {
        this.key = key;
    }

    public int getKey() {
        return this.key;
    }

    public void setKey(int key) {
        this.key = key;
    }
}
