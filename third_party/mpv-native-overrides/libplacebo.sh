#!/bin/bash -e

. ../../include/path.sh

build=_build$ndk_suffix

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf "$build"
	exit 0
else
	exit 255
fi

unset CC CXX
if [ ! -f "$prefix_dir/lib/libshaderc.a" ]; then
	echo "shaderc dependency is missing: $prefix_dir/lib/libshaderc.a" >&2
	exit 1
fi
export CFLAGS="-I$prefix_dir/include ${CFLAGS:-}"
export CXXFLAGS="-I$prefix_dir/include ${CXXFLAGS:-}"
export LDFLAGS="-L$prefix_dir/lib ${LDFLAGS:-}"
meson setup "$build" --cross-file "$prefix_dir/crossfile.txt" \
	-Dopengl=enabled -Dvulkan=enabled \
	-Dshaderc=enabled -Dglslang=disabled \
	-Ddemos=false

config_header="$build/src/include/libplacebo/config.h"
for backend in OPENGL VULKAN; do
	grep -Eq "^#define PL_HAVE_${backend} 1([[:space:]]|$)" "$config_header" || {
		echo "libplacebo backend PL_HAVE_${backend}=1 is missing." >&2
		exit 1
	}
done

ninja -C "$build" -j"$cores"
DESTDIR="$prefix_dir" ninja -C "$build" install

python3 - "$prefix_dir/lib/pkgconfig/libplacebo.pc" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
lines = []
for line in text.splitlines():
    if line.startswith("Libs:"):
        words = line.split()
        for library in ("-lshaderc", "-lc++"):
            if library not in words:
                line += f" {library}"
    lines.append(line)
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
