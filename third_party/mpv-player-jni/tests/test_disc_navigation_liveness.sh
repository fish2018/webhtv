#!/usr/bin/env bash
set -euo pipefail
test_dir="$(cd "$(dirname "$0")" && pwd)"
mpv_source="${1:?usage: bash test_disc_navigation_liveness.sh <mpv source directory>}"
test_work="$(mktemp -d /tmp/p9-disc-liveness.XXXXXX)"
awk '
    /^void demux_drive_nav\(/ { emit = 1 }
    /^const char \*stream_type_name\(/ { exit }
    emit { print }
' "$mpv_source/demux/demux.c" > "$test_work/disc_requests_under_test.h"
awk '
    /^static bool poll_nav\(/ { emit = 1 }
    emit { print }
    emit && /^}/ { exit }
' "$mpv_source/demux/demux.c" > "$test_work/disc_poll_under_test.h"
awk '
    /^static void poll_disc_nav\(/ { emit = 1 }
    /^void disc_nav_update\(/ { exit }
    emit { print }
' "$mpv_source/player/discnav.c" > "$test_work/disc_tick_under_test.h"
for part in requests poll tick; do
    test -s "$test_work/disc_${part}_under_test.h"
done
cc -std=c11 -Wall -Wextra -Werror -I "$test_work" \
    "$test_dir/disc_navigation_liveness_test.c" -o "$test_work/disc_liveness_test"
"$test_work/disc_liveness_test"
