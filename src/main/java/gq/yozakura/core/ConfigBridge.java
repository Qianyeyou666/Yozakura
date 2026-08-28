package gq.yozakura.core;

import gq.yozakura.manager.FileManager;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.List;

public final class ConfigBridge {
    private static FileManager standaloneFileManager;

    private ConfigBridge() {
    }

    public static synchronized FileManager getFileManager() {
        if (Client.instance != null && Client.instance.fileManager != null) {
            return Client.instance.fileManager;
        }
        if (standaloneFileManager == null) {
            standaloneFileManager = new FileManager();
        }
        return standaloneFileManager;
    }

    public static void saveModules() throws IOException {
        getFileManager().saveModules();
    }

    public static void loadModules() throws IOException {
        getFileManager().loadModules();
    }

    public static List<String> listProfiles() throws IOException {
        return getFileManager().listProfiles();
    }

    public static void saveProfile(String name) throws IOException {
        getFileManager().saveProfile(name);
    }

    public static void loadProfile(String name) throws IOException {
        getFileManager().loadProfile(name);
    }

    public static String readProfileSnapshot(String name) throws IOException {
        return getFileManager().readProfileSnapshot(name);
    }

    public static void saveProfileSnapshot(String name, String snapshot) throws IOException {
        getFileManager().saveProfileSnapshot(name, snapshot);
    }

    public static String exportSnapshot() {
        return getFileManager().exportSnapshot();
    }

    public static void importSnapshot(String snapshot) throws IOException {
        getFileManager().importSnapshot(snapshot);
    }

    public static File getProfileDirectory() throws IOException {
        return getFileManager().getProfileDirectory();
    }

    public static void openProfileDirectory() throws IOException {
        File directory = getProfileDirectory();
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new IOException("Opening the config profile directory is not supported on this system");
        }
        Desktop.getDesktop().open(directory);
    }

    public static void loadModulesQuietly() {
        try {
            getFileManager().loadModules(true);
        } catch (Throwable throwable) {
            FileManager.logConfigFailure("Config startup load failed", throwable);
        }
    }

    public static void markDirty() {
        getFileManager().markDirty();
    }

    public static void autoSaveTick() {
        if (YozakuraClientState.consumeStandaloneDirty()) {
            getFileManager().markDirty();
        }
        getFileManager().autoSaveTick();
    }

    public static void saveIfDirtyQuietly() {
        getFileManager().saveIfDirtyQuietly();
    }

    public static void saveModulesQuietly() {
        getFileManager().saveModulesQuietly();
    }

    public static void setAutoSaveSuspended(boolean suspended) {
        getFileManager().setAutoSaveSuspended(suspended);
    }
}
