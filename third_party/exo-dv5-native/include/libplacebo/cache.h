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

#ifndef LIBPLACEBO_CACHE_H_
#define LIBPLACEBO_CACHE_H_

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#include <libplacebo/config.h>
#include <libplacebo/common.h>
#include <libplacebo/log.h>

PL_API_BEGIN

typedef struct pl_cache_obj {
    // Cache object key. This will uniquely identify this cached object.
    uint64_t key;

    // Cache data pointer and length. 0-length cached objects are invalid
    // and will be silently dropped. You can explicitly remove a cached
    // object by overwriting it with a length 0 object.
    void *data;
    size_t size;

    // Free callback, to free memory associated with `data`. (Optional)
    // Will be called when the object is either explicitly deleted, culled
    // due to hitting size limits, or on pl_cache_destroy().
    void (*free)(void *data);
} pl_cache_obj;

struct pl_cache_params {
    // Optional `pl_log` that is used for logging internal events related
    // to the cache, such as insertions, saving and loading.
    pl_log log;

    // Size limits. If 0, no limit is imposed.
    //
    // Note: libplacebo will never detect or invalidate stale cache entries, so
    // setting an upper size limit is strongly recommended
    size_t max_object_size;
    size_t max_total_size;

    // Optional external callback to call after a cached object is modified
    // (including deletion and (re-)insertion). Note that this is not called on
    // objects which are merely pruned from the cache due to `max_total_size`,
    // so users must rely on some external mechanism to prune stale entries or
    // enforce size limits.
    //
    // Note: `pl_cache_load` does not trigger this callback.
    // Note: Ownership of `obj` does *not* pass to the caller.
    // Note: This function must be thread safe.
    void (*set)(void *priv, pl_cache_obj obj);

    // Optional external callback to call on a cache miss. Ownership of the
    // returned object passes to the `pl_cache`. Objects returned by this
    // callback *should* have a valid `free` callback, unless lifetime can be
    // externally managed and guaranteed to outlive the `pl_cache`.
    //
    // Note: This function must be thread safe.
    pl_cache_obj (*get)(void *priv, uint64_t key);

    // External context for get/set.
    void *priv;
};

#define pl_cache_params(...) (&(struct pl_cache_params) { __VA_ARGS__ })
PL_API extern const struct pl_cache_params pl_cache_default_params;

// Thread-safety: Safe
//
// Note: In any context in which `pl_cache` is used, users may also pass NULL
// to disable caching. In other words, NULL is a valid `pl_cache`.
typedef const struct pl_cache_t {
    struct pl_cache_params params;
} *pl_cache;

// Create a new cache. This function will never fail.
PL_API pl_cache pl_cache_create(const struct pl_cache_params *params);

// Destroy a `pl_cache` object, including all underlying objects.
PL_API void pl_cache_destroy(pl_cache *cache);

// Explicitly clear all objects in the cache without destroying it. This is
// similar to `pl_cache_destroy`, but the cache remains valid afterwards.
//
// Note: Objects destroyed in this way *not* propagated to the `set` callback.
PL_API void pl_cache_reset(pl_cache cache);

// Return the current internal number of objects and total size (bytes)
PL_API int pl_cache_objects(pl_cache cache);
PL_API size_t pl_cache_size(pl_cache cache);

// Return a lightweight, order-independent hash of all objects currently stored
// in the `pl_cache`. Can be used to avoid re-saving unmodified caches.
PL_API uint64_t pl_cache_signature(pl_cache cache);

// --- Cache saving and loading APIs

// Serialize the internal state of a `pl_cache` into an abstract cache
// object that can be e.g. saved to disk and loaded again later. Returns the
// number of objects saved.
//
// Note: Using `save/load` is largely redundant with using `insert/lookup`
// callbacks, and the user should decide whether to use the explicit API or the
// callback-based API.
PL_API int pl_cache_save_ex(pl_cache cache,
                            void (*write)(void *priv, size_t size, const void *ptr),
                            void *priv);

// Load the result of a previous `pl_cache_save` call. Any duplicate entries in
// the `pl_cache` will be overwritten. Returns the number of objects loaded, or
// a negative number on serious error (e.g. corrupt header)
//
// Note: This does not trigger the `update` callback.
PL_API int pl_cache_load_ex(pl_cache cache,
                            bool (*read)(void *priv, size_t size, void *ptr),
                            void *priv);

// --- Convenience wrappers around pl_cache_save/load_ex

// Writes data directly to a pointer. Returns the number of bytes that *would*
// have been written, so this can be used on a size 0 buffer to get the required
// total size.
PL_API size_t pl_cache_save(pl_cache cache, uint8_t *data, size_t size);

// Reads data directly from a pointer. This still reads from `data`, so it does
// not avoid a copy.
PL_API int pl_cache_load(pl_cache cache, const uint8_t *data, size_t size);

// Writes/loads data to/from a FILE stream at the current position.
#define pl_cache_save_file(c, file) pl_cache_save_ex(c, pl_write_file_cb, file)
#define pl_cache_load_file(c, file) pl_cache_load_ex(c, pl_read_file_cb,  file)

static inline void pl_write_file_cb(void *priv, size_t size, const void *ptr)
{
    (void) fwrite(ptr, 1, size, (FILE *) priv);
}

static inline bool pl_read_file_cb(void *priv, size_t size, void *ptr)
{
    return fread(ptr, 1, size, (FILE *) priv) == size;
}

// --- Standard callbacks for caching to a files inside a directory.

// Write the cache object to a file. The filename is generated by combining
// `path` with a 16-digit lowercase hex string for the ID. Note that if
// `path` does not end with a directory separator, the filename will be
// effectively prefixed by the last path component. All directories must
// already exist.
//
// If `obj` has size 0, the file will instead be removed.
//
// Note: The files written by this callback use the same internal format as
// `pl_cache_save_file`, and could thus also be loaded directly using
// `pl_cache_load`.
PL_API void pl_cache_set_file(void *path, pl_cache_obj obj);

// Retrieve a cache object from a file inside `dir`. See `pl_cache_set_dir`.
//
// If the cached file is missing, truncated or otherwise corrupt, it is
// instead removed (if needed) and {0} is returned.
PL_API pl_cache_obj pl_cache_get_file(void *path, uint64_t key);

#define pl_cache_set_dir pl_cache_set_file
#define pl_cache_get_dir pl_cache_get_file

// --- Object modification API. Mostly intended for internal use.

// Insert a new cached object into a `pl_cache`. Returns whether successful.
// Overwrites any existing cached object with that signature, so this can be
// used to e.g. delete objects as well (set their size to 0). On success,
// ownership of `obj` passes to the `pl_cache`.
//
// Note: If `object.free` is NULL, this will perform an internal memdup. To
// bypass this (e.g. when directly adding externally managed memory), you can
// set the `free` callback to an explicit noop function.
//
// Note: `obj->data/free` will be reset to NULL on successful insertion.
PL_API bool pl_cache_try_set(pl_cache cache, pl_cache_obj *obj);

// Variant of `pl_cache_try_set` that simply frees `obj` on failure.
PL_API void pl_cache_set(pl_cache cache, pl_cache_obj *obj);

// Looks up `obj->key` in the object cache. If successful, `obj->data` is
// set to memory owned by the caller, which must be either explicitly
// re-inserted, or explicitly freed (using obj->free).
//
// Note: On failure, `obj->data/size/free` are reset to NULL.
PL_API bool pl_cache_get(pl_cache cache, pl_cache_obj *obj);

// Run a callback on every object currently stored in `cache`.
//
// Note: Running any `pl_cache_*` function on `cache` from this callback is
// undefined behavior.
PL_API void pl_cache_iterate(pl_cache cache,
                             void (*cb)(void *priv, pl_cache_obj obj),
                             void *priv);

// Utility wrapper to free a `pl_cache_obj` if necessary (and sanitize it)
static inline void pl_cache_obj_free(pl_cache_obj *obj)
{
    if (obj->free)
        obj->free(obj->data);
    obj->data = NULL;
    obj->free = NULL;
    obj->size = 0;
}

PL_API_END

#endif // LIBPLACEBO_CACHE_H_
