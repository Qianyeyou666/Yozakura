package gq.vapulite.event.bridge;

import net.minecraft.entity.Entity;
import gq.vapulite.event.bus.events.Event;

public class AttackEvent implements Event {
    private final Entity target;
    private boolean cancelled;

    public AttackEvent(Entity target) {
        this.target = target;
        this.cancelled = false;
    }

    public Entity getTarget() {
        return this.target;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}