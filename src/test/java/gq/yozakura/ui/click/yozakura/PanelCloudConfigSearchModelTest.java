package gq.yozakura.ui.click.yozakura;

import gq.yozakura.club.ClubConfigSummary;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PanelCloudConfigSearchModelTest {
    private final ClubConfigSummary alpha = config("config-alpha", "Hypixel Legit", "Sakura");
    private final ClubConfigSummary beta = config("config-beta", "Practice", "NightOwner");
    private final List<ClubConfigSummary> configs = Arrays.asList(alpha, beta);

    @Test
    public void blankQueryPreservesAllConfigs() {
        assertEquals(configs, PanelCloudConfigSearchModel.filter(configs, "  "));
    }

    @Test
    public void queryMatchesNameOrOwnerWithoutCaseSensitivity() {
        assertEquals(Arrays.asList(alpha), PanelCloudConfigSearchModel.filter(configs, "hYpIxEl"));
        assertEquals(Arrays.asList(beta), PanelCloudConfigSearchModel.filter(configs, "nightowner"));
    }

    @Test
    public void selectionUsesStableConfigIdInsteadOfFilteredIndex() {
        List<ClubConfigSummary> filtered = PanelCloudConfigSearchModel.filter(configs, "practice");

        assertSame(beta, PanelCloudConfigSearchModel.findById(filtered, "config-beta"));
        assertNull(PanelCloudConfigSearchModel.findById(filtered, "config-alpha"));
    }

    private static ClubConfigSummary config(String id, String name, String owner) {
        return new ClubConfigSummary(id, name, "public", owner, "created", "updated");
    }
}
