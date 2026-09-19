#include <assert.h>
#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

#define MPMIN(a, b) ((a) < (b) ? (a) : (b))
#define MP_TIME_MS_TO_NS(ms) ((ms) * 1000000LL)
#define MP_INFO(ctx, ...) do { if (false) fprintf(stderr, __VA_ARGS__); } while (0)
enum stream_nav_action {
    STREAM_NAV_UP, STREAM_NAV_DOWN, STREAM_NAV_LEFT, STREAM_NAV_RIGHT,
    STREAM_NAV_SELECT, STREAM_NAV_MENU_ROOT, STREAM_NAV_MENU_TITLE,
    STREAM_NAV_MENU_POPUP, STREAM_NAV_PREV_MENU, STREAM_NAV_MOUSE_MOVE,
    STREAM_NAV_MOUSE_CLICK,
};
enum { STREAM_CTRL_GET_NAV_STATE, STREAM_CTRL_NAV_CMD, STREAM_CTRL_NAV_POLL };
enum { STREAM_ERROR = -1, STREAM_UNSUPPORTED = -2, STREAM_OK = 1 };
enum { BD_VK_NONE, BD_VK_UP, BD_VK_DOWN, BD_VK_LEFT, BD_VK_RIGHT,
       BD_VK_ENTER, BD_VK_POPUP, BD_VK_MOUSE_ACTIVATE, BD_VK_MENU_BACK };
enum { BLURAY_UO_MENU_CALL = 1,
       BLURAY_PLAYER_SETTING_UO_RESTRICTION_LEVEL = 0x102,
       BLURAY_PLAYER_SETTING_UO_RESTRICTION_DISABLED = 0,
       BLURAY_PLAYER_SETTING_UO_RESTRICTION_RELAXED = 5 };
struct mp_osd_res { int w, h, ml, mr, mt, mb; };
struct vo { bool config_ok; };
struct input_ctx { int x, y, reads; };
struct stream_nav_state { int src_w, src_h; };
struct stream_nav_cmd { int action, x, y; };
struct bluray_priv_s {
    bool hdmv_mode, popup_supported, input_pending, data_delivered;
    bool menu_event_active, overlay_ig_visible;
    int64_t next_nav_poll_ns;
    uint32_t uo_mask, nav_change_id, discontinuity_id, trace_request;
    int overlay_lock;
    void *bd;
};
struct stream { struct bluray_priv_s *priv; struct stream_nav_state state; };
typedef struct stream stream_t;
struct priv { bool is_bd, nav_active; };
struct demuxer { struct stream *stream; int drives; struct priv *priv; int event_polls; };
struct demux_packet { int cached; };
struct MPContext {
    struct vo *video_out;
    struct input_ctx *input;
    struct mp_osd_res *osd;
    struct demuxer *demuxer;
    int wakeups;
};
struct mp_cmd_arg { union { double d; int i; } v; };
struct mp_cmd_ctx {
    struct MPContext *mpctx;
    struct mp_cmd_arg args[4];
    bool success;
};

static int select_result, key_result, popup_result, root_result;
static int back_result, back_calls;
static int select_calls, key_calls, popup_calls, root_calls, click_x, click_y;
static uint32_t last_key;
static struct bluray_priv_s bluray;
static int polls;
static bool poll_failed, page_action_pending;
static int restriction_level, setting_calls, unrestricted_root_result;
static bool setting_failed;
static int64_t fake_time;
static int64_t mp_time_ns(void) { return fake_time; }
static void bluray_trace_begin(stream_t *s, const char *phase) { (void)s; (void)phase; }
static void bluray_trace_end(void) {}
static void bluray_trace_state(stream_t *s, const char *phase, int result, int64_t started)
{ (void)s; (void)phase; (void)result; (void)started; }
static void mp_mutex_lock(int *lock) { assert(!*lock); *lock = 1; }
static void mp_mutex_unlock(int *lock) { assert(*lock); *lock = 0; }
static int bd_mouse_select(void *bd, int64_t pts, uint16_t x, uint16_t y)
{
    (void)bd; (void)pts;
    assert(!bluray.overlay_lock);
    select_calls++;
    click_x = x;
    click_y = y;
    return select_result;
}
static int bd_user_input(void *bd, int64_t pts, uint32_t key)
{
    (void)bd; (void)pts;
    assert(!bluray.overlay_lock);
    key_calls++;
    last_key = key;
    if (key == BD_VK_MENU_BACK) {
        back_calls++;
        return back_result;
    }
    if (key == BD_VK_POPUP) {
        popup_calls++;
        return popup_result;
    }
    return key_result;
}
static int bd_menu_call(void *bd, int64_t pts)
{
    (void)bd; (void)pts;
    assert(!bluray.overlay_lock);
    root_calls++;
    return restriction_level == BLURAY_PLAYER_SETTING_UO_RESTRICTION_DISABLED
               ? unrestricted_root_result : root_result;
}
static int bd_set_player_setting(void *bd, uint32_t setting, uint32_t value)
{
    (void)bd;
    assert(!bluray.overlay_lock);
    assert(setting == BLURAY_PLAYER_SETTING_UO_RESTRICTION_LEVEL);
    setting_calls++;
    if (setting_failed) return 0;
    restriction_level = value;
    return 1;
}
#include "disc_menu_call_under_test.h"
static int bluray_poll_hdmv_events(struct stream *s)
{
    assert(!s->priv->overlay_lock);
    polls++;
    if (poll_failed) return -1;
    if (page_action_pending) {
        s->priv->nav_change_id++;
        page_action_pending = false;
    }
    return 0;
}
static int bluray_control(struct stream *s, int command, void *arg)
{
    struct bluray_priv_s *b = s->priv;
    switch (command) {
#include "disc_pump_under_test.h"
#include "disc_bluray_input_under_test.h"
    default: return STREAM_UNSUPPORTED;
    }
}
static int stream_control(struct stream *s, int command, void *arg)
{
    if (command == STREAM_CTRL_GET_NAV_STATE) {
        *(struct stream_nav_state *)arg = s->state;
        return STREAM_OK;
    }
    return bluray_control(s, command, arg);
}
static struct stream *disc_nav_get_stream(struct MPContext *mpctx)
{
    return mpctx->demuxer ? mpctx->demuxer->stream : NULL;
}
static void demux_drive_nav(struct demuxer *demux) { demux->drives++; }
static void demux_poll_nav(struct demuxer *demux) { demux->event_polls++; }
static void mp_wakeup_core(struct MPContext *mpctx) { mpctx->wakeups++; }
static void mp_input_get_mouse_pos(struct input_ctx *input, int *x, int *y, int *hover)
{
    input->reads++;
    *x = input->x; *y = input->y; *hover = 1;
}
static struct mp_osd_res osd_get_vo_res(struct mp_osd_res *osd) { return *osd; }
#include "disc_action_under_test.h"
#include "disc_pointer_under_test.h"
#include "disc_command_under_test.h"

// Compile the actual disc-demux prologue before its cached packet read.
#include "disc_demux_input_under_test.h"
    static struct demux_packet cached = {1};
    *out_pkt = &cached;
    return true;
}

static void reset_results(void)
{
    select_result = key_result = popup_result = root_result = 1;
    select_calls = key_calls = popup_calls = root_calls = 0;
    back_result = back_calls = 0;
    fake_time = 0;
    last_key = BD_VK_NONE;
    polls = 0;
    poll_failed = page_action_pending = false;
    restriction_level = BLURAY_PLAYER_SETTING_UO_RESTRICTION_RELAXED;
    setting_calls = 0;
    setting_failed = false;
    unrestricted_root_result = 1;
    bluray = (struct bluray_priv_s){.hdmv_mode = true};
}
static bool run(struct MPContext *mpctx, int action, double x, double y, int window)
{
    struct mp_cmd_ctx cmd = {.mpctx = mpctx, .success = true};
    cmd.args[0].v.i = action;
    cmd.args[1].v.d = x;
    cmd.args[2].v.d = y;
    cmd.args[3].v.i = window;
    cmd_discnav(&cmd);
    return cmd.success;
}
static void expect_point(struct MPContext *mpctx, double x, double y, int sx, int sy)
{
    assert(run(mpctx, STREAM_NAV_MOUSE_CLICK, x, y, 1));
    assert(click_x == sx && click_y == sy);
    assert(last_key == BD_VK_MOUSE_ACTIVATE);
}

int main(void)
{
    reset_results();
    struct vo vo = {.config_ok = true};
    // The old input queue still holds a different point, outside the video.
    struct input_ctx input = {.x = 3, .y = 4};
    struct mp_osd_res osd = {.w = 1200, .h = 800, .mt = 100, .mb = 100};
    struct stream stream = {.priv = &bluray, .state = {1920, 1080}};
    struct demuxer demux = {.stream = &stream};
    struct MPContext mpctx = {&vo, &input, &osd, &demux, 0};

    // Atomic window points never consult the not-yet-consumed input queue.
    expect_point(&mpctx, 900, 550, 1440, 810);
    expect_point(&mpctx, 300, 250, 480, 270);
    assert(input.reads == 0 && demux.drives == 2);
    // Legacy normalized coordinates and no-coordinate input remain available.
    assert(run(&mpctx, STREAM_NAV_MOUSE_CLICK, 0.5, 0.5, 0));
    assert(click_x == 960 && click_y == 540 && input.reads == 0);
    assert(run(&mpctx, STREAM_NAV_MOUSE_CLICK, 1, 1, 0));
    assert(click_x == 1919 && click_y == 1079);
    input.x = 300; input.y = 250;
    assert(run(&mpctx, STREAM_NAV_MOUSE_CLICK, -1, -1, 0));
    assert(click_x == 480 && click_y == 270 && input.reads == 1);
    assert(!run(&mpctx, STREAM_NAV_MOUSE_CLICK, NAN, 0.5, 0));
    assert(!run(&mpctx, STREAM_NAV_MOUSE_CLICK, 1.1, 0.5, 0));
    assert(!run(&mpctx, STREAM_NAV_MOUSE_CLICK, -1, 0.5, 0));

    int calls = select_calls;
    const double outside[][2] = {{0, 99}, {0, 700}, {-1, 400}, {1200, 400},
                                  {0, 800}, {NAN, 400}, {INFINITY, 400}};
    for (unsigned i = 0; i < sizeof(outside) / sizeof(outside[0]); i++)
        assert(!run(&mpctx, STREAM_NAV_MOUSE_CLICK, outside[i][0], outside[i][1], 1));
    assert(select_calls == calls);
    expect_point(&mpctx, 0, 100, 0, 0);
    expect_point(&mpctx, 1199, 699, 1918, 1078);
    // Zoom/crop: the left edge is the quarter point of the full menu plane.
    osd = (struct mp_osd_res){.w = 960, .h = 540, .ml = -480, .mr = -480,
                             .mt = -270, .mb = -270};
    expect_point(&mpctx, 0, 0, 480, 270);
    expect_point(&mpctx, 480, 270, 960, 540);
    // Pillarbox plus non-square scaling / a smaller published OSD surface.
    osd = (struct mp_osd_res){.w = 1280, .h = 720, .ml = 160, .mr = 160};
    expect_point(&mpctx, 640, 360, 960, 540);
    assert(!run(&mpctx, STREAM_NAV_MOUSE_CLICK, 159, 360, 1));
    osd = (struct mp_osd_res){.w = 640, .h = 360};
    expect_point(&mpctx, 320, 180, 960, 540);
    vo.config_ok = false;
    assert(!run(&mpctx, STREAM_NAV_MOUSE_CLICK, 320, 180, 1));
    vo.config_ok = true;
    stream.state.src_w = 0;
    assert(!run(&mpctx, STREAM_NAV_MOUSE_CLICK, 320, 180, 1));
    stream.state.src_w = 1920;

    // A missed hit or libbluray error must never activate the previous button.
    reset_results();
    for (int hit = -1; hit <= 0; hit++) {
        select_result = hit;
        assert(!run(&mpctx, STREAM_NAV_MOUSE_CLICK, 320, 180, 1));
        assert(key_calls == 0);
    }
    select_result = 1;
    key_result = -1;
    int drives = demux.drives;
    int event_polls = demux.event_polls;
    assert(!run(&mpctx, STREAM_NAV_MOUSE_CLICK, 320, 180, 1));
    assert(demux.drives == drives);
    assert(demux.event_polls == event_polls + 1);
    key_result = 0; // API success without an activation change is not an error.
    assert(run(&mpctx, STREAM_NAV_SELECT, -1, -1, 0));
    assert(demux.drives == drives + 1);
    int keys = key_calls;
    assert(run(&mpctx, STREAM_NAV_MOUSE_MOVE, 320, 180, 1));
    assert(key_calls == keys && demux.drives == drives + 2);

    // Slave packets already cached must not prevent the VM from advancing.
    struct priv disc = {.is_bd = true, .nav_active = true};
    demux.priv = &disc;
    struct demux_packet *packet = NULL;
    page_action_pending = true;
    assert(bluray.input_pending);
    assert(d_read_packet(&demux, &packet) && packet->cached);
    assert(polls == 1 && !bluray.input_pending && !page_action_pending);
    assert(bluray.nav_change_id == 1 && bluray.discontinuity_id == 0);
    assert(d_read_packet(&demux, &packet));
    assert(polls == 1); // No extra VM work on unchanged media reads.
    assert(run(&mpctx, STREAM_NAV_RIGHT, -1, -1, 0));
    assert(bluray.input_pending); // Cursor auto-actions also require a pump.
    poll_failed = true;
    assert(!d_read_packet(&demux, &packet));
    poll_failed = false;
    bluray.input_pending = true;
    disc.is_bd = false;
    assert(d_read_packet(&demux, &packet) && bluray.input_pending);
    disc.is_bd = true;

    // Page animations/auto-actions must keep advancing over cached media, but
    // not at packet frequency and not for ordinary non-menu playback.
    reset_results();
    bluray.menu_event_active = true;
    assert(d_read_packet(&demux, &packet) && polls == 1);
    for (int i = 0; i < 100; i++) assert(d_read_packet(&demux, &packet));
    assert(polls == 1);
    fake_time = MP_TIME_MS_TO_NS(33);
    assert(d_read_packet(&demux, &packet) && polls == 2);
    bluray.menu_event_active = false;
    fake_time += MP_TIME_MS_TO_NS(1000);
    assert(d_read_packet(&demux, &packet) && polls == 2);

    // A verified authored Back route closes the child without restarting ROOT.
    reset_results();
    back_result = 1;
    assert(run(&mpctx, STREAM_NAV_MENU_ROOT, -1, -1, 0));
    assert(run(&mpctx, STREAM_NAV_PREV_MENU, -1, -1, 0));
    assert(back_calls == 2 && root_calls == 0 && popup_calls == 0);

    // Root succeeds: do not accidentally toggle a popup after the menu call.
    reset_results();
    bluray.popup_supported = true;
    assert(run(&mpctx, STREAM_NAV_MENU_ROOT, -1, -1, 0));
    assert(root_calls == 1 && popup_calls == 0);
    root_result = 0;
    assert(run(&mpctx, STREAM_NAV_MENU_ROOT, -1, -1, 0));
    assert(root_calls == 2 && popup_calls == 1);
    popup_result = -1;
    assert(!run(&mpctx, STREAM_NAV_MENU_ROOT, -1, -1, 0));
    bluray.popup_supported = false;
    keys = key_calls;
    assert(!run(&mpctx, STREAM_NAV_MENU_ROOT, -1, -1, 0));
    assert(key_calls == keys + 1 && last_key == BD_VK_MENU_BACK);

    // Back on a non-popup top-menu subpage requests root, not unsupported popup.
    reset_results();
    assert(run(&mpctx, STREAM_NAV_PREV_MENU, -1, -1, 0));
    assert(root_calls == 1 && popup_calls == 0);
    bluray.popup_supported = true;
    assert(run(&mpctx, STREAM_NAV_PREV_MENU, -1, -1, 0));
    assert(root_calls == 1 && popup_calls == 1);
    popup_result = -1;
    assert(run(&mpctx, STREAM_NAV_PREV_MENU, -1, -1, 0));
    assert(root_calls == 2 && popup_calls == 2);
    root_result = 0;
    assert(!run(&mpctx, STREAM_NAV_PREV_MENU, -1, -1, 0));
    // A user menu request can interrupt an authored UO-restricted clip only
    // after FIRST PLAY has run and supplied media. Always restore the policy.
    reset_results();
    root_result = 0;
    bluray.uo_mask = BLURAY_UO_MENU_CALL;
    assert(!run(&mpctx, STREAM_NAV_MENU_ROOT, -1, -1, 0));
    assert(root_calls == 1 && setting_calls == 0); // startup is not bypassed
    bluray.data_delivered = true;
    assert(run(&mpctx, STREAM_NAV_MENU_ROOT, -1, -1, 0));
    assert(root_calls == 3 && setting_calls == 2);
    assert(restriction_level == BLURAY_PLAYER_SETTING_UO_RESTRICTION_RELAXED);
    unrestricted_root_result = 0;
    assert(!run(&mpctx, STREAM_NAV_PREV_MENU, -1, -1, 0));
    assert(root_calls == 5 && setting_calls == 4);
    assert(restriction_level == BLURAY_PLAYER_SETTING_UO_RESTRICTION_RELAXED);
    bluray.uo_mask = 0; // unrelated failure must not change restrictions
    assert(!run(&mpctx, STREAM_NAV_MENU_TITLE, -1, -1, 0));
    assert(root_calls == 6 && setting_calls == 4);
    bluray.uo_mask = BLURAY_UO_MENU_CALL;
    setting_failed = true;
    assert(!run(&mpctx, STREAM_NAV_MENU_ROOT, -1, -1, 0));
    assert(root_calls == 7 && setting_calls == 5);
    assert(restriction_level == BLURAY_PLAYER_SETTING_UO_RESTRICTION_RELAXED);
    bluray.hdmv_mode = false;
    assert(!run(&mpctx, STREAM_NAV_MENU_ROOT, -1, -1, 0));
    demux.stream = NULL;
    assert(!run(&mpctx, STREAM_NAV_SELECT, -1, -1, 0));
    puts("PASS: atomic pointer / OSD mapping / hit gating / native results / cached-packet VM pump / root-popup-back fallback / explicit menu UO restore");
    return 0;
}
