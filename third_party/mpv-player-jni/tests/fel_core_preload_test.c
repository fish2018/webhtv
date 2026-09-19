// The script appends the production core lookahead, VO request/worker and GPU
// cache selection bodies. Only the codec/GPU/platform are deterministic fakes.
// One available codec output is a regression model, not a measured TV DPB size.
#include "filters/f_android_fel_trace.h"
#include "filters/f_android_fel.h"
#include "video/out/fel_bind_probe.h"
#include <libavutil/buffer.h>
#include <assert.h>
#include <stdlib.h>
#include <string.h>

#define CHECK(c) do { if (!(c)) { \
    fprintf(stderr, "FAIL: %s (line %d)\n", #c, __LINE__); abort(); \
} } while (0)
#define mp_assert CHECK
#define MP_ARRAY_SIZE(a) ((int)(sizeof(a) / sizeof((a)[0])))
#define MPCLAMP(v, lo, hi) ((v) < (lo) ? (lo) : (v) > (hi) ? (hi) : (v))
#define MPMAX(a, b) ((a) > (b) ? (a) : (b))
#define MP_TIME_MS_TO_NS(ms) ((ms) * 1000000LL)
#define MP_NOPTS_VALUE (-1e20)
#define MP_INFO(...) ((void)0)
#define MP_ERR(...) ((void)0)
static void test_warn(const char *format, ...) { (void)format; }
#define MP_WARN(ctx, ...) test_warn(__VA_ARGS__)
enum { VO_TRUE = 1, VO_FALSE = 0, VO_ERROR = -1, VO_NOTIMPL = -3 };
enum { IMGFMT_MEDIACODEC = 1, IMGFMT_YUV420P10 = 2 };
enum { PL_COLOR_SYSTEM_DOLBYVISION = 7 };
enum { VO_CAP_NORETAIN = 1, VO_CAP_UNTIMED = 2, VO_CAP_GPU_DOVI_EL_SW = 4 };
enum { VOCTRL_PREPARE_FEL_FRAME = 1, STATUS_SYNCING = 1 };
enum { VD_ERROR = -1, VD_EOF = 0, VD_PROGRESS = 1, VD_NEW_FRAME = 2, VD_WAIT = 3 };
enum { MP_FRAME_NONE, MP_FRAME_EOF, MP_FRAME_VIDEO, MP_FRAME_OTHER };
#define VO_MAX_REQ_FRAMES 10
#define FEL_OUTPUT_CAPACITY (2 * VO_MAX_REQ_FRAMES + 4)

struct mp_rect { int x0, y0, x1, y1; };
struct mp_image_params {
    int imgfmt, w, h, rotate, p_w, p_h;
    bool vflip;
    struct mp_rect crop;
    struct { void *dovi; int sys; } repr;
    struct { int hdr; } color;
};
struct pixels { unsigned refs; int id; bool released; };
struct mp_image {
    int imgfmt;
    double pts;
    struct mp_image_params params;
    struct pixels *pixels;
    AVBufferRef *android_fel_staging;
    uint64_t android_fel_prepared;
};
struct vo_frame { struct mp_image *current, *frames[VO_MAX_REQ_FRAMES]; int num_frames; };
struct vo;
struct vo_driver { unsigned caps; int (*control)(struct vo *, int, void *); };
struct mp_vo_opts { bool android_dovi_fel; };
struct vo_internal {
    int lock;
    struct mp_image *fel_prepare_image;
    uint64_t fel_prepare_generation;
    int fel_prepare_result;
    int64_t fel_prepare_started, fel_prepare_retry;
    int64_t fel_prepare_phase_started;
    unsigned char fel_prepare_phase;
    bool fel_render_init_attempted, fel_render_initializing;
    int64_t fel_render_init_started;
    bool send_reset;
};
struct vo {
    struct mp_vo_opts extra;
    struct vo_internal *in;
    struct mp_vo_opts *opts;
    struct vo_driver *driver;
    struct mp_fel_trace fel_trace;
    int requested;
};
struct mp_frame { int type; struct mp_image *data; };
struct fake_filter { void *pins[2]; };
struct fake_vf { struct fake_filter *f; bool got_output_eof; };
struct vo_chain { bool is_sparse, is_coverart; struct fake_vf *filter; };
struct MPOpts { struct mp_vo_opts *vo; bool untimed, video_latency_hacks; };
struct MPContext {
    struct vo *video_out;
    struct MPOpts *opts;
    struct vo_chain *vo_chain;
    struct mp_image *saved_frame, *next_frames[VO_MAX_REQ_FRAMES + 1];
    int num_next_frames, video_status, play_dir, max_frames;
    double video_pts, hrseek_pts, playback_pts;
    bool hrseek_backstep, hrseek_active, hrseek_lastframe;
};
struct vk_output {
    struct mp_image *source_frame, **source_aliases;
    int num_source_aliases;
    bool pending;
};
struct aimagereader_vk_stable {
    bool android_fel;
    int output_count, output_index;
    struct vk_output outputs[FEL_OUTPUT_CAPACITY];
};
struct ra_hwdec_mapper { struct mp_image_params src_params, dst_params; };
struct ra_hwdec { int unused; };
struct timer_pool { int unused; };
struct fake_ra { struct vo *vo; void *ra; };
struct priv { struct fake_ra *ra_ctx; };

static int live_images, live_pixels, codec_held, codec_capacity, cursor, total;
static int controls, wakes, polls, control_result, mapper_creations;
static bool reset_during_control, software, unexpected, has_frame, defer_completion;
static int64_t fake_now;
static int64_t initialization_ns;
static struct mp_image *initializing_client;
static struct aimagereader_vk_stable cache;
static struct vo_internal in;
static struct mp_vo_opts vo_opts;
static struct MPOpts opts;
static struct vo_driver driver;
static struct vo vo;
static struct fake_filter filter;
static struct fake_vf vf;
static struct vo_chain chain;
static struct MPContext ctx;

static bool output_has_live_fel_frame(struct aimagereader_vk_stable *, struct vk_output *);
static int select_reusable_output(struct aimagereader_vk_stable *, int *);
static void cancel_fel_prepare(struct vo *);
void vo_cancel_fel_frame(struct vo *, struct mp_image *);
int vo_prepare_fel_frame(struct vo *, struct mp_image *);
static int64_t process_fel_prepare(struct vo *);
#ifdef FEL_RENDER_WARMUP_BASELINE
// Old VO has no renderer phase: emulate only entering/leaving draw_frame.
static bool begin_fel_render_init(struct vo *v, const struct vo_frame *f)
{ (void)v; (void)f; return true; }
static void end_fel_render_init(struct vo *v) { (void)v; }
#else
static bool begin_fel_render_init(struct vo *, const struct vo_frame *);
static void end_fel_render_init(struct vo *);
#endif
static int video_output_image(struct MPContext *, bool *);
static int get_req_frames(struct MPContext *, bool);
static bool hwdec_reconfig(struct priv *, struct ra_hwdec_mapper **,
                           struct timer_pool **, struct ra_hwdec *,
                           const struct mp_image_params *);
static void restore_fel_display_params(struct vo *, struct mp_image *,
                                        struct mp_image_params *);

static void release_codec(struct mp_image *img)
{
    if (!img->pixels->released) {
        img->pixels->released = true;
        if (img->imgfmt == IMGFMT_MEDIACODEC)
            codec_held--;
    }
}
static struct mp_image *new_image(int id)
{
    struct mp_image *img = calloc(1, sizeof(*img));
    CHECK(img);
    img->pixels = calloc(1, sizeof(*img->pixels));
    CHECK(img->pixels);
    img->pixels->refs = 1;
    img->pixels->id = id;
    img->imgfmt = software ? IMGFMT_YUV420P10 : IMGFMT_MEDIACODEC;
    img->params.imgfmt = img->imgfmt;
    img->pts = id / 24.0;
    live_images++;
    live_pixels++;
    if (!software)
        codec_held++;
    return img;
}
static struct mp_image *mp_image_new_ref(struct mp_image *src)
{
    struct mp_image *img = malloc(sizeof(*img));
    CHECK(img);
    *img = *src;
    img->pixels->refs++;
    if (src->android_fel_staging) {
        img->android_fel_staging = av_buffer_ref(src->android_fel_staging);
        CHECK(img->android_fel_staging);
    }
    live_images++;
    return img;
}
static void talloc_free(struct mp_image *img)
{
    if (!img)
        return;
    av_buffer_unref(&img->android_fel_staging);
    if (!--img->pixels->refs) {
        release_codec(img);
        free(img->pixels);
        live_pixels--;
    }
    free(img);
    live_images--;
}
static void mp_image_unrefp(struct mp_image **img) { talloc_free(*img); *img = NULL; }
static void mp_image_setrefp(struct mp_image **dst, struct mp_image *src)
{
    mp_image_unrefp(dst);
    *dst = src ? mp_image_new_ref(src) : NULL;
}
static void mp_mutex_lock(int *lock) { CHECK(!*lock); *lock = 1; }
static void mp_mutex_unlock(int *lock) { CHECK(*lock); *lock = 0; }
static int64_t mp_time_ns(void) { return fake_now; }
static void wakeup_locked(struct vo *v) { CHECK(v->in->lock); }
static void wakeup_core(struct vo *v) { (void)v; wakes++; }
static void update_opts(struct vo *v) { CHECK(!v->in->lock); }
static int vo_get_num_req_frames(struct vo *v) { return v->requested; }
static bool vo_has_frame(struct vo *v) { (void)v; return has_frame; }
static void handle_new_frame(struct MPContext *c) { (void)c; }
static double get_play_end_pts(struct MPContext *c) { (void)c; return MP_NOPTS_VALUE; }
static void mp_set_timeout(struct MPContext *c, double seconds)
{ (void)c; CHECK(seconds == .020); polls++; }
static struct mp_frame mp_pin_out_read(void *pin)
{
    (void)pin;
    if (unexpected)
        return (struct mp_frame){MP_FRAME_OTHER, NULL};
    if (cursor == total)
        return (struct mp_frame){MP_FRAME_EOF, NULL};
    if (!software && codec_held >= codec_capacity)
        return (struct mp_frame){0};
    return (struct mp_frame){MP_FRAME_VIDEO, new_image(cursor++)};
}
static void mp_pin_out_unread(void *pin, struct mp_frame frame)
{ (void)pin; talloc_free(frame.data); }
static void mp_frame_unref(struct mp_frame *frame) { talloc_free(frame->data); }
static const char *mp_frame_type_str(int type) { (void)type; return "fake"; }

static bool mp_image_params_static_equal(const struct mp_image_params *a,
                                          const struct mp_image_params *b)
{
    return a->imgfmt == b->imgfmt && a->w == b->w && a->h == b->h &&
           a->rotate == b->rotate && a->vflip == b->vflip &&
           a->p_w == b->p_w && a->p_h == b->p_h && a->repr.sys == b->repr.sys &&
           !memcmp(&a->crop, &b->crop, sizeof(a->crop));
}
static void ra_hwdec_mapper_free(struct ra_hwdec_mapper **m) { free(*m); *m = NULL; }
static struct ra_hwdec_mapper *ra_hwdec_mapper_create(struct ra_hwdec *h,
                                                     const struct mp_image_params *p)
{
    (void)h;
    struct ra_hwdec_mapper *m = calloc(1, sizeof(*m));
    CHECK(m);
    m->src_params = m->dst_params = *p;
    mapper_creations++;
    return m;
}
static struct timer_pool *timer_pool_create(void *ra)
{ (void)ra; return calloc(1, sizeof(struct timer_pool)); }
static void timer_pool_destroy(struct timer_pool *t) { free(t); }

static void stage_image(struct mp_image *image)
{
    for (int n = 0; n < cache.output_count; n++) {
        if (cache.outputs[n].source_frame &&
            cache.outputs[n].source_frame->pixels == image->pixels) {
            if (!defer_completion) {
                release_codec(image);
                mp_android_fel_staging_complete(image->android_fel_staging->data);
            }
            return;
        }
    }
    int waiting;
    int slot = select_reusable_output(&cache, &waiting);
    CHECK(waiting < 0);
    if (slot < 0) {
        CHECK(cache.output_count < FEL_OUTPUT_CAPACITY);
        slot = cache.output_count++;
    }
    mp_image_unrefp(&cache.outputs[slot].source_frame);
    cache.outputs[slot].source_frame = mp_image_new_ref(image);
    cache.output_index = slot;
    if (!defer_completion) {
        release_codec(image);
        if (image->android_fel_staging)
            mp_android_fel_staging_complete(image->android_fel_staging->data);
    }
}
static int control(struct vo *v, int request, void *data)
{
    CHECK(!v->in->lock); // a blocking GPU must never hold the core's VO lock
    CHECK(request == VOCTRL_PREPARE_FEL_FRAME);
    controls++;
    if (initialization_ns) {
        struct vo_frame *frame = data;
        unsigned char *state = frame->current->android_fel_staging->data;
        // The actual mapper publishes these monotonic phase bits while the
        // VO callback is still running. Poll from the producer in that window,
        // not after the fake mapper has already returned a ready texture.
        __atomic_fetch_or(state, 4, __ATOMIC_RELEASE);
        int64_t end = fake_now + initialization_ns;
        while (fake_now < end) {
            fake_now += end - fake_now < MP_TIME_MS_TO_NS(50)
                ? end - fake_now : MP_TIME_MS_TO_NS(50);
            CHECK(vo_prepare_fel_frame(v, initializing_client) == VO_FALSE);
        }
        __atomic_fetch_or(state, 8, __ATOMIC_RELEASE);
    }
    if (reset_during_control) {
        mp_mutex_lock(&v->in->lock);
        cancel_fel_prepare(v);
        mp_mutex_unlock(&v->in->lock);
    }
    if (control_result == VO_TRUE) {
        struct vo_frame *frame = data;
        for (int n = 0; n < frame->num_frames; n++)
            stage_image(frame->frames[n]);
    }
    return control_result;
}
static void reset_test(void)
{
    CHECK(!live_images && !live_pixels && !codec_held);
    controls = wakes = polls = cursor = 0;
    total = 40;
    codec_capacity = 1;
    fake_now = 1000000000LL;
    initialization_ns = 0;
    initializing_client = NULL;
    control_result = VO_TRUE;
    reset_during_control = software = unexpected = has_frame = defer_completion = false;
    in = (struct vo_internal){.fel_prepare_generation = 1};
    vo_opts = (struct mp_vo_opts){true};
    opts = (struct MPOpts){.vo = &vo_opts};
    driver = (struct vo_driver){VO_CAP_GPU_DOVI_EL_SW, control};
    vo = (struct vo){.in = &in, .opts = &vo_opts, .extra = vo_opts, .driver = &driver, .requested = 2};
    mp_fel_trace_init(&vo.fel_trace);
    atomic_store(&vo.fel_trace.enabled, true);
    vf = (struct fake_vf){.f = &filter};
    chain = (struct vo_chain){.filter = &vf};
    ctx = (struct MPContext){.video_out = &vo, .opts = &opts, .vo_chain = &chain,
        .play_dir = 1, .max_frames = -1, .video_pts = 1, .playback_pts = MP_NOPTS_VALUE};
    cache = (struct aimagereader_vk_stable){.android_fel = true,
        .output_count = 4, .output_index = -1};
}
static void cleanup(void)
{
    mp_mutex_lock(&in.lock);
    cancel_fel_prepare(&vo);
    mp_mutex_unlock(&in.lock);
    mp_image_unrefp(&ctx.saved_frame);
    for (int n = 0; n < ctx.num_next_frames; n++)
        mp_image_unrefp(&ctx.next_frames[n]);
    for (int n = 0; n < cache.output_count; n++)
        mp_image_unrefp(&cache.outputs[n].source_frame);
    CHECK(!live_images && !live_pixels && !codec_held);
}
static void test_lookahead(bool fel, int requested, bool sw)
{
    reset_test();
    vo.extra.android_dovi_fel = fel;
    vo.requested = requested;
    software = sw;
    bool eof = false;
    int presented = 0;
    for (int iteration = 0; iteration < 1000; iteration++) {
        int result = video_output_image(&ctx, &eof);
        CHECK(result != VD_ERROR);
        process_fel_prepare(&vo);
        if (result == VD_NEW_FRAME) {
            for (int n = 0; n < ctx.num_next_frames; n++) {
                CHECK(ctx.next_frames[n]->pixels->id == presented + n);
                if (fel && !sw) {
                    CHECK(ctx.next_frames[n]->android_fel_staging);
                    stage_image(ctx.next_frames[n]); // normal VO draw
                }
            }
            talloc_free(ctx.next_frames[0]);
            ctx.num_next_frames--;
            memmove(ctx.next_frames, ctx.next_frames + 1,
                    ctx.num_next_frames * sizeof(ctx.next_frames[0]));
            presented++;
        }
        if (eof && !ctx.num_next_frames)
            break;
        fake_now += 1000000;
    }
    if (fel || sw) {
        CHECK(presented == total && eof);
        CHECK(get_req_frames(&ctx, false) == requested);
    } else {
        CHECK(!presented && cursor == 1 && ctx.num_next_frames == 1);
        CHECK(!controls && !polls); // old ordering deadlocks this output model
    }
    if (sw)
        CHECK(!controls && !atomic_load(&vo.fel_trace.core_prepare_requests));
    cleanup();
}
static void test_async_lifecycle(void)
{
    reset_test();
    struct mp_image *a = new_image(1), *b = new_image(2);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE && !controls);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE && !b->android_fel_staging);
    control_result = VO_FALSE;
    CHECK(process_fel_prepare(&vo) == fake_now + MP_TIME_MS_TO_NS(5));
    CHECK(controls == 1);
    process_fel_prepare(&vo);
    CHECK(controls == 1); // no busy retry
    fake_now += MP_TIME_MS_TO_NS(5);
    control_result = VO_TRUE;
    CHECK(!process_fel_prepare(&vo) && wakes == 1);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_TRUE);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_TRUE && controls == 2);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    process_fel_prepare(&vo);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_TRUE);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_TRUE); // A/B do not evict completion
    CHECK(atomic_load(&vo.fel_trace.core_prepare_requests) == 2);
    CHECK(atomic_load(&vo.fel_trace.core_prepare_completed) == 2);
    talloc_free(a); talloc_free(b);
    cleanup();

    reset_test();
    a = new_image(1);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    in.send_reset = true;
    process_fel_prepare(&vo);
    CHECK(!controls);
    in.send_reset = false;
    reset_during_control = true;
    process_fel_prepare(&vo);
    CHECK(!in.fel_prepare_image && !a->android_fel_prepared);
    CHECK(!atomic_load(&vo.fel_trace.core_prepare_completed));
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    reset_during_control = false;
    process_fel_prepare(&vo);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_TRUE);
    talloc_free(a);
    cleanup();

    reset_test();
    a = new_image(1);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    fake_now += MP_TIME_MS_TO_NS(750);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_ERROR);
    CHECK(!in.fel_prepare_image && !controls);
    CHECK(atomic_load(&vo.fel_trace.core_prepare_errors) == 1);
    driver.caps = 0;
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_NOTIMPL);
    talloc_free(a);
    cleanup();
}
static void test_deferred_handoff(void)
{
    reset_test();
    struct mp_image *a = new_image(1), *b = new_image(2);
    defer_completion = true;
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    // A valid texture is not permission to publish an unreturned codec image.
    // This is the >100 ms copy case from log41, without real-time sleeps.
    CHECK(process_fel_prepare(&vo) == fake_now + MP_TIME_MS_TO_NS(5));
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    CHECK(!a->android_fel_prepared && !a->pixels->released && !wakes);
    fake_now += MP_TIME_MS_TO_NS(5);
    defer_completion = false;
    CHECK(process_fel_prepare(&vo) == 0);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_TRUE);
    CHECK(!in.fel_prepare_image && a->pixels->released);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE && b->android_fel_staging);
    // A cached older frame must not consume B's outstanding request.
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_TRUE);
    CHECK(in.fel_prepare_image->pixels == b->pixels);
    process_fel_prepare(&vo);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_TRUE && !in.fel_prepare_image);
    talloc_free(a); talloc_free(b);
    cleanup();

    reset_test();
    a = new_image(1); b = new_image(2);
    a->android_fel_prepared = in.fel_prepare_generation;
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    process_fel_prepare(&vo);
    // A ready fast path must still acknowledge its own in-flight request.
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_TRUE && !in.fel_prepare_image);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE && b->android_fel_staging);
    process_fel_prepare(&vo);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_TRUE);
    talloc_free(a); talloc_free(b);
    cleanup();
}

static void test_cold_initialization(void)
{
    reset_test();
    struct mp_image *a = new_image(1);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    initializing_client = a;
    initialization_ns = MP_TIME_MS_TO_NS(2130); // log42 measured cold map cost
    process_fel_prepare(&vo);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_TRUE);
    CHECK(!in.fel_prepare_image && a->pixels->released);
    CHECK(atomic_load(&vo.fel_trace.core_prepare_errors) == 0);
    CHECK(atomic_load(&vo.fel_trace.core_prepare_completed) == 1);
    talloc_free(a);
    cleanup();
}

static void test_diagnostic_deadline(void)
{
    reset_test();
    struct mp_image *a = new_image(1);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    mp_android_fel_staging_begin_probe(a->android_fel_staging->data);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    fake_now += MP_FEL_PROBE_TIMEOUT_NS - 1;
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    mp_android_fel_staging_begin_probe(a->android_fel_staging->data);
    fake_now++;
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_ERROR);
    talloc_free(a);
    cleanup();

    reset_test();
    a = new_image(1);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    mp_android_fel_staging_begin_probe(a->android_fel_staging->data);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    fake_now += MP_TIME_MS_TO_NS(4000);
    mp_android_fel_staging_end_probe(a->android_fel_staging->data);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    fake_now += MP_ANDROID_FEL_FRAME_TIMEOUT_NS;
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_ERROR);
    talloc_free(a);
    cleanup();
}

static void test_initialization_deadlines_and_cancel(void)
{
    reset_test();
    struct mp_image *a = new_image(1), *b = new_image(2);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    mp_android_fel_staging_begin_init(a->android_fel_staging->data);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    fake_now += MP_ANDROID_FEL_INIT_TIMEOUT_NS - 1;
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    // Same phase / repeated polling cannot extend a hung initialization.
    mp_android_fel_staging_begin_init(a->android_fel_staging->data);
    fake_now++;
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_ERROR);
    CHECK(!in.fel_prepare_image && !a->pixels->released);
    CHECK(!mp_android_fel_staging_ready(a->android_fel_staging->data));
    talloc_free(a); talloc_free(b);
    cleanup();

    reset_test();
    a = new_image(1); b = new_image(2);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    mp_android_fel_staging_begin_init(a->android_fel_staging->data);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    fake_now += MP_TIME_MS_TO_NS(2130);
    mp_android_fel_staging_end_init(a->android_fel_staging->data);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    fake_now += MP_ANDROID_FEL_FRAME_TIMEOUT_NS - 1;
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    mp_android_fel_staging_end_init(a->android_fel_staging->data);
    fake_now++;
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_ERROR); // steady state still 750ms
    talloc_free(a); talloc_free(b);
    cleanup();

    reset_test();
    a = new_image(1); b = new_image(2);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    vo_cancel_fel_frame(&vo, b); // another frame must not cancel A
    CHECK(in.fel_prepare_image && !b->android_fel_staging);
    uint64_t generation = in.fel_prepare_generation;
    vo_cancel_fel_frame(&vo, a);
    CHECK(!in.fel_prepare_image && in.fel_prepare_generation != generation);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    process_fel_prepare(&vo);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_TRUE);
    talloc_free(a); talloc_free(b);
    cleanup();
}

static bool render_begin(struct mp_image *image)
{
    struct vo_frame frame = {.current = image};
    mp_mutex_lock(&in.lock);
    bool result = begin_fel_render_init(&vo, &frame);
    mp_mutex_unlock(&in.lock);
    return result;
}

static void render_end(void)
{
    mp_mutex_lock(&in.lock);
    end_fel_render_init(&vo);
    mp_mutex_unlock(&in.lock);
}

static void test_render_initialization(void)
{
    reset_test();
    struct mp_image *a = new_image(1), *b = new_image(2);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    fake_now += MP_TIME_MS_TO_NS(100); // spent before first draw, not forgiven
    CHECK(render_begin(a));
    fake_now += MP_TIME_MS_TO_NS(900);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE); // old VO times out here
    render_end();
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    CHECK(!render_begin(a)); // subsequent draws cannot extend the deadline
    process_fel_prepare(&vo);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_TRUE);
    CHECK(!atomic_load(&vo.fel_trace.core_prepare_errors));
    talloc_free(a); talloc_free(b); cleanup();

    // Entire initialization between producer polls; preserve time spent before
    // it and do not grant another full frame deadline when it finishes.
    reset_test();
    a = new_image(1); b = new_image(2);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    fake_now += MP_TIME_MS_TO_NS(100);
    CHECK(render_begin(a));
    fake_now += MP_TIME_MS_TO_NS(1200);
    render_end();
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    fake_now += MP_TIME_MS_TO_NS(650) - 1;
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    fake_now++;
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_ERROR);
    talloc_free(a); talloc_free(b); cleanup();

    // A request first arriving during draw receives only its actual wait time.
    reset_test();
    a = new_image(1); b = new_image(2);
    CHECK(render_begin(a));
    fake_now += MP_TIME_MS_TO_NS(200);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    fake_now += MP_TIME_MS_TO_NS(900);
    render_end();
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    fake_now += MP_ANDROID_FEL_FRAME_TIMEOUT_NS;
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_ERROR);
    talloc_free(a); talloc_free(b); cleanup();

    // The renderer's hard limit starts at draw, not at each request/poll.
    reset_test();
    a = new_image(1); b = new_image(2);
    CHECK(render_begin(a));
    fake_now += MP_TIME_MS_TO_NS(200);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    fake_now += MP_ANDROID_FEL_INIT_TIMEOUT_NS - MP_TIME_MS_TO_NS(200) - 1;
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    CHECK(!render_begin(a));
    fake_now++;
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_ERROR);
    render_end();
    talloc_free(a); talloc_free(b); cleanup();

    // Even a delayed producer cannot miss a >10s warmup failure.
    reset_test();
    a = new_image(1); b = new_image(2);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    CHECK(render_begin(a));
    fake_now += MP_ANDROID_FEL_INIT_TIMEOUT_NS;
    render_end();
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_ERROR);
    CHECK(atomic_load(&vo.fel_trace.core_prepare_errors) == 1);
    talloc_free(a); talloc_free(b); cleanup();

    reset_test();
    a = new_image(1); b = new_image(2);
    CHECK(vo_prepare_fel_frame(&vo, a) == VO_FALSE);
    CHECK(render_begin(a));
    vo_cancel_fel_frame(&vo, a);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    fake_now += MP_TIME_MS_TO_NS(900);
    render_end();
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_FALSE);
    process_fel_prepare(&vo);
    CHECK(vo_prepare_fel_frame(&vo, b) == VO_TRUE);
    CHECK(!render_begin(a)); // cancel/ordinary seek does not repeat cold draw
    talloc_free(a); talloc_free(b); cleanup();

    reset_test();
    a = new_image(1);
    vo.extra.android_dovi_fel = false;
    CHECK(!render_begin(a));
    vo.extra.android_dovi_fel = true;
    driver.caps = 0;
    CHECK(!render_begin(a));
    driver.caps = VO_CAP_GPU_DOVI_EL_SW;
    a->imgfmt = IMGFMT_YUV420P10;
    CHECK(!render_begin(a));
    a->imgfmt = IMGFMT_MEDIACODEC;
    CHECK(!in.fel_render_init_attempted);
    CHECK(render_begin(a));
    render_end();
    talloc_free(a); cleanup();
}

static void test_seek_and_errors(void)
{
    reset_test();
    ctx.hrseek_active = true;
    ctx.hrseek_lastframe = true;
    ctx.video_status = STATUS_SYNCING;
    ctx.hrseek_pts = 10;
    total = 8;
    bool eof = false;
    int result = VD_WAIT;
    for (int n = 0; n < 100 && result != VD_NEW_FRAME; n++) {
        result = video_output_image(&ctx, &eof);
        CHECK(result != VD_ERROR);
        process_fel_prepare(&vo);
        fake_now += 1000000;
    }
    CHECK(eof && result == VD_NEW_FRAME && ctx.num_next_frames == 1);
    CHECK(ctx.next_frames[0]->pixels->id == 7 && !ctx.saved_frame);
    cleanup();

    reset_test();
    CHECK(video_output_image(&ctx, &eof) == VD_PROGRESS);
    CHECK(video_output_image(&ctx, &eof) == VD_WAIT);
    control_result = VO_ERROR;
    process_fel_prepare(&vo);
    CHECK(video_output_image(&ctx, &eof) == VD_ERROR);
    cleanup();
}
static void test_leases_and_crop(void)
{
    reset_test();
    struct mp_image *a = new_image(1);
    a->android_fel_staging = av_buffer_alloc(1);
    CHECK(a->android_fel_staging);
    struct vk_output *o = &cache.outputs[0];
    o->source_frame = mp_image_new_ref(a);
    CHECK(output_has_live_fel_frame(&cache, o));
    struct mp_image *aliases[] = {mp_image_new_ref(a)};
    o->source_aliases = aliases;
    o->num_source_aliases = 1;
    talloc_free(a);
    CHECK(!output_has_live_fel_frame(&cache, o));
    a = mp_image_new_ref(aliases[0]);
    CHECK(output_has_live_fel_frame(&cache, o));
    int waiting;
    cache.output_index = 3;
    CHECK(select_reusable_output(&cache, &waiting) == 1 && waiting < 0);
    cache.android_fel = false;
    CHECK(select_reusable_output(&cache, &waiting) == 0);
    cache.android_fel = true;
    talloc_free(a);
    talloc_free(aliases[0]);
    o->num_source_aliases = 0;
    struct fake_ra ra = {.vo = &vo};
    struct priv p = {.ra_ctx = &ra};
    struct ra_hwdec hwdec = {0};
    struct ra_hwdec_mapper *mapper = NULL;
    struct timer_pool *timer = NULL;
    struct mp_image_params params = {.imgfmt = IMGFMT_MEDIACODEC, .w = 3840,
        .h = 2160, .crop = {0, 0, 3840, 2160}, .p_w = 1, .p_h = 1};
    mapper_creations = 0;
    CHECK(hwdec_reconfig(&p, &mapper, &timer, &hwdec, &params));
    params.crop = (struct mp_rect){40, 50, 3000, 2000};
    params.rotate = 90; params.vflip = true; params.p_w = 2;
    CHECK(hwdec_reconfig(&p, &mapper, &timer, &hwdec, &params));
    CHECK(mapper_creations == 1); // display geometry cannot discard staged pixels
    CHECK(mapper->src_params.repr.sys == PL_COLOR_SYSTEM_DOLBYVISION);
    // The producer stages pure BL. Inheriting EL RPU must not invalidate pixels.
    params.repr.sys = PL_COLOR_SYSTEM_DOLBYVISION;
    params.repr.dovi = &params;
    params.color.hdr = 500;
    CHECK(hwdec_reconfig(&p, &mapper, &timer, &hwdec, &params));
    CHECK(mapper_creations == 1 && mapper->dst_params.repr.dovi == &params);
    struct mp_image display = {.imgfmt = IMGFMT_MEDIACODEC, .params = params};
    struct mp_image_params storage = mapper->dst_params;
    struct mp_rect storage_crop = storage.crop;
    storage.imgfmt = IMGFMT_YUV420P10; // simulate the mapper's distinct output format
    restore_fel_display_params(&vo, &display, &storage);
    CHECK(storage.rotate == 90 && storage.vflip && storage.p_w == 2);
    CHECK(storage.imgfmt == IMGFMT_YUV420P10 && storage.w == 3840);
    CHECK(!memcmp(&storage.crop, &storage_crop, sizeof(storage_crop)));
    display.params.repr.sys = 3; // BL-only interpretation without synthetic RPU
    restore_fel_display_params(&vo, &display, &storage);
    CHECK(storage.repr.sys == 3);
    params.w = 1920;
    CHECK(hwdec_reconfig(&p, &mapper, &timer, &hwdec, &params));
    CHECK(mapper_creations == 2); // actual pixel format is never ignored
    vo.extra.android_dovi_fel = false;
    storage.rotate = 0;
    restore_fel_display_params(&vo, &display, &storage);
    CHECK(storage.rotate == 0); // never override another path's presentation
    CHECK(hwdec_reconfig(&p, &mapper, &timer, &hwdec, &params));
    params.crop.x0++;
    CHECK(hwdec_reconfig(&p, &mapper, &timer, &hwdec, &params));
    CHECK(mapper_creations == 4); // ordinary mapping contract unchanged
    ra_hwdec_mapper_free(&mapper);
    timer_pool_destroy(timer);
    cleanup();
}
int main(void)
{
    test_render_initialization();
    test_cold_initialization();
    test_initialization_deadlines_and_cancel();
    test_diagnostic_deadline();
    test_lookahead(false, 2, false);
    test_lookahead(true, 2, false);
    test_lookahead(true, 6, false);
    test_lookahead(true, 10, false);
    test_lookahead(false, 2, true);
    test_lookahead(true, 2, true);
    test_async_lifecycle();
    test_deferred_handoff();
    test_seek_and_errors();
    test_leases_and_crop();
    puts("PASS: actual FEL core/VO functions: limited-output progress, 2/6/10-frame "
         "lookahead, async retry/timeout/reset, preroll EOF, leases and crop isolation");
    return 0;
}
