# Release obfuscation

The maintained Windows one-click release path is `obfuscate-client.bat`:

1. Gradle runs `clean prepareObfuscation` and produces a fresh Java 8 runtime JAR plus an SRG-aligned Minecraft/Forge classpath.
2. Neko renames eligible application classes, methods, and fields. Eligible classes collapse under `n/**`.
3. Neko control-flow flattening remains globally disabled while that implementation is being reworked. No release rule re-enables it.
4. Stable JNI, native digest, reflection, manifest, custom-loader, Forge callback, and package-access class families remain at their audited ABI names. In particular, `A*` and `B*` remain beside their outer classes.
5. Third-party namespaces (`com/**`, `io/**`, `javax/**`, `javazoom/**`, and `org/**`) bypass Neko and are merged back from the clean Gradle input.
6. Eskid processes only the Neko-renamed `n/**` application boundary. The release profile enables FAST string encryption and number obfuscation, while flow, member shuffling, watermarking, packager, invokedynamic, crasher, and shrinking transforms remain disabled for Forge/Java 8 compatibility.
7. JNIC receives only the stable `gq.yozakura.k.A` / `gq.yozakura.k.B` authentication boundary. It translates nine low-frequency authentication and entitlement methods, then merges the resulting `dev/jnic/**` payload precisely into the complete Neko + Eskid JAR. Hot runtime permit wrappers remain on the registered JNI bridge.
8. The verifier requires a non-empty Neko class/member mapping, real Eskid string/number counts with no missing-class warnings, broad rename evidence, the `n/**` namespace, all non-class resources, required ABI classes and JNI method names, Java 8 bytecode, valid Minecraft GUI callbacks, and the expected expanded JNIC payload.
9. `build-native.bat` independently re-verifies the clean input, Neko and Eskid intermediates, mapping, Eskid log, and final JAR before embedding the protected runtime into `YozakuraLoader-x64.dll`.
10. The native payload verifier reads RCDATA resource 101 back from the x64 DLL and requires its SHA-256 to match both the named release JAR and `build/libs/Yozakura.jar`.
11. Full mode requires a valid Themida project and fails closed. It never publishes an unprotected fallback.

## Windows one-click release

Set `YOZAKURA_NEKO_HOME` and `YOZAKURA_ESKID_JAR` when those tools are not at their defaults. Neko defaults to `D:\obf\neko-obfuscator-main`; Eskid defaults to `D:\obf\Eskid\build\libs\Eskid-0.42.jar`.

The Windows pipeline uses the self-owned JNIC-compatible tool at `D:\obf\jnic\jnic-3.7.0.jar` by default. Override it with `YOZAKURA_JNIC_JAR` when required. The tool JAR and its nearby Zig compiler are still required to exist; their SHA-256 values are recorded in the release audit output.

Full mode uses `D:\obf\Themida v3.1.8.0\Themida64.exe` and `1.tmd` by default. Override them with `YOZAKURA_THEMIDA_HOME`, `YOZAKURA_THEMIDA_PROJECT`, or `YOZAKURA_THEMIDA_PROTECTOR` when needed. The pipeline checks the executable, wrapper, project, VM profile count, process exit code, output existence, and input/output hash difference instead of relying on an environment acknowledgement flag.

The Neko JVM defaults to `-Xmx10240m`. Override it with `YOZAKURA_NEKO_JVM_OPTS` when necessary.

```bat
obfuscate-client.bat preflight
obfuscate-client.bat jar-only
obfuscate-client.bat no-themida
obfuscate-client.bat
```

Artifacts:

- Full protected JAR: `build\libs\Yozakura-1.5.0-neko-eskid-jnic.jar`
- Themida-isolation JAR: `build\libs\Yozakura-1.5.0-neko-eskid-jnic-no-themida.jar`
- x64 loader: `build\libs\YozakuraLoader-x64.dll`
- Neko/Eskid/JNIC logs, mappings, and verification reports: `build\obfuscation\neko-eskid-jnic-*`

## Protection boundary

Neko provides broad Java class/member renaming. Eskid adds string and numeric constant protection to the renamed application boundary. The Windows pipeline does not claim Java-wide flow obfuscation: Neko CFF remains disabled, and Eskid 0.42 flow is disabled because it mutates boolean calls, `StringBuilder.toString()`, class initializers, and fields even when its sub-options are off. JNIC flow and string protection remain confined to the nine translated A/B authentication methods. Re-enable Java-wide flow only after the implementation passes independent memory, bytecode, Java 8, Forge callback, and runtime tests.

## Portable Linux path

`obfuscate-linux.sh` is an independent portable/legacy path and may use a different configured toolchain. Its artifacts are not accepted as Windows release inputs unless they pass the same current Java 8, ABI, resource, and native-payload verification contracts.

## Windows prerequisites

- JDK 8 for Minecraft runtime compatibility, `javap`, and JNI headers.
- JDK 21 for Gradle, Neko, Eskid, and JNIC-compatible tool execution.
- The Neko CLI distribution, Eskid JAR, and the self-owned JNIC-compatible tool with its required Zig compiler.
- Visual Studio x64 build tools and WebView2 dependencies for the loader DLL.
- The configured Themida x64 installation and a valid VM project for full mode; use `no-themida` only for isolated technical validation.

The input must be a fresh Gradle JAR. Any input already containing `myj2c/**` or `dev/jnic/**` is rejected.
