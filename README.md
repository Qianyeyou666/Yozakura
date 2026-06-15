# Yozakura

Free Forge-based Minecraft 1.8.9 client mod.

## Requirements

- JDK 8, selected through `JAVA_HOME`, IntelliJ Gradle JVM, or a user-local Gradle property.
- Gradle wrapper from this repository. Do not upgrade this project to Gradle 9; ForgeGradle 2.1 requires the old wrapper.
- Visual Studio C++ build tools, only for Windows native DLL builds.

## Build

Linux/macOS:

```sh
./gradlew build
```

Windows:

```bat
gradlew.bat build
```

The main Java artifact is written to `build/libs/Yozakura.jar`.

## IntelliJ IDEA

Use the project Gradle wrapper and JDK 8:

- Gradle distribution: `Wrapper`
- Gradle JVM: `JAVA_HOME` or any configured JDK 8 SDK
- Run configuration: `Minecraft Client`

The Gradle `getAssets` task is patched to use HTTPS for Mojang asset downloads.
If the cache still contains corrupt partial files, repair it manually:

```bat
powershell -ExecutionPolicy Bypass -File tools\download-mc-assets.ps1
```

## Native DLL

Build native loaders after the jar exists:

```bat
build-native.bat
```

Outputs:

- `build\libs\YozakuraLoader-x64.dll`
- `build\libs\YozakuraLoader-x86.dll`
- `build\libs\YozakuraLoader.dll`

To wrap an arbitrary jar into the native loader:

```bat
jar-to-dll.bat build\libs\Yozakura.jar gq.yozakura.Yozakura.Client YozakuraLoader x64
```

## License

This project is licensed under [GNU GPL v3.0](LICENSE).
