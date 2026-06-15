package gq.vapulite.bridge.forge;

import net.minecraft.entity.Entity;

public class RenderLivingEvent extends Event {
    public final Entity entity;
    public final float partialRenderTick;

    public RenderLivingEvent(Entity entity, float partialRenderTick) {
        this.entity = entity;
        this.partialRenderTick = partialRenderTick;
    }

    public static class Pre extends RenderLivingEvent {
        public Pre(Entity entity, float partialRenderTick) {
            super(entity, partialRenderTick);
        }
    }

    public static class Post extends RenderLivingEvent {
        public Post(Entity entity, float partialRenderTick) {
            super(entity, partialRenderTick);
        }
    }
}
