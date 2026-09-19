#!/bin/bash -e

. ../../include/path.sh
. ../../include/depinfo.sh
. ../../include/cmake-android.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf _build$ndk_suffix _build_arcdav3a$ndk_suffix
	exit 0
else
	exit 255
fi

mkdir -p _build$ndk_suffix
cd _build$ndk_suffix

cpu=armv7-a
[[ "$ndk_triple" == "aarch64"* ]] && cpu=armv8-a
[[ "$ndk_triple" == "x86_64"* ]] && cpu=generic
[[ "$ndk_triple" == "i686"* ]] && cpu="i686 --disable-asm"

cpuflags=
[[ "$ndk_triple" == "arm"* ]] && cpuflags="$cpuflags -mfpu=neon -mcpu=cortex-a8"

audio_filters=(acompressor alimiter equalizer pan silenceremove stereotools volume)
audio_filter_args=()
for filter in "${audio_filters[@]}"; do
	audio_filter_args+=(--enable-filter="$filter")
done

if ! grep -q -- "--enable-libarcdav3a" ../configure; then
	echo "FFmpeg source does not contain libarcdav3a support. Update the pinned FFmpeg branch." >&2
	exit 1
fi

av3a_source="../dependency/avs3a"
av3a_build="../_build_arcdav3a$ndk_suffix"
if [ ! -f "$av3a_source/CMakeLists.txt" ]; then
	echo "FFmpeg source does not contain dependency/avs3a. Update the pinned FFmpeg branch." >&2
	exit 1
fi
android_cmake_setup "$av3a_source" "$av3a_build" -DBUILD_SHARED_LIBS=OFF
android_cmake_build "$av3a_build"
android_cmake_install "$av3a_build"

args=(
	--target-os=android --enable-cross-compile
	--cross-prefix=$ndk_triple- --cc=$CC --pkg-config=pkg-config --nm=llvm-nm
	--arch=${ndk_triple%%-*} --cpu=$cpu
	--extra-cflags="-I$prefix_dir/include $cpuflags" --extra-ldflags="-L$prefix_dir/lib"

	--enable-{jni,mediacodec,mbedtls,libdav1d,libxml2,libaribcaption,libarcdav3a,libuavs3d} --disable-vulkan
	--enable-decoder=avs3_mediacodec
	--disable-static --enable-shared --enable-{gpl,version3}

	# disable unneeded parts
	--disable-{stripping,doc,programs}
	# to keep the build lean we disable some feature quite aggressively:
	# - muxers, encoders: mpv-android does not have any way to use these
	# - devices: no practical use on Android
	--disable-{muxers,encoders,devices}
	# useful to taking screenshots
	--enable-encoder=mjpeg,png
	# required by TV audio settings through mpv's lavfi audio filter chain
	"${audio_filter_args[@]}"
	# useful for the `dump-cache` command and mpv audio passthrough
	--enable-muxer=mov,matroska,mpegts,spdif
)
../configure "${args[@]}"

if ! grep -Fqx '#define CONFIG_AVS3_MEDIACODEC_DECODER 1' config_components.h; then
	echo "Required AVS3 MediaCodec decoder is missing from the FFmpeg build." >&2
	exit 1
fi

make -j$cores
make DESTDIR="$prefix_dir" install
