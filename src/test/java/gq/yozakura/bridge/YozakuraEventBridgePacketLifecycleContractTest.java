package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class YozakuraEventBridgePacketLifecycleContractTest {
    @Test
    public void cancelledOutboundPacketsCompleteTheirPromiseWithoutDroppingSprintState() throws IOException {
        String combined = combinedSource();

        assertTrue("A cancelled packet must still complete its caller promise",
                combined.contains("if (event.isCancelled())") && combined.contains("completeDroppedWrite(promise);"));
        assertTrue("The dropped-write helper must preserve caller compatibility without claiming a server write",
                combined.contains("PacketWriteDisposition.completeDropped(promise);"));
        assertFalse("Sprint state packets must retain their native C0B lifecycle",
                combined.contains("shouldBlockSprintPacket") || combined.contains("SEND_BLOCKED_SPRINT"));
    }

    @Test
    public void noEventPlayerPacketsStillPassThroughSilentRotationRewrite() throws IOException {
        String combined = combinedSource();
        String write = writeMethod(combined);
        int noEventBranch = write.indexOf("consumeNoEventMarker(packet)");
        int packetEventDispatch = write.indexOf("new PacketEvent(EventType.SEND, packet)");

        assertTrue("The bridge must consume structured no-event replay metadata", noEventBranch >= 0);
        assertTrue("Packet-event dispatch must remain after no-event metadata is resolved",
                packetEventDispatch > noEventBranch);
        int regularNoEventBranch = write.indexOf("boolean skipPacketEvent", noEventBranch);
        String noEventSection = write.substring(regularNoEventBranch, packetEventDispatch);

        assertTrue("The no-event marker must be tracked separately from packet processing",
                noEventSection.contains("skipPacketEvent"));
        assertFalse("Ordinary no-event packets must not bypass rotation rewrite and packet lifecycle handling",
                noEventSection.contains("super.write(ctx, msg, promise)"));
    }

    @Test
    public void forcedLockViewRotationUpdatesTheLocalCamera() throws IOException {
        String forgeSrc = forgeSource();

        assertTrue("The Forge PRE bridge must apply a forced RotationManager view update",
                forgeSrc.contains("applyLocalViewRotation(update);"));
        assertTrue("Lock View is identified by RotationManager's forced-rotation state",
                forgeSrc.contains("YozakuraRuntime.rotationManager.isRotated()"));
        assertTrue("Lock View must update both camera axes",
                forgeSrc.contains("mc.thePlayer.rotationYaw = update.getNewYaw();")
                        && forgeSrc.contains("mc.thePlayer.rotationPitch = update.getNewPitch();"));
    }

    @Test
    public void leavingTheWorldClearsPendingRotationExitState() throws IOException {
        String forgeSrc = forgeSource();
        int tickStart = forgeSrc.indexOf("    public void onClientTick(TickEvent.ClientTickEvent event) {");
        int tickEnd = forgeSrc.indexOf("    @SubscribeEvent", tickStart + 1);
        String tick = forgeSrc.substring(tickStart, tickEnd);
        String clearState = method(forgeSrc, "    private void clearBridgeState() {", "    private void dispatchPreUpdate()");

        assertTrue("Auth loss and world disconnect must not carry a return rotation into the next server",
                count(tick, "clearBridgeState();") >= 2
                        && clearState.contains("RotationExitState.clear();"));
    }

    @Test
    public void nettyUsesOneImmutableRotationSnapshotPerPlayerPacket() throws IOException {
        String combined = combinedSource();
        String forgeSrc = forgeSource();
        String write = writeMethod(combined);

        assertTrue("Forge PRE must publish rotation through one immutable snapshot",
                combined.contains("ForgeRotationPublication"));
        assertTrue("Each C03 write must capture one publication snapshot",
                forgeSrc.contains("return rotationPublication.snapshot();")
                        || combined.contains("ForgeRotationPublication.Snapshot rotation = rotationPublication.snapshot();"));
        assertFalse("The Netty handler must not stitch together mutable RotationState fields",
                write.contains("RotationState.isActived()")
                        || write.contains("RotationState.getRotationYawHead()")
                        || write.contains("RotationState.getRotationPitch()"));
        assertTrue("The rewrite helper must consume the captured snapshot",
                combined.contains("rewritePlayerPacket(C03PacketPlayer packet,")
                        && combined.contains("S rotation)"));
    }

    @Test
    public void postSensitiveActionsStayBeforeC03WithoutGivingUpSilentRotation() throws IOException {
        String forgeSrc = forgeSource();
        String combined = combinedSource();
        String pre = method(forgeSrc, "    private void dispatchPreUpdate() {", "    private void applyLocalViewRotation(");
        String write = writeMethod(combined);
        String handler = combined;
        String playerPacket = forgeWritePlayerPacketMethod();

        int beginPre = pre.indexOf("rotationPublication.beginPre();");
        int exposePre = pre.indexOf("activePreUpdate = update;");
        int dispatch = pre.indexOf("EventManager.call(update);");
        int publish = pre.indexOf("rotationPublication.publish(");
        int finishPre = pre.indexOf("activePreUpdate = null;");
        assertTrue("PRE must expose an in-progress generation before listeners can send actions",
                beginPre >= 0 && exposePre > beginPre && dispatch > exposePre);
        assertTrue("The final listener rotation must be published before PRE is released",
                publish > dispatch && finishPre > publish);

        assertTrue("Vanilla actions must preserve their source order even when a silent rotation is active",
                write.contains("boolean preserveOriginalPacketOrder = true;")
                        && write.contains("afterCurrentRotation = accepted.isAfterCurrentRotationRequired();")
                        && write.contains("preserveOriginalPacketOrder = !afterCurrentRotation")
                        && write.contains("if (!skipPacketEvent && !preserveOriginalPacketOrder && isPostSensitiveAction(packet))")
                        && write.contains("queueCurrentActionPacket(packet, promise, writeId);"));
        String actionClassifier = method(combined,
                "    protected boolean isPostSensitiveAction(Packet<?> packet) {",
                "    protected void queueCurrentActionPacket(");
        assertTrue("Forge must batch only actions whose server interpretation depends on silent yaw",
                actionClassifier.contains("C02PacketUseEntity")
                        && actionClassifier.contains("C07PacketPlayerDigging")
                        && actionClassifier.contains("C08PacketPlayerBlockPlacement")
                        && actionClassifier.contains("C09PacketHeldItemChange")
                        && actionClassifier.contains("C0APacketAnimation")
                        && actionClassifier.contains("C0EPacketClickWindow"));
        assertFalse("Sprint/sneak transitions are movement state, not delayed combat actions",
                actionClassifier.contains("C0BPacketEntityAction"));
        assertFalse("Ability transitions must reach the server in their native movement order",
                actionClassifier.contains("C13PacketPlayerAbilities"));
        assertTrue("The close-window companion packet must stay behind an already queued inventory click",
                write.contains("packet instanceof net.minecraft.network.play.client.C0DPacketCloseWindow")
                        && combined.contains("currentClickWindowPackets > 0")
                        && combined.contains("readyClickWindowPackets > 0"));
        assertTrue("The packet handler must hold separate ready and current action batches",
                handler.contains("OutboundActionBatchQueue<DelayedPacket>")
                        || handler.contains("OutboundActionBatchQueue<D>"));

        int readyFlush = playerPacket.indexOf("flushReadyActionPackets(ctx);");
        int tickAdvance = playerPacket.indexOf(
                "boolean playerTickAdvanced = playerPacketTickGate.consumeNextCanonicalPlayerPacket(");
        int silentWrite = playerPacket.indexOf("super.write(ctx, rewritten, promise);");
        int markSent = playerPacket.indexOf("markRotationSent(snapshot);", silentWrite);
        int flushRotationDependent = playerPacket.indexOf("flushAfterCurrentRotationPackets(ctx);", markSent);
        int promoteCurrent = playerPacket.indexOf("promoteCurrentActionPackets();", flushRotationDependent);
        assertTrue("A prior silent action batch must leave before the following C03",
                tickAdvance >= 0 && readyFlush > tickAdvance && silentWrite > readyFlush);
        assertTrue("Same-tick rotation-dependent actions must leave after their C03 and before legacy batches are promoted",
                markSent > silentWrite && flushRotationDependent > markSent
                        && promoteCurrent > flushRotationDependent);

        int currentFlush = playerPacket.indexOf("flushCurrentActionPackets(ctx);");
        int normalWrite = playerPacket.indexOf("super.write(ctx, packet, promise);");
        assertTrue("Non-silent AutoClicker actions must be emitted before the native C03",
                currentFlush >= 0 && normalWrite > currentFlush);
        assertTrue("Extra same-tick C03 packets must not release actions after an earlier movement packet",
                playerPacket.contains(
                                "boolean playerTickAdvanced = playerPacketTickGate.consumeNextCanonicalPlayerPacket(")
                        && playerPacket.contains("if (playerTickAdvanced && !preUpdatePending && !isRotationActive(snapshot))"));
        assertTrue("Forge PRE must enqueue the exact published generation before the native player packet is sent",
                forgeSrc.contains("handler.markNextPlayerPacketTick(published.getGeneration());"));
    }

    @Test
    public void retainsTheFirstPlayerPacketMarkerUntilTheForgeHandlerIsInstalled() throws IOException {
        String combined = combinedSource();
        int markerStart = combined.indexOf("    void markNextPlayerPacketTick(final long generation) {");
        String marker = combined.substring(markerStart);

        assertTrue("The first Forge C03 must retain its PRE generation while Netty installs the handler",
                combined.contains("pendingPlayerPacketGeneration"));
        assertTrue("handlerAdded must drain a retained first-player marker",
                combined.contains("drainPendingPlayerPacketTick(ctx);"));
        assertTrue("The generation must be saved before handlerContext is consulted",
                marker.indexOf("storePendingPlayerPacketGeneration(generation);") >= 0
                        && marker.indexOf("ChannelHandlerContext current = handlerContext;")
                        > marker.indexOf("storePendingPlayerPacketGeneration(generation);"));
    }

    @Test
    public void delayedPromisesAreFailedOnDisconnectAndBlinkBuffersRewrittenC03() throws IOException {
        String combined = combinedSource();

        int rewrite = combined.indexOf("C03PacketPlayer rewritten = rewritePlayerPacket(");
        int blinkRewritten = combined.indexOf("offerPacket(rewritten, promise, writeId)", rewrite);
        assertTrue("Blink must buffer the silent-rotation C03, not the original packet",
                rewrite >= 0 && blinkRewritten > rewrite);
        assertTrue("Handler removal must fail queued promises",
                combined.contains("handlerRemoved(ChannelHandlerContext ctx)")
                        && combined.contains("failDelayedPackets(new ClosedChannelException())"));
        assertTrue("Channel disconnect must fail queued promises",
                combined.contains("channelInactive(ChannelHandlerContext ctx)"));
        assertTrue("Promise cleanup must use non-throwing completion",
                combined.contains("promise.tryFailure(cause);"));
    }

    @Test
    public void acceptedTeleportInvalidatesStaleRotationAndProtectsItsVanillaConfirmation() throws IOException {
        String base = baseSource();
        String forge = forgeSource();
        String standalone = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java")), StandardCharsets.UTF_8);
        String blink = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/manager/BlinkManager.java")), StandardCharsets.UTF_8);
        String inbound = method(base,
                "    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {",
                "    protected void writePlayerPacketCommon(");

        int cancellation = inbound.indexOf("if (event.isCancelled())");
        int boundary = inbound.indexOf("handleAcceptedTeleportBoundary(", cancellation);
        int vanilla = inbound.indexOf("super.channelRead(ctx, msg);", boundary);
        assertTrue("Only an accepted S08 may create a teleport boundary, before Vanilla applies it",
                cancellation >= 0 && boundary > cancellation && vanilla > boundary
                        && inbound.contains("packet instanceof S08PacketPlayerPosLook"));
        assertTrue("The shared boundary must clear stale tick state and arm exact C04/C06 passthrough",
                base.contains("playerPacketTickGate.invalidatePending();")
                        && base.contains("teleportConfirmationPending = true;")
                        && base.contains("consumeTeleportConfirmation(packet)"));
        assertTrue("Both bridge owners must invalidate their published silent rotation",
                forge.contains("rotationPublication.invalidateForTeleport();")
                        && standalone.contains("rotationPublication.invalidateForTeleport();"));
        assertTrue("Blink must discard stale buffered packets and bypass the required position confirmation",
                blink.contains("onTeleportBoundary(TeleportBoundaryEvent event)")
                        && blink.contains("discardBufferedPacketsForTeleport()")
                        && blink.contains("shouldBypassForTeleport(packet)"));
    }

    private static String writeMethod(String source) {
        int begin = source.indexOf("    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {");
        if (begin < 0) {
            begin = source.indexOf("        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {");
        }
        int finish = source.indexOf("    protected void observePacketWrite(", begin + 1);
        if (finish < 0) {
            finish = source.indexOf("    @Override", begin + 1);
        }
        if (finish < 0) {
            finish = source.indexOf("        @Override", begin + 1);
        }
        return source.substring(begin, finish);
    }

    private static String forgeSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java")), StandardCharsets.UTF_8);
    }

    private static String baseSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/BasePacketBridgeHandler.java")), StandardCharsets.UTF_8);
    }

    private static String combinedSource() throws IOException {
        return baseSource() + "\n" + forgeSource();
    }

    private static String forgeWritePlayerPacketMethod() throws IOException {
        String base = baseSource();
        int begin = base.indexOf("    protected void writePlayerPacketCommon(");
        int end = base.indexOf("    void markNextPlayerPacketTick(", begin);
        if (begin >= 0 && end > begin) {
            return base.substring(begin, end);
        }
        return "";
    }

    private static String method(String source, String beginNeedle, String endNeedle) {
        int begin = source.indexOf(beginNeedle);
        int finish = source.indexOf(endNeedle, begin + beginNeedle.length());
        return source.substring(begin, finish);
    }

    private static int count(String source, String needle) {
        int matches = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            matches++;
            index += needle.length();
        }
        return matches;
    }
}
