#!/usr/bin/env python3
"""Build the standalone, API-24 Exo ASS prototype from the pinned source graph.

Normal App builds never invoke this. No MPV/FFmpeg objects or prefixes are used.
--source-cache may supply clean Git objects/downloads; each identity is verified.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]


def run(args, **kwargs):
    print("+", " ".join(map(str, args)), flush=True)
    subprocess.run(list(map(str, args)), check=True, **kwargs)


def extract(archive, target):
    # Source archives are pinned, but reject escaping paths and links as well.
    target.mkdir(parents=True, exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
        for member in tar.getmembers():
            path = Path(member.name)
            if path.is_absolute() or ".." in path.parts:
                raise ValueError("unsafe source archive path")
            if member.issym() or member.islnk():
                link = Path(member.linkname)
                if link.is_absolute() or ".." in link.parts:
                    raise ValueError("unsafe source archive link")
        tar.extractall(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", type=Path, default=ROOT / "build/exo-ass-native")
    parser.add_argument("--source-cache", type=Path)
    parser.add_argument("--download-cache", type=Path)
    parser.add_argument("--jobs", type=int, default=min(os.cpu_count() or 4, 8))
    parser.add_argument("--install", action="store_true")
    parser.add_argument("--dependencies-only", action="store_true")
    parser.add_argument("--jni-only", action="store_true",
                        help="Reuse this independent build's verified source stamps and static archives")
    args = parser.parse_args()
    lock = json.loads((ROOT / "third_party/exo-ass-lock.json").read_text())
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        for line in (ROOT / "local.properties").read_text().splitlines():
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1]
    if not sdk:
        raise ValueError("Set ANDROID_HOME to the installed Android SDK")
    ndk = Path(sdk) / "ndk" / lock["android"]["ndk"]
    if lock["android"]["ndk"] not in (ndk / "source.properties").read_text():
        raise ValueError("NDK identity mismatch")
    host = "darwin-x86_64" if platform.system() == "Darwin" else "linux-x86_64"
    toolchain = ndk / "toolchains/llvm/prebuilt" / host
    prefix = work / "arm64-v8a/prefix"
    prefix.mkdir(parents=True, exist_ok=True)
    cross = work / "android-arm64.ini"
    cross.write_text("\n".join([
        "[binaries]",
        "c = '" + str(toolchain / "bin/aarch64-linux-android24-clang") + "'",
        "cpp = '" + str(toolchain / "bin/aarch64-linux-android24-clang++") + "'",
        "ar = '" + str(toolchain / "bin/llvm-ar") + "'",
        "strip = '" + str(toolchain / "bin/llvm-strip") + "'",
        "pkg-config = 'pkg-config'",
        "[host_machine]", "system = 'android'", "cpu_family = 'aarch64'",
        "cpu = 'aarch64'", "endian = 'little'",
        "[properties]", "needs_exe_wrapper = true",
        "pkg_config_libdir = ['" + str(prefix / "lib/pkgconfig") + "']",
        "[built-in options]", "default_library = 'static'", "b_staticpic = true",
        "buildtype = 'release'", "wrap_mode = 'nodownload'",
        "c_args = ['-fvisibility=hidden', '-ffunction-sections', '-fdata-sections']",
        "cpp_args = ['-fvisibility=hidden', '-ffunction-sections', '-fdata-sections']",
        "",
    ]))
    env = os.environ.copy()
    env.pop("CC", None)
    env.pop("CXX", None)
    env["PKG_CONFIG_LIBDIR"] = str(prefix / "lib/pkgconfig")
    env["PKG_CONFIG_PATH"] = ""
    sources = {}
    for name, source in lock["sources"].items():
        dest = work / "sources" / name
        stamp = work / "sources" / (name + ".identity")
        identity = source.get("commit") or source["sha256"]
        if not stamp.exists() or stamp.read_text() != identity:
            if dest.exists():
                raise ValueError("Refusing to overwrite an unverified source directory: " + str(dest))
            if "commit" in source:
                cache = args.source_cache / name if args.source_cache else None
                if cache and cache.is_dir():
                    actual = subprocess.check_output([
                        "git", "-C", str(cache), "rev-parse", source["commit"] + "^{commit}"
                    ], text=True).strip()
                    if actual != source["commit"]:
                        raise ValueError("Source cache identity mismatch")
                else:
                    cache = work / "git" / name
                    if not cache.exists():
                        run(["git", "clone", "--no-checkout", source["repo"], cache])
                    run(["git", "-C", cache, "cat-file", "-e", source["commit"] + "^{commit}"])
                archive = subprocess.check_output([
                    "git", "-C", str(cache), "archive", "--format=tar", source["commit"]
                ])
                extract(archive, dest)
            else:
                archive_path = args.download_cache / source["filename"] if args.download_cache else None
                if not archive_path or not archive_path.exists():
                    archive_path = work / source["filename"]
                    if not archive_path.exists():
                        run(["curl", "-L", "--fail", "--retry", "2", source["url"], "-o", archive_path])
                archive = archive_path.read_bytes()
                if hashlib.sha256(archive).hexdigest() != source["sha256"]:
                    raise ValueError("Source archive checksum mismatch: " + name)
                unpacked = work / "sources" / (name + "-unpack")
                extract(archive, unpacked)
                roots = list(unpacked.iterdir())
                if len(roots) != 1 or not roots[0].is_dir():
                    raise ValueError("Unexpected source archive layout")
                roots[0].rename(dest)
            stamp.write_text(identity)
        sources[name] = dest
        if args.jni_only:
            if not (prefix / "lib" / ("lib" + {"freetype2": "freetype"}.get(name, name).removeprefix("lib") + ".a")).is_file():
                raise ValueError("Missing independently built static archive: " + name)
            continue
        if name == "libass":
            # The exported source has no .git. Meson's vcs_tag would otherwise describe
            # the enclosing WebHTV worktree, including its unrelated recovery tag.
            meson_file = dest / "meson.build"
            content = meson_file.read_text()
            original_stamp = "conf.set('CONFIG_SOURCEVERSION', '\"meson, commit: @VCS_TAG@\"')"
            locked_stamp = "conf.set('CONFIG_SOURCEVERSION', '\"meson, locked commit: " + identity + "\"')"
            if original_stamp in content:
                meson_file.write_text(content.replace(original_stamp, locked_stamp, 1))
            elif locked_stamp not in content:
                raise ValueError("Unexpected libass source-version configuration")
        build = work / "arm64-v8a" / name
        opts = source["meson_options"]
        command = ["meson", "setup", build, dest, "--cross-file", cross,
                   "--prefix", prefix, "--libdir", "lib"] + opts
        if (build / "build.ninja").exists():
            command.append("--reconfigure")
        run(command, env=env)
        run(["ninja", "-C", build, "-j", args.jobs], env=env)
        run(["ninja", "-C", build, "install"], env=env)
    if args.dependencies_only:
        return
    native = ROOT / "third_party/exo-ass-native"
    build = work / "arm64-v8a/jni"
    run(["cmake", "-S", native, "-B", build, "-G", "Ninja",
         "-DCMAKE_TOOLCHAIN_FILE=" + str(ndk / "build/cmake/android.toolchain.cmake"),
         "-DANDROID_ABI=arm64-v8a", "-DANDROID_PLATFORM=android-24",
         "-DANDROID_STL=c++_static", "-DCMAKE_BUILD_TYPE=Release",
         "-DASS_PREFIX=" + str(prefix)])
    run(["cmake", "--build", build, "-j", args.jobs])
    lib = build / "libexo_ass.so"
    run([toolchain / "bin/llvm-strip", "--strip-unneeded", lib])
    digest = hashlib.sha256(lib.read_bytes()).hexdigest()
    print("arm64-v8a libexo_ass.so SHA-256", digest)
    if args.install:
        output = native / "prebuilt/arm64-v8a"
        output.mkdir(parents=True, exist_ok=True)
        shutil.copy2(lib, output / lib.name)
        for name, source in lock["sources"].items():
            for license_path in source["licenses"]:
                target = native / "licenses" / name / license_path
                target.parent.mkdir(parents=True, exist_ok=True)
                # Preserve notice text while making generated copies pass repository whitespace
                # checks. Hash the installed form below so source/build provenance stays coherent.
                notice = (sources[name] / license_path).read_bytes()
                notice = b"\n".join(line.rstrip(b" \t\r") for line in notice.split(b"\n"))
                target.write_bytes(notice.rstrip(b"\n") + b"\n")
        manifest = native / "MANIFEST.sha256"
        manifest.write_text(digest + "  prebuilt/arm64-v8a/libexo_ass.so\n")
        def sha(path):
            return hashlib.sha256(path.read_bytes()).hexdigest()
        inputs = [ROOT / "third_party/exo-ass-lock.json", Path(__file__).resolve(),
                  native / "CMakeLists.txt", native / "exo_ass.cpp", native / "mask_copy.h"]
        provenance = {
            "schema": 1,
            "artifact": {"path": "prebuilt/arm64-v8a/libexo_ass.so", "sha256": digest,
                         "bytes": lib.stat().st_size},
            "android": lock["android"],
            "inputs": {str(path.relative_to(ROOT)): sha(path) for path in inputs},
            "sources": {name: source.get("commit") or source["sha256"]
                        for name, source in lock["sources"].items()},
            "static_archives": {path.name: sha(path) for path in sorted((prefix / "lib").glob("*.a"))},
            "licenses": {str(path.relative_to(native)): sha(path)
                         for path in sorted((native / "licenses").rglob("*")) if path.is_file()},
            "compiler": subprocess.check_output([str(toolchain / "bin/clang"), "--version"], text=True).strip(),
            "tools": {name: subprocess.check_output([name, "--version"], text=True).splitlines()[0]
                      for name in ["cmake", "meson", "ninja"]},
            "rebuild": "python3 scripts/build_exo_ass_native.py --jobs 6 --install",
            "cache_note": "Source caches supply fixed Git objects only; all static archives belong to this independent build.",
        }
        (native / "build-provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")


if __name__ == "__main__":
    main()
