/*
 * This file is part of libplacebo.
 *
 * libplacebo is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * libplacebo is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with libplacebo.  If not, see <http://www.gnu.org/licenses/>.
 */

#ifndef LIBPLACEBO_LOG_H_
#define LIBPLACEBO_LOG_H_

#include <libplacebo/config.h>
#include <libplacebo/common.h>

PL_API_BEGIN

// The log level associated with a given log message.
enum pl_log_level {
    PL_LOG_NONE = 0,
    PL_LOG_FATAL,   // results in total loss of function of a major component
    PL_LOG_ERR,     // serious error; may result in degraded function
    PL_LOG_WARN,    // warning; potentially bad, probably user-relevant
    PL_LOG_INFO,    // informational message, also potentially harmless errors
    PL_LOG_DEBUG,   // verbose debug message, informational
    PL_LOG_TRACE,   // very noisy trace of activity,, usually benign
    PL_LOG_ALL = PL_LOG_TRACE,
};

struct pl_log_params {
    // Logging callback. All messages, informational or otherwise, will get
    // redirected to this callback. The logged messages do not include trailing
    // newlines. Optional.
    void (*log_cb)(void *log_priv, enum pl_log_level level, const char *msg);
    void *log_priv;

    // The current log level. Controls the level of message that will be
    // redirected to the log callback. Setting this to PL_LOG_ALL means all
    // messages will be forwarded, but doing so indiscriminately can result
    // in increased CPU usage as it may enable extra debug paths based on the
    // configured log level.
    enum pl_log_level log_level;
};

#define pl_log_params(...) (&(struct pl_log_params) { __VA_ARGS__ })
PL_API extern const struct pl_log_params pl_log_default_params;

// Thread-safety: Safe
//
// Note: In any context in which `pl_log` is used, users may also pass NULL
// to disable logging. In other words, NULL is a valid `pl_log`.
typedef const struct pl_log_t {
    struct pl_log_params params;
} *pl_log;

#define pl_log_glue1(x, y) x##y
#define pl_log_glue2(x, y) pl_log_glue1(x, y)
// Force a link error in the case of linking against an incompatible API
// version.
#define pl_log_create pl_log_glue2(pl_log_create_, PL_API_VER)
// Creates a pl_log. `api_ver` is for historical reasons and ignored currently.
// `params` defaults to `&pl_log_default_params` if left as NULL.
//
// Note: As a general rule, any `params` struct used as an argument to a
// function need only live until the corresponding function returns.
PL_API pl_log pl_log_create(int api_ver, const struct pl_log_params *params);

// Destroy a `pl_log` object.
//
// Note: As a general rule, all `_destroy` functions take the pointer to the
// object to free as their parameter. This pointer is overwritten by NULL
// afterwards. Calling a _destroy function on &{NULL} is valid, but calling it
// on NULL itself is invalid.
PL_API void pl_log_destroy(pl_log *log);

// Update the parameters of a `pl_log` without destroying it. This can be
// used to change the log function, log context or log level retroactively.
// `params` defaults to `&pl_log_default_params` if left as NULL.
//
// Returns the previous params, atomically.
PL_API struct pl_log_params pl_log_update(pl_log log, const struct pl_log_params *params);

// Like `pl_log_update` but only updates the log level, leaving the log
// callback intact.
//
// Returns the previous log level, atomically.
PL_API enum pl_log_level pl_log_level_update(pl_log log, enum pl_log_level level);

// Two simple, stream-based loggers. You can use these as the log_cb. If you
// also set log_priv to a FILE* (e.g. stdout or stderr) it will be printed
// there; otherwise, it will be printed to stdout or stderr depending on the
// log level.
//
// The version with colors will use ANSI escape sequences to indicate the log
// level. The version without will use explicit prefixes.
PL_API void pl_log_simple(void *stream, enum pl_log_level level, const char *msg);
PL_API void pl_log_color(void *stream, enum pl_log_level level, const char *msg);

// Backwards compatibility with older versions of libplacebo
#define pl_context pl_log
#define pl_context_params pl_log_params

PL_API_END

#endif // LIBPLACEBO_LOG_H_
