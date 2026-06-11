package gq.vapulite.Vapu.VapeClickGui.utils;

import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.font.FontLoaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import java.awt.*;

/**
 * 自定义按钮控件，继承自 Minecraft 的 {@link Gui}。
 * <p>
 * 支持自定义颜色（悬停/非悬停状态），使用圆角矩形作为按钮背景，
 * 并利用 {@link FontLoaders} 渲染按钮文字。
 */
public class LuneButton extends Gui {
    /** 按钮纹理资源路径（Minecraft 默认控件纹理） */
    protected static final ResourceLocation buttonTextures = new ResourceLocation("textures/gui/widgets.png");

    /** 按钮宽度（像素） */
    protected int width = 200;

    /** 按钮高度（像素） */
    protected int height = 20;

    /** 控件 X 坐标 */
    public int xPosition;

    /** 控件 Y 坐标 */
    public int yPosition;

    /** 按钮上显示的文本 */
    public String displayString;
    /** 按钮 ID */
    public int id;

    /** 是否启用（false 时灰色不可点击） */
    public boolean enabled = true;

    /** 是否可见（false 时完全隐藏） */
    public boolean visible = true;
    /** 鼠标是否悬停在按钮上 */
    protected boolean hovered;

    /**
     * 使用默认尺寸（200x20）构造按钮。
     */
    public LuneButton(int buttonId, int x, int y, String buttonText) {
        this(buttonId, x, y, 200, 20, buttonText);
    }

    /**
     * 构造一个自定义尺寸的按钮。
     *
     * @param buttonId   按钮 ID
     * @param x          X 坐标
     * @param y          Y 坐标
     * @param widthIn    宽度
     * @param heightIn   高度
     * @param buttonText 显示文本
     */
    public LuneButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
        this.id = buttonId;
        this.xPosition = x;
        this.yPosition = y;
        this.width = widthIn;
        this.height = heightIn;
        this.displayString = buttonText;
    }

    /**
     * 获取按钮悬停状态码。
     *
     * @param mouseOver 鼠标是否在按钮上方
     * @return 0=禁用, 1=正常, 2=悬停
     */
    protected int getHoverState(boolean mouseOver) {
        int i = 1;

        if (!this.enabled) {
            i = 0;
        } else if (mouseOver) {
            i = 2;
        }

        return i;
    }

    /**
     * 渲染按钮。
     *
     * @param mc      Minecraft 实例
     * @param mouseX  鼠标 X 坐标
     * @param mouseY  鼠标 Y 坐标
     * @param a       非悬停状态的颜色
     * @param b       悬停状态的颜色
     */
    public void drawButton(Minecraft mc, int mouseX, int mouseY, Color a, Color b) {
        if (this.visible) {
            FontRenderer fontrenderer = mc.fontRendererObj;
            mc.getTextureManager().bindTexture(buttonTextures);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            // 判断鼠标悬停
            this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
            int i = this.getHoverState(this.hovered);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.blendFunc(770, 771);
            // 使用悬停状态选择对应颜色绘制背景
            RenderUtil.drawRect(this.xPosition, this.yPosition, this.xPosition + (200 - this.width / 2) * 2, this.yPosition + (86) / 4, (i == 1) ? a.getRGB() : b.getRGB());
            this.mouseDragged(mc, mouseX, mouseY);
            // 文字颜色：禁用→灰色，悬停→亮白，正常→浅灰
            int j = 14737632;

            if (!this.enabled) {
                j = 10526880;
            } else if (this.hovered) {
                j = 16777120;
            }

            // 居中绘制按钮文字
            FontLoaders.F22.drawCenteredString(this.displayString, this.xPosition + this.width / 2, this.yPosition + (this.height - 8) / 2, j);
        }
    }

    /** 鼠标拖拽回调（子类可重写） */
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
    }

    /** 鼠标释放回调（子类可重写） */
    public void mouseReleased(int mouseX, int mouseY) {
    }

    /**
     * 判断鼠标是否按下了此按钮。
     *
     * @return true 如果按钮启用、可见且鼠标在其范围内
     */
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        return this.enabled && this.visible && mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
    }

    /** @return 鼠标是否悬停在按钮上 */
    public boolean isMouseOver() {
        return this.hovered;
    }

    /** 绘制按钮前景层（子类可重写） */
    public void drawButtonForegroundLayer(int mouseX, int mouseY) {
    }

    /** 播放按钮按下音效 */
    public void playPressSound(SoundHandler soundHandlerIn) {
        soundHandlerIn.playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }

    /** @return 按钮宽度 */
    public int getButtonWidth() {
        return this.width;
    }

    /** @param width 设置按钮宽度 */
    public void setWidth(int width) {
        this.width = width;
    }
}
