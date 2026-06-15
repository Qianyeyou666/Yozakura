package gq.yozakura.event.bridge;

import net.minecraft.entity.EntityLivingBase;
import gq.yozakura.event.bus.events.Event;
import gq.yozakura.event.bus.types.EventType;

public class RenderLivingEvent implements Event {
    private final EventType type;
    private final EntityLivingBase entity;

    public RenderLivingEvent(EventType type, EntityLivingBase entityLivingBase) {
        this.type = type;
        this.entity = entityLivingBase;
    }

    public EventType getType() {
        return this.type;
    }

    public EntityLivingBase getEntity() {
        return this.entity;
    }
}
