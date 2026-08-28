#!/usr/bin/env python3
import argparse
import collections
import hashlib
import re
import subprocess
import sys
import zipfile
from pathlib import Path


def fail(message):
    raise RuntimeError("Obfuscation verification failed: " + message)


def entries(path):
    with zipfile.ZipFile(path) as archive:
        return [item.filename for item in archive.infolist()]


def text_entry(path, name):
    with zipfile.ZipFile(path) as archive:
        try:
            return archive.read(name).decode("utf-8", errors="replace")
        except KeyError:
            fail("required text entry is missing: " + name)


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def javap(javap_executable, jar, *arguments):
    result = subprocess.run(
        [str(javap_executable), "-classpath", str(jar), *arguments],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.returncode != 0:
        fail("javap failed: " + result.stdout.strip())
    return result.stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--zkm", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--java-home", required=True, type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    for path in (args.input, args.zkm, args.jar):
        if not path.is_file():
            fail("JAR was not found: " + str(path))
    javap_executable = args.java_home / "bin" / "javap"
    if not javap_executable.is_file():
        fail("javap was not found: " + str(javap_executable))

    input_entries = entries(args.input)
    zkm_entries = entries(args.zkm)
    final_entries = entries(args.jar)
    duplicate = next((name for name, count in collections.Counter(final_entries).items() if count > 1), None)
    if duplicate:
        fail("the final JAR contains a duplicate ZIP entry: " + duplicate)

    required = [
        "META-INF/MANIFEST.MF",
        "META-INF/yozakura_at.cfg",
        "mcmod.info",
        "gq/yozakura/YozakuraAttachPoint.class",
        "gq/yozakura/YozakuraBootstrap.class",
        "gq/yozakura/Yozakuraloader.class",
        "gq/yozakura/k/A.class",
        "gq/yozakura/k/B.class",
        "gq/yozakura/k/vendor/tech/skidonion/obfuscator/inline/C.class",
        "gq/yozakura/core/Client.class",
        "gq/yozakura/core/StandaloneClient.class",
        "gq/yozakura/core/ModernForgeClient.class",
        "gq/yozakura/module/Module.class",
        "gq/yozakura/event/bus/EventManager.class",
        "gq/yozakura/event/api/EventManager.class",
        "gq/yozakura/bridge/MovementInputBridge.class",
        "gq/yozakura/ui/click/yozakura/YozakuraClickGui.class",
    ]
    for name in required:
        if name not in final_entries:
            fail("required entry is missing: " + name)
        if name not in zkm_entries:
            fail("ZKM renamed or removed a required contract entry: " + name)

    resources = [
        name for name in input_entries
        if not name.endswith(("/", ".class"))
        and not re.match(r"(?i)^META-INF/.+\.(SF|RSA|DSA)$", name)
    ]
    with zipfile.ZipFile(args.input) as input_archive, zipfile.ZipFile(args.jar) as final_archive:
        final_resource_hashes = {
            hashlib.sha256(final_archive.read(name)).digest()
            for name in final_entries if not name.endswith(("/", ".class"))
        }
        missing_resource = next((
            name for name in resources
            if name not in final_entries
            and hashlib.sha256(input_archive.read(name)).digest() not in final_resource_hashes
        ), None)
    if missing_resource:
        fail("a non-class resource and its content were lost: " + missing_resource)

    manifest = text_entry(args.jar, "META-INF/MANIFEST.MF")
    if not re.search(r"(?m)^Agent-Class:\s*gq\.yozakura\.YozakuraAttachPoint\s*$", manifest):
        fail("the Agent-Class manifest contract is missing or renamed")
    if not re.search(r"(?m)^FMLAT:\s*yozakura_at\.cfg\s*$", manifest):
        fail("the Forge access-transformer manifest contract is missing")
    contracts = set(required[3:])
    candidates = [
        name for name in input_entries
        if re.match(r"^gq/yozakura/.+\.class$", name) and name not in contracts
    ]
    renamed_count = sum(name not in zkm_entries for name in candidates)
    if renamed_count < 10:
        fail(f"ZKM rename evidence is too weak: only {renamed_count} original names disappeared")

    if any(name.startswith("myj2c/") for name in final_entries):
        fail("a MyJ2C payload remains in the JNIC release")
    if not any(name.startswith("dev/jnic/") for name in final_entries):
        fail("the JNIC runtime classes are missing")
    if any(name.startswith("dev/jnic/") for name in zkm_entries):
        fail("the ZKM intermediate unexpectedly contains a JNIC payload")
    if not any(re.match(r"^dev/jnic/lib/.+\.dat$", name) for name in final_entries):
        fail("the JNIC Windows native payload is missing")
    if any(re.match(r"(?i)^META-INF/.+\.(SF|RSA|DSA)$", name) for name in final_entries):
        fail("stale JAR signature files remain")
    zkm_gate = javap(javap_executable, args.zkm, "-p", "gq.yozakura.k.B")
    if re.search(r"\bnative\b", zkm_gate):
        fail("B was native before JNIC")
    final_gate = javap(javap_executable, args.jar, "-p", "gq.yozakura.k.B")
    native_count = sum(1 for line in final_gate.splitlines() if re.search(r"\bnative\b", line))
    if native_count < 1:
        fail(f"B exposes only {native_count} native methods")
    for method in ("verifyOrThrow",):
        if not re.search(r"native .+\b" + method + r"\(", final_gate):
            fail("JNIC did not translate the authentication method: " + method)
    for method in ("permitModuleActivation", "permitTickDispatch", "permitRenderDispatch",
                   "permitInputDispatch", "permitPacketDispatch", "permitEventDispatch",
                   "permitMovementDispatch"):
        if re.search(r"native .+\b" + method + r"\(", final_gate):
            fail("JNIC transformed the hot runtime permit wrapper: " + method)

    bridge = javap(javap_executable, args.jar, "-p", "gq.yozakura.k.A")
    for method in ("q0", "q1", "login0", "redeemLicense0", "logout0", "username0", "role0", "expiry0"):
        if not re.search(r"\b" + method + r"\(", bridge):
            fail("JNI registration contract was renamed or removed: " + method)
    for method in ("login", "redeemLicense"):
        if not re.search(r"native .+\b" + method + r"\(", bridge):
            fail("JNIC did not translate the login boundary method: " + method)
    for method in ("permitStartup", "permitModuleActivation", "permitTickDispatch",
                   "permitRenderDispatch", "permitInputDispatch", "permitPacketDispatch",
                   "permitEventDispatch", "permitMovementDispatch"):
        if re.search(r"native .+\b" + method + r"\(", bridge):
            fail("JNIC transformed the hot native bridge permit wrapper: " + method)

    maximum_major = 0
    with zipfile.ZipFile(args.jar) as archive:
        for item in archive.infolist():
            if not item.filename.endswith(".class"):
                continue
            header = archive.read(item)[:8]
            if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe":
                fail("invalid class header: " + item.filename)
            major = int.from_bytes(header[6:8], "big")
            maximum_major = max(maximum_major, major)
            if major > 52:
                fail(f"class {item.filename} uses major {major}, newer than Java 8")

    lines = [
        "Yozakura obfuscation verification: PASS",
        "Input SHA-256:  " + sha256(args.input),
        "Output SHA-256: " + sha256(args.jar),
        f"JNIC native guard methods in B: {native_count}",
        f"Yozakura classes renamed by ZKM: {renamed_count}",
        f"JAR entries: {len(final_entries)}",
        f"Non-class resources retained: {len(resources)}",
        f"Maximum class major version: {maximum_major}",
    ]
    print("\n".join(lines))
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
