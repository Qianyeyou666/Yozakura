package gq.yozakura.module.combat;

import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Forge-only listener kept out of Aimbot's Lunar-visible method signatures. */
public final class AimAssistForgeCameraBridge {
    private final Aimbot owner;

    AimAssistForgeCameraBridge(Aimbot owner) {
        this.owner = owner;
    }

    static AimAssistForgeCameraBridge register(Aimbot owner) {
        AimAssistForgeCameraBridge bridge = new AimAssistForgeCameraBridge(owner);
        MinecraftForge.EVENT_BUS.register(bridge);
        FMLCommonHandler.instance().bus().register(bridge);
        return bridge;
    }

    static void unregister(Object listener) {
        if (listener instanceof AimAssistForgeCameraBridge) {
            MinecraftForge.EVENT_BUS.unregister(listener);
            FMLCommonHandler.instance().bus().unregister(listener);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        Aimbot.CameraDelta delta = owner.applyLockOnBoundary((float) event.renderPartialTicks);
        event.yaw += delta.getYaw();
        event.pitch += delta.getPitch();
    }

    /** Fallback for Forge forks that omit CameraSetup from the client event path. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        owner.applyLockOnBoundary(event.renderTickTime);
    }
}
