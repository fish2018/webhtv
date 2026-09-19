/* Execute the production FEL preinit option block with mpv's real ta allocator
 * and option copy/free functions. Only GPU creation and the cache container are
 * replaced; the owned option values and their destruction are not mocked. */
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "ta/ta_talloc.h"

typedef struct m_obj_settings {
    char *name;
    char *label;
    bool enabled;
    char **attribs;
} m_obj_settings_t;
typedef struct m_option m_option_t;
typedef struct m_option_type {
    void (*copy)(const m_option_t *, void *, const void *);
    void (*free)(void *);
} m_option_type_t;
struct m_option { const m_option_type_t *type; };

#include "option_functions.inc"

struct ra_ctx_opts {
    m_obj_settings_t *context_list;
    m_obj_settings_t *context_type_list;
    bool probing;
    bool want_alpha;
};
struct priv { void *context; };
struct vo {
    struct priv *priv;
    void *global;
    struct { bool android_dovi_fel; int android_dovi_fel_vulkan; } extra;
};
static const int ra_ctx_conf;
static const m_option_t list_option = { .type = &m_option_type_obj_settings_list };
static struct ra_ctx_opts global_opts;
static int create_calls, fail_attempts, cache_frees, replaced_lists;

static void original_list_freed(void *ptr)
{
    (void)ptr;
    replaced_lists++;
}

static void free_options(void *ptr)
{
    struct ra_ctx_opts *opts = ptr;
    m_option_free(&list_option, &opts->context_list);
    m_option_free(&list_option, &opts->context_type_list);
    cache_frees++;
}

static struct ra_ctx_opts *mp_get_config_group(void *parent, void *global,
                                               const void *conf)
{
    (void)parent; (void)global; (void)conf;
    struct ra_ctx_opts *opts = talloc_zero(NULL, struct ra_ctx_opts);
    m_option_copy(&list_option, &opts->context_list, &global_opts.context_list);
    m_option_copy(&list_option, &opts->context_type_list, &global_opts.context_type_list);
    opts->probing = true;
    talloc_set_destructor(opts->context_list, original_list_freed);
    talloc_set_destructor(opts->context_type_list, original_list_freed);
    talloc_set_destructor(opts, free_options);
    return opts;
}

static void update_ra_ctx_options(struct vo *vo, struct ra_ctx_opts *opts)
{
    (void)vo;
    opts->want_alpha = true;
}

static void *gpu_ctx_create(struct vo *vo, struct ra_ctx_opts *opts)
{
    bool vk = vo->extra.android_dovi_fel_vulkan && create_calls == 0;
    const char *context = vo->extra.android_dovi_fel ? (vk ? "androidvk" : "android") : "saved-context";
    const char *api = vo->extra.android_dovi_fel ? (vk ? "vulkan" : "opengl") : "saved-api";
    assert(strcmp(opts->context_list[0].name, context) == 0);
    assert(strcmp(opts->context_type_list[0].name, api) == 0);
    assert(opts->want_alpha);
    assert(opts->probing == !vo->extra.android_dovi_fel);
    create_calls++;
    return create_calls <= fail_attempts ? NULL : vo;
}

#define MP_WARN(...) ((void)0)
#include "fel_preinit.inc"

static void run_case(const char *name, bool fel, int mode, int failures,
                     int expected_calls, bool success)
{
    create_calls = cache_frees = replaced_lists = 0;
    fail_attempts = failures;
    struct priv priv = {0};
    struct vo vo = { .priv = &priv,
        .extra = { .android_dovi_fel = fel, .android_dovi_fel_vulkan = mode } };
    assert((run_preinit(&vo) == 0) == success);
    assert(create_calls == expected_calls);
    assert(cache_frees == 1);
    assert(replaced_lists == 2);
    assert(strcmp(global_opts.context_list[0].name, "saved-context") == 0);
    assert(strcmp(global_opts.context_list[0].label, "saved-label") == 0);
    assert(strcmp(global_opts.context_list[0].attribs[1], "saved-value") == 0);
    assert(strcmp(global_opts.context_type_list[0].name, "saved-api") == 0);
    printf("PASS %s: creates=%d, original lists freed, global settings preserved\n", name, create_calls);
}

int main(void)
{
    char *attrs[] = {"saved-key", "saved-value", NULL};
    m_obj_settings_t context[] = {{.name="saved-context", .label="saved-label", .enabled=true, .attribs=attrs}, {0}};
    m_obj_settings_t api[] = {{.name="saved-api", .enabled=true}, {0}};
    m_obj_settings_t *context_list=context, *api_list=api;
    m_option_copy(&list_option, &global_opts.context_list, &context_list);
    m_option_copy(&list_option, &global_opts.context_type_list, &api_list);
    run_case("ordinary output", false, 0, 0, 1, true);
    run_case("explicit Vulkan", true, 1, 0, 1, true);
    run_case("explicit OpenGL", true, 0, 0, 1, true);
    run_case("automatic Vulkan", true, 2, 0, 1, true);
    run_case("automatic Vulkan to OpenGL", true, 2, 1, 2, true);
    run_case("both backends fail", true, 2, 2, 2, false);
    run_case("explicit Vulkan failure stays strict", true, 1, 1, 1, false);
    m_option_free(&list_option, &global_opts.context_list);
    m_option_free(&list_option, &global_opts.context_type_list);
    puts("PASS actual mpv ta and option ownership lifecycle");
    return 0;
}
