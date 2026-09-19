// Compile the actual wrapper functions from the pinned, patched mpv source.
// Only their surrounding decoder/dispatch environment is replaced by a fake.
#include "filters/f_android_fel.h"
#include <cassert>
#include <iostream>
#include <mutex>
#include <stdexcept>

enum dec_ctrl { VDCTRL_FORCE_HWDEC_FALLBACK, VDCTRL_GET_HWDEC, VDCTRL_REINIT };
enum { CONTROL_UNKNOWN = -1, CONTROL_FALSE = 0, CONTROL_TRUE = 1 };
struct priv;
struct mp_filter { priv *priv; };
struct mp_decoder_wrapper { mp_filter *f; };
struct mp_decoder {
    mp_filter *f;
    int (*control)(mp_filter *, dec_ctrl, void *);
};
struct priv {
    bool is_group = false;
    int num_children = 0;
    mp_decoder_wrapper **children = nullptr;
    bool android_fel = false;
    bool software_el = false;
    void *queue = nullptr;
    mp_decoder *decoder = nullptr;
    std::mutex cache_lock;
    char *cur_hwdec = nullptr;
    mp_android_fel_hwdec_snapshot fel_hwdec_cache{};
    mp_android_fel_hwdec_snapshot fel_hwdec_read{};
    unsigned long long fel_status_reads = 0;
    double fel_status_log_at = 0;
    double fps = 23.976;
    bool forbid_decoder_wait = false;
    int decoder_locks = 0;
    int decoder_unlocks = 0;
    int decoder_controls = 0;
    int decoder_status = CONTROL_FALSE;
    char decoder_name[96] = "mediacodec";
};
#define mp_assert assert
#define MP_INFO(...) ((void)0)
static void mp_mutex_lock(std::mutex *lock) { lock->lock(); }
static void mp_mutex_unlock(std::mutex *lock) { lock->unlock(); }
static void thread_lock(priv *p) {
    ++p->decoder_locks;
    if (p->forbid_decoder_wait)
        throw std::runtime_error("read-only FEL query waits for the decoder");
}
static void thread_unlock(priv *p) { ++p->decoder_unlocks; }
[[maybe_unused]] static double mp_time_sec() { return 10.0; }
static int fake_control(mp_filter *f, dec_ctrl cmd, void *arg) {
    auto *p = f->priv;
    ++p->decoder_controls;
    if (cmd == VDCTRL_GET_HWDEC) {
        if (p->decoder_status == CONTROL_TRUE)
            *static_cast<char **>(arg) = p->decoder_name[0] ? p->decoder_name : nullptr;
        return p->decoder_status;
    }
    return CONTROL_TRUE;
}

static int update_cached_values(priv *p);
int mp_decoder_wrapper_control(mp_decoder_wrapper *, dec_ctrl, void *);
double mp_decoder_wrapper_get_container_fps(mp_decoder_wrapper *);

int main() {
    priv state;
    mp_filter filter{&state};
    mp_decoder decoder{&filter, fake_control};
    mp_decoder_wrapper wrapper{&filter};
    state.decoder = &decoder;
    state.android_fel = true;
    state.queue = &state; // An asynchronous FEL decoder.
    state.forbid_decoder_wait = true;
    char *name = nullptr;
    try {
        // Probing is unavailable, not an invented software-decoding result.
        update_cached_values(&state);
        assert(mp_decoder_wrapper_control(&wrapper, VDCTRL_GET_HWDEC, &name) == CONTROL_FALSE);
        assert(mp_decoder_wrapper_get_container_fps(&wrapper) == state.fps);
        state.decoder_status = CONTROL_TRUE;
        update_cached_values(&state);
        assert(mp_decoder_wrapper_control(&wrapper, VDCTRL_GET_HWDEC, &name) == CONTROL_TRUE);
        assert(!strcmp(name, "mediacodec"));
        assert(name != state.decoder_name && name != state.fel_hwdec_cache.name);
        auto revision = state.fel_hwdec_cache.revision;
        update_cached_values(&state);
        assert(state.fel_hwdec_cache.revision == revision); // no churn for same state

        // Publishing a new decoder must not invalidate the caller's old value.
        strcpy(state.decoder_name, "mediacodec-copy");
        update_cached_values(&state);
        assert(!strcmp(name, "mediacodec"));
        assert(state.fel_hwdec_cache.revision > revision);
        assert(mp_decoder_wrapper_control(&wrapper, VDCTRL_GET_HWDEC, &name) == CONTROL_TRUE);
        assert(!strcmp(name, "mediacodec-copy"));

        state.decoder_name[0] = 0;
        update_cached_values(&state);
        assert(mp_decoder_wrapper_control(&wrapper, VDCTRL_GET_HWDEC, &name) == CONTROL_TRUE);
        assert(!name[0]); // known software decoder, distinct from unknown
        state.decoder_status = CONTROL_FALSE;
        update_cached_values(&state);
        assert(mp_decoder_wrapper_control(&wrapper, VDCTRL_GET_HWDEC, &name) == CONTROL_FALSE);
        state.decoder_status = CONTROL_TRUE;
        memset(state.decoder_name, 'x', sizeof(state.decoder_name) - 1);
        state.decoder_name[sizeof(state.decoder_name) - 1] = 0;
        update_cached_values(&state);
        assert(mp_decoder_wrapper_control(&wrapper, VDCTRL_GET_HWDEC, &name) == CONTROL_FALSE);
        assert(!name[0]); // refuse overflow/truncation, do not expose stale state
        assert(state.decoder_locks == 0);
    } catch (const std::runtime_error &error) {
        std::cerr << "FAIL: " << error.what() << '\n';
        return 1;
    }

    // Modifying operations still serialize with the decoder.
    state.forbid_decoder_wait = false;
    auto controls = state.decoder_controls;
    assert(mp_decoder_wrapper_control(&wrapper, VDCTRL_REINIT, nullptr) == CONTROL_TRUE);
    assert(state.decoder_locks == 1 && state.decoder_controls > controls);
    assert(mp_decoder_wrapper_control(&wrapper, VDCTRL_FORCE_HWDEC_FALLBACK, nullptr) == CONTROL_TRUE);
    assert(state.decoder_locks == 2);
    // Neither ordinary playback nor a non-threaded wrapper changes behavior.
    state.android_fel = false;
    assert(mp_decoder_wrapper_control(&wrapper, VDCTRL_GET_HWDEC, &name) == CONTROL_TRUE);
    assert(name == state.decoder_name && state.decoder_locks == 3);
    assert(mp_decoder_wrapper_get_container_fps(&wrapper) == state.fps);
    assert(state.decoder_locks == 4);
    state.android_fel = true;
    state.queue = nullptr;
    assert(mp_decoder_wrapper_control(&wrapper, VDCTRL_GET_HWDEC, &name) == CONTROL_TRUE);
    assert(state.decoder_locks == 5);
    assert(state.decoder_unlocks == state.decoder_locks);
    std::cout << "PASS: actual decoder-control functions avoid FEL read waits; "
                 "probe status, owned names and synchronous mutations preserved\n";
}
