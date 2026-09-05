# libmpv Android integration notes

This file records the mpv/libmpv API details that affect the Android MPV player
integration in this project.

## loadfile command

Official syntax from `DOCS/man/input.rst`:

```text
loadfile <url> [<flags> [<index> [<options>]]]
```

Relevant details:

- The second argument is the playlist action, usually `replace`.
- Since mpv 0.38.0, the third argument is a playlist insertion index.
- Per-file options are the fourth argument, formatted as
  `opt1=value1,opt2=value2`.
- If per-file options are needed with `replace`, the third argument must be
  `-1`.

Correct HLS-forced load command:

```java
MPVLib.command(new String[] {
        "loadfile",
        url,
        "replace",
        "-1",
        "demuxer=lavf,demuxer-lavf-format=hls"
});
```

Incorrect command:

```java
MPVLib.command(new String[] {
        "loadfile",
        url,
        "replace",
        "demuxer=lavf,demuxer-lavf-format=hls"
});
```

The incorrect form passes the options string as the insertion index and produces:

```text
The loadfile option must be an integer
```

## State mapping

Do not treat `MPV_EVENT_FILE_LOADED` as Media3 `STATE_READY` by itself. A file can
load and immediately fail before a useful audio/video frame is produced. Prefer
waiting for `MPV_EVENT_PLAYBACK_RESTART` before reporting ready playback.

`MPV_EVENT_END_FILE` can mean success or failure. The bundled JNI now forwards
`mpv_event_end_file.reason` and `mpv_event_end_file.error` through
`MPVLib.endFile(reason, error, errorText)`. Java should use this structured
native event first, and keep mpv logs/properties only as secondary classification
signals for user-facing error messages.

## Vulkan renderer

The OpenGL path remains the default renderer because it is the broadest Android
compatibility path:

```text
vo=gpu
gpu-context=android
opengl-es=yes
```

The Vulkan path is opt-in and should only be enabled when both checks pass:

- bundled `libmpv.so` feature list contains `vulkan`
- device is Android 13+ with OpenGL ES 3.1+ and Vulkan 1.3 hardware feature

When enabled, use:

```text
vo=gpu-next
gpu-context=androidvk
gpu-api=vulkan
```

The bundled native assets intentionally expose only one Android Vulkan
AImageReader path: the `v5.5.6-202608072014` AHardwareBuffer YCbCr-to-RGB GPU
conversion implementation. Options such as
`android-vulkan-aimagereader-backend` and
`android-vulkan-conversion-backend` have been removed; do not add them to the
generated or user `mpv.conf`. The release/acquire flow patch is frame-lifetime
synchronization and is unrelated to a selectable "stable" renderer.

The playback panel's GPU percentage is derived from mpv `vo-passes` GPU
timestamps. The native timing patch includes the separate AImageReader
conversion submission in those passes. It measures this MPV rendering chain
for practical A/B comparison, not whole-device GPU busy time or power draw.

References checked:

- `mpv-android/mpv-android#596`: enabling Vulkan requires native build changes,
  not just Java options. The build needs shaderc, libplacebo Vulkan
  (`-Dvk-proc-addr=enabled`), and mpv `-Dvulkan=enabled`.
- `marlboro-advance/mpvEx`: exposes Vulkan as an experimental decoder/render
  option and gates it behind Android 13+ / Vulkan 1.3 detection.

Do not blindly set `gpu-context=androidvk` with the old bundled native assets.
If native Vulkan is absent, mpv will fail video output initialization. The app
therefore falls back to OpenGL and logs native/device Vulkan availability.

## Current HLS limitation

The existing Exo/Media3 stack in this repo includes the SAMPLE-AES identity and
synthesized-PUSI fixes in the locked Media3 source commit recorded by
`third_party/media-lock.json`. They are no longer reapplied as standalone patch
files, because doing so would apply the same changes twice and break a clean
Media3 rebuild.

Some sources rely on these patches. In logs they can appear to libmpv/FFmpeg as
HLS streams whose media samples are detected as `Video: png`, followed by:

```text
Invalid data found when processing input
no audio or video data played
```

Passing these URLs directly to libmpv is not enough. MPV support for those sources
requires an equivalent local HLS decrypt/remux proxy or native demuxer support.

## Remote Blu-ray ISO timeline

The `webhtv-dvdiso://` stream is byte-backed by the App's HTTP Range reader, but
Blu-ray playback must not be treated as a plain MPEG-TS file. Multi-clip playlists
can reset PTS/DTS between M2TS files, so FFmpeg duration probing can report only
the currently parsed/readahead boundary and ordinary byte seeks can land on the
wrong playback time.

The pinned MPV source therefore applies
`third_party/patches/mpv-stream-cb-disc-controls.patch`. For Blu-ray ISO sessions,
the JNI stream callback exposes the same controls used by MPV's built-in
`stream_bluray.c` and forces the `+disc` wrapper:

- playlist duration from `BLURAY_TITLE_INFO.duration`;
- current playlist time from `bd_tell_time()`;
- time seeks through `bd_seek_time()`;
- chapter start times and MPEG-TS PID language lookup.

`demux_disc` remains responsible for dropping the nested lavf buffers after a
seek, reinitializing the base time, and remapping timestamp discontinuities.
The patch also makes nested lavf recognize a custom `+disc` stream as optical
media, preventing its flush path from issuing a byte seek that would overwrite
the preceding `bd_seek_time()` result.
Do not replace the standard `seek absolute+exact` command with a private direct
stream seek command, because that bypasses `demux_disc`'s reinitialization state.
