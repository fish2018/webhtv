#!/usr/bin/env python3
"""Verify shipped AVS3 artifacts; optionally run extraction tests and compare a baseline."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MAVEN = ROOT / "third_party/maven"
FIXTURES = ROOT / "app/src/test/java/androidx/media3/extractor/fixtures"


def require(value, message):
    if not value:
        raise RuntimeError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def archive(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def artifact(group, name, version, suffix):
    return MAVEN / group.replace(".", "/") / name / version / (name + "-" + version + suffix)


def git_bytes(revision, path):
    return subprocess.check_output(["git", "show", revision + ":" + str(path.relative_to(ROOT))], cwd=ROOT)


def preserved(old, new, allowed, label):
    changed = {n for n in old.keys() | new.keys() if old.get(n) != new.get(n)}
    unexpected = changed - allowed
    require(not unexpected, f"Unrelated {label} entries changed: {sorted(unexpected)}")


def checksums(path):
    for item in path.parent.iterdir():
        if item.suffix[1:] in ("md5", "sha1", "sha256", "sha512"):
            source = item.with_suffix("")
            require(source.is_file(), f"Missing checksum source: {source}")
            require(hashlib.new(item.suffix[1:], source.read_bytes()).hexdigest() == item.read_text().strip(),
                    f"Checksum mismatch: {item}")
    metadata = json.loads(path.with_suffix(".module").read_text())
    for variant in metadata["variants"]:
        for entry in variant.get("files", []):
            data = (path.parent / entry["name"]).read_bytes()
            require(len(data) == entry["size"], f"Module size mismatch: {entry['name']}")
            for algo in ("md5", "sha1", "sha256", "sha512"):
                if algo in entry:
                    require(hashlib.new(algo, data).hexdigest() == entry[algo], f"Module hash mismatch: {entry['name']}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jvm", action="store_true")
    parser.add_argument("--baseline", help="Full pre-AVS3 commit for unrelated-byte preservation checks")
    parser.add_argument("--apk", type=Path, help="Check ARM64 APK native entries against the committed candidates")
    args = parser.parse_args()
    media = json.loads((ROOT / "third_party/media-lock.json").read_text())
    mpv = json.loads((ROOT / "third_party/mpv-native-lock.json").read_text())
    decoder = media["avs3_video"]["decoder"]
    for key in ("commit", "sha256"):
        require(decoder[key] == mpv["sources"]["uavs3d"][key], "Exo/MPV uavs3d input mismatch")
    require(decoder["profiles"] == ["0x20", "0x22"] and decoder["bit_depths"] == [8, 10],
            "AVS3 capability declaration needs a new verified contract")
    high = media["avs3_video"]["high_decoder"]
    require(high == mpv["avs3_high_decoder"], "Exo/MPV HPM inputs differ")
    require(high["profiles"] == ["0x32"] and high["bit_depths"] == [10],
            "HPM capability declaration needs a new verified contract")
    require(sha((ROOT / high["archive"]).read_bytes()) == high["sha256"], "HPM archive hash mismatch")
    simd = high["simd_adapter"]
    require(sha((ROOT / simd["header"]).read_bytes()) == simd["sha256"], "SIMD source hash mismatch")
    for patch in media["fongmi_media"]["patches"] + [media["avs3_video"]["ffmpeg_patch"]]:
        require(sha((ROOT / patch["path"]).read_bytes()) == patch["sha256"], f"Patch hash mismatch: {patch['path']}")
    fixture_manifest = json.loads((FIXTURES / "manifest.json").read_text())
    for depth, stream in fixture_manifest["streams"].items():
        require(sha((FIXTURES / f"baseline-{depth}.avs3").read_bytes()) == stream["sha256"], "Fixture hash mismatch")

    version = media["nextlib"]["version"]
    require(f'nextlib = "{version}"' in (ROOT / "gradle/libs.versions.toml").read_text(), "Gradle nextlib version mismatch")
    next_path = artifact("io.github.anilbeesetti", "nextlib-media3ext", version, ".aar")
    next_entries = archive(next_path.read_bytes())
    checksums(next_path)
    require(sha(next_path.read_bytes()) == media["nextlib"]["avs3_artifact"]["aar_sha256"], "nextlib AAR hash mismatch")
    require(sha(next_path.with_name(next_path.stem + "-sources.jar").read_bytes()) ==
            media["nextlib"]["avs3_artifact"]["sources_sha256"], "nextlib sources hash mismatch")
    license_data = next_entries.get("assets/licenses/uavs3d.txt", b"")
    require(b"Redistribution and use in source and binary forms" in license_data, "Missing shipped uavs3d license")
    require(next_entries.get("assets/licenses/hpm-avs3.txt") ==
            (ROOT / "third_party/avs3-hpm/LICENSE.HPM").read_bytes(), "Missing original HPM license")
    require(b"Permission is hereby granted" in next_entries.get("assets/licenses/sse2neon.txt", b""),
            "Missing SSE2NEON license")
    next_classes = archive(next_entries["classes.jar"])
    require(b"libuavs3d" in next_classes["io/github/anilbeesetti/nextlib/media3ext/ffdecoder/FfmpegLibrary.class"],
            "Shipped nextlib has no AVS3 decoder mapping")
    jars = {}
    for item in media["fongmi_media"]["artifact_overrides"]:
        group, name, ver = item["coordinate"].split(":")
        if name not in ("media3-common", "media3-container", "media3-extractor"):
            continue
        path = artifact(group, name, ver, ".aar")
        require(sha(path.read_bytes()) == item["aar_sha256"], f"AAR hash mismatch: {name}")
        require(sha(path.with_name(path.stem + "-sources.jar").read_bytes()) == item["sources_sha256"],
                f"Sources hash mismatch: {name}")
        checksums(path)
        jars[name] = (path, archive(path.read_bytes())["classes.jar"])
    extractor = archive(jars["media3-extractor"][1])
    require("androidx/media3/extractor/Avs3Config.class" in extractor and
            "androidx/media3/extractor/ts/Avs3Reader.class" in extractor, "Missing shipped AVS3 extraction classes")

    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    readelf = shutil.which("llvm-readelf") or shutil.which("readelf")
    if not readelf:
        candidates = sorted(sdk.glob("ndk/*/toolchains/llvm/prebuilt/*/bin/llvm-readelf"))
        require(candidates, "Set ANDROID_HOME or provide llvm-readelf")
        readelf = str(candidates[-1])
    with tempfile.TemporaryDirectory(prefix="webhtv-avs3-") as temporary:
        work = Path(temporary)
        for abi, flavor, machine in (("arm64-v8a", "arm64_v8a", b"AArch64"), ("armeabi-v7a", "armeabi_v7a", b"ARM")):
            exo = next_entries[f"jni/{abi}/libavcodec.so"]
            mpv_path = ROOT / f"app/src/{flavor}/assets/mpv-libs/{abi}/libmvcodec.so"
            for player, name, data in (("Exo", "libavcodec.so", exo), ("MPV", "libmvcodec.so", mpv_path.read_bytes())):
                require(b"libuavs3d" in data and decoder["commit"].encode() in data, f"{player}/{abi} decoder not embedded")
                require(b"Unsupported AVS3 profile" in data, f"{player}/{abi} lacks the raw profile guard")
                require(b"WebHTV HPM 15.0" in data and high["commit"].encode() in data,
                        f"{player}/{abi} has no pinned High 10-bit backend")
                path = work / name
                path.write_bytes(data)
                elf = subprocess.check_output([readelf, "-h", "-l", "-d", "--wide", str(path)])
                require(machine in elf and ("Library soname: [" + name + "]").encode() in elf, f"{player}/{abi} ABI or SONAME mismatch")
                load_alignment = re.findall(rb"^\s+LOAD\s+.*\s+(0x[0-9a-f]+)\s*$", elf, re.M)
                require(load_alignment and all(int(value, 16) >= 16384 for value in load_alignment),
                        f"{player}/{abi} lost Android 16 KB page alignment")
                needed = re.findall(rb"Shared library: \[([^]]+)\]", elf)
                require(not any(b"uavs3d" in n or b"webhtvhpm" in n or b"pthread" in n for n in needed),
                        "AVS3 must be statically linked")
                exports = subprocess.check_output([readelf, "--dyn-syms", "--wide", str(path)])
                require(not re.search(rb"GLOBAL\s+DEFAULT\s+(?!UND)\S+\s+(?:webhtv_hpm|com_mc|dec_cnk)", exports),
                        f"{player}/{abi} leaks internal reference-decoder symbols")
                forbidden = (b"libmv", b"libmw") if player == "Exo" else (b"libav", b"libsw")
                require(not any(n.startswith(forbidden) for n in needed), f"Cross-player dependency: {player}/{abi}")
                print(f"{player}/{abi}: {sha(data)} ({len(data)} bytes)")

        if args.baseline:
            require(re.fullmatch(r"[0-9a-f]{40}", args.baseline), "Use the full baseline commit")
            old_lock = json.loads(git_bytes(args.baseline, ROOT / "third_party/media-lock.json"))
            old_version = old_lock["nextlib"]["version"]
            old_next = archive(git_bytes(args.baseline, artifact("io.github.anilbeesetti", "nextlib-media3ext", old_version, ".aar")))
            allowed_entries = {"jni/arm64-v8a/libavcodec.so", "jni/armeabi-v7a/libavcodec.so",
                               "assets/licenses/hpm-avs3.txt", "assets/licenses/sse2neon.txt"}
            if "high_decoder" not in old_lock.get("avs3_video", {}) and "avs3_artifact" not in old_lock["nextlib"]:
                allowed_entries.update({"classes.jar", "assets/licenses/uavs3d.txt"})
            preserved(old_next, next_entries, allowed_entries, "nextlib")
            old_classes = archive(old_next["classes.jar"])
            allowed = {n for n in old_classes.keys() | next_classes.keys() if re.search(r"/Ffmpeg(?:Library|VideoDecoder|VideoRenderer)(?:\$[^/]*)?\.class$", n)}
            preserved(old_classes, next_classes, allowed, "nextlib class")
            patch = (ROOT / "third_party/patches/media3-exo-avs3.patch").read_text()
            prefixes = re.findall(r"^\+\+\+ b/libraries/[^/]+/src/main/java/(.+)\.java$", patch, re.M)
            for name, (path, jar) in jars.items():
                old_aar = archive(git_bytes(args.baseline, path))
                preserved(old_aar, archive(path.read_bytes()), {"classes.jar"}, name)
                old_classes, new_classes = archive(old_aar["classes.jar"]), archive(jar)
                allowed = {n for n in old_classes.keys() | new_classes.keys() if any(n == p + ".class" or n.startswith(p + "$") for p in prefixes)}
                preserved(old_classes, new_classes, allowed, name + " class")
            for flavor in ("arm64_v8a", "armeabi_v7a"):
                for path in (ROOT / f"app/src/{flavor}/assets/mpv-libs").rglob("*.so"):
                    if path.name != "libmvcodec.so":
                        require(git_bytes(args.baseline, path) == path.read_bytes(), f"Unrelated MPV library changed: {path}")
            print("All unrelated AAR classes/entries and MPV libraries match the baseline.")

        if args.jvm:
            cp = []
            media_version = next(iter(jars.values()))[0].parent.name
            for name in ("common", "container", "datasource", "database", "decoder", "extractor", "exoplayer"):
                path = artifact("androidx.media3", "media3-" + name, media_version, ".aar")
                jar = work / (name + ".jar")
                jar.write_bytes(archive(path.read_bytes())["classes.jar"])
                cp.append(str(jar))
            cache = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))) / "caches/modules-2/files-2.1"
            for group, name in (("com.google.guava", "guava"), ("junit", "junit"), ("org.hamcrest", "hamcrest-core"),
                                ("androidx.annotation", "annotation-jvm"), ("org.checkerframework", "checker-qual"),
                                ("com.google.errorprone", "error_prone_annotations")):
                candidates = sorted((cache / group / name).glob("*/*/*.jar"))
                require(candidates, f"Resolve Gradle dependency first: {group}:{name}")
                cp.append(str(candidates[-1]))
            platforms = sorted(sdk.glob("platforms/android-*/android.jar"))
            require(platforms, "Set ANDROID_HOME to an SDK with android.jar")
            cp.append(str(platforms[-1]))
            classes = work / "classes"
            classes.mkdir()
            java_bin = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None
            javac = str(java_bin / "javac") if java_bin else "javac"
            java = str(java_bin / "java") if java_bin else "java"
            subprocess.run([javac, "-encoding", "UTF-8", "-proc:none", "-classpath", os.pathsep.join(cp), "-d", str(classes),
                            str(ROOT / "app/src/test/java/androidx/media3/extractor/Avs3ExtractionTest.java")], check=True)
            subprocess.run([java, "-Davs3.fixtures=" + str(FIXTURES), "-classpath", os.pathsep.join([str(classes)] + cp),
                            "org.junit.runner.JUnitCore", "androidx.media3.extractor.Avs3ExtractionTest"], check=True, cwd=ROOT)
        if args.apk:
            apk = archive(args.apk.read_bytes())
            for name, data in next_entries.items():
                if name.startswith("jni/arm64-v8a/"):
                    require(apk.get(name.replace("jni/", "lib/", 1)) == data, f"APK nextlib mismatch: {name}")
            for path in (ROOT / "app/src/arm64_v8a/assets/mpv-libs/arm64-v8a").glob("*.so"):
                require(apk.get("assets/mpv-libs/arm64-v8a/" + path.name) == path.read_bytes(), f"APK MPV mismatch: {path.name}")
            require(apk.get("assets/licenses/uavs3d.txt") == license_data, "APK license missing")
            for name in ("hpm-avs3.txt", "sse2neon.txt"):
                entry = "assets/licenses/" + name
                require(apk.get(entry) == next_entries[entry], "APK license mismatch: " + name)
            print("APK native entries and license match the verified candidates.")
    print("AVS3 artifact contract passed: baseline 0x20/0x22 and HPM 0x32; no 0x30 or real-time claim.")


if __name__ == "__main__":
    main()
