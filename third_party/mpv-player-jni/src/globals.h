#pragma once

#include <atomic>

extern JavaVM *g_vm;
extern std::atomic<mpv_handle *> g_mpv;
extern std::atomic<bool> g_event_thread_started;
extern std::atomic<bool> g_shutdown_requested;
extern std::atomic<bool> g_force_shutdown;
