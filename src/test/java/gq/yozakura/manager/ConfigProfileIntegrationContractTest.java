package gq.yozakura.manager;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class ConfigProfileIntegrationContractTest {
    @Test
    public void forgeShutdownPersistsTheCurrentSnapshotEvenInsideTheAutoSaveDelay() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/Client.java");
        String shutdownHook = method(client, "    private void registerShutdownHook() {", "\n    }\n");

        assertTrue(shutdownHook.contains("fileManager.saveModulesQuietly();"));
        assertTrue(!shutdownHook.contains("fileManager.saveIfDirtyQuietly();"));
    }

    @Test
    public void startupAndShareableProfilesUseTheSameSnapshotApplicationPath() throws IOException {
        String source = source("src/main/java/gq/yozakura/manager/FileManager.java");
        String startupLoad = method(source, "    public synchronized void loadModules(boolean quiet)",
                "    public synchronized List<String> listProfiles()");
        String profileLoad = method(source, "    public synchronized void loadProfile(String name)",
                "    public synchronized void markDirty()");

        assertTrue(startupLoad.contains("applySnapshot(snapshot);"));
        assertTrue(profileLoad.contains("applySnapshot(profileStore.load(name));"));
    }

    @Test
    public void loadedProfileBecomesTheCurrentPersistedConfiguration() throws IOException {
        String source = source("src/main/java/gq/yozakura/manager/FileManager.java");
        String profileLoad = method(source, "    public synchronized void loadProfile(String name)",
                "    public synchronized void markDirty()");

        int apply = profileLoad.indexOf("applySnapshot(profileStore.load(name));");
        int persist = profileLoad.indexOf("writeSnapshot(snapshot);");
        assertTrue("The selected profile must apply before it replaces the current startup snapshot",
                apply >= 0 && persist > apply);
    }

    @Test
    public void clickGuiLanguageUsesTheNormalPersistedValueSnapshot() throws IOException {
        String clickGui = source("src/main/java/gq/yozakura/module/render/ClickGUI.java");
        String fileManager = source("src/main/java/gq/yozakura/manager/FileManager.java");

        assertTrue(clickGui.contains("public static final Mode<ClientLanguage> language"));
        assertTrue(clickGui.contains("this.addValues(guiStyle, palette, language,"));
        assertTrue(fileManager.contains("((Mode) value).setMode(moduleJson.get(value.getName()).getAsString());"));
        assertTrue(fileManager.contains("moduleJson.addProperty(value.getName(), ((Mode) value).getModeAsString());"));
    }

    @Test
    public void panelDimensionsUseTheNormalPersistedNumbersSnapshot() throws IOException {
        String clickGui = source("src/main/java/gq/yozakura/module/render/ClickGUI.java");
        String fileManager = source("src/main/java/gq/yozakura/manager/FileManager.java");

        assertTrue(clickGui.contains("public static final Numbers<Double> panelWidth"));
        assertTrue(clickGui.contains("public static final Numbers<Double> panelHeight"));
        assertTrue(clickGui.contains("public static final Numbers<Double> panelX"));
        assertTrue(clickGui.contains("public static final Numbers<Double> panelY"));
        assertTrue(clickGui.contains("panelWidth, panelHeight, panelX, panelY"));
        assertTrue(fileManager.contains("((Numbers) value).setNumberValue(moduleJson.get(value.getName()).getAsDouble());"));
        assertTrue(fileManager.contains("moduleJson.addProperty(value.getName(), (Number) value.getValue());"));
    }

    @Test
    public void clickGuiKeepsAUsableDefaultKeyWhenLegacyConfigStoredNone() throws IOException {
        String source = source("src/main/java/gq/yozakura/manager/FileManager.java");
        assertTrue(source.contains("private int resolveModuleKey(Module module, int configuredKey)"));
        assertTrue(source.contains("\"ClickGUI\".equalsIgnoreCase(module.getName())"));
        assertTrue(source.contains("configuredKey == Keyboard.KEY_NONE"));
        assertTrue(source.contains("return Keyboard.KEY_RSHIFT;"));
        assertTrue(source.contains("module.setKey(resolveModuleKey(module, moduleJson.get(\"key\").getAsInt()));"));
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue(begin >= 0 && end > begin);
        return source.substring(begin, end);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
