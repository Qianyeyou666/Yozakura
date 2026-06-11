package gq.vapulite.Vapu.VapeClickGui.utils;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import gq.vapulite.font.FontLoaders;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiPageButtonList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.MathHelper;

import java.awt.*;

import static gq.vapulite.Vapu.utils.RenderUtil.drawRoundedRect;
import static gq.vapulite.Vapu.utils.RenderUtils.drawRoundRect;

/**
 * 带背景的文本输入框控件，继承自 Minecraft 的 {@link Gui}。
 * <p>
 * 与 {@link EmptyInputBox} 的区别在于此输入框会绘制圆角背景矩形。
 * 支持文本输入、光标移动、选择、复制粘贴、Ctrl+A/C/V/X 等标准操作。
 * <p>
 * 相比于 EmptyInputBox，额外支持设置文字颜色和启用/禁用背景绘制的开关。
 */
public class InputBox extends Gui
{
    /** 控件 ID */
    private final int id;
    /** 字体渲染器实例 */
    private final FontRenderer fontRendererInstance;
    /** 控件 X 坐标 */
    public int xPosition;
    /** 控件 Y 坐标 */
    public int yPosition;

    /** 输入框宽度 */
    private final int width;
    /** 输入框高度 */
    private final int height;

    /** 当前编辑中的文本 */
    private String text = "";
    /** 最大允许输入字符数 */
    private int maxStringLength = 32;
    /** 光标闪烁计数器 */
    private int cursorCounter;
    /** 是否绘制背景（默认开启） */
    private boolean enableBackgroundDrawing = true;

    /** 是否允许点击其他地方失去焦点 */
    private boolean canLoseFocus = true;

    /** 是否为聚焦状态 */
    private boolean isFocused;

    /** 是否启用 */
    private boolean isEnabled = true;

    /** 渲染文本时的行滚动偏移量 */
    private int lineScrollOffset;
    /** 当前光标位置 */
    private int cursorPosition;

    /** 选择结束位置 */
    private int selectionEnd;
    /** 启用状态下的文字颜色 */
    private int enabledColor = 14737632;
    /** 禁用状态下的文字颜色 */
    private int disabledColor = 7368816;

    /** 输入框是否可见 */
    private boolean visible = true;
    /** GUI 响应器 */
    private GuiPageButtonList.GuiResponder field_175210_x;
    /** 输入验证器 */
    private Predicate<String> validator = Predicates.alwaysTrue();

    /**
     * 构造一个输入框。
     *
     * @param componentId   控件 ID
     * @param fontrendererObj 字体渲染器
     * @param x             X 坐标
     * @param y             Y 坐标
     * @param par5Width     宽度
     * @param par6Height    高度
     */
    public InputBox(int componentId, FontRenderer fontrendererObj, int x, int y, int par5Width, int par6Height)
    {
        this.id = componentId;
        this.fontRendererInstance = fontrendererObj;
        this.xPosition = x;
        this.yPosition = y;
        this.width = par5Width;
        this.height = par6Height;
    }

    /** 设置 GUI 响应器 */
    public void func_175207_a(GuiPageButtonList.GuiResponder p_175207_1_)
    {
        this.field_175210_x = p_175207_1_;
    }

    /** 增加光标计数器 */
    public void updateCursorCounter()
    {
        ++this.cursorCounter;
    }

    /** 设置输入框文本 */
    public void setText(String p_146180_1_)
    {
        if (this.validator.apply(p_146180_1_))
        {
            if (p_146180_1_.length() > this.maxStringLength)
            {
                this.text = p_146180_1_.substring(0, this.maxStringLength);
            }
            else
            {
                this.text = p_146180_1_;
            }

            this.setCursorPositionEnd();
        }
    }

    /** @return 输入框文本内容 */
    public String getText()
    {
        return this.text;
    }

    /** @return 当前选中的文本 */
    public String getSelectedText()
    {
        int i = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
        int j = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
        return this.text.substring(i, j);
    }

    /** 设置输入验证器 */
    public void setValidator(Predicate<String> theValidator)
    {
        this.validator = theValidator;
    }

    /** 在光标位置写入文本 */
    public void writeText(String p_146191_1_)
    {
        String s = "";
        String s1 = ChatAllowedCharacters.filterAllowedCharacters(p_146191_1_);
        int i = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
        int j = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
        int k = this.maxStringLength - this.text.length() - (i - j);
        int l = 0;

        if (this.text.length() > 0)
        {
            s = s + this.text.substring(0, i);
        }

        if (k < s1.length())
        {
            s = s + s1.substring(0, k);
            l = k;
        }
        else
        {
            s = s + s1;
            l = s1.length();
        }

        if (this.text.length() > 0 && j < this.text.length())
        {
            s = s + this.text.substring(j);
        }

        if (this.validator.apply(s))
        {
            this.text = s;
            this.moveCursorBy(i - this.selectionEnd + l);

            if (this.field_175210_x != null)
            {
                this.field_175210_x.func_175319_a(this.id, this.text);
            }
        }
    }

    /** 删除从光标开始的指定数量单词 */
    public void deleteWords(int p_146177_1_)
    {
        if (this.text.length() != 0)
        {
            if (this.selectionEnd != this.cursorPosition)
            {
                this.writeText("");
            }
            else
            {
                this.deleteFromCursor(this.getNthWordFromCursor(p_146177_1_) - this.cursorPosition);
            }
        }
    }

    /** 从光标位置删除指定数量字符 */
    public void deleteFromCursor(int p_146175_1_)
    {
        if (this.text.length() != 0)
        {
            if (this.selectionEnd != this.cursorPosition)
            {
                this.writeText("");
            }
            else
            {
                boolean flag = p_146175_1_ < 0;
                int i = flag ? this.cursorPosition + p_146175_1_ : this.cursorPosition;
                int j = flag ? this.cursorPosition : this.cursorPosition + p_146175_1_;
                String s = "";

                if (i >= 0)
                {
                    s = this.text.substring(0, i);
                }

                if (j < this.text.length())
                {
                    s = s + this.text.substring(j);
                }

                if (this.validator.apply(s))
                {
                    this.text = s;

                    if (flag)
                    {
                        this.moveCursorBy(p_146175_1_);
                    }

                    if (this.field_175210_x != null)
                    {
                        this.field_175210_x.func_175319_a(this.id, this.text);
                    }
                }
            }
        }
    }

    /** @return 控件 ID */
    public int getId()
    {
        return this.id;
    }

    /** 获取从光标开始第 N 个单词的位置 */
    public int getNthWordFromCursor(int p_146187_1_)
    {
        return this.getNthWordFromPos(p_146187_1_, this.getCursorPosition());
    }

    /** 获取从指定位置第 N 个单词的位置 */
    public int getNthWordFromPos(int p_146183_1_, int p_146183_2_)
    {
        return this.func_146197_a(p_146183_1_, p_146183_2_, true);
    }

    /** 计算第 N 个单词的位置 */
    public int func_146197_a(int p_146197_1_, int p_146197_2_, boolean p_146197_3_)
    {
        int i = p_146197_2_;
        boolean flag = p_146197_1_ < 0;
        int j = Math.abs(p_146197_1_);

        for (int k = 0; k < j; ++k)
        {
            if (!flag)
            {
                int l = this.text.length();
                i = this.text.indexOf(32, i);

                if (i == -1)
                {
                    i = l;
                }
                else
                {
                    while (p_146197_3_ && i < l && this.text.charAt(i) == ' ')
                    {
                        ++i;
                    }
                }
            }
            else
            {
                while (p_146197_3_ && i > 0 && this.text.charAt(i - 1) == ' ')
                {
                    --i;
                }

                while (i > 0 && this.text.charAt(i - 1) != ' ')
                {
                    --i;
                }
            }
        }

        return i;
    }

    /** 移动光标偏移量并清除选择 */
    public void moveCursorBy(int p_146182_1_)
    {
        this.setCursorPosition(this.selectionEnd + p_146182_1_);
    }

    /** 设置光标位置 */
    public void setCursorPosition(int p_146190_1_)
    {
        this.cursorPosition = p_146190_1_;
        int i = this.text.length();
        this.cursorPosition = MathHelper.clamp_int(this.cursorPosition, 0, i);
        this.setSelectionPos(this.cursorPosition);
    }

    /** 光标移到开头 */
    public void setCursorPositionZero()
    {
        this.setCursorPosition(0);
    }

    /** 光标移到末尾 */
    public void setCursorPositionEnd()
    {
        this.setCursorPosition(this.text.length());
    }

    /**
     * 处理键盘输入。
     * 支持 Home/End/Delete/Backspace/方向键 以及 Ctrl+A/C/V/X 快捷键。
     */
    public boolean textboxKeyTyped(char p_146201_1_, int p_146201_2_)
    {
        if (!this.isFocused)
        {
            return false;
        }
        else if (GuiScreen.isKeyComboCtrlA(p_146201_2_))
        {
            this.setCursorPositionEnd();
            this.setSelectionPos(0);
            return true;
        }
        else if (GuiScreen.isKeyComboCtrlC(p_146201_2_))
        {
            GuiScreen.setClipboardString(this.getSelectedText());
            return true;
        }
        else if (GuiScreen.isKeyComboCtrlV(p_146201_2_))
        {
            if (this.isEnabled)
            {
                this.writeText(GuiScreen.getClipboardString());
            }

            return true;
        }
        else if (GuiScreen.isKeyComboCtrlX(p_146201_2_))
        {
            GuiScreen.setClipboardString(this.getSelectedText());

            if (this.isEnabled)
            {
                this.writeText("");
            }

            return true;
        }
        else
        {
            switch (p_146201_2_)
            {
                case 14:
                    if (GuiScreen.isCtrlKeyDown())
                    {
                        if (this.isEnabled)
                        {
                            this.deleteWords(-1);
                        }
                    }
                    else if (this.isEnabled)
                    {
                        this.deleteFromCursor(-1);
                    }

                    return true;

                case 199:
                    if (GuiScreen.isShiftKeyDown())
                    {
                        this.setSelectionPos(0);
                    }
                    else
                    {
                        this.setCursorPositionZero();
                    }

                    return true;

                case 203:
                    if (GuiScreen.isShiftKeyDown())
                    {
                        if (GuiScreen.isCtrlKeyDown())
                        {
                            this.setSelectionPos(this.getNthWordFromPos(-1, this.getSelectionEnd()));
                        }
                        else
                        {
                            this.setSelectionPos(this.getSelectionEnd() - 1);
                        }
                    }
                    else if (GuiScreen.isCtrlKeyDown())
                    {
                        this.setCursorPosition(this.getNthWordFromCursor(-1));
                    }
                    else
                    {
                        this.moveCursorBy(-1);
                    }

                    return true;

                case 205:
                    if (GuiScreen.isShiftKeyDown())
                    {
                        if (GuiScreen.isCtrlKeyDown())
                        {
                            this.setSelectionPos(this.getNthWordFromPos(1, this.getSelectionEnd()));
                        }
                        else
                        {
                            this.setSelectionPos(this.getSelectionEnd() + 1);
                        }
                    }
                    else if (GuiScreen.isCtrlKeyDown())
                    {
                        this.setCursorPosition(this.getNthWordFromCursor(1));
                    }
                    else
                    {
                        this.moveCursorBy(1);
                    }

                    return true;

                case 207:
                    if (GuiScreen.isShiftKeyDown())
                    {
                        this.setSelectionPos(this.text.length());
                    }
                    else
                    {
                        this.setCursorPositionEnd();
                    }

                    return true;

                case 211:
                    if (GuiScreen.isCtrlKeyDown())
                    {
                        if (this.isEnabled)
                        {
                            this.deleteWords(1);
                        }
                    }
                    else if (this.isEnabled)
                    {
                        this.deleteFromCursor(1);
                    }

                    return true;

                default:
                    if (ChatAllowedCharacters.isAllowedCharacter(p_146201_1_))
                    {
                        if (this.isEnabled)
                        {
                            this.writeText(Character.toString(p_146201_1_));
                        }

                        return true;
                    }
                    else
                    {
                        return false;
                    }
            }
        }
    }

    /** 处理鼠标点击 */
    public void mouseClicked(int p_146192_1_, int p_146192_2_, int p_146192_3_)
    {
        boolean flag = p_146192_1_ >= this.xPosition && p_146192_1_ < this.xPosition + this.width && p_146192_2_ >= this.yPosition && p_146192_2_ < this.yPosition + this.height;

        if (this.canLoseFocus)
        {
            this.setFocused(flag);
        }

        if (this.isFocused && flag && p_146192_3_ == 0)
        {
            int i = p_146192_1_ - this.xPosition;

            if (this.enableBackgroundDrawing)
            {
                i -= 4;
            }

            String s = this.fontRendererInstance.trimStringToWidth(this.text.substring(this.lineScrollOffset), this.getWidth());
            this.setCursorPosition(this.fontRendererInstance.trimStringToWidth(s, i).length() + this.lineScrollOffset);
        }
    }

    /**
     * 绘制输入框。
     * <p>
     * 与 {@link EmptyInputBox} 的区别：会先绘制一个圆角背景矩形。
     */
    public void drawTextBox()
    {
        if (this.getVisible())
        {
            // 绘制圆角背景
            if (this.getEnableBackgroundDrawing())
            {
                drawRoundRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, new Color(240, 240, 240).getRGB());
            }

            int i = this.isEnabled ? new Color(100,100,100).getRGB() : this.disabledColor;
            int j = this.cursorPosition - this.lineScrollOffset;
            int k = this.selectionEnd - this.lineScrollOffset;
            String s = this.fontRendererInstance.trimStringToWidth(this.text.substring(this.lineScrollOffset), this.getWidth());
            boolean flag = j >= 0 && j <= s.length();
            boolean flag1 = this.isFocused && this.cursorCounter / 6 % 2 == 0 && flag;
            int l = this.enableBackgroundDrawing ? this.xPosition + 4 : this.xPosition;
            int i1 = this.enableBackgroundDrawing ? this.yPosition + (this.height - 8) / 2 : this.yPosition;
            int j1 = l;

            if (k > s.length())
            {
                k = s.length();
            }

            if (s.length() > 0)
            {
                String s1 = flag ? s.substring(0, j) : s;
                j1 = (int) FontLoaders.F16.drawString(s1, (float)l, (float)i1, i);
            }

            boolean flag2 = this.cursorPosition < this.text.length() || this.text.length() >= this.getMaxStringLength();
            int k1 = j1;

            if (!flag)
            {
                k1 = j > 0 ? l + this.width : l;
            }
            else if (flag2)
            {
                k1 = j1 - 1;
                --j1;
            }

            if (s.length() > 0 && flag && j < s.length())
            {
                j1 = (int) FontLoaders.F16.drawString(s.substring(j), (float)j1, (float)i1, i);
            }

            // 绘制闪烁光标
            if (flag1)
            {
                if (flag2)
                {
                    Gui.drawRect(k1, i1 - 1, k1 + 1, i1 + 1 + this.fontRendererInstance.FONT_HEIGHT, -3092272);
                }
                else
                {
                    FontLoaders.F16.drawString("_", (float)k1, (float)i1, i);
                }
            }

            // 绘制选择高亮
            if (k != j)
            {
                int l1 = l + this.fontRendererInstance.getStringWidth(s.substring(0, k));
                this.drawCursorVertical(k1, i1 - 1, l1 - 1, i1 + 1 + this.fontRendererInstance.FONT_HEIGHT);
            }
        }
    }

    /** 绘制选择区域的反转色高亮 */
    private void drawCursorVertical(int p_146188_1_, int p_146188_2_, int p_146188_3_, int p_146188_4_)
    {
        if (p_146188_1_ < p_146188_3_)
        {
            int i = p_146188_1_;
            p_146188_1_ = p_146188_3_;
            p_146188_3_ = i;
        }

        if (p_146188_2_ < p_146188_4_)
        {
            int j = p_146188_2_;
            p_146188_2_ = p_146188_4_;
            p_146188_4_ = j;
        }

        if (p_146188_3_ > this.xPosition + this.width)
        {
            p_146188_3_ = this.xPosition + this.width;
        }

        if (p_146188_1_ > this.xPosition + this.width)
        {
            p_146188_1_ = this.xPosition + this.width;
        }

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        GlStateManager.color(0.0F, 0.0F, 255.0F, 255.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.enableColorLogic();
        GlStateManager.colorLogicOp(5387);
        worldrenderer.begin(7, DefaultVertexFormats.POSITION);
        worldrenderer.pos((double)p_146188_1_, (double)p_146188_4_, 0.0D).endVertex();
        worldrenderer.pos((double)p_146188_3_, (double)p_146188_4_, 0.0D).endVertex();
        worldrenderer.pos((double)p_146188_3_, (double)p_146188_2_, 0.0D).endVertex();
        worldrenderer.pos((double)p_146188_1_, (double)p_146188_2_, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.disableColorLogic();
        GlStateManager.enableTexture2D();
    }

    /** 设置最大输入长度 */
    public void setMaxStringLength(int p_146203_1_)
    {
        this.maxStringLength = p_146203_1_;

        if (this.text.length() > p_146203_1_)
        {
            this.text = this.text.substring(0, p_146203_1_);
        }
    }

    /** @return 最大输入长度 */
    public int getMaxStringLength()
    {
        return this.maxStringLength;
    }

    /** @return 当前光标位置 */
    public int getCursorPosition()
    {
        return this.cursorPosition;
    }

    /** @return 是否启用背景绘制 */
    public boolean getEnableBackgroundDrawing()
    {
        return this.enableBackgroundDrawing;
    }

    /** 设置是否启用背景绘制 */
    public void setEnableBackgroundDrawing(boolean p_146185_1_)
    {
        this.enableBackgroundDrawing = p_146185_1_;
    }

    /** 设置启用状态下的文字颜色 */
    public void setTextColor(int p_146193_1_)
    {
        this.enabledColor = p_146193_1_;
    }

    /** 设置禁用状态下的文字颜色 */
    public void setDisabledTextColour(int p_146204_1_)
    {
        this.disabledColor = p_146204_1_;
    }

    /** 设置聚焦状态 */
    public void setFocused(boolean p_146195_1_)
    {
        if (p_146195_1_ && !this.isFocused)
        {
            this.cursorCounter = 0;
        }

        this.isFocused = p_146195_1_;
    }

    /** @return 是否聚焦 */
    public boolean isFocused()
    {
        return this.isFocused;
    }

    /** 设置启用状态 */
    public void setEnabled(boolean p_146184_1_)
    {
        this.isEnabled = p_146184_1_;
    }

    /** @return 选择结束位置 */
    public int getSelectionEnd()
    {
        return this.selectionEnd;
    }

    /** @return 输入框有效宽度 */
    public int getWidth()
    {
        return this.getEnableBackgroundDrawing() ? this.width - 8 : this.width;
    }

    /** 设置选择锚点位置 */
    public void setSelectionPos(int p_146199_1_)
    {
        int i = this.text.length();

        if (p_146199_1_ > i)
        {
            p_146199_1_ = i;
        }

        if (p_146199_1_ < 0)
        {
            p_146199_1_ = 0;
        }

        this.selectionEnd = p_146199_1_;

        if (this.fontRendererInstance != null)
        {
            if (this.lineScrollOffset > i)
            {
                this.lineScrollOffset = i;
            }

            int j = this.getWidth();
            String s = this.fontRendererInstance.trimStringToWidth(this.text.substring(this.lineScrollOffset), j);
            int k = s.length() + this.lineScrollOffset;

            if (p_146199_1_ == this.lineScrollOffset)
            {
                this.lineScrollOffset -= this.fontRendererInstance.trimStringToWidth(this.text, j, true).length();
            }

            if (p_146199_1_ > k)
            {
                this.lineScrollOffset += p_146199_1_ - k;
            }
            else if (p_146199_1_ <= this.lineScrollOffset)
            {
                this.lineScrollOffset -= this.lineScrollOffset - p_146199_1_;
            }

            this.lineScrollOffset = MathHelper.clamp_int(this.lineScrollOffset, 0, i);
        }
    }

    /** 设置是否允许失去焦点 */
    public void setCanLoseFocus(boolean p_146205_1_)
    {
        this.canLoseFocus = p_146205_1_;
    }

    /** @return 是否可见 */
    public boolean getVisible()
    {
        return this.visible;
    }

    /** 设置可见性 */
    public void setVisible(boolean p_146189_1_)
    {
        this.visible = p_146189_1_;
    }
}
