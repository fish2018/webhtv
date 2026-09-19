// Compile the production header; no replacement progress/formatting algorithm.
#include "filters/f_android_fel_trace.h"
#include <assert.h>
#include <pthread.h>
#include <string.h>

struct writer_args {
    struct mp_fel_trace *trace;
    enum mp_fel_lane lane;
};

static void *write_progress(void *arg)
{
    struct writer_args *args = arg;
    for (int n = 0; n < 10000; n++) {
        enum mp_fel_stage stage = n % 2 ? MP_FEL_SEND : MP_FEL_RECEIVE;
        mp_fel_trace_record(args->trace, args->lane, stage,
                            1000 + n * 32 + args->lane, 18.0, 1, 1, 1);
    }
    return NULL;
}

int main(void)
{
    struct mp_fel_trace trace;
    mp_fel_trace_init(&trace);
    assert(atomic_is_lock_free(&trace.lanes[MP_FEL_BL].stamp));
    mp_fel_trace_record(&trace, MP_FEL_BL, MP_FEL_SEND, 1000, 18.0, 1, 1, 1);
    assert(atomic_load(&trace.lanes[MP_FEL_BL].stamp) == 0);
    assert(atomic_load(&trace.lanes[MP_FEL_BL].inputs) == 0);
    atomic_store(&trace.enabled, true);

    mp_fel_trace_record(&trace, MP_FEL_BL, MP_FEL_RECEIVE, 1000, 18.125, 1, 3, 2);
    char text[1024];
    mp_fel_trace_format(&trace, 1750, text, sizeof(text));
    assert(strstr(text, "v=1 requested=1 refs=2"));
    assert(strstr(text, "BL={codec-receive age=750ms pts=18125 in=3 out=2 held=1}"));
    assert(strstr(text, "VO={unknown age=0ms pts=-1 in=0 out=0 held=0}"));

    // Missing/malformed PTS never undergoes an undefined floating-int cast.
    mp_fel_trace_record(&trace, MP_FEL_BL, MP_FEL_DECODE_IDLE, 1800, NAN, -1, 0, 0);
    mp_fel_trace_record(&trace, MP_FEL_BL, MP_FEL_DECODE_IDLE, 1800, INFINITY, -1, 0, 0);
    mp_fel_trace_record(&trace, MP_FEL_BL, MP_FEL_DECODE_IDLE, 1800, 1e30, -1, 0, 0);
    assert(atomic_load(&trace.lanes[MP_FEL_BL].pts_ms) == 18125);
    assert(atomic_load(&trace.lanes[MP_FEL_BL].held) == 1);

    // The packed 32-bit millisecond timestamp wraps without inventing a stall.
    mp_fel_trace_record(&trace, MP_FEL_VO, MP_FEL_VO_SWAP,
                        (int64_t)UINT32_MAX - 99, 18.0, 2, 1, 0);
    mp_fel_trace_format(&trace, (int64_t)UINT32_MAX + 101, text, sizeof(text));
    assert(strstr(text, "VO={vo-swap-buffers age=200ms pts=18000 in=1 out=0 held=2}"));

    char tiny[] = {'x', 'y', 'z'};
    mp_fel_trace_format(&trace, 2000, tiny, 0);
    assert(tiny[0] == 'x' && tiny[1] == 'y');
    mp_fel_trace_format(&trace, 2000, tiny, 1);
    assert(tiny[0] == 0 && tiny[1] == 'y');
    mp_fel_trace_format(NULL, 2000, text, sizeof(text));
    assert(!strcmp(text, "VO unavailable"));
    assert(!strcmp(mp_fel_stage_name(255), "unknown"));

    // Four independent native lanes publish while the fatal reader snapshots.
    // A single stamp must never contain a stage from a different timestamp.
    struct mp_fel_trace concurrent;
    mp_fel_trace_init(&concurrent);
    atomic_store(&concurrent.enabled, true);
    pthread_t threads[MP_FEL_LANES];
    struct writer_args args[MP_FEL_LANES];
    for (int n = 0; n < MP_FEL_LANES; n++) {
        args[n] = (struct writer_args){&concurrent, n};
        assert(!pthread_create(&threads[n], NULL, write_progress, &args[n]));
    }
    for (int read = 0; read < 4000; read++) {
        for (int n = 0; n < MP_FEL_LANES; n++) {
            uint64_t stamp = atomic_load(&concurrent.lanes[n].stamp);
            if (!stamp)
                continue;
            uint32_t tick = (uint32_t)(stamp >> 8) - 1000 - n;
            assert(tick % 32 == 0);
            unsigned expected = (tick / 32) % 2 ? MP_FEL_SEND : MP_FEL_RECEIVE;
            assert((stamp & 255) == expected);
        }
        mp_fel_trace_format(&concurrent, 400000, text, sizeof(text));
        assert(strstr(text, "core={") && strstr(text, "VO={"));
    }
    for (int n = 0; n < MP_FEL_LANES; n++) {
        assert(!pthread_join(threads[n], NULL));
        assert(atomic_load(&concurrent.lanes[n].inputs) == 10000);
        assert(atomic_load(&concurrent.lanes[n].outputs) == 10000);
    }
    puts("PASS: production FEL progress snapshots: disabled isolation, bounded formatting, clock wrap and concurrent reads/writes");
    return 0;
}
