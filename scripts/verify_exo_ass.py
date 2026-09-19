#!/usr/bin/env python3
"""Verify the scoped E4-LIBASS publication; optionally check APKs and sanitize mask copies."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import re
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / "third_party/exo-ass-native"
BASE = "845ce82c64b16c94a1db7a61ed6bd38d2d1d35ac"
VERSION = "1.11.0-alpha01-fongmi"
MAVEN = Path("third_party/maven/androidx/media3/media3-exoplayer") / VERSION
NAME = "media3-exoplayer-" + VERSION
PREFIX = "androidx/media3/exoplayer/text/TextRenderer"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data, algorithm="sha256"):
    return hashlib.new(algorithm, data).hexdigest()


def archive(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        return {name: z.read(name) for name in z.namelist()}


def original(path):
    return subprocess.check_output(["git", "show", BASE + ":" + str(path)], cwd=ROOT)


def sdk_path():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        sdk = next(line.split("=", 1)[1] for line in (ROOT / "local.properties").read_text().splitlines()
                   if line.startswith("sdk.dir="))
    return Path(sdk)


def verify_native(work):
    lock = json.loads((ROOT / "third_party/exo-ass-lock.json").read_text())
    provenance = json.loads((NATIVE / "build-provenance.json").read_text())
    require(lock["android"] == provenance["android"], "Android provenance mismatch")
    require(lock["android"]["api"] == 24 and lock["android"]["abis"] == ["arm64-v8a"], "Prototype ABI/API drift")
    for name, source in lock["sources"].items():
        identity = source.get("commit") or source["sha256"]
        require(bool(re.fullmatch(r"[0-9a-f]{40}" if "commit" in source else r"[0-9a-f]{64}", identity)),
                "Unpinned source: " + name)
        require(provenance["sources"][name] == identity, "Source provenance mismatch: " + name)
        for notice in source["licenses"]:
            path = "licenses/" + name + "/" + notice
            require(digest((NATIVE / path).read_bytes()) == provenance["licenses"][path], "License mismatch: " + path)
    for name, sha in provenance["inputs"].items():
        require(digest((ROOT / name).read_bytes()) == sha, "Rebuild required after input change: " + name)
    lib = NATIVE / provenance["artifact"]["path"]
    data = lib.read_bytes()
    require(("meson, locked commit: " + lock["sources"]["libass"]["commit"]).encode() in data,
            "libass source-version stamp must match its locked commit")
    sha = digest(data)
    require(sha == provenance["artifact"]["sha256"], "Native provenance mismatch")
    require(len(data) == provenance["artifact"]["bytes"], "Native size mismatch")
    require((NATIVE / "MANIFEST.sha256").read_text().strip() == sha + "  " + provenance["artifact"]["path"],
            "Native manifest mismatch")
    require(data[:6] == b"\x7fELF\x02\x01" and struct.unpack_from("<H", data, 18)[0] == 183, "Expected ELF64 AArch64")
    phoff = struct.unpack_from("<Q", data, 32)[0]
    phsize, phnum = struct.unpack_from("<HH", data, 54)
    alignment = []
    android_api = None
    for i in range(phnum):
        kind, flags, offset, vaddr, _, filesz, memsz, align = struct.unpack_from("<IIQQQQQQ", data, phoff + i * phsize)
        if kind == 1:
            require(align >= 16384 and offset % 16384 == vaddr % 16384, "Invalid ELF LOAD alignment")
            require(flags & 3 != 3, "Writable executable LOAD")
            alignment.append(align)
        if kind == 4:
            end = offset + filesz
            while offset + 12 <= end:
                namesz, descsz, note_type = struct.unpack_from("<III", data, offset)
                offset += 12
                name = data[offset:offset + namesz].rstrip(b"\0")
                offset += (namesz + 3) & ~3
                desc = data[offset:offset + descsz]
                offset += (descsz + 3) & ~3
                if name == b"Android" and note_type == 1:
                    android_api = struct.unpack_from("<I", desc)[0]
    require(alignment and android_api == 24, "Missing API 24 Android note")
    host = "darwin-x86_64" if platform.system() == "Darwin" else "linux-x86_64"
    readelf = sdk_path() / "ndk" / lock["android"]["ndk"] / "toolchains/llvm/prebuilt" / host / "bin/llvm-readelf"
    elf = subprocess.check_output([str(readelf), "-d", "-n", "--dyn-syms", str(lib)], text=True)
    (work / "libexo_ass.elf.txt").write_text(elf)
    needed = re.findall(r"\(NEEDED\).*?\[([^]]+)\]", elf)
    require(set(needed) == {"libandroid.so", "liblog.so", "libEGL.so", "libGLESv2.so", "libm.so", "libdl.so", "libc.so"},
            "Unexpected native linkage: " + repr(needed))
    require("Library soname: [libexo_ass.so]" in elf, "SONAME mismatch")
    exports = []
    for line in elf.splitlines():
        parts = line.split()
        if len(parts) == 8 and parts[0].rstrip(":").isdigit() and parts[4] in ("GLOBAL", "WEAK") and parts[6] != "UND":
            exports.append(parts[7])
    jni = "Java_com_fongmi_android_tv_player_exo_ass_AssNative_"
    require(set(exports) == {jni + x for x in ["create", "load", "loadHeader", "chunk", "render", "setSurface", "destroy", "createTestFonts", "testSurface", "readPixels"]},
            "Unexpected exported symbols")
    return {"sha256": sha, "bytes": len(data), "api": android_api, "load_alignment": alignment,
            "needed": needed, "exports": exports}


def verify_media():
    old = archive(original(MAVEN / (NAME + ".aar")))
    new = archive((ROOT / MAVEN / (NAME + ".aar")).read_bytes())
    require(old.keys() == new.keys(), "Unrelated AAR entry-set change")
    for name in old:
        if name != "classes.jar":
            require(old[name] == new[name], "Unrelated AAR resource changed: " + name)
    classes_old, classes_new = archive(old["classes.jar"]), archive(new["classes.jar"])
    preserved = 0
    for name, value in classes_old.items():
        if not name.startswith(PREFIX):
            require(classes_new.get(name) == value, "Unrelated class changed: " + name)
            preserved += 1
    require(all(name in classes_old or name.startswith(PREFIX) for name in classes_new), "Unexpected new class")
    require(PREFIX + "$Observer.class" in classes_new and PREFIX + "$Stream.class" in classes_new, "Observer API absent")
    old_src = archive(original(MAVEN / (NAME + "-sources.jar")))
    new_src = archive((ROOT / MAVEN / (NAME + "-sources.jar")).read_bytes())
    require(old_src.keys() == new_src.keys(), "Source entry-set change")
    for name in old_src:
        if name != PREFIX + ".java":
            require(old_src[name] == new_src[name], "Unrelated source changed: " + name)
    for suffix in [".aar", "-sources.jar", ".module"]:
        path = ROOT / MAVEN / (NAME + suffix)
        for algorithm in ["md5", "sha1", "sha256", "sha512"]:
            require(path.with_name(path.name + "." + algorithm).read_text().strip() == digest(path.read_bytes(), algorithm),
                    "Maven checksum mismatch: " + path.name)
    module = json.loads((ROOT / MAVEN / (NAME + ".module")).read_text())
    for variant in module["variants"]:
        for file in variant.get("files", []):
            data = (ROOT / MAVEN / file["name"]).read_bytes()
            require(file["size"] == len(data) and all(file[a] == digest(data, a) for a in ["md5", "sha1", "sha256", "sha512"]),
                    "Gradle metadata mismatch")
    lock = json.loads((ROOT / "third_party/media-lock.json").read_text())["fongmi_media"]
    patch = "third_party/patches/media3-exo-ass-observer.patch"
    entry = next(p for p in lock["patches"] if p["path"] == patch)
    require(entry["sha256"] == digest((ROOT / patch).read_bytes()), "Observer patch identity mismatch")
    entry = next(a for a in lock["artifact_overrides"] if a["coordinate"] == "androidx.media3:media3-exoplayer:" + VERSION)
    for key, suffix in [("aar_sha256", ".aar"), ("sources_sha256", "-sources.jar")]:
        require(entry[key] == digest((ROOT / MAVEN / (NAME + suffix)).read_bytes()), "Media lock mismatch")
    return {"preserved_class_entries": preserved, "aar_sha256": entry["aar_sha256"], "sources_sha256": entry["sources_sha256"]}


def verify_apk(path, enabled, native):
    data = path.read_bytes()
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        libs = [n for n in z.namelist() if n.endswith("/libexo_ass.so")]
        require(libs == (["lib/arm64-v8a/libexo_ass.so"] if enabled else []), "APK prototype gate mismatch")
        result = {"sha256": digest(data), "bytes": len(data), "ass_libraries": libs}
        if enabled:
            info = z.getinfo(libs[0])
            require(digest(z.read(info)) == native["sha256"], "APK contains a stale ASS library")
            name_len, extra_len = struct.unpack_from("<HH", data, info.header_offset + 26)
            offset = info.header_offset + 30 + name_len + extra_len
            require(info.compress_type != zipfile.ZIP_STORED or offset % 16384 == 0, "Uncompressed ASS library is not ZIP 16 KiB aligned")
            result.update(library_bytes=info.file_size, library_compressed_bytes=info.compress_size,
                          library_zip_method=info.compress_type, library_data_offset=offset)
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--default-apk", type=Path)
    parser.add_argument("--sanitize", action="store_true")
    parser.add_argument("--work-dir", type=Path, default=ROOT / "build/exo-ass-verification")
    args = parser.parse_args()
    args.work_dir.mkdir(parents=True, exist_ok=True)
    result = {"native": verify_native(args.work_dir), "media3": verify_media()}
    if args.apk:
        result["prototype_apk"] = verify_apk(args.apk, True, result["native"])
    if args.default_apk:
        result["default_apk"] = verify_apk(args.default_apk, False, result["native"])
    if args.sanitize:
        binary = args.work_dir / "mask-copy-sanitized"
        subprocess.run(["clang++", "-std=c++17", "-g", "-O1", "-fsanitize=address,undefined", "-fno-sanitize-recover=all",
                        "-fno-omit-frame-pointer", str(NATIVE / "mask_copy_test.cpp"), "-o", str(binary)], check=True)
        subprocess.run([str(binary)], check=True)
        result["mask_copy_asan_ubsan"] = "PASS: 36 exact-last-row allocations and rejected invalid/oversized dimensions"
    (args.work_dir / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
