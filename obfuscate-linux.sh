#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

OBF_ROOT="${YOZAKURA_OBF_ROOT:-$ROOT/../obf}"
ZKM_JAR="${YOZAKURA_ZKM_JAR:-$OBF_ROOT/zkm/ZKM.jar}"
JNIC_JAR="${YOZAKURA_JNIC_JAR:-$OBF_ROOT/jnic/jnic3.7.0.jar}"
ZKM_LIBS="${YOZAKURA_ZKM_LIBS:-$ROOT/build/obfuscation/zkm-libs}"
OBF_JAVA_HOME="${YOZAKURA_OBF_JAVA_HOME:-${JAVA_HOME:-}}"
ZKM_JAVA_HOME="${YOZAKURA_ZKM_JAVA_HOME:-$OBF_JAVA_HOME}"
WORK="$ROOT/build/obfuscation/work"
INPUT_DIR="$ROOT/build/obfuscation/input"
OUTPUT="$ROOT/build/libs/Yozakura-obfuscated.jar"
AUDIT_DIR="$ROOT/build/libs/obfuscation-audit"

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

require_file() {
    [[ -f "$1" ]] || die "$2 was not found: $1"
}

[[ -n "$OBF_JAVA_HOME" ]] || die "set YOZAKURA_OBF_JAVA_HOME to a Linux JDK 21 installation"
[[ -n "$ZKM_JAVA_HOME" ]] || die "set YOZAKURA_ZKM_JAVA_HOME to the JDK required by ZKM"
JAVA="$OBF_JAVA_HOME/bin/java"
ZKM_JAVA="$ZKM_JAVA_HOME/bin/java"
JAVAP="$OBF_JAVA_HOME/bin/javap"
require_file "$JAVA" "Java launcher"
require_file "$ZKM_JAVA" "ZKM Java launcher"
require_file "$JAVAP" "javap"
require_file "$ZKM_JAR" "ZKM JAR"
require_file "$JNIC_JAR" "JNIC-compatible JAR"
[[ -d "$ZKM_LIBS" ]] || die "ZKM dependency directory was not found: $ZKM_LIBS"
find "$ZKM_LIBS" -maxdepth 1 -type f -name '*.jar' -print -quit | grep -q . ||
    die "ZKM dependency directory contains no JARs: $ZKM_LIBS"
find "$(dirname "$JNIC_JAR")" -maxdepth 3 -type f -name zig -perm -u+x -print -quit | grep -q . ||
    die "a Linux Zig executable must be extracted below $(dirname "$JNIC_JAR")"

if [[ -n "${YOZAKURA_RUNTIME_JAR:-}" ]]; then
    INPUT="$YOZAKURA_RUNTIME_JAR"
    require_file "$INPUT" "unobfuscated runtime JAR"
else
    printf '[0/4] Building the clean runtime JAR and ZKM classpath...\n'
    JAVA_HOME="$OBF_JAVA_HOME" ./gradlew prepareObfuscation \
    INPUT="$INPUT_DIR/Yozakura-unobfuscated.jar"
    require_file "$INPUT" "Gradle obfuscation input"
fi

if unzip -Z1 "$INPUT" | grep -Eq '^(myj2c|dev/jnic)/'; then
    die "input JAR is already Java-to-native transformed; provide a fresh Gradle JAR"
fi

rm -rf "$WORK"
mkdir -p "$WORK" "$INPUT_DIR" "$ROOT/build/libs"
STAGED_INPUT="$WORK/Yozakura-input.jar"
ZKM_OUTPUT="$WORK/Yozakura-zkm.jar"
JNIC_OUTPUT="$WORK/Yozakura-jnic.jar"
ZKM_SCRIPT="$WORK/release.zkm"
ZKM_LOG="$WORK/zkm.log"
JNIC_LOG="$WORK/jnic.log"
cp -- "$INPUT" "$STAGED_INPUT"
cp -- "$STAGED_INPUT" "$INPUT_DIR/Yozakura-unobfuscated.jar"

python3 - "$ROOT/obfuscation/zkm-release.zkm.template" "$ZKM_SCRIPT" \
    "$ZKM_LIBS/*.jar" "$STAGED_INPUT" "$ZKM_OUTPUT" <<'PY'
import pathlib
import sys
import zipfile

template, output, classpath, input_jar, output_jar = sys.argv[1:]
def zkm_path(value):
    return value.replace('\\', '/').replace('"', '\\"')
def class_exclusion(name):
    package, simple_name = name.rsplit('.', 1)
    return f'exclude class {package}({simple_name});'
text = pathlib.Path(template).read_text(encoding='utf-8')
text = text.replace('@ZKM_CLASSPATH@', zkm_path(classpath))
text = text.replace('@INPUT_JAR@', zkm_path(input_jar))
text = text.replace('@OUTPUT_JAR@', zkm_path(output_jar))
prefixes = (
    'gq/yozakura/bridge/forge/',
    'gq/yozakura/event/bridge/',
    'gq/yozakura/core/modern/ModernForgeEventBridge',
)
with zipfile.ZipFile(input_jar) as archive:
    entries = [item.filename for item in archive.infolist() if item.filename.endswith('.class')]
    names = sorted(
        item.filename[:-6].replace('/', '.')
        for item in archive.infolist()
        if item.filename.endswith('.class') and item.filename.startswith(prefixes)
    )
    external_names = sorted(
        name[:-6].replace('/', '.') for name in entries
        if not name.startswith('gq/yozakura/')
    )
preserved_packages = sorted({
    name.rsplit('.', 1)[0] for name in names + external_names
})
dynamic_lines = [f'exclude package {name};' for name in preserved_packages]
dynamic_lines.extend(class_exclusion(name) for name in names)
for name in external_names:
    dynamic_lines.append(class_exclusion(name))
    dynamic_lines.append(f'exclude {name}^*(*);')
dynamic_excludes = '\n'.join(dynamic_lines)
text = text.replace('@DYNAMIC_EXCLUDES@', dynamic_excludes)
pathlib.Path(output).write_text(text, encoding='ascii')
PY

{
    printf 'ZKM JAR: %s\n' "$ZKM_JAR"
    printf 'ZKM SHA-256: '
    sha256sum "$ZKM_JAR" | cut -d' ' -f1
    printf 'JNIC JAR: %s\n' "$JNIC_JAR"
    printf 'JNIC SHA-256: '
    sha256sum "$JNIC_JAR" | cut -d' ' -f1
} > "$WORK/tool-hashes.txt"

printf '[1/4] Parsing and running Zelix KlassMaster...\n'
(
    cd "$(dirname "$ZKM_JAR")"
    "$ZKM_JAVA" -jar "$ZKM_JAR" -p "$ZKM_SCRIPT"
    "$ZKM_JAVA" -jar "$ZKM_JAR" -v -l "$ZKM_LOG" "$ZKM_SCRIPT"
)
require_file "$ZKM_OUTPUT" "ZKM output JAR"
require_file "$ZKM_LOG" "ZKM audit log"
if grep -Eq "WARNING: (Class|Field|Method) '.+' not found" "$ZKM_LOG"; then
    grep -E "WARNING: (Class|Field|Method) '.+' not found" "$ZKM_LOG" | head -10 >&2
    die "ZKM reported unresolved bytecode contracts"
fi

printf '[2/4] Translating the authentication gate with JNIC...\n'
(
    cd "$(dirname "$JNIC_JAR")"
    "$JAVA" -jar "$JNIC_JAR" "$ZKM_OUTPUT" "$JNIC_OUTPUT" \
        "$ROOT/obfuscation/jnic-release.xml"
) 2>&1 | tee "$JNIC_LOG"
require_file "$JNIC_OUTPUT" "JNIC output JAR"

printf '[3/4] Verifying the transformed JAR...\n'
python3 "$ROOT/tools/verify_obfuscated_jar.py" \
    --input "$STAGED_INPUT" \
    --zkm "$ZKM_OUTPUT" \
    --jar "$JNIC_OUTPUT" \
    --java-home "$OBF_JAVA_HOME" \
    --report "$WORK/verification.txt"

printf '[4/4] Publishing the verified release JAR...\n'
mkdir -p "$AUDIT_DIR"
cp -- "$JNIC_OUTPUT" "$OUTPUT.tmp"
mv -f -- "$OUTPUT.tmp" "$OUTPUT"
cp -- "$OUTPUT" "$ROOT/build/libs/Yozakura.jar"
cp -- "$STAGED_INPUT" "$AUDIT_DIR/Yozakura-input.jar"
cp -- "$ZKM_OUTPUT" "$AUDIT_DIR/Yozakura-zkm.jar"
printf 'Release output: %s\n' "$OUTPUT"
