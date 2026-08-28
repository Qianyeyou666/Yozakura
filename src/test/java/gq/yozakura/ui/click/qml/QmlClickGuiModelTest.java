package gq.yozakura.ui.click.qml;

import gq.yozakura.module.ModuleType;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QmlClickGuiModelTest {
    @Test
    public void categorySelectionRebuildsTheVisibleModuleRows() {
        TestModule combat = new TestModule("AimAssist", ModuleType.Combat, "Aim helper");
        TestModule render = new TestModule("HUD", ModuleType.Render, "Heads-up display");
        QmlClickGuiModel model = new QmlClickGuiModel(Arrays.<QmlClickGuiModel.Entry>asList(combat, render), false);

        assertEquals("Combat", model.activeCategory.peek());
        assertEquals(1, model.modules.size());
        assertEquals("AimAssist", model.modules.get(0).get("name"));

        model.selectCategory("Render");

        assertEquals("Render", model.activeCategory.peek());
        assertEquals(1, model.modules.size());
        assertEquals("HUD", model.modules.get(0).get("name"));
    }

    @Test
    public void toggleUpdatesMinecraftStateAndTheExistingQmlModel() {
        TestModule combat = new TestModule("AimAssist", ModuleType.Combat, "Aim helper");
        QmlClickGuiModel model = new QmlClickGuiModel(Arrays.<QmlClickGuiModel.Entry>asList(combat), false);

        model.setModuleEnabled("AimAssist", true);

        assertTrue(combat.state());
        assertEquals(Boolean.TRUE, model.modules.get(0).get("enabled"));

        model.setModuleEnabled("AimAssist", false);

        assertFalse(combat.state());
        assertEquals(Boolean.FALSE, model.modules.get(0).get("enabled"));
    }

    @Test
    public void syncReportsOnlyExternalModuleChanges() {
        TestModule combat = new TestModule("AimAssist", ModuleType.Combat, "Aim helper");
        QmlClickGuiModel model = new QmlClickGuiModel(
                Arrays.<QmlClickGuiModel.Entry>asList(combat), false);

        assertFalse(model.sync());
        combat.setState(true);
        assertTrue(model.sync());
        assertFalse(model.sync());
    }

    @Test
    public void languageSwitchRefreshesLabelsWithoutReloadingTheScene() {
        TestModule combat = new TestModule("AimAssist", ModuleType.Combat, "Aim helper");
        combat.chineseName = "辅助瞄准";
        QmlClickGuiModel model = new QmlClickGuiModel(Arrays.<QmlClickGuiModel.Entry>asList(combat), false);

        model.setChinese(true);

        assertTrue(model.chinese.peek());
        assertEquals("辅助瞄准", model.modules.get(0).get("displayName"));
        Map<String, Object> category = model.categories.get(0);
        assertEquals("战斗类", category.get("displayName"));
    }

    @Test
    public void searchFiltersTheCurrentCategoryWithoutReloadingTheScene() {
        TestModule aim = new TestModule("AimAssist", ModuleType.Combat, "Aim helper");
        TestModule bot = new TestModule("AntiBot", ModuleType.Combat, "Entity filter");
        QmlClickGuiModel model = new QmlClickGuiModel(
                Arrays.<QmlClickGuiModel.Entry>asList(aim, bot), false);

        model.setSearch("entity");

        assertEquals("entity", model.search.peek());
        assertEquals(1, model.modules.size());
        assertEquals("AntiBot", model.modules.get(0).get("name"));
    }

    @Test
    public void closeRequestIsConsumedOnce() {
        QmlClickGuiModel model = new QmlClickGuiModel(
                Arrays.<QmlClickGuiModel.Entry>asList(), false);

        model.requestClose();

        assertTrue(model.consumeCloseRequest());
        assertFalse(model.consumeCloseRequest());
    }

    @Test
    public void changingModeImmediatelyRebuildsVisibleSettings() {
        final ModeSetting mode = new ModeSetting();
        TestModule module = new TestModule("Velocity", ModuleType.Combat, "Velocity control") {
            @Override
            public List<QmlClickGuiModel.Setting> settings() {
                ArrayList<QmlClickGuiModel.Setting> values = new ArrayList<QmlClickGuiModel.Setting>();
                values.add(mode);
                if ("Advanced".equals(mode.current())) {
                    values.add(new BooleanSetting());
                }
                return values;
            }
        };
        QmlClickGuiModel model = new QmlClickGuiModel(
                Arrays.<QmlClickGuiModel.Entry>asList(module), false);
        model.toggleSettings("Velocity");

        assertEquals(1, model.settings.size());

        model.cycleMode("Velocity", "Mode", 1);

        assertEquals("Advanced", model.settings.get(0).get("current"));
        assertEquals(2, model.settings.size());
    }

    private static class TestModule implements QmlClickGuiModel.Entry {
        private final String name;
        private final ModuleType category;
        private final String description;
        private String chineseName;
        private boolean state;

        private TestModule(String name, ModuleType category, String description) {
            this.name = name;
            this.chineseName = name;
            this.category = category;
            this.description = description;
        }

        @Override public String name() { return name; }
        @Override public String chineseName() { return chineseName; }
        @Override public String description() { return description; }
        @Override public ModuleType category() { return category; }
        @Override public boolean state() { return state; }
        @Override public void setState(boolean state) {
            this.state = state;
        }
        @Override public int key() { return 0; }
        @Override public boolean hasSettings() { return !settings().isEmpty(); }
        @Override public List<QmlClickGuiModel.Setting> settings() { return Collections.emptyList(); }
    }

    private static final class ModeSetting implements QmlClickGuiModel.Setting {
        private String current = "Basic";
        @Override public String name() { return "Mode"; }
        @Override public String displayName() { return "Mode"; }
        @Override public String type() { return "mode"; }
        @Override public Object current() { return current; }
        @Override public double minimum() { return 0; }
        @Override public double maximum() { return 1; }
        @Override public List<String> options() { return Arrays.asList("Basic", "Advanced"); }
        @Override public void setMode(String value) { current = value; }
        @Override public void setBoolean(boolean value) { }
        @Override public void setNumber(double value) { }
    }

    private static final class BooleanSetting implements QmlClickGuiModel.Setting {
        private boolean current;
        @Override public String name() { return "OnlyGround"; }
        @Override public String displayName() { return "Only Ground"; }
        @Override public String type() { return "boolean"; }
        @Override public Object current() { return current; }
        @Override public double minimum() { return 0; }
        @Override public double maximum() { return 1; }
        @Override public List<String> options() { return Collections.emptyList(); }
        @Override public void setMode(String value) { }
        @Override public void setBoolean(boolean value) { current = value; }
        @Override public void setNumber(double value) { }
    }
}
