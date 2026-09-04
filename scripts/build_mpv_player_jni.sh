#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCK_FILE="$ROOT/third_party/mpv-native-lock.json"
WORK_DIR="${MPV_NATIVE_WORK_DIR:-$ROOT/build/mpv-native}"
ABI="all"
INSTALL_ASSETS=0

usage() {
  cat <<'EOF'
Usage: scripts/build_mpv_player_jni.sh [options]

Build WebHTV's libplayer.so against the pinned MPV native prefix. Run
scripts/build_mpv_native.sh first for every requested ABI.

Options:
  --abi arm64-v8a        Build only ARM64
  --abi armeabi-v7a      Build only ARMv7
  --abi all              Build both ARM ABIs (default)
  --install              Replace matching app asset libplayer.so files
  --work-dir PATH        MPV native work directory (default: build/mpv-native)
  --lock-file PATH       Dependency lock used by the native build
  -h, --help             Show this help
EOF
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --abi)
      [ "$#" -ge 2 ] || die "--abi requires a value"
      ABI="$2"
      shift 2
      ;;
    --install)
      INSTALL_ASSETS=1
      shift
      ;;
    --work-dir)
      [ "$#" -ge 2 ] || die "--work-dir requires a value"
      WORK_DIR="$2"
      shift 2
      ;;
    --lock-file)
      [ "$#" -ge 2 ] || die "--lock-file requires a value"
      LOCK_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown option: $1"
      ;;
  esac
done

case "$ABI" in
  arm64-v8a|armeabi-v7a|all) ;;
  *) die "unsupported ABI: $ABI" ;;
esac

eval "$(python3 - "$LOCK_FILE" <<'PY'
import json
import shlex
import sys

data = json.load(open(sys.argv[1], encoding="utf-8"))
for key, value in {
    "NDK_VERSION": data["android"]["ndk_version"],
    "ANDROID_API_LEVEL": str(data["android"]["api_level"]),
}.items():
    print(f"{key}={shlex.quote(value)}")
PY
)"

detect_sdk_root() {
  if [ -n "${ANDROID_HOME:-}" ]; then
    printf '%s\n' "$ANDROID_HOME"
  elif [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    printf '%s\n' "$ANDROID_SDK_ROOT"
  elif [ -f "$ROOT/local.properties" ]; then
    python3 - "$ROOT/local.properties" <<'PY'
import sys
from pathlib import Path

for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    if line.startswith("sdk.dir="):
        print(line.split("=", 1)[1].replace("\\\\", "\\").replace("\\:", ":"))
        break
PY
  else
    case "$(uname -s)" in
      Darwin) printf '%s\n' "$HOME/Library/Android/sdk" ;;
      *) printf '%s\n' "$HOME/Android/Sdk" ;;
    esac
  fi
}

ANDROID_SDK="$(detect_sdk_root)"
NDK="${ANDROID_NDK_HOME:-$ANDROID_SDK/ndk/$NDK_VERSION}"

case "$(uname -s)" in
  Darwin) HOST_TAG="darwin-x86_64" ;;
  Linux) HOST_TAG="linux-x86_64" ;;
  *) die "unsupported host: $(uname -s)" ;;
esac

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
SYSROOT="$(cd "$TOOLCHAIN/.." && pwd)/sysroot"
SRC_DIR="$ROOT/third_party/mpv-player-jni/src"
BUILDSCRIPTS="$WORK_DIR/mpv-android/buildscripts"

[ -x "$TOOLCHAIN/llvm-strip" ] || die "NDK LLVM tools not found under $TOOLCHAIN"

SOURCES=(
  "$SRC_DIR/main.cpp"
  "$SRC_DIR/render.cpp"
  "$SRC_DIR/log.cpp"
  "$SRC_DIR/jni_utils.cpp"
  "$SRC_DIR/property.cpp"
  "$SRC_DIR/event.cpp"
  "$SRC_DIR/request.cpp"
  "$SRC_DIR/thumbnail.cpp"
  "$SRC_DIR/iso_dvd.cpp"
)

build_abi() {
  local abi="$1"
  local flavor prefix_name cxx cxx_lib_abi
  case "$abi" in
    arm64-v8a)
      flavor=arm64_v8a
      prefix_name=arm64
      cxx="aarch64-linux-android${ANDROID_API_LEVEL}-clang++"
      cxx_lib_abi=aarch64-linux-android
      ;;
    armeabi-v7a)
      flavor=armeabi_v7a
      prefix_name=armv7l
      cxx="armv7a-linux-androideabi${ANDROID_API_LEVEL}-clang++"
      cxx_lib_abi=arm-linux-androideabi
      ;;
  esac

  local prefix="$BUILDSCRIPTS/prefix/$prefix_name"
  local native_output="$WORK_DIR/output/$abi"
  local output="$WORK_DIR/output-jni/$abi"
  local assets="$ROOT/app/src/$flavor/assets/mpv-libs/$abi"
  local libcxx_shared="$SYSROOT/usr/lib/$cxx_lib_abi/libc++_shared.so"
  local out="$output/libplayer.so"
  local tmp="$out.tmp"

  [ -x "$TOOLCHAIN/$cxx" ] || die "NDK clang++ not found: $TOOLCHAIN/$cxx"
  [ -f "$prefix/include/mpv/client.h" ] || die "missing pinned MPV headers for $abi; build the native stack first"
  [ -f "$prefix/include/mpv/stream_cb.h" ] || die "missing patched stream_cb header for $abi"
  [ -f "$prefix/include/libavcodec/jni.h" ] || die "missing pinned FFmpeg headers for $abi"
  [ -f "$native_output/libmpv.so" ] || die "missing staged native output for $abi"
  [ -f "$native_output/libmvcodec.so" ] || die "missing staged FFmpeg output for $abi"
  [ -f "$native_output/libmwscale.so" ] || die "missing staged swscale output for $abi"

  local dvd_archives=(
    "$prefix/lib/libdvdnav.a"
    "$prefix/lib/libdvdread.a"
    "$prefix/lib/libbluray.a"
  )
  if [ -f "$prefix/lib/libudfread.a" ]; then
    dvd_archives+=("$prefix/lib/libudfread.a")
  fi
  local archive
  for archive in "${dvd_archives[@]}"; do
    [ -s "$archive" ] || die "missing native disc dependency for $abi: $archive"
  done

  mkdir -p "$output"
  printf 'Building staged libplayer.so for %s\n' "$abi"
  "$TOOLCHAIN/$cxx" \
    -fPIC -shared -O2 -std=c++17 -Werror \
    -Wl,-z,max-page-size=16384 \
    -I"$prefix/include" \
    "${SOURCES[@]}" \
    -L"$native_output" \
    -Wl,-rpath-link,"$native_output" \
    -Wl,-soname,libplayer.so \
    -lmwscale -lmvcodec -lmpv \
    "${dvd_archives[@]}" \
    -llog -latomic -ldl -lz -lm "$libcxx_shared" \
    -nostdlib++ \
    -stdlib=libc++ \
    -o "$tmp"
  "$TOOLCHAIN/llvm-strip" --strip-unneeded "$tmp"
  mv "$tmp" "$out"
  chmod 644 "$out"

  "$TOOLCHAIN/llvm-readelf" -d "$out" | grep -Fq 'Library soname: [libplayer.so]' || \
    die "unexpected libplayer.so SONAME for $abi"
  if "$TOOLCHAIN/llvm-readelf" -d "$out" | grep -Eq 'Shared library: \[lib(av|sw).+\.so\]'; then
    die "libplayer.so references unrenamed FFmpeg libraries for $abi"
  fi

  if [ "$INSTALL_ASSETS" -eq 1 ]; then
    [ -d "$assets" ] || die "missing MPV asset directory: $assets"
    cp "$out" "$assets/libplayer.so"
    chmod 644 "$assets/libplayer.so"
    printf 'Installed %s\n' "$assets/libplayer.so"
  else
    printf 'Staged %s\n' "$out"
  fi
}

case "$ABI" in
  arm64-v8a) build_abi arm64-v8a ;;
  armeabi-v7a) build_abi armeabi-v7a ;;
  all)
    build_abi arm64-v8a
    build_abi armeabi-v7a
    ;;
esac
