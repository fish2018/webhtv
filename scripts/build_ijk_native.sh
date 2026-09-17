#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SOURCE_DIR="${IJK_BUILD_DIR:-$ROOT_DIR/third_party/sources/ijkplayer-android}"
IJK_REPO="https://github.com/ShikinChen/ijkplayer-android.git"
IJK_REVISION="be89479c77c52acfb023d3b3acefccc5d8b9a101"
FF4_CONFIG_REVISION="96598d75d381a4e8c3137795a7b490253a26ec80"
FFMPEG_REPO="https://github.com/ShikinChen/FFmpeg.git"
FFMPEG_REVISION="e88b5afbf9ca543a098ede6dd6765ef0e629836d"
OPENSSL_BRANCH="openssl-3.2"
OPENSSL_REVISION="14ddbcee237cb99b3921c352852b4d4fadbb8e6c"
IJK_PATCH="$ROOT_DIR/third_party/patches/ijk-vod-buffer-capacity.patch"
IJK_LINK_PATCH="$ROOT_DIR/third_party/patches/ijk-ffmpeg4-link.patch"
IJK_BUFFER_PATCH="$ROOT_DIR/third_party/patches/ijk-vod-buffer-capacity-ff4.patch"
IJK_CORE_PATCH="$ROOT_DIR/third_party/patches/ijk-ffmpeg4-ndk28-core.patch"
IJK_ERROR_PATCH="$ROOT_DIR/third_party/patches/ijk-error-propagation.patch"
FFMPEG_PATCH="$ROOT_DIR/third_party/patches/ijk-ffmpeg4-ndk28.patch"
ABI="arm64-v8a"
INSTALL=0
CLEAN=0

usage() {
  cat <<'USAGE'
Usage: scripts/build_ijk_native.sh [options]

Options:
  --abi arm64-v8a|armeabi-v7a  ABI to build (default: arm64-v8a)
  --install                    Copy generated .so files into app/src/main/jniLibs
  --clean                      Remove the locked source checkout before building
  -h, --help                   Show this help

Environment:
  ANDROID_HOME / ANDROID_SDK_ROOT  Android SDK root
  ANDROID_NDK_HOME                 NDK root for the selected ABI
  IJK_ALLOW_OLD_NDK=1              Allow an explicit non-locked compatibility build
  IJK_BUILD_DIR                    Optional source/build directory

Recommended NDK:
  arm64-v8a     28.2.13676358 (shared with DVD; MPV/JNI use r29)
  armeabi-v7a   28.2.13676358 (shared with DVD; MPV/JNI use r29)
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --abi) ABI="${2:?Missing ABI}"; shift ;;
    --install) INSTALL=1 ;;
    --clean) CLEAN=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

case "$ABI" in
  arm64-v8a) IJK_ARCH="arm64"; EXPECTED_NDK="28.2.13676358" ;;
  armeabi-v7a) IJK_ARCH="armv7a"; EXPECTED_NDK="28.2.13676358" ;;
  *) echo "Unsupported ABI: $ABI" >&2; exit 1 ;;
esac

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_ROOT" && -f "$ROOT_DIR/local.properties" ]]; then
  SDK_ROOT="$(awk -F= '$1 == "sdk.dir" { print substr($0, 9); exit }' "$ROOT_DIR/local.properties")"
fi
if [[ -z "$SDK_ROOT" || ! -d "$SDK_ROOT" ]]; then
  echo "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 1
fi

NDK_ROOT="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-$SDK_ROOT/ndk/$EXPECTED_NDK}}"
if [[ ! -x "$NDK_ROOT/ndk-build" ]]; then
  echo "Android NDK not found: $NDK_ROOT" >&2
  echo "Install it with: $SDK_ROOT/cmdline-tools/latest/bin/sdkmanager \"ndk;$EXPECTED_NDK\"" >&2
  exit 1
fi
NDK_REVISION="$(sed -n 's/^Pkg\.Revision[[:space:]]*=[[:space:]]*//p' "$NDK_ROOT/source.properties" | head -n 1)"
if [[ -z "$NDK_REVISION" ]]; then
  echo "Unable to determine Android NDK revision: $NDK_ROOT" >&2
  exit 1
fi
if [[ "${IJK_ALLOW_OLD_NDK:-0}" != "1" && "$NDK_REVISION" != "$EXPECTED_NDK" ]]; then
  echo "IJK native build requires NDK $EXPECTED_NDK (found $NDK_REVISION)." >&2
  echo "Set ANDROID_NDK_HOME to NDK $EXPECTED_NDK, or use IJK_ALLOW_OLD_NDK=1 for an explicit compatibility build." >&2
  exit 1
fi
echo "Using Android NDK $NDK_REVISION at $NDK_ROOT"

for tool in git make python3 perl pkg-config file; do
  command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 1; }
done
if [[ "$ABI" == x86* ]] && ! command -v yasm >/dev/null && ! command -v nasm >/dev/null; then
  echo "Missing assembler: install yasm (macOS: brew install yasm; Ubuntu: apt install yasm)." >&2
  exit 1
fi

if [[ $CLEAN -eq 1 ]]; then rm -rf "$SOURCE_DIR"; fi
mkdir -p "$(dirname "$SOURCE_DIR")"
if [[ ! -d "$SOURCE_DIR/.git" ]]; then git clone "$IJK_REPO" "$SOURCE_DIR"; fi
git -C "$SOURCE_DIR" fetch --tags origin
git -C "$SOURCE_DIR" checkout --force --detach "$IJK_REVISION"
git -C "$SOURCE_DIR" clean -fdx
git -C "$SOURCE_DIR" submodule update --init --recursive
git -C "$SOURCE_DIR" checkout "$FF4_CONFIG_REVISION" -- \
  config/module.sh \
  config/module-lite.sh \
  android/ijkplayer/ijkplayer-arm64 \
  android/ijkplayer/ijkplayer-armv7a \
  ijkmedia/ijkplayer
git -C "$SOURCE_DIR" apply --check "$IJK_PATCH"
git -C "$SOURCE_DIR" apply "$IJK_PATCH"
git -C "$SOURCE_DIR" apply --check "$IJK_LINK_PATCH"
git -C "$SOURCE_DIR" apply "$IJK_LINK_PATCH"
git -C "$SOURCE_DIR" apply --check "$IJK_BUFFER_PATCH"
git -C "$SOURCE_DIR" apply "$IJK_BUFFER_PATCH"
git -C "$SOURCE_DIR" apply --check "$IJK_CORE_PATCH"
git -C "$SOURCE_DIR" apply "$IJK_CORE_PATCH"
git -C "$SOURCE_DIR" apply --check "$IJK_ERROR_PATCH"
git -C "$SOURCE_DIR" apply "$IJK_ERROR_PATCH"

# Keep the newer Android/NDK build fixes, but restore the proven FFmpeg 4.0
# configuration used by WebHTV's IJK ABI.
git -C "$SOURCE_DIR" show "$FF4_CONFIG_REVISION:init-android.sh" > "$SOURCE_DIR/init-android.sh"
sed -i.bak "s#https://github.com/Bilibili/FFmpeg.git#$FFMPEG_REPO#g" "$SOURCE_DIR/init-android.sh"
sed -i.bak "s#IJK_FFMPEG_COMMIT=.*#IJK_FFMPEG_COMMIT=$FFMPEG_REVISION#" "$SOURCE_DIR/init-android.sh"
sed -i.bak "s#^IJK_OPENSSL_COMMIT=.*#IJK_OPENSSL_COMMIT=$OPENSSL_REVISION#" "$SOURCE_DIR/init-android-openssl.sh"
for arch in armv5 armv7a arm64 x86 x86_64; do
  if [[ "$arch" != "$IJK_ARCH" ]]; then
    sed -i.bak "/^pull_fork \"$arch\"$/d" "$SOURCE_DIR/init-android.sh"
  fi
done
rm -f "$SOURCE_DIR/init-android.sh.bak" "$SOURCE_DIR/init-android-openssl.sh.bak"
for app_mk in \
  "$SOURCE_DIR/android/ijkplayer/ijkplayer-arm64/src/main/jni/Application.mk" \
  "$SOURCE_DIR/android/ijkplayer/ijkplayer-armv7a/src/main/jni/Application.mk"; do
  sed -i.bak \
    -e 's/^APP_PLATFORM := android-9$/APP_PLATFORM := android-21/' \
    -e '/^NDK_TOOLCHAIN_VERSION=/d' \
    -e 's/^APP_STL := stlport_static$/APP_STL := c++_static/' \
    "$app_mk"
  rm -f "$app_mk.bak"
done

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK="$SDK_ROOT"
export ANDROID_NDK="$NDK_ROOT"
export ANDROID_NDK_HOME="$NDK_ROOT"

cd "$SOURCE_DIR"
./init-android.sh
FFMPEG_DIR="$SOURCE_DIR/android/contrib/ffmpeg-$IJK_ARCH"
git -C "$FFMPEG_DIR" reset --hard "$FFMPEG_REVISION"
git -C "$FFMPEG_DIR" clean -fdx
git -C "$FFMPEG_DIR" apply --check "$FFMPEG_PATCH"
git -C "$FFMPEG_DIR" apply "$FFMPEG_PATCH"
# init-android-openssl.sh edits a tracked OpenSSL config file for clang
# detection. Reset the exact checkout before each ABI build so a previous ABI's
# generated Makefile cannot trigger OpenSSL's one-time configdata rebuild.
OPENSSL_DIR="$SOURCE_DIR/extra/openssl"
if [[ -d "$OPENSSL_DIR/.git" ]]; then
  git -C "$OPENSSL_DIR" fetch origin "$OPENSSL_BRANCH"
  git -C "$OPENSSL_DIR" reset --hard "$OPENSSL_REVISION"
  git -C "$OPENSSL_DIR" clean -fdx
fi
./init-android-openssl.sh
cd android/contrib
./compile-ffmpeg.sh "$IJK_ARCH"
cd ..
./compile-ijk.sh "$IJK_ARCH"

OUTPUT_DIR="$SOURCE_DIR/android/ijkplayer/ijkplayer-$IJK_ARCH/src/main/libs/$ABI"
if [[ ! -d "$OUTPUT_DIR" ]]; then
  echo "IJK output directory not found: $OUTPUT_DIR" >&2
  exit 1
fi

for name in libijkffmpeg.so libijksdl.so libijkplayer.so; do
  file="$OUTPUT_DIR/$name"
  [[ -s "$file" ]] || { echo "Missing output: $file" >&2; exit 1; }
  file "$file"
done

if [[ $INSTALL -eq 1 ]]; then
  DEST="$ROOT_DIR/app/src/main/jniLibs/$ABI"
  mkdir -p "$DEST"
  cp "$OUTPUT_DIR/libijkffmpeg.so" "$OUTPUT_DIR/libijksdl.so" "$OUTPUT_DIR/libijkplayer.so" "$DEST/"
  echo "Installed IJK libraries into $DEST"
else
  echo "IJK libraries are ready in $OUTPUT_DIR"
  echo "Re-run with --install to copy them into the app."
fi
