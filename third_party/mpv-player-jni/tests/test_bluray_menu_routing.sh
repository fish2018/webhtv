#!/usr/bin/env bash
set -euo pipefail
test_dir="$(cd "$(dirname "$0")" && pwd)"
bluray_source="${1:?usage: bash test_bluray_menu_routing.sh <libbluray source directory>}"
test_work="$(mktemp -d /tmp/p9-menu-routing.XXXXXX)"
awk '/^\/\* WebHTV menu visit state/ { emit=1 } emit { print } /^\/\* End WebHTV menu visit state/ { exit }' \
    "$bluray_source/src/libbluray/decoders/graphics_controller.c" > "$test_work/menu_visit_types.h"
awk '/^\/\* WebHTV observed menu transitions/ { emit=1 } emit { print } /^\/\* End WebHTV observed menu transitions/ { exit }' \
    "$bluray_source/src/libbluray/decoders/graphics_controller.c" > "$test_work/menu_history_under_test.h"
awk '/^#define VK_IS_NUMERIC/ { emit=1 } /^static void _set_button_page/ { exit } emit { print }' \
    "$bluray_source/src/libbluray/decoders/graphics_controller.c" > "$test_work/menu_route_under_test.h"
awk '/^static int _mouse_move/ { emit=1 } /^static int _animate/ { exit } emit { print }' \
    "$bluray_source/src/libbluray/decoders/graphics_controller.c" >> "$test_work/menu_route_under_test.h"
test -s "$test_work/menu_route_under_test.h"
cc -std=c11 -Wall -Wextra -Werror -I "$test_work" -I "$bluray_source/src" -I "$bluray_source/src/libbluray" \
    -fsanitize=address,undefined -fno-omit-frame-pointer \
    "$test_dir/bluray_menu_routing_test.c" -o "$test_work/menu_route_test"
"$test_work/menu_route_test"
# Exercise helpers above, and verify they are wired into real lifecycle paths.
awk '
    /^static int _restore_page_state/ { section="restore" }
    /^static void _select_page/ { section="select" }
    /^static void _gc_reset/ { section="reset" }
    /^int gc_decode_ts/ { section="decode" }
    /case GC_CTRL_POPUP:/ { section="popup" }
    /case GC_CTRL_INIT_MENU:/ { section="init" }
    /_clear_menu_history\(gc\)/ { if (section != "") clear[section]=1 }
    /_observe_menu_page_change\(gc, page_id\)/ { if (section == "select") observed=1 }
    /^}/ { section="" }
    END { if (!observed || !clear["restore"] || !clear["reset"] || !clear["decode"] || !clear["popup"] || !clear["init"]) exit 1 }
' "$bluray_source/src/libbluray/decoders/graphics_controller.c"
printf '%s\n' 'PASS: actual page-change / IG replacement / reset / restore / init / popup history invalidation hooks'
