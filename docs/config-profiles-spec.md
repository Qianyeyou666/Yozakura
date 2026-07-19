# Spec: Shareable YZK Config Profiles

## Objective

Allow users to exchange plain JSON client configurations as `.yzk` files. The client scans a local profile directory, displays every valid filename in a ClickGUI-accessible screen, and lets the user save or load a selected profile without replacing the startup autosave format.

## Tech Stack and Commands

- Java 8 source compatibility, Forge 1.8.9 UI APIs, Gson already supplied by Minecraft.
- Focused tests: `gradlew.bat test --tests gq.yozakura.manager.ConfigProfileStoreTest`
- Verification: `gradlew.bat build`

## Project Structure

- `manager`: profile filename validation and filesystem persistence.
- `core`: environment-neutral bridge used by Forge and Standalone clients.
- `ui/click`: shared profile screen opened from every ClickGUI style.
- `module/config`: ClickGUI entry module for the shared screen.
- `src/test/java`: profile storage and UI wiring contracts.

## Code Style

```java
public static void loadProfile(String name) throws IOException {
    getFileManager().loadProfile(name);
}
```

Use UTF-8, four-space indentation, explicit checked failures, and existing module/value identifiers.

## Testing Strategy

- Filesystem tests cover `.yzk` discovery, sorting, import, save, and unsafe names.
- Contract tests ensure the ClickGUI entry, folder button, and load/save actions stay wired.
- The full Gradle build is the final automated gate.

## Boundaries

- Always: validate profile names, keep writes atomic, report malformed files.
- Ask first: deleting profiles or changing the startup autosave format.
- Never: load files outside the profile directory or silently substitute another profile.

## Success Criteria

- `%APPDATA%/Yozakura/configs` is created on demand.
- Only regular `.yzk` files appear, sorted by name.
- Dropped-in `.yzk` files can be selected and loaded from ClickGUI.
- Current settings can be saved to a named `.yzk` file.
- ClickGUI exposes refresh, open-folder, save, and load actions.
- Existing `module.json` startup/autosave behavior remains unchanged.
