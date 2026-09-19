/* Standalone native contract runner; no Android helper APK is required. */
#include "webhtv_hpm.h"
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static uint64_t picture_hash(const WebHpmFrame *f)
{
    uint64_t value = UINT64_C(14695981039346656037);
    for (int c = 0; c < 3; c++) {
        int width = (f->info.width + (c != 0)) >> (c != 0);
        int height = (f->info.height + (c != 0)) >> (c != 0);
        for (int y = 0; y < height; y++) {
            const uint8_t *p = f->data[c] + y * f->stride[c];
            for (int x = 0; x < width * 2; x++)
                value = (value ^ p[x]) * UINT64_C(1099511628211);
        }
    }
    return value;
}

static int decode(WebHpm *a, WebHpm *b, const uint8_t *data, int size, uint64_t *sum)
{
    int offset = 0, frames = 0;
    for (;;) {
        WebHpmFrame fa, fb;
        int used = 0, used_b = 0;
        int result = webhtv_hpm_decode(a, data + offset, size - offset,
                                       INT64_MIN, INT64_MIN, &used, &fa);
        if (result < 0) {
            fprintf(stderr, "decode failed at %d: %s\n", offset, webhtv_hpm_error(a));
            return -1;
        }
        if (b) {
            int rb = webhtv_hpm_decode(b, data + offset, size - offset,
                                       INT64_MIN, INT64_MIN, &used_b, &fb);
            if (rb != result || used != used_b ||
                (result && (picture_hash(&fa) != picture_hash(&fb) || fa.poc != fb.poc))) {
                fprintf(stderr, "interleaved instance mismatch at %d\n", offset);
                return -1;
            }
        }
        offset += used;
        if (result) {
            uint64_t hash = picture_hash(&fa);
            *sum = (*sum ^ hash) * UINT64_C(1099511628211);
            printf("frame=%d poc=%d pts=%" PRId64 " size=%dx%d depth=%d hash=%016" PRIx64 "\n",
                    frames++, fa.poc, fa.pts, fa.info.width, fa.info.height, fa.info.bit_depth, hash);
        } else if (offset == size) {
            /* A nonempty input can consume the last picture without output.
             * Always make a separate empty-input EOS call before finishing. */
            if (used)
                continue;
            break;
        } else if (!used) {
            fprintf(stderr, "decoder made no progress\n");
            return -1;
        }
    }
    return frames;
}

static int malformed(const uint8_t *data, int size)
{
    WebHpm *h = webhtv_hpm_create();
    if (!h)
        return -1;
    for (int length = 1; length < 32 && length < size; length++) {
        WebHpmFrame frame;
        int consumed;
        int result = webhtv_hpm_decode(h, data, length, 0, 0, &consumed, &frame);
        if (result >= 0) {
            fprintf(stderr, "truncated sequence accepted: %d\n", length);
            webhtv_hpm_close(h);
            return -1;
        }
        webhtv_hpm_flush(h);
        if (webhtv_hpm_memory(h))
            return -1;
    }
    webhtv_hpm_close(h);
    puts("PASS truncated sequence and error/flush ownership");
    return 0;
}

int main(int argc, char **argv)
{
    if (argc == 3 && !strcmp(argv[1], "--raw-hash")) {
        unsigned long bytes = strtoul(argv[2], NULL, 10);
        if (!bytes || bytes > 256UL * 1024 * 1024) return 2;
        uint8_t buffer[65536];
        unsigned long left = bytes;
        uint64_t hash = UINT64_C(14695981039346656037);
        int frames = 0;
        for (;;) {
            size_t n = fread(buffer, 1, left < sizeof(buffer) ? left : sizeof(buffer), stdin);
            if (!n) break;
            for (size_t i = 0; i < n; i++)
                hash = (hash ^ buffer[i]) * UINT64_C(1099511628211);
            left -= n;
            if (!left) {
                printf("reference frame=%d hash=%016" PRIx64 "\n", frames++, hash);
                left = bytes;
                hash = UINT64_C(14695981039346656037);
            }
        }
        return ferror(stdin) || left != bytes || !frames ? 1 : 0;
    }
    if (argc < 2) {
        fprintf(stderr, "usage: hpm_contract input.avs3 [--flush] [--interleave] [--malformed]\n");
        return 2;
    }
    int flush = 0, interleave = 0, invalid = 0;
    for (int i = 2; i < argc; i++) {
        if (!strcmp(argv[i], "--flush")) flush = 1;
        else if (!strcmp(argv[i], "--interleave")) interleave = 1;
        else if (!strcmp(argv[i], "--malformed")) invalid = 1;
        else return 2;
    }
    FILE *input = fopen(argv[1], "rb");
    if (!input) return 2;
    if (fseek(input, 0, SEEK_END)) return 2;
    long size = ftell(input);
    if (size <= 0 || size > 64 * 1024 * 1024) return 2;
    rewind(input);
    uint8_t *data = malloc(size);
    if (!data || fread(data, 1, size, input) != (size_t)size) return 2;
    fclose(input);
    if (invalid && malformed(data, (int)size)) return 1;
    WebHpm *a = webhtv_hpm_create();
    WebHpm *b = interleave ? webhtv_hpm_create() : NULL;
    if (!a || (interleave && !b)) return 2;
    clock_t started = clock();
    uint64_t checksum = 0;
    int frames = decode(a, b, data, (int)size, &checksum);
    printf("frames=%d checksum=%016" PRIx64 " memory=%zu cpu_seconds=%.3f\n",
           frames, checksum, webhtv_hpm_memory(a), (double)(clock() - started) / CLOCKS_PER_SEC);
    if (frames <= 0) return 1;
    if (flush) {
        webhtv_hpm_flush(a);
        webhtv_hpm_close(b);
        b = NULL;
        uint64_t replay = 0;
        if (decode(a, NULL, data, (int)size, &replay) != frames || replay != checksum) {
            fprintf(stderr, "flush replay mismatch\n");
            return 1;
        }
        puts("PASS flush replay after closing peer instance");
    }
    webhtv_hpm_close(a);
    webhtv_hpm_close(b);
    free(data);
    return 0;
}
