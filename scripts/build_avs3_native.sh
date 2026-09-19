#!/usr/bin/env bash
# Shared, pinned uavs3d build. Exo and MPV call this with their own NDK/prefix.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ABI=""
NDK=""
PREFIX=""
WORK=""
JOBS="${AVS3_BUILD_JOBS:-4}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --abi) ABI="$2"; shift 2 ;;
    --ndk) NDK="$2"; shift 2 ;;
    --prefix) PREFIX="$2"; shift 2 ;;
    --work-dir) WORK="$2"; shift 2 ;;
    --jobs) JOBS="$2"; shift 2 ;;
    --help|-h)
      echo 'Usage: bash scripts/build_avs3_native.sh --abi arm64-v8a|armeabi-v7a|host --prefix DIR [--ndk DIR] [--work-dir DIR] [--jobs N]'
      exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done
case "$ABI" in
  host) ;;
  arm64-v8a|armeabi-v7a)
    [[ -f "$NDK/build/cmake/android.toolchain.cmake" ]] || {
      echo 'The Android build requires --ndk with an installed NDK.' >&2; exit 2;
    } ;;
  *) echo 'A supported --abi is required.' >&2; exit 2 ;;
esac
[[ -n "$PREFIX" ]] || { echo '--prefix is required.' >&2; exit 2; }
[[ "$JOBS" =~ ^[1-9][0-9]*$ ]] || { echo 'Invalid --jobs.' >&2; exit 2; }
WORK="${WORK:-$ROOT/build/avs3-native/uavs3d-$ABI}"
mkdir -p "$WORK" "$PREFIX"
WORK="$(cd "$WORK" && pwd -P)"
PREFIX="$(cd "$PREFIX" && pwd -P)"

eval "$(python3 - "$ROOT/third_party/media-lock.json" <<'PY'
import json, shlex, sys
decoder = json.load(open(sys.argv[1]))['avs3_video']['decoder']
for key in ('commit', 'url', 'sha256'):
    print('UAVS3D_' + key.upper() + '=' + shlex.quote(decoder[key]))
PY
)"
CACHE="$ROOT/build/avs3-native/cache"
ARCHIVE="$CACHE/uavs3d-$UAVS3D_COMMIT.tar.gz"
mkdir -p "$CACHE"
if [[ ! -f "$ARCHIVE" ]]; then
  curl --fail --location --retry 2 "$UAVS3D_URL" -o "$ARCHIVE.part"
  mv "$ARCHIVE.part" "$ARCHIVE"
fi
python3 - "$ARCHIVE" "$UAVS3D_SHA256" <<'PY'
import hashlib, pathlib, sys
actual = hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest()
if actual != sys.argv[2]:
    raise SystemExit('uavs3d archive SHA-256 mismatch: ' + actual)
PY

SOURCE="$WORK/uavs3d-$UAVS3D_COMMIT"
if [[ ! -d "$SOURCE" ]]; then
  tar -xzf "$ARCHIVE" -C "$WORK"
fi
# Do not run upstream's version.sh inside the WebHTV worktree: it would pick
# WebHTV's Git revision. Each build owns its source copy and pkg-config file.
python3 - "$SOURCE" "$UAVS3D_COMMIT" <<'PY'
from pathlib import Path
import sys
source, revision = Path(sys.argv[1]), sys.argv[2]
cmake = source / 'source/CMakeLists.txt'
text = cmake.read_text()
text = text.replace('execute_process(COMMAND sh ${CONFIG_DIR}/version.sh ${CONFIG_DIR})',
                    '# Version header is generated from the pinned source revision by WebHTV.')
old = 'set(ARMV7_FLAGS "-mcpu=cortex-a9 -mfpu=neon -mfloat-abi=hard")'
new = '''if(ANDROID)
    set(ARMV7_FLAGS "-mfpu=neon -mfloat-abi=softfp")
  else()
    set(ARMV7_FLAGS "-mcpu=cortex-a9 -mfpu=neon -mfloat-abi=hard")
  endif()'''
if 'if(ANDROID)' not in text:
    if text.count(old) != 1:
        raise SystemExit('Unexpected uavs3d ARMv7 build configuration')
    text = text.replace(old, new)
text = text.replace('if(CMAKE_USE_PTHREADS_INIT)',
                    'if(CMAKE_USE_PTHREADS_INIT AND NOT ANDROID)')
cmake.write_text(text)
(source / 'version.h').write_text(
    '#ifndef __VERSION_H__\n#define __VERSION_H__\n'
    '#define VER_MAJOR 1\n#define VER_MINOR 2\n#define VER_BUILD 0\n'
    '#define VERSION_TYPE "release"\n#define VERSION_STR "1.2.0"\n'
    f'#define VERSION_SHA1 "{revision}"\n#endif\n')
PY

args=(
  -S "$SOURCE" -B "$WORK/build"
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_POSITION_INDEPENDENT_CODE=ON
  -DCOMPILE_10BIT=ON -DBUILD_SHARED_LIBS=OFF
  -DCMAKE_INSTALL_PREFIX="$PREFIX"
)
if [[ "$ABI" != host ]]; then
  args+=(
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake"
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-21
    -DANDROID_STL=c++_shared
  )
fi
cmake "${args[@]}"
cmake --build "$WORK/build" --target uavs3d uavs3dec --parallel "$JOBS"
cmake --install "$WORK/build"
mkdir -p "$PREFIX/share/licenses/uavs3d"
cp "$SOURCE/COPYING" "$PREFIX/share/licenses/uavs3d/COPYING"
printf '%s\n' "$UAVS3D_COMMIT" > "$PREFIX/share/licenses/uavs3d/SOURCE"

# The same registered AVS3 decoder dispatches High 10-bit to HPM. Both static
# backends are mandatory inputs; ordinary debug/release builds cannot omit it.
bash "$ROOT/scripts/build_hpm_native.sh" --abi "$ABI" --ndk "$NDK" \
  --prefix "$PREFIX" --work-dir "$WORK/hpm15" --jobs "$JOBS"
python3 - "$PREFIX/lib/pkgconfig/uavs3d.pc" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
if '-lwebhtvhpm' not in text:
    text = text.replace('-luavs3d', '-luavs3d -lwebhtvhpm')
path.write_text(text)
PY
