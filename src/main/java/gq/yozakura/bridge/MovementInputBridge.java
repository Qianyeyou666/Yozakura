package gq.yozakura.bridge;

import gq.yozakura.auth.YozakuraAuthGate;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bridge.LivingUpdateEvent;
import gq.yozakura.event.bridge.MoveInputEvent;
import gq.yozakura.event.bridge.StrafeEvent;
import gq.yozakura.manager.RotationState;
import gq.yozakura.util.module.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MovementInput;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;

final class MovementInputBridge {
    private static final String HOOKED_INPUT_CLASS_SUFFIX = "MovementInputBridge$HookedMovementInput";
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static Field sprintToggleTimerField;
    private static boolean rotationApplied;
    private static volatile boolean blockSprintStartThisTick;
    private static boolean sprintKeySuppressed;
    private static boolean directYawPhysics = true;
    private static int suppressedSprintKey = Integer.MIN_VALUE;
    private static float savedYaw;
    private static float savedPrevYaw;
    private static Runnable beforeMoveInputHook;
    private static Runnable beforePlayerPacketHook;

    private MovementInputBridge() {
    }

    static void install() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.movementInput == null || player.movementInput instanceof HookedMovementInput) {
            return;
        }
        MovementInput delegate = unwrapMovementInput(player.movementInput);
        if (delegate != null) {
            player.movementInput = new HookedMovementInput(delegate);
        }
    }

    static void setDirectYawPhysics(boolean enabled) {
        if (directYawPhysics == enabled) {
            return;
        }
        restoreRotation();
        directYawPhysics = enabled;
    }

    static void setBeforeMoveInputHook(Runnable hook) {
        beforeMoveInputHook = hook;
    }

    static void setBeforePlayerPacketHook(Runnable hook) {
        beforePlayerPacketHook = hook;
    }

    static void uninstall() {
        restoreRotation();
        RotationState.clear();
        beforePlayerPacketHook = null;
        EntityPlayerSP player = mc.thePlayer;
        if (player != null && player.movementInput instanceof HookedMovementInput) {
            MovementInput delegate = unwrapMovementInput(player.movementInput);
            if (delegate != null) {
                player.movementInput = delegate;
            }
        }
    }

    private static MovementInput unwrapMovementInput(MovementInput input) {
        MovementInput current = input;
        for (int i = 0; i < 8 && current != null; i++) {
            if (!current.getClass().getName().endsWith(HOOKED_INPUT_CLASS_SUFFIX)) {
                return current;
            }
            Object next = readMovementDelegate(current);
            if (!(next instanceof MovementInput) || next == current) {
                return null;
            }
            current = (MovementInput) next;
        }
        return current;
    }

    private static Object readMovementDelegate(Object wrapper) {
        Class<?> type = wrapper.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField("delegate");
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                return field.get(wrapper);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
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
        restoreAppliedRotation(player);
    }

    static void resetMovementInput() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.movementInput == null) {
            return;
        }
        if (player.movementInput instanceof HookedMovementInput) {
            HookedMovementInput hooked = (HookedMovementInput) player.movementInput;
            hooked.delegate.updatePlayerMoveState();
            hooked.copyFrom(hooked.delegate);
            hooked.copyTo(hooked.delegate);
            return;
        }
        player.movementInput.updatePlayerMoveState();
    }

    static void prepareRotationForRender() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            rotationApplied = false;
            return;
        }
        restoreAppliedRotation(player);
    }

    static void restoreRotationForRender() {
        prepareRotationForRender();
    }

    static void finishTick() {
        restoreSprintKey();
        resetSprintState();
    }

    static boolean shouldBlockSprintPacket(C0BPacketEntityAction packet) {
        return packet != null
                && blockSprintStartThisTick
                && packet.getAction() == C0BPacketEntityAction.Action.START_SPRINTING;
    }

    private static void resetSprintState() {
        blockSprintStartThisTick = false;
    }

    private static void afterVanillaInput(HookedMovementInput input) {
        if (!YozakuraAuthGate.allowRuntime("movement-input")) {
            restoreRotation();
            return;
        }
        Runnable hook = beforeMoveInputHook;
        if (hook != null) {
            hook.run();
        }
        EventManager.call(new MoveInputEvent());
        applyFakeRotationMoveFix();

        StrafeEvent strafe = new StrafeEvent(input.moveStrafe, input.moveForward, 0.91F);
        EventManager.call(strafe);
        input.moveStrafe = strafe.getStrafe();
        input.moveForward = strafe.getForward();

        LivingUpdateEvent livingUpdate = new LivingUpdateEvent();
        EventManager.call(livingUpdate);
        updateSprintState(input);
        applyRotationForPhysics(input);
        Runnable playerPacketHook = beforePlayerPacketHook;
        if (playerPacketHook != null) {
            playerPacketHook.run();
        }
    }

    private static void applyFakeRotationMoveFix() {
        if (mc.thePlayer == null
                || !hasMovementRotation()
                || !RotationState.isMoveFix()
                || RotationState.getPriority() < 0
                || !MoveUtil.isForwardPressed()) {
            return;
        }
        MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
    }

    private static void updateSprintState(MovementInput input) {
        EntityPlayerSP player = mc.thePlayer;
        blockSprintStartThisTick = hasMovementRotation() && RotationState.getPriority() >= 0;
        if (player != null && blockSprintStartThisTick) {
            stopSprint(player);
            suppressSprintKey();
        }
    }

    private static void enforceSprintState(EntityPlayerSP player) {
        if (blockSprintStartThisTick) {
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
                || !hasMovementRotation()
                || RotationState.getPriority() < 0
                || !directYawPhysics
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

    private static boolean hasMovementRotation() {
        return RotationState.isActived();
    }

    private static void restoreAppliedRotation(EntityPlayerSP player) {
        if (!rotationApplied) {
            return;
        }
        player.rotationYaw = savedYaw;
        player.prevRotationYaw = savedPrevYaw;
        rotationApplied = false;
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
            copyFrom(delegate);
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
