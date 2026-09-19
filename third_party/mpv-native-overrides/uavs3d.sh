#!/bin/bash -e

. ../../include/path.sh

case "$1" in
  build) ;;
  clean) exit 0 ;;
  *) exit 255 ;;
esac

case "$ndk_triple" in
  aarch64*) avs3_abi=arm64-v8a ;;
  arm*) avs3_abi=armeabi-v7a ;;
  *) echo "Unsupported AVS3 ABI: $ndk_triple" >&2; exit 1 ;;
esac

bash "${WEBHTV_ROOT:?WebHTV build root is required}/scripts/build_avs3_native.sh" \
  --abi "$avs3_abi" --ndk "$DIR/sdk/android-ndk-$v_ndk" \
  --prefix "$prefix_dir" --work-dir "$PWD/_build$ndk_suffix" --jobs "$cores"
