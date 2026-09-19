#!/usr/bin/env bash
set -euo pipefail
test_dir="$(cd "$(dirname "$0")" && pwd)"
mpv_source="${1:?usage: bash test_disc_navigation_options.sh <mpv source directory>}"
test_work="$(mktemp -d /tmp/p9-disc-options.XXXXXX)"
awk '
    /^static void load_disc_navigation_options\(/ { emit = 1 }
    /^static MP_THREAD_VOID open_demux_thread\(/ { exit }
    emit { print }
' "$mpv_source/player/loadfile.c" > "$test_work/disc_options_under_test.h"
test -s "$test_work/disc_options_under_test.h"
# It must run after on_preloaded and before any decoder/filter initialization.
awk '
    /process_hooks\(mpctx, "on_preloaded"\)/ { preloaded = NR }
    /^    load_disc_navigation_options\(mpctx\);/ { applied = NR }
    /^    if \(reinit_complex_filters\(mpctx, false\)/ { decoder = NR }
    END { exit !(preloaded > 0 && applied > preloaded && decoder > applied) }
' "$mpv_source/player/loadfile.c"
cc -std=c11 -Wall -Wextra -Werror -I "$test_work" \
    "$test_dir/disc_navigation_options_test.c" -o "$test_work/disc_options_test"
"$test_work/disc_options_test"
