package gq.yozakura.module.render;

import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.event.bridge.Render3DEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Floating damage readouts driven by observed health changes. */
public final class DamageNumbers extends Module {
    private static final long LIFETIME_MILLIS = 850L;
    private static final float WORLD_SCALE = 0.025F;

    private final Option<Boolean> showSelf = new Option<Boolean>("Show Self", "ShowSelf", false);
    private final Numbers<Double> textScale = new Numbers<Double>("Scale", "Scale", 1.0D, 0.6D, 1.8D, 0.05D);
    private final Numbers<Double> red = new Numbers<Double>("DamageRed", "DamageRed", 255.0D, 0.0D, 255.0D, 1.0D);
    private final Numbers<Double> green = new Numbers<Double>("DamageGreen", "DamageGreen", 88.0D, 0.0D, 255.0D, 1.0D);
    private final Numbers<Double> blue = new Numbers<Double>("DamageBlue", "DamageBlue", 95.0D, 0.0D, 255.0D, 1.0D);

    private final Map<Integer, Float> previousHealth = new HashMap<Integer, Float>();
    private final List<DamageNumber> active = new ArrayList<DamageNumber>();

    public DamageNumbers() {
        super("DamageNumbers", Keyboard.KEY_NONE, ModuleType.Render,
                "Show animated damage numbers above hurt entities");
        Chinese = "伤害数字";
        addValues(showSelf, textScale, red, green, blue);
    }

    @Override
    public void enable() {
        previousHealth.clear();
        active.clear();
    }

    @Override
    public void disable() {
        previousHealth.clear();
        active.clear();
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!isInGame()) {
            return;
        }
        long now = System.currentTimeMillis();
        observeHealth(now);
        renderActive(now, event.getPartialTicks());
    }

    private void observeHealth(long now) {
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase entity = (EntityLivingBase) object;
            int id = entity.getEntityId();
            float health = Math.max(0.0F, entity.getHealth());
            Float previous = previousHealth.put(id, health);
            if (previous == null || health >= previous - 0.01F || health <= 0.0F) {
                continue;
            }
            if (entity == mc.thePlayer && !Boolean.TRUE.equals(showSelf.getValue())) {
                continue;
            }
            active.add(new DamageNumber(entity, previous - health, now));
        }
        Iterator<Map.Entry<Integer, Float>> healthIterator = previousHealth.entrySet().iterator();
        while (healthIterator.hasNext()) {
            if (!containsEntity(healthIterator.next().getKey())) {
                healthIterator.remove();
            }
        }
    }

    private boolean containsEntity(int entityId) {
        for (Object object : mc.theWorld.loadedEntityList) {
            if (object instanceof Entity && ((Entity) object).getEntityId() == entityId) {
                return true;
            }
        }
        return false;
    }

    private void renderActive(long now, float partialTicks) {
        if (active.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableTexture2D();
            for (Iterator<DamageNumber> iterator = active.iterator(); iterator.hasNext();) {
                DamageNumber number = iterator.next();
                float progress = (now - number.startedAt) / (float) LIFETIME_MILLIS;
                if (progress >= 1.0F) {
                    iterator.remove();
                    continue;
                }
                EntityLivingBase entity = number.entity;
                if (entity == null || entity.isDead) {
                    iterator.remove();
                    continue;
                }
                float eased = progress * progress * (3.0F - 2.0F * progress);
                float alpha = 1.0F - progress * progress;
                double x = interpolate(entity.lastTickPosX, entity.posX, partialTicks)
                        - mc.getRenderManager().viewerPosX + number.offsetX * (1.0F - eased);
                double y = interpolate(entity.lastTickPosY, entity.posY, partialTicks)
                        - mc.getRenderManager().viewerPosY + entity.height + 0.45D + eased * 0.65D;
                double z = interpolate(entity.lastTickPosZ, entity.posZ, partialTicks)
                        - mc.getRenderManager().viewerPosZ + number.offsetZ * (1.0F - eased);
                GlStateManager.pushMatrix();
                GlStateManager.translate(x, y, z);
                GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
                float pitch = mc.gameSettings.thirdPersonView == 2
                        ? -mc.getRenderManager().playerViewX : mc.getRenderManager().playerViewX;
                GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
                float scale = WORLD_SCALE * textScale.getValue().floatValue();
                GlStateManager.scale(-scale, -scale, scale);
                String text = formatDamage(number.amount);
                int color = withAlpha(color(), Math.round(255.0F * alpha));
                FontLoaders.circularMedium(14).drawCenteredStringWithShadow(text, 0.0D, 0.0D, color);
                GlStateManager.popMatrix();
            }
        } finally {
            GL11.glPopAttrib();
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private int color() {
        return red.getValue().intValue() << 16 | green.getValue().intValue() << 8 | blue.getValue().intValue();
    }

    private static String formatDamage(float amount) {
        if (amount >= 10.0F || Math.abs(amount - Math.round(amount)) < 0.01F) {
            return "-" + Math.round(amount);
        }
        return String.format(java.util.Locale.ROOT, "-%.1f", amount);
    }

    private static float interpolate(double previous, double current, float partialTicks) {
        return (float) (previous + (current - previous) * partialTicks);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | Math.max(0, Math.min(255, alpha)) << 24;
    }

    private static final class DamageNumber {
        private final EntityLivingBase entity;
        private final float amount;
        private final long startedAt;
        private final float offsetX;
        private final float offsetZ;

        private DamageNumber(EntityLivingBase entity, float amount, long startedAt) {
            this.entity = entity;
            this.amount = Math.max(0.01F, amount);
            this.startedAt = startedAt;
            this.offsetX = (entity.getEntityId() * 31 % 9 - 4) * 0.035F;
            this.offsetZ = (entity.getEntityId() * 17 % 9 - 4) * 0.035F;
        }
    }
}
