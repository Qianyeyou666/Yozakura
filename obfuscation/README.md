# Release obfuscation

The release is split across Linux and Windows because the configured ZKM and
JNIC-compatible tools run on Linux:

1. Gradle produces a fresh Java 8 runtime JAR.
2. Zelix KlassMaster renames Java symbols while preserving native, manifest,
   reflection, and custom-class-loader contracts.
3. JNIC translates `gq.yozakura.auth.YozakuraAuthGate` for Windows x64.
4. The verifier checks unresolved ZKM references, hashes, all non-class
   resources, required entry points, JNI method names, ZKM rename evidence,
   JNIC native methods, duplicate entries, the JNIC payload, and Java 8 class
   versions.
5. Linux publishes the verified JAR as
   `build\libs\Yozakura-obfuscated.jar`, copied to
   `build\libs\Yozakura.jar`.
6. Linux also publishes portable audit JARs under
   `build\libs\obfuscation-audit`.
7. Windows `build-native.bat` independently verifies the portable input,
   intermediate, and final JAR before embedding it in `YozakuraLoader-x64.dll`.
8. The final verifier reads RCDATA resource 101 back from the x64 DLL and
   requires its SHA-256 to match the published JAR exactly.

## Linux obfuscation

- Set `YOZAKURA_OBF_JAVA_HOME` to a Linux HotSpot JDK 21.
- Put `ZKM.jar` below `$YOZAKURA_OBF_ROOT/zkm` and the JNIC-compatible JAR
  below `$YOZAKURA_OBF_ROOT/jnic`, or set their explicit environment paths.
- Extract a compatible Linux Zig release below the JNIC tool directory.
- When using an already built clean JAR, set `YOZAKURA_RUNTIME_JAR`. Set
  `YOZAKURA_ZKM_LIBS` to the directory containing the matching Forge/Minecraft
  dependency JARs.

Run:

```sh
./obfuscate-linux.sh
```

## Windows prerequisites

- Set `JAVA8_HOME` to a JDK 8 installation for Minecraft runtime compatibility
  and JNI headers.
- Copy or mount the complete Linux `build\libs` output into the same
  repository path on Windows. This includes `build\libs\obfuscation-audit`.
- Override `YOZAKURA_OBFUSCATED_JAR`, `YOZAKURA_OBFUSCATION_INPUT`, or
  `YOZAKURA_ZKM_INTERMEDIATE` only when transferring them elsewhere.

Run:

```bat
build-native.bat
```

The release JAR is `build\libs\Yozakura-obfuscated.jar`. Intermediate files,
tool SHA-256 values, ZKM/JNIC logs, JAR verification, and native resource
verification are written below `build\obfuscation\work`. JNIC does not offer a
Windows x86 target, so this release pipeline intentionally emits only the x64
loader.

The input must be a fresh Gradle JAR. Supplying a JAR that already contains a
`myj2c/` or `dev/jnic/` payload is rejected.
