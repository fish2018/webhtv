#include <assert.h>
#include <stdbool.h>
#include <stdio.h>

enum { BLURAY_MENU_TITLE = -1, BLURAY_DEFAULT_TITLE = -2,
       BLURAY_TITLE_TOP_MENU = 0 };
struct bluray_priv_s {
    int cfg_title, current_title, overlay_lock;
    bool hdmv_mode;
    void *bd;
};
typedef struct { struct bluray_priv_s *priv; } stream_t;
static bool play_ok, first_play_pending, overlay_registered;
static int play_calls, top_menu_calls, title_selections;
#define MP_VERBOSE(ctx, ...) do { if (false) fprintf(stderr, __VA_ARGS__); } while (0)
static void mp_mutex_init(int *lock) { *lock = 1; }
static void mp_mutex_destroy(int *lock) { assert(*lock == 1); *lock = 0; }
static void bd_yuv_overlay_cb(void) {}
static void bd_register_overlay_proc(void *bd, void *context, void (*cb)(void))
{
    (void)bd; (void)context;
    overlay_registered = cb != NULL;
}
static int bd_play(void *bd)
{
    (void)bd;
    play_calls++;
    first_play_pending = play_ok;
    return play_ok;
}
int bd_play_title(void *bd, int title)
{
    (void)bd;
    assert(title == BLURAY_TITLE_TOP_MENU);
    top_menu_calls++;
    first_play_pending = false; // Discards the not-yet-executed FIRST PLAY.
    return 1;
}
static int bd_get_current_title(void *bd) { (void)bd; return 0; }
static int bd_get_main_title(void *bd) { (void)bd; return 7; }
static void select_initial_title(stream_t *s, int title)
{
    s->priv->current_title = title;
    title_selections++;
}
static void start_navigation(stream_t *s)
{
    struct bluray_priv_s *b = s->priv;
#include "disc_start_under_test.h"
}

int main(void)
{
    struct bluray_priv_s b = {.cfg_title = BLURAY_MENU_TITLE};
    stream_t s = {.priv = &b};
    play_ok = true;
    start_navigation(&s);
    assert(b.hdmv_mode && overlay_registered && b.overlay_lock == 1);
    assert(first_play_pending && play_calls == 1 && top_menu_calls == 0);
    assert(title_selections == 0);

    // Failed boot still quietly falls back to the ordinary main title.
    play_ok = false;
    b = (struct bluray_priv_s){.cfg_title = BLURAY_MENU_TITLE};
    start_navigation(&s);
    assert(!b.hdmv_mode && !overlay_registered && b.overlay_lock == 0);
    assert(b.cfg_title == BLURAY_DEFAULT_TITLE && b.current_title == 7);
    assert(play_calls == 2 && title_selections == 1);

    // Menu-disabled / BD-J fallback must not boot a VM or register overlays.
    b = (struct bluray_priv_s){.cfg_title = BLURAY_DEFAULT_TITLE};
    start_navigation(&s);
    assert(!b.hdmv_mode && !overlay_registered && b.current_title == 7);
    assert(play_calls == 2 && top_menu_calls == 0 && title_selections == 2);
    puts("PASS: FIRST PLAY survives initialization; failed/disabled menus retain ordinary-title fallback");
    return 0;
}
