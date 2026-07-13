package gq.yozakura.ui.overlay;

import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.glow.GlowProfile;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.Priority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class InjectionSuccessAnimation {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int SAKURA = 0xFFFFB7D1;
    private static final int SAKURA_STRONG = 0xFFFF80B3;
    private static final int SAKURA_SOFT = 0xFFFFDCEB;
    private static final int TEXT = 0xFFFFF6FA;
    private static final int MUTED = 0xFFD8C8D2;
    private static final long DURATION_MS = 2000L;
    private static final long INTRO_MS = 260L;
    private static final long OUTRO_MS = 300L;
    private static final float[][] SAKURA_PETAL_POINTS = new float[][]{
            {0.00f, -0.18f}, {-0.30f, -0.07f}, {-0.64f, 0.25f}, {-0.66f, 0.62f},
            {-0.36f, 0.94f}, {-0.10f, 0.82f}, {0.00f, 0.74f}, {0.10f, 0.82f},
            {0.36f, 0.94f}, {0.66f, 0.62f}, {0.64f, 0.25f}, {0.30f, -0.07f},
            {0.00f, -0.18f}
    };

    private static InjectionSuccessAnimation active;

    private long startTime = -1L;

    private InjectionSuccessAnimation() {
    }

    public static synchronized void show() {
        if (active != null) {
            EventManager.unregister(active);
        }
        active = new InjectionSuccessAnimation();
        EventManager.register(active);
    }

    @EventTarget(Priority.LOWEST)
    public void onRender(Render2DEvent event) {
        if (!isInGame()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (startTime < 0L) {
            startTime = now;
        }

        long elapsed = now - startTime;
        if (elapsed > DURATION_MS) {
            EventManager.unregister(this);
            if (active == this) {
                active = null;
            }
            return;
        }

        render(elapsed);
    }

    private void render(long elapsed) {
        ScaledResolution sr = new ScaledResolution(mc);
        float screenW = sr.getScaledWidth();
        float screenH = sr.getScaledHeight();
        float uiScale = clamp(screenW / 620.0f, 0.76f, 1.12f);
        float intro = smoothStep(clamp01(elapsed / (float) INTRO_MS));
        float outro = smoothStep(clamp01((DURATION_MS - elapsed) / (float) OUTRO_MS));
        float alpha = clamp01(Math.min(intro, outro));
        float life = clamp01(elapsed / (float) DURATION_MS);
        float remaining = clamp01((DURATION_MS - elapsed) / (float) DURATION_MS);
        float pulse = (float) (0.5D + 0.5D * Math.sin(elapsed / 760.0D));

        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableDepth();

            drawFullscreenAtmosphere(screenW, screenH, alpha);
            drawCenterComposition(screenW, screenH, uiScale, alpha, intro, pulse, life);
            drawLifetimeBar(screenW, screenH, uiScale, alpha, remaining, pulse);
            drawPetalField(screenW, screenH, uiScale, alpha, life);
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glLineWidth(1.0f);
            GlStateManager.popMatrix();
        }
    }

    private void drawFullscreenAtmosphere(float screenW, float screenH, float alpha) {
        RenderServices.shapes().rect(0.0f, 0.0f, screenW, screenH,
                withAlpha(0xFF050407, 112.0f * alpha));
        RenderServices.shapes().verticalGradient(0.0f, 0.0f, screenW, screenH * 0.36f,
                withAlpha(0xFF130911, 84.0f * alpha), withAlpha(0xFF050407, 0.0f));
        RenderServices.shapes().verticalGradient(0.0f, screenH * 0.64f, screenW, screenH,
                withAlpha(0xFF050407, 0.0f), withAlpha(0xFF150A12, 88.0f * alpha));
    }

    private void drawCenterComposition(float screenW, float screenH, float uiScale,
                                       float alpha, float intro, float pulse, float life) {
        float centerX = screenW * 0.5f;
        float centerY = screenH * 0.43f;
        float titleScale = 0.96f + 0.04f * intro;
        float flowerSize = (15.0f + 1.8f * pulse) * uiScale;

        drawSakuraFlower(centerX, centerY - 43.0f * uiScale, flowerSize, alpha);
        drawOrbitalPetals(centerX, centerY - 43.0f * uiScale, 36.0f * uiScale, uiScale, alpha, life);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(centerX, centerY, 0.0f);
            GlStateManager.scale(titleScale, titleScale, 1.0f);
            GlStateManager.translate(-centerX, -centerY, 0.0f);

            CFontRenderer titleFont = FontLoaders.regular(Math.max(28, Math.round(40.0f * uiScale)));
            CFontRenderer subFont = FontLoaders.regular(Math.max(12, Math.round(15.0f * uiScale)));
            String title = "Yozakura";
            String sub = "Injected successfully";
            float titleX = centerX - titleFont.getStringWidth(title) * 0.5f;
            float titleY = centerY - 8.0f * uiScale;
            float subX = centerX - subFont.getStringWidth(sub) * 0.5f;
            float subY = centerY + 26.0f * uiScale;

            drawTextGlow(titleFont, title, titleX, titleY, uiScale, alpha, 1.24f);
            titleFont.drawString(title, titleX, titleY, withAlpha(TEXT, 248.0f * alpha));
            drawTextGlow(subFont, sub, subX, subY, uiScale, alpha, 0.72f);
            subFont.drawString(sub, subX, subY, withAlpha(MUTED, 226.0f * alpha));
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void drawLifetimeBar(float screenW, float screenH, float uiScale,
                                 float alpha, float remaining, float pulse) {
        float barW = Math.min(260.0f * uiScale, screenW * 0.42f);
        float barX = screenW * 0.5f - barW * 0.5f;
        float barY = screenH * 0.62f;
        float lineH = Math.max(0.8f, 1.0f * uiScale);
        float fillW = barW * remaining;

        RenderServices.shapes().rect(barX, barY + 1.2f * uiScale, barX + barW, barY + 1.7f * uiScale,
                withAlpha(0xFF040305, 92.0f * alpha));
        RenderServices.shapes().rect(barX, barY, barX + barW, barY + lineH,
                withAlpha(0xFFFFD3E3, 42.0f * alpha));
        if (fillW > 0.4f) {
            RenderServices.shapes().rect(barX, barY, barX + fillW, barY + Math.max(lineH, 1.08f * uiScale),
                    withAlpha(SAKURA_STRONG, (218.0f + 18.0f * pulse) * alpha));
            RenderServices.shapes().rect(barX, barY - 0.55f * uiScale, barX + fillW, barY,
                    withAlpha(0xFFFFF4FA, 56.0f * alpha));
        }
        if (remaining > 0.035f) {
            drawSakuraFlower(barX + fillW, barY + lineH * 0.5f, (2.35f + 0.35f * pulse) * uiScale, alpha);
        }
    }

    private void drawPetalField(float screenW, float screenH, float uiScale, float alpha, float life) {
        for (int i = 0; i < 18; i++) {
            float seed = i * 0.073f;
            float phase = (life * (0.72f + i * 0.018f) + seed) % 1.0f;
            float lane = (i % 6) / 5.0f;
            float px = -24.0f * uiScale + phase * (screenW + 48.0f * uiScale);
            float wave = (float) Math.sin((phase * 6.2831855f) + i * 0.91f);
            float py = screenH * (0.16f + lane * 0.68f) + wave * 14.0f * uiScale;
            float petalAlpha = alpha * (0.22f + 0.34f * (1.0f - Math.abs(phase - 0.5f) * 2.0f));
            drawSinglePetal(px, py, (1.9f + (i % 4) * 0.28f) * uiScale,
                    phase * 170.0f + i * 29.0f, petalAlpha);
        }
    }

    private void drawOrbitalPetals(float centerX, float centerY, float orbit, float uiScale,
                                   float alpha, float life) {
        for (int i = 0; i < 8; i++) {
            float angle = life * 6.2831855f * 0.55f + i * 0.7853982f;
            float px = centerX + (float) Math.cos(angle) * orbit;
            float py = centerY + (float) Math.sin(angle) * orbit * 0.42f;
            drawSinglePetal(px, py, (1.55f + i % 3 * 0.18f) * uiScale,
                    angle * 57.29578f + 38.0f, alpha * 0.46f);
        }
    }

    private void drawSakuraFlower(float centerX, float centerY, float size, float alpha) {
        if (alpha <= 0.002f || size <= 0.002f) {
            return;
        }
        RenderServices.shapes().shadow(centerX - size, centerY - size, centerX + size, centerY + size,
                size, withAlpha(SAKURA, 86.0f * alpha), 5, size * 0.70f);
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0.0f);
        GlStateManager.rotate((System.currentTimeMillis() % 3200L) / 3200.0f * 26.0f, 0.0f, 0.0f, 1.0f);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        for (int i = 0; i < 5; i++) {
            GL11.glPushMatrix();
            GL11.glRotatef(i * 72.0f, 0.0f, 0.0f, 1.0f);
            GL11.glTranslatef(0.0f, size * 0.20f, 0.0f);
            drawSakuraPetal(size, alpha);
            GL11.glPopMatrix();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        RenderServices.shapes().circle(centerX, centerY, 0, 360, size * 0.28f,
                withAlpha(0xFFFFF4FA, 232.0f * alpha));
    }

    private void drawSinglePetal(float centerX, float centerY, float size, float rotation, float alpha) {
        if (alpha <= 0.002f || size <= 0.002f) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0.0f);
        GlStateManager.rotate(rotation, 0.0f, 0.0f, 1.0f);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        drawSakuraPetal(size, alpha);
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static void drawSakuraPetal(float size, float alpha) {
        float width = size * 0.58f;
        float length = size * 1.12f;

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        glColor(0xFFFFEDF5, alpha * 0.94f);
        GL11.glVertex2f(0.0f, length * 0.36f);
        for (float[] point : SAKURA_PETAL_POINTS) {
            glColor(SAKURA, alpha * 0.72f);
            GL11.glVertex2f(point[0] * width, point[1] * length);
        }
        GL11.glEnd();

        GL11.glLineWidth(0.7f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        glColor(0xFFFFF8FB, alpha * 0.44f);
        for (float[] point : SAKURA_PETAL_POINTS) {
            GL11.glVertex2f(point[0] * width, point[1] * length);
        }
        GL11.glEnd();
    }

    private static void drawTextGlow(CFontRenderer font, String text, float x, float y,
                                     float uiScale, float alpha, float strength) {
        if (alpha <= 0.018f) {
            return;
        }
        font.drawGlowString(text, x, y, withAlpha(SAKURA_SOFT, 190.0f * alpha),
                Math.min(1.0f, strength * 0.72f), GlowProfile.TEXT);
    }

    private static boolean isInGame() {
        return mc.theWorld != null && mc.thePlayer != null;
    }

    private static float smoothStep(float value) {
        float t = clamp01(value);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static void glColor(int color, float alpha) {
        float a = clamp01(alpha) * ((color >>> 24) & 255) / 255.0f;
        GL11.glColor4f(((color >> 16) & 255) / 255.0f,
                ((color >> 8) & 255) / 255.0f,
                (color & 255) / 255.0f, a);
    }
}
