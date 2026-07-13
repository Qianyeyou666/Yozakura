package gq.yozakura.module.combat;

import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.MoveInputEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.module.world.Scaffold;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.util.module.KeyBindUtil;
import gq.yozakura.value.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class JumpReset extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty fakeCheck = new BooleanProperty("Fake Check", false);
    public final BooleanProperty forceForward = new BooleanProperty("Force Forward", true);

    private int ticksSinceVelocity = -1;
    private boolean handleReset;
    private boolean allowNext = true;

    public JumpReset() {
        super("JumpReset", false);
        setCategory(ModuleType.Combat);
        Chinese = "跳跃重置";
        Descript = "Jump reset after receiving velocity";
        About = Descript;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled() || !isInGame()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof S19PacketEntityStatus) {
            handleEntityStatus((S19PacketEntityStatus) packet);
            return;
        }
        if (!(packet instanceof S12PacketEntityVelocity)) {
            return;
        }

        S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
        if (velocity.getEntityID() != mc.thePlayer.getEntityId() || !consumeVelocityAllowance()) {
            return;
        }
        ticksSinceVelocity = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || !isInGame()) {
            return;
        }

        if (ticksSinceVelocity >= 0) {
            ticksSinceVelocity++;
        }
        if (ticksSinceVelocity >= 10) {
            ticksSinceVelocity = -1;
        }
        handleJumpReset();
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!isEnabled() || !isInGame() || !forceForward.getValue() || !handleReset) {
            return;
        }
        mc.thePlayer.movementInput.moveForward = 1.0F;
    }

    @Override
    public void onEnabled() {
        resetState();
    }

    @Override
    public void onDisabled() {
        releaseJumpKey();
        resetState();
    }

    private void handleEntityStatus(S19PacketEntityStatus packet) {
        Entity entity = packet.getEntity(mc.theWorld);
        if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
            allowNext = false;
        }
    }

    private boolean consumeVelocityAllowance() {
        if (!fakeCheck.getValue()) {
            return true;
        }
        if (!allowNext) {
            allowNext = true;
            return true;
        }
        return false;
    }

    private void handleJumpReset() {
        Scaffold scaffold = (Scaffold) YozakuraRuntime.moduleManager.modules.get(Scaffold.class);
        if (mc.thePlayer == null || mc.currentScreen instanceof GuiInventory
                || scaffold != null && scaffold.isEnabled()) {
            return;
        }
        if (ticksSinceVelocity >= 0) {
            handleReset = true;
            if (ticksSinceVelocity <= 2 && mc.thePlayer.onGround) {
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            }
        }
        if (ticksSinceVelocity >= 4 && ticksSinceVelocity <= 9) {
            releaseJumpKey();
            handleReset = false;
        }
    }

    private void releaseJumpKey() {
        if (mc.gameSettings == null || mc.gameSettings.keyBindJump == null) {
            return;
        }
        int key = mc.gameSettings.keyBindJump.getKeyCode();
        boolean physicallyDown = key < 0 ? Mouse.isButtonDown(key + 100) : Keyboard.isKeyDown(key);
        KeyBindUtil.setKeyBindState(key, physicallyDown);
    }

    private void resetState() {
        ticksSinceVelocity = -1;
        handleReset = false;
        allowNext = true;
    }
}
