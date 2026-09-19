#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <limits.h>

typedef void JNIEnv;
struct priv { bool thread_terminate, compressed; void *audiotrack, *chunk; int lock, wakeup, chunksize; unsigned written_frames; };
struct ao { struct priv *priv; int sstride, samplerate; };
struct mp_aframe { int unused; };
static const struct { int PLAYSTATE_PAUSED, PLAYSTATE_PLAYING; } AudioTrack = {0, 1};
static const struct { int ERROR_DEAD_OBJECT; } AudioManager = {-6};
static unsigned reads, writes, zero_writes, waits, empty_reads, short_writes, recreates;
static int play_state, write_result;
static bool stop_on_wait;
static struct ao *active;
#define MP_THREAD_VOID void
#define MP_THREAD_RETURN() return
#define MP_JNI_GET_ENV(ao) NULL
#define MP_JNI_CALL_INT(...) play_state
#define MP_TIME_S_TO_NS(s) ((int64_t)((s) * 1000000000.0))
#define MP_TIME_MS_TO_NS(ms) ((int64_t)(ms) * 1000000)
#define MP_WARN(...) ((void)0)
#define MP_ERR(...) ((void)0)
static void mp_thread_set_name(const char *name) {}
static void mp_mutex_lock(int *lock) { assert(!*lock); *lock = 1; }
static void mp_mutex_unlock(int *lock) { assert(*lock); *lock = 0; }
static int64_t mp_time_ns(void) { return 0; }
static void mp_cond_timedwait(int *cond, int *lock, int64_t time) {
    assert(*lock);
    assert(time == MP_TIME_MS_TO_NS(play_state == AudioTrack.PLAYSTATE_PLAYING ? 20 : 300));
    waits++;
    if (stop_on_wait) active->priv->thread_terminate = true;
    else play_state = AudioTrack.PLAYSTATE_PLAYING;
}
static double AudioTrack_getLatency(struct ao *ao) { return 0; }
static int ao_read_data(struct ao *ao, void **data, int samples, int64_t ts, bool *eof, bool pad, bool blocking) {
    assert(!pad && !blocking);
    assert(++reads < 20); // A missing empty-read wait must not hang the test.
    return reads <= empty_reads ? 0 : 8;
}
static int AudioTrack_write(struct ao *ao, const void *source, int len) {
    writes++;
    zero_writes += len == 0;
    ao->priv->thread_terminate = true;
    return write_result == INT_MAX ? len : write_result;
}
static int AudioTrack_Recreate(struct ao *ao) { recreates++; return 0; }
static struct mp_aframe *ao_read_frame(struct ao *ao) { return NULL; }
static uint8_t **mp_aframe_get_data_ro(struct mp_aframe *frame) { return NULL; }
static size_t mp_aframe_get_data_size(struct mp_aframe *frame) { return 0; }
static unsigned mp_aframe_get_size(struct mp_aframe *frame) { return 0; }
static void ao_request_reload(struct ao *ao) {}
static void talloc_free(void *data) {}

#include "ao_thread_under_test.h"

static unsigned run(unsigned empty, bool terminate_on_wait, bool paused, bool compressed, int result) {
    reads = writes = zero_writes = waits = short_writes = recreates = 0;
    empty_reads = empty;
    stop_on_wait = terminate_on_wait;
    play_state = paused ? AudioTrack.PLAYSTATE_PAUSED : AudioTrack.PLAYSTATE_PLAYING;
    write_result = result;
    struct priv p = {.audiotrack = (void *)1, .chunksize = 4096, .compressed = compressed};
    struct ao ao = {.priv = &p, .sstride = 24, .samplerate = 48000};
    active = &ao;
    ao_thread(&ao);
    assert(p.lock == 0);
    assert(zero_writes == 0);
    return p.written_frames;
}

int main(void) {
    assert(run(5, false, false, false, INT_MAX) == 8);
    assert(reads == 6 && waits == 5 && writes == 1);
    assert(run(1, true, false, false, INT_MAX) == 0);
    assert(reads == 1 && waits == 1 && writes == 0);
    assert(run(0, false, true, false, INT_MAX) == 8);
    assert(waits == 1 && writes == 1);
    assert(run(0, false, false, false, INT_MAX) == 8);
    assert(waits == 0 && writes == 1);
    assert(run(0, false, false, false, 4 * 24) == 4);
    assert(waits == 0 && writes == 1);
    assert(run(0, false, false, false, AudioManager.ERROR_DEAD_OBJECT) == 0);
    assert(recreates == 1 && waits == 0);
    assert(run(0, true, false, true, INT_MAX) == 0);
    assert(waits == 1 && writes == 0);
    puts("PASS: PCM empty-read wait, recovery, stop, pause/start, normal/short write, dead-object and compressed wait (7 cases)");
}
