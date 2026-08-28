package gq.yozakura.ui.click.yozakura;

import gq.yozakura.module.ModuleType;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class EpsilonPanelReferenceTest {
    @Test
    public void iconCharsMatchThePinnedEpsilonCommit() {
        assertEquals("\uf889", EpsilonPanelIcons.SWORDS);
        assertEquals("\uf0d3", EpsilonPanelIcons.PERSON);
        assertEquals("\ue566", EpsilonPanelIcons.DIRECTIONS_RUN);
        assertEquals("\ue3ae", EpsilonPanelIcons.BRUSH);
        assertEquals("\ue8b8", EpsilonPanelIcons.SETTINGS);
        assertEquals("\ue8b6", EpsilonPanelIcons.SEARCH);
        assertEquals("\ue5ca", EpsilonPanelIcons.CHECK);
        assertEquals("\ue5ce", EpsilonPanelIcons.EXPAND_LESS);
        assertEquals("\ue5cf", EpsilonPanelIcons.EXPAND_MORE);
    }

    @Test
    public void railExposesOnlyTheFourEpsilonCategories() {
        assertArrayEquals(new ModuleType[]{
                ModuleType.Combat,
                ModuleType.Player,
                ModuleType.Movement,
                ModuleType.Render
        }, EpsilonPanelCategories.visibleCategories());
        assertEquals(ModuleType.Player, EpsilonPanelCategories.visibleCategory(ModuleType.World));
        assertEquals(ModuleType.Render, EpsilonPanelCategories.visibleCategory(ModuleType.Other));
    }

    @Test
    public void fontAdapterAccountsForTheLocalHalfScaleAndBaselineOffset() {
        assertEquals(48.0f, EpsilonPanelFonts.SOURCE_PIXEL_SIZE, 0.001f);
        assertEquals(0.35f, EpsilonPanelFonts.DEFAULT_SCALE, 0.001f);
        assertEquals(2.0f, EpsilonPanelFonts.LOCAL_RENDER_SCALE, 0.001f);
        assertEquals(26, EpsilonPanelFonts.pixelSize(0.78f));
        assertEquals(23, EpsilonPanelFonts.pixelSize(0.68f));
        assertEquals(34, EpsilonPanelFonts.pixelSize(1.02f));
        assertEquals(3.0f, EpsilonPanelFonts.BASELINE_COMPENSATION, 0.001f);
        assertEquals(13.104f, EpsilonPanelFonts.lineHeight(0.78f), 0.001f);
    }
}
