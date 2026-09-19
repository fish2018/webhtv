#!/usr/bin/env bash
set -euo pipefail
test_dir="$(cd "$(dirname "$0")" && pwd)"
mpv_source="${1:?usage: bash test_disc_navigation_input.sh <mpv source directory>}"
test_work="$(mktemp -d /tmp/p9-disc-input.XXXXXX)"
awk '
    /^bool disc_nav_window_pos_to_src\(/ { emit = 1 }
    /^\/\/ Push the current menu\/highlight state/ { exit }
    emit { print }
' "$mpv_source/player/discnav.c" > "$test_work/disc_pointer_under_test.h"
awk '
    /^static void cmd_discnav\(/ { emit = 1 }
    /^static void cmd_flush_status_line\(/ { exit }
    emit { print }
' "$mpv_source/player/command.c" > "$test_work/disc_command_under_test.h"
awk '
    /^static int bluray_call_user_menu\(/ { emit = 1 }
    /^static int bluray_stream_control\(/ { exit }
    emit { print }
' "$mpv_source/stream/stream_bluray.c" > "$test_work/disc_menu_call_under_test.h"
awk '
    /^    case STREAM_CTRL_NAV_CMD:/ { emit = 1 }
    /^    case STREAM_CTRL_GET_NAV_STATE:/ { exit }
    emit { print }
' "$mpv_source/stream/stream_bluray.c" > "$test_work/disc_bluray_input_under_test.h"
awk '
    /^    case STREAM_CTRL_NAV_POLL:/ { emit = 1 }
    /^    case STREAM_CTRL_GET_NUM_CHAPTERS:/ { exit }
    emit { print }
' "$mpv_source/stream/stream_bluray.c" > "$test_work/disc_pump_under_test.h"
awk '
    /^static bool d_read_packet\(/ { emit = 1 }
    emit && /^    struct stream_nav_state nav =/ { exit }
    emit { print }
' "$mpv_source/demux/demux_disc.c" > "$test_work/disc_demux_input_under_test.h"
awk '
    /^static inline bool stream_nav_action_activates\(/ { emit = 1 }
    emit { print }
    emit && /^}/ { exit }
' "$mpv_source/stream/stream.h" > "$test_work/disc_action_under_test.h"
for part in pointer command bluray_input menu_call action pump demux_input; do
    test -s "$test_work/disc_${part}_under_test.h"
done
cc -std=c11 -Wall -Wextra -Werror -I "$test_work" \
    "$test_dir/disc_navigation_input_test.c" -o "$test_work/disc_input_test"
"$test_work/disc_input_test"
