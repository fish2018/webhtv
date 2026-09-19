#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>

enum { STREAM_CTRL_NAV_POLL, STATUS_EOF = 4 };
#define mp_assert assert
struct stream_info { const char *name; };
struct stream { struct stream_info *info; int polls, effects_left; };
struct stream_nav_state { bool nav_active, menu_active, still_active; };
struct demux_internal;
struct demuxer {
    struct demux_internal *in;
    struct stream *stream;
    bool cancelled;
};
struct demux_internal {
    struct demuxer *d_user, *d_thread;
    bool nav_poll, nav_pump, reading, blocked;
    int lock, wakeup;
};
struct MPContext {
    struct demuxer *demuxer;
    int video_status, audio_status;
    bool paused;
    double timeout;
};
static void mp_mutex_lock(int *lock) { assert(!*lock); *lock = 1; }
static void mp_mutex_unlock(int *lock) { assert(*lock); *lock = 0; }
static void mp_cond_signal(int *condition) { (*condition)++; }
static bool demux_cancel_test(struct demuxer *demux) { return demux->cancelled; }
static void mp_set_timeout(struct MPContext *ctx, double delay) { ctx->timeout = delay; }
static int stream_control(struct stream *s, int command, void *arg)
{
    assert(command == STREAM_CTRL_NAV_POLL && arg == NULL);
    s->polls++;
    if (s->effects_left) s->effects_left--;
    return 1;
}
#include "disc_requests_under_test.h"
#include "disc_poll_under_test.h"
#include "disc_tick_under_test.h"

static bool dispatch(struct demux_internal *in)
{
    mp_mutex_lock(&in->lock);
    bool worked = poll_nav(in);
    assert(in->lock == 1);
    mp_mutex_unlock(&in->lock);
    return worked;
}

int main(void)
{
    struct stream_info info = {"bd"};
    struct stream stream = {.info = &info, .effects_left = 3};
    struct demux_internal in = {0};
    struct demuxer user = {.in = &in, .stream = &stream};
    struct demuxer owner = {.in = &in, .stream = &stream};
    in.d_user = &user;
    in.d_thread = &owner;
    struct MPContext ctx = {.demuxer = &user, .paused = true};
    struct stream_nav_state nav = {.nav_active = true, .menu_active = true};

    // Full packet queues / paused consumers do not request any media reads.
    for (int frame = 0; frame < 3; frame++) {
        poll_disc_nav(&ctx, &stream, &nav);
        assert(ctx.timeout > 0 && ctx.timeout <= 0.05);
        assert(in.nav_poll && !in.reading && !in.nav_pump && ctx.paused);
        assert(dispatch(&in));
        assert(!dispatch(&in)); // No self-wakeup / busy-loop after servicing.
    }
    assert(stream.effects_left == 0 && stream.polls == 3);

    // Repeated requests coalesce; blocked/closing owners never enter libbluray.
    demux_poll_nav(&user);
    demux_poll_nav(&user);
    in.blocked = true;
    assert(!dispatch(&in) && stream.polls == 3 && in.nav_poll);
    in.blocked = false;
    owner.cancelled = true;
    assert(!dispatch(&in) && stream.polls == 3);
    owner.cancelled = false;
    assert(dispatch(&in) && stream.polls == 4 && !in.reading);

    nav.menu_active = false;
    ctx.timeout = 0;
    poll_disc_nav(&ctx, &stream, &nav);
    assert(!in.nav_poll && !in.nav_pump && ctx.timeout == 0);
    nav.menu_active = true;
    nav.nav_active = false; // Longest-title/BD-J fallback is unchanged.
    poll_disc_nav(&ctx, &stream, &nav);
    assert(!in.nav_poll && ctx.timeout == 0);
    nav.nav_active = true;
    info.name = "dvdnav";
    poll_disc_nav(&ctx, &stream, &nav);
    assert(!in.nav_poll && ctx.timeout == 0);

    info.name = "bdmv/bluray";
    nav.menu_active = false;
    nav.still_active = true;
    poll_disc_nav(&ctx, &stream, &nav);
    assert(in.nav_pump && in.reading && ctx.timeout > 0); // Existing still path.
    in.nav_pump = in.reading = false;
    nav.still_active = false;
    ctx.video_status = ctx.audio_status = STATUS_EOF;
    poll_disc_nav(&ctx, &stream, &nav);
    assert(in.nav_pump && in.reading);
    puts("PASS: paused/full-queue menu animation progresses without media reads; coalescing/cancel/idle preserved");
    return 0;
}
