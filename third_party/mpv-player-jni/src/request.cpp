#include <jni.h>

#include <deque>
#include <limits>
#include <memory>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

#include <mpv/client.h>

#include "globals.h"
#include "jni_utils.h"
#include "log.h"
#include "request.h"

enum class RequestType {
    VIDEO_SURFACE,
    OSD_SURFACE,
    COMMAND,
    SHUTDOWN,
};

static constexpr uint64_t INTERNAL_REQUEST_ID =
    std::numeric_limits<uint64_t>::max();

struct MpvRequest {
    RequestType type;
    uint64_t request_id;
    std::vector<std::string> command;
    jobject surface;

    MpvRequest(RequestType type_, uint64_t request_id_, jobject surface_)
        : type(type_), request_id(request_id_), surface(surface_) {}

    MpvRequest(uint64_t request_id_, std::vector<std::string> command_)
        : type(RequestType::COMMAND), request_id(request_id_),
          command(std::move(command_)), surface(NULL) {}

    MpvRequest()
        : type(RequestType::SHUTDOWN), request_id(INTERNAL_REQUEST_ID),
          command(1, "quit"), surface(NULL) {}
};

struct RequestFailure {
    uint64_t request_id;
    int error;
};

// libmpv may complete asynchronous mutations out of order. Keep one mutation
// in flight so commands and Surface changes are applied in submission order.
static std::mutex request_mutex;
static std::deque<std::unique_ptr<MpvRequest>> pending_requests;
static std::unique_ptr<MpvRequest> request_in_flight;
static jobject video_surface;
static jobject osd_surface;

static void clear_surface(JNIEnv *env, jobject *target) {
    if (!*target)
        return;
    env->DeleteGlobalRef(*target);
    *target = NULL;
}

static void clear_request_surface(JNIEnv *env, MpvRequest *request) {
    if (!request || !request->surface)
        return;
    env->DeleteGlobalRef(request->surface);
    request->surface = NULL;
}

static jobject *get_applied_surface(RequestType type) {
    if (type == RequestType::VIDEO_SURFACE)
        return &video_surface;
    if (type == RequestType::OSD_SURFACE)
        return &osd_surface;
    return NULL;
}

static const char *get_surface_property(RequestType type) {
    if (type == RequestType::VIDEO_SURFACE)
        return "wid";
    if (type == RequestType::OSD_SURFACE)
        return "android-osd-wid";
    return NULL;
}

static bool is_command_request(RequestType type) {
    return type == RequestType::COMMAND || type == RequestType::SHUTDOWN;
}

static int get_reply_event_id(const MpvRequest *request) {
    return is_command_request(request->type) ? MPV_EVENT_COMMAND_REPLY
                                             : MPV_EVENT_SET_PROPERTY_REPLY;
}

static int start_next_request_locked(JNIEnv *env,
                                     std::vector<RequestFailure> *failures) {
    if (request_in_flight || pending_requests.empty())
        return MPV_ERROR_SUCCESS;

    mpv_handle *context = g_mpv.load();
    if (!context || !g_event_thread_started)
        return MPV_ERROR_UNINITIALIZED;

    std::unique_ptr<MpvRequest> request = std::move(pending_requests.front());
    pending_requests.pop_front();

    int result;
    if (is_command_request(request->type)) {
        std::vector<const char *> arguments(request->command.size() + 1, NULL);
        for (size_t i = 0; i < request->command.size(); ++i)
            arguments[i] = request->command[i].c_str();
        result = mpv_command_async(context, request->request_id,
                                   arguments.data());
    } else {
        const char *property = get_surface_property(request->type);
        int64_t wid = reinterpret_cast<intptr_t>(request->surface);
        result = mpv_set_property_async(context, request->request_id,
            property, MPV_FORMAT_INT64, &wid);
    }
    if (result < 0) {
        const char *action = is_command_request(request->type)
            ? "mpv_command_async" : "mpv_set_property_async";
        const char *target = is_command_request(request->type)
            ? (request->command.empty() ? "<empty>" : request->command[0].c_str())
            : get_surface_property(request->type);
        ALOGE("%s(%s) returned error %s", action, target,
              mpv_error_string(result));
        if (request->type == RequestType::SHUTDOWN) {
            g_force_shutdown = true;
            mpv_wakeup(context);
            return MPV_ERROR_SUCCESS;
        }
        if (failures && request->request_id != INTERNAL_REQUEST_ID)
            failures->push_back({request->request_id, result});
        clear_request_surface(env, request.get());
        return result;
    }

    request_in_flight = std::move(request);
    return MPV_ERROR_SUCCESS;
}

static int enqueue_request(JNIEnv *env, std::unique_ptr<MpvRequest> request) {
    std::lock_guard<std::mutex> lock(request_mutex);
    if (!g_mpv || !g_event_thread_started || g_shutdown_requested) {
        clear_request_surface(env, request.get());
        return MPV_ERROR_UNINITIALIZED;
    }
    pending_requests.push_back(std::move(request));
    return start_next_request_locked(env, NULL);
}

int enqueue_command(JNIEnv *env, uint64_t request_id,
                    std::vector<std::string> command) {
    std::unique_ptr<MpvRequest> request(
        new MpvRequest(request_id, std::move(command)));
    return enqueue_request(env, std::move(request));
}

int enqueue_shutdown(JNIEnv *env) {
    std::lock_guard<std::mutex> lock(request_mutex);
    if (!g_mpv || !g_event_thread_started)
        return MPV_ERROR_UNINITIALIZED;
    if (g_shutdown_requested.exchange(true))
        return MPV_ERROR_SUCCESS;

    pending_requests.push_back(std::unique_ptr<MpvRequest>(new MpvRequest()));
    const int result = start_next_request_locked(env, NULL);
    if (result < 0) {
        mpv_handle *context = g_mpv.load();
        if (context) {
            g_force_shutdown = true;
            mpv_wakeup(context);
        }
    }
    return MPV_ERROR_SUCCESS;
}

static int enqueue_surface_request(JNIEnv *env, uint64_t request_id,
                                   SurfaceTarget target, jobject surface) {
    jobject surface_ref = surface ? env->NewGlobalRef(surface) : NULL;
    if (surface && !surface_ref)
        return MPV_ERROR_NOMEM;

    RequestType type;
    if (target == SurfaceTarget::VIDEO) {
        type = RequestType::VIDEO_SURFACE;
    } else {
        type = RequestType::OSD_SURFACE;
    }
    std::unique_ptr<MpvRequest> request(
        new MpvRequest(type, request_id, surface_ref));
    return enqueue_request(env, std::move(request));
}

int enqueue_surface(JNIEnv *env, SurfaceTarget target, jobject surface) {
    return enqueue_surface_request(env, INTERNAL_REQUEST_ID, target, surface);
}

int enqueue_surface_async(JNIEnv *env, uint64_t request_id,
                          SurfaceTarget target, jobject surface) {
    if (!request_id || request_id == INTERNAL_REQUEST_ID)
        return MPV_ERROR_INVALID_PARAMETER;
    return enqueue_surface_request(env, request_id, target, surface);
}

void handle_request_reply(JNIEnv *env, mpv_event *event) {
    if (event->event_id != MPV_EVENT_SET_PROPERTY_REPLY &&
            event->event_id != MPV_EVENT_COMMAND_REPLY)
        return;

    bool notify_current;
    std::vector<RequestFailure> failures;
    {
        std::lock_guard<std::mutex> lock(request_mutex);
        if (!request_in_flight ||
                event->reply_userdata != request_in_flight->request_id ||
                event->event_id != get_reply_event_id(request_in_flight.get()))
            return;

        MpvRequest *request = request_in_flight.get();
        notify_current = request->request_id != INTERNAL_REQUEST_ID;
        jobject *applied_surface = get_applied_surface(request->type);
        if (event->error < 0) {
            ALOGE("asynchronous mpv request failed: %s",
                  mpv_error_string(event->error));
            clear_request_surface(env, request);
        } else if (applied_surface) {
            clear_surface(env, applied_surface);
            *applied_surface = request->surface;
            request->surface = NULL;
        }
        request_in_flight.reset();

        while (!pending_requests.empty()) {
            int result = start_next_request_locked(env, &failures);
            if (result >= 0 || result == MPV_ERROR_UNINITIALIZED)
                break;
        }
    }

    if (notify_current)
        send_command_reply_to_java(env, event->reply_userdata, event->error);
    for (const RequestFailure &failure : failures)
        send_command_reply_to_java(env, failure.request_id, failure.error);
}

void release_requests(JNIEnv *env) {
    std::vector<uint64_t> canceled_request_ids;
    {
        std::lock_guard<std::mutex> lock(request_mutex);
        if (request_in_flight &&
                request_in_flight->request_id != INTERNAL_REQUEST_ID) {
            canceled_request_ids.push_back(request_in_flight->request_id);
        }
        for (const std::unique_ptr<MpvRequest> &request : pending_requests) {
            if (request->request_id != INTERNAL_REQUEST_ID)
                canceled_request_ids.push_back(request->request_id);
        }

        clear_surface(env, &video_surface);
        clear_surface(env, &osd_surface);
        clear_request_surface(env, request_in_flight.get());
        request_in_flight.reset();
        for (const std::unique_ptr<MpvRequest> &request : pending_requests)
            clear_request_surface(env, request.get());
        pending_requests.clear();
    }

    for (uint64_t request_id : canceled_request_ids)
        send_command_reply_to_java(env, request_id, MPV_ERROR_UNINITIALIZED);
}
