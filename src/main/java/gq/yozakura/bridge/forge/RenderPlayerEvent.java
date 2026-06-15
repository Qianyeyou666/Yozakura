package gq.yozakura.bridge.forge;

import net.minecraft.entity.player.EntityPlayer;

public class RenderPlayerEvent extends Event {
    public final EntityPlayer entityPlayer;

    public RenderPlayerEvent(EntityPlayer entityPlayer) {
        this.entityPlayer = entityPlayer;
    }

    public static class Pre extends RenderPlayerEvent {
        public Pre(EntityPlayer entityPlayer) {
            super(entityPlayer);
        }
    }

    public static class Post extends RenderPlayerEvent {
        public Post(EntityPlayer entityPlayer) {
            super(entityPlayer);
        }
    }
}
