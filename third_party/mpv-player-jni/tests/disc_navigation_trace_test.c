#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <pthread.h>

#define thread_local _Thread_local
#define MP_TIME_S_TO_NS(s) ((s) * 1000000000LL)
enum { DBG_CRIT = 1, DBG_HDMV = 2, DBG_GC = 4 };
typedef struct { int event; } BD_EVENT;
struct bluray_priv_s {
    bool trace_navigation, menu_event_active, overlay_ig_visible, popup_supported;
    bool still_active, data_delivered;
    uint32_t trace_request, uo_mask, nav_change_id, discontinuity_id;
    int overlay_lock, plane_w, plane_h;
    void *bd;
};
typedef struct { struct bluray_priv_s *priv; } stream_t;
static unsigned emitted;
static char last_log[1500];
static int64_t now = 1000000000LL;
static void (*handler)(const char *);
static unsigned mask;
static int64_t mp_time_ns(void) { return now; }
static void mp_mutex_lock(int *lock) { assert(!*lock); *lock = 1; }
static void mp_mutex_unlock(int *lock) { assert(*lock); *lock = 0; }
static void log_message(stream_t *s, const char *format, ...)
{
    assert(s);
    va_list ap;
    va_start(ap, format);
    vsnprintf(last_log, sizeof(last_log), format, ap);
    va_end(ap);
    emitted++;
}
#define MP_INFO(s, ...) log_message(s, __VA_ARGS__)
static void bd_set_debug_handler(void (*fn)(const char *)) { handler = fn; }
static void bd_set_debug_mask(unsigned value) { mask = value; }
static int bd_read_ext(void *bd, void *buf, int len, BD_EVENT *event)
{
    (void)bd; (void)buf;
    handler("hdmv_vm.c:997: 0000: SET_BUTTON_PAGE 0, 2\n");
    event->event = 7;
    return len;
}
#include "disc_trace_under_test.h"

static void *unowned_thread(void *unused)
{
    (void)unused;
    handler("hdmv_vm.c:997: must not inherit another thread's owner\n");
    return NULL;
}

int main(void)
{
    bluray_trace_init();
    assert(handler && mask == (DBG_CRIT | DBG_HDMV | DBG_GC));
    assert(bluray_trace_line("hdmv_vm.c:997: SET_BUTTON_PAGE\n", false));
    assert(bluray_trace_line("graphics_controller.c:1558: _user_input(17)\n", false));
    assert(bluray_trace_line("graphics_controller.c:1530: _user_input mouse-hit: button=29 cmds=0\n", true));
    assert(bluray_trace_line("bluray.c:956: HDMV input GC: result=1 cmds=0\n", true));
    assert(bluray_trace_line("bluray.c:965: HDMV input VM: cmds=2 result=0 running=1\n", false));
    assert(bluray_trace_line("graphics_controller.c:1312: render button #29\n", true));
    assert(!bluray_trace_line("graphics_controller.c:1312: render button #29\n", false));
    assert(!bluray_trace_line("graphics_controller.c:1282: skipping already rendered\n", true));
    assert(!bluray_trace_line("graphics_controller.c:1499: auto-activate not triggered (ANIMATING)\n", true));
    assert(!bluray_trace_line("file.c:99: https://private-media-address\n", true));
    assert(!bluray_trace_line(NULL, true));
    handler("hdmv_vm.c:997: no owner\n");
    assert(emitted == 0);
    struct bluray_priv_s b = {.trace_navigation = true, .trace_request = 17};
    stream_t s = {.priv = &b};
    bluray_trace_begin(&s, "input");
    handler("graphics_controller.c:1312: render button #29\n");
    assert(emitted == 1 && strstr(last_log, "seq=17 phase=input"));
    pthread_t thread;
    assert(!pthread_create(&thread, NULL, unowned_thread, NULL));
    assert(!pthread_join(thread, NULL));
    assert(emitted == 1);
    for (int i = 0; i < 200; i++) handler("hdmv_vm.c:997: test\n");
    assert(emitted == 97 && strstr(last_log, "trace capped"));
    bluray_trace_end();
    assert(bd_trace.stream == NULL);
    handler("hdmv_vm.c:997: after close\n");
    assert(emitted == 97);
    now += MP_TIME_S_TO_NS(1);
    unsigned before = emitted;
    for (int input = 0; input < 8; input++) {
        bluray_trace_begin(&s, "input");
        for (int i = 0; i < 90; i++) handler("hdmv_vm.c:997: test\n");
        bluray_trace_end();
    }
    assert(emitted - before == 601); // per-thread one-second budget + one notice
    now += MP_TIME_S_TO_NS(1);
    BD_EVENT event = {0};
    assert(bluray_read_ext(&s, NULL, 0, &event) == 0 && event.event == 7);
    assert(bd_trace.stream == NULL && strstr(last_log, "phase=poll"));
    bluray_trace_state(&s, "input-end", 1, now);
    assert(strstr(last_log, "result=1 elapsed_ms=0"));
    b.trace_navigation = false;
    before = emitted;
    bluray_trace_begin(&s, "input");
    handler("hdmv_vm.c:997: disabled\n");
    bluray_trace_state(&s, "input-end", 1, now);
    bluray_trace_end();
    assert(before == emitted);
    puts("PASS: navigation trace filtering / disabled mode / per-call and per-second caps / thread-local ownership / cleared read context");
    return 0;
}
