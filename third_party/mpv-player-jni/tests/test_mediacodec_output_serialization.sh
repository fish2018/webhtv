#!/usr/bin/env bash
set -euo pipefail
test_dir="$(cd "$(dirname "$0")" && pwd)"
ffmpeg_source="${1:?usage: bash test_mediacodec_output_serialization.sh <FFmpeg source directory>}"
test_work="$(mktemp -d /tmp/p9-mediacodec-lock.XXXXXX)"
awk '
    /^int av_mediacodec_release_buffer_status\(/ { emit = 1 }
    /^#else/ { exit }
    emit { print }
' "$ffmpeg_source/libavcodec/mediacodec.c" > "$test_work/mediacodec_release_under_test.h"
awk '
    /^static void mediacodec_buffer_release\(/ { emit = 1 }
    /^static int mediacodec_dec_flush_codec\(/ { emit = 1 }
    /^int ff_mediacodec_dec_close\(/ { emit = 1 }
    emit { print }
    emit && /^}/ { emit = 0 }
' "$ffmpeg_source/libavcodec/mediacodecdec_common.c" > "$test_work/mediacodec_lifecycle_under_test.h"
test -s "$test_work/mediacodec_release_under_test.h"
test -s "$test_work/mediacodec_lifecycle_under_test.h"
# FFmpeg callbacks retain unused ABI parameters (e.g. AVBuffer free data).
cc -std=c11 -pthread -Wall -Wextra -Werror -Wno-unused-parameter -I "$test_work" \
    "$test_dir/mediacodec_output_serialization_test.c" -o "$test_work/mediacodec_lock_test"
"$test_work/mediacodec_lock_test"
