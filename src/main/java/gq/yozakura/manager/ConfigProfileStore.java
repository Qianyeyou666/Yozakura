package gq.yozakura.manager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class ConfigProfileStore {
    private static final String EXTENSION = ".yzk";
    private static final int MAX_NAME_LENGTH = 64;

    private final File directory;

    ConfigProfileStore(File directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Profile directory is required");
        }
        this.directory = directory;
    }

    File getDirectory() throws IOException {
        ensureDirectory();
        return directory;
    }

    List<String> list() throws IOException {
        ensureDirectory();
        File[] files = directory.listFiles();
        if (files == null) {
            throw new IOException("Unable to list config profile directory: " + directory.getAbsolutePath());
        }

        List<String> profiles = new ArrayList<String>();
        for (File file : files) {
            String name = file.getName();
            if (file.isFile() && name.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
                String profileName = name.substring(0, name.length() - EXTENSION.length());
                try {
                    normalizeName(profileName);
                    profiles.add(profileName);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        Collections.sort(profiles, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                int insensitive = left.compareToIgnoreCase(right);
                return insensitive != 0 ? insensitive : left.compareTo(right);
            }
        });
        return profiles;
    }

    void save(String name, String snapshot) throws IOException {
        File profile = resolve(name, false);
        new ConfigFileStore(profile, new File(profile.getParentFile(), profile.getName() + ".bak"))
                .save(snapshot);
    }

    String load(String name) throws IOException {
        File profile = resolve(name, true);
        ConfigFileStore store = new ConfigFileStore(profile,
                new File(profile.getParentFile(), profile.getName() + ".bak"));
        String snapshot = store.load();
        if (snapshot == null) {
            throw new FileNotFoundException("Config profile not found: " + profile.getAbsolutePath());
        }
        if (store.wasBackupRecovered()) {
            FileManager.logConfigFailure("Recovered config profile from backup: " + profile.getAbsolutePath(), null);
        }
        return snapshot;
    }

    private File resolve(String name, boolean findExisting) throws IOException {
        String normalized = normalizeName(name);
        ensureDirectory();
        if (findExisting) {
            File[] files = directory.listFiles();
            if (files == null) {
                throw new IOException("Unable to list config profile directory: " + directory.getAbsolutePath());
            }
            String expectedName = normalized + EXTENSION;
            for (File file : files) {
                if (file.isFile() && file.getName().equalsIgnoreCase(expectedName)) {
                    return file;
                }
            }
        }

        File profile = new File(directory, normalized + EXTENSION);
        String directoryPath = directory.getCanonicalPath() + File.separator;
        if (!profile.getCanonicalPath().startsWith(directoryPath)) {
            throw new IllegalArgumentException("Invalid config profile name: " + name);
        }
        return profile;
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            normalized = normalized.substring(0, normalized.length() - EXTENSION.length()).trim();
        }
        if (normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH
                || normalized.endsWith(".") || normalized.toLowerCase(Locale.ROOT).contains(EXTENSION)
                || hasForbiddenCharacter(normalized)) {
            throw new IllegalArgumentException("Invalid config profile name: " + name);
        }
        return normalized;
    }

    private boolean hasForbiddenCharacter(String name) {
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (character < 32 || "<>:\"/\\|?*".indexOf(character) >= 0) {
                return true;
            }
        }
        return false;
    }

    private void ensureDirectory() throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Unable to create config profile directory: " + directory.getAbsolutePath());
        }
    }
}
