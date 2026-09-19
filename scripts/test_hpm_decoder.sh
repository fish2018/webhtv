#!/usr/bin/env bash
# Compare the player boundary with an independently built HPM reference binary.
# Stream raw pictures through a FIFO: the 4K128 oracle needs no 3 GB YUV file.
set -euo pipefail
if [[ $# != 4 ]]; then
  echo 'Usage: bash scripts/test_hpm_decoder.sh INPUT_AVS3 REFERENCE_DECODER HPM_CONTRACT OUTPUT_DIR' >&2
  exit 2
fi
python3 - "$@" <<'PY'
from pathlib import Path
import hashlib, json, os, re, subprocess, sys, time

sample, reference, decoder, output = map(lambda p: Path(p).resolve(), sys.argv[1:])
output.mkdir(parents=True, exist_ok=True)
fifo = output / 'reference.yuv.fifo'
if fifo.exists():
    raise SystemExit('Refusing to replace an existing oracle FIFO: ' + str(fifo))
os.mkfifo(fifo)
keep = os.open(fifo, os.O_RDWR | os.O_NONBLOCK)
try:
    # This bounded contract sample is 3840x2160 planar YUV420, 16-bit storage.
    with fifo.open('rb') as pipe, (output / 'reference-hashes.log').open('w') as hashes:
        consumer = subprocess.Popen([str(decoder), '--raw-hash', str(3840 * 2160 * 3)],
                                    stdin=pipe, stdout=hashes)
    started = time.monotonic()
    with (output / 'reference-decode.log').open('w') as log:
        result = subprocess.run([str(reference), '-i', str(sample), '-o', str(fifo), '-v', '0'],
                                stdout=log, stderr=subprocess.STDOUT)
    os.close(keep)
    keep = None
    consumer_result = consumer.wait()
    if result.returncode or consumer_result:
        raise SystemExit('Reference decode/hash failed; inspect retained logs')
    oracle_seconds = time.monotonic() - started
finally:
    if keep is not None:
        os.close(keep)
    fifo.unlink()

with (output / 'adapter-contract.log').open('w') as log:
    subprocess.run([str(decoder), str(sample), '--interleave', '--flush'],
                   stdout=log, stderr=subprocess.STDOUT, check=True)
oracle = re.findall(r'reference frame=\d+ hash=([0-9a-f]+)', (output / 'reference-hashes.log').read_text())
actual = re.findall(r'(?:^|\s)frame=\d+ poc=\S+ .* hash=([0-9a-f]+)', (output / 'adapter-contract.log').read_text(), re.M)
if len(oracle) != 128 or actual != oracle + oracle:
    raise SystemExit('Pixel/frame count mismatch against original HPM (including flush replay)')
record = dict(sample_sha256=hashlib.sha256(sample.read_bytes()).hexdigest(),
              reference_sha256=hashlib.sha256(reference.read_bytes()).hexdigest(),
              adapter_sha256=hashlib.sha256(decoder.read_bytes()).hexdigest(),
              frames=128, pixel_match=True, interleaved_instances=True,
              close_peer_then_flush_replay=True, oracle_seconds=oracle_seconds)
(output / 'result.json').write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(record, indent=2))
PY
