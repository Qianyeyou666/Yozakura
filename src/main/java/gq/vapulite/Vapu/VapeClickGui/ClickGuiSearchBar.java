package gq.vapulite.Vapu.VapeClickGui;

import gq.vapulite.Vapu.utils.RenderUtil;

final class ClickGuiSearchBar {
    private final VapeClickGui gui;

    ClickGuiSearchBar(VapeClickGui gui) {
        this.gui = gui;
    }

    void render(int mouseX, int mouseY, float introY) {
        float x = gui.contentX + 12.0f;
        float y = gui.getSearchY() + introY;
        float w = VapeClickGui.CARD_W - 24.0f;
        boolean hovered = VapeClickGui.isHovered(x, y, x + w, y + VapeClickGui.SEARCH_H, mouseX, mouseY);
        gui.searchFocusProgress = gui.animate(gui.searchFocusProgress, gui.searchFocused ? 1.0f : hovered ? 0.55f : 0.0f, 0.20f);
        if (gui.searchFocusProgress > 0.02f) {
            RenderUtil.drawSoftShadow(x - 1f, y - 1f, x + w + 1f, y + VapeClickGui.SEARCH_H + 1f, 19.0f,
                    gui.withAlpha(gui.guiColors().accent, 86.0f * gui.searchFocusProgress * gui.guiAlpha), 7, 4.0f);
        }
        gui.searchField.setBounds(x, y, w, VapeClickGui.SEARCH_H)
                .text(gui.searchQuery)
                .focused(gui.searchFocused)
                .setAlpha(gui.guiAlpha)
                .render(mouseX, mouseY, 0.0f);
        gui.searchFocused = gui.searchField.focused();
    }

    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        float x = gui.contentX + 12.0f;
        float y = gui.getSearchY();
        float w = VapeClickGui.CARD_W - 24.0f;
        gui.searchField.setBounds(x, y, w, VapeClickGui.SEARCH_H).text(gui.searchQuery).focused(gui.searchFocused);
        if (!gui.searchField.mouseClicked(mouseX, mouseY, mouseButton)) {
            return false;
        }
        String newQuery = gui.searchField.text();
        gui.searchFocused = gui.searchField.focused();
        gui.searchCursorTime = System.currentTimeMillis();
        if (!newQuery.equals(gui.searchQuery)) {
            gui.setSearchQuery(newQuery);
        }
        return true;
    }

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

    void focus() {
        gui.searchFocused = true;
        gui.searchCursorTime = System.currentTimeMillis();
    }
}
