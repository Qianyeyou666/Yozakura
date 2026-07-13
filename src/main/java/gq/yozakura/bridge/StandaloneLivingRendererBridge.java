package gq.yozakura.bridge;

import gq.yozakura.event.bus.EventManager;
import gq.yozakura.manager.VisualRotationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public final class StandaloneLivingRendererBridge {
    private static boolean installLogged;
    private static boolean failureLogged;
    private static boolean uninstallLogged;
    private static boolean uninstallFailureLogged;
    private static Field nameTagVisibilityField;
    private static boolean nameTagVisibilityFieldSearched;

    private StandaloneLivingRendererBridge() {
    }

    public static void install(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        try {
            RenderManager manager = minecraft.getRenderManager();
            if (manager == null) {
                return;
            }
            boolean changed = wrapEntityRenderers(manager);
            changed |= wrapPlayerRenderers(manager);
            changed |= wrapPlayerRendererFields(manager);
            if (changed && !installLogged) {
                installLogged = true;
                log("Standalone living renderer bridge installed", null);
            }
        } catch (Throwable throwable) {
            if (!failureLogged) {
                failureLogged = true;
                log("Failed to install standalone living renderer bridge", throwable);
            }
        }
    }

    private static boolean wrapEntityRenderers(RenderManager manager) {
        Map<Class<? extends Entity>, Render<? extends Entity>> map = manager.entityRenderMap;
        if (map == null) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<Class<? extends Entity>, Render<? extends Entity>> entry : map.entrySet()) {
            Render<? extends Entity> renderer = entry.getValue();
            if (renderer instanceof LivingRenderWrapper || renderer instanceof PlayerRenderWrapper) {
                continue;
            }
            Render<? extends Entity> base = unwrapAsRender(renderer);
            if (!(base instanceof RendererLivingEntity) || base instanceof RenderPlayer) {
                if (base != renderer) {
                    entry.setValue(base);
                    changed = true;
                }
                continue;
            }
            entry.setValue(new LivingRenderWrapper(manager, castLivingRenderer(base)));
            changed = true;
        }
        return changed;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean wrapPlayerRenderers(RenderManager manager) {
        Map skinMap = manager.getSkinMap();
        if (skinMap == null) {
            return false;
        }
        Map replacement = new HashMap(skinMap);
        boolean changed = false;
        for (Object object : skinMap.entrySet()) {
            Map.Entry entry = (Map.Entry) object;
            Object value = entry.getValue();
            if (value instanceof PlayerRenderWrapper) {
                continue;
            }
            Object base = unwrapRenderer(value);
            if (!(base instanceof RenderPlayer)) {
                continue;
            }
            String skin = String.valueOf(entry.getKey());
            replacement.put(entry.getKey(), new PlayerRenderWrapper(manager, skin, (RenderPlayer) base));
            changed = true;
        }
        if (!changed) {
            return false;
        }
        if (!replacePlayerSkinMap(manager, skinMap, replacement)) {
            throw new IllegalStateException("Unable to replace the standalone player skin renderer map");
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean replacePlayerSkinMap(RenderManager manager, Map skinMap, Map replacement) {
        Class<?> type = manager.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    Object value = field.get(manager);
                    if (!matchesPlayerSkinMap(value, skinMap)) {
                        continue;
                    }
                    try {
                        field.set(manager, replacement);
                    } catch (Throwable ignored) {
                        Map writable = (Map) value;
                        writable.clear();
                        writable.putAll(replacement);
                    }
                    return true;
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        try {
            skinMap.clear();
            skinMap.putAll(replacement);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void uninstall(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        try {
            RenderManager manager = minecraft.getRenderManager();
            if (manager == null) {
                return;
            }
            boolean changed = false;
            try {
                changed |= restoreEntityRenderers(manager);
            } catch (Throwable throwable) {
                logUninstallFailure("Failed to restore standalone entity renderer wrappers", throwable);
            }
            try {
                changed |= restorePlayerRenderers(manager);
            } catch (Throwable throwable) {
                logUninstallFailure("Failed to restore standalone player renderer wrappers", throwable);
            }
            try {
                changed |= restorePlayerRendererFields(manager);
            } catch (Throwable throwable) {
                logUninstallFailure("Failed to restore standalone player renderer fields", throwable);
            }
            if (changed && !uninstallLogged) {
                uninstallLogged = true;
                log("Standalone living renderer bridge removed", null);
            }
        } catch (Throwable throwable) {
            logUninstallFailure("Failed to remove standalone living renderer bridge", throwable);
        } finally {
            nameTagVisibilityField = null;
            nameTagVisibilityFieldSearched = false;
        }
    }

    private static boolean restoreEntityRenderers(RenderManager manager) {
        Map<Class<? extends Entity>, Render<? extends Entity>> map = manager.entityRenderMap;
        if (map == null) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<Class<? extends Entity>, Render<? extends Entity>> entry : map.entrySet()) {
            Render<? extends Entity> delegate = readOwnedDelegate(entry.getValue());
            if (delegate == null) {
                continue;
            }
            try {
                entry.setValue(delegate);
                changed = true;
            } catch (Throwable throwable) {
                logUninstallFailure("Failed to restore standalone renderer for " + entry.getKey().getName(),
                        throwable);
            }
        }
        return changed;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean restorePlayerRenderers(RenderManager manager) {
        Map skinMap = manager.getSkinMap();
        if (skinMap == null) {
            return false;
        }
        Map replacement = new HashMap(skinMap);
        boolean changed = false;
        for (Object object : skinMap.entrySet()) {
            Map.Entry entry = (Map.Entry) object;
            Object delegate = readOwnedDelegate(entry.getValue());
            if (!(delegate instanceof RenderPlayer)) {
                continue;
            }
            replacement.put(entry.getKey(), delegate);
            changed = true;
        }
        if (!changed) {
            return false;
        }
        if (!replacePlayerSkinMap(manager, skinMap, replacement)) {
            logUninstallFailure("Unable to restore standalone player skin renderer map", null);
            return false;
        }
        return true;
    }

    private static boolean restorePlayerRendererFields(RenderManager manager) {
        boolean changed = false;
        Class<?> type = manager.getClass();
        while (type != null && type != Object.class) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                if (!RenderPlayer.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    Object delegate = readOwnedDelegate(field.get(manager));
                    if (!(delegate instanceof RenderPlayer)) {
                        continue;
                    }
                    field.set(manager, delegate);
                    changed = true;
                } catch (Throwable throwable) {
                    logUninstallFailure("Failed to restore standalone player renderer field " + field.getName(),
                            throwable);
                }
            }
            type = type.getSuperclass();
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static Render<? extends Entity> readOwnedDelegate(Render<? extends Entity> renderer) {
        if (!(renderer instanceof LivingRenderWrapper) && !(renderer instanceof PlayerRenderWrapper)) {
            return null;
        }
        Object delegate = readDelegate(renderer);
        return delegate instanceof Render ? (Render<? extends Entity>) delegate : null;
    }

    private static Object readOwnedDelegate(Object renderer) {
        if (!(renderer instanceof LivingRenderWrapper) && !(renderer instanceof PlayerRenderWrapper)) {
            return null;
        }
        return readDelegate(renderer);
    }

    private static void logUninstallFailure(String message, Throwable throwable) {
        if (uninstallFailureLogged) {
            return;
        }
        uninstallFailureLogged = true;
        log(message, throwable);
    }

    @SuppressWarnings("rawtypes")
    private static boolean matchesPlayerSkinMap(Object candidate, Map exposed) {
        if (!(candidate instanceof Map)) {
            return false;
        }
        Map map = (Map) candidate;
        if (map.size() != exposed.size() || !map.keySet().equals(exposed.keySet())) {
            return false;
        }
        for (Object key : exposed.keySet()) {
            Object candidateRenderer = unwrapRenderer(map.get(key));
            Object exposedRenderer = unwrapRenderer(exposed.get(key));
            if (!(candidateRenderer instanceof RenderPlayer) || candidateRenderer != exposedRenderer) {
                return false;
            }
        }
        return true;
    }

    private static boolean wrapPlayerRendererFields(RenderManager manager) {
        boolean changed = false;
        Class<?> type = manager.getClass();
        while (type != null && type != Object.class) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                if (!RenderPlayer.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    Object value = field.get(manager);
                    if (value instanceof PlayerRenderWrapper) {
                        continue;
                    }
                    Object base = unwrapRenderer(value);
                    if (!(base instanceof RenderPlayer)) {
                        continue;
                    }
                    field.set(manager, new PlayerRenderWrapper(manager, "default", (RenderPlayer) base));
                    changed = true;
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static Render<? extends Entity> unwrapAsRender(Render<? extends Entity> renderer) {
        Object unwrapped = unwrapRenderer(renderer);
        return unwrapped instanceof Render ? (Render<? extends Entity>) unwrapped : renderer;
    }

    private static Object unwrapRenderer(Object renderer) {
        Object current = renderer;
        for (int i = 0; i < 6 && current != null; i++) {
            String name = current.getClass().getName();
            if (!name.endsWith("StandaloneLivingRendererBridge$LivingRenderWrapper")
                    && !name.endsWith("StandaloneLivingRendererBridge$PlayerRenderWrapper")) {
                break;
            }
            Object next = readDelegate(current);
            if (next == null || next == current) {
                break;
            }
            current = next;
        }
        return current;
    }

    private static Object readDelegate(Object wrapper) {
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

    @SuppressWarnings("unchecked")
    private static RendererLivingEntity<EntityLivingBase> castLivingRenderer(Render<? extends Entity> renderer) {
        return (RendererLivingEntity<EntityLivingBase>) renderer;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean dispatchPre(EntityLivingBase entity, RendererLivingEntity renderer,
                                       double x, double y, double z) {
        gq.yozakura.bridge.forge.RenderLivingEvent.Pre event =
                new gq.yozakura.bridge.forge.RenderLivingEvent.Pre(entity, renderer, x, y, z);
        EventManager.call(event);
        return event.isCanceled();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void dispatchPost(EntityLivingBase entity, RendererLivingEntity renderer,
                                     double x, double y, double z) {
        EventManager.call(new gq.yozakura.bridge.forge.RenderLivingEvent.Post(entity, renderer, x, y, z));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean dispatchSpecialsPre(EntityLivingBase entity, RendererLivingEntity renderer,
                                               double x, double y, double z) {
        gq.yozakura.bridge.forge.RenderLivingEvent.Specials.Pre event =
                new gq.yozakura.bridge.forge.RenderLivingEvent.Specials.Pre(entity, renderer, x, y, z);
        EventManager.call(event);
        return event.isCanceled();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void dispatchSpecialsPost(EntityLivingBase entity, RendererLivingEntity renderer,
                                             double x, double y, double z) {
        EventManager.call(new gq.yozakura.bridge.forge.RenderLivingEvent.Specials.Post(
                entity, renderer, x, y, z));
    }

    private static NameTagVisibilitySnapshot suppressNameTag(EntityLivingBase entity) {
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean hideGuiChanged = minecraft != null && minecraft.gameSettings != null;
        boolean previousHideGui = hideGuiChanged && minecraft.gameSettings.hideGUI;
        if (hideGuiChanged) {
            minecraft.gameSettings.hideGUI = true;
        }

        ScorePlayerTeam scoreTeam = null;
        Field visibilityField = null;
        Team.EnumVisible previousVisibility = null;
        try {
            Team team = entity == null ? null : entity.getTeam();
            if (team instanceof ScorePlayerTeam) {
                ScorePlayerTeam candidate = (ScorePlayerTeam) team;
                visibilityField = getNameTagVisibilityField();
                if (visibilityField != null) {
                    previousVisibility = (Team.EnumVisible) visibilityField.get(candidate);
                    visibilityField.set(candidate, Team.EnumVisible.NEVER);
                    scoreTeam = candidate;
                }
            }
        } catch (Throwable ignored) {
            visibilityField = null;
            previousVisibility = null;
        }
        return new NameTagVisibilitySnapshot(minecraft, hideGuiChanged, previousHideGui,
                scoreTeam, visibilityField, previousVisibility);
    }

    private static Field getNameTagVisibilityField() {
        if (nameTagVisibilityFieldSearched) {
            return nameTagVisibilityField;
        }
        nameTagVisibilityFieldSearched = true;
        Class<?> type = ScorePlayerTeam.class;
        while (type != null && type != Object.class) {
            for (String name : new String[]{"nameTagVisibility", "field_178778_i", "i"}) {
                try {
                    Field field = type.getDeclaredField(name);
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    nameTagVisibilityField = field;
                    return nameTagVisibilityField;
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable ignored) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static void log(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraStandalone.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(message);
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class LivingRenderWrapper extends RendererLivingEntity<EntityLivingBase> {
        private final RendererLivingEntity<EntityLivingBase> delegate;

        LivingRenderWrapper(RenderManager manager, RendererLivingEntity<EntityLivingBase> delegate) {
            super(manager, delegate.getMainModel(), 0.0F);
            this.delegate = delegate;
        }

        @Override
        public boolean shouldRender(EntityLivingBase entity, ICamera camera, double camX, double camY, double camZ) {
            return delegate.shouldRender(entity, camera, camX, camY, camZ);
        }

        @Override
        public void doRender(EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks) {
            if (dispatchPre(entity, delegate, x, y, z)) {
                return;
            }
            boolean specialsCancelled = dispatchSpecialsPre(entity, delegate, x, y, z);
            NameTagVisibilitySnapshot nameTagSnapshot = specialsCancelled
                    ? suppressNameTag(entity) : NameTagVisibilitySnapshot.NOOP;
            try {
                delegate.doRender(entity, x, y, z, entityYaw, partialTicks);
            } finally {
                nameTagSnapshot.restore();
                if (!specialsCancelled) {
                    dispatchSpecialsPost(entity, delegate, x, y, z);
                }
                dispatchPost(entity, delegate, x, y, z);
            }
        }

        @Override
        public void doRenderShadowAndFire(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
            delegate.doRenderShadowAndFire(entity, x, y, z, yaw, partialTicks);
        }

        @Override
        public void setRenderOutlines(boolean renderOutlinesIn) {
            super.setRenderOutlines(renderOutlinesIn);
            delegate.setRenderOutlines(renderOutlinesIn);
        }

        @Override
        public ModelBase getMainModel() {
            return delegate.getMainModel();
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityLivingBase entity) {
            return null;
        }
    }

    private static final class PlayerRenderWrapper extends RenderPlayer {
        private final RenderPlayer delegate;

        PlayerRenderWrapper(RenderManager manager, String skin, RenderPlayer delegate) {
            super(manager, "slim".equalsIgnoreCase(skin));
            this.delegate = delegate;
        }

        @Override
        public boolean shouldRender(AbstractClientPlayer entity, ICamera camera, double camX, double camY, double camZ) {
            return delegate.shouldRender(entity, camera, camX, camY, camZ);
        }

        @Override
        public void doRender(AbstractClientPlayer entity, double x, double y, double z, float entityYaw, float partialTicks) {
            VisualRotationSnapshot rotationSnapshot = VisualRotationSnapshot.apply(entity);
            try {
                if (dispatchPre(entity, delegate, x, y, z)) {
                    return;
                }
                boolean specialsCancelled = dispatchSpecialsPre(entity, delegate, x, y, z);
                NameTagVisibilitySnapshot nameTagSnapshot = specialsCancelled
                        ? suppressNameTag(entity) : NameTagVisibilitySnapshot.NOOP;
                try {
                    delegate.doRender(entity, x, y, z, entityYaw, partialTicks);
                } finally {
                    nameTagSnapshot.restore();
                    if (!specialsCancelled) {
                        dispatchSpecialsPost(entity, delegate, x, y, z);
                    }
                    dispatchPost(entity, delegate, x, y, z);
                }
            } finally {
                rotationSnapshot.restore(entity);
            }
        }

        @Override
        public void doRenderShadowAndFire(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
            delegate.doRenderShadowAndFire(entity, x, y, z, yaw, partialTicks);
        }

        @Override
        public void setRenderOutlines(boolean renderOutlinesIn) {
            super.setRenderOutlines(renderOutlinesIn);
            delegate.setRenderOutlines(renderOutlinesIn);
        }

        @Override
        public ModelPlayer getMainModel() {
            if (delegate == null) {
                return super.getMainModel();
            }
            return delegate.getMainModel();
        }

        @Override
        public void transformHeldFull3DItemLayer() {
            delegate.transformHeldFull3DItemLayer();
        }

        @Override
        public void renderRightArm(AbstractClientPlayer clientPlayer) {
            delegate.renderRightArm(clientPlayer);
        }

        @Override
        public void renderLeftArm(AbstractClientPlayer clientPlayer) {
            delegate.renderLeftArm(clientPlayer);
        }

        @Override
        protected ResourceLocation getEntityTexture(AbstractClientPlayer entity) {
            return entity.getLocationSkin();
        }
    }

    private static final class NameTagVisibilitySnapshot {
        private static final NameTagVisibilitySnapshot NOOP = new NameTagVisibilitySnapshot(
                null, false, false, null, null, null);

        private final Minecraft minecraft;
        private final boolean hideGuiChanged;
        private final boolean previousHideGui;
        private final ScorePlayerTeam scoreTeam;
        private final Field visibilityField;
        private final Team.EnumVisible previousVisibility;

        private NameTagVisibilitySnapshot(Minecraft minecraft, boolean hideGuiChanged, boolean previousHideGui,
                                          ScorePlayerTeam scoreTeam, Field visibilityField,
                                          Team.EnumVisible previousVisibility) {
            this.minecraft = minecraft;
            this.hideGuiChanged = hideGuiChanged;
            this.previousHideGui = previousHideGui;
            this.scoreTeam = scoreTeam;
            this.visibilityField = visibilityField;
            this.previousVisibility = previousVisibility;
        }

        void restore() {
            if (scoreTeam != null && visibilityField != null && previousVisibility != null) {
                try {
                    visibilityField.set(scoreTeam, previousVisibility);
                } catch (Throwable ignored) {
                }
            }
            if (hideGuiChanged && minecraft != null && minecraft.gameSettings != null) {
                minecraft.gameSettings.hideGUI = previousHideGui;
            }
        }
    }

    private static final class VisualRotationSnapshot {
        private static final VisualRotationSnapshot NOOP = new VisualRotationSnapshot(false, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F);

        private final boolean active;
        private final float prevPitch;
        private final float pitch;
        private final float prevYawHead;
        private final float yawHead;
        private final float prevRenderYawOffset;
        private final float renderYawOffset;

        private VisualRotationSnapshot(boolean active, float prevPitch, float pitch, float prevYawHead,
                                       float yawHead, float prevRenderYawOffset, float renderYawOffset) {
            this.active = active;
            this.prevPitch = prevPitch;
            this.pitch = pitch;
            this.prevYawHead = prevYawHead;
            this.yawHead = yawHead;
            this.prevRenderYawOffset = prevRenderYawOffset;
            this.renderYawOffset = renderYawOffset;
        }

        static VisualRotationSnapshot apply(AbstractClientPlayer entity) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || entity == null || entity != minecraft.thePlayer || !VisualRotationState.isActived()) {
                return NOOP;
            }
            VisualRotationSnapshot snapshot = new VisualRotationSnapshot(true,
                    entity.prevRotationPitch,
                    entity.rotationPitch,
                    entity.prevRotationYawHead,
                    entity.rotationYawHead,
                    entity.prevRenderYawOffset,
                    entity.renderYawOffset);
            entity.prevRotationPitch = VisualRotationState.getPrevRotationPitch();
            entity.rotationPitch = VisualRotationState.getRotationPitch();
            entity.prevRotationYawHead = VisualRotationState.getPrevRotationYawHead();
            entity.rotationYawHead = VisualRotationState.getRotationYawHead();
            entity.prevRenderYawOffset = VisualRotationState.getPrevRenderYawOffset();
            entity.renderYawOffset = VisualRotationState.getRenderYawOffset();
            return snapshot;
        }

        void restore(AbstractClientPlayer entity) {
            if (!active || entity == null) {
                return;
            }
            entity.prevRotationPitch = prevPitch;
            entity.rotationPitch = pitch;
            entity.prevRotationYawHead = prevYawHead;
            entity.rotationYawHead = yawHead;
            entity.prevRenderYawOffset = prevRenderYawOffset;
            entity.renderYawOffset = renderYawOffset;
        }
    }
}
