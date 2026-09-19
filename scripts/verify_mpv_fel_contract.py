#!/usr/bin/env python3
"""Static FEL safety contract checks; these do not replace device playback tests."""

import argparse
from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "third_party/patches/mpv-android-fel.patch"


def require(condition, message):
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mpv-source", type=Path)
    args = parser.parse_args()
    patch = PATCH.read_text()
    old_gate = (ROOT / "third_party/patches/mpv-android-dovi-el-surface.patch").read_text()
    require("+#if !HAVE_ANDROID\n+            VO_CAP_GPU_DOVI_EL |" in old_gate,
            "the default Android single-Surface gate must remain")
    additions = "\n".join(line[1:] for line in patch.splitlines()
                          if line.startswith("+") and not line.startswith("+++"))
    for marker in (
        '"android-dovi-fel", OPT_BOOL(android_dovi_fel)',
        "mpctx->video_out->extra.android_dovi_fel && el",
        "VO_CAP_GPU_DOVI_EL_SW",
        "track->stream->codec->dv_profile == 7",
        "info->force_swdec = true",
        ".force_swdec = sinfo->force_swdec",
        "AV_CODEC_CAP_HARDWARE | AV_CODEC_CAP_HYBRID",
        "!ctx->force_swdec &&",
        "ctx->hwdec_opts->software_fallback == INT_MAX",
    ):
        require(marker in additions, f"missing FEL safety contract: {marker}")
    require(not re.search(r"android_dovi_fel\s*=\s*(?:true|1)\b", additions),
            "FEL must never become a native default")
    build_script = (ROOT / "scripts/build_mpv_native.sh").read_text()
    require('apply --recount "$MPV_ANDROID_FEL_PATCH"' in build_script,
            "the production native build must apply the FEL patch")

    if args.mpv_source:
        source = args.mpv_source.resolve()
        subprocess.run(["git", "-C", str(source), "apply", "--reverse", "--check",
                        "--recount", str(PATCH)], check=True)
        pair = (source / "filters/f_enhancement_pair.c").read_text()
        decoder = (source / "video/decode/vd_lavc.c").read_text()
        renderer = (source / "video/out/vo_gpu_next.c").read_text()
        wrapper = (source / "filters/f_decoder_wrapper.c").read_text()
        vo = (source / "video/out/vo.c").read_text()
        stable = (source / "video/out/hwdec/hwdec_aimagereader_vk_stable.c").read_text()
        timing = (source / "filters/f_android_fel_perf.h").read_text()
        require("CLOCK_THREAD_CPUTIME_ID" in timing and "bits < 64" in timing,
                "FEL diagnostics must distinguish thread CPU and valid-bit GPU timestamps")
        collect = stable[stable.index("static void collect_fel_gpu_time("):
                         stable.index("static void init_fel_gpu_timer(")]
        finish = stable[stable.index("static bool finish_output("):
                        stable.index("static void destroy_output(")]
        require("VK_QUERY_RESULT_WAIT_BIT" not in collect
                and "VK_QUERY_RESULT_64_BIT" in collect
                and "output->fel_query_recorded = false" in collect
                and "p->fel_query_failed = true" in collect,
                "optional GPU timing cannot wait, reuse old results, or fail playback")
        require(finish.index('"waiting for AHardwareBuffer conversion"')
                    < finish.index("collect_fel_gpu_time(p, output)")
                    < finish.index("vkResetFences("),
                "read GPU timestamps only after this copy fence succeeds and before reset")
        require("p->fel_profile = p->android_fel && mp_msg_test(p->log, MSGL_INFO)" in stable
                and "output->fel_query_recorded = p->fel_query_pool && !p->fel_query_failed" in stable,
                "timing must be explicit-FEL/log gated and disable cleanly when unavailable")
        require("WebHTV FEL decoder threads:" in decoder
                and "avctx->active_thread_type" in decoder
                and "GPU last-known pass averages, not frame wall time" in renderer,
                "diagnostics must report actual thread configuration and label cached GPU samples")
        prepare = stable[stable.index("static bool prepare_conversion("):
                         stable.index("static bool submit_conversion(")]
        reuse_hit = prepare[prepare.index("if (hit)"):
                            prepare.index("p->recording_misses++")]
        require("p->android_fel && input->initialized && !input->removed && output->written" in prepare
                and "if (output->pending)" in prepare
                and reuse_hit.index("record->valid = false")
                    < reuse_hit.index("record_conversion")
                    < reuse_hit.index("record->valid = true")
                    < reuse_hit.index("output->active_command = record->command")
                and "update_conversion_descriptor" not in reuse_hit
                and "if (!p->push_descriptors)" in reuse_hit
                and "p->descriptor_content_hits++" in reuse_hit
                and "output->fel_query_recorded = record->timestamps" in reuse_hit,
                "completed FEL content hits skip writes but must bind and record before selection")
        require("record->input_view == input->view" in stable
                and "record->output_view == output->view" in stable
                and "record->input_view = input->view" in prepare
                and "record->output_view = output->view" in prepare,
                "descriptor content identity must include both exact image views")
        require("#define FEL_INPUT_CACHE_SIZE 32" in stable
                and "#define FEL_RECORD_CACHE_SIZE 128" in stable
                and "p->android_fel ? FEL_INPUT_CACHE_SIZE : INPUT_CACHE_SIZE" in stable
                and ".flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT" in stable
                and "command-mode=fresh-bind-record replay=0" in stable,
                "FEL metadata reuse stays bounded; every frame uses a fresh one-shot recording")
        descriptor_layout = stable[stable.index("static bool create_conversion_descriptor_layout("):
                                   stable.index("static bool create_pipeline(")]
        pipeline = stable[stable.index("static bool create_pipeline("):
                          stable.index("static int output_sample_depth(VkFormat format)\n{")]
        require("if (p->android_fel)" in descriptor_layout
                and "has_extension(p->vk, VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME)" in descriptor_layout
                and "sampler_descriptors < max_push" in descriptor_layout
                and "push-layout-failed" in descriptor_layout
                and pipeline.index("if (p->push_descriptors)") < pipeline.index("vkCreateDescriptorPool("),
                "push descriptors require enabled capabilities and a set-layout fallback without pool allocation")
        require("p->CmdPushDescriptorSetKHR(command, VK_PIPELINE_BIND_POINT_COMPUTE" in stable
                and "FEL_API_PUSH_DESCRIPTORS" in stable
                and "if (!p->push_descriptors && !p->recording_pool)" in stable
                and "if (!p->push_descriptors && !record->descriptor)" in stable
                and "if (!p->push_descriptors)\n            vkUpdateDescriptorSets" in stable,
                "push layouts cannot allocate/update ordinary descriptor sets, including extra output slots")
        destroy = stable[stable.index("static void destroy_conversion_resources("):
                         stable.index("static int ycbcr_format_depth(")]
        require(destroy.index("finish_output(p, &p->outputs[n], UINT64_MAX)")
                    < destroy.index("destroy_recording_cache(p)")
                    < destroy.index("destroy_output(p, &p->outputs[n])"),
                "cached commands must finish and be freed before referenced resources are destroyed")
        for release in ("vkDestroyPipelineLayout(", "vkDestroyDescriptorSetLayout(",
                        "vkDestroySampler(", "vkDestroySamplerYcbcrConversion("):
            require(destroy.index("destroy_recording_cache(p)") < destroy.index(release),
                    "descriptor pool lifetime must end before immutable layout/sampler/YCbCr objects")
        destroy_input = stable[stable.index("static void destroy_input("):
                               stable.index("static void destroy_recording_cache(")]
        require(destroy_input.index("invalidate_input_recordings(p, input)")
                    < destroy_input.index("vkDestroyImageView("),
                "input generation must be invalidated before Vulkan can recycle a view handle")
        require("#define FEL_LATENCY_SAMPLES 128" in stable
                and "latency-window:%s" in stable
                and "descriptor-content-cache=1" in stable,
                "content reuse and actual bind/map latency must remain separately observable and bounded")
        require("recording_cache_disabled = true" in stable
                and "WebHTV FEL reuse:" in stable and "WebHTV FEL api perf:" in stable,
                "optional reuse failure must stop allocation retries and remain diagnosable")
        require("#define FEL_ORDER_CAPACITY 8" in stable
                and "WebHTV FEL frame order:" in stable
                and "WebHTV FEL api slow:" in stable
                and "p->fel_slow_count < 4" in stable
                and "FEL_API_BARRIER_IN" in stable and "FEL_API_DISPATCH" in stable,
                "frame identity and slow-call evidence must be bounded and stage-specific")
        app = (ROOT / "app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java").read_text()
        callback = app[app.index("public void logMessage("):app.index("private void openCurrent(")]
        fast_log = callback[callback.index("if (performanceKind >= 0)"):
                            callback.index("String line = MpvDiagnosticsPolicy.redactSensitive")]
        collector = (ROOT / "app/src/main/java/androidx/media3/mpvplayer/MpvDiagnosticCollector.java").read_text()
        native_log = collector[collector.index("synchronized void nativeLog("):
                               collector.index("static boolean isHook(")]
        sink = (ROOT / "app/src/main/java/com/fongmi/android/tv/player/PlaybackDiagnosticCollector.java").read_text()
        require(callback.index("diagnostics.nativeLog(prefix, level, text)")
                < callback.index("if (performanceKind >= 0)")
                and "log.emitNative(" in native_log and "DebugLogStore.event(event)" in sink
                and "postToMain(" not in native_log and "PlaybackTrace.log(" not in native_log
                and "return;" in fast_log
                and "postToMain(" not in fast_log and "PlaybackTrace.log(" not in fast_log,
                "pure FEL measurements must not enqueue UI work or duplicate pretty Logcat output")
        require("WebHTV FEL wait sample:" in stable and "capability-v=1" in stable
                and "MP_TIME_MS_TO_NS(3000)" in stable
                and "poll(&state, 1, 0)" in stable and "RUSAGE_THREAD" in stable,
                "FEL waiting diagnostics must remain bounded, per-thread and non-waiting")
        output = wrapper[wrapper.index("output_frame:\n", wrapper.index("static void read_frame(")):]
        require(output.index("stage_fel_before_publish(p, frame)") < output.index("mp_pin_in_write(pin, frame)"),
                "FEL BL must return its source before publishing to the decoder queue")
        require("mp_android_fel_staging_ready(image->android_fel_staging->data)" in wrapper,
                "a texture handle alone is not safe source handoff")
        handoff = wrapper[wrapper.index("static int stage_fel_before_publish("):
                          wrapper.index("static void read_frame(")]
        require("mp_sleep" not in handoff and "mp_dispatch_queue_process" not in handoff,
                "pending FEL must return to filter dispatch, not wait on a live stack frame")
        require("p->fel_stage_frame = frame;" in output
                and "p->fel_stage_frame.type ? 0.002 : INFINITY" in wrapper
                and "mp_filter_mark_async_progress(p->decf)" in wrapper,
                "pending frame ownership and interruptible decoder scheduling must be connected")
        require(re.search(r"if \(p->fel_stage_failed\)\s*return;", wrapper)
                and "if (!p->decoder || p->fel_stage_failed)" not in wrapper,
                "FEL failure must not repeatedly enqueue zero-weight EOF signals")
        require("reset_fel_staging(p);\n        mp_filter_reset(p->dec_root_filter);" in wrapper
                and "reset_fel_staging(p);\n        p->request_terminate_dec_thread = 1;" in wrapper,
                "reset/stop must cancel the pending lease before resetting/terminating decode")
        stable_map = stable[stable.index("int aimagereader_vk_stable_map("):]
        require("mp_android_fel_staging_begin_init(" in stable_map
                and "mp_android_fel_staging_end_init(" in stable_map
                and stable_map.index("mp_android_fel_staging_begin_init(")
                    < stable_map.index("input = create_input(")
                    < stable_map.index("mp_android_fel_staging_end_init(")
                and "MP_ANDROID_FEL_INIT_TIMEOUT_NS" in vo
                and "MP_ANDROID_FEL_FRAME_TIMEOUT_NS" in vo,
                "mapper cold initialization must retain its separate bounded deadline")
        draw = vo[vo.index("static bool render_frame("):]
        require("in->fel_render_init_attempted" in vo
                and "MPMAX(in->fel_prepare_phase_started, in->fel_render_init_started)" in vo
                and draw.index("begin_fel_render_init(vo, frame)")
                    < draw.index("in->visible = vo->driver->draw_frame(vo, frame)")
                    < draw.index("end_fel_render_init(vo)"),
                "first actual draw needs bounded renderer warmup independent of producer polling")
        require(vo.index("if (same_request)") < vo.index("} else if (image->android_fel_prepared"),
                "the prepared fast path must acknowledge its own single-slot request")
        require(re.search(r"if \(result == VO_TRUE &&\s*"
                          r"!mp_android_fel_staging_ready\(image->android_fel_staging->data\)\)\s*"
                          r"result = VO_FALSE;", vo),
                "mapped-but-unreturned sources must retain the pending request")
        release = stable[stable.index("static bool release_fel_source_async("):
                         stable.index("static bool has_ycbcr_conversion(")]
        require(release.index("AImage_deleteAsync(") < release.index("mp_android_fel_staging_release_fenced("),
                "publish fence-backed completion only after Android owns the image/fd")
        require("{output->ready, output->source_release}" in stable
                and "VK_EXTERNAL_SEMAPHORE_FEATURE_EXPORTABLE_BIT" in stable
                and "p->android_fel && p->release_sync_fd &&" in stable,
                "FEL source-fence export must be capability gated and independent of rendering")
        require("#define PTS_MATCH_TOLERANCE 1e-6" in pair and "#define QUEUE_MAX 16" in pair,
                "keep the upstream bounded PTS matching contract")
        require("p->el_eof" in pair and "static void pair_reset" in pair,
                "pairing must still handle EOF and seek/reset")
        require("frame->enhancement_layer = &fp->el_frame" in renderer
                and "upload_planes_sw(vo, gpu, el" in renderer,
                "gpu-next must compile the software EL upload path")
        require(re.search(r"else if \(!ctx->force_swdec &&\s*"
                          r"ctx->hwdec_opts->software_fallback == INT_MAX\)", decoder),
                "disabled BL fallback must not reject explicitly software EL")
        require("while (!ctx->avctx);" not in decoder,
                "decoder failure must honor forced EOF instead of retrying forever")
        require("#if !HAVE_ANDROID\n            VO_CAP_GPU_DOVI_EL |" in renderer,
                "ordinary Android playback must not acquire hardware EL capability")
    print("PASS: opt-in FEL gate, software-only EL, BL fallback isolation, and build reachability")


if __name__ == "__main__":
    main()
