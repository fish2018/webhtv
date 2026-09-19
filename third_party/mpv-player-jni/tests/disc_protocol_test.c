#include <stdio.h>

#include <mpv/client.h>
#include <mpv/stream_cb.h>

static int probe_open(void *context, char *uri, mpv_stream_cb_info *info)
{
    (void)context;
    (void)uri;
    (void)info;
    return MPV_ERROR_LOADING_FAILED;
}

static int expect_registration(mpv_handle *handle, const char *label,
                               const char *protocol,
                               mpv_stream_cb_open_ro_fn callback, int expected)
{
    int actual = mpv_stream_cb_add_ro(handle, protocol, NULL, callback);
    printf("%s: protocol=%s actual=%d expected=%d %s\n", label, protocol,
           actual, expected, actual == expected ? "PASS" : "FAIL");
    return actual != expected;
}

int main(void)
{
    mpv_handle *handle = mpv_create();
    if (!handle) {
        fprintf(stderr, "mpv_create failed\n");
        return 2;
    }

    int failures = 0;
    failures += expect_registration(handle, "ISO callback", "webhtv-dvdiso",
                                    probe_open, 0);
    failures += expect_registration(handle, "Duplicate callback", "webhtv-dvdiso",
                                    probe_open, MPV_ERROR_INVALID_PARAMETER);
    failures += expect_registration(handle, "Other custom callback", "webhtv-probe",
                                    probe_open, 0);
    failures += expect_registration(handle, "Built-in file stays reserved", "file",
                                    probe_open, MPV_ERROR_INVALID_PARAMETER);
    failures += expect_registration(handle, "Built-in HTTPS stays reserved", "https",
                                    probe_open, MPV_ERROR_INVALID_PARAMETER);
    failures += expect_registration(handle, "Missing callback", "webhtv-null",
                                    NULL, MPV_ERROR_INVALID_PARAMETER);

    mpv_destroy(handle);
    printf("Native protocol regression: %s (%d failures)\n",
           failures ? "FAIL" : "PASS", failures);
    return failures ? 1 : 0;
}
