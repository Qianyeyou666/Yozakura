package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PanelClickGuiModuleRowTest {
    @Test
    public void timewarpModuleCardMetricsStayExact() {
        assertEquals(48.0f, PanelClickGuiModuleRow.HEIGHT, 0.001f);
        assertEquals(0.70f, PanelClickGuiModuleRow.TITLE_SCALE, 0.001f);
        assertEquals(0.52f, PanelClickGuiModuleRow.DESCRIPTION_SCALE, 0.001f);
    }

    @Test
    public void titleAndDescriptionUseTwoLineCardLayout() {
        assertEquals(12.0f, PanelClickGuiModuleRow.titleY(48.0f, 12.0f), 0.001f);
        assertEquals(11.0f, PanelClickGuiModuleRow.titleY(48.0f, 15.0f), 0.001f);
        assertEquals(26.0f, PanelClickGuiModuleRow.descriptionY(48.0f, 15.0f), 0.001f);
    }
}
