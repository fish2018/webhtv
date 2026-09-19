#include <assert.h>
#include <errno.h>
#include <inttypes.h>
#include <pthread.h>
#include <sched.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <unistd.h>

#define AVERROR(e) (-(e))
#define AVERROR_EXTERNAL (-999)
#define AV_LOG_DEBUG 1
#define AV_LOG_ERROR 2
#define av_log(ctx, level, ...) do { (void)(ctx); (void)(level); if (false) fprintf(stderr, __VA_ARGS__); } while (0)
#define ff_mutex_lock pthread_mutex_lock
#define ff_mutex_unlock pthread_mutex_unlock
typedef struct { int unused; } AVCodecContext;
typedef struct MediaCodecDecContext MediaCodecDecContext;
typedef struct { MediaCodecDecContext *owner; } FFAMediaCodec;
struct MediaCodecDecContext {
    atomic_int refcount, hw_buffer_count, serial;
    pthread_mutex_t output_mutex;
    bool output_mutex_initialized, delay_flush;
    FFAMediaCodec *codec;
    int output_buffer_count, draining, flushing, eos, current_input_buffer;
    void *hdr10_plus_metadata;
};
typedef struct {
    MediaCodecDecContext *ctx;
    atomic_int released;
    int serial;
    ssize_t index;
    int64_t pts;
} AVMediaCodecBuffer;
static atomic_int platform_active, release_calls, flush_calls, stop_calls;
static int platform_result;
static void enter_platform(FFAMediaCodec *codec)
{
    // Both validity checks and the actual platform operation must hold one lock.
    assert(pthread_mutex_trylock(&codec->owner->output_mutex) == EBUSY);
    assert(atomic_fetch_add(&platform_active, 1) == 0);
    sched_yield(); // Widen the race with the decoder/VO worker.
}
static int leave_platform(void)
{
    assert(atomic_fetch_sub(&platform_active, 1) == 1);
    return platform_result;
}
static int ff_AMediaCodec_releaseOutputBuffer(FFAMediaCodec *codec, ssize_t index, int render)
{
    (void)index; (void)render;
    enter_platform(codec);
    atomic_fetch_add(&release_calls, 1);
    return leave_platform();
}
static int ff_AMediaCodec_releaseOutputBufferAtTime(FFAMediaCodec *codec, ssize_t index, int64_t time)
{
    (void)time;
    return ff_AMediaCodec_releaseOutputBuffer(codec, index, 1);
}
static int ff_AMediaCodec_flush(FFAMediaCodec *codec)
{
    enter_platform(codec);
    atomic_fetch_add(&flush_calls, 1);
    return leave_platform();
}
static int ff_AMediaCodec_stop(FFAMediaCodec *codec)
{
    enter_platform(codec);
    atomic_fetch_add(&stop_calls, 1);
    return leave_platform();
}
static void mediacodec_packet_props_clear(MediaCodecDecContext *ctx) { (void)ctx; }
static void av_buffer_unref(void **buffer) { *buffer = NULL; }
static void av_freep(void *address) { void **value = address; free(*value); *value = NULL; }
// The context is stack-owned by this fixture; track the real release ordering.
static void ff_mediacodec_dec_unref(MediaCodecDecContext *ctx)
{
    assert(atomic_fetch_sub(&ctx->refcount, 1) > 0);
}
#include "mediacodec_release_under_test.h"
#include "mediacodec_lifecycle_under_test.h"

struct worker { AVMediaCodecBuffer *buffer; MediaCodecDecContext *ctx; int mode; bool close; };
static void *release_worker(void *arg)
{
    struct worker *work = arg;
    if (work->mode == 0) assert(av_mediacodec_release_buffer_status(work->buffer, 1) >= 0);
    if (work->mode == 1) assert(av_mediacodec_render_buffer_at_time(work->buffer, 1234) == 0);
    if (work->mode == 2) mediacodec_buffer_release(work->buffer, NULL);
    return NULL;
}
static void *lifecycle_worker(void *arg)
{
    struct worker *work = arg;
    if (work->close) assert(ff_mediacodec_dec_close(NULL, work->ctx) == 0);
    else assert(mediacodec_dec_flush_codec(NULL, work->ctx) == 0);
    return NULL;
}
static AVMediaCodecBuffer *setup(MediaCodecDecContext *ctx, FFAMediaCodec *codec)
{
    *ctx = (MediaCodecDecContext){.output_mutex_initialized = true, .codec = codec};
    atomic_init(&ctx->refcount, 2); // Decoder and one external frame.
    atomic_init(&ctx->hw_buffer_count, 1);
    atomic_init(&ctx->serial, 1);
    assert(!pthread_mutex_init(&ctx->output_mutex, NULL));
    codec->owner = ctx;
    AVMediaCodecBuffer *buffer = calloc(1, sizeof(*buffer));
    assert(buffer);
    buffer->ctx = ctx;
    buffer->serial = 1;
    atomic_init(&buffer->released, 0);
    atomic_store(&release_calls, 0);
    atomic_store(&flush_calls, 0);
    atomic_store(&stop_calls, 0);
    return buffer;
}
int main(void)
{
    alarm(20); // A stuck mutex is a failed test, not an unbounded test runner.
    for (int close = 0; close < 2; close++) {
        for (int mode = 0; mode < 3; mode++) {
            for (int round = 0; round < 80; round++) {
                MediaCodecDecContext ctx;
                FFAMediaCodec codec;
                AVMediaCodecBuffer *buffer = setup(&ctx, &codec);
                struct worker work = {buffer, &ctx, mode, close};
                pthread_t release, lifecycle;
                assert(!pthread_create(&release, NULL, release_worker, &work));
                assert(!pthread_create(&lifecycle, NULL, lifecycle_worker, &work));
                assert(!pthread_join(release, NULL));
                assert(!pthread_join(lifecycle, NULL));
                assert(atomic_load(&platform_active) == 0);
                assert(atomic_load(&release_calls) <= 1);
                assert(atomic_load(&ctx.hw_buffer_count) == 0);
                if (!close) assert(atomic_load(&flush_calls) == 1 && atomic_load(&ctx.serial) == 2);
                if (mode != 2) {
                    // Invalid/duplicate buffers are discarded, never resubmitted.
                    int calls = atomic_load(&release_calls);
                    assert(av_mediacodec_release_buffer_status(buffer, 1) == 0);
                    mediacodec_buffer_release(buffer, NULL);
                    assert(atomic_load(&release_calls) == calls);
                }
                if (!close) assert(ff_mediacodec_dec_close(NULL, &ctx) == 0);
                assert(atomic_load(&ctx.refcount) == 0);
                assert(!pthread_mutex_destroy(&ctx.output_mutex));
            }
        }
    }
    MediaCodecDecContext ctx;
    FFAMediaCodec codec;
    AVMediaCodecBuffer *buffer = setup(&ctx, &codec);
    platform_result = -1;
    assert(av_mediacodec_release_buffer_status(buffer, 1) == -1);
    assert(!pthread_mutex_trylock(&ctx.output_mutex));
    pthread_mutex_unlock(&ctx.output_mutex);
    assert(mediacodec_dec_flush_codec(NULL, &ctx) == AVERROR_EXTERNAL);
    assert(!pthread_mutex_trylock(&ctx.output_mutex));
    pthread_mutex_unlock(&ctx.output_mutex);
    platform_result = 0;
    mediacodec_buffer_release(buffer, NULL);
    ff_mediacodec_dec_close(NULL, &ctx);
    assert(!pthread_mutex_destroy(&ctx.output_mutex));
    assert(av_mediacodec_release_buffer_status(NULL, 0) == AVERROR(EINVAL));
    alarm(0);
    puts("PASS: 480 output/flush/close races, timed/immediate/final release, stale/duplicate buffers and error unlock");
    return 0;
}
