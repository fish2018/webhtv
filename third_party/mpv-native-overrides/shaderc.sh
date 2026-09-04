#!/bin/bash -e

. ../../include/path.sh
. ../../include/depinfo.sh

: "${android_api:=24}"
: "${prefix_name:=${ndk_suffix#_}}"

shaderc_dir="$DIR/deps/shaderc"

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf "$shaderc_dir/obj" "$shaderc_dir/libs"
	rm -rf "$prefix_dir/include/shaderc"
	rm -f "$prefix_dir/lib/libshaderc.a" "$prefix_dir/lib/pkgconfig/shaderc.pc"
	exit 0
else
	exit 255
fi

case "$prefix_name" in
	armv7l)
	android_abi=armeabi-v7a
	;;
	arm64)
	android_abi=arm64-v8a
	;;
	x86)
	android_abi=x86
	;;
	x86_64)
	android_abi=x86_64
	;;
	*)
		echo "Invalid architecture: $prefix_name" >&2
		exit 1
	;;
esac

ndk_dir="$DIR/sdk/android-ndk-${v_ndk}"
shaderc_lib="$shaderc_dir/libs/c++_static/$android_abi/libshaderc.a"
ndk_build="$ndk_dir/ndk-build"

if [ ! -d "$shaderc_dir" ]; then
	echo "Staged shaderc sources not found: $shaderc_dir" >&2
	exit 1
fi
if [ ! -x "$ndk_build" ]; then
	echo "ndk-build not found: $ndk_build" >&2
	exit 1
fi
if [ ! -f "$shaderc_dir/Android.mk" ]; then
	echo "Staged shaderc Android.mk not found: $shaderc_dir/Android.mk" >&2
	exit 1
fi

# NDK r29 ships shaderc sources below the SDK installation. That directory can
# be read-only (and is outside the build cache), while ndk-build writes obj/ and
# libs/ beside Android.mk. Build the staged copy under buildscripts/deps instead.
"$ndk_build" -C "$shaderc_dir" \
	NDK_PROJECT_PATH=. \
	APP_BUILD_SCRIPT=Android.mk \
	APP_PLATFORM=android-$android_api \
	APP_STL=c++_static \
	APP_ABI="$android_abi" \
	libshaderc_combined \
	-j$cores
if [ ! -f "$shaderc_lib" ]; then
	echo "shaderc build output not found: $shaderc_lib" >&2
	exit 1
fi

mkdir -p "$prefix_dir/include" "$prefix_dir/lib/pkgconfig"
rm -rf "$prefix_dir/include/shaderc"
cp -R "$shaderc_dir/libshaderc/include/shaderc" "$prefix_dir/include/"
cp "$shaderc_lib" "$prefix_dir/lib/libshaderc.a"

cat >"$prefix_dir/lib/pkgconfig/shaderc.pc" <<SHADERCPC
prefix=/usr/local
libdir=\${prefix}/lib
includedir=\${prefix}/include

Name: shaderc
Description: Android NDK shaderc static library
Version: 2024.0
Libs: -L\${libdir} -lshaderc
Cflags: -I\${includedir}
SHADERCPC
