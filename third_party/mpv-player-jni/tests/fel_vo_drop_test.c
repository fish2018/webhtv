// Compile real vo.c::render_frame and gpu-next's dropped-frame preparation.
// The fake codec has one free output beyond its DPB. A future frame occupies
// that output until GPU staging returns it; retaining it after a VO drop must
// not make the core's two-frame lookahead wait for itself.
#include "filters/f_android_fel_trace.h"
#include "filters/f_android_fel.h"
#include <assert.h>
#include <stdlib.h>
#include <string.h>

#define CHECK(c) do { if (!(c)) { \
    fprintf(stderr, "FAIL: %s (line %d)\n", #c, __LINE__); exit(1); \
} } while (0)
#define mp_assert CHECK
#define MPMAX(a, b) ((a) > (b) ? (a) : (b))
#define MP_TIME_MS_TO_NS(ms) ((ms) * 1000000LL)
#define MP_NOPTS_VALUE (-1e20)
#define MP_WARN(...) ((void)0)
#define MP_INFO(...) ((void)0)
#define MP_ERR(...) ((void)0)
#define MP_VERBOSE(...) ((void)0)
#define MP_STATS(...) ((void)0)
enum { VO_TRUE = 1, VO_FALSE = 0, VO_ERROR = -1, VO_NOTIMPL = -3 };
enum { IMGFMT_MEDIACODEC = 1, IMGFMT_YUV420P10 = 2 };
enum { VO_CAP_FRAMEDROP = 1, VO_CAP_NORETAIN = 2,
       VO_CAP_FRAMEOWNER = 4, VO_CAP_GPU_DOVI_EL_SW = 8 };
enum { VOCTRL_PREPARE_FEL_FRAME = 1, RA_HWDEC_MAP_RETRY = -2 };

struct mp_image_params { int imgfmt; };
struct mp_image {
    int imgfmt;
    double pts;
    struct mp_image_params params;
    bool staged;
};
struct vo_frame {
    int64_t pts, duration;
    bool display_synced, can_drop, repeat;
    int num_vsyncs, num_frames, frame_id;
    double vsync_offset, vsync_interval;
    double ideal_frame_vsync, ideal_frame_vsync_duration;
    struct mp_image *current, *frames[4];
};
struct vo_internal {
    int lock, wakeup;
    struct vo_frame *current_frame, *frame_queued;
    bool paused, hasframe, hasframe_rendered, dropped_frame;
    bool expecting_vsync, request_redraw, want_redraw, rendering, visible;
    int64_t prev_vsync, flip_queue_offset, drop_count;
    int64_t drop_late_total, drop_late_max;
    uint64_t drop_diag_frames;
    struct mp_image *fel_prepare_image;
    int fel_prepare_result;
    int64_t fel_prepare_phase_started, fel_render_init_started;
    bool fel_render_init_attempted, fel_render_initializing;
    void *stats;
};
struct vo;
struct vo_vsync_info { int64_t last_queue_display_time, skipped_vsyncs; };
struct vo_driver {
    unsigned caps;
    int (*control)(struct vo *, uint32_t, void *);
    bool (*draw_frame)(struct vo *, struct vo_frame *);
    void (*flip_page)(struct vo *);
    void (*get_vsync)(struct vo *, struct vo_vsync_info *);
};
struct mp_vo_opts { bool android_dovi_fel; };
struct vo {
    struct mp_vo_opts extra;
    struct vo_internal *in;
    struct mp_vo_opts *opts;
    struct vo_driver *driver;
    struct mp_fel_trace fel_trace;
    void *priv;
};
struct ra_hwdec_mapper { int unused; };
struct timer_pool { int unused; };
struct ra_hwdec_mapper_driver { int unused; };
struct ra_hwdec_driver { struct ra_hwdec_mapper_driver *mapper; };
struct ra_hwdec { struct ra_hwdec_driver *driver; };
struct priv {
    int hwdec_ctx;
    struct ra_hwdec_mapper *hwdec_mapper;
    struct timer_pool *hwdec_timer;
    bool deferred, available;
};
static const struct vo_driver video_out_mediacodec_embed = {0};
static struct ra_hwdec_mapper_driver mapper_driver;
static struct ra_hwdec_driver hwdec_driver = {&mapper_driver};
static struct ra_hwdec hwdec = {&hwdec_driver};
static struct priv *active_gpu;
static struct vo *active_vo;
static int codec_outputs, copies, map_calls, controls, draws, flips;
static int fail_map, backend_errors, core_wakeups;
static int64_t fake_now = 1000000000LL;

static int64_t mp_time_ns(void) { return fake_now; }
static void mp_mutex_lock(int *lock) { CHECK(!*lock); *lock = 1; }
static void mp_mutex_unlock(int *lock) { CHECK(*lock); *lock = 0; }
static void mp_cond_broadcast(int *cond) { (void)cond; }
static void update_display_fps(struct vo *vo) { (void)vo; }
static void wakeup_core(struct vo *vo) { (void)vo; core_wakeups++; }
static void stats_time_start(void *stats, const char *name) { (void)stats; (void)name; }
static void stats_time_end(void *stats, const char *name) { (void)stats; (void)name; }
static void wait_until(struct vo *vo, int64_t time) { (void)vo; (void)time; }
static void update_vsync_timing_after_swap(struct vo *vo, struct vo_vsync_info *info)
{ (void)vo; (void)info; }
static void vo_report_backend_error(struct vo *vo)
{ CHECK(!vo->in->lock); backend_errors++; }
static struct vo_frame *vo_frame_ref(struct vo_frame *frame)
{
    struct vo_frame *copy = malloc(sizeof(*copy));
    CHECK(copy);
    *copy = *frame;
    return copy;
}
static void talloc_free(void *ptr) { free(ptr); }
static struct ra_hwdec *ra_hwdec_get(int *ctx, int format)
{
    (void)ctx;
    return active_gpu->available && format == IMGFMT_MEDIACODEC ? &hwdec : NULL;
}
static bool hwdec_driver_uses_deferred_dst_params(
    struct priv *p, const struct ra_hwdec_mapper_driver *driver)
{ return driver && p->deferred; }
static int preload_hwdec_image(struct priv *p, struct ra_hwdec_mapper **mapper,
                               struct timer_pool **timer, struct ra_hwdec *hw,
                               struct mp_image *image, bool *preloaded, bool measure)
{
    (void)p; (void)mapper; (void)timer; (void)hw; (void)measure;
    CHECK(!active_vo->in->lock); // Never wait for a Surface with the VO lock held.
    map_calls++;
    if (fail_map) return fail_map;
    if (!image->staged) {
        CHECK(codec_outputs > 0);
        codec_outputs--;
        copies++;
        image->staged = true; // Retain independent pixels, not a raw codec output.
    }
    *preloaded = true;
    return 1;
}

static int preload_dropped_fel_frame(struct vo *vo, struct vo_frame *frame);
static bool begin_fel_render_init(struct vo *vo, const struct vo_frame *frame);
static void end_fel_render_init(struct vo *vo);
static bool render_frame(struct vo *vo);
#ifdef FEL_VO_OLD_PATH
// Only used when compiling the unmodified scheduler to demonstrate failure.
static int preload_dropped_fel_frame(struct vo *vo, struct vo_frame *frame)
{ (void)vo; (void)frame; return VO_NOTIMPL; }
#endif

static int fake_control(struct vo *vo, uint32_t command, void *data)
{
    CHECK(command == VOCTRL_PREPARE_FEL_FRAME);
    CHECK(!vo->in->lock && vo->in->rendering);
    controls++;
    return preload_dropped_fel_frame(vo, data);
}
static bool fake_draw(struct vo *vo, struct vo_frame *frame)
{
    CHECK(!vo->in->lock);
    bool fel = vo->extra.android_dovi_fel &&
        (vo->driver->caps & VO_CAP_GPU_DOVI_EL_SW) &&
        frame->current->imgfmt == IMGFMT_MEDIACODEC;
    if (!draws) CHECK(vo->in->fel_render_initializing == fel);
    draws++;
    return true;
}
static void fake_flip(struct vo *vo) { (void)vo; flips++; }

struct fixture {
    struct vo vo;
    struct vo_internal in;
    struct mp_vo_opts opts;
    struct vo_driver driver;
    struct priv gpu;
    struct mp_image images[2];
};
static void init_fixture(struct fixture *f)
{
    memset(f, 0, sizeof(*f));
    codec_outputs = 1;
    copies = map_calls = controls = draws = flips = 0;
    fail_map = backend_errors = core_wakeups = 0;
    f->opts.android_dovi_fel = true;
    f->driver = (struct vo_driver) {
        .caps = VO_CAP_GPU_DOVI_EL_SW, .control = fake_control,
        .draw_frame = fake_draw, .flip_page = fake_flip,
    };
    f->gpu.deferred = f->gpu.available = true;
    f->vo = (struct vo) { .in = &f->in, .opts = &f->opts, .extra = f->opts,
                         .driver = &f->driver, .priv = &f->gpu };
    mp_fel_trace_init(&f->vo.fel_trace);
    atomic_store(&f->vo.fel_trace.enabled, true);
    active_gpu = &f->gpu;
    active_vo = &f->vo;
    f->in.hasframe = f->in.hasframe_rendered = true;
    f->in.prev_vsync = fake_now - MP_TIME_MS_TO_NS(50);
    f->images[0] = (struct mp_image) {
        .imgfmt = IMGFMT_MEDIACODEC, .pts = 19.144, .staged = true,
    };
    f->images[1] = (struct mp_image) {
        .imgfmt = IMGFMT_MEDIACODEC, .pts = 19.186,
    };
    struct vo_frame frame = {
        .pts = fake_now - MP_TIME_MS_TO_NS(80), .duration = MP_TIME_MS_TO_NS(40),
        .can_drop = true, .num_vsyncs = 1, .num_frames = 2, .frame_id = 4,
        .current = &f->images[0], .frames = {&f->images[0], &f->images[1]},
    };
    f->in.frame_queued = vo_frame_ref(&frame);
}
static void finish_fixture(struct fixture *f)
{
    CHECK(!f->in.lock && !f->in.rendering && !f->in.fel_render_initializing);
    free(f->in.current_frame);
    free(f->in.frame_queued);
}

int main(void)
{
    struct fixture f;
    init_fixture(&f);
    CHECK(!render_frame(&f.vo));
    CHECK(f.in.drop_count == 1 && draws == 0 && flips == 0);
    CHECK(f.in.current_frame && f.in.current_frame->num_frames == 2);
    CHECK(f.in.current_frame->frames[1] == &f.images[1]);
    CHECK(codec_outputs == 0); // Old code deadlocks here: no room for next BL.
    CHECK(copies == 1 && controls == 1 && map_calls == 2 && !backend_errors);
    CHECK(f.images[0].staged && f.images[1].staged);
    CHECK(core_wakeups > 0);
    finish_fixture(&f);

    init_fixture(&f);
    f.vo.extra.android_dovi_fel = false;
    render_frame(&f.vo);
    CHECK(f.in.drop_count == 1 && controls == 0 && copies == 0);
    finish_fixture(&f);

    init_fixture(&f);
    f.images[0].imgfmt = f.images[1].imgfmt = IMGFMT_YUV420P10;
    render_frame(&f.vo);
    CHECK(f.in.drop_count == 1 && controls == 0 && copies == 0);
    finish_fixture(&f);

    init_fixture(&f);
    f.driver.caps = 0; // Unrelated VO, even if a custom config set the FEL option.
    render_frame(&f.vo);
    CHECK(f.in.drop_count == 1 && controls == 0 && copies == 0);
    finish_fixture(&f);

    init_fixture(&f);
    f.in.frame_queued->pts = fake_now; // On-time frames keep the usual draw path.
    render_frame(&f.vo);
    CHECK(!f.in.drop_count && controls == 0 && draws == 1 && flips == 1);
    CHECK(f.in.fel_render_init_attempted && !f.in.fel_render_initializing);
    finish_fixture(&f);

    init_fixture(&f);
    f.in.paused = true;
    render_frame(&f.vo);
    CHECK(!f.in.drop_count && controls == 0 && draws == 1);
    finish_fixture(&f);

    init_fixture(&f);
    f.gpu.deferred = false; // Do not pre-acquire several GL external images.
    render_frame(&f.vo);
    CHECK(f.in.drop_count == 1 && copies == 0 && !backend_errors);
    finish_fixture(&f);

    init_fixture(&f);
    fail_map = RA_HWDEC_MAP_RETRY;
    render_frame(&f.vo);
    CHECK(!backend_errors && f.in.request_redraw);
    CHECK(f.in.current_frame && codec_outputs == 1);
    fail_map = 0;
    CHECK(preload_dropped_fel_frame(&f.vo, f.in.current_frame) == VO_TRUE);
    CHECK(codec_outputs == 0 && copies == 1);
    finish_fixture(&f);

    init_fixture(&f);
    fail_map = VO_ERROR;
    render_frame(&f.vo);
    CHECK(backend_errors == 1 && copies == 0);
    finish_fixture(&f);
    puts("PASS: real VO drop/control paths free finite codec output without drawing; FEL-off, software, other VO, timing, pause, GL, retry, fatal and lock isolation");
    return 0;
}
