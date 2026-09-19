// Compile the actual wrapper process, pending-frame handoff, reset helper and
// decoder dispatch loop. Platform/codec callbacks are deterministic fakes;
// unlike a GPU-only model this exercises the real frame ownership boundary.
#include "filters/f_android_fel.h"
#include <libavutil/buffer.h>
#include <assert.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#define MPMAX(a, b) ((a) > (b) ? (a) : (b))
#define MP_TIME_MS_TO_NS(ms) ((ms) * 1000000LL)
#define MP_NOPTS_VALUE (-1e20)
#define MP_INFO(...) ((void)0)
#define MP_ERR(...) ((void)0)
#define mp_assert assert
#define MP_THREAD_VOID void
#define MP_THREAD_RETURN() return
enum { STREAM_VIDEO = 1, STREAM_AUDIO, MP_FRAME_VIDEO, MP_FRAME_EOF };
enum { IMGFMT_MEDIACODEC = 1, IMGFMT_SW };
enum { VO_ERROR = -1, VO_NOTIMPL = -3, VO_FALSE = 0, VO_TRUE = 1 };
struct mp_image { int imgfmt; double pts; AVBufferRef *android_fel_staging; };
struct mp_frame { int type; struct mp_image *data; };
#define MP_NO_FRAME ((struct mp_frame){0})
#define MP_EOF_FRAME ((struct mp_frame){.type = MP_FRAME_EOF})
#define MAKE_FRAME(t, p) ((struct mp_frame){t, (void *)(p)})
enum { MP_FRAME_PACKET = 5 };
struct mp_pin { int unused; };
struct mp_filter { void *priv; struct mp_pin *pins[2], *ppins[1]; };
struct mp_codec_params { int unused; };
struct mp_decoder { struct mp_filter *f; };
struct demux_packet { bool segmented; struct mp_codec_params *codec; double start, end; };
struct vo { int unused; };
struct priv {
    bool android_fel, software_el, fel_stage_failed, request_terminate_dec_thread;
    void *queue, *dec_dispatch, *opt_cache;
    struct { struct vo *dr_vo; } stream_info;
    struct { int type; } *header;
    struct mp_filter *decf, *dec_root_filter;
    struct mp_decoder *decoder;
    struct mp_codec_params *codec;
    struct mp_frame fel_stage_frame, decoded_coverart, *reverse_queue, packet;
    int64_t fel_stage_started, fel_stage_ns, fel_stage_max_ns;
    unsigned long long fel_staged, fel_stage_retries;
    double fel_stage_log_at, fel_queue_log_at;
    int fel_queue_peak, coverart_returned, num_reverse_queue, play_dir;
    int cache_lock, attempt_framedrops, packets_without_output, dropped_frames;
    bool attached_picture, preroll_discard, reverse_queue_complete;
    size_t reverse_queue_byte_size;
    struct demux_packet *new_segment;
    double start, end;
    int public;
};

static void read_frame(struct priv *);
static int stage_fel_before_publish(struct priv *, struct mp_frame);
static void reset_fel_staging(struct priv *);
static void decf_process(struct mp_filter *);
static void dec_thread(void *);
static int64_t now_ns, ready_ns;
static int reads, feeds, prepares, conversions, failures, eof_writes, video_writes;
static int allocations, cancellations, dispatch_ticks, async_marks, controls_at_tick;
static bool force_error, ordinary;
static struct priv producer;
static struct mp_filter wrapper, root, decoder_filter;
static struct mp_decoder decoder;
static struct vo vo;
static struct mp_image *output, *request;

static int64_t mp_time_ns(void) { return now_ns; }
static double mp_time_sec(void) { return now_ns / 1e9; }
static void mp_mutex_lock(int *lock) { assert(!*lock); *lock = 1; }
static void mp_mutex_unlock(int *lock) { assert(*lock); *lock = 0; }
static bool mp_pin_in_needs_data(struct mp_pin *pin) { (void)pin; return true; }
static void mp_frame_unref(struct mp_frame *frame)
{
    if (frame->type == MP_FRAME_VIDEO) {
        assert(frame->data && frame->data != request);
        av_buffer_unref(&frame->data->android_fel_staging);
        free(frame->data);
        allocations--;
    }
    *frame = MP_NO_FRAME;
}
static struct mp_frame mp_frame_ref(struct mp_frame frame)
{ (void)frame; abort(); }
static double mp_frame_get_pts(struct mp_frame frame) { return frame.data->pts; }
static void mp_pin_in_write(struct mp_pin *pin, struct mp_frame frame)
{
    (void)pin;
    if (frame.type == MP_FRAME_EOF) { eof_writes++; return; }
    assert(frame.type == MP_FRAME_VIDEO && !output);
    assert(ordinary || (frame.data->android_fel_staging &&
        mp_android_fel_staging_ready(frame.data->android_fel_staging->data)));
    output = frame.data;
    video_writes++;
}
static struct mp_frame mp_pin_out_read(struct mp_pin *pin)
{
    (void)pin;
    assert(!producer.fel_stage_frame.type && !request);
    reads++;
    struct mp_image *img = calloc(1, sizeof(*img));
    assert(img);
    allocations++;
    img->imgfmt = ordinary ? IMGFMT_SW : IMGFMT_MEDIACODEC;
    img->pts = reads / 24.0;
    return (struct mp_frame){MP_FRAME_VIDEO, img};
}
static int vo_prepare_fel_frame(struct vo *v, struct mp_image *image)
{
    assert(v == &vo && (!request || request == image));
    prepares++;
    if (force_error) { request = NULL; return VO_ERROR; }
    if (!image->android_fel_staging) image->android_fel_staging = av_buffer_allocz(1);
    assert(image->android_fel_staging);
    request = image;
    if (now_ns < ready_ns) return VO_FALSE;
    mp_android_fel_staging_complete(image->android_fel_staging->data);
    request = NULL;
    return VO_TRUE;
}
static void vo_cancel_fel_frame(struct vo *v, struct mp_image *image)
{
    assert(v == &vo && image == request);
    request = NULL;
    cancellations++;
}
static void process_output_frame(struct priv *p, struct mp_frame frame)
{ (void)p; assert(frame.type == MP_FRAME_VIDEO); conversions++; }
static bool process_decoded_frame(struct priv *p, struct mp_frame *frame)
{ (void)p; (void)frame; return false; }
static void enqueue_backward_frame(struct priv *p, struct mp_frame frame)
{ (void)p; (void)frame; abort(); }
static void reset_decoder(struct priv *p) { (void)p; abort(); }
static bool decoder_wrapper_reinit(int *p) { (void)p; abort(); }
static void mp_filter_internal_mark_progress(struct mp_filter *f) { (void)f; }
static void mp_filter_internal_mark_failed(struct mp_filter *f) { (void)f; failures++; }
static bool m_config_cache_update(void *cache) { (void)cache; return false; }
static void update_queue_config(struct priv *p) { (void)p; }
static void feed_packet(struct priv *p) { (void)p; feeds++; }
static int update_cached_values(struct priv *p) { (void)p; return 0; }
static int mp_async_queue_get_frames(void *queue) { (void)queue; return eof_writes; }
static void mp_thread_set_name(const char *name) { assert(name); }
static void mp_filter_graph_run(struct mp_filter *f)
{ assert(f == &root); decf_process(&wrapper); }
static void mp_filter_mark_async_progress(struct mp_filter *f)
{ assert(f == &wrapper); async_marks++; }
static void mp_dispatch_queue_process(void *dispatch, double wait)
{
    assert(dispatch == &producer);
    dispatch_ticks++;
    if (producer.fel_stage_frame.type) {
        assert(wait == .002 && !eof_writes && !video_writes);
        now_ns += MP_TIME_MS_TO_NS(2);
        if (controls_at_tick == dispatch_ticks) {
            reset_fel_staging(&producer); // reset/stop runs at dispatch boundary
            producer.request_terminate_dec_thread = true;
        }
    } else {
        assert(isinf(wait)); // old paths and terminal state do not start polling
        producer.request_terminate_dec_thread = true;
    }
}
static void reset(void)
{
    assert(!allocations && !request && !output);
    now_ns = MP_TIME_MS_TO_NS(1000);
    ready_ns = now_ns + MP_TIME_MS_TO_NS(2130);
    reads = feeds = prepares = conversions = failures = eof_writes = video_writes = 0;
    cancellations = dispatch_ticks = async_marks = controls_at_tick = 0;
    ordinary = force_error = false;
    static struct { int type; } header = {STREAM_VIDEO};
    wrapper = (struct mp_filter){.priv = &producer};
    decoder = (struct mp_decoder){.f = &decoder_filter};
    producer = (struct priv){.android_fel = true, .queue = &producer,
        .dec_dispatch = &producer, .decf = &wrapper, .dec_root_filter = &root,
        .decoder = &decoder, .stream_info.dr_vo = &vo, .play_dir = 1};
    producer.header = (void *)&header;
}
static void free_output(void)
{
    struct mp_frame frame = {MP_FRAME_VIDEO, output};
    output = NULL;
    mp_frame_unref(&frame);
}
int main(void)
{
    reset();
    int64_t started = now_ns;
    decf_process(&wrapper);
    assert(now_ns == started && producer.fel_stage_frame.type == MP_FRAME_VIDEO);
    assert(reads == 1 && feeds == 1 && conversions == 1 && !eof_writes);
    dec_thread(&producer); // actual thread loop, simulated 2.13-second cold init
    assert(video_writes == 1 && !failures && !eof_writes);
    assert(reads == 1 && feeds == 1 && conversions == 1 && producer.fel_staged == 1);
    assert(now_ns == ready_ns && dispatch_ticks > 1000 && async_marks > 1000);
    free_output();

    reset();
    controls_at_tick = 10;
    dec_thread(&producer);
    assert(cancellations == 1 && !producer.fel_stage_frame.type && !allocations);
    assert(now_ns < ready_ns && !failures && !eof_writes && !video_writes);
    reset_fel_staging(&producer);
    assert(cancellations == 1); // no double cancel/free

    reset();
    force_error = true;
    decf_process(&wrapper);
    assert(producer.fel_stage_failed && failures == 1 && eof_writes == 1);
    assert(!allocations && reads == 1 && feeds == 1);
    for (int n = 0; n < 30000; n++) {
        decf_process(&wrapper);
        read_frame(&producer);
    }
    assert(eof_writes == 1 && reads == 1 && feeds == 1 && prepares == 1);
    dec_thread(&producer);
    assert(dispatch_ticks == 1 && async_marks == 0);

    reset();
    ordinary = true;
    producer.android_fel = false;
    dec_thread(&producer);
    assert(video_writes == 1 && prepares == 0 && dispatch_ticks == 1 && !async_marks);
    assert(!producer.fel_stage_frame.type && !producer.fel_stage_started);
    free_output();
    puts("PASS: actual wrapper/dispatch: cold-init pending ownership, no extra decode/PTS fix, responsive cancellation, 30000 post-failure requests emit one EOF, default isolation");
    return 0;
}
