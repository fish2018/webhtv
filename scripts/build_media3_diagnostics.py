#!/usr/bin/env python3
"""Compile AV-DIAG hooks against the committed patched AARs; preserve unrelated bytes."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = "180811f16073271ba1cb6a4f2f889008966facd3"
VERSION = "1.11.0-alpha01-fongmi"
PATCH = "third_party/patches/media3-playback-diagnostics.patch"


def digest(data, algorithm="sha256"):
    return hashlib.new(algorithm, data).hexdigest()


def baseline(path):
    return subprocess.check_output(["git", "show", BASE + ":" + str(path)], cwd=ROOT)


def repack(data, updates):
    output = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as source, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as target:
        for info in source.infolist():
            target.writestr(info, updates.pop(info.filename, source.read(info.filename)))
        for name, content in sorted(updates.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            target.writestr(info, content)
    return output.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--install", action="store_true")
    parser.add_argument("--work-dir", type=Path, default=ROOT / "build/media3-diagnostics")
    args = parser.parse_args()
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    source_root = work / "source"
    sources = re.findall(r"^\+\+\+ b/(libraries/(?:common|exoplayer)/src/main/java/[^\n]+\.java)$", (ROOT / PATCH).read_text(), re.M)
    if not sources:
        raise ValueError("No declared hook sources")
    original = {}
    classpath = []
    for module in ["common", "container", "datasource", "database", "decoder", "extractor", "exoplayer"]:
        name = "media3-" + module + "-" + VERSION
        relative = Path("third_party/maven/androidx/media3/media3-" + module) / VERSION
        aar = baseline(relative / (name + ".aar"))
        with zipfile.ZipFile(io.BytesIO(aar)) as archive:
            jar = work / (module + ".jar")
            jar.write_bytes(archive.read("classes.jar"))
            classpath.append(jar)
        if module not in ["common", "exoplayer"]:
            continue
        src = baseline(relative / (name + "-sources.jar"))
        original[module] = (relative, name, aar, src)
        with zipfile.ZipFile(io.BytesIO(src)) as archive:
            for path in sources:
                prefix = "libraries/" + module + "/src/main/java/"
                if not path.startswith(prefix):
                    continue
                target = source_root / path
                target.parent.mkdir(parents=True, exist_ok=True)
                inner = path[len(prefix):]
                if inner in archive.namelist():
                    target.write_bytes(archive.read(inner))
                elif target.exists():
                    target.unlink()  # Only the task-owned generated new source, for repeatable builds.
    subprocess.run(["patch", "--batch", "-p1", "-i", str(ROOT / PATCH)], cwd=source_root, check=True)
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    for name in ["guava-33.6.0-android.jar", "checker-qual-3.43.0.jar", "annotation-jvm-1.9.1.jar",
                 "error_prone_annotations-2.48.0.jar", "kotlin-stdlib-2.2.10.jar", "j2objc-annotations-3.0.0.jar"]:
        matches = list(cache.glob("**/" + name))
        if len(matches) != 1:
            raise ValueError("Resolve exact compiler dependency: " + name)
        classpath.append(matches[0])
    sdk = os.environ.get("ANDROID_HOME", "/Users/macbookpro/Downloads/bizhi/android-sdk")
    classpath.append(Path(sdk) / "platforms/android-36/android.jar")
    javac = Path(os.environ["JAVA_HOME"]) / "bin/javac"
    classes = work / "classes"
    classes.mkdir(exist_ok=True)
    compiler_sources = []
    for path in sources:
        content = (source_root / path).read_text()
        # This package-private @Retention(SOURCE) annotation is absent from the shipped AAR.
        # Strip it in the javac-only copy; preserve the canonical source JAR and runtime code.
        if path.endswith("/ExoPlayerImplInternal.java"):
            if content.count("@MediaPeriodQueue.UpdatePeriodQueueResult") != 4:
                raise ValueError("Unexpected source-only annotation count")
            content = content.replace("@MediaPeriodQueue.UpdatePeriodQueueResult", "")
        compiler_source = work / "compiler" / path
        compiler_source.parent.mkdir(parents=True, exist_ok=True)
        compiler_source.write_text(content)
        compiler_sources.append(str(compiler_source))
    subprocess.run([str(javac), "-J-Duser.language=en", "-encoding", "UTF-8", "-g", "--release", "8", "-proc:none", "-Xlint:-classfile,-options",
                    "-classpath", os.pathsep.join(map(str, classpath)), "-d", str(classes)] + compiler_sources, check=True)
    all_updates = {str(p.relative_to(classes)): p.read_bytes() for p in classes.rglob("*.class")}
    prefixes = [p.split("/src/main/java/", 1)[1][:-5] for p in sources]
    if any(not any(name == prefix + ".class" or name.startswith(prefix + "$") for prefix in prefixes) for name in all_updates):
        raise ValueError("Compiler escaped declared class boundary")
    reports = {}
    lock_path = ROOT / "third_party/media-lock.json"
    lock = json.loads(lock_path.read_text())
    for module, (relative, name, old_aar, old_sources) in original.items():
        updates = {n: v for n, v in all_updates.items() if n.startswith("androidx/media3/" + module + "/")}
        with zipfile.ZipFile(io.BytesIO(old_aar)) as archive:
            old_classes = archive.read("classes.jar")
        new_classes = repack(old_classes, updates.copy())
        with zipfile.ZipFile(io.BytesIO(old_classes)) as old, zipfile.ZipFile(io.BytesIO(new_classes)) as new:
            preserved = 0
            for entry in old.namelist():
                if entry not in updates:
                    if old.read(entry) != new.read(entry):
                        raise ValueError("Unrelated class changed: " + entry)
                    preserved += 1
        aar = repack(old_aar, {"classes.jar": new_classes})
        changed_sources = {p.split("/src/main/java/", 1)[1]: (source_root / p).read_bytes() for p in sources if p.startswith("libraries/" + module + "/")}
        source_jar = repack(old_sources, changed_sources)
        outputs = {name + ".aar": aar, name + "-sources.jar": source_jar}
        metadata = json.loads(baseline(relative / (name + ".module")))
        for variant in metadata["variants"]:
            for entry in variant.get("files", []):
                if entry["name"] in outputs:
                    data = outputs[entry["name"]]
                    entry["size"] = len(data)
                    for algorithm in ["md5", "sha1", "sha256", "sha512"]:
                        entry[algorithm] = digest(data, algorithm)
        outputs[name + ".module"] = (json.dumps(metadata, indent=2) + "\n").encode()
        for file, data in list(outputs.items()):
            for algorithm in ["md5", "sha1", "sha256", "sha512"]:
                outputs[file + "." + algorithm] = digest(data, algorithm).encode()
        destination = ROOT / relative if args.install else work / "publication" / module
        destination.mkdir(parents=True, exist_ok=True)
        for file, data in outputs.items():
            (destination / file).write_bytes(data)
        coordinate = "androidx.media3:media3-" + module + ":" + VERSION
        overrides = lock["fongmi_media"]["artifact_overrides"]
        override = next((item for item in overrides if item["coordinate"] == coordinate), None)
        if override is None:
            override = {"coordinate": coordinate}; overrides.append(override)
        override.update(aar_sha256=digest(aar), sources_sha256=digest(source_jar),
                        reason="Committed local baseline including ASS; AV-DIAG-01 optional owner hooks, all unrelated class bytes preserved")
        reports[module] = {"baseline_aar": digest(old_aar), "aar_sha256": digest(aar), "sources_sha256": digest(source_jar),
                           "preserved_entries": preserved, "compiled_classes": sorted(updates)}
    if args.install:
        patches = lock["fongmi_media"]["patches"]
        patches[:] = [item for item in patches if item["path"] != PATCH]
        patches.append({"path": PATCH, "sha256": digest((ROOT / PATCH).read_bytes())})
        lock_path.write_text(json.dumps(lock, indent=2) + "\n")
    print(json.dumps({"baseline": BASE, "modules": reports, "javac": str(javac),
                      "compile_inputs": {str(p): digest(p.read_bytes()) for p in classpath}}, indent=2))


if __name__ == "__main__":
    main()
