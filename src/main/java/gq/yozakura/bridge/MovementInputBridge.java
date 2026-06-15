package gq.yozakura.bridge;

import gq.yozakura.auth.YozakuraAuthGate;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bridge.LivingUpdateEvent;
import gq.yozakura.event.bridge.MoveInputEvent;
import gq.yozakura.event.bridge.StrafeEvent;
import gq.yozakura.manager.RotationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MovementInput;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;

final class MovementInputBridge {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static Field sprintToggleTimerField;
    private static boolean rotationApplied;
    private static boolean silentMovementThisTick;
    private static boolean sprintAllowedThisTick = true;
    private static boolean sprintKeySuppressed;
    private static int suppressedSprintKey = Integer.MIN_VALUE;
    private static float savedYaw;
    private static float savedPrevYaw;

    private MovementInputBridge() {
    }

    static void install() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.movementInput == null || player.movementInput instanceof HookedMovementInput) {
            return;
        }
        player.movementInput = new HookedMovementInput(player.movementInput);
    }

    static void uninstall() {
        restoreRotation();
        EntityPlayerSP player = mc.thePlayer;
        if (player != null && player.movementInput instanceof HookedMovementInput) {
            player.movementInput = ((HookedMovementInput) player.movementInput).delegate;
        }
    }

    static void restoreRotation() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            rotationApplied = false;
            resetSprintState();
            return;
        }
        enforceSprintState(player);
        restoreSprintKey();
        if (!rotationApplied) {
            return;
        }
        player.rotationYaw = savedYaw;
        player.prevRotationYaw = savedPrevYaw;
        rotationApplied = false;
    }

    static void finishTick() {
        restoreSprintKey();
        resetSprintState();
    }

    static boolean shouldBlockSprintPacket(C0BPacketEntityAction packet) {
        return packet != null
                && silentMovementThisTick
                && !sprintAllowedThisTick
                && packet.getAction() == C0BPacketEntityAction.Action.START_SPRINTING;
    }

    private static void resetSprintState() {
        silentMovementThisTick = false;
        sprintAllowedThisTick = true;
    }

    private static void afterVanillaInput(HookedMovementInput input) {
        if (!YozakuraAuthGate.allowRuntime("movement-input")) {
            restoreRotation();
            return;
        }
        EventManager.call(new MoveInputEvent());

        StrafeEvent strafe = new StrafeEvent(input.moveStrafe, input.moveForward, 0.91F);
        EventManager.call(strafe);
        input.moveStrafe = strafe.getStrafe();
        input.moveForward = strafe.getForward();

        EventManager.call(new LivingUpdateEvent());
        updateSprintState(input);
        applyRotationForPhysics(input);
    }

    private static void updateSprintState(MovementInput input) {
        EntityPlayerSP player = mc.thePlayer;
        silentMovementThisTick = RotationState.isActived() && RotationState.getPriority() >= 0;
        sprintAllowedThisTick = !silentMovementThisTick;
        if (player != null && !sprintAllowedThisTick) {
            stopSprint(player);
            suppressSprintKey();
        }
    }

    private static void enforceSprintState(EntityPlayerSP player) {
        if (silentMovementThisTick && !sprintAllowedThisTick) {
            stopSprint(player);
        }
    }

    private static void stopSprint(EntityPlayerSP player) {
        try {
            player.setSprinting(false);
            resetSprintToggleTimer(player);
        } catch (Throwable ignored) {
        }
    }

    private static void applyRotationForPhysics(MovementInput input) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null
                || !RotationState.isActived()
                || RotationState.getPriority() < 0
                || (input.moveForward == 0.0F && input.moveStrafe == 0.0F)) {
            return;
        }
        if (!rotationApplied) {
            savedYaw = player.rotationYaw;
            savedPrevYaw = player.prevRotationYaw;
            rotationApplied = true;
        }
        float yaw = RotationState.getSmoothedYaw();
        player.rotationYaw = yaw;
        player.prevRotationYaw = yaw;
    }

    private static void suppressSprintKey() {
        if (mc.gameSettings == null || mc.gameSettings.keyBindSprint == null) {
            return;
        }
        int key = mc.gameSettings.keyBindSprint.getKeyCode();
        sprintKeySuppressed = true;
        suppressedSprintKey = key;
        KeyBinding.setKeyBindState(key, false);
    }

    private static void restoreSprintKey() {
        if (!sprintKeySuppressed) {
            return;
        }
        int key = suppressedSprintKey;
        sprintKeySuppressed = false;
        suppressedSprintKey = Integer.MIN_VALUE;
        if (key == Integer.MIN_VALUE) {
            return;
        }
        try {
            boolean physicallyDown = key < 0 ? Mouse.isButtonDown(key + 100) : Keyboard.isKeyDown(key);
            KeyBinding.setKeyBindState(key, physicallyDown);
        } catch (Throwable ignored) {
            KeyBinding.setKeyBindState(key, false);
        }
    }

    private static void resetSprintToggleTimer(EntityPlayerSP player) {
        try {
            Field field = getSprintToggleTimerField();
            if (field != null) {
                field.setInt(player, 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Field getSprintToggleTimerField() {
        if (sprintToggleTimerField != null) {
            return sprintToggleTimerField;
        }
        for (String name : new String[]{"sprintToggleTimer", "field_71156_d"}) {
            try {
                Field field = EntityPlayerSP.class.getDeclaredField(name);
                field.setAccessible(true);
                sprintToggleTimerField = field;
                return sprintToggleTimerField;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static final class HookedMovementInput extends MovementInput {
        private final MovementInput delegate;

        private HookedMovementInput(MovementInput delegate) {
            this.delegate = delegate;
        }

        @Override
        public void updatePlayerMoveState() {
            delegate.updatePlayerMoveState();
            copyFrom(delegate);
            afterVanillaInput(this);
            copyTo(delegate);
        }

        private void copyFrom(MovementInput input) {
            this.moveStrafe = input.moveStrafe;
            this.moveForward = input.moveForward;
            this.jump = input.jump;
            this.sneak = input.sneak;
        }

        private void copyTo(MovementInput input) {
            input.moveStrafe = this.moveStrafe;
            input.moveForward = this.moveForward;
            input.jump = this.jump;
            input.sneak = this.sneak;
        }
    }
}
