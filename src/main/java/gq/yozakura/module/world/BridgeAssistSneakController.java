package gq.yozakura.module.world;

import gq.yozakura.event.bridge.SneakInputEvent;
import gq.yozakura.util.module.ItemUtil;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/** Maintains BridgeAssist's edge prediction and submits one sneak intent per input frame. */
final class BridgeAssistSneakController {
    private static final float MIN_LOOK_DOWN_PITCH = 70.0F;
    private static final double GROUND_CHECK_DEPTH = 0.01D;

    private final Minecraft mc;
    private final Numbers<Double> edgeOffset;
    private final Numbers<Double> unsneakDelay;
    private final Numbers<Double> sneakOnJump;
    private final Option<Boolean> sneakKeyPressed;
    private final Option<Boolean> holdingBlocks;
    private final Option<Boolean> lookingDown;
    private final Option<Boolean> notMovingForward;
    private final BridgeAssistSneakStateMachine stateMachine = new BridgeAssistSneakStateMachine();
    private final PlacementSessionTracker placementSessions = new PlacementSessionTracker();

    BridgeAssistSneakController(Minecraft mc, Numbers<Double> edgeOffset, Numbers<Double> unsneakDelay,
                                Numbers<Double> sneakOnJump, Option<Boolean> sneakKeyPressed,
                                Option<Boolean> holdingBlocks, Option<Boolean> lookingDown,
                                Option<Boolean> notMovingForward) {
        this.mc = mc;
        this.edgeOffset = edgeOffset;
        this.unsneakDelay = unsneakDelay;
        this.sneakOnJump = sneakOnJump;
        this.sneakKeyPressed = sneakKeyPressed;
        this.holdingBlocks = holdingBlocks;
        this.lookingDown = lookingDown;
        this.notMovingForward = notMovingForward;
    }

    void onSneakInput(SneakInputEvent event) {
        long inputFrame = placementSessions.beginInputFrame();
        float forward = event.getRawForward();
        float strafe = event.getRawStrafe();
        boolean activePlacementPending = placementSessions.hasPendingPlacementForActiveSession();
        if (shouldClearSneak(forward)) {
            reset();
            return;
        }
        boolean placementPending = activePlacementPending
                || placementSessions.hasPlacementForInputFrame(inputFrame);

        EdgeProbe probe = probeEdge(forward, strafe);
        BridgeAssistSneakStateMachine.Frame frame = new BridgeAssistSneakStateMachine.Frame(
                event.getTick(),
                true,
                forward != 0.0F || strafe != 0.0F,
                event.isPhysicalSneak(),
                Boolean.TRUE.equals(sneakKeyPressed.getValue()),
                event.isJump(),
                mc.thePlayer.onGround,
                probe == EdgeProbe.EDGE,
                probe == EdgeProbe.VOID,
                placementPending,
                placementSessions.consumePlacementCommit(),
                BridgeAssistMovementPrediction.ticksFromMillis(unsneakDelay.getValue()),
                BridgeAssistMovementPrediction.ticksFromMillis(sneakOnJump.getValue())
        );
        BridgeAssistSneakStateMachine.Decision decision = stateMachine.update(frame);
        placementSessions.finishInputFrame(decision == BridgeAssistSneakStateMachine.Decision.FORCE_ON,
                inputFrame);
        event.requestSneak(toIntent(decision), 0);
    }

    void onPlacementPacketAccepted(long writeId) {
        placementSessions.onPlacementAccepted(writeId);
    }

    void onPlacementPacketCompleted(long writeId, boolean success) {
        placementSessions.onPlacementCompleted(writeId, success);
    }

    void clearUnavailableState() {
        reset();
    }

    void disable() {
        reset();
    }

    private void reset() {
        placementSessions.reset();
        stateMachine.reset();
    }

    private boolean shouldClearSneak(float forward) {
        return Boolean.TRUE.equals(notMovingForward.getValue()) && forward > 0.0F
                || Boolean.TRUE.equals(lookingDown.getValue()) && mc.thePlayer.rotationPitch < MIN_LOOK_DOWN_PITCH
                || Boolean.TRUE.equals(holdingBlocks.getValue()) && !ItemUtil.isBlock(mc.thePlayer.getHeldItem());
    }

    private EdgeProbe probeEdge(float forward, float strafe) {
        double offset = computeEdgeOffset(predictInputBox(forward, strafe));
        if (Double.isNaN(offset)) {
            return EdgeProbe.VOID;
        }
        return offset > edgeOffset.getValue() ? EdgeProbe.EDGE : EdgeProbe.SUPPORTED;
    }

    private SneakInputEvent.SneakIntent toIntent(BridgeAssistSneakStateMachine.Decision decision) {
        if (decision == BridgeAssistSneakStateMachine.Decision.FORCE_ON) {
            return SneakInputEvent.SneakIntent.FORCE_ON;
        }
        if (decision == BridgeAssistSneakStateMachine.Decision.FORCE_OFF) {
            return SneakInputEvent.SneakIntent.FORCE_OFF;
        }
        return SneakInputEvent.SneakIntent.KEEP;
    }

    private AxisAlignedBB predictInputBox(float forward, float strafe) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        // The coordinator applies sneak scaling after this raw-input safety probe.
        double[] inputMotion = BridgeAssistMovementPrediction.calculateInputMotion(
                forward, strafe, inputAcceleration(), mc.thePlayer.rotationYaw
        );
        return box.offset(mc.thePlayer.motionX + inputMotion[0], 0.0D, mc.thePlayer.motionZ + inputMotion[1]);
    }

    private float inputAcceleration() {
        return BridgeAssistMovementPrediction.calculateInputAcceleration(
                mc.thePlayer.onGround,
                mc.thePlayer.getAIMoveSpeed(),
                mc.thePlayer.jumpMovementFactor,
                groundSlipperiness()
        );
    }

    private float groundSlipperiness() {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        BlockPos belowPlayer = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(box.minY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        return mc.theWorld.getBlockState(belowPlayer).getBlock().slipperiness;
    }

    private double computeEdgeOffset(AxisAlignedBB simBox) {
        AxisAlignedBB groundCheck = new AxisAlignedBB(
                simBox.minX, simBox.minY - GROUND_CHECK_DEPTH, simBox.minZ,
                simBox.maxX, simBox.minY, simBox.maxZ
        );
        List<AxisAlignedBB> groundBoxes = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, groundCheck);
        if (groundBoxes.isEmpty()) {
            return Double.NaN;
        }

        double feetX = (simBox.minX + simBox.maxX) / 2.0D;
        double feetZ = (simBox.minZ + simBox.maxZ) / 2.0D;
        double minDistance = Double.MAX_VALUE;
        for (AxisAlignedBB box : groundBoxes) {
            double closestX = Math.max(box.minX, Math.min(feetX, box.maxX));
            double closestZ = Math.max(box.minZ, Math.min(feetZ, box.maxZ));
            double dx = Math.abs(feetX - closestX);
            double dz = Math.abs(feetZ - closestZ);
            minDistance = Math.min(minDistance, Math.max(dx, dz));
        }
        return minDistance;
    }

    static final class PlacementSessionTracker {
        private static final long NO_PLACEMENT_SESSION = 0L;
        private static final long NO_WRITE_ID = 0L;
        private static final int MAX_UNCLAIMED_PLACEMENTS = 16;

        private final Object placementLock = new Object();
        private final Map<Long, Long> pendingPlacementSessions = new HashMap<Long, Long>();
        private final Map<Long, UnclaimedPlacement> unclaimedPlacementWriteIds =
                new HashMap<Long, UnclaimedPlacement>();
        private final Queue<Long> completedPlacementSessions = new ArrayDeque<Long>();
        private long nextPlacementSession;
        private long activePlacementSession;
        private long inputFrame;
        private boolean inputFrameOpen;

        long beginInputFrame() {
            synchronized (placementLock) {
                inputFrame++;
                inputFrameOpen = true;
                return inputFrame;
            }
        }

        void finishInputFrame(boolean forceSneak, long frame) {
            synchronized (placementLock) {
                if (!inputFrameOpen || inputFrame != frame) {
                    return;
                }
                inputFrameOpen = false;
                if (!forceSneak) {
                    activePlacementSession = NO_PLACEMENT_SESSION;
                    expireUnclaimedPlacements(frame);
                    return;
                }
                if (activePlacementSession == NO_PLACEMENT_SESSION) {
                    activePlacementSession = ++nextPlacementSession;
                }
                claimUnclaimedPlacementSessions(frame, activePlacementSession);
            }
        }

        void onPlacementAccepted(long writeId) {
            if (writeId == NO_WRITE_ID) {
                return;
            }
            synchronized (placementLock) {
                if (activePlacementSession != NO_PLACEMENT_SESSION) {
                    pendingPlacementSessions.put(writeId, activePlacementSession);
                    return;
                }
                if (unclaimedPlacementWriteIds.containsKey(writeId)
                        || unclaimedPlacementWriteIds.size() >= MAX_UNCLAIMED_PLACEMENTS) {
                    return;
                }
                long eligibleInputFrame = inputFrameOpen ? inputFrame : inputFrame + 1L;
                unclaimedPlacementWriteIds.put(writeId, new UnclaimedPlacement(eligibleInputFrame));
            }
        }

        void onPlacementCompleted(long writeId, boolean success) {
            if (writeId == NO_WRITE_ID) {
                return;
            }
            synchronized (placementLock) {
                Long session = pendingPlacementSessions.remove(writeId);
                if (session != null) {
                    if (success) {
                        completedPlacementSessions.offer(session);
                    }
                    return;
                }
                UnclaimedPlacement unclaimed = unclaimedPlacementWriteIds.get(writeId);
                if (unclaimed != null) {
                    unclaimed.complete(success);
                }
            }
        }

        boolean hasPendingPlacementForActiveSession() {
            synchronized (placementLock) {
                if (activePlacementSession == NO_PLACEMENT_SESSION) {
                    return false;
                }
                for (Long session : pendingPlacementSessions.values()) {
                    if (session != null && session.longValue() == activePlacementSession) {
                        return true;
                    }
                }
                return false;
            }
        }

        boolean hasPlacementForInputFrame(long frame) {
            synchronized (placementLock) {
                for (UnclaimedPlacement placement : unclaimedPlacementWriteIds.values()) {
                    if (placement.eligibleInputFrame == frame && placement.completion != Completion.FAILED) {
                        return true;
                    }
                }
                return false;
            }
        }

        boolean consumePlacementCommit() {
            synchronized (placementLock) {
                if (activePlacementSession == NO_PLACEMENT_SESSION) {
                    completedPlacementSessions.clear();
                    return false;
                }
                boolean committed = false;
                Long session;
                while ((session = completedPlacementSessions.poll()) != null) {
                    if (session.longValue() == activePlacementSession) {
                        committed = true;
                    }
                }
                return committed;
            }
        }

        void reset() {
            synchronized (placementLock) {
                activePlacementSession = NO_PLACEMENT_SESSION;
                inputFrameOpen = false;
                pendingPlacementSessions.clear();
                unclaimedPlacementWriteIds.clear();
                completedPlacementSessions.clear();
            }
        }

        private void claimUnclaimedPlacementSessions(long frame, long session) {
            Iterator<Map.Entry<Long, UnclaimedPlacement>> entries =
                    unclaimedPlacementWriteIds.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<Long, UnclaimedPlacement> entry = entries.next();
                UnclaimedPlacement placement = entry.getValue();
                if (placement.eligibleInputFrame < frame) {
                    entries.remove();
                    continue;
                }
                if (placement.eligibleInputFrame > frame) {
                    continue;
                }
                entries.remove();
                if (placement.completion == Completion.PENDING) {
                    pendingPlacementSessions.put(entry.getKey(), session);
                } else if (placement.completion == Completion.SUCCEEDED) {
                    completedPlacementSessions.offer(session);
                }
            }
        }

        private void expireUnclaimedPlacements(long frame) {
            Iterator<UnclaimedPlacement> placements = unclaimedPlacementWriteIds.values().iterator();
            while (placements.hasNext()) {
                if (placements.next().eligibleInputFrame <= frame) {
                    placements.remove();
                }
            }
        }

        private enum Completion {
            PENDING,
            SUCCEEDED,
            FAILED
        }

        private static final class UnclaimedPlacement {
            private final long eligibleInputFrame;
            private Completion completion = Completion.PENDING;

            private UnclaimedPlacement(long eligibleInputFrame) {
                this.eligibleInputFrame = eligibleInputFrame;
            }

            private void complete(boolean success) {
                if (completion == Completion.PENDING) {
                    completion = success ? Completion.SUCCEEDED : Completion.FAILED;
                }
            }
        }
    }

    private enum EdgeProbe {
        SUPPORTED,
        EDGE,
        VOID
    }
}
