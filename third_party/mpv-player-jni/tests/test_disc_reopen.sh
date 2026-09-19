#!/usr/bin/env bash
set -euo pipefail
test_dir="$(cd "$(dirname "$0")" && pwd)"
mpv_source="${1:?usage: bash test_disc_reopen.sh <mpv source directory>}"
test_work="$(mktemp -d /tmp/p9-disc-reopen.XXXXXX)"
awk '
    /^static bool reopen_slave\(/ { emit = 1 }
    /^static bool d_read_packet\(/ { exit }
    emit { print }
' "$mpv_source/demux/demux_disc.c" > "$test_work/disc_reopen_under_test.h"
test -s "$test_work/disc_reopen_under_test.h"
cc -std=c11 -Wall -Wextra -Werror -I "$test_work" \
    "$test_dir/disc_reopen_test.c" -o "$test_work/disc_reopen_test"
"$test_work/disc_reopen_test"
