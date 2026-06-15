# Repository Guidelines

## Project Structure & Module Organization

Yozakura is a Minecraft Forge 1.8.9 client mod. Java sources live in `src/main/java/gq/yozakura`, split across `core`, `module`, `event`, `manager`, `ui`, `util`, `value`, and `xray`. Runtime resources are in `src/main/resources`; shaders, fonts, and GUI assets sit under `assets/minecraft`. Native loader and injector code is in `native/`; scripts are in `tools/` and the repository root. Do not edit vendored `third_party/imgui` code unless tasked. Keep `build/`, `run/`, `.gradle/`, and IDE output untracked.

## Build, Test, and Development Commands

- `./gradlew build` or `gradlew.bat build`: compiles, remaps, builds the shadow jar, and writes `build/libs/Yozakura.jar`.
- `./gradlew clean`: removes Gradle build outputs before a fresh build.
- `build-native.bat`: builds Windows DLL loaders after `build/libs/Yozakura.jar` exists; requires Visual Studio C++ tools and `JAVA8_HOME`.
- `powershell -ExecutionPolicy Bypass -File tools\download-mc-assets.ps1`: repairs missing Minecraft 1.8.9 assets.

Do not run interactive Gradle client/server tasks such as `runClient` or `runServer` for validation unless the user explicitly asks.

## Coding Style & Naming Conventions

Use UTF-8 and four-space Java indentation. Match surrounding style before adding a new pattern. Keep packages under `gq.yozakura`; place features in the closest existing package, for example `module/movement` or `util/render`. Public types use `PascalCase`; methods and fields use `camelCase`. Preserve Java 8 bytecode via `options.release = 8`. Do not commit user-local JDK, Visual Studio, or Minecraft paths.

## Design & Maintainability

Keep complex modules object-oriented and split into focused classes. Do not put large multi-responsibility features in one file when state, rendering, configuration, or event handling can be separated. Add comments only for parameters, constraints, or critical algorithms that are not obvious.

## Testing Guidelines

There is no committed `src/test` suite. Treat `./gradlew build` as the default verification gate. For UI, rendering, input, or injection changes, ask before launching Minecraft. If adding tests, place them under `src/test/java`, name classes `*Test`, and document dependencies in `build.gradle`.

## Fallback Policy

This project is under active development. Never add silent fallbacks by default. If rendering, settings, module behavior, configuration loading, or injection cannot work as designed, ask the user instead of hiding failure, disabling behavior, or switching settings automatically.

## Commit & Pull Request Guidelines

Recent history is inconsistent, so prefer clear imperative commits such as `Refactor ClickGUI module layout`. Keep commits scoped to one logical change. PRs should describe behavior changes, list verification commands, mention manual Minecraft testing, link issues, and include screenshots or clips for UI/rendering changes.
