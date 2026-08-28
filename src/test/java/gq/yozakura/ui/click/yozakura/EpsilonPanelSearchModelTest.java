package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpsilonPanelSearchModelTest {
    @Test
    public void searchGeometryAndFilteringMatchTheModulePanelContract() {
        PanelClickGuiLayout.Rect panel = new PanelClickGuiLayout.Rect(100.0f, 20.0f, 164.0f, 314.0f);
        PanelClickGuiLayout.Rect search = EpsilonPanelSearchModel.bounds(panel);
        assertEquals(182.0f, search.x(), 0.001f);
        assertEquals(28.0f, search.y(), 0.001f);
        assertEquals(76.0f, search.width(), 0.001f);
        assertEquals(18.0f, search.height(), 0.001f);
        assertTrue(EpsilonPanelSearchModel.matches("KillAura", "killa"));
        assertTrue(EpsilonPanelSearchModel.matches("Bridge Assist", "assist"));
        assertTrue(EpsilonPanelSearchModel.matches("KillAura", "杀戮光环", "杀戮"));
        assertFalse(EpsilonPanelSearchModel.matches("Velocity", "击退控制", "aura"));
    }
}
