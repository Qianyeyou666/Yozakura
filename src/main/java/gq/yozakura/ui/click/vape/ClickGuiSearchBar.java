package gq.yozakura.ui.click.vape;

import gq.yozakura.engine.render.ui.RenderServices;

/**
 * ClickGUI 搜索栏组件，负责模块搜索的输入与渲染。
 * <p>
 * 包含搜索文本框的渲染、鼠标点击处理和键盘输入处理。
 * 搜索栏聚焦时显示发光阴影边框效果，并支持 Ctrl+F 快捷键激活。
 * <p>
 * 包级私有（package-private），仅供 {@link VapeClickGui} 内部使用。
 */
final class ClickGuiSearchBar {
    /** 关联的主 GUI 实例 */
    private final VapeClickGui gui;

    /**
     * 构造搜索栏组件。
     *
     * @param gui 主 ClickGUI 实例
     */
    ClickGuiSearchBar(VapeClickGui gui) {
        this.gui = gui;
    }

    /**
     * 渲染搜索栏。
     * <p>
     * 绘制搜索文本框，并在聚焦或悬停时渲染发光阴影效果。
     * 包含 introY 偏移以支持打开/关闭动画。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param introY Y 轴动画偏移量
     */
    void render(int mouseX, int mouseY, float introY) {
        // 计算搜索栏位置和尺寸
        float x = gui.contentX + 12.0f;
        float y = gui.getSearchY() + introY;
        float w = VapeClickGui.CARD_W - 24.0f;
        // 判断鼠标悬停状态
        boolean hovered = VapeClickGui.isHovered(x, y, x + w, y + VapeClickGui.SEARCH_H, mouseX, mouseY);
        // 动画更新聚焦进度：聚焦=1.0，悬停=0.55，否则=0.0
        gui.searchFocusProgress = gui.animate(gui.searchFocusProgress, gui.searchFocused ? 1.0f : hovered ? 0.55f : 0.0f, 0.20f);
        // 绘制聚焦/悬停发光阴影
        if (gui.searchFocusProgress > 0.02f) {
            RenderServices.shapes().shadow(x - 1f, y - 1f, x + w + 1f, y + VapeClickGui.SEARCH_H + 1f, 19.0f,
                    gui.withAlpha(gui.guiColors().accent, 86.0f * gui.searchFocusProgress * gui.guiAlpha), 7, 4.0f);
        }
        // 渲染搜索文本框
        gui.searchField.setBounds(x, y, w, VapeClickGui.SEARCH_H)
                .text(gui.searchQuery)
                .focused(gui.searchFocused)
                .setAlpha(gui.guiAlpha)
                .render(mouseX, mouseY, 0.0f);
        gui.searchFocused = gui.searchField.focused();
    }

    /**
     * 处理鼠标点击事件。
     *
     * @return true 如果事件被搜索栏消费
     */
    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        float x = gui.contentX + 12.0f;
        float y = gui.getSearchY();
        float w = VapeClickGui.CARD_W - 24.0f;
        gui.searchField.setBounds(x, y, w, VapeClickGui.SEARCH_H).text(gui.searchQuery).focused(gui.searchFocused);
        if (!gui.searchField.mouseClicked(mouseX, mouseY, mouseButton)) {
            return false;
        }
        // 更新搜索查询文本并记录光标时间
        String newQuery = gui.searchField.text();
        gui.searchFocused = gui.searchField.focused();
        gui.searchCursorTime = System.currentTimeMillis();
        if (!newQuery.equals(gui.searchQuery)) {
            gui.setSearchQuery(newQuery);
        }
        return true;
    }

    /**
     * 处理键盘输入事件。
     *
     * @return true 如果事件被搜索栏消费
     */
    boolean keyTyped(char typedChar, int keyCode) {
        if (!gui.searchFocused && !gui.searchField.focused()) {
            return false;
        }
        gui.searchField.text(gui.searchQuery).focused(gui.searchFocused);
        if (!gui.searchField.keyTyped(typedChar, keyCode)) {
            return false;
        }
        gui.searchFocused = gui.searchField.focused();
        gui.setSearchQuery(gui.searchField.text());
        return true;
    }

    /** 激活搜索栏聚焦（由 Ctrl+F 快捷键触发） */
    void focus() {
        gui.searchFocused = true;
        gui.searchCursorTime = System.currentTimeMillis();
    }
}
