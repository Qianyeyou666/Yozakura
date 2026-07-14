package gq.yozakura.bridge;

import gq.yozakura.auth.YozakuraAuthGate;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bridge.LivingUpdateEvent;
import gq.yozakura.event.bridge.MoveInputEvent;
import gq.yozakura.event.bridge.SneakInputEvent;
import gq.yozakura.event.bridge.StrafeEvent;
import gq.yozakura.manager.RotationState;
import gq.yozakura.util.module.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;

final class MovementInputBridge {
    private static final String HOOKED_INPUT_CLASS_SUFFIX = "MovementInputBridge$HookedMovementInput";
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static boolean rotationApplied;
    private static boolean directYawPhysics = true;
    private static float savedYaw;
    private static float savedPrevYaw;
    private static Runnable beforeMoveInputHook;
    private static Runnable afterMoveInputHook;
    private static final SneakInputCoordinator sneakInputCoordinator = new SneakInputCoordinator();

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

    static void setAfterMoveInputHook(Runnable hook) {
        afterMoveInputHook = hook;
    }

    static void setSafeWalkRequested(boolean requested) {
        sneakInputCoordinator.setSafeWalkRequested(requested);
    }

    static void uninstall() {
        restoreRotation();
        RotationState.clear();
        sneakInputCoordinator.clear();
        beforeMoveInputHook = null;
        afterMoveInputHook = null;
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
            return;
        }
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
    }

    private static void afterVanillaInput(HookedMovementInput input) {
        if (!YozakuraAuthGate.allowRuntime("movement-input")) {
            restoreRotation();
            sneakInputCoordinator.clear();
            return;
        }
        Runnable hook = beforeMoveInputHook;
        if (hook != null) {
            hook.run();
        }
        resolveSneakInput(input);
        EventManager.call(new MoveInputEvent());
        applyFakeRotationMoveFix();

        StrafeEvent strafe = new StrafeEvent(input.moveStrafe, input.moveForward, 0.91F);
        EventManager.call(strafe);
        input.moveStrafe = strafe.getStrafe();
        input.moveForward = strafe.getForward();

        LivingUpdateEvent livingUpdate = new LivingUpdateEvent();
        EventManager.call(livingUpdate);
        applyRotationForPhysics(input);
        Runnable postInputHook = afterMoveInputHook;
        if (postInputHook != null) {
            postInputHook.run();
        }
    }

    private static void resolveSneakInput(HookedMovementInput input) {
        boolean sampledSneak = input.sneak;
        float rawForward = SneakInputCoordinator.toRawAxis(input.moveForward, sampledSneak);
        float rawStrafe = SneakInputCoordinator.toRawAxis(input.moveStrafe, sampledSneak);
        int tick = mc.thePlayer == null ? Integer.MIN_VALUE : mc.thePlayer.ticksExisted;
        boolean physicalSneak = mc.gameSettings != null
                && isPhysicalKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());
        SneakInputEvent event = new SneakInputEvent(tick, rawForward, rawStrafe, input.jump,
                sampledSneak, physicalSneak);
        EventManager.call(event);

        SneakInputCoordinator.ResolvedInput resolved = sneakInputCoordinator.resolve(sampledSneak,
                rawForward, rawStrafe, event.getIntent());
        input.sneak = resolved.isSneaking();
        input.moveForward = resolved.getForward();
        input.moveStrafe = resolved.getStrafe();
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

    private static boolean isPhysicalKeyDown(int key) {
        try {
            return key < 0 ? Mouse.isCreated() && Mouse.isButtonDown(key + 100) : Keyboard.isKeyDown(key);
        } catch (Throwable ignored) {
            return false;
        }
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
