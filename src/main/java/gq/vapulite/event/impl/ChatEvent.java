/*
 * Decompiled with CFR 0.152.
 */
package gq.vapulite.event.impl;

import gq.vapulite.event.Event;

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
