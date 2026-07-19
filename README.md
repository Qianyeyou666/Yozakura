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

Build native loaders with the production HTTPS authentication endpoint:

```bat
build-native.bat
```

The production endpoint is compiled into the native layer and is not emitted as
a configurable JAR resource. Native certificate pinning remains the trust
boundary for login and heartbeat traffic.

The DLL registers the JNI authentication bridge before starting the embedded
client. Login, session token storage, entitlement expiry parsing and heartbeat
verification run through WinHTTP in the native layer. Running the extracted JAR
without the native loader fails closed. After login, the customer sees the
verified role and expiry returned by the server.

Run `obfuscate-linux.sh` on Linux first. It applies ZKM and the JNIC-compatible
translator, verifies the JAR, and writes
`build/libs/Yozakura-obfuscated.jar`. Then run `build-native.bat` on Windows;
the BAT independently verifies the Linux artifacts before embedding the JAR.
See `obfuscation\README.md` for environment variables and verification gates.

Outputs:

- `build\libs\Yozakura-obfuscated.jar`
- `build\libs\Yozakura.jar`
- `build\libs\YozakuraLoader-x64.dll`
- `build\libs\YozakuraLoader.dll`

JNIC does not provide a Windows x86 target, so the protected release build is
x64-only.

To wrap an arbitrary jar into the native loader:

```bat
jar-to-dll.bat build\libs\Yozakura.jar gq.yozakura.Yozakura.Client YozakuraLoader x64
```

## License

This project is licensed under [GNU GPL v3.0](LICENSE).
