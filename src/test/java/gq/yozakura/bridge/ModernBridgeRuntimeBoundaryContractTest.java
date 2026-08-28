package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModernBridgeRuntimeBoundaryContractTest {
    @Test
    public void modernEntryPointsDoNotLinkLegacyMinecraftTypes() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/modern/ModernForgeEventBridge.java");
        String client = source("src/main/java/gq/yozakura/core/ModernForgeClient.java");
        String reflection = source("src/main/java/gq/yozakura/bridge/util/ReflectionUtils.java");

        assertFalse("The modern bridge must not implement the legacy typed bridge contract",
                bridge.contains("implements ClientBridge"));
        assertFalse("The modern bridge must not import the 1.8.9 Minecraft singleton",
                bridge.contains("import net.minecraft.client.Minecraft;"));
        assertFalse("The modern bridge must not expose the 1.8.9 packet descriptor",
                bridge.contains("net.minecraft.network.Packet"));
        assertFalse("The modern client must not initialize the legacy Client class",
                client.contains("Client.username"));
        assertFalse("Reflection shared with bootstrap must not expose the 1.8.9 NetworkManager descriptor",
                reflection.contains("import net.minecraft.network.NetworkManager;")
                        || reflection.contains("getChannel(NetworkManager"));
    }

    @Test
    public void modernInitializationPublishesStateOnlyAfterRegistrationSucceeds() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/modern/ModernForgeEventBridge.java");
        String init = method(bridge, "    public void init() {", "    private void register()");

        int register = init.indexOf("register();");
        int publish = init.indexOf("initialized = true;");
        assertTrue("Modern registration must complete before the bridge is published active",
                register >= 0 && publish > register);
        assertTrue("A partial modern listener registration must be rolled back",
                init.contains("rollbackRegistration("));

        String client = source("src/main/java/gq/yozakura/core/ModernForgeClient.java");
        String constructor = method(client, "    public ModernForgeClient() {", "    public static boolean isState()");
        int bridgeInit = constructor.indexOf("ModernForgeEventBridge.initBridge();");
        int clientPublish = constructor.indexOf("state = true;");
        assertTrue("Modern client state must only be published after event registration succeeds",
                bridgeInit >= 0 && clientPublish > bridgeInit);
    }

    @Test
    public void modernUnloadDetachesListenersAndClearsRuntimeState() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/modern/ModernForgeEventBridge.java");
        String client = source("src/main/java/gq/yozakura/core/ModernForgeClient.java");
        String shutdown = method(bridge, "    public void shutdown() {", "    public boolean isInGame()");
        String uninject = method(client, "    public static void unInject() {", "    public static void showInjectionSuccessAnimation()");

        assertTrue("Modern client unload must stop the event bridge",
                uninject.contains("ModernForgeEventBridge.shutdownBridge();"));
        assertTrue("Registered modern consumers must be tracked for exact unregistration",
                bridge.contains("REGISTERED_LISTENERS") && shutdown.contains("unregisterListeners();"));
        assertTrue("Modern packet queues and Netty handler must be released on unload",
                shutdown.contains("ModernPacketBridge.shutdown();"));
        assertTrue("Forced movement keys must be restored on unload",
                shutdown.contains("ModernMovementBridge.shutdown();"));
        assertTrue("Combat targets and timers must not survive reinjection",
                shutdown.contains("ModernCombatBridge.shutdown();"));
        assertTrue("Rotation and full-bright overrides must be restored on unload",
                shutdown.contains("ModernRotationBridge.clearSilentRotation();")
                        && shutdown.contains("ModernFullBrightBridge.shutdown();"));
        assertTrue("A completed shutdown must make reinjection possible",
                shutdown.contains("initialized = false;"));
    }

    @Test
    public void modernCombatHonorsConfiguredKnockbackDelay() throws IOException {
        String combat = source("src/main/java/gq/yozakura/bridge/modern/ModernCombatBridge.java");
        String tick = method(combat, "    static void onClientTick(Object event) {", "    private static Object chooseTarget(");
        String attack = method(combat, "    private static void performAttack(", "    private static void onAttackModules(");

        assertTrue("KnockbackDelay must observe player hurt state on every combat tick",
                tick.contains("updateKnockbackDelay(player);"));
        assertTrue("Automated attacks must honor an active knockback delay",
                attack.contains("isAttackDelayed(player)"));
        assertTrue("KnockbackDelay must use the configured base delay",
                combat.contains("\"KnockbackDelay\", \"Delay MS\""));
        assertTrue("KnockbackDelay must use configured jitter and chance",
                combat.contains("\"KnockbackDelay\", \"Jitter MS\"")
                        && combat.contains("\"KnockbackDelay\", \"Chance\""));
        assertTrue("KnockbackDelay must honor the 1.8.9 eligibility controls",
                combat.contains("\"KnockbackDelay\", \"Only Weapon\"")
                        && combat.contains("\"KnockbackDelay\", \"Require Moving\"")
                        && combat.contains("\"KnockbackDelay\", \"Ground Only\""));
    }

    @Test
    public void modernFastPlaceCapsUseCooldownWithoutSyntheticClicks() throws IOException {
        String movement = source("src/main/java/gq/yozakura/bridge/modern/ModernMovementBridge.java");
        String tick = method(movement, "    static void onClientTick(Object event) {", "    static void onMovementInput(Object event) {");

        assertTrue("FastPlace must run from the modern client tick",
                tick.contains("handleFastPlace(minecraft, player, options);"));
        assertTrue("FastPlace must honor its enabled state and configured delay",
                movement.contains("enabled(\"FastPlace\")")
                        && movement.contains("\"FastPlace\", \"Delay\""));
        assertTrue("FastPlace must honor the Only Blocks control",
                movement.contains("\"FastPlace\", \"Only Blocks\""));
        assertTrue("FastPlace must restore reflection state on unload",
                movement.contains("rightClickDelayField = null;"));
        assertFalse("FastPlace must not synthesize a use-item click",
                movement.contains("handleKeybinds") || movement.contains("startUseItem"));
    }

    @Test
    public void modernKeepSprintMatchesLegacyAttackMotionControls() throws IOException {
        String combat = source("src/main/java/gq/yozakura/bridge/modern/ModernCombatBridge.java");
        String controller = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");
        String attack = method(combat, "    private static void performAttack(", "    private static void onAttackModules(");

        assertTrue("KeepSprint must snapshot movement before the attack applies vanilla slowdown",
                attack.contains("captureAttackMotion(player)"));
        assertTrue("KeepSprint must restore configured post-attack motion only after a successful attack",
                combat.contains("applyKeepSprint(player, target, attackMotion)"));
        assertTrue("KeepSprint must use the legacy slowdown interpolation",
                combat.contains("0.6D + 0.4D * (1.0D - slowdown / 100.0D)"));
        assertTrue("KeepSprint must honor Prediction, Ground Only and Reach Only",
                combat.contains("\"KeepSprint\", \"Prediction\"")
                        && combat.contains("\"KeepSprint\", \"Ground Only\"")
                        && combat.contains("\"KeepSprint\", \"Reach Only\""));
        assertTrue("The modern GUI must expose every legacy KeepSprint control",
                controller.contains(".bool(\"Prediction\", false)")
                        && controller.contains(".bool(\"Ground Only\", false)")
                        && controller.contains(".bool(\"Reach Only\", false)"));
    }

    @Test
    public void modernJumpResetObservesPacketsAndRestoresPhysicalInput() throws IOException {
        String eventBridge = source("src/main/java/gq/yozakura/bridge/modern/ModernForgeEventBridge.java");
        String packet = source("src/main/java/gq/yozakura/bridge/modern/ModernPacketBridge.java");
        String jumpReset = source("src/main/java/gq/yozakura/bridge/modern/ModernJumpResetBridge.java");

        assertTrue("JumpReset must keep the packet bridge installed while enabled",
                packet.contains("enabled(\"JumpReset\")"));
        assertTrue("Incoming packets must be observed without being cancelled",
                packet.contains("ModernJumpResetBridge.onIncoming(packet, level, player);")
                        && jumpReset.contains("clientboundsetentitymotionpacket")
                        && jumpReset.contains("clientboundentityeventpacket"));
        assertTrue("JumpReset must advance from the client tick and movement-input event",
                eventBridge.contains("ModernJumpResetBridge.onClientTick(event);")
                        && eventBridge.contains("ModernJumpResetBridge.onMovementInput(event);"));
        assertTrue("JumpReset must restore the user's physical jump-key state",
                jumpReset.contains("ModernInputBridge.physicalDown(jumpKey)")
                        && jumpReset.contains("ModernInputBridge.setKeyDown(jumpKey, physicalDown)"));
        assertTrue("JumpReset must honor every modern GUI control",
                jumpReset.contains("\"JumpReset\", \"Fake Check\"")
                        && jumpReset.contains("\"JumpReset\", \"Force Forward\"")
                        && jumpReset.contains("\"JumpReset\", \"Chance\""));
        assertTrue("JumpReset transient input must be released on modern unload",
                eventBridge.contains("ModernJumpResetBridge.shutdown();"));
    }

    @Test
    public void modernVelocityAttackModeConsumesLocalKnockbackWindow() throws IOException {
        String eventBridge = source("src/main/java/gq/yozakura/bridge/modern/ModernForgeEventBridge.java");
        String packet = source("src/main/java/gq/yozakura/bridge/modern/ModernPacketBridge.java");
        String combat = source("src/main/java/gq/yozakura/bridge/modern/ModernCombatBridge.java");
        String velocity = source("src/main/java/gq/yozakura/bridge/modern/ModernVelocityBridge.java");
        String controller = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");

        assertTrue("Velocity Attack must keep the packet observer installed",
                packet.contains("ModernVelocityBridge.needsPacketObservation()")
                        && packet.contains("ModernVelocityBridge.onIncoming(packet);"));
        assertTrue("Velocity Attack must advance and expire from the client tick",
                eventBridge.contains("ModernVelocityBridge.onClientTick(event);"));
        assertTrue("A successful attack must give Velocity priority over KeepSprint",
                combat.contains("ModernVelocityBridge.applyAttackSlowdown(player, target,")
                        && combat.contains("attackMotion.x, attackMotion.y, attackMotion.z, attackMotion.available")
                        && combat.contains("else {\n            applyKeepSprint(player, target, attackMotion);"));
        assertTrue("Velocity Attack must apply the legacy 60 percent horizontal slowdown",
                velocity.contains("motionX * 0.6D")
                        && velocity.contains("motionZ * 0.6D")
                        && velocity.contains("setSprinting(player, false)"));
        assertTrue("Velocity Attack must honor every legacy eligibility control",
                velocity.contains("\"Velocity\", \"Attack Timeout\"")
                        && velocity.contains("\"Velocity\", \"Attack Range\"")
                        && velocity.contains("\"Velocity\", \"Only Sprinting\"")
                        && velocity.contains("\"Velocity\", \"Require KillAura\"")
                        && velocity.contains("\"Velocity\", \"Players Only\"")
                        && velocity.contains("\"Velocity\", \"Chance\""));
        assertTrue("The modern GUI must expose the legacy Velocity Attack controls",
                controller.contains(".number(\"Attack Timeout\"")
                        && controller.contains(".number(\"Attack Range\"")
                        && controller.contains(".bool(\"Only Sprinting\"")
                        && controller.contains(".bool(\"Require KillAura\"")
                        && controller.contains(".bool(\"Players Only\"")
                        && controller.contains(".number(\"Chance\""));
        assertTrue("Velocity transient state must be cleared on modern unload",
                eventBridge.contains("ModernVelocityBridge.shutdown();"));
    }

    @Test
    public void modernHitSelectGatesAutomatedAttacksWithLegacyControls() throws IOException {
        String eventBridge = source("src/main/java/gq/yozakura/bridge/modern/ModernForgeEventBridge.java");
        String combat = source("src/main/java/gq/yozakura/bridge/modern/ModernCombatBridge.java");
        String hitSelect = source("src/main/java/gq/yozakura/bridge/modern/ModernHitSelectBridge.java");
        String hitSelectState = source("src/main/java/gq/yozakura/bridge/modern/ModernHitSelectState.java");
        String controller = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");
        String attack = method(combat, "    private static void performAttack(", "    private static AttackMotion captureAttackMotion(");

        assertTrue("HitSelect must gate both AutoClicker and KillAura before an attack is sent",
                attack.contains("ModernHitSelectBridge.shouldAttack(player, target, multiAttack)"));
        assertTrue("HitSelect must update its post-attack window only after a successful attack",
                attack.contains("ModernHitSelectBridge.onAttack(target);"));
        assertTrue("HitSelect must distinguish KillAura Switch multi-target attacks",
                combat.contains("boolean multiAttack = allowCriticals")
                        && combat.contains("\"KillAura\", \"Mode\", \"Single\""));
        assertTrue("HitSelect must implement vulnerable, trade and smart timing",
                controller.contains("\"Vulnerable\"")
                        && hitSelectState.contains("\"Trade\"")
                        && hitSelectState.contains("\"Smart\""));
        assertTrue("HitSelect must honor every legacy timing and eligibility control",
                hitSelect.contains("\"HitSelect\", \"Max HurtTime\"")
                        && hitSelect.contains("\"HitSelect\", \"Min Delay\"")
                        && hitSelect.contains("\"HitSelect\", \"Max Delay\"")
                        && hitSelect.contains("\"HitSelect\", \"Chance\"")
                        && hitSelect.contains("\"HitSelect\", \"Trade Window\"")
                        && hitSelect.contains("\"HitSelect\", \"Post Delay\"")
                        && hitSelect.contains("\"HitSelect\", \"Only Weapon\"")
                        && hitSelect.contains("\"HitSelect\", \"Allow Multi\""));
        assertTrue("The modern GUI must expose the complete legacy HitSelect configuration",
                controller.contains(".mode(\"Mode\", \"Smart\", \"Smart\", \"Trade\", \"Vulnerable\")")
                        && controller.contains(".number(\"Max HurtTime\"")
                        && controller.contains(".number(\"Min Delay\"")
                        && controller.contains(".number(\"Max Delay\"")
                        && controller.contains(".number(\"Trade Window\"")
                        && controller.contains(".number(\"Post Delay\"")
                        && controller.contains(".bool(\"Only Weapon\"")
                        && controller.contains(".bool(\"Allow Multi\""));
        assertTrue("HitSelect state must be observed each tick and cleared on unload",
                eventBridge.contains("ModernHitSelectBridge.shutdown();")
                        && combat.contains("ModernHitSelectBridge.onClientTick(player);"));
    }

    @Test
    public void modernGhostHandSkipsEligiblePlayersWithoutIgnoringBlocks() throws IOException {
        String raycast = source("src/main/java/gq/yozakura/bridge/modern/ModernRaycastBridge.java");
        String ghostHand = source("src/main/java/gq/yozakura/bridge/modern/ModernGhostHandBridge.java");
        String controller = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");

        assertTrue("GhostHand must run before Reach and HitBoxes replace the vanilla hit result",
                raycast.indexOf("ModernGhostHandBridge.apply(minecraft, player)")
                        < raycast.indexOf("enabled(\"Reach\")"));
        assertTrue("GhostHand must only reraycast when the current hit is an eligible player",
                ghostHand.contains("currentHitEntity(minecraft)")
                        && ghostHand.contains("shouldSkip(player, current)"));
        assertTrue("GhostHand reraycast must preserve the nearest vanilla block as an upper bound",
                ghostHand.contains("blockHitDistance(minecraft, player, eyes, end, reach)")
                        && ghostHand.contains("double bestDistance = blockDistance;"));
        assertTrue("GhostHand must exclude the skipped entity and continue to the next valid entity",
                ghostHand.contains("entity == skipped")
                        && ghostHand.contains("ModernMinecraftAccess.entities(minecraft)")
                        && ghostHand.contains("isItemFrame(entity)")
                        && ghostHand.contains("ModernRaycastBridge.applyHitResult(minecraft, result);"));
        assertTrue("GhostHand must honor Team Only and Ignore Weapons",
                ghostHand.contains("\"GhostHand\", \"Team Only\"")
                        && ghostHand.contains("\"GhostHand\", \"Ignore Weapons\""));
        assertTrue("The modern GUI must expose the exact legacy GhostHand settings",
                controller.contains(".bool(\"Team Only\", true)")
                        && controller.contains(".bool(\"Ignore Weapons\", false)"));
    }

    @Test
    public void modernBowAimBotUsesSharedBallisticsAndLegacyControls() throws IOException {
        String eventBridge = source("src/main/java/gq/yozakura/bridge/modern/ModernForgeEventBridge.java");
        String bowAim = source("src/main/java/gq/yozakura/bridge/modern/ModernBowAimBotBridge.java");
        String aimMath = source("src/main/java/gq/yozakura/bridge/modern/ModernAimMath.java");
        String controller = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");

        assertTrue("BowAimBot must advance from the modern client tick",
                eventBridge.contains("ModernBowAimBotBridge.onClientTick(event);"));
        assertTrue("BowAimBot must only run while a bow is actively being used",
                bowAim.contains("isUsingItem(player)")
                        && bowAim.contains("isBow(usedStack)"));
        assertTrue("BowAimBot must select the nearest visible non-bot living target",
                bowAim.contains("ModernMinecraftAccess.livingEntities(minecraft)")
                        && bowAim.contains("isArmorStand(entity)")
                        && bowAim.contains("isProbablyBotForBridge(entity)")
                        && bowAim.contains("hasLineOfSight(player, entity)"));
        assertTrue("BowAimBot must prefer elapsed use ticks and retain a duration fallback",
                bowAim.contains("getTicksUsingItem")
                        && bowAim.contains("getUseItemRemainingTicks")
                        && bowAim.contains("getUseDuration"));
        assertTrue("BowAimBot must use shared prediction and low-arc ballistics",
                bowAim.contains("ModernAimMath.predict(")
                        && bowAim.contains("ModernAimMath.solveLowArc(")
                        && aimMath.contains("DEFAULT_BOW_GRAVITY = 0.006D"));
        assertTrue("BowAimBot must use a stateful visible rotation with independent axis speeds",
                bowAim.contains("ModernVisibleAimState")
                        && bowAim.contains("\"BowAimBot\", \"Yaw Speed\"")
                        && bowAim.contains("\"BowAimBot\", \"Pitch Speed\"")
                        && bowAim.contains("ModernRotationBridge.applyVisibleRotation"));
        assertTrue("The modern GUI must expose the exact legacy BowAimBot controls",
                controller.contains(".number(\"Yaw Speed\", 24.0D, 2.0D, 90.0D, 1.0D)")
                        && controller.contains(".number(\"Pitch Speed\", 18.0D, 2.0D, 90.0D, 1.0D)")
                        && controller.contains(".number(\"Prediction\", 0.55D, 0.0D, 2.0D, 0.05D)"));
        assertTrue("BowAimBot state must be cleared on modern unload",
                eventBridge.contains("ModernBowAimBotBridge.shutdown();"));
    }

    @Test
    public void modernInventoryModulesShareOneAccessLayerAndLegacyControls() throws IOException {
        String eventBridge = source("src/main/java/gq/yozakura/bridge/modern/ModernForgeEventBridge.java");
        String inventoryBridge = source("src/main/java/gq/yozakura/bridge/modern/ModernInventoryBridge.java");
        String access = source("src/main/java/gq/yozakura/bridge/modern/ModernInventoryAccess.java");
        String policy = source("src/main/java/gq/yozakura/bridge/modern/ModernInventoryPolicy.java");
        String controller = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiController.java");

        assertTrue("All inventory modules must advance through one modern lifecycle bridge",
                eventBridge.contains("ModernInventoryBridge.onClientTick(event);")
                        && eventBridge.contains("ModernInventoryBridge.onRightClickBlock(event);"));
        assertTrue("AutoTools, InventoryManager and ChestStealer must share reflection access",
                inventoryBridge.contains("ModernInventoryAccess")
                        && inventoryBridge.contains("handleAutoTools(")
                        && inventoryBridge.contains("handleInventoryManager(")
                        && inventoryBridge.contains("handleChestStealer("));
        assertTrue("Container clicks must use the modern game mode and ClickType contract",
                access.contains("handleInventoryMouseClick")
                        && access.contains("net.minecraft.world.inventory.ClickType"));
        assertTrue("Shared selection rules must remain pure and testable",
                policy.contains("bestHotbarSlot(")
                        && policy.contains("isBetterCandidate(")
                        && policy.contains("nextDelay("));
        assertTrue("AutoTools must preserve and restore its owned hotbar slot",
                inventoryBridge.contains("autoToolsOwnsSlot")
                        && inventoryBridge.contains("restoreAutoToolsSlot("));
        assertTrue("InventoryManager must support the exact OPEN, SPOOF and ALWAYS modes",
                inventoryBridge.contains("\"InventoryManager\", \"Mode\", \"SPOOF\"")
                        && controller.contains(".mode(\"Mode\", \"SPOOF\", \"SPOOF\", \"OPEN\", \"ALWAYS\")"));
        assertTrue("The modern GUI must expose every legacy inventory setting",
                controller.contains(".bool(\"Restore Slot\", true)")
                        && controller.contains(".bool(\"Preserve Tools\", true)")
                        && controller.contains(".number(\"Minimum Durability\", 10.0D, 1.0D, 100.0D, 1.0D)")
                        && controller.contains(".bool(\"Ignore Custom Name\", true)")
                        && controller.contains(".number(\"Delay Jitter\", 20.0D, 0.0D, 250.0D, 5.0D)")
                        && controller.contains(".bool(\"Random Order\", true)"));
        assertTrue("ChestStealer must bind work to a recent physical chest interaction",
                inventoryBridge.contains("CHEST_INTERACTION_TIMEOUT_MS")
                        && inventoryBridge.contains("authorizedChestWindowId"));
        assertTrue("Inventory-owned transient state must be cleared on unload",
                eventBridge.contains("ModernInventoryBridge.shutdown();"));
    }

    @Test
    public void modernSubsystemShutdownsReleaseOwnedState() throws IOException {
        String packet = source("src/main/java/gq/yozakura/bridge/modern/ModernPacketBridge.java");
        String movement = source("src/main/java/gq/yozakura/bridge/modern/ModernMovementBridge.java");
        String combat = source("src/main/java/gq/yozakura/bridge/modern/ModernCombatBridge.java");
        String fullBright = source("src/main/java/gq/yozakura/bridge/modern/ModernFullBrightBridge.java");
        String hud = source("src/main/java/gq/yozakura/bridge/modern/ModernHudEditor.java");
        String web = source("src/main/java/gq/yozakura/ui/click/web/ModernWebClickGuiService.java");

        String packetShutdown = method(packet, "    static void shutdown() {", "    static Map<Integer, ArrayDeque<TrackedBox>> backtrackHistory()");
        assertTrue(packetShutdown.contains("releaseBacktrackPackets();"));
        assertTrue(packetShutdown.contains("releaseFakeLagPackets();"));
        assertTrue(packetShutdown.contains("removeHandler();"));
        assertTrue(packetShutdown.contains("BACKTRACK_HISTORY.clear();"));
        assertTrue(packetShutdown.contains("BYPASS.clear();"));

        String movementShutdown = method(movement, "    static void shutdown() {", "    private static boolean isContainerScreen");
        assertTrue(movementShutdown.contains("restoreSprintKey(options);"));
        assertTrue(movementShutdown.contains("ModernInputBridge.shutdown();"));

        String combatShutdown = method(combat, "    static void shutdown() {", "    private static void resetState()");
        assertTrue(combatShutdown.contains("resetState();"));

        String fullBrightShutdown = method(fullBright, "    static void shutdown() {", "    private static void restore(");
        assertTrue(fullBrightShutdown.contains("restore("));

        String hudShutdown = method(hud, "    static void shutdown() {", "    static boolean handleScroll(");
        assertTrue(hudShutdown.contains("ELEMENTS.clear();"));
        assertTrue(hudShutdown.contains("minecraft = null;"));

        String webStop = method(web, "    public static synchronized void stop() {", "    private static void ensureStarted()");
        assertTrue(web.contains("ExecutorService"));
        assertTrue(webStop.contains("executor.shutdownNow();"));
        assertTrue(webStop.contains("executor = null;"));
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("Expected method marker: " + beginMarker, begin >= 0);
        assertTrue("Expected end marker: " + endMarker, end > begin);
        return source.substring(begin, end);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
