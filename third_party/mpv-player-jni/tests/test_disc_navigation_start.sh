#!/usr/bin/env bash
set -euo pipefail
test_dir="$(cd "$(dirname "$0")" && pwd)"
mpv_source="${1:?usage: bash test_disc_navigation_start.sh <mpv source directory>}"
test_work="$(mktemp -d /tmp/p9-disc-start.XXXXXX)"
awk '
    /^    b->hdmv_mode = b->cfg_title == BLURAY_MENU_TITLE;/ { emit = 1 }
    /^    \/\/ Angle selection is only valid/ { exit }
    emit { print }
' "$mpv_source/stream/stream_bluray.c" > "$test_work/disc_start_under_test.h"
test -s "$test_work/disc_start_under_test.h"
cc -std=c11 -Wall -Wextra -Werror -I "$test_work" \
    "$test_dir/disc_navigation_start_test.c" -o "$test_work/disc_start_test"
"$test_work/disc_start_test"
