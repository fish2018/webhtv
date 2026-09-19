#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>

struct stream_info { const char *name; };
struct stream { struct stream_info *info; };
struct stream_nav_state { bool nav_active; };
struct m_obj_settings { const char *name; };
struct mp_vo_opts { struct m_obj_settings *video_driver_list; };
struct MPOpts { struct mp_vo_opts *vo; };
struct MPContext { struct MPOpts *opts; void *mconfig; };

#define STREAM_CTRL_GET_NAV_STATE 1
#define M_SETOPT_BACKUP 16
#define MP_VERBOSE(...) ((void)0)
#define bstr0(value) (value)

static struct stream_info info = {.name = "bd"};
static struct stream disc = {.info = &info};
static struct stream *active_stream = &disc;
static bool navigation_active = true;
static int query_result = 1, option_calls;
static struct stream *disc_nav_get_stream(struct MPContext *mpctx)
{
    (void)mpctx;
    return active_stream;
}
static int stream_control(struct stream *s, int command, void *arg)
{
    assert(s == &disc && command == STREAM_CTRL_GET_NAV_STATE);
    ((struct stream_nav_state *)arg)->nav_active = navigation_active;
    return query_result;
}
static int m_config_set_option_cli(void *config, const char *name,
                                   const char *value, int flags)
{
    (void)config;
    assert(strcmp(name, "hwdec-software-fallback") == 0);
    assert(strcmp(value, "yes") == 0);
    assert(flags == M_SETOPT_BACKUP);
    option_calls++;
    return 0;
}

#include "disc_options_under_test.h"

static void expect_calls(struct MPContext *mpctx, int count)
{
    option_calls = 0;
    load_disc_navigation_options(mpctx);
    assert(option_calls == count);
}

int main(void)
{
    struct m_obj_settings output = {.name = "gpu-next"};
    struct mp_vo_opts vo = {.video_driver_list = &output};
    struct MPOpts opts = {.vo = &vo};
    struct MPContext mpctx = {.opts = &opts};

    expect_calls(&mpctx, 1);
    output.name = "gpu";
    info.name = "bdmv/bluray";
    expect_calls(&mpctx, 1);
    navigation_active = false; // BD-J/no-menu longest-title fallback.
    expect_calls(&mpctx, 0);
    navigation_active = true;
    query_result = 0;
    expect_calls(&mpctx, 0);
    query_result = 1;
    info.name = "dvdnav";
    expect_calls(&mpctx, 0);
    info.name = "bd";
    output.name = "mediacodec_embed";
    expect_calls(&mpctx, 0);
    output.name = "libmpv";
    expect_calls(&mpctx, 0);
    output.name = NULL;
    expect_calls(&mpctx, 0);
    vo.video_driver_list = NULL;
    expect_calls(&mpctx, 0);
    active_stream = NULL;
    expect_calls(&mpctx, 0);
    puts("PASS: HDMV GPU fallback is file-local and precedes decoder init; other paths unchanged");
    return 0;
}
