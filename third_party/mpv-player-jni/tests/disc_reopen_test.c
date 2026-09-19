#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

// Compile the real reopen_slave() against a deterministic event-only stream.
// In particular, EOF between playlists must not leave a freed slave behind.
struct chapter { void *metadata; };
struct demuxer {
    void *priv, *stream, *cancel, *global;
    int stream_origin, depth, num_chapters;
    struct chapter *chapters;
};
struct priv {
    struct demuxer *slave;
    bool is_cdda;
    int slave_to_outer_count;
    void **slave_to_outer;
};
struct demuxer_params {
    const char *force_format;
    void *external_stream;
    int stream_flags, depth;
};

static struct demuxer slave;
static int free_count, double_frees, peek_bytes;
static bool slave_live, open_ok;

#define MP_VERBOSE(...) ((void)0)
#define MP_WARN(...) ((void)0)
#define STREAM_CTRL_GET_TIME_LENGTH 1

static void demux_free(struct demuxer *value)
{
    if (!value) return;
    free_count++;
    if (!slave_live) double_frees++;
    slave_live = false;
}
static void stream_rebase_position(void *stream) { (void)stream; }
static int stream_read_peek(void *stream, void *buffer, unsigned size)
{
    (void)stream; (void)buffer; (void)size;
    return peek_bytes;
}
static struct demuxer *demux_open_url(const char *url, struct demuxer_params *p,
                                      void *cancel, void *global)
{
    (void)url; (void)p; (void)cancel; (void)global;
    slave_live = open_ok;
    return open_ok ? &slave : NULL;
}
static void sync_streams(struct demuxer *d) { (void)d; }
static int stream_control(void *stream, int command, void *arg)
{
    (void)stream; (void)command; (void)arg;
    return 0;
}
static void demux_set_duration(struct demuxer *d, double v) { (void)d; (void)v; }
static void talloc_free(void *p) { (void)p; }
static void add_stream_chapters(struct demuxer *d) { (void)d; }
static void sync_initial_edition(struct demuxer *d) { (void)d; }
static void demux_lists_changed(struct demuxer *d) { (void)d; }

#include "disc_reopen_under_test.h"

#define CHECK(condition) do { \
    if (!(condition)) { \
        fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #condition); \
        return 1; \
    } \
} while (0)

int main(void)
{
    struct priv p = {.slave = &slave};
    struct demuxer parent = {.priv = &p};
    slave_live = true;

    // Event-only reads may repeat, and closing after either must be safe.
    for (int n = 0; n < 3; n++) {
        CHECK(!reopen_slave(&parent));
        CHECK(p.slave == NULL);
        CHECK(free_count == 1 && double_frees == 0);
    }
    demux_free(p.slave);
    CHECK(free_count == 1 && double_frees == 0);

    // A failed format probe has the same ownership contract.
    peek_bytes = 192;
    CHECK(!reopen_slave(&parent));
    CHECK(p.slave == NULL && double_frees == 0);

    // Later data can recover; a subsequent jump must release exactly once.
    open_ok = true;
    CHECK(reopen_slave(&parent));
    CHECK(p.slave == &slave && slave_live);
    CHECK(reopen_slave(&parent));
    CHECK(free_count == 2 && double_frees == 0);
    demux_free(p.slave);
    CHECK(free_count == 3 && double_frees == 0);
    puts("disc reopen regression: PASS (event-only retries, close, failed probe, recovery)");
    return 0;
}
