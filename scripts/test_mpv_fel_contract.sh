#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/.." && pwd)"
mpv_source="${1:-$task_root/build/mpv-native/mpv-android/buildscripts/deps/mpv}"
test_output="$(mktemp -d /private/tmp/webhtv-fel-contract.XXXXXX)"
python3 "$task_root/scripts/test_mpv_fel_context_options.py" --mpv-source "$mpv_source"
awk '
  /^static bool should_use_android_fel_output\(/ || /^void reinit_video_chain_src\(/ ||
  /^static int mp_property_android_fel_active\(/ || /^static int mp_property_video_frame_submitted\(/ { copying = 1 }
  copying { print }
  copying && /^}/ { copying = 0 }
' "$mpv_source/player/video.c" "$mpv_source/player/command.c" | \
  "${CC:-cc}" -std=c11 -Wall -Wextra -Werror -Wno-unused-parameter \
    -fsanitize=address,undefined \
    -include "$task_root/third_party/mpv-player-jni/tests/fel_startup_selection_test.c" \
    -x c - -o "$test_output/fel-startup-selection-test"
"$test_output/fel-startup-selection-test"
# Use Vulkan declarations without using Android's libc headers on the host.
vulkan_headers="${VULKAN_HEADERS_INCLUDE:-}"
if [[ -z "$vulkan_headers" && -n "${ANDROID_NDK_HOME:-}" ]]; then
  for candidate in "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/sysroot/usr/include; do
    if [[ -f "$candidate/vulkan/vulkan.h" ]]; then vulkan_headers="$candidate"; break; fi
  done
fi
if [[ ! -f "$vulkan_headers/vulkan/vulkan.h" ]]; then
  printf '%s\n' 'Set VULKAN_HEADERS_INCLUDE or ANDROID_NDK_HOME for the FEL Vulkan host contract.' >&2
  exit 1
fi
awk '
  /^bool get_internal_paused\(/ || /^static void handle_fel_bind_probe\(/ ||
  /^static int mp_property_android_fel_bind_probe\(/ ||
  /^static void fel_latency_add\(/ || /^static int fel_latency_compare\(/ ||
  /^static void fel_latency_summary\(/ { copying = 1 }
  copying { print }
  copying && /^}/ { copying = 0 }
' "$mpv_source/player/playloop.c" "$mpv_source/player/command.c" \
  "$mpv_source/video/out/hwdec/hwdec_aimagereader_vk_stable.c" | \
  "${CC:-cc}" -std=gnu11 -Wall -Wextra -Werror -Wno-unused-parameter \
    -fsanitize=address,undefined -I"$mpv_source" -idirafter "$vulkan_headers" \
    -include "$task_root/third_party/mpv-player-jni/tests/fel_bind_probe_test.c" \
    -x c - -o "$test_output/fel-bind-probe-test"
"$test_output/fel-bind-probe-test"
awk '
  /^static struct fel_api_clock fel_api_begin\(/ ||
  /^static int fel_parse_schedstat\(/ || /^static int fel_read_schedstat\(/ ||
  /^static int fel_probe_acquire_fence\(/ || /^static struct fel_wait_snapshot fel_wait_snapshot\(/ ||
  /^static int64_t fel_wait_delta\(/ || /^static struct fel_wait_sample fel_wait_probe_begin\(/ ||
  /^static void fel_wait_probe_end\(/ || /^static bool has_extension\(/ ||
  /^static void log_fel_descriptor_capabilities\(/ { copying = 1 }
  copying { print }
  copying && /^}/ { copying = 0 }
' "$mpv_source/video/out/hwdec/hwdec_aimagereader_vk_stable.c" | \
  "${CC:-cc}" -std=c11 -Wall -Wextra -Werror -Wno-unused-parameter \
    -fsanitize=address,undefined -idirafter "$vulkan_headers" \
    -include "$task_root/third_party/mpv-player-jni/tests/fel_wait_probe_test.c" \
    -x c - -o "$test_output/fel-wait-probe-test"
"$test_output/fel-wait-probe-test"
awk '
  /^static const char \*const fel_api_names\[/ ||
  /^static bool has_extension\(/ || /^static bool create_conversion_descriptor_layout\(/ ||
  /^static void trace_fel_frame_order\(/ || /^static void format_fel_frame_order\(/ ||
  /^static struct fel_api_clock fel_api_begin\(/ || /^static void fel_api_end\(/ ||
  /^static void fel_latency_add\(/ || /^static int fel_latency_compare\(/ ||
  /^static void fel_latency_summary\(/ || /^static void fel_perf_checkpoint\(/ ||
  /^static void fel_perf_finish_map\(/ ||
  /^static void invalidate_input_recordings\(/ || /^static void destroy_input\(/ ||
  /^static void destroy_recording_cache\(/ || /^static struct vk_input \*find_input\(/ ||
  /^static void purge_removed_inputs\(/ || /^static struct vk_input \*select_input_slot\(/ ||
  /^static bool recording_pending\(/ || /^static bool recording_matches\(/ ||
  /^static struct vk_recording \*select_recording\(/ || /^static bool allocate_recording\(/ ||
  /^static void update_conversion_descriptor\(/ || /^static bool record_conversion\(/ ||
  /^static bool prepare_conversion\(/ || /^void aimagereader_vk_stable_buffer_removed\(/ { copying = 1 }
  copying { print }
  copying && /^}/ { copying = 0 }
' "$mpv_source/video/out/hwdec/hwdec_aimagereader_vk_stable.c" | \
  "${CC:-cc}" -std=c11 -Wall -Wextra -Werror -Wno-unused-parameter \
    -fsanitize=address,undefined -I"$mpv_source" -idirafter "$vulkan_headers" \
    -include "$task_root/third_party/mpv-player-jni/tests/fel_vk_cache_test.c" \
    -x c - -o "$test_output/fel-vk-cache-test"
"$test_output/fel-vk-cache-test"
"${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -I"$task_root/third_party/mpv-player-jni/src" \
  -I"$task_root/third_party/mpv-player-jni/include" -I"$mpv_source" \
  "$task_root/third_party/mpv-player-jni/tests/fel_contract_test.cpp" \
  -o "$test_output/fel-contract-test"
"$test_output/fel-contract-test"
# Compile the real wrapper bodies; a blocking decoder-dispatch stub makes any
# accidental synchronous read fail deterministically, without timing sleeps.
awk '
  /^static int update_cached_values\(/ || /^int mp_decoder_wrapper_control\(/ ||
  /^double mp_decoder_wrapper_get_container_fps\(/ { copying = 1 }
  copying { print }
  copying && /^}/ { copying = 0 }
' "$mpv_source/filters/f_decoder_wrapper.c" | \
  "${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
    -I"$mpv_source" \
    -include "$task_root/third_party/mpv-player-jni/tests/fel_decoder_control_test.cpp" \
    -x c++ - -o "$test_output/fel-decoder-control-test"
"$test_output/fel-decoder-control-test"
awk '
  /^static int preload_dropped_fel_frame\(/ || /^static bool render_frame\(/ ||
  /^static bool begin_fel_render_init\(/ || /^static void end_fel_render_init\(/ { copying = 1 }
  copying { print }
  copying && /^}/ { copying = 0 }
' "$mpv_source/video/out/vo_gpu_next.c" "$mpv_source/video/out/vo.c" | \
  "${CC:-cc}" -std=c11 -Wall -Wextra -Werror -Wno-unused-function \
    -fsanitize=address,undefined -I"$mpv_source" \
    -include "$task_root/third_party/mpv-player-jni/tests/fel_vo_drop_test.c" \
    -x c - -o "$test_output/fel-vo-drop-test"
"$test_output/fel-vo-drop-test"
awk '
  /^static void cancel_fel_prepare\(/ || /^void vo_cancel_fel_frame\(/ ||
  /^static bool begin_fel_render_init\(/ || /^static void end_fel_render_init\(/ ||
  /^static bool fel_prepare_timed_out\(/ || /^int vo_prepare_fel_frame\(/ ||
  /^static int64_t process_fel_prepare\(/ || /^static bool use_video_lookahead\(/ ||
  /^static int get_req_frames\(/ || /^static bool needs_new_frame\(/ ||
  /^static void add_new_frame\(/ || /^static bool have_new_frame\(/ ||
  /^static void trace_fel_core\(/ || /^static int video_output_image\(/ ||
  /^static bool output_has_live_fel_frame\(/ || /^static int select_reusable_output\(/ ||
  /^static void restore_fel_display_params\(/ || /^static bool hwdec_reconfig\(/ { copying = 1 }
  copying { print }
  copying && /^}/ { copying = 0 }
' "$mpv_source/video/out/vo.c" "$mpv_source/player/video.c" \
  "$mpv_source/video/out/hwdec/hwdec_aimagereader_vk_stable.c" \
  "$mpv_source/video/out/vo_gpu_next.c" | \
  "${CC:-cc}" -std=c11 -Wall -Wextra -Werror -Wno-unused-function \
    -fsanitize=address,undefined -I"$mpv_source" \
    $(pkg-config --cflags libavutil) \
    -include "$task_root/third_party/mpv-player-jni/tests/fel_core_preload_test.c" \
    -x c - $(pkg-config --libs libavutil) -o "$test_output/fel-core-preload-test"
"$test_output/fel-core-preload-test"
awk '
  /^static int stage_fel_before_publish\(/ || /^static bool finish_output\(/ ||
  /^static void fel_perf_checkpoint\(/ || /^static void fel_perf_finish_map\(/ ||
  /^static void collect_fel_gpu_time\(/ || /^static void init_fel_gpu_timer\(/ ||
  /^static VkSemaphore create_fel_release_semaphore\(/ ||
  /^static bool release_fel_source_async\(/ || /^static bool submit_conversion\(/ ||
  /^bool aimagereader_vk_stable_reuse\(/ { copying = 1 }
  copying { print }
  copying && /^}/ { copying = 0 }
' "$mpv_source/filters/f_decoder_wrapper.c" \
  "$mpv_source/video/out/hwdec/hwdec_aimagereader_vk_stable.c" | \
  "${CC:-cc}" -std=c11 -Wall -Wextra -Werror -Wno-unused-function \
    -fsanitize=address,undefined -I"$mpv_source" \
    $(pkg-config --cflags libavutil) \
    -include "$task_root/third_party/mpv-player-jni/tests/fel_producer_handoff_test.c" \
    -x c - $(pkg-config --libs libavutil) -o "$test_output/fel-producer-handoff-test"
"$test_output/fel-producer-handoff-test"
awk '
  /^static int stage_fel_before_publish\(/ || /^static void reset_fel_staging\(/ ||
  /^static void read_frame\(/ || /^static void decf_process\(/ ||
  /^static MP_THREAD_VOID dec_thread\(/ { copying = 1 }
  copying { print }
  copying && /^}/ { copying = 0 }
' "$mpv_source/filters/f_decoder_wrapper.c" | \
  "${CC:-cc}" -std=c11 -Wall -Wextra -Werror -Wno-unused-function \
    -fsanitize=address,undefined -I"$mpv_source" \
    $(pkg-config --cflags libavutil) \
    -include "$task_root/third_party/mpv-player-jni/tests/fel_async_producer_test.c" \
    -x c - $(pkg-config --libs libavutil) -o "$test_output/fel-async-producer-test"
"$test_output/fel-async-producer-test"
awk '
  /^static void mp_image_destructor\(/ || /^void mp_image_unref_data\(/ ||
  /^static void ref_buffer\(/ || /^struct mp_image \*mp_image_new_ref\(/ ||
  /^struct mp_image \*mp_image_new_dummy_ref\(/ { copying = 1 }
  copying { print }
  copying && /^}/ { copying = 0 }
' "$mpv_source/video/mp_image.c" | \
  "${CC:-cc}" -std=c11 -Wall -Wextra -Werror -fsanitize=address,undefined \
    $(pkg-config --cflags libavutil) \
    -include "$task_root/third_party/mpv-player-jni/tests/fel_image_lease_test.c" \
    -x c - $(pkg-config --libs libavutil) -o "$test_output/fel-image-lease-test"
"$test_output/fel-image-lease-test"
"${CC:-cc}" -std=c11 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -pthread -I"$mpv_source" \
  "$task_root/third_party/mpv-player-jni/tests/fel_progress_test.c" \
  -o "$test_output/fel-progress-test"
"$test_output/fel-progress-test"
# Exercise the production bitstream/ownership helper with a real FFmpeg BSF.
# Optional argument 2 is a length-prefixed Profile 7 sample (not a fixture copy).
"${CC:-cc}" -std=c11 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -I"$mpv_source" $(pkg-config --cflags libavformat libavcodec libavutil) \
  "$task_root/third_party/mpv-player-jni/tests/fel_packet_test.c" \
  $(pkg-config --libs libavformat libavcodec libavutil) \
  -o "$test_output/fel-packet-test"
"$test_output/fel-packet-test" ${2:+"$2"}
"${CC:-cc}" -std=c11 -Wall -Wextra -Werror -fsanitize=address,undefined \
  $(pkg-config --cflags libavformat libavcodec libavutil) \
  "$task_root/third_party/mpv-player-jni/tests/fel_el_rpu_test.c" \
  $(pkg-config --libs libavformat libavcodec libavutil) \
  -o "$test_output/fel-el-rpu-test"
"$test_output/fel-el-rpu-test" ${2:+"$2"}
awk '
  /^static void inherit_dovi_from_el\(/ { copying = 1 }
  copying { print }
  copying && /^}/ { copying = 0 }
' "$mpv_source/filters/f_enhancement_pair.c" | \
  "${CC:-cc}" -std=c11 -Wall -Wextra -Werror -fsanitize=address,undefined \
    $(pkg-config --cflags libavutil) \
    -include "$task_root/third_party/mpv-player-jni/tests/fel_rpu_inherit_test.c" \
    -x c - $(pkg-config --libs libavutil) -o "$test_output/fel-rpu-inherit-test"
"$test_output/fel-rpu-inherit-test"
python3 "$task_root/scripts/verify_mpv_fel_contract.py" --mpv-source "$mpv_source"
rg -q 'mp_android_fel_stage_output\(' "$mpv_source/video/out/hwdec/hwdec_aimagereader.c"
rg -q 'mp_android_fel_preserve_depth\(' "$mpv_source/video/out/hwdec/hwdec_aimagereader_vk_stable.c"
rg -q 'finish_output\(p, output, MP_ANDROID_FEL_COPY_WAIT_NS\)' "$mpv_source/video/out/hwdec/hwdec_aimagereader_vk_stable.c"
