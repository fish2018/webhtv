#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="${1:-$ROOT/build/mpv-native/mpv-android/buildscripts/deps/ffmpeg}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/webhtv-avs3-mediacodec.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

# Execute the production functions; only the Android platform calls are faked.
python3 - "$SOURCE" "$WORK/production.inc" <<'PY'
from pathlib import Path
import sys
source = Path(sys.argv[1]) / 'libavcodec'
parts = []
for file, name in [('mediacodecdec.c', 'avs3_set_extradata'),
                   ('mediacodecdec_common.c', 'mediacodec_dec_get_video_codec'),
                   ('mediacodecdec_common.c', 'mediacodec_dec_get_audio_codec')]:
    text = (source / file).read_text()
    start = text.index('static int ' + name + '(')
    end = text.index('\n}\n', start) + 3
    parts.append(text[start:end])
Path(sys.argv[2]).write_text('\n'.join(parts))
PY

"${CC:-clang}" -std=c11 -O1 -g -Wall -Wextra -Werror \
  -fsanitize=address,undefined -fno-omit-frame-pointer -I"$WORK" \
  "$ROOT/scripts/tests/avs3_mediacodec/contract.c" -o "$WORK/contract"
"$WORK/contract"
