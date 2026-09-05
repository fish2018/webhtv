#!/usr/bin/env bash
# Pulls the ad-audio diagnostic trail from a connected device.
#
# Requires 设置 → 增强功能 → 调试日志 to be enabled (pref key: debug_log).
# Usage: scripts/pull-ad-audio-log.sh [serial]
set -u

SERIAL="${1:-emulator-5560}"
PKG=com.silent.android.webhtv
LOG=/data/data/$PKG/cache/webhtv-debug-log.txt

run_as() { adb -s "$SERIAL" shell "run-as $PKG sh -c '$1'" 2>/dev/null; }

echo "=== device: $SERIAL ==="
adb -s "$SERIAL" shell "pidof $PKG" 2>/dev/null | sed 's/^/pid: /'

echo
echo "=== debug_log enabled? (must be true, else nothing is recorded) ==="
run_as "cat /data/data/$PKG/shared_prefs/${PKG}_preferences.xml" \
    | grep -o -E '<boolean name="debug_log"[^>]*>' || echo 'debug_log NOT SET → 请先开启调试日志'

echo
echo "=== speech ad settings ==="
run_as "cat /data/data/$PKG/shared_prefs/${PKG}_preferences.xml" \
    | grep -o -E '<(boolean|int|string) name="(speech_ad_[a-z_]*|player|perf_exo_audio_pass_through|subtitle_realtime_model)"[^>]*/?>'

echo
echo "=== ad-audio trail (last 80) ==="
run_as "grep -a 'ad-audio' $LOG | tail -80" || echo '(none)'

echo
echo "=== interpretation ==="
cat <<'EOF'
refresh skipped ...      -> provider never started; check ui/fingerprint/speech flags
refresh ineligible ...   -> not an ExoPlayer VOD session, or not STATE_READY yet
activated speech=DEGRADED-> model probe failed (SPEECH_MODEL_UNAVAILABLE)
activated capture=false  -> no AD_AUDIO capture lease
activated pipeline=false -> AudioSink has no PCM tap; expect a rebuild line next
pipeline rebuild ...     -> player restarting to attach the PCM tap
speech pcm n=1 ...       -> PCM is reaching the recognizer (good)
speech pcm n=... ...     -> PCM still flowing (sampled 1:200)
   (absent)              -> no audio ever arrived; capture/pipeline problem
speech pcm dropped ...   -> frames discarded because the provider is not RUNNING
speech fed n=... ...     -> frames actually handed to sherpa (sampled 1:200)
   (absent, pcm present) -> drain/resample stalled before the recognizer
speech text ... matched=false -> recognizer works but the keyword was not heard
speech text ... matched=true  -> keyword hit; a prompt line must follow
   (absent, fed present) -> sherpa never emitted an endpoint/result
prompt dropped ...       -> candidate rejected by clock/seek validation
prompt shown ...         -> dialog was displayed
SPEECH_TEXT_EMPTY        -> recognizer returned blank text
SPEECH_* / SEEK_REJECTED -> counter names with running totals
EOF
