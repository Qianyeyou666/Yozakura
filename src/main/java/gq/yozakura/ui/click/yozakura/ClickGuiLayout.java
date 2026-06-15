package gq.yozakura.ui.click.yozakura;

import gq.yozakura.module.render.ClickGUI;
import net.minecraft.client.gui.ScaledResolution;

final class ClickGuiLayout {
    final float contentX;
    final float contentY;
    final float navX;
    final float navY;
    final float navW;
    final float detailX;
    final float detailW;
    final float sideX;
    final float sideW;
    final float windowW;
    final float panelH;
    final boolean sidePanelVisible;

    private ClickGuiLayout(float contentX, float contentY, float navX, float navY, float navW,
                           float detailX, float detailW, float sideX, float sideW,
                           float windowW, float panelH, boolean sidePanelVisible) {
        this.contentX = contentX;
        this.contentY = contentY;
        this.navX = navX;
        this.navY = navY;
        this.navW = navW;
        this.detailX = detailX;
        this.detailW = detailW;
        this.sideX = sideX;
        this.sideW = sideW;
        this.windowW = windowW;
        this.panelH = panelH;
        this.sidePanelVisible = sidePanelVisible;
    }

    static ClickGuiLayout calculate(ScaledResolution sr) {
        float screenW = sr.getScaledWidth();
        float screenH = sr.getScaledHeight();
        boolean sidePanelVisible = screenW >= 900.0f;
        float sideW = sidePanelVisible ? YozakuraClickGui.SIDE_W : 0.0f;
        float available = Math.max(360.0f, screenW - 24.0f);
        float detailW = Math.min(YozakuraClickGui.DETAIL_MAX_W,
                Math.max(YozakuraClickGui.DETAIL_MIN_W, available - YozakuraClickGui.CARD_W - YozakuraClickGui.GAP
                        - (sidePanelVisible ? sideW + YozakuraClickGui.GAP : 0.0f)));
        float totalW = YozakuraClickGui.CARD_W + YozakuraClickGui.GAP + detailW
                + (sidePanelVisible ? YozakuraClickGui.GAP + sideW : 0.0f);
        if (totalW > available) {
            detailW = Math.max(YozakuraClickGui.DETAIL_MIN_W, available - YozakuraClickGui.CARD_W - YozakuraClickGui.GAP
                    - (sidePanelVisible ? sideW + YozakuraClickGui.GAP : 0.0f));
            totalW = YozakuraClickGui.CARD_W + YozakuraClickGui.GAP + detailW
                    + (sidePanelVisible ? YozakuraClickGui.GAP + sideW : 0.0f);
        }

        float contentX = Math.max(10.0f, screenW / 2.0f - totalW / 2.0f);
        if (ClickGUI.windowX.getValue() >= 0.0D) {
            contentX = clamp(ClickGUI.windowX.getValue().floatValue(), 10.0f,
                    Math.max(10.0f, screenW - totalW - 10.0f));
        }
        float navY = 12.0f;
        if (ClickGUI.windowY.getValue() >= 0.0D) {
            navY = clamp(ClickGUI.windowY.getValue().floatValue(), 6.0f,
                    Math.max(6.0f, screenH - 260.0f));
        }
        float detailX = contentX + YozakuraClickGui.CARD_W + YozakuraClickGui.GAP;
        float sideX = detailX + detailW + YozakuraClickGui.GAP;
        float navX = detailX;
        float navW = detailW + (sidePanelVisible ? YozakuraClickGui.GAP + sideW : 0.0f);
        float contentY = navY + YozakuraClickGui.NAV_H + 12.0f;
        float panelH = Math.max(280.0f, screenH - contentY - 12.0f);
        return new ClickGuiLayout(contentX, contentY, navX, navY, navW, detailX, detailW,
                sideX, sideW, totalW, panelH, sidePanelVisible);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
