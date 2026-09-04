#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCK_FILE="$ROOT/third_party/mpv-native-lock.json"
OVERRIDE_DIR="$ROOT/third_party/mpv-native-overrides"
MPV_DISC_PATCH="$ROOT/third_party/patches/mpv-stream-cb-disc-controls.patch"
MPV_DOVI_SURFACE_PATCH="$ROOT/third_party/patches/mpv-android-dovi-el-surface.patch"
MPV_DOVI_HDR10_BL_PATCH="$ROOT/third_party/patches/mpv-dovi-profile7-hdr10-base-layer.patch"
MPV_DOVI_P81_PATCH="$ROOT/third_party/patches/mpv-dovi-profile7-p81.patch"
MPV_AUDIO_TRUEHD_PATCH="$ROOT/third_party/patches/mpv-audiotrack-truehd-channel-mask.patch"
MPV_OPTIONAL_OSD_PATCH="$ROOT/third_party/patches/mpv-mediacodec-embed-optional-osd.patch"
MPV_MEDIACODEC_TIMED_RELEASE_PATCH="$ROOT/third_party/patches/mpv-mediacodec-embed-timed-release.patch"
MPV_MEDIACODEC_TIMING_DIAGNOSTICS_PATCH="$ROOT/third_party/patches/mpv-mediacodec-output-timing-diagnostics.patch"
MPV_VULKAN_CONVERSION_PATCH="$ROOT/third_party/patches/mpv-android-vulkan-conversion-default.patch"
MPV_VULKAN_SMART_PATCH="$ROOT/third_party/patches/mpv-android-vulkan-smart-backend.patch"
MPV_VULKAN_LEGACY_PATCH="$ROOT/third_party/patches/mpv-android-vulkan-legacy-backend.patch"
MPV_AIMAGEREADER_STABLE_PATCH="$ROOT/third_party/patches/mpv-aimagereader-stable-flow.patch"
MPV_P2_GENERIC_UV_PATCH="$ROOT/third_party/patches/mpv-p2-generic-uv.patch"
MPV_AIMAGEREADER_STABLE_SOURCE="$OVERRIDE_DIR/aimagereader-stable/video/out/hwdec/hwdec_aimagereader_vk_stable.c"
MPV_AIMAGEREADER_STABLE_SHADER="$OVERRIDE_DIR/aimagereader-stable/video/out/hwdec/hwdec_aimagereader_vk_stable.comp"
MPV_MATROSKA_PATCH="$ROOT/third_party/patches/mpv-matroska-segment-end.patch"
MPV_P1_PACKED_RGB10_PATCH="$ROOT/third_party/patches/mpv-p1-packed-rgb10.patch"
MPV_P1_EBML_DEFAULTS_PATCH="$ROOT/third_party/patches/mpv-p1-ebml-defaults.patch"
MPV_P1_HLS_EDITION_PATCH="$ROOT/third_party/patches/mpv-p1-hls-edition.patch"
LIBPLACEBO_P1_ALPHA_PATCH="$ROOT/third_party/patches/libplacebo-p1-alpha.patch"
FFMPEG_PROXY_RANGE_PATCH="$ROOT/third_party/patches/ffmpeg-webhtv-proxy-range.patch"
FFMPEG_MEDIACODEC_STARVATION_PATCH="$ROOT/third_party/patches/ffmpeg-mediacodec-port-starvation.patch"
WORK_DIR="${MPV_NATIVE_WORK_DIR:-$ROOT/build/mpv-native}"
ABI="arm64-v8a"
JOBS="${MPV_NATIVE_JOBS:-}"
INSTALL_ASSETS=0
PREPARE_ONLY=0
STAGE_ONLY=0
INCREMENTAL=0

usage() {
  cat <<'EOF'
Usage: scripts/build_mpv_native.sh [options]

Rebuild the pinned MPV/FFmpeg native stack. Normal Gradle and GitHub Actions
builds do not call this script; they reuse the committed .so assets.

Options:
  --abi arm64-v8a        Build the validated 64-bit stack (default)
  --abi armeabi-v7a      Build the 32-bit stack
  --abi all              Build both ARM ABIs
  --install              Replace the matching app/src/*/assets/mpv-libs files
  --prepare-only         Download and pin all sources without compiling
  --stage-only           Stage and verify an already-built native prefix
  --incremental          Keep build prefixes instead of doing a clean rebuild
  --lock-file PATH       Use an alternate dependency lock for compatibility tests
  --work-dir PATH        Build/cache directory (default: build/mpv-native)
  --jobs N               Parallel compiler jobs
  -h, --help             Show this help

Examples:
  scripts/build_mpv_native.sh --abi arm64-v8a --install
  scripts/build_mpv_native.sh --abi all --install --jobs 8
EOF
}

log() {
  printf '\n==> %s\n' "$*"
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing command: $1"
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
    --prepare-only)
      PREPARE_ONLY=1
      shift
      ;;
    --stage-only)
      STAGE_ONLY=1
      shift
      ;;
    --incremental)
      INCREMENTAL=1
      shift
      ;;
    --lock-file)
      [ "$#" -ge 2 ] || die "--lock-file requires a value"
      LOCK_FILE="$2"
      shift 2
      ;;
    --work-dir)
      [ "$#" -ge 2 ] || die "--work-dir requires a value"
      WORK_DIR="$2"
      shift 2
      ;;
    --jobs)
      [ "$#" -ge 2 ] || die "--jobs requires a value"
      JOBS="$2"
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
[ "$PREPARE_ONLY" -eq 0 ] || [ "$STAGE_ONLY" -eq 0 ] || \
  die "--prepare-only and --stage-only cannot be used together"

need_cmd git
need_cmd curl
need_cmd tar
need_cmd make
need_cmd python3
need_cmd pkg-config
need_cmd perl
need_cmd cmake
need_cmd gperf

eval "$(python3 - "$LOCK_FILE" <<'PY'
import json
import shlex
import sys

data = json.load(open(sys.argv[1], encoding="utf-8"))
values = {
    "BUILDER_REPO": data["builder"]["repo"],
    "BUILDER_REV": data["builder"]["commit"],
    "NDK_VERSION": data["android"]["ndk_version"],
    "NDK_LABEL": data["android"]["ndk_label"],
    "ANDROID_API_LEVEL": str(data["android"]["api_level"]),
    "MESON_VERSION": data["python_tools"]["meson"],
    "NINJA_VERSION": data["python_tools"]["ninja"],
}
for name, source in data["sources"].items():
    prefix = name.upper().replace("-", "_")
    for key in (
        "repo", "commit", "url", "sha256", "version", "describe_tag",
        "tag_repo", "history_depth"
    ):
        if key in source:
            values[f"{prefix}_{key.upper()}"] = str(source[key])
    values[f"{prefix}_SUBMODULES"] = "1" if source.get("submodules") else "0"
for key, value in values.items():
    print(f"{key}={shlex.quote(value)}")
PY
)"

ENABLE_LIBCURL=0
if [ -n "${CURL_URL:-}${CURL_COMMIT:-}" ] || [ -n "${NGHTTP2_URL:-}${NGHTTP2_COMMIT:-}" ]; then
  [ -n "${CURL_URL:-}${CURL_COMMIT:-}" ] || die "curl source is required when nghttp2 is enabled"
  [ -n "${NGHTTP2_URL:-}${NGHTTP2_COMMIT:-}" ] || die "nghttp2 source is required when curl is enabled"
  ENABLE_LIBCURL=1
fi

detect_sdk_root() {
  if [ -n "${ANDROID_HOME:-}" ]; then
    printf '%s\n' "$ANDROID_HOME"
    return
  fi
  if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    printf '%s\n' "$ANDROID_SDK_ROOT"
    return
  fi
  if [ -f "$ROOT/local.properties" ]; then
    python3 - "$ROOT/local.properties" <<'PY'
import sys
from pathlib import Path

for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    if line.startswith("sdk.dir="):
        value = line.split("=", 1)[1]
        print(value.replace("\\\\", "\\").replace("\\:", ":"))
        break
PY
    return
  fi
  case "$(uname -s)" in
    Darwin) printf '%s\n' "$HOME/Library/Android/sdk" ;;
    *) printf '%s\n' "$HOME/Android/Sdk" ;;
  esac
}

SDK_ROOT="$(detect_sdk_root)"
NDK_ROOT="${ANDROID_NDK_HOME:-$SDK_ROOT/ndk/$NDK_VERSION}"
[ -f "$NDK_ROOT/source.properties" ] || die "Android NDK $NDK_VERSION not found at $NDK_ROOT. Install it with sdkmanager \"ndk;$NDK_VERSION\" or set ANDROID_NDK_HOME."
grep -q "Pkg.Revision = $NDK_VERSION" "$NDK_ROOT/source.properties" || die "NDK revision mismatch: expected $NDK_VERSION at $NDK_ROOT"

case "$(uname -s)" in
  Darwin) HOST_TAG=darwin-x86_64 ;;
  Linux) HOST_TAG=linux-x86_64 ;;
  *) die "supported hosts are macOS and Linux" ;;
esac

TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"
[ -d "$TOOLCHAIN" ] || die "NDK toolchain not found: $TOOLCHAIN"
OBJCOPY="$TOOLCHAIN/bin/llvm-objcopy"
STRIP="$TOOLCHAIN/bin/llvm-strip"
READELF="$TOOLCHAIN/bin/llvm-readelf"
GLSLC="$NDK_ROOT/shader-tools/$HOST_TAG/glslc"
SPIRV_VAL="$NDK_ROOT/shader-tools/$HOST_TAG/spirv-val"
[ -x "$OBJCOPY" ] && [ -x "$STRIP" ] && [ -x "$READELF" ] && \
  [ -x "$GLSLC" ] && [ -x "$SPIRV_VAL" ] || \
  die "NDK LLVM/shader tools are incomplete"
python3 "$ROOT/scripts/verify_mpv_vulkan_shader_contract.py"

if [ -z "$JOBS" ]; then
  if command -v nproc >/dev/null 2>&1; then
    JOBS="$(nproc)"
  elif command -v sysctl >/dev/null 2>&1; then
    JOBS="$(sysctl -n hw.ncpu 2>/dev/null || printf '4\n')"
  else
    JOBS=4
  fi
fi
case "$JOBS" in
  ''|*[!0-9]*) die "invalid --jobs value: $JOBS" ;;
esac

mkdir -p "$WORK_DIR" "$WORK_DIR/downloads"
FRAMEWORK_DIR="$WORK_DIR/mpv-android"
BUILDSCRIPTS="$FRAMEWORK_DIR/buildscripts"
VENV="$WORK_DIR/python-tools"

checkout_repo() {
  local name="$1"
  local repo="$2"
  local revision="$3"
  local directory="$4"
  local submodules="${5:-0}"

  log "Preparing $name @ ${revision:0:12}"
  if [ ! -d "$directory/.git" ]; then
    rm -rf "$directory"
    mkdir -p "$directory"
    git -C "$directory" init -q
    git -C "$directory" remote add origin "$repo"
  elif [ "$(git -C "$directory" remote get-url origin)" != "$repo" ]; then
    git -C "$directory" remote set-url origin "$repo"
  fi
  if ! git -C "$directory" cat-file -e "$revision^{commit}" 2>/dev/null; then
    git -C "$directory" fetch --depth 1 origin "$revision"
  fi
  git -C "$directory" checkout -q --force --detach "$revision"
  if [ "$submodules" = "1" ]; then
    git -C "$directory" submodule sync --recursive
    if ! git -C "$directory" submodule update --init --recursive --depth 1; then
      git -C "$directory" submodule update --init --recursive
    fi
  fi
  local actual
  actual="$(git -C "$directory" rev-parse HEAD)"
  [ "$actual" = "$revision" ] || die "$name revision mismatch: $actual"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

extract_archive() {
  local name="$1"
  local url="$2"
  local expected_sha="$3"
  local directory="$4"
  local archive="$WORK_DIR/downloads/${url##*/}"
  local marker="$directory/.webhtv-source-sha256"

  if [ ! -f "$archive" ] || [ "$(sha256_file "$archive")" != "$expected_sha" ]; then
    log "Downloading $name"
    rm -f "$archive"
    curl --location --fail --retry 3 --output "$archive" "$url"
  fi
  local actual_sha
  actual_sha="$(sha256_file "$archive")"
  [ "$actual_sha" = "$expected_sha" ] || die "$name SHA-256 mismatch: $actual_sha"
  if [ ! -f "$marker" ] || [ "$(cat "$marker")" != "$expected_sha" ]; then
    rm -rf "$directory"
    mkdir -p "$directory"
    tar -xf "$archive" -C "$directory" --strip-components=1
    printf '%s\n' "$expected_sha" >"$marker"
  fi
}

prepare_python_tools() {
  if [ ! -x "$VENV/bin/python" ]; then
    log "Creating isolated Meson/Ninja environment"
    python3 -m venv "$VENV"
  fi
  "$VENV/bin/python" -m pip install --disable-pip-version-check --quiet \
    "meson==$MESON_VERSION" "ninja==$NINJA_VERSION"
  export PATH="$VENV/bin:$PATH"
  meson --version | grep -qx "$MESON_VERSION" || die "Meson version mismatch"
}

prepare_framework() {
  checkout_repo "mpv-android build framework" "$BUILDER_REPO" "$BUILDER_REV" "$FRAMEWORK_DIR"
  mkdir -p "$BUILDSCRIPTS/sdk"
  rm -rf "$BUILDSCRIPTS/sdk/android-ndk-$NDK_LABEL"
  ln -s "$NDK_ROOT" "$BUILDSCRIPTS/sdk/android-ndk-$NDK_LABEL"

  cp "$OVERRIDE_DIR/depinfo.sh" "$BUILDSCRIPTS/include/depinfo.sh"
  cp "$OVERRIDE_DIR/path.sh" "$BUILDSCRIPTS/include/path.sh"
  cp "$OVERRIDE_DIR/libass.sh" "$BUILDSCRIPTS/scripts/libass.sh"
  cp "$OVERRIDE_DIR/lua.sh" "$BUILDSCRIPTS/scripts/lua.sh"
  cp "$OVERRIDE_DIR/shaderc.sh" "$BUILDSCRIPTS/scripts/shaderc.sh"
  cp "$OVERRIDE_DIR/libplacebo.sh" "$BUILDSCRIPTS/scripts/libplacebo.sh"
  cp "$OVERRIDE_DIR/nghttp2.sh" "$BUILDSCRIPTS/scripts/nghttp2.sh"
  cp "$OVERRIDE_DIR/curl.sh" "$BUILDSCRIPTS/scripts/curl.sh"
  cp "$OVERRIDE_DIR/mpv.sh" "$BUILDSCRIPTS/scripts/mpv.sh"
  local lock_hash
  lock_hash="$(sha256_file "$LOCK_FILE")"
  printf '\n# WebHTV wrapper cache identity: exact selected lock file.\nci_tarball="prefix-webhtv-%s.tgz"\n' \
    "$lock_hash" >> "$BUILDSCRIPTS/include/depinfo.sh"
  chmod +x "$BUILDSCRIPTS/scripts/libass.sh" "$BUILDSCRIPTS/scripts/lua.sh" \
    "$BUILDSCRIPTS/scripts/shaderc.sh" \
    "$BUILDSCRIPTS/scripts/libplacebo.sh" "$BUILDSCRIPTS/scripts/nghttp2.sh" \
    "$BUILDSCRIPTS/scripts/curl.sh" "$BUILDSCRIPTS/scripts/mpv.sh"
  python3 - "$BUILDSCRIPTS/buildall.sh" "$BUILDSCRIPTS/include/cmake-android.sh" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
old = "\t$BUILDSCRIPT build\n"
new = '\tbash -e "$BUILDSCRIPT" build || return $?\n'
current = '\tbash "$BUILDSCRIPT" build\n'
current_fixed = '\tbash -e "$BUILDSCRIPT" build || return $?\n'
legacy_fixed = '\tbash "$BUILDSCRIPT" build || return $?\n'
if old in text:
    text = text.replace(old, new)
elif current in text:
    text = text.replace(current, current_fixed)
elif legacy_fixed in text:
    text = text.replace(legacy_fixed, current_fixed)
elif new not in text and current_fixed not in text:
    raise SystemExit("unexpected upstream buildall.sh layout")
clean = '\t[ $cleanbuild -eq 1 ] && bash "$BUILDSCRIPT" clean\n'
clean_fixed = '\t[ $cleanbuild -eq 1 ] && bash -e "$BUILDSCRIPT" clean\n'
if clean in text:
    text = text.replace(clean, clean_fixed)
elif clean_fixed not in text:
    raise SystemExit("unexpected upstream clean command layout")
old_mark = '\tdeclare -g "$varname=0"\n'
new_mark = '\tprintf -v "$varname" "%s" 0\n'
if old_mark in text:
    text = text.replace(old_mark, new_mark)
elif new_mark not in text:
    raise SystemExit("unexpected upstream build marker layout")
old_api = "\tlocal apilvl=23\n"
new_api = "\tlocal apilvl=${android_api:-24}\n"
current_api = "\texport android_api=24\n"
locked_api = "\texport android_api=${WEBHTV_ANDROID_API_LEVEL:-24}\n"
if old_api in text:
    text = text.replace(old_api, new_api)
elif current_api in text:
    text = text.replace(current_api, locked_api)
elif new_api not in text and locked_api not in text:
    raise SystemExit("unexpected upstream Android API layout")
path.write_text(text, encoding="utf-8")

cmake_path = Path(sys.argv[2])
cmake_text = cmake_path.read_text(encoding="utf-8")
policy_flag = "\t\t-DCMAKE_POLICY_VERSION_MINIMUM=3.5 \\\n"
toolchain_flag = '\t\t-DCMAKE_TOOLCHAIN_FILE="$ndk_dir/build/cmake/android.toolchain.cmake" \\\n'
if policy_flag not in cmake_text:
    if toolchain_flag not in cmake_text:
        raise SystemExit("unexpected upstream Android CMake helper layout")
    cmake_text = cmake_text.replace(toolchain_flag, toolchain_flag + policy_flag)
cmake_path.write_text(cmake_text, encoding="utf-8")
PY
}

generate_mpv_shader_header() {
  local stage="$1"
  local source="$2"
  local header="$3"
  local symbol="$4"
  local generated_dir="$WORK_DIR/generated-shaders"
  local stem="${header##*/}"
  local spv="$generated_dir/$stem.spv"
  local initializer="$generated_dir/$stem.inc"

  mkdir -p "$generated_dir"
  "$GLSLC" -fshader-stage="$stage" --target-env=vulkan1.2 -O \
    -o "$spv" "$source"
  "$SPIRV_VAL" --target-env vulkan1.2 "$spv"
  "$GLSLC" -fshader-stage="$stage" --target-env=vulkan1.2 -O -mfmt=c \
    -o "$initializer" "$source"
  {
    printf '// Generated from %s with:\n' "${source##*/}"
    printf '// glslc -fshader-stage=%s --target-env=vulkan1.2 -O -mfmt=c\n' \
      "$stage"
    printf 'static const uint32_t %s[] = ' "$symbol"
    cat "$initializer"
    printf ';\n'
  } >"$header"
}

prepare_sources() {
  local deps="$BUILDSCRIPTS/deps"
  mkdir -p "$deps"
  extract_archive libiconv "$LIBICONV_URL" "$LIBICONV_SHA256" "$deps/libiconv"
  extract_archive uchardet "$UCHARDET_URL" "$UCHARDET_SHA256" "$deps/uchardet"
  extract_archive bzip2 "$BZIP2_URL" "$BZIP2_SHA256" "$deps/bzip2"
  extract_archive xz "$XZ_URL" "$XZ_SHA256" "$deps/xz"
  extract_archive zstd "$ZSTD_URL" "$ZSTD_SHA256" "$deps/zstd"
  checkout_repo mbedtls "$MBEDTLS_REPO" "$MBEDTLS_COMMIT" "$deps/mbedtls" "$MBEDTLS_SUBMODULES"
  "$VENV/bin/python" -m pip install --disable-pip-version-check --quiet \
    -r "$deps/mbedtls/scripts/basic.requirements.txt"
  checkout_repo dav1d "$DAV1D_REPO" "$DAV1D_COMMIT" "$deps/dav1d"
  checkout_repo FFmpeg "$FFMPEG_REPO" "$FFMPEG_COMMIT" "$deps/ffmpeg"
  [ -f "$FFMPEG_PROXY_RANGE_PATCH" ] || die "missing FFmpeg proxy range patch: $FFMPEG_PROXY_RANGE_PATCH"
  git -C "$deps/ffmpeg" apply --check "$FFMPEG_PROXY_RANGE_PATCH"
  git -C "$deps/ffmpeg" apply "$FFMPEG_PROXY_RANGE_PATCH"
  [ -f "$FFMPEG_MEDIACODEC_STARVATION_PATCH" ] || die "missing FFmpeg MediaCodec starvation patch: $FFMPEG_MEDIACODEC_STARVATION_PATCH"
  git -C "$deps/ffmpeg" apply --check "$FFMPEG_MEDIACODEC_STARVATION_PATCH"
  git -C "$deps/ffmpeg" apply "$FFMPEG_MEDIACODEC_STARVATION_PATCH"
  checkout_repo FreeType "$FREETYPE2_REPO" "$FREETYPE2_COMMIT" "$deps/freetype2" "$FREETYPE2_SUBMODULES"
  extract_archive libxml2 "$LIBXML2_URL" "$LIBXML2_SHA256" "$deps/libxml2"
  extract_archive libaribcaption "$LIBARIBCAPTION_URL" "$LIBARIBCAPTION_SHA256" "$deps/libaribcaption"
  checkout_repo fontconfig "$FONTCONFIG_REPO" "$FONTCONFIG_COMMIT" "$deps/fontconfig"
  checkout_repo FriBidi "$FRIBIDI_REPO" "$FRIBIDI_COMMIT" "$deps/fribidi"
  checkout_repo HarfBuzz "$HARFBUZZ_REPO" "$HARFBUZZ_COMMIT" "$deps/harfbuzz"
  extract_archive libunibreak "$UNIBREAK_URL" "$UNIBREAK_SHA256" "$deps/unibreak"
  checkout_repo libass "$LIBASS_REPO" "$LIBASS_COMMIT" "$deps/libass"
  extract_archive Lua "$LUA_URL" "$LUA_SHA256" "$deps/lua"
  local shaderc_source="$NDK_ROOT/sources/third_party/shaderc"
  local shaderc_marker="$deps/shaderc/.webhtv-ndk-version"
  [ -f "$shaderc_source/Android.mk" ] || \
    die "Android NDK $NDK_VERSION does not contain shaderc sources at $shaderc_source"
  if [ ! -f "$shaderc_marker" ] || [ "$(cat "$shaderc_marker")" != "$NDK_VERSION" ]; then
    log "Staging shaderc from Android NDK $NDK_VERSION"
    rm -rf "$deps/shaderc"
    mkdir -p "$deps/shaderc"
    cp -R "$shaderc_source/." "$deps/shaderc/"
    printf '%s\n' "$NDK_VERSION" >"$shaderc_marker"
  fi
  checkout_repo libplacebo "$LIBPLACEBO_REPO" "$LIBPLACEBO_COMMIT" "$deps/libplacebo" "$LIBPLACEBO_SUBMODULES"
  [ -f "$LIBPLACEBO_P1_ALPHA_PATCH" ] || die "missing libplacebo alpha correctness patch: $LIBPLACEBO_P1_ALPHA_PATCH"
  git -C "$deps/libplacebo" apply --check "$LIBPLACEBO_P1_ALPHA_PATCH"
  git -C "$deps/libplacebo" apply "$LIBPLACEBO_P1_ALPHA_PATCH"
  if [ "$ENABLE_LIBCURL" -eq 1 ]; then
    extract_archive nghttp2 "$NGHTTP2_URL" "$NGHTTP2_SHA256" "$deps/nghttp2"
    extract_archive curl "$CURL_URL" "$CURL_SHA256" "$deps/curl"
  fi
  extract_archive libbluray "$LIBBLURAY_URL" "$LIBBLURAY_SHA256" "$deps/libbluray"
  extract_archive libarchive "$LIBARCHIVE_URL" "$LIBARCHIVE_SHA256" "$deps/libarchive"
  extract_archive libdvdread "$LIBDVDREAD_URL" "$LIBDVDREAD_SHA256" "$deps/libdvdread"
  extract_archive libdvdnav "$LIBDVDNAV_URL" "$LIBDVDNAV_SHA256" "$deps/libdvdnav"
  extract_archive rubberband "$RUBBERBAND_URL" "$RUBBERBAND_SHA256" "$deps/rubberband"
  checkout_repo mpv "$MPV_REPO" "$MPV_COMMIT" "$deps/mpv"
  # The initial exact-commit fetch is shallow. Fetch enough ancestry and the
  # release tag so MPV embeds the version string recorded by the selected lock.
  if [ "$(git -C "$deps/mpv" describe --abbrev=9 --tags --match "$MPV_DESCRIBE_TAG" HEAD 2>/dev/null || true)" != "v$MPV_VERSION" ]; then
    # Fetch the upstream tag first. A depth-1 tag fetch marks the tag commit as
    # a shallow boundary; expanding the selected FongMi commit afterwards
    # reconnects that commit and keeps git-describe deterministic.
    git -C "$deps/mpv" fetch --depth=1 "$MPV_TAG_REPO" \
      "refs/tags/$MPV_DESCRIBE_TAG:refs/tags/$MPV_DESCRIBE_TAG"
    git -C "$deps/mpv" fetch --depth="$MPV_HISTORY_DEPTH" origin "$MPV_COMMIT"
  fi
  [ "$(git -C "$deps/mpv" describe --abbrev=9 --tags --match "$MPV_DESCRIBE_TAG" HEAD)" = "v$MPV_VERSION" ] || die "MPV describe version mismatch"
  # FongMi tracks post-release commits while upstream's MPV_VERSION remains
  # "0.41.0-UNKNOWN". Embed the deterministic git-describe version selected by
  # the lock so staged binaries and later asset verification agree exactly.
  printf '%s\n' "$MPV_VERSION" >"$deps/mpv/MPV_VERSION"
  [ -f "$deps/mpv/video/out/hwdec/hwdec_aimagereader_vk.c" ] || \
    die "pinned FongMi MPV is missing its Vulkan AImageReader backend"
  [ -f "$deps/mpv/video/out/hwdec/hwdec_aimagereader_vk_direct.c" ] || \
    die "pinned FongMi MPV is missing direct Vulkan AHardwareBuffer sampling"
  [ -f "$MPV_AIMAGEREADER_STABLE_SOURCE" ] || \
    die "missing stable Vulkan AImageReader conversion source: $MPV_AIMAGEREADER_STABLE_SOURCE"
  [ -f "$MPV_AIMAGEREADER_STABLE_SHADER" ] || \
    die "missing stable Vulkan AImageReader conversion shader: $MPV_AIMAGEREADER_STABLE_SHADER"
  cp "$MPV_AIMAGEREADER_STABLE_SOURCE" \
    "$deps/mpv/video/out/hwdec/hwdec_aimagereader_vk_stable.c"
  cp "$MPV_AIMAGEREADER_STABLE_SHADER" \
    "$deps/mpv/video/out/hwdec/hwdec_aimagereader_vk_stable.comp"
  "$GLSLC" -fshader-stage=compute --target-env=vulkan1.2 -O \
    -o "$deps/mpv/video/out/hwdec/hwdec_aimagereader_vk_stable_comp.spv" \
    "$deps/mpv/video/out/hwdec/hwdec_aimagereader_vk_stable.comp"
  "$SPIRV_VAL" --target-env vulkan1.2 \
    "$deps/mpv/video/out/hwdec/hwdec_aimagereader_vk_stable_comp.spv"
  "$GLSLC" -fshader-stage=compute --target-env=vulkan1.2 -O -mfmt=c \
    -o "$deps/mpv/video/out/hwdec/hwdec_aimagereader_vk_stable_comp.inc" \
    "$deps/mpv/video/out/hwdec/hwdec_aimagereader_vk_stable.comp"
  [ -f "$MPV_DISC_PATCH" ] || die "missing MPV disc controls patch: $MPV_DISC_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_DISC_PATCH"
  git -C "$deps/mpv" apply "$MPV_DISC_PATCH"
  [ -f "$MPV_DOVI_SURFACE_PATCH" ] || die "missing MPV Android Dolby Vision Surface patch: $MPV_DOVI_SURFACE_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_DOVI_SURFACE_PATCH"
  git -C "$deps/mpv" apply "$MPV_DOVI_SURFACE_PATCH"
  [ -f "$MPV_DOVI_HDR10_BL_PATCH" ] || die "missing MPV Dolby Vision Profile 7 HDR10 base-layer patch: $MPV_DOVI_HDR10_BL_PATCH"
  git -C "$deps/mpv" apply --check --recount "$MPV_DOVI_HDR10_BL_PATCH"
  git -C "$deps/mpv" apply --recount "$MPV_DOVI_HDR10_BL_PATCH"
  [ -f "$MPV_DOVI_P81_PATCH" ] || die "missing MPV Dolby Vision Profile 7 P8.1 patch: $MPV_DOVI_P81_PATCH"
  git -C "$deps/mpv" apply --check --recount "$MPV_DOVI_P81_PATCH"
  git -C "$deps/mpv" apply --recount "$MPV_DOVI_P81_PATCH"
  grep -Fq 'av_bsf_get_by_name(filter_name)' "$deps/mpv/demux/dovi_split.c" || \
    die "MPV Dolby Vision Profile 7 P8.1 BSF selection is absent"
  grep -Fq 'DV7 P8.1 conversion: using FFmpeg dovi_rpu BSF.' "$deps/mpv/demux/dovi_split.c" || \
    die "MPV Dolby Vision Profile 7 P8.1 conversion marker is absent"
  grep -Fq 'DV7 P8.1 conversion: removed stale enhancement-layer configuration.' "$deps/mpv/demux/dovi_split.c" || \
    die "MPV Dolby Vision Profile 7 P8.1 stale enhancement-layer configuration guard is absent"
  grep -Fq '(!s->base_only && !s->convert_p81)' "$deps/mpv/demux/dovi_split.c" || \
    die "MPV Dolby Vision Profile 7 P8.1 base-packet filter guard is absent"
  grep -Fq '(!bl->codec->dv_el_present && !base_only && !convert_p81)' "$deps/mpv/demux/dovi_split.c" || \
    die "MPV Dolby Vision Profile 7 metadata-missing splitter guard is absent"
  grep -Fq 'MPSWAP(AVCodecParameters, *bl->codec->lav_codecpar, *filtered)' "$deps/mpv/demux/dovi_split.c" || \
    die "MPV Dolby Vision Profile 7 filtered codec parameters are not synchronized"
  grep -Fq 'bl->codec->dv_el_present = false' "$deps/mpv/demux/dovi_split.c" || \
    die "MPV Dolby Vision Profile 7 enhancement-layer metadata is not cleared"
  grep -Fq 'bl_dp->len > INT_MAX' "$deps/mpv/demux/dovi_split.c" || \
    die "MPV Dolby Vision Profile 7 packet-size guard is absent"
  [ -f "$MPV_AUDIO_TRUEHD_PATCH" ] || die "missing MPV AudioTrack codec-aware channel-mask patch: $MPV_AUDIO_TRUEHD_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_AUDIO_TRUEHD_PATCH"
  git -C "$deps/mpv" apply "$MPV_AUDIO_TRUEHD_PATCH"
  grep -Fq 'ENTRY(BuildVersion)' "$deps/mpv/audio/out/ao_audiotrack.c" || \
    die "MPV AudioTrack Android API mapping is absent"
  grep -Fq 'BuildVersion.SDK_INT >= ANDROID_API_LEVEL_S &&' "$deps/mpv/audio/out/ao_audiotrack.c" || \
    die "MPV AudioTrack Android 12 carrier gate is absent"
  grep -Fq 'ao->channels.num == 8' "$deps/mpv/audio/out/ao_audiotrack.c" || \
    die "MPV AudioTrack 8-channel carrier gate is absent"
  [ -f "$MPV_MEDIACODEC_TIMED_RELEASE_PATCH" ] || die "missing MPV MediaCodec timed-release patch: $MPV_MEDIACODEC_TIMED_RELEASE_PATCH"
  git -C "$deps/mpv" apply --check --recount "$MPV_MEDIACODEC_TIMED_RELEASE_PATCH"
  git -C "$deps/mpv" apply --recount "$MPV_MEDIACODEC_TIMED_RELEASE_PATCH"
  [ -f "$MPV_OPTIONAL_OSD_PATCH" ] || die "missing MPV optional direct-output OSD patch: $MPV_OPTIONAL_OSD_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_OPTIONAL_OSD_PATCH"
  git -C "$deps/mpv" apply "$MPV_OPTIONAL_OSD_PATCH"
  [ -f "$MPV_MEDIACODEC_TIMING_DIAGNOSTICS_PATCH" ] || die "missing MPV MediaCodec output timing diagnostics patch: $MPV_MEDIACODEC_TIMING_DIAGNOSTICS_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_MEDIACODEC_TIMING_DIAGNOSTICS_PATCH"
  git -C "$deps/mpv" apply "$MPV_MEDIACODEC_TIMING_DIAGNOSTICS_PATCH"
  [ -f "$MPV_VULKAN_CONVERSION_PATCH" ] || die "missing MPV Android Vulkan conversion-default patch: $MPV_VULKAN_CONVERSION_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_VULKAN_CONVERSION_PATCH"
  git -C "$deps/mpv" apply "$MPV_VULKAN_CONVERSION_PATCH"
  [ -f "$MPV_VULKAN_SMART_PATCH" ] || die "missing MPV Android Vulkan smart-backend patch: $MPV_VULKAN_SMART_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_VULKAN_SMART_PATCH"
  git -C "$deps/mpv" apply "$MPV_VULKAN_SMART_PATCH"
  [ -f "$MPV_VULKAN_LEGACY_PATCH" ] || die "missing MPV Android Vulkan legacy-backend patch: $MPV_VULKAN_LEGACY_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_VULKAN_LEGACY_PATCH"
  git -C "$deps/mpv" apply "$MPV_VULKAN_LEGACY_PATCH"
  [ -f "$MPV_AIMAGEREADER_STABLE_PATCH" ] || die "missing MPV stable AImageReader flow patch: $MPV_AIMAGEREADER_STABLE_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_AIMAGEREADER_STABLE_PATCH"
  git -C "$deps/mpv" apply "$MPV_AIMAGEREADER_STABLE_PATCH"
  [ -f "$MPV_P2_GENERIC_UV_PATCH" ] || die "missing MPV P2 generic UV patch: $MPV_P2_GENERIC_UV_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_P2_GENERIC_UV_PATCH"
  git -C "$deps/mpv" apply "$MPV_P2_GENERIC_UV_PATCH"
  generate_mpv_shader_header compute \
    "$deps/mpv/video/out/hwdec/hwdec_aimagereader.comp" \
    "$deps/mpv/video/out/hwdec/hwdec_aimagereader_comp.h" \
    aimagereader_comp_spv
  generate_mpv_shader_header fragment \
    "$deps/mpv/video/out/hwdec/hwdec_aimagereader.frag" \
    "$deps/mpv/video/out/hwdec/hwdec_aimagereader_frag.h" \
    aimagereader_frag_spv
  python3 "$ROOT/scripts/verify_mpv_vulkan_shader_contract.py" \
    --mpv-source "$deps/mpv"
  [ -f "$MPV_MATROSKA_PATCH" ] || die "missing MPV Matroska segment patch: $MPV_MATROSKA_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_MATROSKA_PATCH"
  git -C "$deps/mpv" apply "$MPV_MATROSKA_PATCH"
  [ -f "$MPV_P1_PACKED_RGB10_PATCH" ] || die "missing MPV packed RGB10 patch: $MPV_P1_PACKED_RGB10_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_P1_PACKED_RGB10_PATCH"
  git -C "$deps/mpv" apply "$MPV_P1_PACKED_RGB10_PATCH"
  [ -f "$MPV_P1_EBML_DEFAULTS_PATCH" ] || die "missing MPV EBML defaults patch: $MPV_P1_EBML_DEFAULTS_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_P1_EBML_DEFAULTS_PATCH"
  git -C "$deps/mpv" apply "$MPV_P1_EBML_DEFAULTS_PATCH"
  [ -f "$MPV_P1_HLS_EDITION_PATCH" ] || die "missing MPV HLS edition patch: $MPV_P1_HLS_EDITION_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_P1_HLS_EDITION_PATCH"
  git -C "$deps/mpv" apply "$MPV_P1_HLS_EDITION_PATCH"
}

patch_dynamic_names() {
  local directory="$1"
  local file tmp patched
  for file in "$directory"/*.so; do
    tmp="$directory/.dynstr.$(basename "$file")"
    patched="$file.patched"
    "$OBJCOPY" --dump-section ".dynstr=$tmp" "$file"
    python3 - "$tmp" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
data = path.read_bytes()
replacements = {
    b"libavcodec.so": b"libmvcodec.so",
    b"libavdevice.so": b"libmvdevice.so",
    b"libavfilter.so": b"libmvfilter.so",
    b"libavformat.so": b"libmvformat.so",
    b"libavutil.so": b"libmvutil.so",
    b"libswresample.so": b"libmwresample.so",
    b"libswscale.so": b"libmwscale.so",
}
for old, new in replacements.items():
    if len(old) != len(new):
        raise SystemExit(f"replacement length mismatch: {old!r} -> {new!r}")
    data = data.replace(old, new)
path.write_bytes(data)
PY
    "$OBJCOPY" --update-section ".dynstr=$tmp" "$file" "$patched"
    mv "$patched" "$file"
    rm -f "$tmp"
  done
}

verify_directory() {
  local directory="$1"
  local required=(
    libc++_shared.so libmpv.so libmvcodec.so libmvdevice.so libmvfilter.so
    libmvformat.so libmvutil.so libmwresample.so libmwscale.so
  )
  local file dynamic name soname
  for name in "${required[@]}"; do
    [ -f "$directory/$name" ] || die "missing native output: $directory/$name"
  done
  for file in "$directory"/*.so; do
    dynamic="$("$READELF" -d "$file")"
    if printf '%s\n' "$dynamic" | grep -Eq 'Shared library: \[lib(av|sw).+\.so\]'; then
      die "unrenamed FFmpeg dependency in $file"
    fi
    if printf '%s\n' "$dynamic" | grep -Eq 'Shared library: \[lib(fontconfig|expat|xml2)\.so'; then
      die "font stack dependency must remain static in $file"
    fi
    name="$(basename "$file")"
    if [ "$name" != "libc++_shared.so" ]; then
      soname="$(printf '%s\n' "$dynamic" | sed -n 's/.*Library soname: \[\([^]]*\)\].*/\1/p')"
      [ "$soname" = "$name" ] || die "SONAME mismatch in $file: $soname"
    fi
  done
  dynamic="$("$READELF" -d "$directory/libmpv.so")"
  for name in libmvcodec.so libmvdevice.so libmvfilter.so libmvformat.so libmvutil.so libmwresample.so libmwscale.so libvulkan.so; do
    printf '%s\n' "$dynamic" | grep -Fq "Shared library: [$name]" || die "libmpv.so does not depend on $name"
  done
  local version_strings codec_strings format_strings
  version_strings="$(strings "$directory/libmpv.so")"
  codec_strings="$(strings "$directory/libmvcodec.so")"
  format_strings="$(strings "$directory/libmvformat.so")"
  grep -Fq "mpv v$MPV_VERSION" <<<"$version_strings" || die "unexpected MPV version in $directory/libmpv.so"
  grep -Fq "v$LIBPLACEBO_VERSION" <<<"$version_strings" || die "unexpected libplacebo version in $directory/libmpv.so"
  grep -Fq "WebHTV stream_cb controls enabled" <<<"$version_strings" || die "MPV stream_cb disc controls patch missing from $directory/libmpv.so"
  grep -Fq "Vulkan AImageReader backend:" <<<"$version_strings" || die "MPV Vulkan AImageReader backend missing from $directory/libmpv.so"
  grep -Fq "Using Vulkan YCbCr AHardwareBuffer sampling" <<<"$version_strings" || die "MPV direct Vulkan AHardwareBuffer sampling missing from $directory/libmpv.so"
  grep -Fq "Vulkan AImageReader sync-fd:" <<<"$version_strings" || die "MPV AImageReader sync-fd support missing from $directory/libmpv.so"
  grep -Fq "android-osd-wid" <<<"$version_strings" || die "MPV dual-Surface OSD option missing from $directory/libmpv.so"
  grep -Fq "Direct Dolby Vision initialization failed" <<<"$version_strings" || die "MPV direct Dolby Vision fallback missing from $directory/libmpv.so"
  grep -Fq "video output has no queue-safe EL decoder" <<<"$version_strings" || die "MPV Android Dolby Vision EL capability guard missing from $directory/libmpv.so"
  grep -Fq "DV7 HDR10 fallback: using MediaCodec base-layer decoder" <<<"$version_strings" || die "MPV Dolby Vision Profile 7 HDR10 direct base-layer fallback missing from $directory/libmpv.so"
  grep -Fq "DV7 HDR10 fallback: stripping EL/RPU before decoder." <<<"$version_strings" || die "MPV Dolby Vision Profile 7 demux base-layer filter missing from $directory/libmpv.so"
  grep -Fq "DV7 HDR10 fallback: synchronized decoder parameters to the HDR10 base layer." <<<"$version_strings" || die "MPV Dolby Vision Profile 7 filtered codec-parameter sync missing from $directory/libmpv.so"
  grep -Fq "DV7 HDR10 fallback: failed to produce base-layer packet." <<<"$version_strings" || die "MPV Dolby Vision Profile 7 filter failure guard missing from $directory/libmpv.so"
  grep -Fq "DV7 P8.1 conversion: using FFmpeg dovi_rpu BSF." <<<"$version_strings" || die "MPV Dolby Vision Profile 7 P8.1 conversion missing from $directory/libmpv.so"
  grep -Fq "DV7 P8.1 conversion: removed stale enhancement-layer configuration." <<<"$version_strings" || die "MPV Dolby Vision Profile 7 P8.1 stale enhancement-layer configuration guard missing from $directory/libmpv.so"
  if grep -Fq "Using device native output sample rate for passthrough compatibility" <<<"$version_strings"; then
    die "obsolete MPV AudioTrack passthrough native-rate patch present in $directory/libmpv.so"
  fi
  grep -Fq "Using 7.1 IEC61937 carrier mask for TrueHD" <<<"$version_strings" || die "MPV AudioTrack TrueHD channel-mask patch missing from $directory/libmpv.so"
  grep -Fq "Using 7.1 IEC61937 carrier mask for Android 12+ 8-channel stream" <<<"$version_strings" || die "MPV AudioTrack DTS-HD MA channel-mask path missing from $directory/libmpv.so"
  grep -Fq "WebHTV direct output accepts an optional Android OSD Surface" <<<"$version_strings" || die "MPV optional direct-output OSD patch missing from $directory/libmpv.so"
  grep -Fq "WebHTV timestamped MediaCodec output enabled" <<<"$version_strings" || die "MPV MediaCodec timestamped-release patch missing from $directory/libmpv.so"
  grep -Fq "MediaCodec VO drop timing" <<<"$version_strings" || die "MPV MediaCodec output timing diagnostics missing from $directory/libmpv.so"
  grep -Fq "WebHTV Vulkan auto backend prefers direct AHardwareBuffer sampling" <<<"$version_strings" || die "MPV Android Vulkan smart backend patch missing from $directory/libmpv.so"
  grep -Fq "WebHTV Vulkan auto uses a queue-safe four-output bounded-fence pool" <<<"$version_strings" || die "MPV Android Vulkan queue-safe conversion pool missing from $directory/libmpv.so"
  grep -Fq "CPU-precomputed UV transform" <<<"$version_strings" || die "MPV Android Vulkan low-power coordinate transform missing from $directory/libmpv.so"
  grep -Fq "Generic Vulkan conversion uses CPU-precomputed UV transform" <<<"$version_strings" || die "MPV Android Vulkan generic coordinate transform missing from $directory/libmpv.so"
  grep -Fq "Stable Vulkan conversion preserves Dolby Vision raw YUV component mapping" <<<"$version_strings" || die "MPV Android Vulkan stable Dolby Vision mapping missing from $directory/libmpv.so"
  grep -Fq "WebHTV Vulkan keeps AImage until the conversion fence completes" <<<"$version_strings" || die "MPV Android Vulkan stable AImage lifetime patch missing from $directory/libmpv.so"
  grep -Fq "WebHTV AImageReader uses stable release/acquire flow" <<<"$version_strings" || die "MPV Android stable AImageReader release/acquire patch missing from $directory/libmpv.so"
  grep -Fq "Using declared Matroska segment end for seek metadata." <<<"$version_strings" || die "MPV Matroska segment seek patch missing from $directory/libmpv.so"
  grep -Fq "libarcdav3a AV3A" <<<"$codec_strings" || die "FFmpeg AV3A decoder missing from $directory/libmvcodec.so"
  grep -Fq "failing hardware decode so the player can fall back" <<<"$codec_strings" || die "FFmpeg MediaCodec fallback patch missing from $directory/libmvcodec.so"
  grep -Fq "libaribcaption" <<<"$codec_strings" || die "FFmpeg ARIB caption decoder missing from $directory/libmvcodec.so"
  grep -Fq "Timed Text Markup Language subtitle" <<<"$codec_strings" || die "FFmpeg TTML decoder missing from $directory/libmvcodec.so"
  grep -Fq "MMT protocol over TLV packets" <<<"$format_strings" || die "FFmpeg MMT/TLV demuxer missing from $directory/libmvformat.so"
  grep -Fq "WebHTV proxy range offset accepted" <<<"$format_strings" || die "FFmpeg proxy range patch missing from $directory/libmvformat.so"
  grep -Fq "No usable fontconfig configuration file found, using fallback." <<<"$version_strings" || die "libass fontconfig provider missing from $directory/libmpv.so"
  if [ "$ENABLE_LIBCURL" -eq 1 ]; then
    grep -Fq "libcurl/$CURL_VERSION" <<<"$version_strings" || die "libcurl $CURL_VERSION missing from $directory/libmpv.so"
    grep -Fq "HTTP2" <<<"$version_strings" || die "HTTP/2 support missing from $directory/libmpv.so"
  fi
}

verify_curl_ffmpeg_compat() {
  [ "$ENABLE_LIBCURL" -eq 1 ] || return
  python3 - "$BUILDSCRIPTS/deps/ffmpeg/libavformat/version_major.h" \
    "$BUILDSCRIPTS/deps/ffmpeg/libavformat/version.h" <<'PY'
import re
import sys
from pathlib import Path

text = "\n".join(Path(path).read_text(encoding="utf-8") for path in sys.argv[1:])
values = {}
for key in ("MAJOR", "MINOR", "MICRO"):
    match = re.search(rf"LIBAVFORMAT_VERSION_{key}\s+(\d+)", text)
    if not match:
        raise SystemExit(f"missing libavformat {key.lower()} version")
    values[key] = int(match.group(1))

version = (values["MAJOR"], values["MINOR"], values["MICRO"])
safe = version[0] > 62 or (
    version[0] == 62 and (
        version[1] >= 15 or
        (version[1] == 12 and version[2] >= 102) or
        (version[1] == 3 and version[2] >= 103)
    )
)
if not safe:
    raise SystemExit(
        "libcurl requires the nested AVIO cleanup fix; "
        f"libavformat {version[0]}.{version[1]}.{version[2]} is too old"
    )
PY
}

stage_abi() {
  local arch="$1"
  local abi="$2"
  local prefix_name="$3"
  local cxx_abi="$4"
  local source="$BUILDSCRIPTS/prefix/$prefix_name/lib"
  local output="$WORK_DIR/output/$abi"
  local assets

  case "$abi" in
    arm64-v8a) assets="$ROOT/app/src/arm64_v8a/assets/mpv-libs/arm64-v8a" ;;
    armeabi-v7a) assets="$ROOT/app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a" ;;
  esac

  rm -rf "$output"
  mkdir -p "$output"
  for name in libmpv.so libavcodec.so libavdevice.so libavfilter.so libavformat.so libavutil.so libswresample.so libswscale.so; do
    [ -f "$source/$name" ] || die "build output missing: $source/$name"
    cp "$source/$name" "$output/$name"
  done
  patch_dynamic_names "$output"
  mv "$output/libavcodec.so" "$output/libmvcodec.so"
  mv "$output/libavdevice.so" "$output/libmvdevice.so"
  mv "$output/libavfilter.so" "$output/libmvfilter.so"
  mv "$output/libavformat.so" "$output/libmvformat.so"
  mv "$output/libavutil.so" "$output/libmvutil.so"
  mv "$output/libswresample.so" "$output/libmwresample.so"
  mv "$output/libswscale.so" "$output/libmwscale.so"
  cp "$TOOLCHAIN/sysroot/usr/lib/$cxx_abi/libc++_shared.so" "$output/libc++_shared.so"
  "$STRIP" --strip-unneeded "$output"/*.so
  chmod 644 "$output"/*.so
  verify_directory "$output"

  if [ "$INSTALL_ASSETS" -eq 1 ]; then
    log "Installing $abi native assets"
    mkdir -p "$assets"
    cp "$output"/*.so "$assets/"
    verify_directory "$assets"
  fi
  log "$abi output ready: $output"
}

build_abi() {
  local abi="$1"
  local arch prefix_name cxx_abi
  case "$abi" in
    arm64-v8a)
      arch=arm64
      prefix_name=arm64
      cxx_abi=aarch64-linux-android
      ;;
    armeabi-v7a)
      arch=armv7l
      prefix_name=armv7l
      cxx_abi=arm-linux-androideabi
      ;;
  esac

  log "Building pinned MPV native stack for $abi"
  if [ "$INCREMENTAL" -eq 0 ]; then
    rm -rf "$BUILDSCRIPTS/prefix/$prefix_name"
  fi
  export cores="$JOBS"
  export android_api="$ANDROID_API_LEVEL"
  export WEBHTV_ANDROID_API_LEVEL="$ANDROID_API_LEVEL"
  local targets=(
    libiconv uchardet bzip2 xz zstd mbedtls dav1d libxml2 freetype2
    libaribcaption ffmpeg fontconfig fribidi harfbuzz unibreak libass lua
    shaderc libplacebo
  )
  if [ "$ENABLE_LIBCURL" -eq 1 ]; then
    targets+=(nghttp2 curl)
    export WEBHTV_MPV_LIBCURL=enabled
  else
    export WEBHTV_MPV_LIBCURL=auto
  fi
  targets+=(libbluray libarchive libdvdread libdvdnav rubberband)
  targets+=(mpv)
  local target
  for target in "${targets[@]}"; do
    if [ "$INCREMENTAL" -eq 1 ]; then
      bash "$BUILDSCRIPTS/buildall.sh" -n --arch "$arch" "$target"
    else
      bash "$BUILDSCRIPTS/buildall.sh" -n --clean --arch "$arch" "$target"
    fi
    case "$target" in
      libiconv) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libiconv.a" ] ;;
      uchardet) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libuchardet.a" ] ;;
      bzip2) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libbz2.a" ] ;;
      xz) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/liblzma.a" ] ;;
      zstd) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libzstd.a" ] ;;
      mbedtls) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libmbedtls.a" ] ;;
      dav1d) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libdav1d.a" ] ;;
      libxml2) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libxml2.a" ] ;;
      freetype2) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libfreetype.a" ] ;;
      libaribcaption) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libaribcaption.a" ] ;;
      ffmpeg) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libavcodec.so" ] ;;
      fontconfig) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libfontconfig.a" ] ;;
      fribidi) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libfribidi.a" ] ;;
      harfbuzz) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libharfbuzz.a" ] ;;
      unibreak) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libunibreak.a" ] ;;
      libass) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libass.a" ] ;;
      lua) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/liblua.a" ] ;;
      shaderc) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libshaderc.a" ] ;;
      libplacebo) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libplacebo.a" ] ;;
      nghttp2) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libnghttp2.a" ] ;;
      curl) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libcurl.a" ] ;;
      libbluray) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libbluray.a" ] ;;
      libarchive) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libarchive.a" ] ;;
      libdvdread) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libdvdread.a" ] ;;
      libdvdnav) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libdvdnav.a" ] ;;
      rubberband) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/librubberband.a" ] ;;
      mpv) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libmpv.so" ] ;;
    esac || die "$target did not produce its expected $abi output"
    [ "$target" != "ffmpeg" ] || verify_curl_ffmpeg_compat
  done
  stage_abi "$arch" "$abi" "$prefix_name" "$cxx_abi"
}

log "Using lock file $LOCK_FILE"
log "Using Android NDK $NDK_VERSION at $NDK_ROOT"
if [ "$STAGE_ONLY" -eq 1 ]; then
  case "$ABI" in
    arm64-v8a) stage_abi arm64 arm64-v8a arm64 aarch64-linux-android ;;
    armeabi-v7a) stage_abi armv7l armeabi-v7a armv7l arm-linux-androideabi ;;
    all)
      stage_abi arm64 arm64-v8a arm64 aarch64-linux-android
      stage_abi armv7l armeabi-v7a armv7l arm-linux-androideabi
      ;;
  esac
  log "Existing MPV native output staged and verified"
  exit 0
fi
prepare_python_tools
prepare_framework
prepare_sources

if [ "$PREPARE_ONLY" -eq 1 ]; then
  log "All sources are downloaded and pinned under $WORK_DIR"
  exit 0
fi

case "$ABI" in
  arm64-v8a) build_abi arm64-v8a ;;
  armeabi-v7a) build_abi armeabi-v7a ;;
  all)
    build_abi arm64-v8a
    build_abi armeabi-v7a
    ;;
esac

log "MPV native build completed"
if [ "$INSTALL_ASSETS" -eq 1 ]; then
  printf '%s\n' "The committed libplayer.so was preserved. Build the app normally with Gradle."
else
  printf '%s\n' "Review output under $WORK_DIR/output, then rerun with --install to update app assets."
fi
