package gq.yozakura.module.world;

import gq.yozakura.util.module.BlockUtil;
import gq.yozakura.util.module.ItemUtil;
import gq.yozakura.util.module.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * TellyBridge 的有界放置搜索。
 *
 * <p>搜索只返回经真实射线、reach、碰撞、支撑面和固定水平车道共同验证的候选；
 * 候选高度按参考 Telly 的当前、严格、上一帧及受限上升上下文动态判定。</p>
 */
final class TellyBridgePlacementSearch {
    private static final EnumFacing[] ALL_PLACE_FACES = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.UP
    };
    private static final double[] FACE_HIT_OFFSETS = {0.5D, 0.35D, 0.65D};
    private static final double[] EXTENDED_FACE_HIT_OFFSETS = {
            0.5D, 0.35D, 0.65D, 0.2D, 0.8D, 0.05D, 0.95D
    };
    private static final int REJECT_TICKS = 4;

    static final class Candidate {
        final BlockPos target;
        final BlockPos support;
        final EnumFacing face;
        final Vec3 hitVec;
        final float yaw;
        final float pitch;

        Candidate(BlockPos target, BlockPos support, EnumFacing face, Vec3 hitVec,
                  float yaw, float pitch) {
            this.target = target;
            this.support = support;
            this.face = face;
            this.hitVec = hitVec;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private final Minecraft mc;
    private final Map<BlockPos, Integer> rejectedTargets = new HashMap<BlockPos, Integer>();
    private final Set<BlockPos> cancelledGhostBlocks = new LinkedHashSet<BlockPos>();
    private boolean hasLastSentServerPos;
    private double lastSentServerPosX;
    private double lastSentServerPosY;
    private double lastSentServerPosZ;
    private BlockPos activationAnchor;
    private BlockPos lastPlacedPos;
    private BlockPos lastSupportPos;
    private EnumFacing lastSupportFace;
    private int travelX;
    private int travelZ;
    private int bridgeLaneBlock;
    private int bridgeStartProgress;
    private boolean laneReady;
    private int forcedModeCheck;

    TellyBridgePlacementSearch(Minecraft mc) {
        this.mc = mc;
    }

    void beginLane(EntityPlayerSP player, float baseYaw, BlockPos anchor) {
        travelX = TellyBridgeProgram.travelX(baseYaw);
        travelZ = TellyBridgeProgram.travelZ(baseYaw);
        activationAnchor = anchor;
        BlockPos start = anchor != null ? anchor : new BlockPos(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.posY) - 1,
                MathHelper.floor_double(player.posZ));
        bridgeLaneBlock = travelX != 0 ? start.getZ() : start.getX();
        bridgeStartProgress = progress(start);
        lastPlacedPos = start;
        lastSupportPos = null;
        lastSupportFace = null;
        hasLastSentServerPos = false;
        laneReady = true;
        forcedModeCheck = 0;
        rejectedTargets.clear();
        cancelledGhostBlocks.clear();
    }

    Candidate resolveCandidateWithOffCursorSilentPitch(EntityPlayerSP player, float yaw,
                                                        float currentPitch, ItemStack heldStack,
                                                        long deadlineMs) {
        Candidate candidate = findBelowPlacement(player, yaw, currentPitch, heldStack, deadlineMs);
        if (candidate == null) {
            return null;
        }
        if (matchesRay(candidate, yaw, currentPitch)) {
            return candidate;
        }
        Candidate derived = buildCandidate(player, candidate.target, candidate.support,
                candidate.face, candidate.hitVec, yaw, false);
        return derived != null ? derived : candidate;
    }

    Candidate findBelowPlacement(EntityPlayerSP player, float yaw, float currentPitch,
                                 ItemStack heldStack, long deadlineMs) {
        if (!isUsable(heldStack) || expired(deadlineMs)) {
            return null;
        }
        pruneRejectedTargets(player.ticksExisted);

        Candidate direct = findDirectCursorRayPlacement(player, yaw, currentPitch, heldStack);
        if (direct != null) {
            return direct;
        }

        boolean straight = !isDiagonalMovementContext(player);
        Candidate result = null;
        if (straight) {
            boolean centerAir = isStraightCenterBelowAir(player);
            boolean groundException = isStraightPreviousTickCenterOnGroundSupport(player);
            boolean previousFirst = centerAir || groundException
                    || !isCursorDirectedAtBlock(yaw, currentPitch)
                    || isNearStraightSupportEdge(player);
            if (centerAir) {
                result = findBelowPlayerAirborneFallback(player, yaw, currentPitch, heldStack,
                        Math.max(deadlineMs, System.currentTimeMillis() + 4L));
            }
            if (result == null && previousFirst) {
                result = findStraightPreviousVisibleFaceFallback(player, yaw, currentPitch,
                        heldStack, deadlineMs);
            }
            if (result == null && groundException) {
                result = findStraightGroundExceptionCandidate(player, yaw, currentPitch,
                        heldStack, deadlineMs);
            }
            if (result == null) {
                result = findStraightLegacyLaneFallback(player, yaw, currentPitch,
                        heldStack, deadlineMs);
            }
            if (result == null && !previousFirst) {
                result = findStraightPreviousVisibleFaceFallback(player, yaw, currentPitch,
                        heldStack, deadlineMs);
            }
            if (result == null) {
                result = findPreviousBlockAirborneFallback(player, yaw, currentPitch,
                        heldStack, Math.max(deadlineMs, System.currentTimeMillis() + 4L));
            }
            return result;
        }

        long diagonalDeadline = Math.max(deadlineMs, System.currentTimeMillis() + 10L);
        result = findBelowPlacementForSupport(player, yaw, currentPitch, heldStack,
                null, null, diagonalDeadline);
        if (result == null) {
            result = findBelowPlayerAirborneFallback(player, yaw, currentPitch, heldStack,
                    diagonalDeadline);
        }
        if (result == null) {
            result = findNearestSupportToBelowPlayerFallback(player, yaw, currentPitch,
                    heldStack, diagonalDeadline);
        }
        if (result == null) {
            result = findLegacyBelowPlacement(player, yaw, currentPitch, heldStack,
                    diagonalDeadline);
        }
        return result;
    }

    Candidate findDirectCursorRayPlacement(EntityPlayerSP player, float yaw, float pitch,
                                           ItemStack heldStack) {
        MovingObjectPosition hit = RotationUtil.rayTrace(yaw, pitch, reach(), 1.0F);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || hit.sideHit == EnumFacing.DOWN) {
            return null;
        }
        BlockPos support = hit.getBlockPos();
        BlockPos target = support.offset(hit.sideHit);
        if (!isPlacementTargetAvailable(player, target)
                || !isSupportAvailable(support)
                || shouldRejectStraightSideSwitch(player, target, hit.sideHit)) {
            return null;
        }
        return new Candidate(target, support, hit.sideHit, hit.hitVec, yaw, pitch);
    }

    Candidate findStraightPreviousVisibleFaceFallback(EntityPlayerSP player, float yaw,
                                                       float currentPitch, ItemStack heldStack,
                                                       long deadlineMs) {
        if (lastSupportPos == null || lastSupportFace == null || expired(deadlineMs)) {
            return null;
        }
        BlockPos target = lastSupportPos.offset(lastSupportFace);
        if (!isPlacementTargetAvailable(player, target)) {
            return null;
        }
        return findPitchPlacementForTarget(player, yaw, currentPitch, target,
                lastSupportPos, lastSupportFace, deadlineMs, true, true);
    }

    Candidate findStraightGroundExceptionCandidate(EntityPlayerSP player, float yaw,
                                                    float currentPitch, ItemStack heldStack,
                                                    long deadlineMs) {
        int previousForcedMode = forcedModeCheck;
        forcedModeCheck = 2;
        long fallbackDeadline = Math.max(deadlineMs, System.currentTimeMillis() + 4L);
        try {
            Candidate candidate = findBelowPlacementForSupport(player, yaw, currentPitch,
                    heldStack, null, null, deadlineMs);
            if (candidate == null) {
                candidate = findBelowPlayerAirborneFallback(player, yaw, currentPitch,
                        heldStack, fallbackDeadline);
            }
            if (candidate == null) {
                candidate = findNearestSupportToBelowPlayerFallback(player, yaw, currentPitch,
                        heldStack, fallbackDeadline);
            }
            return candidate;
        } finally {
            forcedModeCheck = previousForcedMode;
        }
    }

    Candidate findStraightLegacyLaneFallback(EntityPlayerSP player, float yaw,
                                             float currentPitch, ItemStack heldStack,
                                             long deadlineMs) {
        Set<BlockPos> targets = new LinkedHashSet<BlockPos>();
        int currentY = getCurrentBelowTargetY(player);
        int strictY = getStrictBelowTargetY(player);
        int previousY = getPreviousBelowTargetY(player);
        int upwardY = isStraightAscendingContext(player) ? currentY + 1 : Integer.MIN_VALUE;
        addCursorLaneTargets(targets, player, yaw, currentPitch, currentY);
        if (strictY != currentY) {
            addCursorLaneTargets(targets, player, yaw, currentPitch, strictY);
        }
        if (previousY != Integer.MIN_VALUE && previousY != currentY && previousY != strictY) {
            addCursorLaneTargets(targets, player, yaw, currentPitch, previousY);
        }
        if (upwardY != Integer.MIN_VALUE && upwardY != currentY && upwardY != strictY
                && upwardY != previousY) {
            addCursorLaneTargets(targets, player, yaw, currentPitch, upwardY);
        }
        for (BlockPos target : targets) {
            if (expired(deadlineMs)) {
                return null;
            }
            if (!isBasePlacementTargetAvailable(player, target)) {
                continue;
            }
            if (!isStraightLaneTargetAvailable(player, target, currentY, strictY,
                    previousY, upwardY)) {
                continue;
            }
            Candidate candidate = findLegacyPitchPlacementForTarget(player, yaw, currentPitch,
                    target, null, deadlineMs);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    Candidate findBelowPlayerAirborneFallback(EntityPlayerSP player, float yaw,
                                               float currentPitch, ItemStack heldStack,
                                               long deadlineMs) {
        int y = getCurrentBelowTargetY(player);
        boolean diagonal = isDiagonalMovementContext(player);
        boolean allowNonCursorTarget = diagonal || !player.onGround;
        List<BlockPos> targets = getBelowPlayerFallbackEndpoints(player, yaw, currentPitch, y);
        for (BlockPos target : targets) {
            if (expired(deadlineMs)) {
                return null;
            }
            if (!isPlacementTargetAvailable(player, target)) {
                continue;
            }
            if (!allowNonCursorTarget && !isCursorOrBelowPlayerTarget(player, target,
                    yaw, currentPitch)) {
                continue;
            }
            Candidate candidate = findPitchPlacementForTarget(player, yaw, currentPitch,
                    target, null, null, deadlineMs, false, allowNonCursorTarget);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private List<BlockPos> getBelowPlayerFallbackEndpoints(EntityPlayerSP player, float yaw,
                                                           float pitch, int targetY) {
        List<BlockPos> endpoints = new ArrayList<BlockPos>();
        if (!isDiagonalMovementContext(player)) {
            if (!player.onGround) {
                addUnique(endpoints, new BlockPos(player.posX, targetY, player.posZ));
                addUnique(endpoints, new BlockPos(player.posX + player.motionX, targetY,
                        player.posZ + player.motionZ));
                addUnique(endpoints, new BlockPos(player.posX + player.motionX * 1.7D, targetY,
                        player.posZ + player.motionZ * 1.7D));
            }
            addUnique(endpoints, cursorStartTargetAtY(player, yaw, pitch, targetY));
            addUnique(endpoints, cursorPlacedTargetFromRay(yaw, pitch, targetY));
            addUnique(endpoints, cursorTargetAtY(player, yaw, pitch, targetY));
            return endpoints;
        }
        addUnique(endpoints, new BlockPos(player.posX + player.motionX, targetY,
                player.posZ + player.motionZ));
        addUnique(endpoints, new BlockPos(player.posX + player.motionX * 1.7D, targetY,
                player.posZ + player.motionZ * 1.7D));
        return endpoints;
    }

    Candidate findNearestSupportToBelowPlayerFallback(EntityPlayerSP player, float yaw,
                                                       float currentPitch, ItemStack heldStack,
                                                       long deadlineMs) {
        if (expired(deadlineMs)) {
            return null;
        }
        BlockPos belowPlayer = new BlockPos(player.posX, getCurrentBelowTargetY(player), player.posZ);
        if (hasDirectSupportNeighbor(belowPlayer)) {
            return null;
        }
        BlockPos searchOrigin = getPathStartTowardBelowPlayer(player, belowPlayer);
        BlockPos nearestStart = findNearestSupportedReplaceableTarget(player, searchOrigin,
                belowPlayer, deadlineMs);
        if (nearestStart == null) {
            return null;
        }
        List<BlockPos> requiredPath = rasterizeHorizontalLineAtY(nearestStart, belowPlayer,
                belowPlayer.getY(), 64);
        for (int i = requiredPath.size() - 1; i >= 0; i--) {
            if (expired(deadlineMs)) {
                return null;
            }
            BlockPos pathPos = requiredPath.get(i);
            if (!isPlacementTargetAvailable(player, pathPos)) {
                continue;
            }
            Candidate candidate = findPitchPlacementForTarget(player, yaw, currentPitch,
                    pathPos, null, null, deadlineMs, false, true);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    Candidate findPreviousBlockAirborneFallback(EntityPlayerSP player, float yaw,
                                                float currentPitch, ItemStack heldStack,
                                                long deadlineMs) {
        BlockPos support = lastSupportPos != null ? lastSupportPos : lastPlacedPos;
        EnumFacing face = lastSupportFace;
        if (support == null || face == null || expired(deadlineMs)) {
            return null;
        }
        BlockPos target = support.offset(face);
        if (!isPlacementTargetAvailable(player, target)) {
            return null;
        }
        return findPitchPlacementForTarget(player, yaw, currentPitch, target,
                support, face, deadlineMs, false, isDiagonalMovementContext(player));
    }

    Candidate findLegacyBelowPlacement(EntityPlayerSP player, float yaw, float currentPitch,
                                       ItemStack heldStack, long deadlineMs) {
        for (BlockPos target : messageStyleBelowTargets(player)) {
            if (expired(deadlineMs)) {
                return null;
            }
            if (!isPlacementTargetAvailable(player, target)) {
                continue;
            }
            Candidate candidate = findLegacyPitchPlacementForTarget(player, yaw, currentPitch,
                    target, lastPlacedPos, deadlineMs);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    Candidate findBelowPlacementForSupport(EntityPlayerSP player, float yaw,
                                           float currentPitch, ItemStack heldStack,
                                           BlockPos preferredSupport, EnumFacing preferredFace,
                                           long deadlineMs) {
        List<BlockPos> targets = new ArrayList<BlockPos>();
        addUnique(targets, currentBelow(player));
        addUnique(targets, new BlockPos(player.posX + player.motionX, currentBelow(player).getY(),
                player.posZ + player.motionZ));
        addUnique(targets, new BlockPos(player.posX + player.motionX * 1.7D,
                currentBelow(player).getY(), player.posZ + player.motionZ * 1.7D));
        for (BlockPos target : targets) {
            if (expired(deadlineMs)) {
                return null;
            }
            Candidate candidate = findPitchPlacementForTarget(player, yaw, currentPitch,
                    target, preferredSupport, preferredFace, deadlineMs, false);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    Candidate findLegacyPitchPlacementForTarget(EntityPlayerSP player, float yaw,
                                                float currentPitch, BlockPos target,
                                                BlockPos preferredSupport, long deadlineMs) {
        float base = MathHelper.clamp_float(currentPitch, 40.0F, 89.0F);
        for (int offset = 0; offset <= 49; offset++) {
            if (expired(deadlineMs)) {
                return null;
            }
            Candidate up = tryLegacyPitch(player, yaw, base + offset, target, preferredSupport);
            if (up != null) {
                return up;
            }
            if (offset > 0) {
                Candidate down = tryLegacyPitch(player, yaw, base - offset, target, preferredSupport);
                if (down != null) {
                    return down;
                }
            }
        }
        return null;
    }

    Candidate findPitchPlacementForTarget(EntityPlayerSP player, float yaw, float currentPitch,
                                          BlockPos target, BlockPos preferredSupport,
                                          EnumFacing preferredFace, long deadlineMs,
                                          boolean requirePreviousFace) {
        return findPitchPlacementForTarget(player, yaw, currentPitch, target, preferredSupport,
                preferredFace, deadlineMs, requirePreviousFace, false);
    }

    private Candidate findPitchPlacementForTarget(EntityPlayerSP player, float yaw,
                                                  float currentPitch, BlockPos target,
                                                  BlockPos preferredSupport,
                                                  EnumFacing preferredFace, long deadlineMs,
                                                  boolean requirePreviousFace,
                                                  boolean allowNonCursorTarget) {
        if (!isPlacementTargetAvailable(player, target)) {
            return null;
        }
        boolean effectiveAllowNonCursorTarget = allowNonCursorTarget
                || shouldAllowPlayerOneNonCursorTarget(player, target);
        if (!effectiveAllowNonCursorTarget
                && !isCursorOrBelowPlayerTarget(player, target, yaw, currentPitch)) {
            return null;
        }
        Candidate best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (EnumFacing face : allowedFaces()) {
            if (expired(deadlineMs)) {
                break;
            }
            if (preferredFace != null && face != preferredFace) {
                continue;
            }
            if (shouldRejectStraightSideSwitch(player, target, face)) {
                continue;
            }
            BlockPos support = target.offset(face.getOpposite());
            if (preferredSupport != null && !preferredSupport.equals(support)) {
                continue;
            }
            if (!isSupportAvailable(support) || !isWithinReach(player, support)) {
                continue;
            }
            for (double primary : EXTENDED_FACE_HIT_OFFSETS) {
                for (double secondary : EXTENDED_FACE_HIT_OFFSETS) {
                    if (expired(deadlineMs)) {
                        break;
                    }
                    Vec3 hitVec = supportFaceHitVec(support, face, primary, secondary);
                    Candidate candidate = buildCandidate(player, target, support, face,
                            hitVec, yaw, requirePreviousFace);
                    if (candidate == null) {
                        continue;
                    }
                    double score = Math.abs(candidate.pitch - currentPitch)
                            + Math.abs(primary - 0.5D) * 2.0D
                            + Math.abs(secondary - 0.5D) * 2.0D
                            + (face == EnumFacing.UP ? 0.0D : 0.35D)
                            + getStraightSideSwitchPenalty(player, face);
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        if (best == null && preferredSupport != null && preferredFace != null) {
            return findRayAlignedPitchCandidate(player, yaw, currentPitch, target,
                    preferredSupport, preferredFace, deadlineMs);
        }
        return best;
    }

    Candidate findRayAlignedPitchCandidate(EntityPlayerSP player, float yaw, float currentPitch,
                                            BlockPos target, BlockPos support, EnumFacing face,
                                            long deadlineMs) {
        float base = MathHelper.clamp_float(currentPitch, 40.0F, 89.0F);
        for (int offset = 0; offset <= 49; offset++) {
            if (expired(deadlineMs)) {
                return null;
            }
            Candidate candidate = tryRayAlignedPitch(player, yaw, base + offset,
                    target, support, face);
            if (candidate != null) {
                return candidate;
            }
            if (offset > 0) {
                candidate = tryRayAlignedPitch(player, yaw, base - offset,
                        target, support, face);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    void recordAccepted(Candidate candidate) {
        if (candidate == null) {
            return;
        }
        lastPlacedPos = candidate.target;
        lastSupportPos = candidate.support;
        lastSupportFace = candidate.face;
        rejectedTargets.remove(candidate.target);
        cancelledGhostBlocks.remove(candidate.target);
    }

    void discardInvalidContinuation() {
        if (lastPlacedPos != null && !isSupportAvailable(lastPlacedPos)) {
            clearContinuation();
        }
    }

    private void clearContinuation() {
        lastPlacedPos = null;
        lastSupportPos = null;
        lastSupportFace = null;
    }

    void updateServerPosition(double x, double y, double z) {
        hasLastSentServerPos = true;
        lastSentServerPosX = x;
        lastSentServerPosY = y;
        lastSentServerPosZ = z;
    }

    void markCancelledGhost(BlockPos target) {
        if (target != null) {
            cancelledGhostBlocks.add(target);
        }
    }

    void clearCancelledGhost(BlockPos target) {
        if (target != null) {
            cancelledGhostBlocks.remove(target);
        }
    }

    void markRejected(BlockPos target, int tick) {
        if (target != null) {
            rejectedTargets.put(target, tick);
        }
    }

    void clearGhost(BlockPos position) {
        rejectedTargets.remove(position);
        cancelledGhostBlocks.remove(position);
    }

    void reset() {
        activationAnchor = null;
        lastPlacedPos = null;
        lastSupportPos = null;
        lastSupportFace = null;
        rejectedTargets.clear();
        cancelledGhostBlocks.clear();
        hasLastSentServerPos = false;
        laneReady = false;
        forcedModeCheck = 0;
        travelX = 0;
        travelZ = 0;
    }

    private BlockPos getPathStartTowardBelowPlayer(EntityPlayerSP player, BlockPos fallback) {
        if (lastPlacedPos != null && lastPlacedPos.getY() == fallback.getY()) {
            return lastPlacedPos;
        }
        BlockPos motion = new BlockPos(player.posX + player.motionX * 1.7D,
                fallback.getY(), player.posZ + player.motionZ * 1.7D);
        if (isPlacementTargetAvailable(player, motion)) {
            return motion;
        }
        motion = new BlockPos(player.posX + player.motionX,
                fallback.getY(), player.posZ + player.motionZ);
        return isPlacementTargetAvailable(player, motion) ? motion : fallback;
    }

    private BlockPos findNearestSupportedReplaceableTarget(EntityPlayerSP player,
                                                           BlockPos origin,
                                                           BlockPos belowPlayer,
                                                           long deadlineMs) {
        if (origin == null || belowPlayer == null || expired(deadlineMs)) {
            return null;
        }
        for (int radius = 0; radius <= 3; radius++) {
            BlockPos best = null;
            double bestScore = Double.POSITIVE_INFINITY;
            for (int dx = -radius; dx <= radius; dx++) {
                int dzAbs = radius - Math.abs(dx);
                BlockPos positive = new BlockPos(origin.getX() + dx, belowPlayer.getY(),
                        origin.getZ() + dzAbs);
                best = chooseNearestSupported(player, belowPlayer, origin, positive, best, deadlineMs);
                if (dzAbs != 0) {
                    BlockPos negative = new BlockPos(origin.getX() + dx, belowPlayer.getY(),
                            origin.getZ() - dzAbs);
                    best = chooseNearestSupported(player, belowPlayer, origin, negative, best, deadlineMs);
                }
                if (best != null) {
                    bestScore = scoreAirPathStartCandidate(best, belowPlayer, origin);
                }
            }
            if (best != null && bestScore < Double.POSITIVE_INFINITY) {
                return best;
            }
        }
        return null;
    }

    private BlockPos chooseNearestSupported(EntityPlayerSP player, BlockPos belowPlayer,
                                            BlockPos origin, BlockPos candidate,
                                            BlockPos currentBest, long deadlineMs) {
        if (expired(deadlineMs) || !isPlacementTargetAvailable(player, candidate)
                || !hasDirectSupportNeighbor(candidate)) {
            return currentBest;
        }
        if (currentBest == null || scoreAirPathStartCandidate(candidate, belowPlayer, origin)
                < scoreAirPathStartCandidate(currentBest, belowPlayer, origin)) {
            return candidate;
        }
        return currentBest;
    }

    private double scoreAirPathStartCandidate(BlockPos candidate, BlockPos belowPlayer,
                                              BlockPos origin) {
        double goalDx = candidate.getX() - belowPlayer.getX();
        double goalDz = candidate.getZ() - belowPlayer.getZ();
        double originDx = candidate.getX() - origin.getX();
        double originDz = candidate.getZ() - origin.getZ();
        return (goalDx * goalDx + goalDz * goalDz) * 4.0D
                + originDx * originDx + originDz * originDz;
    }

    private List<BlockPos> rasterizeHorizontalLineAtY(BlockPos start, BlockPos end, int y,
                                                       int maxSteps) {
        List<BlockPos> line = new ArrayList<BlockPos>();
        int x0 = start.getX();
        int z0 = start.getZ();
        int x1 = end.getX();
        int z1 = end.getZ();
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = Integer.compare(x1, x0);
        int sz = Integer.compare(z1, z0);
        int movedX = 0;
        int movedZ = 0;
        for (int steps = 0; steps < maxSteps; steps++) {
            line.add(new BlockPos(x0, y, z0));
            if ((x0 == x1 && z0 == z1) || (movedX >= dx && movedZ >= dz)) {
                break;
            }
            if (movedX >= dx) {
                z0 += sz;
                movedZ++;
            } else if (movedZ >= dz) {
                x0 += sx;
                movedX++;
            } else if ((1 + 2 * movedX) * dz < (1 + 2 * movedZ) * dx) {
                x0 += sx;
                movedX++;
            } else {
                z0 += sz;
                movedZ++;
            }
        }
        return line;
    }

    private Candidate tryLegacyPitch(EntityPlayerSP player, float yaw, float pitch,
                                     BlockPos target, BlockPos preferredSupport) {
        if (pitch < 40.0F || pitch > 89.0F) {
            return null;
        }
        MovingObjectPosition hit = RotationUtil.rayTrace(yaw, pitch, reach(), 1.0F);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || hit.sideHit == EnumFacing.DOWN) {
            return null;
        }
        if (preferredSupport != null && !preferredSupport.equals(hit.getBlockPos())) {
            return null;
        }
        if (!target.equals(hit.getBlockPos().offset(hit.sideHit))) {
            return null;
        }
        return new Candidate(target, hit.getBlockPos(), hit.sideHit, hit.hitVec, yaw, pitch);
    }

    private Candidate tryRayAlignedPitch(EntityPlayerSP player, float yaw, float pitch,
                                         BlockPos target, BlockPos support, EnumFacing face) {
        if (pitch < 40.0F || pitch > 89.0F) {
            return null;
        }
        MovingObjectPosition hit = RotationUtil.rayTrace(yaw, pitch, reach(), 1.0F);
        if (hit == null || !support.equals(hit.getBlockPos()) || face != hit.sideHit
                || !target.equals(support.offset(face))) {
            return null;
        }
        return new Candidate(target, support, face, hit.hitVec, yaw, pitch);
    }

    private Candidate buildCandidate(EntityPlayerSP player, BlockPos target, BlockPos support,
                                     EnumFacing face, Vec3 hitVec, float yaw,
                                     boolean requirePreviousFace) {
        if (requirePreviousFace && lastSupportFace != null && face != lastSupportFace) {
            return null;
        }
        Vec3 eyes = player.getPositionEyes(1.0F);
        double dx = hitVec.xCoord - eyes.xCoord;
        double dy = hitVec.yCoord - eyes.yCoord;
        double dz = hitVec.zCoord - eyes.zCoord;
        float pitch = MathHelper.clamp_float((float) -Math.toDegrees(
                Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -89.0F, 89.0F);
        Candidate candidate = new Candidate(target, support, face, hitVec, yaw, pitch);
        return matchesRay(candidate, yaw, pitch) ? candidate : null;
    }

    private boolean matchesRay(Candidate candidate, float yaw, float pitch) {
        MovingObjectPosition hit = RotationUtil.rayTrace(yaw, pitch, reach(), 1.0F);
        return hit != null
                && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && candidate.support.equals(hit.getBlockPos())
                && candidate.face == hit.sideHit
                && candidate.target.equals(hit.getBlockPos().offset(hit.sideHit));
    }

    private boolean isPlacementTargetAvailable(EntityPlayerSP player, BlockPos target) {
        return isBasePlacementTargetAvailable(player, target) && isStrictOneBelowPlayer(player, target);
    }

    private boolean isBasePlacementTargetAvailable(EntityPlayerSP player, BlockPos target) {
        return target != null
                && isStraightTellyTarget(target)
                && !isRejectedTarget(target, player.ticksExisted)
                && !doesPlacementIntersectPlayer(player, target)
                && BlockUtil.isReplaceable(target);
    }

    private boolean isStrictOneBelowPlayer(EntityPlayerSP player, BlockPos target) {
        if (target == null) {
            return false;
        }
        int currentY = getCurrentBelowTargetY(player);
        if (target.getY() == currentY) {
            return true;
        }
        int strictY = getStrictBelowTargetY(player);
        if (target.getY() == strictY) {
            return true;
        }
        int previousY = getPreviousBelowTargetY(player);
        if (previousY != Integer.MIN_VALUE && target.getY() == previousY) {
            return true;
        }
        return isStraightAscendingContext(player) && target.getY() == currentY + 1;
    }

    private boolean doesPlacementIntersectPlayer(EntityPlayerSP player, BlockPos target) {
        AxisAlignedBB blockBox = new AxisAlignedBB(target.getX(), target.getY(), target.getZ(),
                target.getX() + 1.0D, target.getY() + 1.0D, target.getZ() + 1.0D);
        if (player.getEntityBoundingBox().intersectsWith(blockBox)
                || isInsidePlayerPositionCell(target, player.posX, player.posY, player.posZ)) {
            return true;
        }
        if (!shouldUseHistoricalPlayerCollisionChecks(player, target)) {
            return false;
        }
        if ((player.prevPosX != player.posX || player.prevPosY != player.posY
                || player.prevPosZ != player.posZ)
                && (playerBoxAt(player, player.prevPosX, player.prevPosY, player.prevPosZ)
                .intersectsWith(blockBox)
                || isInsidePlayerPositionCell(target, player.prevPosX,
                player.prevPosY, player.prevPosZ))) {
            return true;
        }
        return hasLastSentServerPos
                && (lastSentServerPosX != player.posX || lastSentServerPosY != player.posY
                || lastSentServerPosZ != player.posZ)
                && (playerBoxAt(player, lastSentServerPosX, lastSentServerPosY,
                lastSentServerPosZ).intersectsWith(blockBox)
                || isInsidePlayerPositionCell(target, lastSentServerPosX,
                lastSentServerPosY, lastSentServerPosZ));
    }

    private boolean shouldUseHistoricalPlayerCollisionChecks(EntityPlayerSP player,
                                                               BlockPos target) {
        return player.onGround && (target == null || target.getY() > currentBelow(player).getY());
    }

    private AxisAlignedBB playerBoxAt(EntityPlayerSP player, double x, double y, double z) {
        double halfWidth = player.width * 0.5D;
        return new AxisAlignedBB(x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + player.height, z + halfWidth);
    }

    private boolean isInsidePlayerPositionCell(BlockPos target, double x, double y, double z) {
        int playerX = MathHelper.floor_double(x);
        int playerY = MathHelper.floor_double(y);
        int playerZ = MathHelper.floor_double(z);
        return target.getX() == playerX && target.getZ() == playerZ
                && (target.getY() == playerY || target.getY() == playerY + 1);
    }

    private boolean isWithinReach(EntityPlayerSP player, BlockPos support) {
        Vec3 eyes = player.getPositionEyes(1.0F);
        double x = Math.max(support.getX(), Math.min(eyes.xCoord, support.getX() + 1.0D));
        double y = Math.max(support.getY(), Math.min(eyes.yCoord, support.getY() + 1.0D));
        double z = Math.max(support.getZ(), Math.min(eyes.zCoord, support.getZ() + 1.0D));
        double dx = eyes.xCoord - x;
        double dy = eyes.yCoord - y;
        double dz = eyes.zCoord - z;
        return dx * dx + dy * dy + dz * dz <= reach() * reach();
    }

    private boolean isSupportAvailable(BlockPos support) {
        return support != null
                && !cancelledGhostBlocks.contains(support)
                && !BlockUtil.isReplaceable(support)
                && !BlockUtil.isInteractable(support);
    }

    private boolean hasDirectSupportNeighbor(BlockPos target) {
        for (EnumFacing face : ALL_PLACE_FACES) {
            if (isSupportAvailable(target.offset(face.getOpposite()))) {
                return true;
            }
        }
        return false;
    }

    private double getStraightSideSwitchPenalty(EntityPlayerSP player, EnumFacing face) {
        if (!laneReady || lastSupportFace == null || lastSupportFace.getIndex() < 2
                || face.getIndex() < 2 || face == lastSupportFace) {
            return 0.0D;
        }
        return 0.8D;
    }

    private boolean shouldRejectStraightSideSwitch(EntityPlayerSP player, BlockPos target,
                                                   EnumFacing face) {
        if (!laneReady || target == null || lastSupportFace == null
                || lastSupportFace.getIndex() < 2 || face.getIndex() < 2
                || face == lastSupportFace || isNearStraightSupportEdge(player)) {
            return false;
        }
        BlockPos laneSupport = target.offset(lastSupportFace.getOpposite());
        return isSupportAvailable(laneSupport) && isWithinReach(player, laneSupport);
    }

    private boolean isNearStraightSupportEdge(EntityPlayerSP player) {
        if (lastSupportPos == null || lastSupportFace == null
                || lastSupportFace.getIndex() < 2) {
            return false;
        }
        double localX = player.posX - lastSupportPos.getX();
        double localZ = player.posZ - lastSupportPos.getZ();
        if (isPastStraightSupportEdgeThreshold(lastSupportFace, localX, localZ)) {
            return true;
        }
        double motionSq = player.motionX * player.motionX + player.motionZ * player.motionZ;
        if (motionSq < 1.0E-4D || !isMovingTowardStraightSupportEdge(
                lastSupportFace, player.motionX, player.motionZ)) {
            return false;
        }
        return isPastStraightSupportEdgeThreshold(lastSupportFace,
                localX + player.motionX * 1.45D,
                localZ + player.motionZ * 1.45D);
    }

    private boolean isPastStraightSupportEdgeThreshold(EnumFacing face,
                                                        double localX, double localZ) {
        if (face == EnumFacing.EAST) return localX >= 0.52D;
        if (face == EnumFacing.WEST) return localX <= 0.48D;
        if (face == EnumFacing.SOUTH) return localZ >= 0.52D;
        if (face == EnumFacing.NORTH) return localZ <= 0.48D;
        return false;
    }

    private boolean isMovingTowardStraightSupportEdge(EnumFacing face,
                                                       double motionX, double motionZ) {
        if (face == EnumFacing.EAST) return motionX > 0.0D;
        if (face == EnumFacing.WEST) return motionX < 0.0D;
        if (face == EnumFacing.SOUTH) return motionZ > 0.0D;
        if (face == EnumFacing.NORTH) return motionZ < 0.0D;
        return false;
    }

    private boolean isCursorOrBelowPlayerTarget(EntityPlayerSP player, BlockPos target,
                                                float yaw, float pitch) {
        if (target == null) {
            return false;
        }
        if (isDiagonalMovementContext(player)) {
            int strictY = getStrictBelowTargetY(player);
            return isBelowPlayerTargetAtY(player, target, strictY)
                    || isBelowPlayerTargetAtY(player, target, getCurrentBelowTargetY(player));
        }
        int currentY = getCurrentBelowTargetY(player);
        if (target.equals(cursorStartTargetAtY(player, yaw, pitch, currentY))
                || target.equals(cursorPlacedTargetFromRay(yaw, pitch, currentY))
                || target.equals(cursorTargetAtY(player, yaw, pitch, currentY))) {
            return true;
        }
        int strictY = getStrictBelowTargetY(player);
        if (strictY != currentY
                && (target.equals(cursorStartTargetAtY(player, yaw, pitch, strictY))
                || target.equals(cursorPlacedTargetFromRay(yaw, pitch, strictY)))) {
            return true;
        }
        return isCursorInsideTargetAtY(player, target, yaw, pitch, currentY)
                || target.equals(cursorTargetAtY(player, yaw, pitch, currentY));
    }

    private boolean isBelowPlayerTargetAtY(EntityPlayerSP player, BlockPos target, int targetY) {
        return target.equals(new BlockPos(player.posX + player.motionX, targetY,
                player.posZ + player.motionZ))
                || target.equals(new BlockPos(player.posX + player.motionX * 1.7D, targetY,
                player.posZ + player.motionZ * 1.7D));
    }

    private boolean isCursorDirectedAtBlock(float yaw, float pitch) {
        MovingObjectPosition hit = RotationUtil.rayTrace(yaw, pitch, reach(), 1.0F);
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private BlockPos cursorStartTargetAtY(EntityPlayerSP player, float yaw, float pitch, int targetY) {
        Vec3 intersection = cursorIntersectionAtY(player, yaw, pitch, targetY);
        Vec3 look = lookVector(yaw, pitch);
        if (intersection == null || look == null) {
            return null;
        }
        return new BlockPos(intersection.xCoord - look.xCoord * 0.03D, targetY,
                intersection.zCoord - look.zCoord * 0.03D);
    }

    private BlockPos cursorTargetAtY(EntityPlayerSP player, float yaw, float pitch, int targetY) {
        Vec3 intersection = cursorIntersectionAtY(player, yaw, pitch, targetY);
        return intersection == null ? null : new BlockPos(intersection.xCoord, targetY,
                intersection.zCoord);
    }

    private BlockPos cursorPlacedTargetFromRay(float yaw, float pitch, int targetY) {
        MovingObjectPosition hit = RotationUtil.rayTrace(yaw, pitch, reach(), 1.0F);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        BlockPos target = hit.getBlockPos().offset(hit.sideHit);
        return target.getY() == targetY ? target : null;
    }

    private Vec3 cursorIntersectionAtY(EntityPlayerSP player, float yaw, float pitch, int targetY) {
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = lookVector(yaw, pitch);
        if (look == null || Math.abs(look.yCoord) < 1.0E-4D) {
            return null;
        }
        double t = (targetY + 0.5D - eyes.yCoord) / look.yCoord;
        return t <= 0.0D ? null : new Vec3(eyes.xCoord + look.xCoord * t,
                targetY + 0.5D, eyes.zCoord + look.zCoord * t);
    }

    private Vec3 lookVector(float yaw, float pitch) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRadians);
        return new Vec3(-Math.sin(yawRadians) * cosPitch,
                -Math.sin(pitchRadians), Math.cos(yawRadians) * cosPitch);
    }

    private boolean isCursorInsideTargetAtY(EntityPlayerSP player, BlockPos target,
                                            float yaw, float pitch, int targetY) {
        Vec3 intersection = cursorIntersectionAtY(player, yaw, pitch, targetY);
        return intersection != null && target.getY() == targetY
                && intersection.xCoord >= target.getX() - 1.0E-6D
                && intersection.xCoord <= target.getX() + 1.0D + 1.0E-6D
                && intersection.zCoord >= target.getZ() - 1.0E-6D
                && intersection.zCoord <= target.getZ() + 1.0D + 1.0E-6D;
    }

    private boolean isDiagonalMovementContext(EntityPlayerSP player) {
        return getConditionModeCheck(player) == 2;
    }

    private int getConditionModeCheck(EntityPlayerSP player) {
        if (forcedModeCheck != 0) {
            return forcedModeCheck;
        }
        // Telly runtime 始终沿 beginLane 锁定的直线车道运行；阶段中的输入分量
        // 不能重新解释为搜索模式，否则会把固定车道错误切换为对角 fallback。
        return laneReady ? 1 : detectedModeCheck(player);
    }

    private int detectedModeCheck(EntityPlayerSP player) {
        float forward = Math.abs(player.moveForward);
        float strafe = Math.abs(player.moveStrafing);
        return forward >= 0.08F && strafe >= 0.08F ? 2 : 1;
    }

    private boolean isStraightTellyTarget(BlockPos target) {
        if (!laneReady) {
            return true;
        }
        int lane = travelX != 0 ? target.getZ() : target.getX();
        return lane == bridgeLaneBlock && progress(target) >= bridgeStartProgress;
    }

    private int progress(BlockPos position) {
        return position.getX() * travelX + position.getZ() * travelZ;
    }

    private boolean isStraightPreviousTickCenterOnGroundSupport(EntityPlayerSP player) {
        BlockPos previous = new BlockPos(player.prevPosX,
                getPreviousBelowTargetY(player), player.prevPosZ);
        return !BlockUtil.isReplaceable(previous);
    }

    private boolean isStraightCenterBelowAir(EntityPlayerSP player) {
        return BlockUtil.isReplaceable(new BlockPos(player.posX,
                getCurrentBelowTargetY(player), player.posZ));
    }

    private boolean shouldAllowPlayerOneNonCursorTarget(EntityPlayerSP player, BlockPos target) {
        if (target == null || isDiagonalMovementContext(player) || player.onGround) {
            return false;
        }
        if (!isPlayerHitboxFullyInsideSingleBlockColumn(player)
                || lastSupportPos == null || lastSupportFace == null
                || lastSupportFace == EnumFacing.DOWN
                || !isSupportAvailable(lastSupportPos)
                || !isWithinReach(player, lastSupportPos)) {
            return false;
        }
        BlockPos continuationTarget = lastSupportPos.offset(lastSupportFace);
        if (!target.equals(continuationTarget)) {
            return false;
        }
        int currentY = getCurrentBelowTargetY(player);
        int strictY = getStrictBelowTargetY(player);
        if (target.getY() != currentY && target.getY() != strictY) {
            return false;
        }
        BlockPos feetBelow = new BlockPos(player.posX, target.getY(), player.posZ);
        return Math.abs(target.getX() - feetBelow.getX())
                + Math.abs(target.getZ() - feetBelow.getZ()) <= 1;
    }

    private boolean isPlayerHitboxFullyInsideSingleBlockColumn(EntityPlayerSP player) {
        double half = player.width * 0.5D;
        int minX = MathHelper.floor_double(player.posX - half + 1.0E-4D);
        int maxX = MathHelper.floor_double(player.posX + half - 1.0E-4D);
        if (minX != maxX) {
            return false;
        }
        int minZ = MathHelper.floor_double(player.posZ - half + 1.0E-4D);
        int maxZ = MathHelper.floor_double(player.posZ + half - 1.0E-4D);
        return minZ == maxZ;
    }

    private int getCurrentBelowTargetY(EntityPlayerSP player) {
        return MathHelper.floor_double(getStableBelowReferenceY(player)) - 1;
    }

    private int getStrictBelowTargetY(EntityPlayerSP player) {
        if (isDiagonalMovementContext(player)) {
            return getCurrentBelowTargetY(player);
        }
        double projectedY = getStableBelowReferenceY(player);
        if (!player.onGround && player.motionY < -0.12D) {
            projectedY = player.posY + player.motionY * 0.75D;
        }
        return MathHelper.floor_double(projectedY) - 1;
    }

    private int getPreviousBelowTargetY(EntityPlayerSP player) {
        return MathHelper.floor_double(player.prevPosY) - 1;
    }

    private boolean isStraightAscendingContext(EntityPlayerSP player) {
        if (getConditionModeCheck(player) != 1) {
            return false;
        }
        return player.motionY > 0.0D || player.posY > player.prevPosY + 1.0E-4D;
    }

    private double getStableBelowReferenceY(EntityPlayerSP player) {
        double referenceY = player.posY;
        if (!player.onGround && player.motionY > -0.12D && player.motionY <= 0.0D) {
            referenceY = Math.max(referenceY, player.prevPosY);
        }
        return referenceY;
    }

    private boolean isStraightLaneTargetAvailable(EntityPlayerSP player, BlockPos target,
                                                   int currentY, int strictY, int previousY,
                                                   int upwardY) {
        if (!isBasePlacementTargetAvailable(player, target)) {
            return false;
        }
        int targetY = target.getY();
        if (targetY == currentY || targetY == strictY) {
            return true;
        }
        if (previousY != Integer.MIN_VALUE && targetY == previousY) {
            return true;
        }
        return upwardY != Integer.MIN_VALUE && targetY == upwardY;
    }

    private void addCursorLaneTargets(Set<BlockPos> targets, EntityPlayerSP player,
                                      float yaw, float pitch, int targetY) {
        addUnique(targets, cursorStartTargetAtY(player, yaw, pitch, targetY));
        addUnique(targets, cursorPlacedTargetFromRay(yaw, pitch, targetY));
        addUnique(targets, cursorTargetAtY(player, yaw, pitch, targetY));
    }

    private static void addUnique(Set<BlockPos> targets, BlockPos target) {
        if (target != null) {
            targets.add(target);
        }
    }

    private BlockPos currentBelow(EntityPlayerSP player) {
        return new BlockPos(player.posX, getCurrentBelowTargetY(player), player.posZ);
    }

    private void addLaneTargets(Set<BlockPos> targets, EntityPlayerSP player, int y) {
        BlockPos feet = new BlockPos(player.posX, y, player.posZ);
        targets.add(feet);
        targets.add(new BlockPos(player.posX + player.motionX, y, player.posZ + player.motionZ));
        targets.add(new BlockPos(player.posX + player.motionX * 1.7D, y,
                player.posZ + player.motionZ * 1.7D));
        if (lastPlacedPos != null) {
            targets.add(lastPlacedPos.offset(travelFacing()));
        }
    }

    private List<BlockPos> messageStyleBelowTargets(EntityPlayerSP player) {
        List<BlockPos> targets = new ArrayList<BlockPos>();
        double[] offsets = {0.0D, 0.29D, -0.29D};
        int maxY = MathHelper.floor_double(player.posY) - 1;
        for (int y = maxY; y >= maxY - 1; y--) {
            for (double x : offsets) {
                for (double z : offsets) {
                    addUnique(targets, new BlockPos(player.posX + x, y, player.posZ + z));
                }
            }
        }
        return targets;
    }

    private EnumFacing[] allowedFaces() {
        EnumFacing forward = travelFacing();
        return new EnumFacing[]{forward.rotateY(), forward.rotateYCCW(), forward,
                forward.getOpposite(), EnumFacing.UP};
    }

    private EnumFacing travelFacing() {
        if (travelX > 0) {
            return EnumFacing.EAST;
        }
        if (travelX < 0) {
            return EnumFacing.WEST;
        }
        return travelZ > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }

    private Vec3 supportFaceHitVec(BlockPos support, EnumFacing face,
                                   double primary, double secondary) {
        primary = Math.max(0.001D, Math.min(0.999D, primary));
        secondary = Math.max(0.001D, Math.min(0.999D, secondary));
        switch (face) {
            case NORTH:
                return new Vec3(support.getX() + primary, support.getY() + secondary,
                        support.getZ() + 0.001D);
            case SOUTH:
                return new Vec3(support.getX() + primary, support.getY() + secondary,
                        support.getZ() + 0.999D);
            case WEST:
                return new Vec3(support.getX() + 0.001D, support.getY() + primary,
                        support.getZ() + secondary);
            case EAST:
                return new Vec3(support.getX() + 0.999D, support.getY() + primary,
                        support.getZ() + secondary);
            case DOWN:
                return new Vec3(support.getX() + primary, support.getY() + 0.001D,
                        support.getZ() + secondary);
            default:
                return new Vec3(support.getX() + primary, support.getY() + 0.999D,
                        support.getZ() + secondary);
        }
    }

    private void pruneRejectedTargets(int tick) {
        Iterator<Map.Entry<BlockPos, Integer>> iterator = rejectedTargets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = iterator.next();
            if (tick - entry.getValue() > REJECT_TICKS || !BlockUtil.isReplaceable(entry.getKey())) {
                iterator.remove();
            }
        }
    }

    private boolean isRejectedTarget(BlockPos target, int tick) {
        Integer rejectedAt = rejectedTargets.get(target);
        return rejectedAt != null && tick - rejectedAt <= REJECT_TICKS;
    }

    private double reach() {
        return mc.playerController == null ? 4.5D : mc.playerController.getBlockReachDistance();
    }

    private static boolean expired(long deadlineMs) {
        return System.currentTimeMillis() >= deadlineMs;
    }

    private static boolean isUsable(ItemStack stack) {
        return ItemUtil.isBlock(stack);
    }

    private static void addUnique(List<BlockPos> targets, BlockPos target) {
        if (target != null && !targets.contains(target)) {
            targets.add(target);
        }
    }
}
