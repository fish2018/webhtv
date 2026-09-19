#!/usr/bin/env bash
set -euo pipefail
test_dir="$(cd "$(dirname "$0")" && pwd)"
mpv_source="${1:?usage: bash test_audiotrack_underrun.sh <mpv source directory>}"
test_work="$(mktemp -d /tmp/p9-audiotrack-test.XXXXXX)"
awk '
    /^static MP_THREAD_VOID ao_thread\(void \*arg\)/ { emit = 1 }
    emit { print }
    emit && /^}/ { exit }
' "$mpv_source/audio/out/ao_audiotrack.c" > "$test_work/ao_thread_under_test.h"
test -s "$test_work/ao_thread_under_test.h"
cc -std=c11 -Wall -Wextra -Werror -Wno-unused-parameter -Wno-unused-variable \
    -I "$test_work" "$test_dir/audiotrack_underrun_test.c" -o "$test_work/test"
"$test_work/test"
