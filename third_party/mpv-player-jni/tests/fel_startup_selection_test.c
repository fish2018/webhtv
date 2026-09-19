// Compile the actual selected-track decision and video-chain constructor.
// External VO/decoder APIs are fakes so ordering, reuse and failure are exact.
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#define HAVE_ANDROID 1
#define VO_CAP_NATIVE_DOVI 1
#define MP_OUTPUT_CHAIN_VIDEO 1
#define MPV_ERROR_VO_INIT_FAILED -12
#define STATUS_READY 3
#define mp_assert assert
#define MP_INFO(...) ((void)0)
#define MP_WARN(...) ((void)0)
#define MP_FATAL(...) ((void)0)
#define talloc_zero(parent, type) calloc(1, sizeof(type))
struct m_obj_settings { char *name; };
struct mp_vo_opts {
    bool android_dovi_fel;
    int android_dovi_fel_vulkan;
    struct m_obj_settings *video_driver_list;
};
struct MPOpts { struct mp_vo_opts *vo; };
struct mp_codec_params { int dv_profile, source_dv_profile; };
struct sh_stream { struct mp_codec_params *codec; bool still_image; };
struct mp_filter { void *pins[2]; };
struct mp_decoder_wrapper { struct mp_filter *f; };
struct vo_chain;
struct track {
    bool attached_picture;
    struct sh_stream *stream;
    int user_tid;
    struct vo_chain *vo_c;
    struct mp_decoder_wrapper *dec;
};
struct vo_extra {
    void *input_ctx, *osd, *encode_lavc_ctx, *wakeup_ctx;
    void (*wakeup_cb)(void *);
    bool prefer_hdr_output, android_dovi_fel;
    int android_dovi_fel_vulkan;
};
struct vo_driver { int caps; };
struct vo {
    struct vo_extra extra;
    const struct vo_driver *driver;
    const char *context_name;
    bool config_ok, has_frame;
};
struct mp_output_chain {
    struct mp_filter *f;
    void (*update_subtitles)(void *);
    void *update_subtitles_ctx;
    double container_fps;
};
struct vo_chain {
    void *log, *dec_src;
    struct vo *vo;
    struct mp_output_chain *filter;
    struct track *track;
    bool is_coverart, is_sparse;
};
typedef struct MPContext {
    struct MPOpts *opts;
    struct vo *video_out;
    struct vo_chain *vo_chain;
    struct track *android_dolby_vision_direct_failed_track;
    void *input, *osd, *encode_lavc_ctx, *global, *log, *filter_root;
    int error_playing, video_status;
    bool mouse_cursor_visible;
} MPContext;
struct m_property { int unused; };
static bool should_use_android_fel_output(struct MPContext *, struct track *);
void reinit_video_chain_src(struct MPContext *, struct track *);
static int mp_property_android_fel_active(void *, struct m_property *, int, void *);
static int mp_property_video_frame_submitted(void *, struct m_property *, int, void *);
static int creates, destroys, decodes;
static bool fail_vo;
static const struct vo_driver gpu_driver = {0}, direct_driver = {VO_CAP_NATIVE_DOVI};
static struct mp_filter decoder_filter;
static struct mp_decoder_wrapper decoder = {&decoder_filter};
static bool should_use_android_dolby_vision_direct_output(struct MPContext *m, struct track *t)
{ return false; }
static bool is_android_dolby_vision_direct_output_active(struct MPContext *m)
{ return m->video_out && (m->video_out->driver->caps & VO_CAP_NATIVE_DOVI); }
static bool is_android_opengl_output_active(struct MPContext *m)
{ return m->video_out && !strcmp(m->video_out->context_name, "android"); }
static bool should_prefer_android_hdr_output(struct MPContext *m, struct track *t)
{ return t && t->stream && t->stream->codec && t->stream->codec->dv_profile > 0; }
static void uninit_video_chain(struct MPContext *m)
{
    if (!m->vo_chain) return;
    if (m->vo_chain->track) m->vo_chain->track->vo_c = NULL;
    free(m->vo_chain->filter->f);
    free(m->vo_chain->filter);
    free(m->vo_chain);
    m->vo_chain = NULL;
}
static void uninit_video_out(struct MPContext *m)
{
    uninit_video_chain(m);
    if (m->video_out) destroys++;
    free(m->video_out);
    m->video_out = NULL;
}
static void mp_wakeup_core_cb(void *p) {}
static struct vo *init_video_out_by_name(void *g, struct vo_extra *ex, const char *name)
{
    creates++;
    if (fail_vo) return NULL;
    struct vo *v = calloc(1, sizeof(*v));
    v->extra = *ex;
    v->driver = !strcmp(name, "mediacodec_embed") ? &direct_driver : &gpu_driver;
    v->context_name = ex->android_dovi_fel_vulkan ? "androidvk" : "android";
    assert(!ex->android_dovi_fel || !strcmp(name, "gpu-next"));
    return v;
}
static struct vo *init_best_video_out(void *g, struct vo_extra *ex)
{ return init_video_out_by_name(g, ex, "configured"); }
static struct mp_output_chain *mp_output_chain_create(void *r, int type)
{
    struct mp_output_chain *f = calloc(1, sizeof(*f));
    f->f = calloc(1, sizeof(*f->f));
    return f;
}
static void mp_output_chain_set_vo(struct mp_output_chain *f, struct vo *v) { assert(v); }
static void filter_update_subtitles(void *p) {}
static bool try_init_video_decoder(struct MPContext *m, struct track *t)
{
    // The constructor must decide/initialize the VO before creating any decoder.
    assert(m->video_out && t->vo_c && t->vo_c->vo == m->video_out);
    assert(m->video_out->extra.android_dovi_fel == should_use_android_fel_output(m, t));
    t->dec = &decoder;
    decodes++;
    return true;
}
static double mp_decoder_wrapper_get_container_fps(struct mp_decoder_wrapper *d) { return 24; }
static void mp_decoder_wrapper_set_coverart_flag(struct mp_decoder_wrapper *d, bool b) {}
static void mp_pin_connect(void *a, void *b) {}
static void update_vo_chain_el_state(struct MPContext *m) {}
static bool recreate_video_filters(struct MPContext *m) { return true; }
static void update_content_type(struct MPContext *m, struct track *t) {}
static void update_screensaver_state(struct MPContext *m) {}
static void update_window_title(struct MPContext *m, bool b) {}
static bool get_internal_paused(struct MPContext *m) { return false; }
static void vo_set_paused(struct vo *v, bool p) {}
static void reset_video_state(struct MPContext *m) { m->video_status = 0; }
static void term_osd_clear_subs(struct MPContext *m) {}
static void error_on_track(struct MPContext *m, struct track *t) {}
static void handle_force_window(struct MPContext *m, bool b) {}
static bool vo_has_frame(struct vo *v) { return v->has_frame; }
static int m_property_bool_ro(int action, void *arg, bool value)
{ *(bool *)arg = value; return 1; }

int main(void)
{
    struct mp_vo_opts vo_opts = {.android_dovi_fel = true, .android_dovi_fel_vulkan = 2};
    struct MPOpts opts = {.vo = &vo_opts};
    struct MPContext m = {.opts = &opts};
    struct mp_codec_params codec = {.dv_profile = 7};
    struct sh_stream stream = {.codec = &codec};
    struct track selected = {.stream = &stream, .user_tid = 3};
    reinit_video_chain_src(&m, &selected);
    assert(creates == 1 && decodes == 1 && m.video_out->extra.android_dovi_fel);
    assert(m.video_out->extra.android_dovi_fel_vulkan == 2);
    struct vo *first = m.video_out;
    uninit_video_chain(&m);
    reinit_video_chain_src(&m, &selected);
    assert(m.video_out == first && creates == 1 && decodes == 2);
    bool active = false;
    mp_property_android_fel_active(&m, NULL, 0, &active);
    assert(active);
    m.video_out->config_ok = true;
    m.video_status = STATUS_READY;
    mp_property_video_frame_submitted(&m, NULL, 0, &active);
    assert(!active); // configured dimensions or audio restart are insufficient
    m.video_out->has_frame = true;
    mp_property_video_frame_submitted(&m, NULL, 0, &active);
    assert(active);
    m.video_status = 0;
    mp_property_video_frame_submitted(&m, NULL, 0, &active);
    assert(!active); // a retained old image after seek is insufficient
    uninit_video_chain(&m);
    codec.dv_profile = 5;
    reinit_video_chain_src(&m, &selected);
    assert(creates == 2 && destroys == 1 && !m.video_out->extra.android_dovi_fel);
    assert(vo_opts.android_dovi_fel && vo_opts.android_dovi_fel_vulkan);
    for (int profile = 0; profile <= 8; profile++) {
        codec.dv_profile = profile;
        assert(should_use_android_fel_output(&m, &selected) == (profile == 7));
    }
    codec.dv_profile = 8;
    codec.source_dv_profile = 7;
    assert(should_use_android_fel_output(&m, &selected));
    selected.attached_picture = true;
    assert(!should_use_android_fel_output(&m, &selected));
    selected.attached_picture = false;
    vo_opts.android_dovi_fel = false;
    assert(!should_use_android_fel_output(&m, &selected));
    vo_opts.android_dovi_fel = true;
    assert(!should_use_android_fel_output(&m, NULL));
    struct m_obj_settings null_vo[] = {{.name = "null"}, {0}};
    vo_opts.video_driver_list = null_vo;
    assert(!should_use_android_fel_output(&m, &selected));
    vo_opts.video_driver_list = NULL;
    uninit_video_out(&m);
    reinit_video_chain_src(&m, NULL);
    assert(!m.video_out->extra.android_dovi_fel && !m.vo_chain->track);
    uninit_video_out(&m);
    fail_vo = true;
    int before_creates = creates, before_decodes = decodes;
    reinit_video_chain_src(&m, &selected);
    assert(creates == before_creates + 1 && decodes == before_decodes);
    assert(!m.video_out && !m.vo_chain && m.error_playing == MPV_ERROR_VO_INIT_FAILED);
    puts("PASS: selected-track FEL before decoder, VO reuse/transition, null/coverart/profile isolation, failure and first-frame epoch");
}
