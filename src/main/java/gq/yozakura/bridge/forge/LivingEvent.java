package gq.vapulite.bridge.forge;

import net.minecraft.entity.EntityLivingBase;

public class LivingEvent extends Event {
    public final EntityLivingBase entityLiving;

    public LivingEvent(EntityLivingBase entityLiving) {
        this.entityLiving = entityLiving;
    }

    public static class LivingUpdateEvent extends LivingEvent {
        public LivingUpdateEvent(EntityLivingBase entityLiving) {
            super(entityLiving);
        }
    }
}
