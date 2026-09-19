#!/usr/bin/env python3
"""Rebuild only TextRenderer classes, preserving every unrelated shipped class.

The input AAR/source JAR is the committed, already patched WebHTV artifact. The
same source patch is also listed in build_media_deps.sh for full source builds.
Use --install to update the coupled AAR, sources, metadata, checksums and lock.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = "845ce82c64b16c94a1db7a61ed6bd38d2d1d35ac"
VERSION = "1.11.0-alpha01-fongmi"
NAME = "media3-exoplayer-" + VERSION
REL = Path("third_party/maven/androidx/media3/media3-exoplayer") / VERSION
JAVA = "androidx/media3/exoplayer/text/TextRenderer.java"
PATCH = "third_party/patches/media3-exo-ass-observer.patch"


def baseline(path):
    return subprocess.check_output(["git", "show", BASE + ":" + str(path)], cwd=ROOT)


def digest(data, algorithm="sha256"):
    return hashlib.new(algorithm, data).hexdigest()


def repack(data, updates):
    out = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as src, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            dst.writestr(info, updates.pop(info.filename, src.read(info.filename)))
        for name, value in sorted(updates.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            dst.writestr(info, value)
    return out.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--install", action="store_true")
    parser.add_argument("--work-dir", type=Path, default=ROOT / "build/exo-ass-media3")
    parser.add_argument("--gradle-cache", type=Path, default=Path.home() / ".gradle/caches/modules-2/files-2.1")
    args = parser.parse_args()
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    original_aar = baseline(REL / (NAME + ".aar"))
    original_sources = baseline(REL / (NAME + "-sources.jar"))
    if digest(original_aar) != "cfea29681799509923174cac7ce31b7e944e542db9df58fd56b7a86499a5c07e":
        raise ValueError("Unexpected input AAR")
    if digest(original_sources) != "83f4f83b4f44e621d52002c161f63fbcb77be6856af4b1e4a4cb0982b04549e1":
        raise ValueError("Unexpected input sources")
    source_root = work / "source"
    target = source_root / "libraries/exoplayer/src/main/java" / JAVA
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(original_sources)) as archive:
        target.write_bytes(archive.read(JAVA))
    # patch operates on this extracted tree even inside the parent Git worktree.
    subprocess.run(["patch", "-p1", "-i", str(ROOT / PATCH)], cwd=source_root, check=True)
    if "public interface Observer" not in target.read_text():
        raise ValueError("Observer patch was not applied to the compiler input")
    classpath = []
    for module in ["common", "container", "datasource", "database", "decoder", "extractor", "exoplayer"]:
        artifact = "media3-" + module
        aar = (ROOT / "third_party/maven/androidx/media3" / artifact / VERSION / (artifact + "-" + VERSION + ".aar")).read_bytes()
        if module == "exoplayer":
            aar = original_aar
        with zipfile.ZipFile(io.BytesIO(aar)) as archive:
            jar = work / (artifact + ".jar")
            jar.write_bytes(archive.read("classes.jar"))
            classpath.append(jar)
    compile_jars = ["guava-33.6.0-android.jar", "checker-qual-3.43.0.jar", "annotation-jvm-1.9.1.jar"]
    for name in compile_jars:
        matches = list(args.gradle_cache.glob("**/" + name))
        if len(matches) != 1:
            raise ValueError("Resolve compile dependency first: " + name)
        classpath.append(matches[0])
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        for line in (ROOT / "local.properties").read_text().splitlines():
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1]
    classpath.append(Path(sdk) / "platforms/android-36/android.jar")
    javac = Path(os.environ["JAVA_HOME"]) / "bin/javac" if "JAVA_HOME" in os.environ else Path("javac")
    classes = work / "classes"
    classes.mkdir(exist_ok=True)
    subprocess.run([str(javac), "-J-Duser.language=en", "-encoding", "UTF-8", "-g", "--release", "8", "-proc:none", "-Xlint:-classfile,-options",
                    "-classpath", os.pathsep.join(map(str, classpath)), "-d", str(classes), str(target)], check=True)
    updates = {str(p.relative_to(classes)): p.read_bytes() for p in classes.rglob("*.class")}
    if any(not n.startswith("androidx/media3/exoplayer/text/TextRenderer") for n in updates):
        raise ValueError("Compilation escaped the declared class boundary")
    if not {JAVA[:-5] + "$Observer.class", JAVA[:-5] + "$Stream.class"}.issubset(updates):
        raise ValueError("Observer API classes missing from compiler output")
    with zipfile.ZipFile(io.BytesIO(original_aar)) as archive:
        old_classes = archive.read("classes.jar")
    new_classes = repack(old_classes, updates.copy())
    with zipfile.ZipFile(io.BytesIO(old_classes)) as old, zipfile.ZipFile(io.BytesIO(new_classes)) as new:
        preserved = 0
        for name in old.namelist():
            if name not in updates:
                if old.read(name) != new.read(name):
                    raise ValueError("Unrelated class changed: " + name)
                preserved += 1
    aar = repack(original_aar, {"classes.jar": new_classes})
    sources = repack(original_sources, {JAVA: target.read_bytes()})
    outputs = {NAME + ".aar": aar, NAME + "-sources.jar": sources}
    module = json.loads(baseline(REL / (NAME + ".module")))
    for variant in module["variants"]:
        for item in variant.get("files", []):
            if item["name"] in outputs:
                data = outputs[item["name"]]
                item["size"] = len(data)
                for algorithm in ["md5", "sha1", "sha256", "sha512"]:
                    item[algorithm] = digest(data, algorithm)
    outputs[NAME + ".module"] = (json.dumps(module, indent=2) + "\n").encode()
    for name, data in list(outputs.items()):
        for algorithm in ["md5", "sha1", "sha256", "sha512"]:
            outputs[name + "." + algorithm] = digest(data, algorithm).encode()
    dest = ROOT / REL if args.install else work / "publication"
    dest.mkdir(parents=True, exist_ok=True)
    for name, data in outputs.items():
        (dest / name).write_bytes(data)
    if args.install:
        path = ROOT / "third_party/media-lock.json"
        lock = json.loads(path.read_text())
        patches = lock["fongmi_media"]["patches"]
        patches[:] = [p for p in patches if p["path"] != PATCH]
        patches.append({"path": PATCH, "sha256": digest((ROOT / PATCH).read_bytes())})
        for artifact in lock["fongmi_media"]["artifact_overrides"]:
            if artifact["coordinate"] == "androidx.media3:media3-exoplayer:" + VERSION:
                artifact["aar_sha256"] = digest(aar)
                artifact["sources_sha256"] = digest(sources)
                artifact["reason"] = "E3-1a/E4-1/E7-2/E-SP3-B baseline; E4-LIBASS optional TextRenderer observer (all unrelated class bytes preserved)"
        path.write_text(json.dumps(lock, indent=2) + "\n")
    print(json.dumps({"aar_sha256": digest(aar), "sources_sha256": digest(sources),
                      "preserved_class_entries": preserved, "compiled_classes": sorted(updates),
                      "compile_inputs": {str(p): digest(p.read_bytes()) for p in classpath},
                      "javac": str(javac)}, indent=2))


if __name__ == "__main__":
    main()
