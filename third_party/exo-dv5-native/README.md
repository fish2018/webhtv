# Exo DV5 native dependencies

This directory owns the native dependency closure for the independent Exo
DV5 renderer. Build and runtime code must use these repository-relative files
and must not reference another WebHTV workspace.

- libplacebo: 7.375.0 / API 375, source commit
  `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`.
- shaderc: Android NDK r29 (`29.0.14206865`) staged source, built with
  `APP_PLATFORM=android-24`, `APP_STL=c++_static`, once per ABI.
- ABIs: `arm64-v8a` and `armeabi-v7a`; archives are not interchangeable.

`MANIFEST.sha256` is the authoritative artifact integrity list. The copied
libplacebo LGPL license and the Android toolchain notice are under `licenses/`.
