package gq.yozakura.module.combat;

import gq.yozakura.bridge.PacketBridgeSupport;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class Criticals extends Module {
    public enum CriticalMode {
        PACKET,
        JUMP
    }

    private static Criticals INSTANCE;

    private final Mode<CriticalMode> mode =
            new Mode<CriticalMode>("Mode", "Mode", CriticalMode.values(), CriticalMode.PACKET);
    private final Numbers<Double> chance = new Numbers<Double>("Chance", "Chance", 100.0, 0.0, 100.0, 1.0);
    private int lastCritTick;

    public Criticals() {
        super("Criticals", Keyboard.KEY_NONE, ModuleType.Combat, "Force critical hits on attack");
        this.addValues(mode, chance);
        Chinese = "刀刀暴击";
        INSTANCE = this;
    }

    @Override
    public void enable() {
        lastCritTick = 0;
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button != 0 || !event.buttonstate || !isInGame()) {
            return;
        }
        if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) {
            return;
        }
        performCritical();
    }

    public static void tryCritical() {
        tryCritical(true);
    }

    public static void tryCritical(boolean allowMovementPackets) {
        if (INSTANCE == null || !INSTANCE.getState() || !INSTANCE.isInGame()) {
            return;
        }
        INSTANCE.performCritical(allowMovementPackets);
    }

    private void performCritical() {
        performCritical(true);
    }

    private void performCritical(boolean allowMovementPackets) {
        if (!canCritical()) {
            return;
        }
        if (mc.thePlayer.ticksExisted == lastCritTick) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble(100.0D) > chance.getValue()) {
            return;
        }

        if (mode.getValue() == CriticalMode.JUMP) {
            mc.thePlayer.motionY = 0.42D;
            mc.thePlayer.onGround = false;
        } else {
            if (!allowMovementPackets) {
                return;
            }
            double x = mc.thePlayer.posX;
            double y = mc.thePlayer.posY;
            double z = mc.thePlayer.posZ;
            C03PacketPlayer first = new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0625D, z, false);
            C03PacketPlayer second = new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false);
            PacketBridgeSupport.markNonCanonicalPlayerPacket(first);
            PacketBridgeSupport.markNonCanonicalPlayerPacket(second);
            mc.thePlayer.sendQueue.addToSendQueue(first);
            mc.thePlayer.sendQueue.addToSendQueue(second);
        }
        lastCritTick = mc.thePlayer.ticksExisted;
    }

    private boolean canCritical() {
        return isInGame()
                && mc.thePlayer.onGround
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isInLava()
                && !mc.thePlayer.isOnLadder()
                && mc.thePlayer.ridingEntity == null;
    }
}
