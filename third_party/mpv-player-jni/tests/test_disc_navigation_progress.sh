#!/usr/bin/env bash
set -euo pipefail
test_dir="$(cd "$(dirname "$0")" && pwd)"
mpv_source="${1:?usage: bash test_disc_navigation_progress.sh <mpv source directory>}"
test_work="$(mktemp -d /tmp/p9-disc-progress.XXXXXX)"
awk '
    /^static int bluray_poll_hdmv_events\(/ { emit = 1 }
    /^static int bluray_stream_fill_buffer\(/ { emit = 1 }
    /^static int bluray_call_user_menu\(/ { exit }
    /^static int bluray_stream_control\(/ { exit }
    emit { print }
' "$mpv_source/stream/stream_bluray.c" > "$test_work/disc_progress_under_test.h"
awk '
    /^static bool bd_stream_pid_valid\(/ { emit = 1 }
    /^static struct sh_stream \*find_outer_for_slave\(/ { exit }
    emit { print }
' "$mpv_source/demux/demux_disc.c" > "$test_work/disc_pid_under_test.h"
awk '
    /if \(.*bd_stream_pid_valid\(src\)/ {
        sub(/^[[:space:]]*if \(/, "#define DISC_STREAM_IGNORED ");
        sub(/\) \{[[:space:]]*$/, "");
        print; exit;
    }
' "$mpv_source/demux/demux_disc.c" >> "$test_work/disc_pid_under_test.h"
test -s "$test_work/disc_progress_under_test.h"
test -s "$test_work/disc_pid_under_test.h"
cc -std=c11 -Wall -Wextra -Werror -I "$test_work" \
    "$test_dir/disc_navigation_progress_test.c" -o "$test_work/disc_progress_test"
"$test_work/disc_progress_test"
