# VapuLite

Free Forge-based Minecraft 1.8.9 client mod.

## Requirements

- Windows
- JDK 8: `C:\Users\qiany\.jdks\corretto-1.8.0_492`
- Gradle wrapper from this repository. Do not upgrade this project to Gradle 9; ForgeGradle 2.1 requires the old wrapper.
- Visual Studio C++ build tools, only for native DLL builds.

## Build

```bat
gradlew.bat build
```

The main Java artifact is written to `build\libs\VapuLite.jar`.

## IntelliJ IDEA

Use the project Gradle wrapper and JDK 8:

- Gradle distribution: `Wrapper`
- Gradle JVM: `corretto-1.8`
- Run configuration: `Minecraft Client`

If ForgeGradle reports asset download errors, repair the local Minecraft 1.8 asset cache:

```bat
powershell -ExecutionPolicy Bypass -File tools\download-mc-assets.ps1
```

## Native DLL

Build native loaders after the jar exists:

```bat
build-native.bat
```

Outputs:

- `build\libs\VapuLiteLoader-x64.dll`
- `build\libs\VapuLiteLoader-x86.dll`
- `build\libs\VapuLiteLoader.dll`

To wrap an arbitrary jar into the native loader:

```bat
jar-to-dll.bat build\libs\VapuLite.jar gq.vapulite.Vapu.Client VapuLiteLoader x64
```

## License

This project is licensed under [GNU GPL v3.0](LICENSE).
