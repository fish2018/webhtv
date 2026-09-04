#pragma once

#include <jni.h>
#include <stdint.h>
#include <string>
#include <vector>

struct mpv_event;

enum class SurfaceTarget {
    VIDEO,
    OSD,
};

int enqueue_command(JNIEnv *env, uint64_t request_id,
                    std::vector<std::string> command);
int enqueue_surface(JNIEnv *env, SurfaceTarget target, jobject surface);
int enqueue_surface_async(JNIEnv *env, uint64_t request_id,
                          SurfaceTarget target, jobject surface);
int enqueue_shutdown(JNIEnv *env);
void handle_request_reply(JNIEnv *env, mpv_event *event);
void release_requests(JNIEnv *env);
