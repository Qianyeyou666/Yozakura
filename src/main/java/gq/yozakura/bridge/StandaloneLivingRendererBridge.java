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
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Map;

public final class StandaloneLivingRendererBridge {
    private static boolean installLogged;
    private static boolean failureLogged;

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
            entry.setValue(new PlayerRenderWrapper(manager, skin, (RenderPlayer) base));
            changed = true;
        }
        return changed;
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

    private static boolean isLiving(Entity entity) {
        return entity instanceof EntityLivingBase;
    }

    private static void dispatchPre(Entity entity, float partialTicks) {
        if (isLiving(entity)) {
            EventManager.call(new gq.yozakura.bridge.forge.RenderLivingEvent.Pre(entity, partialTicks));
        }
    }

    private static void dispatchPost(Entity entity, float partialTicks) {
        if (isLiving(entity)) {
            EventManager.call(new gq.yozakura.bridge.forge.RenderLivingEvent.Post(entity, partialTicks));
        }
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
            dispatchPre(entity, partialTicks);
            try {
                delegate.doRender(entity, x, y, z, entityYaw, partialTicks);
            } finally {
                dispatchPost(entity, partialTicks);
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
                try {
                    dispatchPre(entity, partialTicks);
                    delegate.doRender(entity, x, y, z, entityYaw, partialTicks);
                } finally {
                    dispatchPost(entity, partialTicks);
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
