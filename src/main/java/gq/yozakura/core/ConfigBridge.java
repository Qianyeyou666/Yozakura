package gq.yozakura.core;

import gq.yozakura.manager.FileManager;

import java.io.IOException;

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

    public static void setAutoSaveSuspended(boolean suspended) {
        getFileManager().setAutoSaveSuspended(suspended);
    }
}
