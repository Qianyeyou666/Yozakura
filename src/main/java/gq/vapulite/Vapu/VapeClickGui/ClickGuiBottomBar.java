package gq.vapulite.Vapu.VapeClickGui;

import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.font.FontLoaders;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Color;

/**
 * ClickGUI 底部栏组件，显示用户信息和快捷键提示。
 * <p>
 * 左侧显示当前用户配置文件名，右侧显示 GUI 打开/关闭快捷键提示（Right Shift）。
 * 包级私有（package-private），仅供 {@link VapeClickGui} 内部使用。
 */
final class ClickGuiBottomBar {
    /** 关联的主 GUI 实例 */
    private final VapeClickGui gui;

    ClickGuiBottomBar(VapeClickGui gui) {
        this.gui = gui;
    }

    /**
     * 渲染底部栏。
     * <p>
     * 绘制两个毛玻璃卡片：左侧配置文件信息和右侧快捷键提示。
     *
     * @param sr 当前屏幕分辨率信息
     */
    void render(ScaledResolution sr) {
        // 底部栏 Y 坐标（距屏幕底部 34px）
        float y = sr.getScaledHeight() - 34.0f;

        // ===== 左侧：用户配置文件卡片 =====
        float profileW = 132.0f;
        gui.drawThemedGlass(gui.contentX, y, gui.contentX + profileW, y + 25.0f, 8.0f, 1.0f,
                gui.withAlpha(gui.guiColors().glassFillSoft, 190.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 48.0f * gui.guiAlpha));
        // 用户头像区域
        gui.drawSoftRect(gui.contentX + 10.0f, y + 5.0f, gui.contentX + 26.0f, y + 21.0f, 5.0f,
                gui.withAlpha(new Color(81, 87, 103, 220).getRGB(), 220.0f * gui.guiAlpha));
        gui.drawCenteredIcon(FontLoaders.ICON_USER, FontLoaders.I14, gui.contentX + 18.0f, y + 13.0f,
                gui.withAlpha(gui.guiColors().text, 235.0f * gui.guiAlpha));
        // 用户名和配置名
        gui.drawFont("Default", gui.contentX + 34.0f, y + 5.0f,
                gui.withAlpha(gui.guiColors().text, 235.0f * gui.guiAlpha));
        gui.drawFont("Profile 1", gui.contentX + 34.0f, y + 17.0f,
                gui.withAlpha(gui.guiColors().muted, 190.0f * gui.guiAlpha));
        // 下拉箭头
        gui.drawCenteredIcon(FontLoaders.ICON_DROPDOWN_ARROW, FontLoaders.I14, gui.contentX + profileW - 16.0f, y + 13.0f,
                gui.withAlpha(gui.guiColors().muted, 182.0f * gui.guiAlpha));

        // ===== 右侧：快捷键提示卡片 =====
        float hintW = 96.0f;
        float hintX = sr.getScaledWidth() - hintW - 16.0f;
        gui.drawThemedGlass(hintX, y, hintX + hintW, y + 25.0f, 8.0f, 1.0f,
                gui.withAlpha(gui.guiColors().glassFillSoft, 190.0f * gui.guiAlpha),
                gui.withAlpha(gui.guiColors().glassBorder, 48.0f * gui.guiAlpha));
        // 按键名称
        gui.drawCenteredText("Right Shift", hintX + 8.0f, y + 7.0f, hintX + 64.0f, y + 19.0f,
                gui.withAlpha(gui.guiColors().text, 220.0f * gui.guiAlpha));
        // GUI 标签
        gui.drawSoftRect(hintX + 66.0f, y + 5.0f, hintX + hintW - 7.0f, y + 20.0f, 6.0f,
                gui.withAlpha(gui.guiColors().detailSelectedFill, 232.0f * gui.guiAlpha));
        gui.drawCenteredText("GUI", hintX + 66.0f, y + 8.0f, hintX + hintW - 7.0f, y + 19.0f,
                gui.withAlpha(gui.guiColors().text, 245.0f * gui.guiAlpha));
    }
}
