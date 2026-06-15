package gq.vapulite.bridge.forge;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class AttackEntityEvent extends Event {
    public final EntityPlayer entityPlayer;
    public final Entity target;

    public AttackEntityEvent(EntityPlayer entityPlayer, Entity target) {
        this.entityPlayer = entityPlayer;
        this.target = target;
    }
}
