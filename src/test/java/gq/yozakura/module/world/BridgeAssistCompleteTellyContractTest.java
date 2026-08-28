package gq.yozakura.module.world;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BridgeAssistCompleteTellyContractTest {
    private static final String MAIN = "src/main/java/gq/yozakura/module/world/";

    @Test
    public void legacyTellyRuntimeWasCompletelyRemoved() {
        assertFalse(Files.exists(Paths.get(MAIN + "BridgeAssistTellyController.java")));
        assertFalse(Files.exists(Paths.get(MAIN + "BridgeAssistTellyPlacementSearch.java")));
        assertFalse(Files.exists(Paths.get(MAIN + "BridgeAssistTellyProgram.java")));
        assertFalse(Files.exists(Paths.get(MAIN + "BridgeAssistTellyRotation.java")));
        assertFalse(Files.exists(Paths.get(MAIN + "BridgeAssistCircleRenderer.java")));
    }

    @Test
    public void rewrittenTellyRuntimeUsesFreshImplementationBoundaries() throws IOException {
        assertTrue(Files.exists(Paths.get(MAIN + "TellyBridgeRuntime.java")));
        assertTrue(Files.exists(Paths.get(MAIN + "TellyBridgePlacementSearch.java")));
        assertTrue(Files.exists(Paths.get(MAIN + "TellyBridgeProgram.java")));
        assertTrue(Files.exists(Paths.get(MAIN + "TellyBridgeRotation.java")));

        String bridge = source(MAIN + "BridgeAssist.java");
        String stateMachine = source(MAIN + "BridgeAssistBridgeModeStateMachine.java");
        String runtime = source(MAIN + "TellyBridgeRuntime.java");

        assertTrue(stateMachine.contains("TellyBridge"));
        assertTrue(bridge.contains("TellyBridgeRuntime"));
        assertTrue(bridge.contains("tellyRuntime.onSneakInput(event)"));
        assertTrue(bridge.contains("tellyRuntime.observeModeSelection()"));
        assertTrue(bridge.contains("tellyRuntime.onUpdate(event)"));
        assertTrue(bridge.contains("tellyRuntime.onPacket(event)"));
        assertTrue(bridge.contains("tellyRuntime.onPacketAccepted(event)"));
        assertTrue(bridge.contains("tellyRuntime.onPacketWritten(event)"));
        assertTrue(bridge.contains("tellyRuntime.onRightClick(event)"));
        assertTrue(bridge.contains("tellyRuntime.ownsAttackPath()"));
        assertTrue(bridge.contains("tellyRuntime.onSafeWalk(event)"));
        assertTrue(bridge.contains("tellyRuntime.onWorldJoin()"));

        assertTrue(runtime.contains("boolean ownsAttackPath()"));
        assertTrue(runtime.contains("return selected() && running;"));
        assertTrue(runtime.contains("void observeModeSelection()"));
        assertTrue(runtime.contains("event.requestMovement("));
        assertTrue(runtime.contains("TellyBridgeMotionCurve"));
        assertTrue(runtime.contains("motionCurve.sample("));
        assertTrue(runtime.contains("mc.thePlayer.onGround"));
        assertTrue(runtime.contains("event.trySetRotation("));
        assertTrue(runtime.contains("event.setPervRotation("));
        assertTrue(runtime.contains("processAutoPlaceTick"));
        assertTrue(runtime.contains("event.getType() == EventType.POST"));
        assertTrue(runtime.contains("c08AtTickBoundary = totalAcceptedC08.get()"));
        assertTrue(runtime.contains("updateActivationPromptFade"));
        assertTrue(runtime.contains("drawActivationFaceRegion"));
        assertTrue(runtime.contains("FontLoaders.BRICOLAGE12"));
        assertTrue(runtime.contains("FontLoaders.BRICOLAGE14"));
        assertTrue(runtime.contains("FontLoaders.BRICOLAGE12.drawStringWithGlow(visual.centerText"));
        assertTrue(runtime.contains("FontLoaders.BRICOLAGE14.drawStringWithGlow(visual.labelText"));
        assertFalse(runtime.contains("FontLoaders.MONO10"));
        assertTrue(runtime.contains("applyAntiSway"));
        assertTrue(runtime.contains("refreshAdaptivePlacementAim"));
        assertTrue(runtime.contains("detectManualCameraTakeover"));
        assertTrue(runtime.contains("takeoverCameraYaw"));
        assertTrue(runtime.contains("takeoverCameraPitch"));
        assertTrue(runtime.contains("mc.thePlayer.rotationYaw - takeoverCameraYaw"));
        assertTrue(runtime.contains("mc.thePlayer.rotationPitch - takeoverCameraPitch"));
        assertFalse(runtime.contains("mc.thePlayer.rotationYaw - scriptedRotationYaw"));
        assertFalse(runtime.contains("mc.thePlayer.rotationPitch - scriptedRotationPitch"));
        assertTrue(runtime.contains("S08PacketPlayerPosLook"));
        assertTrue(runtime.contains("S23PacketBlockChange"));
        assertTrue(runtime.contains("movement.isMoving()"));
        assertTrue(runtime.contains("placementSearch.updateServerPosition("));
        assertTrue(runtime.contains("placementSearch.markCancelledGhost(target)"));
        assertTrue(runtime.contains("lastSuccessfulPlaceTick"));
        assertTrue(runtime.contains("forceSuppressTick"));
        assertTrue(runtime.contains("retryPlacement"));
        assertTrue(runtime.contains("shouldSuppressManualClicksThisTick"));
        assertTrue(runtime.contains("submittedPlacements"));
        assertTrue(runtime.contains("hasOutstandingPlacement"));
        assertTrue(runtime.contains("useSuppressed"));
        assertTrue(runtime.contains("suppressUse()"));
        assertTrue(runtime.contains("restoreUseToAutomationState()"));
        assertFalse(runtime.contains("lastSuccessfulPlaceTick = tick;"));
        assertTrue(runtime.contains("lastSuccessfulPlaceTick = accepted.tick;"));
    }

    @Test
    public void runtimeOwnsReferenceRotationAndAppliesTheWinningViewLocally() throws IOException {
        String runtime = source(MAIN + "TellyBridgeRuntime.java");
        String bridge = source(MAIN + "BridgeAssist.java");
        String packetBridge = source("src/main/java/gq/yozakura/bridge/BasePacketBridgeHandler.java");
        String forgeBridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String standaloneBridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertTrue(runtime.contains("ROTATION_PRIORITY = 5"));
        assertTrue(runtime.contains("event.trySetRotation(sample.yaw, sample.pitch, ROTATION_PRIORITY)"));
        assertTrue(runtime.contains("event.setPervRotation(sample.yaw, ROTATION_PRIORITY, false)"));
        assertTrue(runtime.contains("VisualRotationState.publish(ROTATION_SOURCE, sample.yaw, sample.pitch"));
        assertTrue(runtime.contains("void onRotationResolved(RotationResolvedEvent event)"));
        assertTrue(runtime.contains("mc.thePlayer.rotationYaw = event.getYaw()"));
        assertTrue(runtime.contains("mc.thePlayer.rotationPitch = event.getPitch()"));
        assertTrue(runtime.contains("takeoverCameraYaw = event.getYaw()"));
        assertTrue(runtime.contains("takeoverCameraPitch = event.getPitch()"));
        assertTrue(runtime.contains("scriptedRotationYaw = mc.thePlayer.rotationYaw"));
        assertTrue(runtime.contains("scriptedRotationPitch = mc.thePlayer.rotationPitch"));
        assertTrue(runtime.contains("visualRotationYaw"));
        assertTrue(runtime.contains("visualRotationPitch"));
        assertTrue(runtime.contains("smoothVisualRotation("));
        assertTrue(runtime.contains("mc.thePlayer.rotationYaw = visualRotationYaw"));
        assertTrue(runtime.contains("mc.thePlayer.rotationPitch = visualRotationPitch"));
        assertTrue(bridge.contains("tellyRuntime.onRotationResolved(event)"));

        assertTrue(packetBridge.contains("C03PacketPlayer rewritten = rewritePlayerPacket(packet, snapshot)"));
        assertTrue(packetBridge.contains("getRotationYaw(rotation)"));
        assertTrue(packetBridge.contains("getRotationPitch(rotation)"));
        assertTrue(forgeBridge.contains("rotationPublication.publish(RotationState.isActived()"));
        assertTrue(standaloneBridge.contains("rotationPublication.publish(rotationActive"));
    }

    @Test
    public void activationGuidanceFadesOutWhenSneakIsReleased() throws IOException {
        String runtime = source(MAIN + "TellyBridgeRuntime.java");

        assertTrue(runtime.contains("boolean shouldShow = mc.thePlayer.isSneaking()"));
        assertTrue(runtime.contains("updateHudAnimation(now, shouldShow, showingSuccess)"));
        assertTrue(runtime.contains("float targetPresence = shouldShow ? 1.0F : 0.0F"));
        assertTrue(runtime.contains("mc.thePlayer == null || !mc.thePlayer.isSneaking()"));
    }

    @Test
    public void runtimeRestoresThePreviousActivationHudAndGuidance() throws IOException {
        String runtime = source(MAIN + "TellyBridgeRuntime.java");

        assertTrue(runtime.contains("NIGHTBLOOM_PINK = 0xFFFF7AC8"));
        assertTrue(runtime.contains("NIGHTBLOOM_VIOLET = 0xFFA78BFA"));
        assertTrue(runtime.contains("RenderServices.glow().queueRing(layout.centerX, layout.ringCenterY"));
        assertFalse(runtime.contains("RenderServices.glow().queueRoundedRect("));
        assertFalse(runtime.contains("RenderServices.shapes().progressBar("));
        assertTrue(runtime.contains("RenderUtil.drawArcOutline(layout.centerX, layout.ringCenterY"));
        assertTrue(runtime.contains("!shadowFrameOpen && !glowFrameOpen"));
        assertTrue(runtime.contains("GL11.glBegin(GL11.GL_LINES)"));
        assertTrue(runtime.contains("GL11.glLineWidth(3.8F)"));
        assertTrue(runtime.contains("GL11.glLineWidth(1.35F)"));
        assertTrue(runtime.contains("GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)"));
        assertTrue(runtime.contains("new TellyHudVisual(\"IDLE\""));
        assertTrue(runtime.contains("new TellyHudVisual(\"GO\""));
        assertTrue(runtime.contains("new TellyHudVisual(\"OK\""));
        assertTrue(runtime.contains("\"Telly activated\", NIGHTBLOOM_VIOLET"));
        assertTrue(runtime.contains("\"Hold blocks\""));
        assertTrue(runtime.contains("\"Face diagonal\""));
        assertTrue(runtime.contains("\"Look down\""));
        assertTrue(runtime.contains("\"Aim at block side\""));
        assertTrue(runtime.contains("\"Aim at marked zone\""));
        assertTrue(runtime.contains("\"Move to the edge\""));
        assertTrue(runtime.contains("\"Release sneak + hold use\""));
        assertFalse(runtime.contains("String text = \"Activate?\""));
    }

    @Test
    public void activationPublishesTheScriptedRotationInTheSamePreUpdate() throws IOException {
        String runtime = source(MAIN + "TellyBridgeRuntime.java");

        assertTrue(runtime.contains("updateActivationPrompt();\n            if (!running) {\n                return true;\n            }"));
        assertFalse(runtime.contains("updateActivationPrompt();\n            return true;"));
    }

    @Test
    public void runtimeKeepsActivationCycleAndPacketWindowStateBoundaries() throws IOException {
        String runtime = source(MAIN + "TellyBridgeRuntime.java");

        assertTrue(runtime.contains("PROMPT_READY_MS = 1000L"));
        assertTrue(runtime.contains("PROMPT_SUPPRESS_USE_MS = 850L"));
        assertTrue(runtime.contains("PROMPT_BREAK_GRACE_MS = 300L"));
        assertTrue(runtime.contains("if (setupTick < 12)"));
        assertTrue(runtime.contains("cyclePhase = 19;"));
        assertTrue(runtime.contains("(phase + 1) % TellyBridgeProgram.length()"));
        assertTrue(runtime.contains("event.getType() == EventType.POST"));
        assertTrue(runtime.contains("c08AtTickBoundary = totalAcceptedC08.get();"));
        assertTrue(runtime.contains("if (result.success)"));
        assertTrue(runtime.contains("placementSearch.markRejected(accepted.candidate.target"));
        assertTrue(runtime.contains("acceptedPlacements.clear()"));
        assertTrue(runtime.contains("submittedPlacements.clear()"));
        assertTrue(runtime.contains("placementWriteResults.clear()"));
        assertTrue(runtime.contains("scriptedUse = TellyBridgeProgram.using(phase)"));
        assertTrue(runtime.contains("setUsePressed(running ? scriptedUse : physicalUseDown())"));
        assertFalse(runtime.contains("setUsePressed(running ? TellyBridgeProgram.using(cyclePhase)"));
    }

    @Test
    public void longRunningPlacementKeepsUseWindowAndContinuationStateAligned() throws IOException {
        String runtime = source(MAIN + "TellyBridgeRuntime.java");
        String search = source(MAIN + "TellyBridgePlacementSearch.java");

        assertTrue(runtime.contains("if (!scriptedUse)"));
        assertTrue(runtime.contains("placementSearch.discardInvalidContinuation()"));
        assertTrue(search.contains("void discardInvalidContinuation()"));
        assertTrue(search.contains("lastPlacedPos != null && !isSupportAvailable(lastPlacedPos)"));
        assertTrue(search.contains("clearContinuation()"));
        assertTrue(search.contains("lastPlacedPos = null"));
        assertTrue(search.contains("lastSupportPos = null"));
        assertTrue(search.contains("lastSupportFace = null"));
    }

    @Test
    public void placementLifecycleSurvivesAsynchronousNettyAcceptance() throws IOException {
        String runtime = source(MAIN + "TellyBridgeRuntime.java");

        assertTrue(runtime.contains("ConcurrentHashMap<Long, AcceptedPlacement>"));
        assertTrue(runtime.contains("ConcurrentLinkedQueue<SubmittedPlacement>"));
        assertTrue(runtime.contains("ConcurrentLinkedQueue<PlacementWriteResult>"));
        assertTrue(runtime.contains("AtomicLong totalAcceptedC08"));
        assertTrue(runtime.contains("submittedPlacements.offer(new SubmittedPlacement(candidate, tick, runtimeGeneration))"));
        assertTrue(runtime.contains("SubmittedPlacement submitted = claimSubmittedPlacement(packet)"));
        assertTrue(runtime.contains("placementWriteResults.offer("));
        assertTrue(runtime.contains("drainPlacementWriteResults()"));
        assertTrue(runtime.contains("pruneSubmittedPlacements(mc.thePlayer.ticksExisted)"));
        assertFalse(runtime.contains("placingViaRuntime && pendingPlacement != null"));
        assertFalse(runtime.contains("boolean packetAccepted = totalAcceptedC08 > before;"));
        assertFalse(runtime.contains("placementSearch.recordAccepted(candidate);"));
    }

    @Test
    public void rewrittenSearchKeepsTheCompleteFallbackChain() throws IOException {
        String search = source(MAIN + "TellyBridgePlacementSearch.java");

        assertTrue(search.contains("findBelowPlayerAirborneFallback"));
        assertTrue(search.contains("findStraightPreviousVisibleFaceFallback"));
        assertTrue(search.contains("findStraightGroundExceptionCandidate"));
        assertTrue(search.contains("findStraightLegacyLaneFallback"));
        assertTrue(search.contains("findNearestSupportToBelowPlayerFallback"));
        assertTrue(search.contains("findPreviousBlockAirborneFallback"));
        assertTrue(search.contains("resolveCandidateWithOffCursorSilentPitch"));
        assertTrue(search.contains("EXTENDED_FACE_HIT_OFFSETS"));
        assertTrue(search.contains("shouldUseHistoricalPlayerCollisionChecks"));
        assertTrue(search.contains("cancelledGhostBlocks"));
        assertTrue(search.contains("shouldRejectStraightSideSwitch"));
        assertTrue(search.contains("getStraightSideSwitchPenalty"));
        assertTrue(search.contains("findNearestSupportedReplaceableTarget"));
        assertTrue(search.contains("getPathStartTowardBelowPlayer"));
        assertTrue(search.contains("rasterizeHorizontalLineAtY"));
        assertTrue(search.contains("isCursorOrBelowPlayerTarget"));
        assertTrue(search.contains("isDiagonalMovementContext"));
        assertTrue(search.contains("isCursorDirectedAtBlock"));
        assertTrue(search.contains("getBelowPlayerFallbackEndpoints"));
        assertTrue(search.contains("Math.max(deadlineMs"));
        assertTrue(search.contains("shouldAllowPlayerOneNonCursorTarget"));
        assertTrue(search.contains("isPlayerHitboxFullyInsideSingleBlockColumn"));
        assertTrue(search.contains("getStrictBelowTargetY"));
        assertTrue(search.contains("getCurrentBelowTargetY"));
        assertTrue(search.contains("getPreviousBelowTargetY"));
        assertTrue(search.contains("return MathHelper.floor_double(player.prevPosY) - 1;"));
        assertTrue(search.contains("forcedModeCheck"));
        assertTrue(search.contains("detectedModeCheck"));
        assertFalse(search.contains("bridgeDeckY"));
        assertTrue(search.contains(
                "int upwardY = isStraightAscendingContext(player) ? currentY + 1"));
        assertTrue(search.contains("private boolean isStraightAscendingContext"));
        assertTrue(search.contains("player.motionY > 0.0D"));
        assertTrue(search.contains("target.getY() == currentY + 1"));
        assertTrue(search.contains("targetY == currentY || targetY == strictY"));
        assertTrue(search.contains("targetY == previousY"));
        assertTrue(search.contains("targetY == upwardY"));
        assertTrue(search.contains("lane == bridgeLaneBlock"));
        assertTrue(search.contains("lastPlacedPos = candidate.target"));
        assertTrue(search.contains("lastSupportPos = candidate.support"));
        assertTrue(search.contains("lastSupportFace = candidate.face"));
    }

    @Test
    public void activationHudUsesSharedGlowWithoutACapsuleAndSmoothsVisualState() throws IOException {
        String runtime = source(MAIN + "TellyBridgeRuntime.java");
        String render2D = method(runtime, "void onRender2D()", "void onRender3D()");
        String glow = source("src/main/java/gq/yozakura/engine/render/glow/GlowRenderer.java");
        String renderUtil = source("src/main/java/gq/yozakura/util/render/RenderUtil.java");
        String glowMask = source("src/main/resources/assets/minecraft/yozakura/shaders/glow_mask.frag");

        assertTrue(runtime.contains("private static final float HUD_RING_RADIUS = 13.0F"));
        assertTrue(runtime.contains("private static final float HUD_RING_WIDTH = 1.45F"));
        assertTrue(runtime.contains("private static final float HUD_LABEL_GAP = 6.0F"));
        assertTrue(render2D.contains("TellyHudVisual visual = resolveHudVisual("));
        assertTrue(render2D.contains("TellyHudLayout layout = resolveHudLayout("));
        assertTrue(render2D.contains("drawTellyHud(visual, layout)"));
        assertTrue(runtime.contains("drawGlowString(visual.centerText"));
        assertTrue(runtime.contains("drawStringWithGlow(visual.centerText"));
        assertTrue(runtime.contains("drawStringWithGlow(visual.labelText"));
        assertTrue(runtime.contains("withAlpha(visual.accentColor, 132.0F * hudPresence)"));
        assertTrue(runtime.contains("withAlpha(visual.accentColor, 230.0F * hudPresence)"));
        assertTrue(runtime.contains("withAlpha(visual.accentColor, 156.0F * hudPresence)"));
        assertFalse(render2D.contains("RenderServices.glow().queueRoundedRect("));
        assertFalse(render2D.contains("RenderServices.shapes().progressBar("));
        assertTrue(runtime.contains("float centerX = labelX + labelWidth * 0.5F"));
        assertTrue(runtime.contains("float labelY = ringCenterY + HUD_RING_RADIUS + HUD_LABEL_GAP"));
        assertTrue(runtime.contains("withAlpha(visual.accentColor, 220.0F * hudPresence)"));
        assertTrue(runtime.contains("0.94F, GlowProfile.ACCENT"));
        assertTrue(runtime.contains("withAlpha(visual.accentColor, 150.0F * hudPresence)"));
        assertTrue(runtime.contains("0.78F, GlowProfile.PANEL"));
        assertTrue(runtime.contains("RenderUtil.drawRoundedArcOutline("));
        assertTrue(runtime.contains("int labelColor = withAlpha(NIGHTBLOOM_TEXT"));
        assertTrue(runtime.contains("238.0F * hudPresence * stateTextAlpha()"));
        assertTrue(runtime.contains("RenderServices.markHudEffectsStateChanged()"));
        assertTrue(runtime.indexOf("RenderServices.markHudEffectsStateChanged()")
                < runtime.indexOf("RenderServices.glow().queueRing(layout.centerX, layout.ringCenterY"));
        assertTrue(runtime.contains("RenderServices.glow().queueRing(layout.centerX, layout.ringCenterY"));
        assertTrue(runtime.contains("RenderServices.beginHudEffectsFrame()"));
        assertTrue(runtime.contains("RenderServices.flushHudEffectsFrame()"));
        assertTrue(runtime.contains("1.0F - (float) Math.exp("));
        assertTrue(runtime.contains("persistentDisplayProgress"));
        assertTrue(runtime.contains("visualProgress"));
        assertFalse(render2D.contains("RenderServices.shapes().shadow("));
        assertFalse(render2D.contains("RenderServices.shapes().rounded("));
        assertFalse(runtime.contains("TELLY_AMBER"));
        assertFalse(runtime.contains("TELLY_LIME"));

        assertTrue(glow.contains("public void queueRing("));
        assertTrue(glow.contains("command instanceof RingCommand"));
        assertTrue(renderUtil.contains("public static void drawRoundedArcOutline("));
        assertTrue(renderUtil.contains("float capRadius = lineWidth * 0.5f"));
        assertTrue(glowMask.contains("float startCapAlpha"));
        assertTrue(glowMask.contains("float endCapAlpha"));
        assertTrue(glowMask.contains("max(bodyAlpha, max(startCapAlpha, endCapAlpha))"));
        assertTrue(glowMask.contains("if (mode == 1)"));
        assertTrue(glowMask.contains("else if (mode == 2)"));
    }

    private static String method(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        if (start < 0 || end < 0) {
            throw new AssertionError("Unable to isolate method between " + startToken + " and " + endToken);
        }
        return source.substring(start, end);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
