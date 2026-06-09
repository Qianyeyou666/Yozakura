package gq.vapulite.utils;

import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.ColorUtils;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.Vapu.utils.TimerUtil;
import gq.vapulite.font.FontLoaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import java.awt.*;

public class Notification {
    public static Minecraft mc = Minecraft.getMinecraft();

    public boolean isClassicNotification;
    public String message;
    public String title;
    public String icon;
    public TimerUtil timer;
    public Type type;
    public long stayTime;
    Module module;
    private float animationX;
    private float animationY;
    private float width;
    private final float height;
    private final long createdAt;
    private long lastFrameMS;

    public Notification(String title, String message, Type type, long stayTime) {
        this(title, message, type, stayTime, null);
    }

    public Notification(String title, String message, Type type, long stayTime, Module module) {
        this.module = module;

        this.message = message == null ? "" : message;
        this.title = title == null ? "" : title;
        isClassicNotification = true;
        width = Math.max(Math.max(FontLoaders.C18.getStringWidth(this.title), FontLoaders.C14.getStringWidth(this.message)) + 46, 150);
        this.height = 38.0f;
        this.animationX = 1.0f;
        this.type = type == null ? Type.INFO : type;
        this.stayTime = stayTime;
        this.createdAt = System.currentTimeMillis();
        this.lastFrameMS = this.createdAt;

        this.timer = new TimerUtil();
        timer.reset();
    }

    public void draw(float x, float offsetY) {
        long now = System.currentTimeMillis();
        float delta = Math.max(1.0f, Math.min(50.0f, now - lastFrameMS));
        lastFrameMS = now;
        float target = isFinished() ? 1.0f : 0.0f;
        float factor = 1.0f - (float) Math.pow(0.001D, delta / 260.0D);
        this.animationX = lerp(this.animationX, target, factor);

        if (animationY == 0) {
            animationY = offsetY;
        }

        animationY = lerp(animationY, offsetY, factor);

        float x1 = x - width - 8.0f + this.animationX * (width + 12.0f);
        float x2 = x1 + width;

        float y1 = animationY - height;
        float y2 = y1 + height;
        int accent = getAccentColor();
        int background = ColorUtils.applyAlpha(0xFF10131A, 185);
        int border = ColorUtils.applyAlpha(accent, 120);
        float progress = 1.0f - ColorUtils.clamp((now - createdAt) / (float) Math.max(1L, stayTime), 0.0f, 1.0f);

        RenderUtil.drawSoftShadow(x1, y1, x2, y2, 6.0f, 0x90000000, 5, 3.0f);
        RenderUtil.drawRoundedBorderedRect(x1, y1, x2, y2, 6.0f, 1.0f, background, border);
        RenderUtil.drawRect(x1 + 4.0f, y1 + 6.0f, x1 + 7.0f, y2 - 6.0f, ColorUtils.applyAlpha(accent, 230));
        FontLoaders.C18.drawString(getTitle(), x1 + 14.0f, y1 + 9.0f, 0xFFFFFFFF);
        FontLoaders.C14.drawString(message, x1 + 14.0f, y1 + 23.0f, 0xFFC8D0DA);
        RenderUtil.drawProgressBar(x1 + 10.0f, y2 - 4.0f, x2 - 10.0f, y2 - 2.0f, 1.5f, progress,
                0x30000000, ColorUtils.applyAlpha(accent, 210));
    }

    public boolean shouldDelete() {
        return isFinished() && this.animationX > 0.98f;
    }

    public float getHeight() {
        return height;
    }

    private boolean isFinished() {
        return timer.delay(stayTime);
    }

    private String getTitle() {
        if (title.length() > 0) {
            return title;
        }
        if (module != null) {
            return module.getName();
        }
        return type.name();
    }

    private int getAccentColor() {
        switch (this.type) {
            case MODULE:
            case INFO:
                return 0xFF42A5F5;
            case WARNING:
                return 0xFFFFC857;
            case ERROR:
                return 0xFFFF5C5C;
            case SUCCESS:
                return 0xFF5ED68A;
            default:
                return 0xFF42A5F5;
        }
    }

    private float lerp(float current, float target, float factor) {
        return current + (target - current) * ColorUtils.clamp(factor, 0.0f, 1.0f);
    }

    public void drawArrow(float left, float top, float right, float bottom, int color) {
        float shiet;
        if (left < right) {
            shiet = left;
            left = right;
            right = shiet;
        }
        if (top < bottom) {
            shiet = top;
            top = bottom;
            bottom = shiet;
        }
        float a = (float) (color >> 24 & 255) / 255.0F;
        float b = (float) (color >> 16 & 255) / 255.0F;
        float c = (float) (color >> 8 & 255) / 255.0F;
        float d = (float) (color & 255) / 255.0F;
        WorldRenderer worldRenderer = Tessellator.getInstance().getWorldRenderer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 1);
        GlStateManager.color(b, c, d, a);
        worldRenderer.begin(7, DefaultVertexFormats.POSITION);
        worldRenderer.pos(left, bottom + 6f, 0.0D).endVertex();
        worldRenderer.pos(right, bottom, 0.0D).endVertex();
        worldRenderer.pos(right, top, 0.0D).endVertex();
        worldRenderer.pos(left, top - 6f, 0.0D).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 2.0F);
    }

    public enum Type {
        INFO, WARNING, ERROR, SUCCESS, MODULE
    }

}
