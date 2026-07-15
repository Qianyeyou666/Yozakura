package gq.yozakura.module.render;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class BedDefensePanelTest {
    @Test
    public void keepsOneIconForEachDistinctDefenseMaterialWithoutQuantities() {
        assertEquals(Arrays.asList("wool:red", "end_stone", "planks:oak"),
                BedDefensePanel.uniqueMaterials(Arrays.asList(
                        "wool:red", "wool:red", "end_stone", "planks:oak", "end_stone")));
    }

    @Test
    public void keepsAllDefenseIconsInsideOneCompactPanel() {
        assertEquals(3, BedDefensePanel.columns(3));
        assertEquals(1, BedDefensePanel.rows(3));
        assertEquals(6, BedDefensePanel.columns(8));
        assertEquals(2, BedDefensePanel.rows(8));
    }

    @Test
    public void scalesTheEntirePanelDownAsTheBedGetsFartherAway() {
        assertEquals(1.0F, BedDefensePanel.scaleForDistance(0.0F), 0.0001F);
        assertEquals(0.8F, BedDefensePanel.scaleForDistance(32.0F), 0.0001F);
        assertEquals(0.6F, BedDefensePanel.scaleForDistance(64.0F), 0.0001F);
        assertEquals(0.6F, BedDefensePanel.scaleForDistance(128.0F), 0.0001F);
    }
}
