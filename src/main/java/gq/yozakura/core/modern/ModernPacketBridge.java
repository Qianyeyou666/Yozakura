package gq.yozakura.core.modern;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

final class ModernPacketBridge {
    private static final String HANDLER_NAME = "yozakura_modern_packet_bridge";
    private static final int MAX_BACKTRACK_PACKETS = 128;
    private static final int MAX_FAKELAG_PACKETS = 256;
    private static final int MAX_HISTORY = 44;
    private static final long HISTORY_CLEANUP_GRACE_MS = 180L;
    private static final Map<Object, Boolean> BYPASS =
            Collections.synchronizedMap(new IdentityHashMap<Object, Boolean>());
    private static final Map<Integer, ArrayDeque<TrackedBox>> BACKTRACK_HISTORY =
            new java.util.concurrent.ConcurrentHashMap<Integer, ArrayDeque<TrackedBox>>();
    private static final Queue<QueuedIncoming> BACKTRACK_PACKETS =
            new java.util.concurrent.ConcurrentLinkedQueue<QueuedIncoming>();
    private static final Queue<QueuedOutgoing> FAKELAG_PACKETS =
            new java.util.concurrent.ConcurrentLinkedQueue<QueuedOutgoing>();

    private static Channel channel;
    private static Field channelField;
    private static long fakeLagNextPulseAt;
    private static long fakeLagReleaseAfterAttackAt;
    private static boolean fakeLagAllowed;
    private static boolean fakeLagReleaseInProgress;

    private ModernPacketBridge() {
    }

    static void onClientTick(Object event) {
        try {
            if (!isEndPhase(event)) {
                return;
            }
            Object minecraft = ModernMinecraftAccess.minecraft();
            Object player = ModernMinecraftAccess.player(minecraft);
            Object level = ModernMinecraftAccess.level(minecraft);
            if (minecraft == null || player == null || level == null) {
                releaseBacktrackPackets();
                releaseFakeLagPackets();
                removeHandler();
                BACKTRACK_HISTORY.clear();
                ModernRotationBridge.clearSilentRotation();
                return;
            }

            if (needsNetworkBridge()) {
                injectHandler(player, minecraft);
            } else {
                releaseBacktrackPackets();
                releaseFakeLagPackets();
                removeHandler();
            }
            recordBacktrackHistory(minecraft, player);
            releaseDueBacktrackPackets();
            tickFakeLag(player);
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Modern packet bridge tick failed", throwable);
        }
    }

    static void markBypass(Object packet) {
        if (packet != null) {
            BYPASS.put(packet, Boolean.TRUE);
        }
    }

    static boolean consumeBypass(Object packet) {
        return packet != null && BYPASS.remove(packet) != null;
    }

    static Map<Integer, ArrayDeque<TrackedBox>> backtrackHistory() {
        return BACKTRACK_HISTORY;
    }

    static Object applyBacktrackHit(Object minecraft, Object player, double range, double expand) {
        if (!ModernForgeEventBridge.enabled("Backtrack")) {
            return null;
        }
        String mode = ModernForgeEventBridge.mode("Backtrack", "Mode", "Hybrid");
        if ("Packet".equalsIgnoreCase(mode)) {
            return null;
        }
        ModernRaycastBridge.RaycastResult result =
                ModernRaycastBridge.raycastHistorical(player, range, expand, BACKTRACK_HISTORY);
        if (result == null || result.entity == null) {
            return null;
        }
        ModernRaycastBridge.applyHitResult(minecraft, result);
        return result.entity;
    }

    private static void injectHandler(Object player, Object minecraft) {
        try {
            Object connection = ModernMinecraftAccess.connection(minecraft, player);
            Object networkManager = ModernMinecraftAccess.connectionNetworkManager(connection);
            Channel next = channel(networkManager);
            if (next == null || !next.isOpen()) {
                return;
            }
            if (channel != null && channel != next) {
                releaseBacktrackPackets();
                releaseFakeLagPackets();
                removeHandler();
            }
            if (next.pipeline().get(HANDLER_NAME) == null) {
                next.pipeline().addBefore("packet_handler", HANDLER_NAME, new PacketHandler());
            }
            channel = next;
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Modern packet bridge install failed", throwable);
            channel = null;
        }
    }

    private static void removeHandler() {
        Channel old = channel;
        channel = null;
        if (old == null) {
            return;
        }
        try {
            if (old.isOpen() && old.pipeline().get(HANDLER_NAME) != null) {
                old.pipeline().remove(HANDLER_NAME);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Channel channel(Object networkManager) {
        if (networkManager == null) {
            return null;
        }
        try {
            if (channelField == null) {
                channelField = findField(networkManager.getClass(), "channel", "f_129468_", "field_150746_k", "k");
            }
            Object value = channelField == null ? null : channelField.get(networkManager);
            return value instanceof Channel ? (Channel) value : null;
        } catch (Throwable ignored) {
            channelField = null;
            return null;
        }
    }

    private static boolean needsNetworkBridge() {
        if (ModernForgeEventBridge.enabled("Velocity") || ModernForgeEventBridge.enabled("FakeLag")) {
            return true;
        }
        if (!ModernForgeEventBridge.enabled("Backtrack")) {
            return false;
        }
        String mode = ModernForgeEventBridge.mode("Backtrack", "Mode", "Hybrid");
        return ("Packet".equalsIgnoreCase(mode) || "Hybrid".equalsIgnoreCase(mode))
                && ModernForgeEventBridge.number("Backtrack", "Packet Delay", 120.0D) > 0.0D;
    }

    private static Object onOutgoing(Object packet, ChannelHandlerContext ctx, ChannelPromise promise) {
        if (packet == null || consumeBypass(packet)) {
            return packet;
        }
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object player = ModernMinecraftAccess.player(minecraft);
        if (ModernForgeEventBridge.enabled("FakeLag") && shouldQueueFakeLag(packet, player)) {
            if (queueFakeLag(ctx, packet, promise)) {
                return null;
            }
        }
        return packet;
    }

    private static boolean onIncoming(Object packet, ChannelHandlerContext ctx) {
        if (packet == null || consumeBypass(packet)) {
            return false;
        }
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object player = ModernMinecraftAccess.player(minecraft);
        Object level = ModernMinecraftAccess.level(minecraft);
        if (minecraft == null || player == null || level == null) {
            return false;
        }
        if (ModernForgeEventBridge.enabled("Velocity") && handleVelocity(packet, player)) {
            return true;
        }
        if (ModernForgeEventBridge.enabled("Backtrack") && shouldDelayBacktrack(packet, level, player)) {
            queueBacktrack(ctx, packet);
            return true;
        }
        return false;
    }

    private static boolean handleVelocity(Object packet, Object player) {
        String name = className(packet);
        if (name.contains("clientboundsetentitymotionpacket") || name.contains("clientboundsetentityvelocitypacket")) {
            int id = intValue(invokeOrField(packet, "getId", "m_133182_", "id", "f_133170_"), -999999);
            if (id != ModernRotationBridge.entityId(player)) {
                return false;
            }
            double horizontal = ModernForgeEventBridge.number("Velocity", "Horizontal", 100.0D) / 100.0D;
            double vertical = ModernForgeEventBridge.number("Velocity", "Vertical", 100.0D) / 100.0D;
            if (horizontal <= 0.0D && vertical <= 0.0D) {
                return true;
            }
            scaleMotionPacket(packet, horizontal, vertical);
            return false;
        }
        if (name.contains("clientboundexplosionpacket") || name.contains("clientboundexplodepacket")) {
            double horizontal = ModernForgeEventBridge.number("Velocity", "Explosions Horizontal",
                    ModernForgeEventBridge.number("Velocity", "Horizontal", 100.0D)) / 100.0D;
            double vertical = ModernForgeEventBridge.number("Velocity", "Explosions Vertical",
                    ModernForgeEventBridge.number("Velocity", "Vertical", 100.0D)) / 100.0D;
            if (horizontal <= 0.0D && vertical <= 0.0D) {
                zeroExplosion(packet);
                return false;
            }
            scaleExplosionPacket(packet, horizontal, vertical);
        }
        return false;
    }

    private static void scaleMotionPacket(Object packet, double horizontal, double vertical) {
        int xa = scaledInt(packet, horizontal, "getXa", "m_133192_", "xa", "f_133171_", "x", "f_133171_");
        int ya = scaledInt(packet, vertical, "getYa", "m_133199_", "ya", "f_133172_", "y", "f_133172_");
        int za = scaledInt(packet, horizontal, "getZa", "m_133196_", "za", "f_133173_", "z", "f_133173_");
        setInt(packet, xa, "xa", "f_133171_", "x", "field_149415_b");
        setInt(packet, ya, "ya", "f_133172_", "y", "field_149416_c");
        setInt(packet, za, "za", "f_133173_", "z", "field_149414_d");
    }

    private static int scaledInt(Object packet, double scale, String method, String obfuscatedMethod,
                                 String field, String obfuscatedField, String altField, String altObfuscated) {
        Object value = ModernForgeEventBridge.invoke(packet, method);
        if (value == null) {
            value = ModernForgeEventBridge.invoke(packet, obfuscatedMethod);
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(packet, field);
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(packet, obfuscatedField);
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(packet, altField);
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(packet, altObfuscated);
        }
        return value instanceof Number ? (int) Math.round(((Number) value).intValue() * scale) : 0;
    }

    private static void scaleExplosionPacket(Object packet, double horizontal, double vertical) {
        Object push = firstValue(packet,
                new String[]{"getKnockbackX", "m_132995_", "getPlayerKnockbackX", "getXPower"},
                new String[]{"knockbackX", "f_132984_", "xPower", "field_149152_f"});
        if (push instanceof Number || hasField(packet, "knockbackX", "f_132984_", "xPower", "field_149152_f")) {
            setDoubleOrFloat(packet, doubleValue(push, 0.0D) * horizontal,
                    "knockbackX", "f_132984_", "xPower", "field_149152_f");
            setDoubleOrFloat(packet, doubleValue(firstValue(packet,
                    new String[]{"getKnockbackY", "m_132996_", "getPlayerKnockbackY", "getYPower"},
                    new String[]{"knockbackY", "f_132985_", "yPower", "field_149153_g"}), 0.0D) * vertical,
                    "knockbackY", "f_132985_", "yPower", "field_149153_g");
            setDoubleOrFloat(packet, doubleValue(firstValue(packet,
                    new String[]{"getKnockbackZ", "m_132997_", "getPlayerKnockbackZ", "getZPower"},
                    new String[]{"knockbackZ", "f_132986_", "zPower", "field_149159_h"}), 0.0D) * horizontal,
                    "knockbackZ", "f_132986_", "zPower", "field_149159_h");
        }
    }

    private static void zeroExplosion(Object packet) {
        setDoubleOrFloat(packet, 0.0D, "knockbackX", "f_132984_", "xPower", "field_149152_f");
        setDoubleOrFloat(packet, 0.0D, "knockbackY", "f_132985_", "yPower", "field_149153_g");
        setDoubleOrFloat(packet, 0.0D, "knockbackZ", "f_132986_", "zPower", "field_149159_h");
    }

    private static boolean shouldDelayBacktrack(Object packet, Object level, Object player) {
        String mode = ModernForgeEventBridge.mode("Backtrack", "Mode", "Hybrid");
        if (!"Packet".equalsIgnoreCase(mode) && !"Hybrid".equalsIgnoreCase(mode)) {
            return false;
        }
        long delay = Math.round(ModernForgeEventBridge.number("Backtrack", "Packet Delay", 120.0D));
        if (delay <= 0L || BACKTRACK_PACKETS.size() >= MAX_BACKTRACK_PACKETS) {
            return false;
        }
        int id = movingEntityId(packet);
        if (id < 0 || id == ModernRotationBridge.entityId(player)) {
            return false;
        }
        Object entity = entityById(level, id);
        if (entity == null) {
            return false;
        }
        double range = ModernForgeEventBridge.number("Backtrack", "Range", 3.6D) + 1.0D;
        return ModernRotationBridge.distanceSq(player, entity) <= range * range && isAllowedTarget(entity, "Backtrack");
    }

    private static int movingEntityId(Object packet) {
        String name = className(packet);
        if (!name.contains("clientboundmoveentitypacket")
                && !name.contains("clientboundteleportentitypacket")
                && !name.contains("s14packetentity")
                && !name.contains("s18packetentityteleport")) {
            return -1;
        }
        Object value = firstValue(packet,
                new String[]{"getId", "m_133182_", "getEntityId", "m_131525_"},
                new String[]{"id", "entityId", "f_131490_", "field_149451_a"});
        return intValue(value, -1);
    }

    private static void queueBacktrack(ChannelHandlerContext ctx, Object packet) {
        if (ctx == null || packet == null) {
            return;
        }
        if (BACKTRACK_PACKETS.size() >= MAX_BACKTRACK_PACKETS) {
            releaseBacktrackPackets();
            return;
        }
        long delay = Math.max(0L, Math.round(ModernForgeEventBridge.number("Backtrack", "Packet Delay", 120.0D)));
        BACKTRACK_PACKETS.offer(new QueuedIncoming(ctx, packet, System.currentTimeMillis() + delay));
    }

    private static void releaseDueBacktrackPackets() {
        long now = System.currentTimeMillis();
        while (true) {
            QueuedIncoming queued = BACKTRACK_PACKETS.peek();
            if (queued == null || queued.releaseAt > now) {
                break;
            }
            BACKTRACK_PACKETS.poll();
            fireIncoming(queued);
        }
    }

    private static void releaseBacktrackPackets() {
        QueuedIncoming queued;
        while ((queued = BACKTRACK_PACKETS.poll()) != null) {
            fireIncoming(queued);
        }
    }

    private static void fireIncoming(final QueuedIncoming queued) {
        if (queued == null || queued.ctx == null || queued.packet == null) {
            return;
        }
        queued.ctx.executor().execute(new Runnable() {
            @Override
            public void run() {
                queued.ctx.fireChannelRead(queued.packet);
            }
        });
    }

    private static void recordBacktrackHistory(Object minecraft, Object player) {
        if (!ModernForgeEventBridge.enabled("Backtrack")) {
            BACKTRACK_HISTORY.clear();
            return;
        }
        String mode = ModernForgeEventBridge.mode("Backtrack", "Mode", "Hybrid");
        if ("Packet".equalsIgnoreCase(mode)) {
            cleanupBacktrackHistory(0L);
            return;
        }
        long now = System.currentTimeMillis();
        long historyMs = Math.max(50L, Math.round(ModernForgeEventBridge.number("Backtrack", "History MS", 180.0D)));
        double range = ModernForgeEventBridge.number("Backtrack", "Range", 3.6D) + 1.0D;
        for (Object entity : ModernMinecraftAccess.livingEntities(minecraft)) {
            if (entity == null || entity == player || !isAllowedTarget(entity, "Backtrack")) {
                continue;
            }
            if (ModernRotationBridge.distanceSq(player, entity) > range * range) {
                continue;
            }
            int id = ModernRotationBridge.entityId(entity);
            if (id < 0) {
                continue;
            }
            ArrayDeque<TrackedBox> boxes = BACKTRACK_HISTORY.get(Integer.valueOf(id));
            if (boxes == null) {
                boxes = new ArrayDeque<TrackedBox>();
                BACKTRACK_HISTORY.put(Integer.valueOf(id), boxes);
            }
            ModernRaycastBridge.Box box = ModernRaycastBridge.entityBox(entity, 0.0D);
            if (box != null) {
                boxes.addLast(new TrackedBox(now, entity, box,
                        ModernRotationBridge.x(entity), ModernRotationBridge.y(entity), ModernRotationBridge.z(entity)));
            }
            while (boxes.size() > MAX_HISTORY || !boxes.isEmpty() && now - boxes.peekFirst().time > historyMs) {
                boxes.pollFirst();
            }
        }
        cleanupBacktrackHistory(historyMs + HISTORY_CLEANUP_GRACE_MS);
    }

    private static void cleanupBacktrackHistory(long maxAgeMs) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, ArrayDeque<TrackedBox>>> iterator = BACKTRACK_HISTORY.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ArrayDeque<TrackedBox>> entry = iterator.next();
            ArrayDeque<TrackedBox> boxes = entry.getValue();
            if (boxes == null || boxes.isEmpty() || maxAgeMs <= 0L || now - boxes.peekLast().time > maxAgeMs) {
                iterator.remove();
            }
        }
    }

    private static void tickFakeLag(Object player) {
        boolean enabled = ModernForgeEventBridge.enabled("FakeLag");
        fakeLagAllowed = enabled && shouldLagNow(player);
        long now = System.currentTimeMillis();
        if (!enabled || !fakeLagAllowed) {
            releaseFakeLagPackets();
            fakeLagNextPulseAt = now + fakeLagDelay();
            fakeLagReleaseAfterAttackAt = 0L;
            return;
        }
        releaseDueFakeLagPackets();
        long pulse = Math.round(ModernForgeEventBridge.number("FakeLag", "Pulse", 200.0D));
        if (pulse > 0L && now >= fakeLagNextPulseAt) {
            releaseFakeLagPackets();
            fakeLagNextPulseAt = now + Math.max(50L, pulse);
        }
        if (fakeLagReleaseAfterAttackAt > 0L && now >= fakeLagReleaseAfterAttackAt) {
            fakeLagReleaseAfterAttackAt = 0L;
            releaseFakeLagPackets();
            fakeLagNextPulseAt = now + fakeLagDelay();
        }
    }

    private static boolean shouldLagNow(Object player) {
        if (ModernForgeEventBridge.bool("FakeLag", "Combat Only", true) && !ModernCombatBridge.hasCombatFocus()) {
            return false;
        }
        if (!ModernForgeEventBridge.bool("FakeLag", "Only Moving", false)) {
            return true;
        }
        return ModernMovementBridge.isMovingForBridge(player);
    }

    private static boolean shouldQueueFakeLag(Object packet, Object player) {
        if (fakeLagReleaseInProgress || !fakeLagAllowed || packet == null || player == null || fakeLagDelay() <= 0L) {
            return false;
        }
        String name = className(packet);
        if (name.contains("serverboundinteractpacket")) {
            if (ModernForgeEventBridge.bool("FakeLag", "Release On Attack", true)) {
                fakeLagReleaseAfterAttackAt = Math.max(fakeLagReleaseAfterAttackAt, System.currentTimeMillis() + 45L);
            }
            return false;
        }
        if (name.contains("serverboundmoveplayerpacket")) {
            return true;
        }
        if (name.contains("serverboundplayercommandpacket")
                || name.contains("serverboundswingpacket")
                || name.contains("serverbounduseitem")
                || name.contains("serverboundsetcarrieditem")
                || name.contains("serverboundplayerinputpacket")) {
            return true;
        }
        return !ModernForgeEventBridge.bool("FakeLag", "Movement Only", true)
                && name.startsWith("net.minecraft.network.protocol.game.serverbound");
    }

    private static boolean queueFakeLag(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {
        if (ctx == null || packet == null || promise == null) {
            return false;
        }
        if (FAKELAG_PACKETS.size() >= MAX_FAKELAG_PACKETS) {
            releaseFakeLagPackets();
            return false;
        }
        long releaseAt = System.currentTimeMillis() + fakeLagDelay();
        FAKELAG_PACKETS.offer(new QueuedOutgoing(ctx, packet, promise, releaseAt));
        return true;
    }

    private static void releaseDueFakeLagPackets() {
        long now = System.currentTimeMillis();
        while (true) {
            QueuedOutgoing queued = FAKELAG_PACKETS.peek();
            if (queued == null || queued.releaseAt > now) {
                break;
            }
            FAKELAG_PACKETS.poll();
            writeOutgoing(queued);
        }
    }

    private static void releaseFakeLagPackets() {
        QueuedOutgoing queued;
        while ((queued = FAKELAG_PACKETS.poll()) != null) {
            writeOutgoing(queued);
        }
    }

    private static void writeOutgoing(final QueuedOutgoing queued) {
        if (queued == null || queued.ctx == null || queued.packet == null || queued.promise == null) {
            return;
        }
        queued.ctx.executor().execute(new Runnable() {
            @Override
            public void run() {
                fakeLagReleaseInProgress = true;
                try {
                    markBypass(queued.packet);
                    queued.ctx.writeAndFlush(queued.packet, queued.promise);
                } finally {
                    fakeLagReleaseInProgress = false;
                }
            }
        });
    }

    private static long fakeLagDelay() {
        return Math.max(0L, Math.round(ModernForgeEventBridge.number("FakeLag", "Delay", 120.0D)));
    }

    private static boolean isAllowedTarget(Object entity, String module) {
        if (!isAlive(entity)) {
            return false;
        }
        if (isPlayer(entity)) {
            return ModernForgeEventBridge.bool(module, "Players", true);
        }
        if (isAnimal(entity)) {
            return ModernForgeEventBridge.bool(module, "Animals", false);
        }
        return ModernForgeEventBridge.bool(module, "Mobs", false);
    }

    private static boolean isAlive(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "isAlive");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_6084_");
        }
        if (value instanceof Boolean && !((Boolean) value).booleanValue()) {
            return false;
        }
        Object health = ModernForgeEventBridge.invoke(entity, "getHealth");
        if (health == null) {
            health = ModernForgeEventBridge.invoke(entity, "m_21223_");
        }
        return !(health instanceof Number) || ((Number) health).floatValue() > 0.0F;
    }

    private static boolean isPlayer(Object entity) {
        return isInstance(entity, "net.minecraft.world.entity.player.Player");
    }

    private static boolean isAnimal(Object entity) {
        return isInstance(entity, "net.minecraft.world.entity.animal.Animal")
                || isInstance(entity, "net.minecraft.world.entity.animal.WaterAnimal")
                || isInstance(entity, "net.minecraft.world.entity.ambient.AmbientCreature")
                || isInstance(entity, "net.minecraft.world.entity.npc.Villager");
    }

    private static boolean isInstance(Object object, String className) {
        Class<?> type = ModernRotationBridge.classForName(className);
        return type != null && object != null && type.isInstance(object);
    }

    private static Object entityById(Object level, int id) {
        Object value = ModernForgeEventBridge.invoke(level, "getEntity", Integer.valueOf(id));
        if (value == null) {
            value = ModernForgeEventBridge.invoke(level, "m_6815_", Integer.valueOf(id));
        }
        return value;
    }

    private static Field findField(Class<?> owner, String... names) {
        Class<?> current = owner;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean hasField(Object object, String... names) {
        return object != null && findField(object.getClass(), names) != null;
    }

    private static void setInt(Object target, int value, String... names) {
        Field field = findField(target.getClass(), names);
        if (field == null) {
            return;
        }
        try {
            field.setInt(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setDoubleOrFloat(Object target, double value, String... names) {
        Field field = findField(target.getClass(), names);
        if (field == null) {
            return;
        }
        try {
            if (field.getType() == float.class || field.getType() == Float.class) {
                field.setFloat(target, (float) value);
            } else if (field.getType() == double.class || field.getType() == Double.class) {
                field.setDouble(target, value);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object invokeOrField(Object target, String method, String obfuscatedMethod,
                                        String field, String obfuscatedField) {
        Object value = ModernForgeEventBridge.invoke(target, method);
        if (value == null) {
            value = ModernForgeEventBridge.invoke(target, obfuscatedMethod);
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(target, field);
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(target, obfuscatedField);
        }
        return value;
    }

    private static Object firstValue(Object target, String[] methods, String[] fields) {
        if (target == null) {
            return null;
        }
        for (String method : methods) {
            Object value = ModernForgeEventBridge.invoke(target, method);
            if (value != null) {
                return value;
            }
        }
        for (String field : fields) {
            Object value = ModernForgeEventBridge.field(target, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static double doubleValue(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static String className(Object packet) {
        return packet == null ? "" : packet.getClass().getName().toLowerCase(Locale.ROOT);
    }

    private static boolean isEndPhase(Object event) {
        Object phase = ModernForgeEventBridge.field(event, "phase");
        if (phase == null) {
            phase = ModernForgeEventBridge.invoke(event, "phase");
        }
        if (phase == null) {
            phase = ModernForgeEventBridge.invoke(event, "getPhase");
        }
        return phase == null || "END".equals(String.valueOf(phase));
    }

    static final class TrackedBox {
        final long time;
        final Object entity;
        final ModernRaycastBridge.Box box;
        final double x;
        final double y;
        final double z;

        TrackedBox(long time, Object entity, ModernRaycastBridge.Box box, double x, double y, double z) {
            this.time = time;
            this.entity = entity;
            this.box = box;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class QueuedIncoming {
        final ChannelHandlerContext ctx;
        final Object packet;
        final long releaseAt;

        QueuedIncoming(ChannelHandlerContext ctx, Object packet, long releaseAt) {
            this.ctx = ctx;
            this.packet = packet;
            this.releaseAt = releaseAt;
        }
    }

    private static final class QueuedOutgoing {
        final ChannelHandlerContext ctx;
        final Object packet;
        final ChannelPromise promise;
        final long releaseAt;

        QueuedOutgoing(ChannelHandlerContext ctx, Object packet, ChannelPromise promise, long releaseAt) {
            this.ctx = ctx;
            this.packet = packet;
            this.promise = promise;
            this.releaseAt = releaseAt;
        }
    }

    private static final class PacketHandler extends ChannelDuplexHandler {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            Object packet = onOutgoing(msg, ctx, promise);
            if (packet == null) {
                return;
            }
            super.write(ctx, packet, promise);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (onIncoming(msg, ctx)) {
                return;
            }
            super.channelRead(ctx, msg);
        }
    }
}
