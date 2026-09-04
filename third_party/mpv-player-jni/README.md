# MPV player JNI

This directory contains the maintainable source for the `libplayer.so` JNI
bridge loaded by `is.xyz.mpv.MPVLib`.

Source baseline:

- `FongMi/mpv-android`
- Local reference directory: `/Users/macbookpro/Desktop/github/mpv-android-reference`
- Upstream master observed commit: `4c57302c655b2973c8112941e6a9d3ff571fab8e`
- FongMi native branch commit: `99a60ad2141d5ace94453590903c2c6b9a0a2443`
- Source license: see `LICENSE`

Project-specific changes:

- `MPV_EVENT_END_FILE` reads `mpv_event_end_file.reason` and
  `mpv_event_end_file.error`.
- Native calls `MPVLib.endFile(reason, error, errorText)` instead of only
  forwarding the event id.
- Blu-ray ISO streams expose libbluray duration/current-time/time-seek,
  chapters, and PID language controls to the patched MPV `stream_cb` API.
- The build links against this project's bundled MPV assets, whose FFmpeg
  libraries use renamed SONAMEs such as `libmvcodec.so` and `libmwscale.so`.

Build:

```bash
scripts/build_mpv_player_jni.sh
```

The script rebuilds and replaces:

- `app/src/arm64_v8a/assets/mpv-libs/arm64-v8a/libplayer.so`
- `app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a/libplayer.so`
