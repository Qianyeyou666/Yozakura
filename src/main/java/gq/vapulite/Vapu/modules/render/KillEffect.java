package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class KillEffect extends Module {
    public enum EffectMode {
        SAKURA
    }

    private static final long TRACK_MS = 4200L;
    private static final long EFFECT_MS = 1350L;

    private final Mode<EffectMode> mode = new Mode<EffectMode>("Mode", "Mode", EffectMode.values(), EffectMode.SAKURA);
    private final Numbers<Double> scale = new Numbers<Double>("Scale", "Scale", 1.0, 0.55, 1.8, 0.05);
    private final Numbers<Double> density = new Numbers<Double>("Density", "Density", 1.0, 0.55, 1.65, 0.05);
    private final Option<Boolean> glow = new Option<Boolean>("Glow", "Glow", true);

    private final Map<Integer, Long> attacked = new HashMap<Integer, Long>();
    private final ArrayList<BloomEffect> effects = new ArrayList<BloomEffect>();
    private long lastFrameMS = System.currentTimeMillis();

    public KillEffect() {
        super("KillEffect", Keyboard.KEY_NONE, ModuleType.Render, "Play a visual effect when a target dies");
        this.addValues(mode, scale, density, glow);
        Chinese = "击杀特效";
    }

    @Override
    public void enable() {
        attacked.clear();
        effects.clear();
        lastFrameMS = System.currentTimeMillis();
    }

    @Override
    public void disable() {
        attacked.clear();
        effects.clear();
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isInGame() || event.entityPlayer != mc.thePlayer || !(event.target instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase target = (EntityLivingBase) event.target;
        if (target == mc.thePlayer) {
            return;
        }
        attacked.put(target.getEntityId(), System.currentTimeMillis());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!isInGame()) {
            attacked.clear();
            effects.clear();
            return;
        }

        Iterator<Map.Entry<Integer, Long>> iterator = attacked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            if (now - entry.getValue() > TRACK_MS) {
                iterator.remove();
                continue;
            }
            Entity entity = mc.theWorld.getEntityByID(entry.getKey());
            if (entity instanceof EntityLivingBase) {
                EntityLivingBase living = (EntityLivingBase) entity;
                if (living.isDead || living.deathTime > 0 || living.getHealth() <= 0.0f) {
                    spawnEffect(living.posX, living.posY + Math.max(0.35f, living.height * 0.55f), living.posZ);
                    iterator.remove();
                }
            } else if (entity == null) {
                iterator.remove();
            }
        }

        Iterator<BloomEffect> effectsIterator = effects.iterator();
        while (effectsIterator.hasNext()) {
            if (now - effectsIterator.next().started > EFFECT_MS) {
                effectsIterator.remove();
            }
        }
    }

    @SubscribeEvent
    public void onWorld(RenderWorldLastEvent event) {
        if (!isInGame() || effects.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        updateFrame(now);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);

        try {
            for (BloomEffect effect : effects) {
                float progress = (now - effect.started) / (float) EFFECT_MS;
                if (progress >= 0.0f && progress <= 1.0f) {
                    drawSakuraBloom(effect, progress, event.partialTicks);
                }
            }
        } finally {
            GL11.glDepthMask(true);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void spawnEffect(double x, double y, double z) {
        if (mode.getValue() == EffectMode.SAKURA) {
            effects.add(new BloomEffect(x, y, z, System.currentTimeMillis()));
        }
        while (effects.size() > 8) {
            effects.remove(0);
        }
    }

    private void drawSakuraBloom(BloomEffect effect, float progress, float partialTicks) {
        double x = effect.x - mc.getRenderManager().viewerPosX;
        double y = effect.y - mc.getRenderManager().viewerPosY;
        double z = effect.z - mc.getRenderManager().viewerPosZ;
        float open = easeOut(MathHelper.clamp_float(progress / 0.46f, 0.0f, 1.0f));
        float bloomHold = 1.0f - smoothStep(0.74f, 1.0f, progress);
        float fade = MathHelper.clamp_float(Math.min(1.0f, progress / 0.12f) * bloomHold, 0.0f, 1.0f);
        float localScale = scale.getValue().floatValue();
        float bloomRadius = (0.46f + open * 1.72f) * localScale;
        float flowerSize = (0.56f + open * 0.84f) * localScale;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        if (Boolean.TRUE.equals(glow.getValue())) {
            drawGroundBloom(bloomRadius, fade);
            drawCoreGlow(0.36f + open * 0.70f, fade);
        }

        drawOpeningSakuraFlower(0.0f, 0.36f + open * 0.18f, 0.0f, flowerSize,
                effect.seed * 0.21f + progress * 54.0f, open, Math.min(1.0f, fade * 1.35f));

        int count = Math.max(4, Math.round(7.0f * density.getValue().floatValue()));
        for (int i = 0; i < count; i++) {
            float seed = i * 12.9898f + effect.seed;
            float angle = (float) (Math.PI * 2.0D * i / count + seed * 0.03f);
            float lift = 0.24f + 0.40f * open + 0.12f * (float) Math.sin(seed);
            float spread = bloomRadius * (0.35f + 0.23f * pseudo(seed));
            float swirl = angle + open * (0.72f + 0.35f * pseudo(seed * 1.7f));
            float px = (float) Math.cos(swirl) * spread;
            float py = lift + 0.10f * (float) Math.sin(open * Math.PI + seed);
            float pz = (float) Math.sin(swirl) * spread;
            float size = (0.090f + 0.040f * pseudo(seed * 2.3f)) * localScale;
            int color = i % 3 == 0 ? 0xFFFF78B7 : i % 3 == 1 ? 0xFFFFA8CF : 0xFFFFD4E6;
            drawPetalTrail(px, py, pz, angle, size, color, fade * 0.72f, progress);
            drawSakuraFlower(px, py, pz, size, angle * 57.29578f + open * 128.0f, color,
                    Math.min(1.0f, fade * 1.15f), false);
        }
        GL11.glPopMatrix();
    }

    private void drawOpeningSakuraFlower(float x, float y, float z, float size, float spin, float open, float alpha) {
        if (alpha <= 0.002f || size <= 0.002f) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, z);
        GL11.glRotatef(-mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
        GL11.glRotatef(spin, 0.0f, 0.0f, 1.0f);

        if (Boolean.TRUE.equals(glow.getValue())) {
            drawBillboardGlow(size * (3.15f + open * 1.35f), 0xFFFF4FA6, alpha * 0.50f, 36);
            drawBillboardGlow(size * (2.12f + open * 0.88f), 0xFFFF9BCC, alpha * 0.58f, 32);
            drawBillboardGlow(size * 1.10f, 0xFFFFFFFF, alpha * 0.28f, 28);
        }

        for (int i = 0; i < 5; i++) {
            GL11.glPushMatrix();
            GL11.glRotatef(i * 72.0f, 0.0f, 0.0f, 1.0f);
            GL11.glTranslatef(0.0f, size * (0.04f + open * 0.33f), 0.0f);
            GL11.glScalef(0.48f + open * 0.52f, 0.46f + open * 0.54f, 1.0f);
            drawPetalShape(size, i % 2 == 0 ? 0xFFFF74B7 : 0xFFFFA3CC, alpha, false);
            drawPetalRidge(size, alpha * 0.62f);
            GL11.glPopMatrix();
        }

        drawBillboardGlow(size * 0.46f, 0xFFFFF0A8, alpha * 0.95f, 18);
        drawBillboardGlow(size * 0.18f, 0xFFFFFFFF, alpha * 0.82f, 14);
        GL11.glPopMatrix();
    }

    private void drawPetalTrail(float x, float y, float z, float angle, float size, int color, float fade, float progress) {
        int samples = 6;
        for (int i = samples; i >= 1; i--) {
            float t = i / (float) samples;
            float back = t * (0.16f + progress * 0.34f);
            float tx = x - (float) Math.cos(angle) * back;
            float ty = y - t * 0.12f;
            float tz = z - (float) Math.sin(angle) * back;
            float alpha = fade * (1.0f - t) * (1.0f - t) * 0.38f;
            drawSakuraFlower(tx, ty, tz, size * (0.42f + 0.35f * (1.0f - t)), angle * 57.29578f,
                    color, alpha, true);
        }
    }

    private void drawGroundBloom(float radius, float alpha) {
        int segments = 80;
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            float u = i / (float) segments;
            setColor(0xFFFF7BB8, alpha * 0.14f);
            GL11.glTexCoord2f(u, 0.0f);
            GL11.glVertex3d(cos * radius * 0.62f, -0.38D, sin * radius * 0.62f);
            setColor(0xFFFFD2E4, alpha * 0.02f);
            GL11.glTexCoord2f(u, 1.0f);
            GL11.glVertex3d(cos * radius * 1.22f, -0.38D, sin * radius * 1.22f);
        }
        GL11.glEnd();
    }

    private void drawCoreGlow(float radius, float alpha) {
        GL11.glPushMatrix();
        GL11.glRotatef(-mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
        drawBillboardGlow(radius * 2.8f, 0xFFFF8DBF, alpha * 0.30f, 24);
        drawBillboardGlow(radius * 1.4f, 0xFFFFFFFF, alpha * 0.28f, 20);
        GL11.glPopMatrix();
    }

    private void drawSakuraFlower(float x, float y, float z, float size, float spin, int color, float alpha, boolean trail) {
        if (alpha <= 0.002f || size <= 0.002f) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, z);
        GL11.glRotatef(-mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
        GL11.glRotatef(spin, 0.0f, 0.0f, 1.0f);
        if (!trail && Boolean.TRUE.equals(glow.getValue())) {
            drawBillboardGlow(size * 3.0f, 0xFFFF7DB8, alpha * 0.20f, 18);
        }
        for (int i = 0; i < 5; i++) {
            GL11.glPushMatrix();
            GL11.glRotatef(i * 72.0f, 0.0f, 0.0f, 1.0f);
            GL11.glTranslatef(0.0f, size * 0.20f, 0.0f);
            drawPetalShape(size, color, alpha, trail);
            GL11.glPopMatrix();
        }
        if (!trail) {
            drawBillboardGlow(size * 0.34f, 0xFFFFF0A8, alpha * 0.82f, 12);
        }
        GL11.glPopMatrix();
    }

    private void drawPetalShape(float size, int color, float alpha, boolean trail) {
        float width = size * (trail ? 0.46f : 0.58f);
        float length = size * (trail ? 0.96f : 1.12f);
        float[][] points = new float[][]{
                {0.00f, -0.18f}, {-0.30f, -0.07f}, {-0.64f, 0.25f}, {-0.66f, 0.62f},
                {-0.36f, 0.94f}, {-0.10f, 0.82f}, {0.00f, 0.74f}, {0.10f, 0.82f},
                {0.36f, 0.94f}, {0.66f, 0.62f}, {0.64f, 0.25f}, {0.30f, -0.07f}, {0.00f, -0.18f}
        };

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        setColor(0xFFFFEAF3, alpha * (trail ? 0.40f : 0.98f));
        GL11.glTexCoord2f(0.5f, 0.58f);
        GL11.glVertex3d(0.0D, length * 0.36D, 0.0D);
        int edgeColor = interpolate(color, 0xFFFFFFFF, trail ? 0.08f : 0.18f);
        for (int i = 0; i < points.length; i++) {
            double px = points[i][0] * width;
            double py = points[i][1] * length;
            float edgeAlpha = trail ? 0.12f : 0.56f;
            if (i == 5 || i == 6 || i == 7) {
                edgeAlpha *= 0.82f;
            }
            setColor(edgeColor, alpha * edgeAlpha);
            GL11.glTexCoord2f((float) (0.5D + px / Math.max(0.001f, width * 2.0f)),
                    (float) (0.5D + py / Math.max(0.001f, length * 2.1f)));
            GL11.glVertex3d(px, py, 0.0D);
        }
        GL11.glEnd();
    }

    private void drawPetalRidge(float size, float alpha) {
        if (alpha <= 0.002f) {
            return;
        }
        GL11.glLineWidth(1.2f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        setColor(0xFFFFFFFF, alpha * 0.20f);
        GL11.glVertex3d(0.0D, -size * 0.10D, 0.0D);
        setColor(0xFFFFF3F8, alpha * 0.38f);
        GL11.glVertex3d(0.0D, size * 0.42D, 0.0D);
        setColor(0xFFFF7DB8, alpha * 0.10f);
        GL11.glVertex3d(0.0D, size * 0.74D, 0.0D);
        GL11.glEnd();
    }

    private void drawBillboardGlow(float radius, int color, float alpha, int segments) {
        if (alpha <= 0.002f) {
            return;
        }
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        setColor(color, alpha);
        GL11.glTexCoord2f(0.5f, 0.5f);
        GL11.glVertex3d(0.0D, 0.0D, 0.0D);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            setColor(color, 0.0f);
            GL11.glTexCoord2f((float) (0.5D + Math.cos(angle) * 0.5D), (float) (0.5D + Math.sin(angle) * 0.5D));
            GL11.glVertex3d(Math.cos(angle) * radius, Math.sin(angle) * radius, 0.0D);
        }
        GL11.glEnd();
    }

    private int interpolate(int first, int second, float progress) {
        float t = MathHelper.clamp_float(progress, 0.0f, 1.0f);
        int r = Math.round(((first >> 16) & 255) + (((second >> 16) & 255) - ((first >> 16) & 255)) * t);
        int g = Math.round(((first >> 8) & 255) + (((second >> 8) & 255) - ((first >> 8) & 255)) * t);
        int b = Math.round((first & 255) + ((second & 255) - (first & 255)) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private void setColor(int color, float alpha) {
        GL11.glColor4f(((color >> 16) & 255) / 255.0f,
                ((color >> 8) & 255) / 255.0f,
                (color & 255) / 255.0f,
                MathHelper.clamp_float(alpha, 0.0f, 1.0f));
    }

    private float easeOut(float value) {
        float v = MathHelper.clamp_float(value, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - v, 4.0D);
    }

    private float smoothStep(float edge0, float edge1, float value) {
        float t = MathHelper.clamp_float((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private float pseudo(float seed) {
        return (float) (Math.sin(seed * 12.9898f) * 43758.5453D - Math.floor(Math.sin(seed * 12.9898f) * 43758.5453D));
    }

    private void updateFrame(long now) {
        lastFrameMS = now;
    }

    private static final class BloomEffect {
        final double x;
        final double y;
        final double z;
        final long started;
        final float seed;

        BloomEffect(double x, double y, double z, long started) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.started = started;
            this.seed = (float) ((x * 31.0D + y * 17.0D + z * 13.0D) % 1000.0D);
        }
    }
}
