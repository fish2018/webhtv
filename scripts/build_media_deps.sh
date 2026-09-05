#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/build_media_deps.sh [--clean] [--with-nextlib|--nextlib-only] [--use-aliyun-mirrors]

Builds the locked FongMi Media3 artifacts into third_party/maven so the app can
depend on normal Maven coordinates instead of embedding external source trees.

Options:
  --clean          Remove generated third_party/maven before publishing.
  --with-nextlib   Also build and publish the locked nextlib FFmpeg extension.
                  This rebuilds FFmpeg/libarcdav3a for both Android ARM ABIs.
  --nextlib-only   Build and publish only nextlib-media3ext.
  --use-aliyun-mirrors
                  Add temporary Aliyun Gradle/Google/Maven mirrors to the generated
                  Media3 checkout. Useful when Plugin Portal or Google Maven is
                  unavailable; the mirror changes are never included in the AARs.
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
LOCK_FILE="$ROOT_DIR/third_party/media-lock.json"
THIRD_PARTY_DIR="$ROOT_DIR/third_party"
SOURCE_DIR="$THIRD_PARTY_DIR/sources"
LOCAL_MAVEN="$THIRD_PARTY_DIR/maven"
MEDIA_DIR="$SOURCE_DIR/media"
NEXTLIB_DIR="$SOURCE_DIR/nextlib"

CLEAN=0
WITH_NEXTLIB=0
BUILD_MEDIA=1
USE_ALIYUN_MIRRORS=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --clean)
      CLEAN=1
      ;;
    --with-nextlib)
      WITH_NEXTLIB=1
      ;;
    --nextlib-only)
      WITH_NEXTLIB=1
      BUILD_MEDIA=0
      ;;
    --use-aliyun-mirrors)
      USE_ALIYUN_MIRRORS=1
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

json_get() {
  local object="$1"
  local field="$2"
  awk -v object="\"$object\"" -v field="\"$field\"" '
    $0 ~ object { in_object = 1; next }
    in_object && $0 ~ /^ *}/ { in_object = 0 }
    in_object && $0 ~ field {
      line = $0
      sub(/^.*: *"/, "", line)
      sub(/".*$/, "", line)
      print line
      exit
    }
  ' "$LOCK_FILE"
}

require_value() {
  local name="$1"
  local value="$2"
  if [[ -z "$value" ]]; then
    echo "Missing $name in $LOCK_FILE" >&2
    exit 1
  fi
}

read_local_property() {
  local key="$1"
  local file="$ROOT_DIR/local.properties"
  [[ -f "$file" ]] || return 0
  awk -F= -v key="$key" '$1 == key { print substr($0, length(key) + 2); exit }' "$file"
}

resolve_android_sdk() {
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$sdk" ]]; then
    sdk="$(read_local_property sdk.dir)"
  fi
  if [[ -z "$sdk" || ! -d "$sdk/platforms" ]]; then
    echo "Android SDK not found. Set ANDROID_HOME/ANDROID_SDK_ROOT or sdk.dir in local.properties." >&2
    exit 1
  fi
  printf '%s\n' "$sdk"
}

version_catalog_value() {
  local key="$1"
  awk -F'"' -v key="$key" '$1 ~ "^" key " *= *" { print $2; exit }' "$ROOT_DIR/gradle/libs.versions.toml"
}

platform_api_exists() {
  local sdk="$1"
  local api="$2"
  [[ -d "$sdk/platforms/android-$api" ]] && return 0
  local platform
  for platform in "$sdk"/platforms/android-*; do
    [[ -f "$platform/source.properties" ]] || continue
    if grep -Eq "^AndroidVersion.ApiLevel=${api}(\\.0)?$" "$platform/source.properties"; then
      return 0
    fi
  done
  return 1
}

patch_gradle_number_property() {
  local file="$1"
  local key="$2"
  local value="$3"
  perl -0pi -e "s/${key} = [0-9]+/${key} = ${value}/" "$file"
}

patch_gradle_string_property() {
  local file="$1"
  local key="$2"
  local value="$3"
  perl -0pi -e "s/${key} = '[^']+'/${key} = '${value}'/" "$file"
}

patch_toml_string_value() {
  local file="$1"
  local key="$2"
  local value="$3"
  perl -0pi -e "s/${key} = \"[^\"]+\"/${key} = \"${value}\"/" "$file"
}

clone_or_update() {
  local repo="$1"
  local branch="$2"
  local commit="$3"
  local dir="$4"
  mkdir -p "$(dirname "$dir")"
  if [[ -d "$dir/.git" ]]; then
    if git -C "$dir" cat-file -e "$commit^{commit}" 2>/dev/null; then
      git -C "$dir" checkout --force --detach "$commit"
      git -C "$dir" clean -fdx
      return 0
    fi
    echo "Fetching $repo"
    git -C "$dir" fetch --tags --prune origin "$branch"
  else
    echo "Cloning $repo"
    git clone "$repo" "$dir"
  fi
  git -C "$dir" fetch --tags origin "$branch"
  git -C "$dir" checkout --force --detach "$commit"
  git -C "$dir" clean -fdx
}

latest_installed_build_tools() {
  local sdk="$1"
  local build_tools_dir="$sdk/build-tools"
  [[ -d "$build_tools_dir" ]] || return 0
  find "$build_tools_dir" -maxdepth 1 -mindepth 1 -type d -exec basename {} \; | sort -V | tail -n 1
}

insert_line_after_android_block() {
  local file="$1"
  local line="$2"
  local tmp="$file.tmp"
  awk -v insert_line="$line" '{ print; if ($0 == "android {") print insert_line }' "$file" > "$tmp"
  mv "$tmp" "$file"
}

add_media_jar_dependency() {
  local dependency="$1"
  local file="$MEDIA_DIR/missing_aar_type_workaround.gradle"
  local tmp="$file.tmp"
  if grep -q "\"$dependency\"" "$file"; then
    return 0
  fi
  awk -v dep="$dependency" '{ print; if ($0 ~ /"com.google.guava:guava",/) print "        \"" dep "\"," }' "$file" > "$tmp"
  mv "$tmp" "$file"
}

patch_media_release_version() {
  local version="$1"
  if [[ -f "$MEDIA_DIR/gradle/libs.versions.toml" ]]; then
    patch_toml_string_value "$MEDIA_DIR/gradle/libs.versions.toml" "releaseVersion" "$version"
  else
    patch_gradle_string_property "$MEDIA_DIR/constants.gradle" "releaseVersion" "$version"
  fi
}

patch_media_build_tools() {
  if [[ ! -f "$MEDIA_DIR/common_config.gradle" ]]; then
    return 0
  fi
  local build_tools
  build_tools="$(latest_installed_build_tools "$ANDROID_HOME")"
  if [[ -z "$build_tools" ]]; then
    echo "No Android SDK Build Tools found under $ANDROID_HOME/build-tools." >&2
    exit 1
  fi
  insert_line_after_android_block "$MEDIA_DIR/common_config.gradle" "    buildToolsVersion \"$build_tools\""
  echo "Building locked Media3 with installed Build Tools $build_tools."
}

patch_media_pom_workaround() {
  if [[ ! -f "$MEDIA_DIR/missing_aar_type_workaround.gradle" ]]; then
    return 0
  fi
  add_media_jar_dependency "com.googlecode.juniversalchardet:juniversalchardet"
  add_media_jar_dependency "com.hierynomus:smbj"
  add_media_jar_dependency "org.brotli:dec"
}

apply_media_patches() {
  local patch_dir="$THIRD_PARTY_DIR/patches"
  local patch_file
  [[ -d "$patch_dir" ]] || return 0
  # Keep dependency and release order explicit instead of relying on filesystem glob order.
  local patches=(
    "$patch_dir/media3-danmaku-live.patch"
    "$patch_dir/media3-dolby-vision-matroska.patch"
    "$patch_dir/media3-upstream-playback-fixes-2026-08.patch"
    "$patch_dir/media3-exo-hdr-parser-safety.patch"
    "$patch_dir/media3-deferred-cues.patch"
    "$patch_dir/media3-exo-pixel-eac3-joc-guard.patch"
    "$patch_dir/media3-exo-dts-14bit-frame-size.patch"
    "$patch_dir/media3-exo-subtitle-byte-safety.patch"
    "$patch_dir/media3-exo-cue-data-contract.patch"
    "$patch_dir/media3-exo-bounded-cache-writer.patch"
    "$patch_dir/media3-exo-iso-reader-safety.patch"
    "$patch_dir/media3-exo-iso-multi-extent.patch"
    "$patch_dir/media3-precache-hls-safety.patch"
  )
  for patch_file in "${patches[@]}"; do
    [[ -f "$patch_file" ]] || continue
    echo "Applying Media3 patch $(basename "$patch_file")"
    apply_media_patch_lf "$patch_file" "$MEDIA_DIR" --unidiff-zero
  done
}

apply_media_patch_lf() {
  local patch_file="$1"
  local target_dir="$2"
  shift 2
  local apply_args=("$@")
  # git apply on Windows cannot parse CRLF-formatted patches whose context
  # lines contain a bare CR; GNU patch strips trailing CRs automatically.
  if git -C "$target_dir" apply --check "${apply_args[@]}" "$patch_file" 2>/dev/null; then
    git -C "$target_dir" apply "${apply_args[@]}" "$patch_file"
  else
    patch -p1 --dry-run -d "$target_dir" < "$patch_file" >/dev/null 2>&1 \
      && patch -p1 -d "$target_dir" < "$patch_file"
  fi
}

apply_nextlib_patches() {
  local patch_file
  local patches=(
    "$THIRD_PARTY_DIR/patches/nextlib-ffmpeg-soft-load-shedding.patch"
    "$THIRD_PARTY_DIR/patches/nextlib-av3a.patch"
  )
  for patch_file in "${patches[@]}"; do
    if [[ ! -f "$patch_file" ]]; then
      echo "Missing nextlib patch: $patch_file" >&2
      exit 1
    fi
    echo "Applying nextlib patch $(basename "$patch_file")"
    apply_media_patch_lf "$patch_file" "$NEXTLIB_DIR"
  done
}

apply_media_build_mirrors() {
  if [[ "$USE_ALIYUN_MIRRORS" != "1" ]]; then
    return 0
  fi
  local patch_file="$THIRD_PARTY_DIR/patches/gradle-media3-aliyun-mirrors.patch"
  if [[ ! -f "$patch_file" ]]; then
    echo "Missing Media3 Gradle mirror patch: $patch_file" >&2
    exit 1
  fi
  echo "Applying temporary Media3 Gradle Aliyun mirrors"
  apply_media_patch_lf "$patch_file" "$MEDIA_DIR"
}

prepare_android_env() {
  ANDROID_HOME="$(resolve_android_sdk)"
  export ANDROID_HOME
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export ANDROID_USER_HOME="$ROOT_DIR/.gradle/android-user-home"
  unset ANDROID_SDK_HOME
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle/media-deps}"
  mkdir -p "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
}

prepare_media_compile_sdk() {
  local media_compile
  local app_compile
  app_compile="$(version_catalog_value compileSdk)"
  if [[ -f "$MEDIA_DIR/gradle/libs.versions.toml" ]]; then
    media_compile="$(awk -F'"' '$1 ~ /^compileSdkVersion *= */ { print $2; exit }' "$MEDIA_DIR/gradle/libs.versions.toml")"
  else
    media_compile="$(awk -F'= *' '/compileSdkVersion/ { print $2; exit }' "$MEDIA_DIR/constants.gradle" | tr -dc '0-9')"
  fi
  if [[ -z "$media_compile" ]]; then
    echo "Unable to read Media3 compileSdkVersion." >&2
    exit 1
  fi
  if platform_api_exists "$ANDROID_HOME" "$media_compile"; then
    return 0
  fi
  if [[ -n "$app_compile" && "$app_compile" =~ ^[0-9]+$ ]] && platform_api_exists "$ANDROID_HOME" "$app_compile"; then
    echo "Android SDK platform $media_compile is not installed; building locked Media3 with project compileSdk $app_compile."
    if [[ -f "$MEDIA_DIR/gradle/libs.versions.toml" ]]; then
      patch_toml_string_value "$MEDIA_DIR/gradle/libs.versions.toml" "compileSdkVersion" "$app_compile"
    else
      patch_gradle_number_property "$MEDIA_DIR/constants.gradle" "compileSdkVersion" "$app_compile"
    fi
    return 0
  fi
  echo "Android SDK platform $media_compile is required to build FongMi/media." >&2
  echo "Install platforms;android-$media_compile or install the project compileSdk platform $app_compile." >&2
  exit 1
}

prepare_nextlib_compile_sdk() {
  local app_compile
  app_compile="$(version_catalog_value compileSdk)"
  if [[ -n "$app_compile" && "$app_compile" =~ ^[0-9]+$ ]] && platform_api_exists "$ANDROID_HOME" "$app_compile"; then
    patch_toml_string_value "$NEXTLIB_DIR/gradle/libs.versions.toml" "androidCompileSdk" "$app_compile"
  fi
}

publish_media() {
  local media_repo media_branch media_commit media_version
  media_repo="$(json_get fongmi_media repo)"
  media_branch="$(json_get fongmi_media branch)"
  media_commit="$(json_get fongmi_media commit)"
  media_version="$(json_get fongmi_media version)"
  require_value fongmi_media.repo "$media_repo"
  require_value fongmi_media.branch "$media_branch"
  require_value fongmi_media.commit "$media_commit"
  require_value fongmi_media.version "$media_version"

  clone_or_update "$media_repo" "$media_branch" "$media_commit" "$MEDIA_DIR"
  apply_media_patches
  apply_media_build_mirrors
  patch_media_release_version "$media_version"
  patch_media_pom_workaround
  prepare_media_compile_sdk
  patch_media_build_tools

  local modules=(
    lib-common
    lib-container
    lib-database
    lib-datasource
    lib-datasource-okhttp
    lib-datasource-rtmp
    lib-decoder
    lib-effect
    lib-exoplayer
    lib-exoplayer-dash
    lib-exoplayer-hls
    lib-exoplayer-rtsp
    lib-exoplayer-smoothstreaming
    lib-extractor
    lib-session
    lib-ui
    lib-ui-danmaku
  )
  local tasks=()
  local module
  for module in "${modules[@]}"; do
    tasks+=(":$module:publishReleasePublicationToMavenRepository")
  done

  local publish_repo
  publish_repo="$(mktemp -d "$ROOT_DIR/.gradle/media-maven-publish.XXXXXX")"
  echo "Publishing FongMi/media $media_version to staging repository $publish_repo"
  if ! (cd "$MEDIA_DIR" && ./gradlew --no-daemon --console=plain \
      -Pkotlin.compiler.execution.strategy=in-process \
      -PmavenRepo="$publish_repo" \
      -PreleaseVersion="$media_version" \
      "${tasks[@]}"); then
    echo "Media3 publishing failed; staged output retained at $publish_repo" >&2
    return 1
  fi
  mkdir -p "$LOCAL_MAVEN"
  cp -R "$publish_repo"/. "$LOCAL_MAVEN"/
  rm -rf "$publish_repo"
  echo "Installed complete Media3 publication into $LOCAL_MAVEN"
}

verify_nextlib_aar() {
  local aar="$1"
  local temp_dir
  local abi
  [[ -f "$aar" ]] || {
    echo "Missing nextlib AAR: $aar" >&2
    return 1
  }
  temp_dir="$(mktemp -d "$ROOT_DIR/.gradle/nextlib-aar-verify.XXXXXX")"
  unzip -q "$aar" -d "$temp_dir"
  for abi in arm64-v8a armeabi-v7a; do
    [[ -f "$temp_dir/jni/$abi/libmedia3ext.so" ]] || {
      echo "Missing $abi libmedia3ext.so in $aar" >&2
      return 1
    }
    [[ -f "$temp_dir/jni/$abi/libavcodec.so" ]] || {
      echo "Missing $abi libavcodec.so in $aar" >&2
      return 1
    }
    if ! grep -aFq "libarcdav3a AV3A" "$temp_dir/jni/$abi/libavcodec.so"; then
      echo "Missing $abi libarcdav3a decoder marker in $aar" >&2
      return 1
    fi
    if ! grep -aFq "AV3A Audio Vivid" "$temp_dir/jni/$abi/libavcodec.so"; then
      echo "Missing $abi AV3A Audio Vivid marker in $aar" >&2
      return 1
    fi
  done
  rm -rf "$temp_dir"
}

publish_nextlib() {
  local repo branch commit version
  repo="$(json_get nextlib repo)"
  branch="$(json_get nextlib branch)"
  commit="$(json_get nextlib commit)"
  version="$(json_get nextlib version)"
  require_value nextlib.repo "$repo"
  require_value nextlib.branch "$branch"
  require_value nextlib.commit "$commit"
  require_value nextlib.version "$version"

  clone_or_update "$repo" "$branch" "$commit" "$NEXTLIB_DIR"
  apply_nextlib_patches
  prepare_nextlib_compile_sdk
  if ! grep -Fq "version = \"$version\"" "$NEXTLIB_DIR/build.gradle.kts"; then
    echo "nextlib patch version does not match lock version $version" >&2
    exit 1
  fi

  local publish_repo
  local artifact_dir
  local aar
  publish_repo="$(mktemp -d "$ROOT_DIR/.gradle/nextlib-maven-publish.XXXXXX")"
  echo "Building nextlib-media3ext $version with FFmpeg/libarcdav3a"
  if ! (cd "$NEXTLIB_DIR" && ./gradlew --no-daemon --console=plain \
      -Dmaven.repo.local="$publish_repo" \
      :media3ext:publishMavenPublicationToMavenLocal); then
    echo "nextlib publishing failed; staged output retained at $publish_repo" >&2
    return 1
  fi
  artifact_dir="$publish_repo/io/github/anilbeesetti/nextlib-media3ext/$version"
  aar="$artifact_dir/nextlib-media3ext-$version.aar"
  verify_nextlib_aar "$aar"
  mkdir -p "$LOCAL_MAVEN/io/github/anilbeesetti"
  cp -R "$publish_repo"/io/github/anilbeesetti/nextlib-media3ext "$LOCAL_MAVEN/io/github/anilbeesetti/"
  rm -rf "$publish_repo"
  echo "Installed verified nextlib-media3ext $version into $LOCAL_MAVEN"
}

prepare_android_env
mkdir -p "$THIRD_PARTY_DIR"
if [[ "$CLEAN" == "1" ]]; then
  if [[ "$BUILD_MEDIA" == "1" ]]; then
    echo "Cleaning $LOCAL_MAVEN"
    rm -rf "$LOCAL_MAVEN"
  else
    echo "Cleaning local nextlib publication"
    rm -rf "$LOCAL_MAVEN/io/github/anilbeesetti/nextlib-media3ext"
  fi
fi
mkdir -p "$LOCAL_MAVEN"

if [[ "$BUILD_MEDIA" == "1" ]]; then
  publish_media
fi
if [[ "$WITH_NEXTLIB" == "1" ]]; then
  publish_nextlib
fi

echo "Done. Local media dependencies are available under $LOCAL_MAVEN"
