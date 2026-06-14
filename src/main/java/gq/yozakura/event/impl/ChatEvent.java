/*
 * Decompiled with CFR 0.152.
 */
package gq.yozakura.event.impl;

import gq.yozakura.event.Event;

public class ChatEvent
extends Event {
    public String message;

    public ChatEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
