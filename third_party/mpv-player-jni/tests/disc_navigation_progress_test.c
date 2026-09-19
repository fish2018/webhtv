#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

enum { BD_EVENT_NONE, BD_EVENT_PLAYLIST };
enum { STREAM_VIDEO, STREAM_AUDIO, STREAM_SUB };
typedef struct { int event; } BD_EVENT;
struct bluray_priv_s {
    void *bd;
    bool hdmv_mode, still_active, data_delivered;
    int overlay_lock;
    uint32_t discontinuity_id;
};
typedef struct { struct bluray_priv_s *priv; void *cancel; } stream_t;
struct sh_stream { int demuxer_id, type; };
struct priv { bool is_bd; };
static bool pending_command, cancelled, io_error;
static int poll_reads, data_reads, legacy_reads;
static void mp_mutex_lock(int *lock) { assert(!*lock); *lock = 1; }
static void mp_mutex_unlock(int *lock) { assert(*lock); *lock = 0; }

#define MP_VERBOSE(ctx, ...) do { if (false) fprintf(stderr, __VA_ARGS__); } while (0)
#define MP_TIME_MS_TO_NS(ms) (ms)
static void mp_sleep_ns(int ns) { (void)ns; }
static bool mp_cancel_test(void *cancel) { (void)cancel; return cancelled; }
static int bd_read_ext(void *bd, void *buffer, int len, BD_EVENT *event)
{
    (void)bd; (void)buffer;
    if (io_error) return -1;
    event->event = BD_EVENT_NONE;
    if (!len) {
        poll_reads++;
        if (pending_command) {
            pending_command = false;
            event->event = BD_EVENT_PLAYLIST;
        }
        return 0;
    }
    data_reads++;
    return len;
}
static int bluray_read_ext(stream_t *s, void *buffer, int len, BD_EVENT *event)
{
    return bd_read_ext(s->priv->bd, buffer, len, event);
}
static int bd_get_event(void *bd, BD_EVENT *event)
{
    (void)bd; (void)event;
    return 0; // Commands are not events until bd_read_ext runs the VM.
}
static int bd_read(void *bd, void *buffer, int len)
{
    (void)bd; (void)buffer;
    legacy_reads++;
    return len;
}
static void handle_event(stream_t *s, BD_EVENT *event)
{
    if (event->event == BD_EVENT_PLAYLIST) {
        s->priv->still_active = false;
        s->priv->discontinuity_id++;
    }
}

#include "disc_progress_under_test.h"
#include "disc_pid_under_test.h"

static bool ignored(bool native_bd, int type, int id)
{
    struct priv state = {.is_bd = native_bd};
    struct priv *p = &state;
    struct sh_stream input = {.demuxer_id = id, .type = type};
    struct sh_stream *src = &input;
    return DISC_STREAM_IGNORED;
}

int main(void)
{
    struct bluray_priv_s b = {.hdmv_mode = true, .still_active = true,
        .data_delivered = true, .discontinuity_id = 4};
    stream_t s = {.priv = &b};
    char buffer[192];
    pending_command = true;

    // A selected still-menu button must execute, preserving the clip boundary.
    assert(bluray_stream_fill_buffer(&s, buffer, sizeof(buffer)) == 0);
    assert(!pending_command && !b.still_active);
    assert(b.discontinuity_id == 5 && poll_reads == 2 && data_reads == 0);
    assert(bluray_stream_fill_buffer(&s, buffer, sizeof(buffer)) == 192);
    assert(data_reads == 1);

    // Waiting on a still without a command must not skip it or fabricate a hop.
    b.still_active = true;
    assert(bluray_stream_fill_buffer(&s, buffer, sizeof(buffer)) == 0);
    assert(b.still_active && b.discontinuity_id == 5 && data_reads == 1);

    // First-play commands may directly produce the initial clip.
    b.data_delivered = false;
    pending_command = true;
    assert(bluray_stream_fill_buffer(&s, buffer, sizeof(buffer)) == 192);
    assert(!b.still_active && b.discontinuity_id == 6);

    cancelled = true;
    assert(bluray_stream_fill_buffer(&s, buffer, sizeof(buffer)) == -1);
    cancelled = false;
    io_error = true;
    assert(bluray_stream_fill_buffer(&s, buffer, sizeof(buffer)) == -1);
    io_error = false;
    b.hdmv_mode = false;
    assert(bluray_stream_fill_buffer(&s, buffer, sizeof(buffer)) == 192);
    assert(legacy_reads == 1);

    for (int type = STREAM_VIDEO; type <= STREAM_SUB; type++) {
        assert(ignored(false, type, 0x1fff));
        assert(ignored(true, type, 0x1fff));
    }
    assert(!ignored(false, STREAM_AUDIO, 0x1100));
    assert(!ignored(true, STREAM_AUDIO, 0x1100));
    assert(!ignored(true, STREAM_VIDEO, 0x1011));
    assert(!ignored(true, STREAM_SUB, 0x1200));
    assert(!ignored(false, STREAM_AUDIO, 0x80)); // DVD remains unchanged.
    puts("PASS: still-menu command progresses, clip boundary held, NULL_PID rejected on native/callback paths");
    return 0;
}
