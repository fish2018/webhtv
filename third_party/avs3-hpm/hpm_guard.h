/* Internal to the pinned HPM build; never included by other FFmpeg codecs. */
#ifndef WEBHTV_HPM_GUARD_H
#define WEBHTV_HPM_GUARD_H
#include <stddef.h>
#include <stdint.h>

void *webhtv_hpm_alloc(size_t size);
void *webhtv_hpm_calloc(size_t count, size_t size);
void *webhtv_hpm_aligned_alloc(size_t alignment, size_t size);
void webhtv_hpm_free(void *ptr);
__attribute__((noreturn)) void webhtv_hpm_fail(int code, const char *reason, int line);
void webhtv_hpm_set_color(int range, int primaries, int trc, int matrix);
#define HPM_REQUIRE(x) do { if (!(x)) webhtv_hpm_fail(-1, #x, __LINE__); } while (0)
#define HPM_EXIT(x) webhtv_hpm_fail(-1, "upstream fatal error", __LINE__)
#endif
