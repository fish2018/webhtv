#!/usr/bin/env bash
# HPM is built separately for each player/ABI. No runtime feature switch.
set -euo pipefail
HPM_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
HPM_ABI=""
HPM_NDK=""
HPM_PREFIX=""
HPM_WORK=""
HPM_JOBS=4
HPM_ASAN=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --abi) HPM_ABI="$2"; shift 2 ;;
    --ndk) HPM_NDK="$2"; shift 2 ;;
    --prefix) HPM_PREFIX="$2"; shift 2 ;;
    --work-dir) HPM_WORK="$2"; shift 2 ;;
    --jobs) HPM_JOBS="$2"; shift 2 ;;
    --asan) HPM_ASAN=1; shift ;;
    *) echo "Unknown HPM argument: $1" >&2; exit 2 ;;
  esac
done
case "$HPM_ABI" in
  host) ;;
  arm64-v8a|armeabi-v7a)
    [[ -f "$HPM_NDK/build/cmake/android.toolchain.cmake" ]] || {
      echo 'HPM requires an installed Android NDK.' >&2; exit 2;
    } ;;
  *) echo 'HPM requires --abi host|arm64-v8a|armeabi-v7a.' >&2; exit 2 ;;
esac
[[ -n "$HPM_PREFIX" && "$HPM_JOBS" =~ ^[1-9][0-9]*$ ]] || exit 2
HPM_WORK="${HPM_WORK:-$HPM_ROOT/build/avs3-native/hpm-$HPM_ABI}"
mkdir -p "$HPM_WORK" "$HPM_PREFIX"
HPM_WORK="$(cd "$HPM_WORK" && pwd -P)"
HPM_PREFIX="$(cd "$HPM_PREFIX" && pwd -P)"

python3 - "$HPM_ROOT" "$HPM_WORK" <<'PY'
from pathlib import Path
import hashlib, json, shutil, subprocess, sys, tarfile
root, work = map(Path, sys.argv[1:])
source = json.loads((root / 'third_party/media-lock.json').read_text())['avs3_video']['high_decoder']
archive = root / source['archive']
if hashlib.sha256(archive.read_bytes()).hexdigest() != source['sha256']:
    raise SystemExit('HPM archive identity mismatch')
simd = source['simd_adapter']
if hashlib.sha256((root / simd['header']).read_bytes()).hexdigest() != simd['sha256']:
    raise SystemExit('sse2neon header identity mismatch')
adapter = root / 'third_party/avs3-hpm/prepare_source.py'
stamp = source['sha256'] + ':' + hashlib.sha256(adapter.read_bytes()).hexdigest()
if not (work / 'source.stamp').exists() or (work / 'source.stamp').read_text() != stamp:
    tree = work / 'hpm-15.0'
    staging = work / 'prepare'
    if staging.exists():
        shutil.rmtree(staging)
    staging.mkdir()
    with tarfile.open(archive) as tar:
        for member in tar.getmembers():
            parts = Path(member.name).parts
            if not parts or parts[0] != 'hpm-15.0' or '..' in parts or member.issym() or member.islnk():
                raise SystemExit('Unexpected HPM archive entry: ' + member.name)
        tar.extractall(staging)
    prepared = staging / 'hpm-15.0'
    subprocess.run([sys.executable, str(adapter), str(prepared)], check=True)
    # Preserve mtimes for unchanged source: a one-file safety correction must
    # not force a complete native rebuild of all reference-code translation units.
    for path in prepared.rglob('*'):
        if path.is_file():
            target = tree / path.relative_to(prepared)
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.exists() or target.read_bytes() != path.read_bytes():
                shutil.copyfile(path, target)
    shutil.rmtree(staging)
    (work / 'source.stamp').write_text(stamp)
PY

hpm_args=(-S "$HPM_ROOT/third_party/avs3-hpm" -B "$HPM_WORK/build"
  -DHPM_SOURCE="$HPM_WORK/hpm-15.0" -DCMAKE_BUILD_TYPE=Release
  -DCMAKE_INSTALL_PREFIX="$HPM_PREFIX")
if [[ "$HPM_ABI" != host ]]; then
  hpm_args+=(-DCMAKE_TOOLCHAIN_FILE="$HPM_NDK/build/cmake/android.toolchain.cmake"
    -DANDROID_ABI="$HPM_ABI" -DANDROID_PLATFORM=android-21 -DANDROID_STL=none)
fi
if [[ "$HPM_ASAN" == 1 ]]; then
  hpm_args+=(-DCMAKE_C_FLAGS=-fsanitize=address -DCMAKE_EXE_LINKER_FLAGS=-fsanitize=address)
fi
cmake "${hpm_args[@]}"
cmake --build "$HPM_WORK/build" --parallel "$HPM_JOBS"
cmake --install "$HPM_WORK/build"
mkdir -p "$HPM_PREFIX/share/licenses/hpm-avs3"
cp "$HPM_ROOT/third_party/avs3-hpm/LICENSE.HPM" "$HPM_PREFIX/share/licenses/hpm-avs3/LICENSE"
python3 - "$HPM_ROOT/third_party/avs3-hpm/vendor/sse2neon.h" "$HPM_PREFIX/share/licenses/hpm-avs3/SSE2NEON" <<'PY'
from pathlib import Path
import sys
text = Path(sys.argv[1]).read_text()
notice = text[text.index('/*'):text.index('*/') + 2]
Path(sys.argv[2]).write_text('DLTcollab/sse2neon@3cf69760cc6fdc45a5d70f0d42a8f079489f9867\n' + notice + '\n')
PY
printf '%s\n' 'zzZ2001a/avs@0c7ac42edfac6d18b92b58a5ef43bca58526ca7a dependencies/hpm-HPM-15.0' \
  > "$HPM_PREFIX/share/licenses/hpm-avs3/SOURCE"
